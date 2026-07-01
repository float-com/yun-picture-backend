package org.example.yunpicturebackend.manager.upload;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.config.CosClientConfig;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.manager.CosManager;
import org.example.yunpicturebackend.model.dto.file.UploadPictureResult;

import javax.annotation.Resource;
import java.io.File;
import java.util.Date;
import java.util.List;

/**
 * 图片上传抽象模板类
 * <p>
 * 设计模式：模板方法模式 (Template Method Pattern)
 * 业务场景：统文本地文件上传和网络 URL 图片上传的公共主干流程。
 * 架构设计：为了让模板同时兼容 MultipartFile (本地上传) 和 String (URL 上传) 类型的参数，
 * 将两者的输入源统一向上抽象为 Object 类型的 inputSource。
 * 核心骨架（上传逻辑、云端交互、清理文件）固化在父类，差异化逻辑（校验、流处理、获取文件名）延迟到子类实现。
 */
@Slf4j
public abstract class PictureUploadTemplate {

    @Resource
    protected CosManager cosManager;

    @Resource
    protected CosClientConfig cosClientConfig;

    /**
     * 模板方法：定义核心的图片上传与处理业务骨架
     * <p>
     * 业务流程：
     * 1. 基础校验：拦截非法格式和超大体积的恶意请求。
     * 2. 构造对象键（Key）：生成带时间戳和 UUID 的唯一文件路径，避免云端文件覆盖冲突。
     * 3. 流转临时文件：将输入源转化为本地磁盘临时文件，以适配 COS SDK 的 API 要求。
     * 4. 云端交互：调用 COS Manager 执行上传，并同步触发数据万象（CI）的图片处理任务（如格式压缩、生成缩略图）。
     * 5. 封装结果：优先解析云端返回的处理后图片列表（压缩图、缩略图等），提取元数据并组装完整的可访问 URL；若无处理任务，则返回原图信息。
     * 6. 兜底清理：无论成功失败，强制清理本地临时文件。
     *
     * @param inputSource      输入源对象（兼容本地 MultipartFile 和网络 String URL）
     * @param uploadPathPrefix 上传路径的目录前缀，用于在存储桶内实现业务模块的隔离（如 "public/picture"）
     * @return UploadPictureResult 上传结果对象，包含完整公网 URL（含缩略图）以及宽高、体积、比例等元数据
     */
    public final UploadPictureResult uploadPicture(Object inputSource, String uploadPathPrefix) {
        // 1. 严苛的安全性校验，防止非预期文件或超大文件进入后续高消耗流程
        validPicture(inputSource);

        // 2. 构造文件在云端的唯一对象键（Key）
        // 策略：当天日期作为目录前缀 + 16位随机字母数字 + 原文件后缀名
        // 目的：防止不同用户上传同名文件导致原文件被覆盖；按天做前缀方便在控制台管理和排查
        String uuid = RandomUtil.randomString(16);
        String originFilename = getOriginFilename(inputSource);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;
        try {
            // 3. 将抽象的输入源转存为本地临时物理文件
            // 原因：底层 COS SDK 需要明确的 java.io.File 对象以实现计算 MD5、分块传输等高级特性
            file = File.createTempFile(uploadPath, null);
            // 调度子类的具体实现处理文件源（如：内存流转存、或 HTTP 远程下载）
            processFile(inputSource, file);

            // 4. 调度底层 CosManager 发起带图片处理指令（PicOperations）的上传请求
            // 这一步不仅会上传原图，还会同步触发我们定义的数据万象处理规则（如：转 WebP 格式、生成缩略图）
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 从云端数据万象（CI）的返回体中解析出原图的真实尺寸和格式等元数据
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            // 获取图片处理（如压缩、缩略图）的异步/同步执行结果列表
            ProcessResults processResults = putObjectResult.getCiUploadResult().getProcessResults();
            List<CIObject> objectList = processResults.getObjectList();

            if (CollUtil.isNotEmpty(objectList)) {
                // 根据我们定义的 PicOperations 规则顺序，明确提取对应的处理结果

                // 1. 按 PicOperations 规则的动态添加顺序，提取对应的处理结果
                // 索引 0：对应第一条必选规则，即 WebP 格式转换后的主图（压缩图）对象
                CIObject compressedCiObject = objectList.get(0);

                // 2. 缩略图兜底策略：默认直接使用主图作为缩略图
                // 业务原因：如果上传的是极小图片（<= 2KB），前端并未触发缩略图生成规则，此时保证业务不报错
                CIObject thumbnailCiObject = compressedCiObject;

                // 3. 动态解析可选的缩略图结果
                // 如果处理结果列表大于 1，说明触发了专属缩略图生成规则
                if (objectList.size() > 1) {
                    // 索引 1：对应第二条规则，即提取等比缩放后生成的专属缩略图对象
                    thumbnailCiObject = objectList.get(1);
                }


                // 5. 封装包含压缩图和缩略图的多维返回结果
                // 优势：优先返回处理后的轻量级图片信息，大幅降低前端列表页的加载延迟和 CDN 流量消耗
                return buildResult(originFilename, compressedCiObject, thumbnailCiObject);
            }

            // 5. 兜底策略：如果没有触发任何图片处理规则（或者未配置规则），则直接提取并组装原图的返回结果
            return buildResult(originFilename, file, uploadPath, imageInfo);
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 清理本地缓存临时文件
            // 注意：这是绝对不可省略的兜底操作。防止高并发下因临时文件堆积导致服务器磁盘 100% 报警并引起服务雪崩
            deleteTempFile(file);
        }
    }

