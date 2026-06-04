package org.example.yunpicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户信息更新请求参数封装类 (DTO)
 * <p>
 * 【设计原理】
 * 1. 明确标识：必须包含 id 字段，用于定位数据库中需要被更新的唯一记录。
 * 2. 按需更新：除 id 外的其它字段通常是可选的。业务逻辑层一般会做判空处理，
 * 前端传了哪个字段，就只更新该字段（局部更新），未传的字段保持数据库原有值不变。
 */
@Data
public class UserUpdateRequest implements Serializable {

    /**
     * 被更新用户的唯一 ID
     * <p>
     * 【重要】此字段必填，作为更新操作的 Where 条件依据。
     */
    private Long id;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像 URL
     */
    private String userAvatar;

    /**
     * 个人简介
     */
    private String userProfile;

    /**
     * 用户角色
     * <p>
     * 取值范围：user (普通用户), admin (管理员)。通常仅限管理员操作此字段。
     */
    private String userRole;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}