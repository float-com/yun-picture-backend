package org.example.yunpicturebackend.manager;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.config.CosClientConfig;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.model.dto.file.UploadPictureResult;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 通用文件管理服务（Service 层）
 * <p>
 * 作用：作为文件上传和处理的业务门面，负责接收前端传递的 MultipartFile 文件流，
 * 进行安全性校验、本地临时文件流转，并调度 Manager 层的 CosManager 完成最终的云端交互。
 * 优势：将复杂的业务校验、动态文件名称生成机制与底层的第三方 SDK 调用相解耦，
 * 使核心业务逻辑更加清晰，同时保证本地资源的及时释放。
 */
@Service
@Slf4j
public class FileManager {

    /**
     * 注入 COS 客户端配置属性
     * 用于获取 application.yml 中配置的 host（外网访问域名）等基础参数，以便拼接最终的图片访问 URL
     */
    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 注入 COS 对象存储通用操作类
     * 封装了底层腾讯云 COS SDK 的核心网络请求逻辑，供本业务类调度执行真正的云端交互
     */
    @Resource
    private CosManager cosManager;

    /**
     * 上传图片并解析返回元数据
     * <p>
     * 业务场景：用户在前端触发图片上传动作后，系统在存入云端的同时，同步提取图片的宽、高、格式等属性。
     * 业务流程：
     * 1. 基础校验：拦截非法格式和超大体积的恶意请求。
     * 2. 构造对象键（Key）：生成带时间戳和 UUID 的唯一文件路径，避免云端文件覆盖冲突。
     * 3. 流转临时文件：将 Spring 内存文件流写入本地磁盘临时文件，以适配 COS SDK 的 API 要求。
     * 4. 云端交互：调用 COS Manager 执行上传，并利用数据万象（CI）同步解析图片参数。
     * 5. 封装结果：提取云端返回的元数据并组装完整的可访问 URL。
     * 6. 兜底清理：无论成功失败，强制清理本地临时文件。
     *
     * @param multipartFile    需要上传的前端物理图片文件对象（Spring MVC 自动封装）
     * @param uploadPathPrefix 上传路径的目录前缀。用于在存储桶内实现业务模块的目录隔离。
     * 例如："public/picture" 或 "user/avatar"（注意：前后不要加 "/"）
     * @return UploadPictureResult 上传结果对象，包含图片的完整公网 URL 以及宽高、体积、比例等元数据信息
     */
    public UploadPictureResult uploadPicture(MultipartFile multipartFile, String uploadPathPrefix) {
        // 1. 严苛的安全性校验，防止非预期文件或超大文件进入后续高消耗流程
        validPicture(multipartFile);

        // 2. 构造文件在云端的唯一对象键（Key）
        // 策略：当天日期作为目录前缀 + 16位随机字母数字 + 原文件后缀名
        // 目的：1. 防止不同用户上传同名文件导致原文件被覆盖丢失；2. 按天做前缀方便后续在控制台管理和排查
        String uuid = RandomUtil.randomString(16);
        String originFilename = multipartFile.getOriginalFilename();
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);

        File file = null;
        try {
            // 3. 将前端传入的内存流/分片流转存为本地临时物理文件
            // 原因：底层 COS SDK 需要明确的 java.io.File 对象以实现计算 MD5、分块传输等高级特性
            file = File.createTempFile(uploadPath, null);
            multipartFile.transferTo(file);

            // 4. 调度底层 CosManager 发起带图片处理指令（PicOperations）的上传请求
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);

            // 5. 提取并组装结果
            // 从云端数据万象（CI）的返回体中解析出真实尺寸和格式
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();

            UploadPictureResult uploadPictureResult = new UploadPictureResult();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            // 计算图片比例，使用 Hutool 进行精确运算并保留两位小数
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();

            // FileUtil.mainName 作用是将 "test.jpg" 截取为 "test" 作为展示名称
            uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicSize(FileUtil.size(file));

            // 拼接外部访问可用的绝对 URL（例如：https://your-bucket.cos.ap-guangzhou.myqcloud.com/public/picture/2024...）
            uploadPictureResult.setUrl(cosClientConfig.getHost() + "/" + uploadPath);

            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 清理本地缓存流
            // 注意：这是绝对不可省略的兜底操作。防止高并发下因临时文件堆积导致服务器磁盘 100% 报警并引起服务雪崩
            this.deleteTempFile(file);
        }
    }

    /**
     * 校验上传的图片文件是否合法
     * <p>
     * 作用：在正式读写磁盘或消耗网络带宽前，进行轻量级的参数合法性拦截。
     * 拦截维度：文件判空、文件体积上限拦截、文件扩展名白名单拦截。
     *
     * @param multipartFile 待校验的物理文件流对象
     * @throws BusinessException 如果触碰任一红线，立即抛出异常阻断上传动作
     */
    public void validPicture(MultipartFile multipartFile) {
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 1. 校验文件大小
        // 限制：单张图片体积不得超过 2MB。防止恶意消耗存储容量和下行 CDN 流量
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");

        // 2. 校验文件后缀类型
        // 策略：采用白名单机制放行常规图片格式。严防 .php, .jsp 等木马脚本伪装上传
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 安全清理本地临时文件
     * <p>
     * 业务场景：由于上传过程中将内存流转存为了磁盘临时文件，生命周期完结后必须立即销毁。
     * 注意：必须在 try-catch-finally 的 finally 块中调用此方法，确保任何异常抛出时都不会产生僵尸文件。
     *
     * @param file 需要销毁的本地物理文件对象
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 调用底层 API 执行物理擦除
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            // 若删除动作失败（通常是 IO 占用未释放导致），必须记录 Error 日志以备排查
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }

}