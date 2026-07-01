package org.example.yunpicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.example.yunpicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 腾讯云 COS 对象存储通用操作类（Manager 层）
 * <p>
 * 作用：将针对第三方云存储的底层 API 调用封装在 Manager 层，
 * 避免业务逻辑（Service 层）与第三方 SDK 强耦合，便于后续维护或更换存储厂商。
 */
@Component
public class CosManager {

    /**
     * 注入 COS 客户端配置属性
     * 用于获取 application.yml 中配置的 bucket（存储桶）名称等基本信息
     */
    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 注入 COS 客户端实例
     * 由 CosClientConfig 中配置的 @Bean 注册，用于实际向腾讯云发起网络请求
     */
    @Resource
    private COSClient cosClient;

    /**
     * 将本地文件上传至 COS 对象存储
     * 以下代码参考官方文档生成：https://cloud.tencent.com/document/product/436/65935
     *
     * @param key  对象键（Key）。即文件在 COS 中的绝对路径和名称。
     * 例如："public/test/my-image.jpg"（注意：前面不要加 "/"）
     * @param file 需要上传的本地物理文件对象
     * @return PutObjectResult 上传结果对象，包含上传成功后的 ETag、RequestId、文件 MD5 等元数据信息
     */
    public PutObjectResult putObject(String key, File file) {
        // 构造上传请求，指定存储桶 (Bucket)、对象键 (Key) 以及要上传的文件 (File)
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        // 调用 COS 客户端执行上传操作，并返回结果
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 从 COS 对象存储下载文件对象
     * 以下代码参考官方文档生成：https://cloud.tencent.com/document/product/436/65937
     *
     * @param key 对象键（Key）。即文件在 COS 中的绝对路径和名称。
     * 例如："public/test/my-image.jpg"（注意：前面不要加 "/"）
     * @return COSObject 下载结果对象，包含对象的内容输入流（ObjectContent）以及元数据信息（如文件大小、类型等）。
     * 注意：获取到数据后，请务必及时关闭并释放 COSObjectInputStream 流，避免网络连接泄漏。
     */
    public COSObject getObject(String key) {
        // 构造下载请求，指定存储桶 (Bucket) 以及要获取的对象键 (Key)
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);

        // 调用 COS 客户端执行下载操作，并返回包含文件流信息的对象
        return cosClient.getObject(getObjectRequest);
    }



