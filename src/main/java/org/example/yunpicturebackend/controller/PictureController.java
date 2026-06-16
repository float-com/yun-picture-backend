package org.example.yunpicturebackend.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.annotation.AuthCheck;
import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.DeleteRequest;
import org.example.yunpicturebackend.common.ResultUtils;
import org.example.yunpicturebackend.constant.UserConstant;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.model.dto.picture.*;
import org.example.yunpicturebackend.model.entity.Picture;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.enums.PictureReviewStatusEnum;
import org.example.yunpicturebackend.model.vo.PictureTagCategory;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.example.yunpicturebackend.service.PictureService;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


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
    //@AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 权限拦截：仅允许具有管理员角色的用户访问此接口，防止普通用户恶意调用上传耗费云存储
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

    /**
     * 通过 URL 上传图片（统一处理新增和重新上传替换）
     * <p>
     * 业务场景：用户提供网络图片的公网 URL，系统将其抓取并转存到自己的云存储中。
     * 既支持全新的抓取上传，也支持对已存在图片记录的物理文件进行更新替换。
     * <p>
     * 【设计原理】参数绑定：
     * 这里必须在 PictureUploadRequest 前使用 @RequestBody。因为前端是以 application/json 格式提交的数据
     * （而非 multipart/form-data），Spring MVC 需要借助 HttpMessageConverter 将请求体中的 JSON 字符串反序列化为 Java 对象。
     *
     * @param pictureUploadRequest 前端提交的 JSON 格式请求参数，核心包含待抓取的网络图片地址 fileUrl，以及可选的 id 字段（用于区分是“全新上传”还是“更新替换”）。
     * @param request              Servlet 原生 HTTP 请求对象，用于从中提取当前会话（Session）或 Token，进而获取登录态。
     * @return 统一返回体包装的 PictureVO 视图对象，包含转存后的公网访问 URL 和各项解析出来的元数据。
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {

        // 1. 获取当前登录用户信息（基于 HTTP Session 或 Token）
        // 如果未登录，底层的 getLoginUser 会抛出未授权异常，交由全局异常处理器拦截处理
        User loginUser = userService.getLoginUser(request);

        // 2. 从请求体中提取网络图片的公网链接
        String fileUrl = pictureUploadRequest.getFileUrl();

        // 3. 调度 Service 层执行核心上传及入库逻辑
        // 此处将 URL 字符串作为 inputSource 传入，底层方法会根据输入源类型自动适配 URL 抓取策略，完成云端转存与数据库持久化
        PictureVO pictureVO = pictureService.uploadPicture(fileUrl, pictureUploadRequest, loginUser);

        // 4. 将最终生成的脱敏视图对象包装为全局统一的标准 JSON 格式并返回
        return ResultUtils.success(pictureVO);
    }




    /**
     * 删除图片接口
     *
     * @PostMapping: RESTful 风格的路由注解。
     * 架构考量：在绝对标准的 REST 规范中，删除应使用 @DeleteMapping。但在许多大厂实践中，
     * 为了规避某些老旧网关/防火墙对 DELETE 请求的拦截，或者为了统一使用请求体 (Body) 传递扩展参数，往往统一妥协使用 @PostMapping。
     *
     * @param deleteRequest 包含待删除图片 ID 的请求体，封装为 DTO 以便后续无缝增加其他参数
     * @param request       Tomcat 注入的原生 HTTP 请求对象，核心作用是用来校验当前的登录态
     * @return 包装在统一响应体 BaseResponse 中的布尔值，标识是否删除成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(
            /*
             * @RequestBody: 核心反序列化注解。
             * 拦截前端发来的 JSON 格式请求体，自动将其装配为底层的 DeleteRequest 数据传输对象。
             */
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {

        // 1. 防御性拦截：确保请求载荷合法
        // 任何依赖 ID 的操作，第一步永远是防空和防非负数。如果这里不拦截，查库时可能会引发全表扫描或底层报错。
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 提取当前上下文的登录用户
        // 核心安全准则：永远不要相信前端传来的 userId，一定要从后端的 Session/Token 中去取，防止水平越权。
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();

        // 3. 校验目标资源是否存在
        // 经典的“查后删”逻辑：先确认数据库里确实有这条记录，才能进行后续的权限校验。
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 越权防御（核心安全机制）
        // 业务规则：这条图片到底谁能删？只有“图片的创建者”或者“全站管理员”可以。
        // 如果两者都不是，直接抛出无权限异常，阻断恶意用户的越权删除尝试。
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 5. 执行物理/逻辑删除
        // 委托给 MyBatis-Plus 的 IService 执行删除，并严格校验底层受影响的行数。
        boolean result = pictureService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 6. 统一格式返回
        return ResultUtils.success(true);
    }

    /**
     * 更新图片接口（仅管理员可用）
     *
     * @AuthCheck: 自定义权限拦截注解。
     * 架构考量：利用 AOP (面向切面编程) 将权限校验逻辑与业务逻辑剥离。
     * 方法一旦被打上该注解，在进入 Controller 逻辑前就会被切面拦截，如果当前登录用户没有 ADMIN_ROLE 角色，会直接抛出异常，无需在业务代码中反复手写 if(isAdmin) 判断。
     *
     * @param pictureUpdateRequest 包含前端传入的最新图片属性的请求对象 (DTO)
     * @return 统一响应体，更新成功返回 true
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {

        // 1. 基础参数防御
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. DTO 转 Entity (核心隔离设计)
        // Controller 层的职责之一就是“数据转换”。前端传来的 DTO 和数据库对应的 Entity 必须隔离，
        // 我们通过 BeanUtils 将 DTO 中的属性浅拷贝到 Entity 中准备入库。
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);

        // 3. 特殊字段处理 (JSON 序列化)
        // 数据库通常没有直接的 List 字段，需要将其序列化为 JSON 字符串存储。
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));

        // 4. 数据业务校验
        // 委托给 Service 层进行核心字段的非空、长度等严格校验。
        pictureService.validPicture(picture);

        // 5. 校验目标记录存在性
        long id = pictureUpdateRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        //补充审核参数【非常重要】
        User loginUser = userService.getLoginUser(request);
        pictureService.fillReviewParams(picture,loginUser);


        // 6. 覆盖更新并响应
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 根据 id 获取图片原始信息（仅管理员可用）
     * <p>返回底层全量字段（包含物理文件路径等敏感信息），不作脱敏处理。</p>
     *
     * @param id      图片 ID
     * @param request HTTP 请求对象
     * @return 包含图片原始实体信息的统一响应体
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 1. 查询数据库获取底层实体
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 2. 封装并返回原始数据
        return ResultUtils.success(picture);
    }

    /**
     * 根据 id 获取图片视图对象（供 C 端普通用户使用）
     * <p>对底层实体进行脱敏及关联信息组装，适用于前端详情展示。</p>
     *
     * @param id      图片 ID
     * @param request HTTP 请求对象
     * @return 包含图片脱敏视图对象 (PictureVO) 的统一响应体
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 1. 查询数据库获取底层实体
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        // 2. 转换为视图对象并返回
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }

    /**
     * 分页获取图片原始列表（仅管理员可用）
     * <p>支持复杂条件检索，返回未脱敏的全量字段，用于后台管理系统。</p>
     *
     * @param pictureQueryRequest 包含分页参数和检索条件的请求体
     * @return 包含图片原始数据分页对象的统一响应体
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 构建动态查询条件并执行分页查询
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }


    /**
     * 分页获取图片视图列表接口（供 C 端普通用户使用）
     *
     * 为什么分出 /page 和 /page/vo 两个接口？
     * 架构考量：读写分离原则。后台管理员需要看底层全量字段（如物理文件路径、逻辑删除标识等），调 /page；
     * C 端用户只需要看展示信息，调 /page/vo，后者会在 Service 层经过严格的脱敏和关联用户信息处理，防止数据泄露。
     *
     * @param pictureQueryRequest 包含分页参数和复杂检索条件的 DTO
     * @param request             用于提取当前登录态
     * @return 包含脱敏数据分页对象 (Page<PictureVO>) 的统一响应体
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(
            @RequestBody PictureQueryRequest pictureQueryRequest,
            HttpServletRequest request) {

        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 1. 安全防御：防止恶意爬虫
        // 如果不限制 size，黑客可能发送 size=100000 的请求，一次性拉取全库数据，导致服务器 OOM (内存溢出) 或数据库崩溃。
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 新增：普通用户默认只能查看已过审的数据【非常重要的权限隔离】
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 2. 底层数据检索
        // 委托给 Service 层，将 DTO 解析为 MyBatis-Plus 的 QueryWrapper 动态 SQL 语句，并在数据库中执行分页。
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 3. 视图模型装配 (解决 N+1 问题)
        // 在 getPictureVOPage 方法内部，不仅会将 Picture 转换为 PictureVO，
        // 还会采用批量查询的方式查出所有作者信息并组装，保证高性能的视图渲染。
        return ResultUtils.success(pictureService.getPictureVOPage(picturePage, request));
    }

    /**
     * 编辑图片接口（供普通用户使用）
     *
     * Edit (编辑) 和 Update (更新) 的核心区别是什么？
     * 1. 业务对象不同：Update 面向管理员，允许全量覆盖；Edit 面向普通用户，通常只允许修改系统开放的部分字段（如名字、标签）。
     * 2. 权限维度不同：Update 靠 @AuthCheck 一刀切拦截；Edit 必须在代码深处校验“你是不是这张图片的主人”。
     *
     * @param pictureEditRequest  包含修改字段的请求 DTO
     * @param request             HTTP 原生请求对象，用于鉴权
     * @return 统一响应体，编辑成功返回 true
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest, HttpServletRequest request) {

        // 1. 基础防空
        if (pictureEditRequest == null || pictureEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. DTO 映射及特殊字段处理
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        //注意将List 转为 String
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));

        // 业务补充：只要发生编辑，就刷新最后编辑时间，方便后续做“近期修改”排序或缓存失效策略
        picture.setEditTime(new Date());

        // 3. 合法性检查
        pictureService.validPicture(picture);

        User loginUser = userService.getLoginUser(request);

        //补充审核参数【非常重要】
        pictureService.fillReviewParams(picture,loginUser);
        long id = pictureEditRequest.getId();

        // 4. 提取原数据准备越权校验
        Picture oldPicture = pictureService.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 5. 核心越权防御
        // 逻辑：如果“原图片的归属者”不是“当前登录用户”，并且“当前登录用户”也不是“管理员”，则拒绝访问。
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 6. 落库保存
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 获取预设的图片标签与分类选项列表（业务初期可以先这样写）
     * <p>
     * 【接口描述】供前端在“上传图片”或“图片大盘筛选”表单中，渲染下拉菜单或标签选择器时使用。
     * 【架构考量】
     * 1. 网络层优化：将“标签”和“分类”这两个独立的字典数据合并在同一个接口中下发，有效减少了前端的 HTTP 请求次数（从 2 次降为 1 次），提升了页面首屏渲染性能。
     * 2. 演进说明：当前系统处于初创阶段，采用硬编码 (Hardcode) 的形式快速提供基础枚举数据。在后续迭代中，这部分数据通常会迁移到数据库的“数据字典表”中动态维护，并结合 Redis 缓存以保障高并发读取。
     * 【权限控制】基础公共接口，所有用户（含未登录的访客）均可访问。
     *
     * @return BaseResponse<PictureTagCategory> 包含预设标签列表和分类列表的聚合响应体
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        // 硬编码预设标签
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        // 硬编码预设分类
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }



    /**
     * 图片审核接口（仅供后台管理员使用）
     * <p>
     * 【业务场景】系统内容安全的最后一道防线。决定用户上传的图片是否能够流入公共图库进行公开展示。
     * 【权限控制】严格受限接口。通过 @AuthCheck(mustRole = ADMIN_ROLE) 注解进行 AOP 级别的“一刀切”拦截，强制要求调用方必须具备管理员角色。
     * 【架构考量】
     * 为什么不在 Controller 层做具体的枚举比对和数据库校验？
     * MVC 架构中 Controller 应当保持“薄”的特性，主要负责路由和基础网关工作。
     * 此处仅做最基础的 HTTP 防空，并提取可信的登录态上下文（loginUser）。
     * 至于“防重复审核（幂等性）”、“枚举值合法性”、“数据的按需更新”等纯业务逻辑，统一下沉至 Service 层的方法内聚处理。
     *
     * @param pictureReviewRequest 包含目标审核状态、驳回原因等核心参数的请求 DTO
     * @param request              HTTP 原生请求对象，用于提取当前操作的管理员凭证（Session/Token）
     * @return BaseResponse<Boolean> 统一响应体，审核操作执行成功则返回 true
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                 HttpServletRequest request) {
        // 1. 基础防空拦截：将连请求体都没传的非法请求直接拒之门外
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 提取上下文：从可信的后端环境中安全获取当前登录的管理员信息，为后续记录“审核人(reviewerId)”提供防篡改的数据源
        User loginUser = userService.getLoginUser(request);

        // 3. 委派执行：将核心的审核逻辑（查库、校验、幂等防重、修改落库）交由 Service 层处理
        pictureService.doPictureReview(pictureReviewRequest, loginUser);

        // 4. 封装并返回标准响应体
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取并创建图片
     * <p>仅限具有管理员权限的用户调用</p>
     *
     * @param pictureUploadByBatchRequest 包含搜索词和数量的请求体
     * @param request                     HttpServletRequest，用于获取当前登录用户态
     * @return 成功抓取和上传的图片数量
     */
    @PostMapping("/upload/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE) // 权限校验：必须是管理员角色
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request
    ) {
        // 1. 校验请求参数是否为空
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);

        // 2. 从 request 中获取当前登录的用户信息
        User loginUser = userService.getLoginUser(request);

        // 3. 调用 Service 层执行批量抓取并获取成功条数
        int uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);

        // 4. 返回统一包装的成功响应结果
        return ResultUtils.success(uploadCount);
    }



}