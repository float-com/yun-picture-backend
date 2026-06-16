package org.example.yunpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

/**
 * URL 网络图片上传的具体实现类
 * <p>
 * 业务场景：用户提供网络图片 URL，系统后台自动抓取该图片并转存到自有云存储中。
 * 继承模板父类，实现了网络源特有的 URL 连通性探测、防 SSRF 校验和远程下载逻辑。
 */
@Service
public class UrlPictureUpload extends PictureUploadTemplate {

    /**
     * 校验远程网络图片流是否合法
     * <p>
     * 核心设计：在正式发起全量文件下载、消耗服务器公网带宽前，先进行极轻量级的探测与拦截。
     *
     * @param inputSource 统一抽象的输入源（此处强转为 String 类型的 URL）
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");

        try {
            // 1. 验证 URL 格式：防范非法字符或恶意伪造的链接导致后端解析崩溃
            new URL(fileUrl);
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }

        // 2. 校验 URL 协议（极度重要）
        // 策略：严防 file://, ftp:// 等内部协议，防止黑客借此漏洞触发 SSRF（服务器端请求伪造）探测内网
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");

        // 3. 发送轻量级 HEAD 请求以验证文件状态及提取元数据
        // 优势：HEAD 请求只获取响应头，不下载庞大的响应体，能以极低代价提前排雷
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            // 若未正常返回（如 404 不存在 / 500 远端异常），代表远端图片不可用，直接放行交由后续业务处理或直接中断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }

            // 4. 校验远端文件类型
            // 策略：通过 Header 的 Content-Type 采用白名单机制放行常规图片。严防恶意伪装文件
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }

            // 5. 校验远端文件大小
            // 策略：通过 Header 的 Content-Length 提取远端文件体积。防止超大文件导致后端内存溢出或公网带宽被打满
            String contentLengthStr = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_MB = 2 * 1024 * 1024L; // 限制转存阈值为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } finally {
            // 释放网络连接资源，防止连接池耗尽
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * 提取网络上传的原始文件名
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        // 借助 Hutool 从 URL 路径的末尾提取真实的文件名（例如从 http://xxx.com/pic.jpg 提取出 pic）
        return FileUtil.mainName(fileUrl);
    }

    /**
     * 抓取远程网络文件并写入本地临时物理文件
     *
     * @param inputSource 前端传入的图片 URL 字符串
     * @param file        由模板方法预先创建的空临时文件
     * @throws Exception 网络下载 IO 异常
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        // 调用底层的 HTTP 客户端，将远程公网上的图片流持续写入到本地初始化的物理文件中
        HttpUtil.downloadFile(fileUrl, file);
    }
}