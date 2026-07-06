package org.example.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.exception.ThrowUtils;
import org.example.yunpicturebackend.event.PictureCacheClearEvent;
import org.example.yunpicturebackend.mapper.PictureMapper;
import org.example.yunpicturebackend.model.dto.space.SpaceAddRequest;
import org.example.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import org.example.yunpicturebackend.model.entity.Picture;
import org.example.yunpicturebackend.model.entity.Space;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.enums.SpaceLevelEnum;
import org.example.yunpicturebackend.model.enums.SpaceTypeEnum;
import org.example.yunpicturebackend.model.vo.SpaceVO;
import org.example.yunpicturebackend.model.vo.UserVO;
import org.example.yunpicturebackend.service.SpaceService;
import org.example.yunpicturebackend.mapper.SpaceMapper;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author 24042
* @description 针对表【space(图库空间表)】的数据库操作Service实现
* @createDate 2026-07-02 11:30:34。
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    /**
     * 空间管理员完整权限列表
     * <p>
     * 业务场景：私有空间创建者和系统管理员进入空间详情页时，应能看到上传、编辑、删除、成员管理等操作入口。
     */
    private static final List<String> SPACE_ADMIN_PERMISSION_LIST = Arrays.asList(
            "spaceUser:manage",
            "picture:view",
            "picture:upload",
            "picture:edit",
            "picture:delete"
    );

    @Resource
    private  UserService userService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 用户本地锁池 (ConcurrentHashMap)
     * 架构考量：替代 String.intern() 进行细粒度加锁，避免无限制污染 JVM 字符串常量池。
     * 注意：此处刻意不提供 remove 逻辑，以防止高并发下多个线程竞争同一把锁时，由于锁对象被提前释放而导致的并发击穿问题。
     */
    private final Map<Long, Object> lockMap = new ConcurrentHashMap<>();

    /**
     * 创建图库空间 (支持细粒度并发防重与编程式事务)
     * <p>
     * 业务场景：用户在前端点击“开通私有空间”时调用。系统强制限制每个用户最多只能拥有一个私有图库空间。
     * 架构考量：
     * 1. 锁粒度优化：采用基于 userId 的局部互斥锁（ConcurrentHashMap），相比方法级的锁大幅提升了系统的并发吞吐量。
     * 2. 事务与锁的执行顺序：必须保证【锁包裹事务】（先 synchronized 加锁，再开启 transactionTemplate 事务）。如果反过来，会导致事务尚未提交，锁就被释放，从而引发脏读超卖问题。
     *
     * @param spaceAddRequest 包含空间名称、级别等初始参数的请求体 DTO
     * @param loginUser       当前已认证的登录用户对象
     * @return 新创建的图库空间主键 ID
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        // 1. DTO 转换为 Entity (隔离外部参数与底层数据模型)
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);

        // 2. 初始化默认值：提升接口的业务包容性
        if (StrUtil.isBlank(spaceAddRequest.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (spaceAddRequest.getSpaceType() == null) {
            space.setSpaceType(SpaceTypeEnum.PRIVATE.getValue());
        }
        if (spaceAddRequest.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }

        // 3. 动态装配与校验
        // 根据空间级别自动填充对应的容量和数量配额
        this.fillSpaceBySpaceLevel(space);
        // 校验基础字段合法性 (true 表示当前为新增操作)
        this.validSpace(space, true);

        // 4. 绑定属主关系
        Long userId = loginUser.getId();
        space.setUserId(userId);

        // 5. 核心越权防御
        // 业务规则：普通用户只能创建“普通版”空间；若要直接开通高级别空间，操作者必须具备管理员角色
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddRequest.getSpaceLevel() && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的私有空间");
        }

        // 6. 细粒度并发锁控制 (解决连点器导致的重复创建问题)
        // 只有同一个 userId 才会获取到同一个 Object 锁对象，不同用户互不阻塞
        Object lock = lockMap.computeIfAbsent(userId, key -> new Object());

        synchronized (lock) {
            // 7. 编程式事务执行：确保查询校验与插入操作的原子性
            Long newSpaceId = transactionTemplate.execute(status -> {

                // 7.1. 唯一性校验：查询该用户是否已经拥有图库空间
                boolean exists = this.lambdaQuery()
                        .eq(Space::getUserId, userId)
                        .exists();

                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每个用户仅能拥有一个私有空间");

                // 7.2. 物理落库
                boolean result = this.save(space);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存空间到数据库失败");

                // 7.3. 返回底层自增/雪花算法生成的新主键 ID
                return space.getId();
            });

            // 8. 拆箱与安全返回 (规避包装类引发的 NPE)
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }

    /**
     * 删除图库空间，并关联清理空间内图片
     * <p>
     * 业务场景：当空间被删除时，空间内图片已经失去归属容器，必须同步删除图片记录，避免后续列表查询出现孤立数据。
     * 事务边界：空间记录删除与图片记录删除共同提交；云端物理文件清理由图片模块的异步清理能力兜底，不阻塞空间删除主流程。
     *
     * @param spaceId   待删除的空间 ID
     * @param loginUser 当前已认证的登录用户对象
     */
    @Override
    public void deleteSpace(long spaceId, User loginUser) {
        // 1. 基础防御：校验空间 ID 与登录态，避免非法请求继续穿透到数据库层
        ThrowUtils.throwIf(spaceId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 查后删确认：删除前必须锁定旧空间，用于存在性确认和归属权限校验
        Space oldSpace = this.getById(spaceId);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 3. 核心越权防御：仅空间创建者或系统管理员允许删除空间
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 4. 事务化删除：空间与空间内图片记录必须作为一个整体删除，避免产生孤立图片
        transactionTemplate.execute(status -> {
            pictureMapper.delete(new QueryWrapper<Picture>().eq("spaceId", spaceId));
            boolean removeSpace = this.removeById(spaceId);
            ThrowUtils.throwIf(!removeSpace, ErrorCode.OPERATION_ERROR, "删除空间失败");
            return true;
        });

        // 5. 缓存一致性处理：空间内图片集合已变化，需要清理图片分页 VO 缓存
        applicationEventPublisher.publishEvent(new PictureCacheClearEvent());
    }


    /**
     * 构建图库空间查询的 MyBatis-Plus 包装类 (QueryWrapper)
     * <p>
     * 业务场景：将前端传递的查询请求对象转换为 MyBatis-Plus 可执行的数据库动态查询条件。
     * 包含对普通字段的精准/模糊查询，以及基于公共组件的动态排序逻辑。
     *
     * @param spaceQueryRequest 前端传入的查询请求封装对象
     * @return 组装完毕的 {@link QueryWrapper<Space>}
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();

        // 1. 基础防御：如果未传任何参数，直接返回空的 Wrapper（等同于查询全表）
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }

        // 2. 提取参数：从请求 DTO 中解构出所有需要参与条件组装的字段
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceType = spaceQueryRequest.getSpaceType();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        // 3. 精确查询 (eq) 与 模糊查询 (like)
        // 技巧：利用 MyBatis-Plus 提供的方法首参 condition（返回 true 才拼接该条件），避免繁琐的 if 判空
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceType), "spaceType", spaceType);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);

        // 4. 动态排序
        // 仅在传入了排序列 (sortField) 的情况下生效，通过判断 sortOrder 是否为前端约定的 "ascend" 来决定是升序还是降序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);

        return queryWrapper;
    }

    /**
     * 将单个图库空间实体转换为视图对象 (VO)
     *
     * @param space   原始图库空间实体
     * @param request HTTP 请求对象
     * @return 组装完毕的 SpaceVO
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 1. 基础转换：将底层的 Space 实体属性浅拷贝到 VO 中
        SpaceVO spaceVO = SpaceVO.objToVo(space);

        // 2. 关联查询：获取并填充该图库空间的拥有者（创建者）信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            // 查询底层用户实体
            User user = userService.getById(userId);
            // 将用户实体脱敏为 UserVO 并设置到空间 VO 中
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }

        // 3. 权限填充：空间拥有者或系统管理员拥有该空间的完整管理权限，供前端控制操作按钮展示
        User loginUser = userService.getLoginUser(request);
        if (loginUser.getId().equals(space.getUserId()) || userService.isAdmin(loginUser)) {
            spaceVO.setPermissionList(SPACE_ADMIN_PERMISSION_LIST);
        }

        return spaceVO;
    }

    /**
     * 将图库空间分页结果转换为视图对象 (VO) 分页结果
     * <p>
     * 核心逻辑：使用批处理思想，先提取所有的 userId 一次性查出用户，再在内存中组装映射，大幅降低数据库 IO。
     *
     * @param spacePage 原始空间分页结果
     * @param request   HTTP 请求对象
     * @return 组装完毕的分页 SpaceVO
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());

        // 判空保护：如果当前页没有数据，直接返回空的翻页对象
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }

        // 1. 基础转换：将 List<Space> 映射为 List<SpaceVO>
        List<SpaceVO> spaceVOList = spaceList.stream()
                .map(SpaceVO::objToVo)
                .collect(Collectors.toList());

        // 2. 批量提取关联 ID：收集当前页所有空间对应的 userId（使用 Set 去重，避免重复查询同一个用户）
        Set<Long> userIdSet = spaceList.stream()
                .map(Space::getUserId)
                .collect(Collectors.toSet());

        // 3. 批量查询与分组：一次性查出所有关联用户，并按 userId 进行分组（转化为 Map<userId, List<User>> 结构）
        // 彻底避免了在 for 循环中频繁调用 userService.getById() 造成的 N+1 查询性能瓶颈
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 4. 内存数据填充：遍历 spaceVOList，从 Map 中快速匹配并填充用户信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            // 如果在 Map 中找到了对应的用户信息，则取第一个匹配项（用户 ID 必定唯一）
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            // 将用户实体脱敏转换为 UserVO 并赋值给当前的 SpaceVO
            spaceVO.setUser(userService.getUserVO(user));
        });

        // 5. 组装结果：将转换和填充完毕的记录列表塞回分页对象中
        spaceVOPage.setRecords(spaceVOList);

        return spaceVOPage;
    }

    /**
     * 校验图库空间参数的合法性
     * <p>
     * 业务场景：通常在执行图库空间的【新增】或【修改】操作前调用，防止脏数据、越权配置等非法数据落库引发异常。
     * 校验规则：
     * 1. 基础拦截：传入的图库空间实体对象不能为空。
     * 2. 新增必填项校验：创建空间时，空间名称和空间级别必须存在。
     * 3. 级别匹配：若传入了空间级别，必须匹配系统预设的有效枚举值。
     * 4. 长度限制：空间名称不得超过 30 个字符。
     *
     * @param space 需要校验的图库空间实体对象
     * @param add   是否为新增操作（true 表示新增；false 表示更新/修改）
     * @throws RuntimeException (由 ThrowUtils 抛出) 当匹配到非法参数时，中断流程并抛出 PARAMS_ERROR 业务异常
     */
    @Override
    public void validSpace(Space space, boolean add) {
        // 1. 基础防御：实体不能为 null
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);

        // 2. 提取需要校验的核心字段
        String spaceName = space.getSpaceName();
        Integer spaceType = space.getSpaceType();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(spaceType);
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);

        // 3. 核心规则校验
        // 新增场景校验：创建数据时，必须提供空间名称和空间级别进行初始化
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceType == null, ErrorCode.PARAMS_ERROR, "空间类型不能为空");
            ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAMS_ERROR, "空间级别不能为空");
        }

        // 类型有效性校验：若传入了空间类型，需确保其在系统定义的枚举范围内
        ThrowUtils.throwIf(spaceType != null && spaceTypeEnum == null, ErrorCode.PARAMS_ERROR, "空间类型不存在");

        // 级别有效性校验：若传入了空间级别，需确保其在系统定义的枚举范围内 (防止绕过前端传入非法越权级别)
        ThrowUtils.throwIf(spaceLevel != null && spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不存在");

        // 名称长度校验：若传入了空间名称，需控制字符长度，防止超长文本导致 UI 换行异常或数据库截断报错
        ThrowUtils.throwIf(StrUtil.isNotBlank(spaceName) && spaceName.length() > 30, ErrorCode.PARAMS_ERROR, "空间名称过长");
    }

    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 1. 获取当前空间的级别枚举：将底层整型标识（0/1/2）转换为具体的枚举实例，以便提取预设的物理参数
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());

        // 2. 匹配校验：若成功匹配到预设的空间级别，则执行配额装配逻辑
        if (spaceLevelEnum != null) {

            // 3. 动态装配容量配额 (maxSize)
            long maxSize = spaceLevelEnum.getMaxSize();
            // 核心业务逻辑：仅当空间尚未明确指定容量上限时，才使用枚举的默认值进行兜底填充。
            // 优势：解耦了死板的等级绑定，允许管理员为特殊用户单独分配独立配额（例如给予某个“普通版”用户 5GB 的特权容量）
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }

            // 4. 动态装配数量配额 (maxCount)
            long maxCount = spaceLevelEnum.getMaxCount();
            // 同理，仅在缺失数量上限设定时，才使用对应级别的默认张数限制进行初始化
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }


}




