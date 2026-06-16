package org.example.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.manager.FileManager;
import org.example.yunpicturebackend.manager.upload.FilePictureUpload;
import org.example.yunpicturebackend.manager.upload.PictureUploadTemplate;
import org.example.yunpicturebackend.manager.upload.UrlPictureUpload;
import org.example.yunpicturebackend.model.dto.file.UploadPictureResult;
import org.example.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureReviewRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import org.example.yunpicturebackend.model.entity.Picture;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.enums.PictureReviewStatusEnum;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.example.yunpicturebackend.model.vo.UserVO;
import org.example.yunpicturebackend.service.PictureService;
import org.example.yunpicturebackend.mapper.PictureMapper;
import org.example.yunpicturebackend.service.UserService;
import org.jsoup.Jsoup;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /*已弃用*/
//    @Resource
//    private FileManager fileManager;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    /**
     * 上传图片（统一处理新增和更新逻辑）
     * <p>
     * 业务流程：
     * 1. 权限校验：确保用户已登录。
     * 2. 更新校验：若携带图片 ID，则防御性校验该记录在数据库中是否存在。
     * 3. 云端上传：根据 inputSource 类型选择对应的上传策略（文件或 URL），将图片安全上传至 COS，并按用户 ID 隔离存储目录。
     * 4. 数据装配：提取云端返回的元数据（宽高、大小、格式等）组装数据库实体。
     * 5. 持久化：利用 saveOrUpdate 特性，根据 ID 的有无，自动执行 INSERT 或 UPDATE。
     *
     * @param inputSource          图片输入源（支持 MultipartFile 物理文件对象，或 String 类型的图片 URL 地址）
     * @param pictureUploadRequest 图片上传扩展参数（核心用于携带图片 id，区分新增或更新）
     * @param loginUser            当前已认证的登录用户对象
     * @return PictureVO           上传并成功入库后，返回给前端的脱敏视图对象
     */
    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. 基础安全拦截：强制要求必须登录后才能执行上传，防范匿名用户恶意传图消耗云端流量和存储
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 提取更新标识（判断当前操作是“全新上传”还是“对旧记录的物理文件替换”）
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        // 3. 更新操作的防御性编程
        if (pictureId != null) {
            // 3.1 查库获取历史记录
            // 【架构演进说明】这里舍弃了之前通过 lambdaQuery().exists() 仅判断数据是否存在的轻量级做法。
            // 原因：Controller 层取消了管理员权限的一刀切拦截后，我们必须获取到该记录的真实拥有者（userId），以便进行后续的越权校验。
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在或已被删除");

            // 3.3 审核状态重置 (可选业务逻辑，视具体需求而定)
            // 注意：如果用户修改了图片实体文件，通常意味着图片内容发生了变化，此时应当将图片的审核状态打回“待审核”。

//      ///////////////////////////已弃用的代码//////////////////////////////////////
//            // 如果是更新动作，必须查库校验待更新的图片记录是否真实存在。
//            // 避免前端传递虚假 ID 导致后续业务出现脏数据或空指针异常。
//            boolean exists = this.lambdaQuery()
//                    .eq(Picture::getId, pictureId)
//                    .exists();
//            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
//            // 注意：严格的生产环境中，这里可能还需要进一步校验该 pictureId 是否归属于当前 loginUser.getId()，防止水平越权（修改别人的图片）
//      //////////////////////////////////////////////////////////////////////////

            // 3.2 水平越权与垂直越权校验
            // 【业务场景】开放普通用户上传/编辑权限后，必须严防“张三通过抓包修改 pictureId 来覆盖李四的图片”这种高危漏洞。
            // 【校验逻辑】如果“原图片的归属者”不是“当前登录用户”（防水平越权），
            // 并且“当前登录用户”也不是“管理员”（保留管理员全局管理的特权），则果断拒绝访问。
            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权编辑他人的图片");
            }
        }

        // 4. 动态构造云端对象存储的目录前缀
        // 策略：按照业务线 (public) + 用户 ID (loginUser.getId()) 划分外层目录
        // 优势：实现不同用户间的文件物理隔离，不仅方便排查问题，也有利于后续针对单个用户进行空间配额统计或违规资源清理
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        // 5. 根据 inputSource 的类型动态调度底层的文件或URL上传策略，完成向第三方 COS 的安全上传与图片元数据（CI）解析
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if(inputSource instanceof String){
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);

        // 6. 数据搬运与装配：将上传成功后的 DTO 结果转换为数据库底层能识别的 Entity 实体
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());

        // 6.5 解析与挂载图片名称
        // 【业务场景】默认情况下，系统会自动从云端返回的元数据或原始物理文件中提取名称作为缺省值。
        // 但为了支持类似“批量抓取时统一自定义前缀”或“前端用户上传时主动重命名”等高级需求，此处设计了覆盖机制。
        // 【逻辑说明】优先采用外部扩展请求（pictureUploadRequest）中显式指定的图片名称；
        // 若外部未传递或传空串，则平滑降级，使用云端上传结果中解析出的默认名称。
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

        // 更新字段之前先补充审核参数【极其重要】
        this.fillReviewParams(picture,loginUser);

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

        // 9. 将入库成功的实体对象转换为剔除了敏感字段的 VO 对象返回给前端渲染
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


}
