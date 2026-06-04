package org.example.yunpicturebackend.model.dto.user;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.yunpicturebackend.common.PageRequest;

import java.io.Serializable;

/**
 * 用户列表查询请求参数封装类 (DTO)
 * <p>
 * 【设计原理】
 * 1. 继承 PageRequest：复用基础的分页参数（如 current, pageSize, sortField, sortOrder），避免重复定义。
 * 2. 动态查询：此类中的所有字段通常都是可选的（非必填）。MyBatis Plus 等 ORM 框架会根据这些字段是否为空，
 * 动态拼接 SQL（例如：如果 userName 不为空，则拼接 `LIKE '%userName%'`）。
 */
@EqualsAndHashCode(callSuper = true) // 确保在调用 equals 和 hashCode 方法时，包含父类 (PageRequest) 的属性
@Data
public class UserQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户 ID（精确查询）
     */
    private Long id;

    /**
     * 用户昵称（通常支持模糊查询）
     */
    private String userName;

    /**
     * 账号（通常支持模糊查询或精确查询）
     */
    private String userAccount;

    /**
     * 个人简介（通常支持模糊查询）
     */
    private String userProfile;

    /**
     * 用户角色
     * <p>
     * 取值范围：user (普通用户), admin (管理员), ban (封号)
     */
    private String userRole;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}