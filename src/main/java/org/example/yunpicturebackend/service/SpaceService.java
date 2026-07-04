package org.example.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import org.example.yunpicturebackend.model.entity.Space;
import org.example.yunpicturebackend.model.vo.SpaceVO;

import javax.servlet.http.HttpServletRequest;

/**
 * @author 24042
 * @description 针对表【space(图库空间表)】的数据库操作Service
 * @createDate 2026-07-02 11:30:34
 */
public interface SpaceService extends IService<Space> {

    /**
     * 构建空间查询的 MyBatis-Plus 包装类 (QueryWrapper)
     * <p>
     * 业务场景：在执行空间的分页查询或列表检索前，将前端传入的 DTO 查询条件转换为数据库可执行的 SQL 条件。
     * 转换规则：
     * 1. 模糊匹配：对 spaceName 进行 LIKE 查询，便于前端搜索框进行模糊检索。
     * 2. 精确匹配：对 id, userId, spaceLevel 等业务核心标识进行 EQ (等于) 查询。
     *
     * @param spaceQueryRequest 前端传入的查询请求封装对象（允许包含空字段，方法内部会自动进行判空拦截与 SQL 动态拼接）
     * @return QueryWrapper<Space> 组装完毕的 MyBatis-Plus 查询包装类，可直接交由 Mapper 或 Service 执行底层检索
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取单个图库空间的脱敏视图对象 (VO)
     * <p>
     * 业务场景：在查看图库空间详情时，将数据库底层的 Space 实体转换为前端直接渲染所需的 VO 对象。
     * 包含逻辑：除转换空间基础信息与配额统计字段外，还会自动关联查询并填充拥有该空间的用户信息 (UserVO)。
     *
     * @param space   原始图库空间实体对象
     * @param request HTTP 请求对象（可用于获取当前登录用户的会话上下文，辅助权限判断）
     * @return SpaceVO 包含脱敏空间信息及关联用户信息的视图对象
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取分页图库空间的脱敏视图对象 (VO)
     * <p>
     * 业务场景：在空间列表、后台全局管理等分页检索场景下，将底层的分页实体对象转换为前端展示的分页 VO 对象。
     * 性能说明：内部采用“批量提取 userId -> 单次 IN 查询 user 表 -> 内存映射组装”的策略，彻底规避传统 for 循环查库引发的 N+1 性能瓶颈问题。
     *
     * @param spacePage 原始图库空间分页查询结果
     * @param request   HTTP 请求对象
     * @return Page<SpaceVO> 包含脱敏空间信息及关联拥有者信息的分页视图对象
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);


    /**
     * 校验图库空间参数的合法性
     * <p>
     * 业务场景：在图库空间记录入库（新建）或更新前统一调用，确保核心字段满足系统约束与业务规范（例如：空间级别是否在合法枚举范围内、空间名称字符长度限制等）。
     * 注意事项：
     * 1. 异常阻断：该方法无返回值，一旦校验到非法参数，将直接抛出业务级异常（BusinessException）中断当前请求流程，从而避免脏数据落库。
     * 2. 动态校验机制：通过 add 参数区分当前所处的业务上下文。新增操作侧重于基础字段的初始化校验；而修改/更新操作则在此基础上，强制要求空间主键 ID 不能为空。
     *
     * @param space 待校验的图库空间实体对象（若传入 null，方法内部将直接触发非法参数异常）
     * @param add   是否为新增操作（true-代表当前为新建空间；false-代表当前为更新/编辑现有空间）
     */
    void validSpace(Space space, boolean add);

    /**
     * 根据空间级别自动填充图库空间配额
     * <p>
     * 业务场景：通常在创建新图库空间，或后台管理员变更图库空间级别时调用，用于初始化或同步该空间的存储上限。
     * 逻辑机制：系统会根据传入实体中的 spaceLevel（如：0-普通版、1-专业版），从全局枚举配置中提取对应的限额数据并赋值。
     * 兼容性设计（按需补充策略）：若 space 实体中已经显式指定了 maxSize 或 maxCount（通常为管理员对特定用户进行的破格定制提额），该方法会自动跳过覆盖，优先保留自定义设置。
     *
     * @param space 待处理的图库空间实体对象（需保证实体内部的 spaceLevel 字段已被正确赋值）
     */
    void fillSpaceBySpaceLevel(Space space);

}
