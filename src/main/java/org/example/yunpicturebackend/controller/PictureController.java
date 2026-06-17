package org.example.yunpicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;


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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 构建 JVM 级本地缓存 (L1 Cache)
     * <p>
     * 【架构演进：为什么在 Redis 之外还需要本地缓存？】
     * 在极高并发（如秒杀、C端首页高频拉取）场景下，纵然有 Redis (L2 Cache) 加持，网络 I/O 的开销
     * 以及 Redis 集群面对“极端热点 Key”时的性能瓶颈依然存在。
     * 引入基于 Caffeine（当前 Java 生态下性能天花板的本地缓存框架）的 JVM 缓存，
     * 能够将部分高频读请求直接拦截在应用服务器（Tomcat）内部，实现“纳秒级”的极致响应，彻底解放 Redis 与 MySQL。
     * </p>
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            // 【性能调优：内存预分配】
            // 设置底层哈希表的初始容量。在系统刚启动或突发流量涌入时，能有效避免
            // 因底层结构频繁扩容（Rehash）而导致的性能抖动和 CPU 资源消耗。
            .initialCapacity(1024)

            // 【系统自保：防 OOM (内存溢出) 机制】
            // 强制设定当前 JVM 堆内存中最多允许驻留的缓存条目数上限为 1 万条。
            // 当数据量逼近阈值时，Caffeine 会基于其强大的 W-TinyLFU 算法，
            // 极其精准地剔除“冷数据”和“低频数据”，确保应用服务永远不会因缓存无限膨胀而崩溃。
            .maximumSize(10000L)

            // 【数据一致性与空间回收】
            // 采用“写入后绝对过期”的淘汰策略（TTL 为 5 分钟）。
            // 业务考量：在“高性能”与“强一致性”之间取得平衡。容忍业务数据存在最多 5 分钟的延迟（伪静态化），
            // 同时确保那些曾经是热点、但现在已无人问津的数据能够自动释放，归还宝贵的内存空间。
            .expireAfterWrite(5L, TimeUnit.MINUTES)

            // 构建并初始化缓存实例
            .build();


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
     * 现在暂不使用当前接口【改为使用listPictureVOByPageWithCatch这个多级缓存架构的查询接口】
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
     * 分页获取图片视图列表接口（供 C 端普通用户使用）[Redis缓存架构]
     * 此接口仅用于演示Redis缓存优化，不投入项目使用。
     * <p>
     * 【架构考量：为什么引入 Redis 缓存？】
     * C 端首页的图片展示是典型的高频读、低频写的“热点接口”。如果任由海量并发请求直接打穿到 MySQL，
     * 执行复杂的条件过滤和分页查询，极易导致数据库 CPU 飙升甚至宕机。
     * 引入 Redis 作为前置缓冲层，能实现百倍级的性能提升（QPS）并大幅降低响应延迟。
     * </p>
     *
     * @param pictureQueryRequest 包含分页参数和复杂检索条件的 DTO
     * @param request             用于提取当前登录态
     * @return 包含脱敏数据分页对象 (Page<PictureVO>) 的统一响应体
     */
    @PostMapping("/list/page/vo/Redis")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithRedis(
            @RequestBody PictureQueryRequest pictureQueryRequest,
            HttpServletRequest request) {

        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 1. 安全防御：防止恶意爬虫
        // 如果不限制 size，黑客可能发送 size=100000 的请求，一次性拉取全库数据，导致服务器 OOM (内存溢出) 或数据库崩溃。
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 2. 权限隔离：普通用户可见性控制
        // 新增：普通用户默认只能查看已过审的数据【非常重要的越权防御】
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 3. 构建分布式缓存 Key (Cache Key Generation)
        // 策略：将用户动态提交的查询条件（DTO）完整序列化为 JSON 字符串。
        // 优化：由于 JSON 字符串可能极长，直接作为 Redis Key 会严重浪费内存且影响匹配效率。
        // 因此使用 MD5 算法对其进行信息摘要，生成 32 位固定长度的十六进制 Hash 字符串。
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String redisKey = "yunpicture:listPictureVOByPage:" + hashKey;

        // 4. 读缓存 (Read-Through)
        // 尝试从 Redis 中获取已缓存的当前页数据
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        String cachedValue = valueOps.get(redisKey);
        if (cachedValue != null) {
            // 缓存命中 (Cache Hit)：直接反序列化为 Page 对象并短路返回，彻底解放底层数据库。
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 5. 回源查询底层数据 (Cache Miss)
        // 缓存未命中时，委托给 Service 层，将 DTO 解析为 MyBatis-Plus 的 QueryWrapper 动态 SQL 语句，查出原始物理模型。
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 6. 视图模型装配 (解决 N+1 问题)
        // 将底层的 Picture 转换为对外展示的 PictureVO，内部会批量查询并装配作者等关联信息。
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage,request);

        // 7. 写缓存 (Write-back) 与 容错保护机制
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 【架构考量：防缓存雪崩 (Cache Avalanche)】
        // 策略：基础过期时间（300秒，即5分钟） + 随机扰动时间（0-300秒）。
        // 目的：打散这批缓存的过期节点，保证总 TTL 在 5~10 分钟之间浮动。
        // 避免同一时间点大量高并发的缓存 Key 同时失效，导致瞬间所有请求全部涌向 MySQL 引发雪崩。
        int cacheExpireTime = 300 +  RandomUtil.randomInt(0, 300);
        valueOps.set(redisKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // 8. 返回最终视图
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片视图列表接口（供 C 端普通用户使用）[纯本地缓存架构版]
     * 此接口仅用于演示本地缓存优化，不投入项目使用。
     * <p>
     * 【架构演进：为什么从 Redis 降级/切换为本地缓存？】
     * 在极端的“读多写少”且“数据一致性要求不高”的场景下（例如 C 端默认首页的推荐流），
     * 即便是 Redis 的网络 I/O 延迟和序列化开销也可能成为性能瓶颈。
     * 引入基于 JVM 内存的本地缓存（如 Caffeine，L1 Cache），能实现无网络损耗的“纳秒级”响应，
     * 作为系统的第一道防线，将绝大部分高频且重复的分页请求直接拦截在应用实例内部。
     * </p>
     *
     * @param pictureQueryRequest 包含分页参数和复杂检索条件的 DTO
     * @param request             用于提取当前登录态
     * @return 包含脱敏数据分页对象 (Page<PictureVO>) 的统一响应体
     */
    @PostMapping("/list/page/vo/LocalCache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithLocalCache(
            @RequestBody PictureQueryRequest pictureQueryRequest,
            HttpServletRequest request) {

        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 1. 安全防御：防止恶意爬虫与内存雪崩
        // 强制限制单页最大拉取数量。若不加限制，黑客请求 size=100000 极易引发跨网络海量数据传输，
        // 进而导致数据库崩溃或当前 JVM 实例 OOM（内存溢出）。
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 2. 权限隔离：数据可见性控制
        // 【核心越权防御】强行覆盖前端传入的状态码，确保普通用户永远只能看到“已过审(PASS)”的健康内容。
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 3. 构建本地缓存 Key (Cache Key Generation)
        // 策略：将动态检索条件 DTO 序列化为 JSON。
        // 内存调优：由于 JSON 字符串较长，直接作为 Key 会极大地消耗宝贵的 JVM 堆内存。
        // 此处采用 MD5 算法进行信息摘要，生成 32 位固定长度的十六进制字符串，兼顾了唯一性与极高的内存利用率。
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "listPictureVOByPage:" + hashKey;

        // 4. 探查一级缓存 (L1 Cache Read-Through)
        // 尝试直接从本地内存（Caffeine）中读取缓存的分页数据
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 【缓存命中 (Cache Hit)】：直接反序列化为 Page 对象并短路返回，全流程零数据库/网络 I/O 消耗。
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 5. 回源查询底层数据 (Cache Miss)
        // 缓存未命中时，委托给 Service 层，将 DTO 解析为 MyBatis-Plus 的 QueryWrapper 动态 SQL 语句，查出原始物理模型。
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 6. 视图模型装配 (解决 N+1 问题)
        // 将底层的 Picture 转换为对外展示的 PictureVO，内部会批量查询并装配作者等关联信息。
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage,request);

        // 7. 写入本地缓存 (Write-back)
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);

        // 将最新组装好的分页视图 JSON 塞入本地内存，生命周期由 Caffeine 后台线程静默接管
        LOCAL_CACHE.put(cacheKey, cacheValue);

        // 8. 返回最终视图
        return ResultUtils.success(pictureVOPage);
    }




    /**
     * 分页获取图片视图列表接口（供 C 端普通用户使用）[多级缓存架构版]
     * 此接口已废弃，其核心业务逻辑已重构并下沉至 Service 层统一管理。
     * <p>
     * 【架构演进：为什么引入 多级缓存 (L1 Caffeine + L2 Redis)？】
     * 1. 极致性能 (L1)：C端首页的流量极其庞大。将高频访问的首页数据缓存在 JVM 内存 (Caffeine) 中，
     * 可实现真正的“零网络损耗”和“纳秒级”响应，作为抵御洪峰的第一道防线。
     * 2. 分布式共享与容底 (L2)：本地缓存容量有限且各节点不共享。当 L1 未命中时，请求下沉到 Redis。
     * Redis 作为二级缓存，容量更大且全集群共享，避免所有应用节点的穿透请求直接打垮 MySQL。
     * 3. 动态回填机制：当 Redis 命中时，自动将数据回写到 L1，实现热点数据的自适应预热。
     * </p>
     *
     * @param pictureQueryRequest 包含分页参数和复杂检索条件的 DTO
     * @param request             用于提取当前登录态
     * @return 包含脱敏数据分页对象 (Page<PictureVO>) 的统一响应体
     */
    //@PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCatch(
            @RequestBody PictureQueryRequest pictureQueryRequest,
            HttpServletRequest request) {

        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 1. 安全防御：防止恶意爬虫
        // 强制限制拉取数量，防止黑客通过 size=100000 引发跨网络海量数据传输，导致 OOM 或 DB 崩溃。
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 2. 权限隔离：数据可见性控制
        // 普通用户默认只能查看已过审的数据【核心越权防御】
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        // 3. 构建多级缓存共享 Key (Cache Key Generation)
        // 将 DTO 序列化并进行 MD5 摘要，生成 32 位固定长度 Hash，兼顾唯一性与内存利用率。
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        String cacheKey = "yunpicture:listPictureVOByPage:" + hashKey;

        // =========================================================
        // 多级缓存核心链路开始 (L1 -> L2 -> DB)
        // =========================================================

        // 4. 探查一级缓存 (L1 Cache - Caffeine)
        // 第一道防线：纯内存读取，无网络 I/O 损耗
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);
        if (cachedValue != null) {
            // 命中 L1：直接反序列化并短路返回，性能达到极致
            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 5. 探查二级缓存 (L2 Cache - Redis)
        // 第二道防线：跨节点共享缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        cachedValue = valueOps.get(cacheKey);
        if (cachedValue != null) {
            // 命中 L2：说明该数据是热点，但在当前 JVM 实例的 L1 中已过期或未被加载过。
            // 【缓存预热/回写】：立即将数据塞入当前节点的 L1，保护 Redis 免受后续同类高频请求冲击。
            LOCAL_CACHE.put(cacheKey, cachedValue);

            Page<PictureVO> cachedPage = JSONUtil.toBean(cachedValue, Page.class);
            return ResultUtils.success(cachedPage);
        }

        // 6. 回源查询数据库 (Cache Miss - MySQL)
        // 前两级缓存均告破，请求真正触达底层数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size),
                pictureService.getQueryWrapper(pictureQueryRequest));

        // 视图模型装配 (解决 N+1 问题)
        Page<PictureVO> pictureVOPage = pictureService.getPictureVOPage(picturePage, request);

        // 7. 缓存双写与容错保护 (Write-back L1 & L2)
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);

        // 7.1 更新 L1 缓存（生命周期由 Caffeine 初始化配置的 TTL 接管）
        LOCAL_CACHE.put(cacheKey, cacheValue);

        // 7.2 更新 L2 缓存
        // 【架构考量：防缓存雪崩 (Cache Avalanche)】
        // 即使采用多级缓存，仍需保留过期时间随机扰动机制（基础 5 分钟 + 0~5分钟随机波动）。
        // 强力打散 Redis 中大批热点 Key 的集体失效时间，防止极端情况下瞬间打穿到底层 MySQL。
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);

        // 8. 返回最终视图
        return ResultUtils.success(pictureVOPage);
    }

    /**
     * 分页获取图片视图列表（C 端多级缓存版）
     * <p>
     * 架构说明：
     * 遵循控制层与业务层职责分离的设计原则。Controller 层仅负责请求接收与统一响应封装；
     * L1/L2 多级缓存读取、数据库回源、VO 数据组装及缓存回写等完整业务链路均下沉至 Service 层，
     * 从而有效降低层级耦合，提升代码的可复用性、可测试性与后续维护效率。
     * </p>
     *
     * @param pictureQueryRequest 包含分页参数与复杂筛选条件的查询请求 DTO
     * @param request             HTTP 请求对象（用于提取当前用户登录态参与 VO 装配）
     * @return BaseResponse       统一响应体，包含脱敏后的图片分页视图对象 (Page<PictureVO>)
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(
            @RequestBody PictureQueryRequest pictureQueryRequest,
            HttpServletRequest request) {
        Page<PictureVO> pictureVOPage = pictureService.listPictureVOByPageWithCache(pictureQueryRequest, request);
        return ResultUtils.success(pictureVOPage);
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