    /**
     * 校验输入源（本地文件或 URL）是否合法
     * <p>
     * 作用：在正式读写磁盘或消耗网络带宽前，进行轻量级的参数合法性拦截。
     *
     * @param inputSource 统一抽象的输入源
     * @throws BusinessException 触碰任一红线（如超大、格式不对等），立即抛出异常阻断流程
     */
    protected abstract void validPicture(Object inputSource);

    /**
     * 获取输入源的原始文件名
     *
     * @param inputSource 统一抽象的输入源
     * @return 文件的原始名称，用于提取文件后缀以及回显给前端
     */
    protected abstract String getOriginFilename(Object inputSource);

    /**
     * 处理输入源并生成本地临时文件
     * <p>
     * 业务差异：
     * - 本地上传子类需实现：multipartFile.transferTo(file)
     * - URL上传子类需实现：HttpUtil.downloadFile(fileUrl, file)
     *
     * @param inputSource 统一抽象的输入源
     * @param file        模板方法初始化的空临时物理文件
     * @throws Exception 处理流或网络操作时可能抛出的异常
     */
    protected abstract void processFile(Object inputSource, File file) throws Exception;

    /**
     * 封装云端图片多任务处理（WebP 压缩与缩略图生成）成功后的结果数据
     * <p>
     * 业务场景：当触发了数据万象的多条图片处理规则后，COS 会按规则顺序返回处理后的图片信息列表。
     * 此方法专门用于解析这些处理后的对象节点（压缩主图和缩略图），并将其元数据转化为系统标准的结果集返回给前端。
     *
     * @param originFilename     客户端上传时的原始文件名
     * @param compressedCiObject 数据万象处理生成的主图对象（通常为 WebP 格式的压缩图）
     * @param thumbnailCiObject  数据万象处理生成的缩略图对象
     * @return UploadPictureResult 统一的图片上传结果实体（包含压缩主图的宽、高、大小，以及主图和缩略图的访问 URL）
     */
    private UploadPictureResult buildResult(String originFilename, CIObject compressedCiObject, CIObject thumbnailCiObject) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();

        // 1. 提取主图（压缩处理后）的尺寸信息
        int picWidth = compressedCiObject.getWidth();
        int picHeight = compressedCiObject.getHeight();

        // 2. 计算主图宽高比（使用 Hutool 进行精确运算，避免浮点数精度丢失，强制保留两位小数）
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

        // 3. 基础信息赋值：FileUtil.mainName 用于剥离后缀（如 "test.jpg" -> "test"）作为文件展示名
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(compressedCiObject.getFormat());

        // 4. 注意：此处文件大小取的是“压缩/处理后”的主图云端文件大小，而非本地原图大小，体现了压缩优化的效果
        uploadPictureResult.setPicSize(compressedCiObject.getSize().longValue());

        // 5. 拼接主图（压缩图）的外部访问链接，使用 COS 配置的 Host 和处理后生成的新对象键（Key）
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + compressedCiObject.getKey());

        // 6. 拼接缩略图的外部访问链接，供前端在瀑布流、列表页等场景下快速加载
        uploadPictureResult.setThumbnailUrl(cosClientConfig.getHost() + "/" + thumbnailCiObject.getKey());

        return uploadPictureResult;
    }

    /**
     * 封装原图上传成功后的结果数据（无额外格式转换或需记录原图参数时调用）
     * <p>
     * 业务场景：仅利用数据万象（CI）获取原图信息（isPicInfo=1），而不依赖它的压缩产物。
     *
     * @param originFilename 客户端上传时的原始文件名
     * @param file           本地临时物理文件（用于准确获取原图的字节大小）
     * @param uploadPath     原图在 COS 中的对象键（Key）
     * @param imageInfo      数据万象（CI）同步解析出的原图元数据实体
     * @return UploadPictureResult 统一的图片上传结果实体（包含原图的宽高、大小及 URL）
     */
    private UploadPictureResult buildResult(String originFilename, File file, String uploadPath, ImageInfo imageInfo) {
        UploadPictureResult uploadPictureResult = new UploadPictureResult();

        // 1. 提取原图的尺寸信息
        int picWidth = imageInfo.getWidth();
        int picHeight = imageInfo.getHeight();

        // 2. 计算图片宽高比（使用 Hutool 进行精确运算，强制保留两位小数）
        double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

        // 3. 基础信息赋值：剥离文件后缀，保留纯净的文件名
        uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
        uploadPictureResult.setPicWidth(picWidth);
        uploadPictureResult.setPicHeight(picHeight);
        uploadPictureResult.setPicScale(picScale);
        uploadPictureResult.setPicFormat(imageInfo.getFormat());

        // 4. 注意：由于 ImageInfo 侧重于图像维度信息，此处直接读取本地物理文件的 Size 更为严谨准确
        uploadPictureResult.setPicSize(FileUtil.size(file));

        // 5. 拼接原图的绝对访问 URL 链接（例如：https://your-bucket.cos.ap-guangzhou...）
        uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);

        return uploadPictureResult;
    }

    /**
     * 安全物理擦除本地临时文件
     * <p>
     * 警告：必须置于 try-catch-finally 块的最末端，确保任何异常下都不会产生僵尸文件。
     *
     * @param file 需要销毁的本地物理文件对象
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            // 物理删除失败（如 IO 流未正确关闭导致被系统占用），必须记录 Error 以便触发告警
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }
}