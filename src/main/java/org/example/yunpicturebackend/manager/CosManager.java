package org.example.yunpicturebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.example.yunpicturebackend.config.CosClientConfig;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;

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
     * 将本地图片上传至 COS 对象存储，并附带获取图片基本信息
     * <p>
     * 业务场景：在上传图片的同时，利用腾讯云数据万象（Cloud Infinite, CI）的底层能力，
     * 要求 COS 服务端在上传成功后自动解析并返回原图的基本信息（如宽度、高度、格式等）。
     * 优势：将图片解析的压力转移到云端，避免在应用服务器本地消耗大量 CPU 和内存去读取图片流。
     * 以下代码参考官方文档生成：https://cloud.tencent.com/document/product/436/55377
     *
     * @param key  对象键（Key）。即文件在 COS 中的绝对路径和名称。
     * 例如："public/test/my-image.jpg"（注意：前面不要加 "/"）
     * @param file 需要上传的本地物理图片文件对象
     * @return PutObjectResult 上传结果对象。除了包含基础的 ETag、RequestId 外，
     * 如果在存储桶中开启了数据万象，该结果内还会封装从服务端返回的图片元数据信息（CIUploadResult）。
     */
    public PutObjectResult putPictureObject(String key, File file) {
        // 1. 构造基础上传请求，指定存储桶 (Bucket)、对象键 (Key) 以及要上传的文件 (File)
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);

        // 2. 构造图片处理参数对象（PicOperations 用于封装数据万象相关的处理规则）
        PicOperations picOperations = new PicOperations();

        // 3. 开启返回原图信息标志位
        // 参数设为 1 表示：要求 COS 服务端在完成图片存盘后，立即同步解析图片，并在响应体中返回图片的基本信息
        picOperations.setIsPicInfo(1);

        // 4. 将配置好的图片处理参数附加到本次上传请求中
        putObjectRequest.setPicOperations(picOperations);

        // 5. 调用 COS 客户端执行带有图片处理指令的上传操作，并返回结果
        PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
        return putObjectResult;
    }


}
