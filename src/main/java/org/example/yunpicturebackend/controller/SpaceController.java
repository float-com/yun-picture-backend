package org.example.yunpicturebackend.controller;

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
import org.example.yunpicturebackend.model.dto.space.SpaceEditRequest;
import org.example.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import org.example.yunpicturebackend.model.dto.space.SpaceUpdateRequest;
import org.example.yunpicturebackend.model.entity.Space;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.SpaceVO;
import org.example.yunpicturebackend.service.SpaceService;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 图库空间模块对外 HTTP 接口 (Controller 层)
 * <p>
 * 核心职责：
 * 1. 负责接收前端的 HTTP 请求，并进行参数的初步绑定与校验。
 * 2. 处理应用层的权限控制（结合 @AuthCheck 注解与业务内的属主校验）。
 * 3. 调度下层的 SpaceService 执行具体业务逻辑。
 * 4. 隔离 Entity 与 DTO/VO，保障底层数据安全。
 */
@Slf4j
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;


    /**
     * 删除图库空间接口
     * <p>
     * 架构考量：使用 @PostMapping 配合 DeleteRequest 封装体，规避部分老旧网关对 DELETE 请求体的拦截问题。
     *
     * @param deleteRequest 包含待删除空间 ID 的请求体
     * @param request       用于提取当前登录态
     * @return 包装在统一响应体 BaseResponse 中的布尔值，标识是否删除成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest,
                                             HttpServletRequest request) {
        // 1. 基础防空：校验 ID 的合法性
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. 提取当前上下文的登录用户
        User loginUser = userService.getLoginUser(request);
        long id = deleteRequest.getId();

        // 3. 校验目标资源是否存在 (查后删)
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 4. 核心越权防御
        // 业务规则：仅空间的“创建者”或“全站管理员”有权删除。
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 5. 执行逻辑/物理删除
        boolean result = spaceService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }


    /**
     * 更新空间信息（仅管理员可用）
     * <p>
     * 业务场景：后台管理员对空间进行强制管控，例如修改违规名称，或“破格”调整某个空间的级别和容量配额。
     *
     * @param spaceUpdateRequest 包含空间全量可修改字段的 DTO
     * @param request            HTTP 请求对象
     * @return 更新成功返回 true
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest,
                                             HttpServletRequest request) {
        // 1. 基础参数防御
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. DTO 转 Entity (核心隔离设计)
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);

        // 3. 动态填充配额：当管理员调整了 spaceLevel 时，自动补齐对应的 maxSize 和 maxCount
        spaceService.fillSpaceBySpaceLevel(space);

        // 4. 数据业务校验 (复用 Service 层的校验逻辑，false 代表为更新操作)
        spaceService.validSpace(space, false);

        // 5. 校验目标记录存在性
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 6. 执行全量覆盖更新
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 编辑空间信息（供 C 端普通用户使用）
     * <p>
     * 业务场景：用户在个人中心修改自己创建的空间信息。
     * 权限区分：Edit 接口严格屏蔽了配额相关的修改权限，仅允许修改 spaceName 等基础展示信息，并通过代码深处的属主校验防越权。
     *
     * @param spaceEditRequest 包含修改字段的请求 DTO
     * @param request          HTTP 原生请求对象，用于鉴权
     * @return 编辑成功返回 true
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest,
                                           HttpServletRequest request) {
        // 1. 基础防空
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 2. DTO 映射到实体
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);

        // 3. 针对更新操作进行参数合法性校验
        spaceService.validSpace(space, false);

        // 4. 提取当前登录用户，准备进行越权校验
        User loginUser = userService.getLoginUser(request);
        long id = spaceEditRequest.getId();

        // 5. 提取原数据并执行越权防御
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 逻辑：如果“原空间的拥有者”不是“当前登录用户”，并且“当前登录用户”也不是“管理员”，则拒绝访问。
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 6. 落库保存
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取空间原始信息（仅管理员可用）
     * <p>返回底层全量字段，不作脱敏处理。</p>
     *
     * @param id      空间 ID
     * @param request HTTP 请求对象
     * @return 包含空间原始实体信息的统一响应体
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 1. 查询数据库获取底层实体
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(space);
    }

    /**
     * 根据 id 获取空间视图对象（供 C 端普通用户使用）
     * <p>对底层实体进行脱敏及关联信息组装，适用于前端空间详情展示。</p>
     *
     * @param id      空间 ID
     * @param request HTTP 请求对象
     * @return 包含空间脱敏视图对象 (SpaceVO) 的统一响应体
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 1. 查询底层实体
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 2. 转换为脱敏视图对象（含创建者信息嵌套）并返回
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }

    /**
     * 分页获取空间原始列表（仅管理员可用）
     * <p>支持复杂条件检索，返回未脱敏的全量字段，用于后台管理系统进行大盘面管理。</p>
     *
     * @param spaceQueryRequest 包含分页参数和检索条件的请求体
     * @return 包含空间原始数据分页对象的统一响应体
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 解析检索条件并执行数据库分页
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间视图列表（供 C 端普通用户使用）
     * <p>
     * 架构考量：读写分离。返回脱敏视图数据，并在 Service 层解决 N+1 关联查询问题。
     *
     * @param spaceQueryRequest 包含分页参数和复杂检索条件的 DTO
     * @param request           用于提取当前登录态
     * @return 包含脱敏数据分页对象 (Page<SpaceVO>) 的统一响应体
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                         HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();

        // 1. 安全防御：防止恶意爬虫一次性拉取海量数据导致 OOM
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 2. 底层数据检索
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));

        // 3. 视图模型装配并返回
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

}