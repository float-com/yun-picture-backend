package org.example.yunpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * 本地图片上传的具体实现类
 * <p>
 * 业务场景：处理前端直接传递的物理图片文件（MultipartFile）。
 * 继承模板父类，实现了本地特有的参数校验、文件名提取和流转换逻辑。
 */
@Service
public class FilePictureUpload extends PictureUploadTemplate {

    /**
     * 校验前端传入的本地物理图片是否合法
     *
     * @param inputSource 统一抽象的输入源（此处强转为 MultipartFile）
     */
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 1. 校验文件大小
        // 策略：限制单张图片体积不得超过 2MB。防止恶意消耗存储容量和下行 CDN 流量
        long fileSize = multipartFile.getSize();
        final long ONE_M = 1024 * 1024L;
        ThrowUtils.throwIf(fileSize > 2 * ONE_M, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");

        // 2. 校验文件后缀类型
        // 策略：采用白名单机制放行常规图片格式。严防 .php, .jsp, .sh 等木马脚本伪装上传
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, "文件类型错误");
    }

    /**
     * 提取本地上传的原始文件名
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 处理内存文件流并写入本地临时物理文件
     *
     * @param inputSource 前端传入的 MultipartFile 对象
     * @param file        由模板方法预先创建的空临时文件
     * @throws Exception 磁盘 IO 异常
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        // 将 Spring 托管的内存流/分片流直接安全转存到本地磁盘，以供后续 COS SDK 处理
        multipartFile.transferTo(file);
    }
}