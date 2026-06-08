package org.example.yunpicturebackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.annotation.AuthCheck;
import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.ResultUtils;
import org.example.yunpicturebackend.constant.UserConstant;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.example.yunpicturebackend.service.PictureService;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;


/**
 * 图片模块对外 HTTP 接口 (Controller 层)
 * <p>
 * 核心职责：
 * 1. 负责接收前端的 HTTP 请求，并进行参数的初步绑定与校验。
 * 2. 处理应用层的权限控制（如使用 AOP 切面注解拦截非法请求）。
 * 3. 调度下层的 Service 业务代码执行具体的业务逻辑。
 * 4. 将业务执行结果统一封装为 BaseResponse 返回给前端。
 */
@Slf4j
@RestController
@RequestMapping("/picture")
public class PictureController {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    /**
     * 上传图片（统一处理新增和重新上传替换）
     * <p>
     * 业务场景：后台管理人员上传新的图库素材，或对已存在的图片进行物理文件的替换更新。
     * <p>
     * 【设计原理】参数绑定：
     * 这里不能在 PictureUploadRequest 前使用 @RequestBody。因为前端是以 multipart/form-data 格式提交的数据（既有文件流，又有普通键值对），
     * Spring MVC 会自动将 form-data 中的普通字段通过 Setter 方法映射到 pictureUploadRequest 对象中。
     *
     * @param multipartFile        前端通过表单提交的文件流。@RequestPart("file") 指定了前端对应表单的 name 属性值。
     * @param pictureUploadRequest 图片上传扩展参数（主要携带可选的 id 字段，用于区分是“全新上传”还是“更新替换”）。
     * @param request              Servlet 原生 HTTP 请求对象，用于从中提取当前会话（Session）或 Token，进而获取登录态。
     * @return 统一返回体包装的 PictureVO 视图对象，包含图片的公网访问 URL 和各项解析出来的元数据。
     */
    @PostMapping("/upload")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 权限拦截：仅允许具有管理员角色的用户访问此接口，防止普通用户恶意调用上传耗费云存储
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {

        // 1. 获取当前登录用户信息（基于 HTTP Session 或 Token）
        // 如果未登录，底层的 getLoginUser 会直接抛出未授权的业务异常，全局异常处理器会将其捕获并返回给前端
        User loginUser = userService.getLoginUser(request);

        // 2. 调度 Service 层执行核心上传及入库逻辑
        // 将复杂的文件处理、第三方云存储交互、数据库 CRUD 统统交给 Service 层完成，保证 Controller 的轻量化
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);

        // 3. 将最终生成的脱敏视图对象包装为全局统一的标准 JSON 格式并返回
        return ResultUtils.success(pictureVO);
    }

}