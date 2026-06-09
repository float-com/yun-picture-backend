package org.example.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.manager.FileManager;
import org.example.yunpicturebackend.model.dto.file.UploadPictureResult;
import org.example.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import org.example.yunpicturebackend.model.entity.Picture;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.example.yunpicturebackend.model.vo.UserVO;
import org.example.yunpicturebackend.service.PictureService;
import org.example.yunpicturebackend.mapper.PictureMapper;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService{

    @Resource
    private FileManager fileManager;

    @Resource
    private UserService userService;

    /**
     * 上传图片（统一处理新增和更新逻辑）
     * <p>
     * 业务流程：
     * 1. 权限校验：确保用户已登录。
     * 2. 更新校验：若携带图片 ID，则防御性校验该记录在数据库中是否存在。
     * 3. 物理上传：调用底层的 FileManager 将文件安全上传至 COS，并按用户 ID 隔离存储目录。
     * 4. 数据装配：提取云端返回的元数据（宽高、大小、格式等）组装数据库实体。
     * 5. 持久化：利用 saveOrUpdate 特性，根据 ID 的有无，自动执行 INSERT 或 UPDATE。
     *
     * @param multipartFile        前端传入的物理图片文件对象
     * @param pictureUploadRequest 图片上传扩展参数（核心用于携带图片 id，区分新增或更新）
     * @param loginUser            当前已认证的登录用户对象
     * @return PictureVO           上传并成功入库后，返回给前端的脱敏视图对象
     */
    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 1. 基础安全拦截：强制要求必须登录后才能执行上传，防范匿名用户恶意传图消耗云端流量和存储
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 提取更新标识（判断当前操作是“全新上传”还是“对旧记录的物理文件替换”）
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }

        // 3. 更新操作的防御性编程
        if (pictureId != null) {
            // 如果是更新动作，必须查库校验待更新的图片记录是否真实存在。
            // 避免前端传递虚假 ID 导致后续业务出现脏数据或空指针异常。
            boolean exists = this.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .exists();
            ThrowUtils.throwIf(!exists, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            // 注意：严格的生产环境中，这里可能还需要进一步校验该 pictureId 是否归属于当前 loginUser.getId()，防止水平越权（修改别人的图片）
        }

        // 4. 动态构造云端对象存储的目录前缀
        // 策略：按照业务线 (public) + 用户 ID (loginUser.getId()) 划分外层目录
        // 优势：实现不同用户间的文件物理隔离，不仅方便排查问题，也有利于后续针对单个用户进行空间配额统计或违规资源清理
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());

        // 5. 调度底层文件管理器，完成向第三方 COS 的安全上传与图片元数据（CI）解析
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        // 6. 数据搬运与装配：将上传成功后的 DTO 结果转换为数据库底层能识别的 Entity 实体
        Picture picture = new Picture();
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setName(uploadPictureResult.getPicName());
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        // 绑定图片归属权，记录数据拥有者
        picture.setUserId(loginUser.getId());

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
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);

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




}
