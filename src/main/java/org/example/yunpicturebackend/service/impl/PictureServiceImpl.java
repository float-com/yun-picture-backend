package org.example.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.config.CosClientConfig;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.manager.CosManager;
import org.example.yunpicturebackend.manager.FileManager;
import org.example.yunpicturebackend.manager.upload.FilePictureUpload;
import org.example.yunpicturebackend.manager.upload.PictureUploadTemplate;
import org.example.yunpicturebackend.manager.upload.UrlPictureUpload;
import org.example.yunpicturebackend.model.dto.file.UploadPictureResult;
import org.example.yunpicturebackend.model.dto.picture.*;
import org.example.yunpicturebackend.model.entity.Picture;
import org.example.yunpicturebackend.model.entity.Space;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.enums.PictureReviewStatusEnum;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.example.yunpicturebackend.model.vo.UserVO;
import org.example.yunpicturebackend.service.PictureService;
import org.example.yunpicturebackend.mapper.PictureMapper;
import org.example.yunpicturebackend.service.SpaceService;
import org.example.yunpicturebackend.service.UserService;
import org.jsoup.Jsoup;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图片信息服务实现类 (Service Implementation)
 * <p>
 * 职责：处理与图片相关的核心业务逻辑。继承自 MyBatis-Plus 的 ServiceImpl，默认拥有基础的 CRUD 能力。
 * @author 24042
 * @description 针对表【picture(图片信息表)】的数据库操作Service实现
 * @createDate 2026-06-08 19:33:54
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService{

    /**
     * @deprecated 旧版本地文件上传管理器已废弃，仅保留作历史实现对照。
     * 该实现将文件上传、校验、解析和 COS 存储逻辑集中在一个类中，扩展 MultipartFile 与 URL 上传时容易产生重复代码。
     * 新上传链路请使用基于模板方法的 {@link PictureUploadTemplate}，并根据输入源选择
     * {@link FilePictureUpload} 或 {@link UrlPictureUpload}。
     */
    @Deprecated
    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 图片分页 VO 缓存 Key 前缀。
     * <p>
     * 所有 /list/page/vo/cache 查询生成的 Redis Key 都以该前缀开头，写操作成功后按此前缀统一清理，
     * 避免上传、删除、编辑、审核后前端继续命中旧列表缓存。
     */
    private static final String PICTURE_VO_PAGE_CACHE_KEY_PREFIX = "yunpicture:listPictureVOByPage:";

    /**
     * 构建 JVM 级本地缓存 (L1 Cache)
     * <p>
     * 业务场景：图片分页列表属于典型的读多写少、高频访问接口。将热点分页结果缓存在当前应用实例内存中，
     * 可以在 Redis 之前先拦截一批重复请求，减少网络 I/O 与二级缓存压力。
     * </p>
     */
    private final Cache<String, String> LOCAL_CACHE = Caffeine.newBuilder()
            // 初始容量：为常见热点分页请求预留空间，降低运行期扩容带来的性能抖动
            .initialCapacity(1024)
            // 最大容量：限制 JVM 内存占用，避免缓存条目无限增长导致 OOM
            .maximumSize(10000L)
            // 写入后过期：与当前 Controller 中的多级缓存策略保持一致，兼顾性能与数据时效性
            .expireAfterWrite(5L, TimeUnit.MINUTES)
            .build();

    /**
     * 上传图片（统一处理新增和更新逻辑）
     * <p>
     * 业务流程：
     * 1. 权限与空间校验：确保用户已登录，若指定了图库空间，需严格校验空间的归属权。
     * 2. 更新校验：若携带图片 ID，则防御性校验该记录在数据库中是否存在，并校验空间一致性。
     * 3. 云端上传：根据 inputSource 类型选择对应的上传策略（文件或 URL），按“公共图库”或“私有空间”动态隔离底层存储目录。
     * 4. 数据装配：提取云端返回的元数据（宽高、大小、格式等）组装数据库实体。
     * 5. 持久化：利用 saveOrUpdate 特性，根据 ID 的有无，自动执行 INSERT 或 UPDATE。
     *
     * @param inputSource          图片输入源（支持 MultipartFile 物理文件对象，或 String 类型的图片 URL 地址）
     * @param pictureUploadRequest 图片上传扩展参数（核心用于携带图片 id 区分新增或更新，以及 spaceId 区分所属空间）
     * @param loginUser            当前已认证的登录用户对象
     * @return PictureVO           上传并成功入库后，返回给前端的脱敏视图对象
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. 基础安全拦截：强制要求必须登录后才能执行上传，防范匿名用户恶意传图消耗云端流量和存储
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 1.5 目标图库空间校验 (核心隔离边界)
        // 业务逻辑：如果前端传了 spaceId，说明用户想把图片传到特定的私有空间里
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "指定的图库空间不存在");
            // 越权防御：只能往自己创建的空间里传图片，严禁张三把图片塞进李四的空间里
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权向该私有空间上传图片");
            }
        }

        // 2. 提取更新标识（判断当前操作是“全新上传”还是“对旧记录的物理文件替换”）
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 记录替换前的旧图片数据。只有重新上传替换文件时才会有值；
        // 后续必须等新图片信息成功落库后，再清理旧的云端物理文件，避免更新失败时误删仍在使用的旧文件。
        Picture oldPicture = null;

        // 3. 更新操作的防御性编程
        if (pictureId != null) {
            // 3.1 查库获取历史记录
            oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在或已被删除");

//      ///////////////////////////已弃用的代码 (保留供复盘参考)//////////////////////////////////////
//            // 如果是更新动作，必须查库校验待更新的图片记录是否真实存在。
//            // 避免前端传递虚假 ID 导致后续业务出现脏数据或空指针异常。
//            boolean exists = this.lambdaQuery()
//                    .eq(Picture::getId, pictureId)
//                    .exists();
//            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
//            // 注意：严格的生产环境中，这里可能还需要进一步校验该 pictureId 是否归属于当前 loginUser.getId()，防止水平越权（修改别人的图片）
//      ///////////////////////////////////////////////////////////////////////////////////////////

            // 3.2 水平越权与垂直越权校验 (注：本段代码正是为了解决上面注释中提到的“防止水平越权”问题而演进的)
            // 【校验逻辑】如果“原图片的归属者”不是“当前登录用户”（防水平越权），
            // 并且“当前登录用户”也不是“管理员”（保留管理员全局管理的特权），则果断拒绝访问。
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权编辑他人的图片");
            }

            // 3.3 空间一致性校验 (防数据漂移)
            // 业务约束：当前接口的定位是“重新上传替换文件”，不支持在替换文件的同时把图片转移到另一个空间。
            if (spaceId == null) {
                // 如果本次更新没传 spaceId，为了防止丢失原有的空间归属，必须平滑继承旧图片的 spaceId
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 如果本次更新传了 spaceId，必须严格校验它和旧图片原本所在的 spaceId 是否完全一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "更新图片时，空间标识不一致");
                }
            }

            // 3.4 审核状态重置 (可选业务逻辑，视具体需求而定)
            // 注意：如果用户修改了图片实体文件，通常意味着图片内容发生了变化，此时应当将图片的审核状态打回“待审核”。
            // 实际执行位置：下沉至 fillReviewParams() 方法内统管。
        }

        // 4. 动态构造云端对象存储的目录前缀 (实现云端文件的物理隔离)
        // 策略：按照目标空间类型进行顶层目录划分
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库：存放在 public 目录下，并按用户 ID 划分子目录
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 私有图库：存放在 space 目录下，严格按空间 ID 划分子目录，便于后续基于空间维度的数据管理与清理
            uploadPathPrefix = String.format("space/%s", spaceId);
        }

        // 5. 根据 inputSource 的类型动态调度底层的文件或 URL 上传策略，完成向第三方 COS 的安全上传与图片元数据（CI）解析
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 6. 数据搬运与装配：将上传成功后的 DTO 结果转换为数据库底层能识别的 Entity 实体
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        // 补充缩略图字段
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());

        // 6.5 解析与挂载图片名称
        // 优先采用外部扩展请求（pictureUploadRequest）中显式指定的图片名称；
        // 若外部未传递，则平滑降级，使用云端上传结果中解析出的默认名称。
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);

        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());

        // 绑定图片归属权，记录数据拥有者
        picture.setUserId(loginUser.getId());

        // 绑定所属图库空间
        picture.setSpaceId(spaceId);

        // 更新字段之前先补充审核参数【极其重要】
        this.fillReviewParams(picture, loginUser);

        // 7. 处理“更新操作”的特有字段逻辑
        if (pictureId != null) {
            // 补充主键 ID（MyBatis-Plus 识别到有主键时会触发 UPDATE 而不是 INSERT）
            picture.setId(pictureId);
            // 强制刷新业务层面的手动编辑时间
            picture.setEditTime(new Date());
        }

        // 8. 统一持久化入库
        // 机制：底层会自动判断 picture 对象的 id 是否为空，若为空执行 insert，非空则执行 updateById
        boolean result = this.saveOrUpdate(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败");

        // 图片列表接口使用了多级缓存；新增或替换图片成功后，必须主动清理列表缓存，避免前端继续看到旧分页结果。
        this.clearPictureVOPageCache();

        // 9. 将入库成功的实体对象转换为剔除了敏感字段的 VO 对象返回给前端渲染
        // 重新上传替换图片文件后，数据库记录已经指向新图片，此时再清理旧图片资源。
        // 这样可以保证“先上传新文件并更新记录，后删除旧文件”的安全顺序，避免新图保存失败时旧图已被删。
        if (pictureId != null && oldPicture != null) {
            this.clearPictureFiles(oldPicture);
        }

        return PictureVO.objToVo(picture);
    }


    /**
     * 构建图片查询的 MyBatis-Plus 包装类 (QueryWrapper)
     * <p>
     * 业务场景：将前端传递的查询请求对象转换为 MyBatis-Plus 可执行的数据库动态查询条件。
     * 包含对普通字段的精准/模糊查询、多字段组合的聚合搜索、JSON 数组的包含查询以及动态排序逻辑。
     *
     * @param pictureQueryRequest 前端传入的查询请求封装对象
     * @return 组装完毕的 {@link QueryWrapper<Picture>}
     */
    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();

        // 1. 基础防御：如果未传任何参数，直接返回空的 Wrapper（等同于查询全表）
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }

        // 2. 提取参数：从请求 DTO 中解构出所有需要参与条件组装的字段
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 审核与权限相关字段的动态查询构建
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        // 空间隔离相关字段：spaceId 指定私有空间，nullSpaceId 表示仅查询公共图库
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();

        // 3. 聚合搜索（多字段组合模糊查询）
        if (StrUtil.isNotBlank(searchText)) {
            // 业务逻辑：如果传入了全局搜索词，需在名称或简介中寻找匹配项。
            // 使用 and(qw -> qw.like().or().like()) 嵌套，生成的 SQL 为：AND (name LIKE '%xxx%' OR introduction LIKE '%xxx%')
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }

        // 4. 精确查询 (eq) 与 模糊查询 (like)
        // 技巧：利用 MyBatis-Plus 提供的方法首参 condition（返回 true 才拼接该条件），避免繁琐的 if 判空
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        // 动态拼接模糊查询 (like)：当审核驳回信息有值且不为空白字符时生效，方便后台溯源
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        // 动态拼接等值查询 (eq)：当 reviewStatus 不为空时生效
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        // 动态拼接等值查询 (eq)：当操作人 ID 不为空时生效，用于筛选特定审核员处理的图片
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // 动态拼接等值查询 (eq)：当空间 ID 不为空时，限定只查询指定私有空间下的图片
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        // 动态拼接空值查询：公共图库图片的 spaceId 必须为空，防止私有空间图片混入公开列表
        queryWrapper.isNull(nullSpaceId, "spaceId");


        // 5. JSON 数组字段查询 (tags)
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                // 业务逻辑：因为 tags 在数据库里可能是序列化后的 JSON 字符串（如 ["高清", "动漫"]）。
                // 为避免部分匹配（如搜“动”却匹配到了“动漫”），手动拼接双引号（"tag"）再进行 LIKE 包含查询。
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }

        // 6. 动态排序
        // 仅在传入了排序列 (sortField) 的情况下生效，通过判断 sortOrder 是否为前端约定的 "ascend" 来决定是升序还是降序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }

    /**
     * 将单张图片实体转换为视图对象 (VO)
     *
     * @param picture 原始图片实体
     * @param request HTTP 请求对象
     * @return 组装完毕的 PictureVO
     */
    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 1. 基础转换：将底层的 Picture 实体属性拷贝到 VO 中
        PictureVO pictureVO = PictureVO.objToVo(picture);

        // 2. 关联查询：获取并填充图片的创建者/上传者信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            // 查询底层用户实体
            User user = userService.getById(userId);
            // 将用户实体脱敏为 UserVO 并设置到图片 VO 中
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }

        return pictureVO;
    }

    /**
     * 将图片分页结果转换为视图对象 (VO) 分页结果
     * <p>
     * 核心逻辑：使用批处理思想，先提取所有的 userId 一次性查出用户，再在内存中组装，大幅降低数据库 IO。
     *
     * @param picturePage 原始图片分页结果
     * @param request     HTTP 请求对象
     * @return 组装完毕的分页 PictureVO
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());

        // 判空保护：如果当前页没有数据，直接返回空的翻页对象
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 1. 基础转换：将 List<Picture> 映射为 List<PictureVO>
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 2. 批量提取关联 ID：收集当前页所有图片对应的 userId（使用 Set 去重）
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());

        // 3. 批量查询与分组：一次性查出所有关联用户，并按 userId 进行分组（转化为 Map<userId, List<User>> 结构）
        // 避免了在 for 循环中频繁调用 userService.getById() 造成的 N+1 查询问题
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 4. 内存数据填充：遍历 pictureVOList，从 Map 中快速匹配并填充用户信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            // 如果在 Map 中找到了对应的用户信息，则取第一个匹配项（ID 通常是唯一的）
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 将用户实体转换为 UserVO 并赋值
            pictureVO.setUser(userService.getUserVO(user));
        });

        // 5. 组装结果：将转换和填充完毕的记录列表塞回分页对象中
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;
    }

//=================================================================================================
    /**
     * 分页获取图片视图列表（多级缓存版）
     * <p>
     * 业务场景：适用于 C 端首页和空间图片列表等高频读接口。
     * 将参数校验、权限兜底、多级缓存（L1+L2）、数据库回源、VO 装配及缓存回写等完整链路下沉至 Service 层，
     * 使 Controller 层专注请求接收与统一响应。
     * </p>
     *
     * @param pictureQueryRequest 图片分页查询请求参数
     * @param request             HTTP 请求对象（用于复用现有的 VO 装配逻辑）
     * @return Page<PictureVO>    脱敏后的图片分页视图
     */
    @Override
    public Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request) {
        // 1. 基础参数校验：防止异常超大分页请求穿透至底层
        validatePicturePageQueryRequest(pictureQueryRequest);

        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();

        // 2. 空间权限隔离：必须在生成缓存 Key 前完成权限约束补齐
        // 原因：多级缓存是跨请求共享的，如果权限条件没有进入 Key，就可能发生公共图库与私有空间数据串读。
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 公共图库：普通用户只能查看已过审且不属于任何私有空间的图片
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true);
        } else {
            // 私有空间：必须登录，并且只能查询自己创建的空间下的图片
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
            // 私有空间没有公共审核展示限制，避免把用户自己的待审核/未审核图片过滤掉
            pictureQueryRequest.setReviewStatus(null);
            pictureQueryRequest.setNullSpaceId(false);
        }

        // 3. 构建多级缓存共享 Key
        String cacheKey = buildPictureVOPageCacheKey(pictureQueryRequest);

        // 4. 优先读取多级缓存（L1 Caffeine -> L2 Redis）
        Page<PictureVO> cachedPictureVOPage = getCachedPictureVOPage(cacheKey);
        if (cachedPictureVOPage != null) {
            return cachedPictureVOPage;
        }

        // 5. 缓存未命中，回源查询数据库
        Page<Picture> picturePage = this.page(new Page<>(current, size),
                this.getQueryWrapper(pictureQueryRequest));

        // 6. 装配分页 VO：复用现有逻辑，完成用户信息等字段的批量填充
        Page<PictureVO> pictureVOPage = this.getPictureVOPage(picturePage, request);

        // 7. 缓存回写：写入 L1 与 L2 缓存，供后续相同查询命中
        savePictureVOPageCache(cacheKey, pictureVOPage);

        return pictureVOPage;
    }

    /**
     * 校验图片分页查询参数
     * <p>
     * 目的：由于多级缓存接口可能被内部其他调用方复用，在此保留分页大小限制，
     * 防止异常调用绕过 Controller 导致数据库、Redis 或 JVM 内存过载。
     * </p>
     *
     * @param pictureQueryRequest 图片分页查询请求参数
     */
    private void validatePicturePageQueryRequest(PictureQueryRequest pictureQueryRequest) {
        // 校验请求参数对象是否为空，防止后续取值时引发空指针异常 (NullPointerException)
        ThrowUtils.throwIf(pictureQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前请求的单页记录条数（分页大小）
        long size = pictureQueryRequest.getPageSize();
        // 限制单次查询的分页大小上限为 20 条，防止单次加载过多数据压垮内存及拖慢查询速度
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
    }

    /**
     * 构建图片分页 VO 的多级缓存共享 Key
     * <p>
     * 设计思路：
     * 1. JSON 序列化：确保分页、排序、搜索词、分类等完整查询条件均参与缓存隔离。
     * 2. MD5 摘要：将长串 JSON 压缩为固定长度的 Key，避免 Redis 存储及网络传输开销过大。
     * </p>
     *
     * @param pictureQueryRequest 已补齐默认查询约束的请求参数
     * @return 唯一的缓存 Key 字符串
     */
    private String buildPictureVOPageCacheKey(PictureQueryRequest pictureQueryRequest) {
        // 将包含分页、排序、筛选等所有查询条件的请求对象统一序列化为 JSON 字符串
        String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
        // 将冗长的 JSON 字符串转换为字节数组，并使用 MD5 算法生成 32 位的十六进制哈希值，以此压缩 Key 的长度
        String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
        // 拼接业务模块前缀与生成的哈希值，返回最终用于 L1 本地缓存和 L2 Redis 缓存的共享 Key
        return PICTURE_VO_PAGE_CACHE_KEY_PREFIX + hashKey;
    }

    /**
     * 读取图片分页 VO 多级缓存
     * <p>
     * 命中策略：
     * 1. 查 L1 本地缓存（Caffeine），命中即返回。
     * 2. 未命中查 L2 分布式缓存（Redis）。
     * 3. 若 L2 命中，将其回填至 L1 缓存，提升后续同类请求在当前 JVM 节点的响应速度。
     * </p>
     *
     * @param cacheKey 缓存 Key
     * @return 命中的分页视图；未命中时返回 null
     */
    private Page<PictureVO> getCachedPictureVOPage(String cacheKey) {
        // 尝试从 L1 本地缓存 (如 Caffeine) 中读取对应 Key 的值
        String cachedValue = LOCAL_CACHE.getIfPresent(cacheKey);

        // 如果 L1 缓存命中（值不为空）
        if (cachedValue != null) {
            // 直接将缓存的 JSON 字符串反序列化为 Page 对象并返回，终止后续查询
            return JSONUtil.toBean(cachedValue, Page.class);
        }

        // L1 缓存未命中，获取 Redis 字符串类型的操作对象，准备查询 L2 缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();

        // 尝试从 L2 分布式缓存 (Redis) 中读取对应 Key 的值
        cachedValue = valueOps.get(cacheKey);

        // 如果 L2 缓存命中（值不为空）
        if (cachedValue != null) {
            // 将 Redis 中读取到的热点数据回填到当前 JVM 的 L1 本地缓存中
            LOCAL_CACHE.put(cacheKey, cachedValue);
            // 将 JSON 字符串反序列化为 Page 对象并返回
            return JSONUtil.toBean(cachedValue, Page.class);
        }

        // 如果 L1 和 L2 缓存均未命中，返回 null，提示上层业务需要回源查询数据库
        return null;
    }

    /**
     * 写入图片分页 VO 多级缓存
     * <p>
     * 写入策略：
     * 1. 同步写入 L1（Caffeine）与 L2（Redis）。
     * 2. 防雪崩处理：L2 Redis 在基础过期时间上增加随机抖动，避免大量热点 Key 在同一时刻集中失效引发缓存雪崩。
     * </p>
     *
     * @param cacheKey      缓存 Key
     * @param pictureVOPage 组装完成的分页结果数据
     */
    private void savePictureVOPageCache(String cacheKey, Page<PictureVO> pictureVOPage) {
        // 将装配完成的图片分页视图对象序列化为 JSON 字符串，以便存储
        String cacheValue = JSONUtil.toJsonStr(pictureVOPage);
        // 同步将数据写入 L1 本地缓存（其过期时间由初始化本地缓存时的全局配置决定）
        LOCAL_CACHE.put(cacheKey, cacheValue);
        // 获取 Redis 字符串类型的操作对象，准备写入 L2 缓存
        ValueOperations<String, String> valueOps = stringRedisTemplate.opsForValue();
        // 计算 Redis 缓存过期时间：基础时间 300 秒（5分钟） + 0 到 300 秒的随机抖动时间
        // 这样做是为了防止大量同类缓存记录在同一时间失效，导致数据库瞬间面临巨大的回源压力（缓存雪崩）
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        // 将数据写入 L2 缓存 (Redis)，并设置带有随机抖动的过期时间
        valueOps.set(cacheKey, cacheValue, cacheExpireTime, TimeUnit.SECONDS);
    }

    /**
     * 清理图片分页 VO 列表缓存。
     * <p>
     * 缓存一致性策略：图片列表属于读多写少场景，查询时使用 Caffeine + Redis 提升性能；
     * 但只要图片数据发生写入变化（上传、删除、编辑、审核等），旧分页结果就可能包含过期数据。
     * 因此写操作成功后统一清空本地缓存，并按业务前缀删除 Redis 中的列表缓存。
     * </p>
     * <p>
     * 注意：缓存清理失败不应影响主业务写操作结果，所以 Redis 清理异常只记录日志，不向外抛出。
     * </p>
     */
    @Override
    public void clearPictureVOPageCache() {
        LOCAL_CACHE.invalidateAll();

        try {
            Set<String> cacheKeys = stringRedisTemplate.keys(PICTURE_VO_PAGE_CACHE_KEY_PREFIX + "*");
            if (CollUtil.isEmpty(cacheKeys)) {
                log.info("图片分页 VO 缓存清理完成，本地缓存已清空，Redis 缓存数量 = 0");
                return;
            }
            Long deleteCount = stringRedisTemplate.delete(cacheKeys);
            log.info("图片分页 VO 缓存清理完成，本地缓存已清空，Redis 缓存删除数量 = {}", deleteCount);
        } catch (Exception e) {
            log.error("图片分页 VO Redis 缓存清理失败，本地缓存已清空", e);
        }
    }
    //=================================================================================================


    /**
     * 校验图片参数的合法性
     * <p>
     * 业务场景：通常在执行图片信息的【更新/修改】操作前调用，防止脏数据、超长文本等非法数据落库引发异常。
     * 校验规则：
     * 1. 基础拦截：传入的图片实体对象不能为空。
     * 2. ID 校验：主键 ID 必须存在（必传项，表明这是对已有数据的修改）。
     * 3. 长度限制：图片 URL 不得超过 1024 个字符；图片简介不得超过 800 个字符。
     *
     * @param picture 需要校验的图片实体对象
     * @throws RuntimeException (由 ThrowUtils 抛出) 当匹配到非法参数时，中断流程并抛出 PARAMS_ERROR 业务异常
     */
    @Override
    public void validPicture(Picture picture) {
        // 1. 基础防御：实体不能为 null
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);

        // 2. 提取需要校验的核心字段
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();

        // 3. 核心规则校验
        // 强制校验 ID：修改数据时必须有主键来定位记录
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");

        // URL 校验：若传入了 url，需确保其长度不超出数据库字段的设计容量 (通常 varchar 为 1024 或 2048)
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }

        // 简介校验：若传入了简介，需控制长文本体积，防止恶意构造超长文本打满带宽或数据库
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }


    /**
     * 执行图片内容审核操作
     * <p>
     * 业务场景：通常应用于后台管理系统的工作台。管理员对用户上传的图片进行内容合规性检查（如涉黄、侵权判定），
     * 从而决定该图片是否获准在前端公共图库或瀑布流中公开展示。
     * </p>
     * <p>
     * 处理逻辑与核心规则：
     * 1. 基础拦截 (防御性编程)：校验入参合法性。明确限制审核的目标状态只能是“通过(1)”或“拒绝(2)”，严禁前端将其反向重置为“待审核(0)”。
     * 2. 数据一致性校验：确认被审核的图片在数据库中真实存在，拦截对已物理删除/逻辑删除数据的无效操作。
     * 3. 幂等性校验 (防重复提交)：对比数据库中的当前状态与前端请求的目标状态。若两者一致，则直接抛出异常拦截，防止因网络抖动或前端重复点击造成的无效数据库写操作。
     * 4. 安全赋值与按需更新 (高阶处理)：
     * - 权限与审计安全：强制从后端的 loginUser 上下文提取操作人 ID，并由服务器生成当前绝对时间，彻底杜绝前端伪造审核记录的风险。
     * - 性能优化策略：利用 new Picture() 构建空对象并按需 set 字段，触发 MyBatis-Plus 的“非空动态更新”机制，避免全量字段（包含长文本）被无意义地覆盖刷新。
     * </p>
     *
     * @param pictureReviewRequest 图片审核请求参数 DTO（包含：待审核图片的 ID、目标审核状态、审核驳回信息等）
     * @param loginUser            当前执行审核操作的登录用户上下文（通常需确保该用户已通过 AOP 或拦截器的管理员权限校验）
     * @throws BusinessException   (由 ThrowUtils 抛出) 当匹配到参数非法、图片不存在、重复审核或数据库更新失败等异常情况时，中断流程并向前端抛出业务异常
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // ==========================================
        // 1. 校验参数 (防御性编程：将非法的请求拦截在最外层)
        // ==========================================
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);

        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);

        // 核心拦截：
        // a. ID不能为空
        // b. 传入的状态值必须能在枚举中找到匹配项
        // c. 目标状态绝对不能是“待审核(REVIEWING)”，审核操作只能是“通过”或“拒绝”
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "非法的审核状态");
        }

        // ==========================================
        // 2. 判断图片是否存在 (数据一致性校验)
        // ==========================================
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在或已被删除");

        // ==========================================
        // 3. 校验审核状态是否重复 (幂等性校验)
        // ==========================================
        // 如果数据库中的当前状态，已经等于前端要修改的目标状态，则直接拦截。
        // 作用：防止前端重复点击或网络重发导致的不必要数据库写操作。
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }

        // ==========================================
        // 4. 数据库操作 (高阶技巧：按需更新与审计字段安全注入)
        // ==========================================
        // 性能优化：为什么这里要 new Picture() 而不是直接 updateById(oldPicture)？
        // 因为 oldPicture 包含了图片的所有字段（甚至长文本简介等）。
        // new 一个空对象，利用 MyBatis-Plus 默认的“非空更新”策略，底层生成的 SQL 只会 UPDATE 我们显式 set 的这几个字段。
        Picture updatePicture = new Picture();

        // 将 DTO 中的基础参数（id, reviewStatus, reviewMessage）拷贝到实体类中
        BeanUtils.copyProperties(pictureReviewRequest, updatePicture);

        // 安全防范：在此处补全审核人与审核时间。
        // 绝不信任前端传递的权限数据，而是从后端的 loginUser (Session/Token) 取出管理员 ID，
        // 并由服务器统一生成当前绝对时间，保证审计链路的真实性。
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());

        // 执行更新并校验结果
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片审核状态更新失败，请重试");

        // 审核状态会影响 C 端列表是否展示该图片，审核成功后必须清理列表缓存。
        this.clearPictureVOPageCache();
    }



    /**
     * 自动填充图片审核相关参数
     * <p>
     * 【业务场景】
     * 无论图片是首次上传（新增），还是后续被修改了元数据或物理文件（编辑），都需要重新判定其内容安全状态。
     * 本方法根据操作人的身份执行差异化逻辑：
     * 1. 管理员操作：享有“信任特权”，其上传或修改的图片默认直接过审（PASS），并自动留下审计痕迹。
     * 2. 普通用户操作：触发“逢变必审”机制，状态强制重置或初始化为“待审核（REVIEWING）”，等待后台人工或机器介入。
     * </p>
     * <p>
     * 【架构考量与抽取原因】
     * 1. 遵循 DRY (Don't Repeat Yourself) 原则：新增图片、修改图片、甚至是未来的批量导入图片等多个核心业务流，
     * 都需要执行这套“审核状态装配”逻辑。将其抽离为独立方法，极大降低了代码的冗余度。
     * 2. 职责单一原则 (SRP)：让 uploadPicture 和 editPicture 等方法专注于“文件解析、参数校验与基础落库”，
     * 将“内容安全与审核状态流转”的核心业务规则内聚于此。未来若需扩展逻辑（如：引入白名单用户免审、接入第三方机审 API），只需在此一处改动即可，系统扩展性极强。
     * 3. 提升安全性与一致性：统一收口审核状态的赋值逻辑，有效避免团队协作时在不同接口中漏写、错写状态重置代码，
     * 从而彻底杜绝“普通用户修改旧图片绕过审核”的安全漏洞。
     * </p>
     *
     * @param picture   待装配审核参数的图片实体（通常是在实体基础属性组装完毕后、准备执行 saveOrUpdate 落库前调用）
     * @param loginUser 当前执行操作的登录上下文（用于判断是否具备管理员特权）
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // ==========================================
            // 管理员特权通道：自动赋予“通过”状态，并完善审计追踪字段
            // ==========================================
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // ==========================================
            // 普通用户通道：强制进入/重置为“待审核”池
            // ==========================================
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());

            // 架构提示：针对“编辑旧图片”的场景，一旦状态被打回 REVIEWING，
            // 实际上原有的 reviewerId 和 reviewMessage 应该视业务需求考虑是否被置空（设为 null），
            // 避免前端在待审核状态下依然展示出上一次的历史审核驳回信息。当前系统若无此苛刻要求，仅重置状态即可拦截公开展示。
        }
    }


    /**
     * 批量抓取和创建图片的核心业务实现
     * <p>
     * 【业务场景】
     * 运营人员或管理员需要快速为系统图库扩充特定主题（如“风景”、“头像”）的图片素材。
     * 本方法通过调用第三方搜索引擎（必应图片异步加载接口），根据用户输入的搜索词，
     * 自动抓取网页图片元素，并将其清洗、下载、转存到本系统的对象存储（OSS）及数据库中。
     * </p>
     * <p>
     * 【架构考量与设计精要】
     * 1. 强防御性编程（系统自保机制）：在方法入口处强制设定 `count > 30` 的硬限制。
     * 批量网络抓取和 I/O 密集型操作极其消耗资源，此举有效防止了恶意调用或误操作导致的
     * 接口长时间阻塞（Timeout）、服务器内存溢出（OOM），同时极大降低了因高频访问被目标网站封禁 IP 的风险。
     * 2. 核心逻辑复用 (DRY 原则)：在此方法中，我们只处理“爬虫与数据提取”的特有逻辑，
     * 而在真正的图片落库阶段，直接循环调用了已有的单图上传方法（`this.uploadPicture`）。
     * 单图链路中沉淀的“图片下载、格式校验、尺寸解析、OSS 上传、乃至审核参数自动填充”等一系列
     * 复杂且极具价值的业务规则被完美复用，实现了极高的代码内聚。
     * 3. 隔离异常与容错机制 (Fault Tolerance)：在批量遍历处理单图时，使用了独立的 `try-catch` 块包裹核心调用。
     * 外部网络抓取本质上是不可靠的（如：某些图片的源链接已失效、被防盗链拦截等）。
     * 此设计确保了单张图片的失败（脏数据）只会触发日志记录并跳过，而绝不会中断整个批处理任务（不抛出阻断异常），
     * 保障了系统在恶劣网络环境下的健壮性和可用性。
     * 4. 数据清洗与规范化：精准截取 URL 中的 `?` 前置部分，去除了外部引擎生成的缩略图或动态裁剪参数，
     * 确保系统抓取并转存的是最原始的高质量图片文件。
     * </p>
     *
     * @param pictureUploadByBatchRequest 批量抓取请求参数（含搜索词及预期抓取数量）
     * @param loginUser                   当前执行操作的登录上下文
     * @return 最终实际成功抓取并转存入库的图片总数
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 1. 获取请求参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        // 校验抓取数量，限制最多 30 条，防止单次请求时间过长或被目标网站封禁 IP
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");

        // 1.5 解析图片名称前缀
        // 优先使用前端传入的自定义前缀；若未指定，则优雅降级，默认使用当前搜索词作为图片命名基础
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }

        // 2. 构造要抓取的目标地址（此处采用必应图片搜索的异步加载接口）
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            // 3. 使用 Jsoup 发送 HTTP GET 请求，获取页面 HTML 文档对象
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }

        // 4. 解析页面内容：找到包裹图片的主容器（必应图片的容器 class 为 dgControl）
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }

        // 从主容器中筛选出所有真实图片的 DOM 元素（class 为 mimg）
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;

        // 5. 遍历图片元素列表，依次执行上传
        for (Element imgElement : imgElementList) {
            // 提取图片的源地址 (src 属性)
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }

            // 处理图片上传地址，防止出现转义问题（例如去除 URL 后拼接的宽高等 query 参数，保留纯图片后缀）
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }

            // 6. 执行单张图片的上传逻辑
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();

            // 6.5 动态组装图片名称
            if (StrUtil.isNotBlank(namePrefix)) {
                // 设置图片名称，采用“前缀 + 连续自增序号”的命名规范（例如："风景1", "风景2"），提升图库可读性与检索体验
                pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            }

            try {
                // 复用单张图片上传的方法，将外部图片 URL 转存到我们的系统中
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++; // 记录成功上传的数量
            } catch (Exception e) {
                // 遇到单张图片上传失败的情况，仅记录错误日志，并跳过当前循环继续抓取下一张
                log.error("图片上传失败", e);
                continue;
            }

            // 7. 数量控制：一旦成功上传的数量达到用户指定的抓取数量，立即终止循环
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    /**
     * 异步清理图片对应的云端物理存储文件
     * <p>
     * 业务场景：当图片记录被逻辑/物理删除或更新替换时触发调用。
     * 核心逻辑：采用“引用计数”的安全机制，先校验该云端文件是否被其他数据库记录复用。
     * 只有在确认该文件已成为“孤儿文件”时，才真正发起 COS 删除请求。
     * 注意：业务要求保留原始上传文件，因此这里只清理 WebP 压缩图、缩略图等派生产物；
     * 不删除 COS 中的原始图片，方便后续下载、追溯或重新处理。
     * </p>
     *
     * @param oldPicture 包含旧主图 URL 和旧缩略图 URL 信息的历史图片实体
     */
    @Async
    @Override
    public void clearPictureFiles(Picture oldPicture) {
        if (oldPicture == null) {
            return;
        }

        // 1. 安全校验：查询数据库中是否还有其他记录正在使用同一个主图 URL
        String pictureUrl = oldPicture.getUrl();
        if (StrUtil.isBlank(pictureUrl)) {
            return;
        }

        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();

        // 2. 防误删拦截：当前记录删除或替换后，如果数据库里仍有记录指向同一个 URL，说明文件还在被复用，不能删除 COS 物理文件
        if (count > 0) {
            return;
        }

        // 3. COS SDK 删除对象时要求传入对象 Key，而不是数据库中保存的完整访问 URL。
        // 当前数据库中的主图 URL 通常指向 WebP 压缩图；原始上传文件属于需要保留的业务资产。
        // 因此这里只按同名前缀补充清理 WebP 和缩略图等派生产物，不删除原始图片。
        for (String objectKey : buildRelatedCosObjectKeys(pictureUrl)) {
            deleteCosObject(objectKey, "图片关联文件");
        }

        // 4. 同步清理关联的缩略图文件（进行非空校验，兼容早期可能没有生成缩略图的历史数据）
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl) && !StrUtil.equals(pictureUrl, thumbnailUrl)) {
            deleteCosObject(thumbnailUrl, "缩略图");
        }
    }

    /**
     * 根据数据库中保存的主图 URL 推导需要同步清理的 COS 派生产物 Key。
     * <p>
     * 上传链路会先保存原图，再通过数据万象生成 WebP 压缩图和缩略图；数据库主图 url
     * 优先保存的是 WebP 地址。由于业务要求保留原始上传文件，这里只推导并清理：
     * WebP 压缩图、不同后缀的缩略图，以及历史异常缩略图 key。
     */
    private Set<String> buildRelatedCosObjectKeys(String pictureUrl) {
        Set<String> objectKeys = new LinkedHashSet<>();
        String mainObjectKey = parseCosObjectKey(pictureUrl);
        if (StrUtil.isBlank(mainObjectKey)) {
            return objectKeys;
        }
        objectKeys.add(mainObjectKey);

        String filePrefix = cosManager.getFilePrefix(mainObjectKey);
        objectKeys.add(filePrefix + ".webp");

        String[] suffixes = {"png", "jpg", "jpeg", "webp"};
        for (String suffix : suffixes) {
            objectKeys.add(filePrefix + "_thumbnail." + suffix);
        }
        // 兼容早期 URL 没有扩展名时生成的异常缩略图 key，例如 xxx_thumbnail.
        objectKeys.add(filePrefix + "_thumbnail.");
        return objectKeys;
    }

    /**
     * 删除 COS 中的单个对象，并打印关键日志。
     * <p>
     * 注意：COS 删除接口接收的是对象 Key，不是完整访问 URL；同时删除不存在的 Key 通常不会抛出业务错误。
     * 因此这里必须打印转换后的 Key，便于和 COS 控制台中的对象路径逐字核对。
     * </p>
     *
     * @param fileUrl 数据库中保存的完整访问 URL，或历史数据中直接保存的对象 Key
     * @param label   日志标签，用于区分主图、缩略图等文件类型
     */
    private void deleteCosObject(String fileUrl, String label) {
        // 1. 核心转换：将前端可访问的完整 URL 解析为 COS 底层 API 真正识别的物理对象键（ObjectKey）
        String objectKey = parseCosObjectKey(fileUrl);

        // 2. 防御性校验：拦截空数据或无效 URL，避免向云端发起无效 HTTP 请求造成资源浪费
        if (StrUtil.isBlank(objectKey)) {
            log.warn("跳过删除 COS {}，文件地址为空，fileUrl = {}", label, fileUrl);
            return;
        }

        // 3. 异常隔离：云端网络交互属于不可控的外部依赖。
        // 使用 try-catch 包裹是为了保证即使删除云端文件失败（如网络超时、权限不足），也不会向外抛出异常从而导致主业务（如数据库记录删除）发生事务回滚。
        try {
            log.info("开始删除 COS {}，fileUrl = {}，objectKey = {}", label, fileUrl, objectKey);

            // 发起网络调用物理删除文件
            cosManager.deleteObject(objectKey);

            log.info("删除 COS {} 完成，objectKey = {}", label, objectKey);
        } catch (Exception e) {
            // 4. 故障追溯：详细记录失败时的上下文参数和完整异常堆栈，方便后续排查或通过定时任务进行补偿清理
            log.error("删除 COS {} 失败，fileUrl = {}，objectKey = {}", label, fileUrl, objectKey, e);
        }
    }

    /**
     * 将数据库中保存的图片访问地址转换为 COS 对象 Key。
     * <p>
     * 业务背景：图片表中保存的是前端可直接访问的完整 URL，而腾讯云 COS 的 deleteObject(bucket, key)
     * 只接受对象 Key。这里统一剥离配置的 host 和多余的斜杠，避免把完整 URL 传给 COS SDK 导致删除（或查询）失败。
     * </p>
     *
     * @param fileUrl 数据库中保存的完整访问 URL，或已经是对象 Key 的历史数据
     * @return COS 对象 Key，例如 "public/user/a.png"（不带前缀斜杠）
     */
    private String parseCosObjectKey(String fileUrl) {
        // 1. 空值兜底：防止传入 null 或空串引发后续的空指针异常
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }

        String objectKey = fileUrl;
        String host = cosClientConfig.getHost();

        // 2. 剥离域名部分：
        // 如果传入的是包含配置 Host 的完整 URL，则安全剔除该前缀。
        // 注意：StrUtil.removeSuffix 能够兼容 host 配置项末尾是否带有 "/" 的情况，保证截取的准确性
        if (StrUtil.isNotBlank(host)) {
            objectKey = StrUtil.removePrefix(objectKey, StrUtil.removeSuffix(host, "/"));
        }

        // 3. 规范化路径前缀：
        // 腾讯云 COS 的对象键（Key）规范是不带前导斜杠的（即 "a/b.jpg" 而不是 "/a/b.jpg"）。
        // 使用 while 循环剥离，防止出现截取域名后残留多个 "///" 的极端情况
        while (objectKey.startsWith("/")) {
            objectKey = objectKey.substring(1);
        }

        // 4. 清理 URL 查询参数：
        // 如果传入的 URL 带有临时防盗链签名（?q-sign=xxx）或数据万象图片处理参数（?imageMogr2），
        // 必须将其丢弃，只保留纯净的物理文件路径，否则 COS 无法精准定位文件
        int queryIndex = objectKey.indexOf("?");
        if (queryIndex >= 0) {
            objectKey = objectKey.substring(0, queryIndex);
        }

        return objectKey;
    }

    /**
     * 执行图片删除的核心业务流转（统筹校验、鉴权、删除与资源清理）
     * <p>
     * 业务编排：
     * 1. 防御性拦截：确保入参合法，防止无效查询。
     * 2. 查后删确认：锁定目标记录，为鉴权和清理提供元数据。
     * 3. 统一鉴权：调度基于空间属性的动态权限策略，拦截越权行为。
     * 4. 数据擦除：执行数据库层面的记录移除。
     * 5. 资源释放：同步清理 Redis 分页缓存与对象存储（OSS）中的物理文件。
     *
     * @param pictureId 待删除的目标图片主键 ID
     * @param loginUser 当前已认证的登录用户对象（操作主体）
     */
    @Override
    public void deletePicture(long pictureId, User loginUser) {
        // 1. 防御性拦截：确保核心入参合法，避免无效的底层 DB 扫描与空指针异常 (NPE)
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 校验目标资源是否存在
        // 经典的“查后删”模式：必须先查出老数据，不仅是为了确认记录存在，更是为了提取 spaceId 和图片 URL 供后续鉴权和清文件使用
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 越权防御与权限校验
        // 复用核心鉴权路由：将权限判断逻辑收拢至 checkPictureAuth，由其动态决定公共/私有空间的越权拦截策略
        this.checkPictureAuth(loginUser, oldPicture);

        // 4. 执行数据擦除 (物理/逻辑删除)
        // 委托给 MyBatis-Plus 底层的 IService 执行移除操作，并严格核验受影响的行数，确保删除真正落地
        boolean result = this.removeById(pictureId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 5. 缓存一致性处理
        // 图片元数据销毁后，旧的列表分页缓存即成为脏数据；此处主动失效，防止前端短暂出现“幽灵图片”
        this.clearPictureVOPageCache();

        // 6. 清理云端物理资源
        // 释放对象存储上的源文件与相关资源，避免产生闲置的“孤儿文件”导致存储成本白白浪费
        this.clearPictureFiles(oldPicture);
    }

    /**
     * 执行图片编辑的核心业务流转（统筹映射、校验、鉴权、审核与更新）
     * <p>
     * 业务编排：
     * 1. DTO 转换与装配：构建实体对象，更新编辑时间。
     * 2. 业务属性校验：调用 validPicture 拦截非法数据格式。
     * 3. 查后改确认：锁定目标记录，提取老数据用于越权比对。
     * 4. 统一鉴权：调度基于空间属性的动态权限策略。
     * 5. 审核状态重置：调用 fillReviewParams 根据修改动作重新评估审核状态。
     * 6. 数据落地与缓存清理。
     *
     * @param pictureEditRequest 包含待修改字段的请求 DTO
     * @param loginUser          当前已认证的登录用户对象（操作主体）
     */
    @Override
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 1. 防御性拦截：确保核心入参合法，避免无效操作与空指针异常 (NPE)
        ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. DTO 实体映射与特殊字段处理
        // 将前端传来的修改请求对象转换为底层数据库实体，并处理复杂结构（如 List 序列化为 JSON 字符串）
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));

        // 业务补充：只要发生编辑，就刷新最后编辑时间，方便后续做“近期修改”排序或缓存淘汰策略
        picture.setEditTime(new Date());

        // 3. 数据合法性校验
        // 针对修改后的属性进行业务规则校验（如 URL 格式、名称长度等）
        this.validPicture(picture);

        // 4. 校验目标资源是否存在
        // 经典的“查后改”模式：确认底层数据真实存在，并提取旧数据供后续越权比对使用
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 5. 越权防御与权限校验
        // 复用核心鉴权路由：将权限判断逻辑收拢至 checkPictureAuth，由其动态决定越权拦截策略
        this.checkPictureAuth(loginUser, oldPicture);

        // 6. 补充审核参数 【关键步骤】
        // 图片内容或元数据发生变更，可能需要重新进入人工审核流；此处根据系统规则和操作者身份重新填充审核状态
        this.fillReviewParams(picture, loginUser);

        // 7. 执行数据更新
        // 委托给 MyBatis-Plus 执行修改，并核验底层受影响的行数，确保更新真正落地
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 8. 缓存一致性处理
        // 普通编辑会影响前端列表的展示内容（如改了名字或标签），写入成功后主动清理旧的分页缓存
        this.clearPictureVOPageCache();
    }


    /**
     * 校验图片操作权限（统一处理公共图库与私有空间的越权防御）
     * <p>
     * 业务策略：
     * 1. 基础校验：拦截鉴权主体（用户）与客体（图片）为空的非法请求，防止后续空指针异常。
     * 2. 路由分发：提取图片的归属空间标识（spaceId），动态选择对应的底层鉴权策略。
     * 3. 公共图库鉴权 (spaceId 为空)：遵循常规的 RBAC 权限体系，仅允许图片的拥有者（本人）或系统级管理员进行操作。
     * 4. 私有空间鉴权 (spaceId 非空)：实行绝对的数据隐私隔离，严禁任何人（包括全站超级管理员）窥探或篡改他人私有资产，仅允许该空间的拥有者操作。
     *
     * @param loginUser 当前已认证的登录用户对象（鉴权主体）
     * @param picture   待操作的底层图片实体记录（鉴权客体）
     * @throws BusinessException 当用户缺失对应操作权限时，抛出 NO_AUTH_ERROR 业务异常直接阻断后续流程
     */
    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        // 1. 防御性拦截：确保鉴权主体与客体均存在，防止后续调用 get 方法时触发空指针异常 (NPE)
        ThrowUtils.throwIf(loginUser == null || picture == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 提取路由鉴权的核心标识
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();

        // 3. 动态分发鉴权策略
        if (spaceId == null) {
            // ==========================================
            // 策略 A：公共图库权限管控
            // 规则：仅图片的上传者（本人）或系统级管理员具备修改/删除的权限
            // ==========================================
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作公共图库中他人的图片");
            }
        } else {
            // ==========================================
            // 策略 B：私有图库空间权限管控
            // 规则：绝对的私有化隔离。哪怕是全站管理员，也严禁越权操作他人私有空间内的数据
            // ==========================================
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作他人私有空间内的图片");
            }
        }
    }

}