    /**
     * 将本地图片上传至 COS 对象存储，并附带获取图片基本信息及执行同步图片处理（如 WebP 压缩）
     * <p>
     * 业务场景：在上传图片的同时，利用腾讯云数据万象（Cloud Infinite, CI）的底层能力，
     * 要求 COS 服务端在上传成功后自动解析返回原图信息（宽、高、格式等），并同步执行格式转换处理。
     * 优势：将图片解析与压缩转换的计算压力完全转移到云端，避免在应用服务器本地消耗大量 CPU 和内存处理图片流。
     * 以下代码参考官方文档生成：https://cloud.tencent.com/document/product/436/55377
     *
     * @param key  对象键（Key）。即文件在 COS 中的绝对路径和名称。
     * 例如："public/test/my-image.jpg"（注意：前面不要加 "/"）
     * @param file 需要上传的本地物理图片文件对象
     * @return PutObjectResult 上传结果对象。除了包含基础的 ETag、RequestId 外，
     * 如果在存储桶中开启了数据万象，该结果内还会封装从服务端返回的图片元数据信息（CIUploadResult），
     * 以及所配置的各项图片处理规则（如 WebP 转换）的执行结果。
     */
    public PutObjectResult putPictureObject(String key, File file) {
        // 1. 构造基础上传请求，指定存储桶 (Bucket)、对象键 (Key) 以及要上传的文件 (File)
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        // 2. 构造图片处理参数对象（PicOperations 用于封装数据万象相关的处理规则）
        PicOperations picOperations = new PicOperations();

        // 3. 开启返回原图信息标志位
        // 参数设为 1 表示：要求 COS 服务端在完成图片存盘后，立即同步解析原图，并在响应体中返回图片的基本信息
        picOperations.setIsPicInfo(1);

        // 4. 构造图片处理规则列表（可支持格式转换、压缩、裁剪、水印等多项操作）
        List<PicOperations.Rule> rules = new ArrayList<>();

        // ==================================================================================================
        // 4.1 定义图片格式转换（压缩为 WebP 格式）规则
        // 参考官网地址：https://cloud.tencent.com/document/product/436/113299 生成
        // 业务说明：WebP 格式能在保持较高视觉质量的同时大幅减小文件体积，非常适合作为图库的展示或缩略图，以节省 CDN 流量
        String filePrefix = getFilePrefix(key);
        String originalSuffix = FileUtil.getSuffix(key);
        String thumbnailSuffix = StrUtil.blankToDefault(originalSuffix, "png");
        String webpKey = filePrefix + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();

        // 设置处理规则：利用数据万象的基础图片处理接口（imageMogr2），将图片目标格式转换为 webp
        compressRule.setRule("imageMogr2/format/webp");
        // 设置处理结果的存储目标桶（通常与原图存储桶保持一致）
        compressRule.setBucket(cosClientConfig.getBucket());
        // 设置处理后生成的新文件的对象键（Key），即 WebP 图片的存储路径和名称
        compressRule.setFileId(webpKey);

        // 将该转换规则加入到规则列表中
        rules.add(compressRule);
        // ==================================================================================================


        // ==================================================================================================
        // 边界优化：仅对大于 2KB 的图片生成缩略图，避免极小图片因处理产生元数据开销导致体积反增
        if(file.length() > 2 * 1024){
            // 4.2 定义缩略图生成规则
            // 参考官网地址：https://cloud.tencent.com/document/product/436/113295 生成
            // 业务说明：生成较小尺寸的缩略图能够大幅提升列表页等多图场景的前端加载速度，降低客户端内存开销并节省 CDN 流量成本
            PicOperations.Rule thumbnailUrlRule = new PicOperations.Rule();

            String thumbnailKey = filePrefix + "_thumbnail." + thumbnailSuffix;
            // 设置处理规则：利用数据万象的基础图片处理接口（imageMogr2），将图片按最大宽高 256x256 进行等比缩放
            // 缩放规则: /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailUrlRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 256, 256));
            // 设置处理结果的存储目标桶（通常与原图存储桶保持一致）
            thumbnailUrlRule.setBucket(cosClientConfig.getBucket());
            // 设置处理后生成的新文件的对象键（Key），即缩略图的存储路径和名称
            thumbnailUrlRule.setFileId(thumbnailKey);

            // 将该缩略图规则加入到规则列表中
            rules.add(thumbnailUrlRule);
        }
        // ==================================================================================================

        // 5. 构造处理参数
        // 5.1 将构建好的图片处理规则集合绑定到操作参数对象中
        picOperations.setRules(rules);
        // 5.2 将配置完成的图片处理参数（包含原图解析与 WebP 转换指令）附加到本次上传请求中
        putObjectRequest.setPicOperations(picOperations);

        // 6. 执行上传并触发云端图片处理，直接返回包含处理结果（如 WebP 压缩状态）的响应对象
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 删除云端对象（文件）
     * <p>
     * 业务说明：根据提供的对象键（Key），从 COS 存储桶中永久删除对应的物理文件。
     * 通常用于用户删除图片记录时，同步清理云端存储以释放空间。
     *
     * @param key 待删除文件在云端的唯一对象键（即存储路径与文件名，例如："public/picture/2023-10-25_uuid.jpg"）
     * @throws CosClientException 当网络异常或无权限执行删除操作时抛出
     */
    public void deleteObject(String key) throws CosClientException {
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }

    /**
     * 获取对象 key 去掉扩展名后的同目录前缀。
     * <p>
     * 业务背景：例如 "public/u/a.png" -> "public/u/a"。
     * 后续生成的 WebP 压缩图和缩略图都基于这个前缀生成，
     * 核心目的有两个：
     * 1. 保证处理产物仍落在原图所在目录下，方便在 COS 控制台按目录统一管理。
     * 2. 在删除原图时，能通过同一个前缀（如 prefix="public/u/a"）批量匹配并反推删除所有关联的衍生文件。
     * </p>
     *
     * @param key 云端原始对象键（例如："public/u/a.png" 或根目录的 "a.png"）
     * @return 剔除后缀后的同目录前缀字符串
     */
    public String getFilePrefix(String key) {
        // 1. 定位最后一个路径分隔符，用于切分“所在目录”和“具体文件名”
        int slashIndex = key.lastIndexOf("/");

        // 2. 提取目录路径部分
        // 兼容逻辑：如果找不到 "/"（slashIndex < 0），说明文件直接存放在 Bucket 根目录，目录记为空串；
        // 否则截取包含最后一个 "/" 在内的前缀（例如："public/u/"）
        String dir = slashIndex >= 0 ? key.substring(0, slashIndex + 1) : "";

        // 3. 提取完整的原始文件名（带后缀）
        // 兼容逻辑：同上，根目录文件直接取原 key；否则取最后一个 "/" 之后的字符串（例如："a.png"）
        String filename = slashIndex >= 0 ? key.substring(slashIndex + 1) : key;

        // 4. 拼接并返回最终前缀
        // FileUtil.mainName 用于剥离扩展名（"a.png" -> "a"），最终拼接回 "public/u/a"
        return dir + FileUtil.mainName(filename);
    }


}
