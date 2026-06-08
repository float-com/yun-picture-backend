package org.example.yunpicturebackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云对象存储（COS）客户端配置类
 * <p>
 * 用于读取 application.yml 中的 cos.client 配置，并向 Spring 容器注入 COSClient 实例。
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

    /**
     * 访问域名（通常用于配置自定义域名或内网访问 endpoint）
     */
    private String host;

    /**
     * 腾讯云 API 密钥 SecretId（用于标识 API 调用者身份）
     */
    private String secretId;

    /**
     * 腾讯云 API 密钥 SecretKey（用于验证 API 调用者身份，请严格保密，切勿提交至公开代码仓库）
     */
    private String secretKey;

    /**
     * 存储桶所属地域简称（例如：ap-guangzhou、ap-shanghai）
     */
    private String region;

    /**
     * 存储桶名称（格式通常为 BucketName-APPID，例如：example-1250000000）
     */
    private String bucket;

    /**
     * 初始化并向 Spring 容器注入 COSClient 实例
     * 以下代码参考官方文档改造而成：https://cloud.tencent.com/document/product/436/10199
     *
     * @return 配置完毕的 COSClient 对象
     */
    @Bean
    public COSClient cosClient() {
        // 1. 初始化用户身份凭证 (SecretId, SecretKey)
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);

        // 2. 设置存储桶所在的地域 (Region)
        // 官方地域简称列表：https://cloud.tencent.com/document/product/436/6224
        ClientConfig clientConfig = new ClientConfig(new Region(region));

        // 3. 组装并生成 COS 客户端实例
        return new COSClient(cred, clientConfig);
    }
}