package org.example.yunpicturebackend.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户创建请求参数封装类 (DTO)
 * <p>
 * 【业务场景】
 * 与普通的“用户注册 (UserRegisterRequest)”不同，此 DTO 通常用于后台管理系统中，
 * 由管理员直接手动创建新用户。因此，它允许在创建时直接指定用户的各项详细信息（包含角色、头像等），
 * 而普通注册接口通常只允许传入账号和密码。
 */
@Data
public class UserAddRequest implements Serializable {

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户账号（登录凭证）
     */
    private String userAccount;

    /**
     * 用户头像 URL
     */
    private String userAvatar;

    /**
     * 用户个人简介
     */
    private String userProfile;

    /**
     * 用户角色
     * <p>
     * 取值范围参考 UserRoleEnum：user (普通用户), admin (管理员)
     */
    private String userRole;

    /**
     * 序列化版本号
     * <p>
     * 显式固定一个 serialVersionUID，保证在跨网络传输或对象状态持久化时反序列化的兼容性。
     */
    private static final long serialVersionUID = 1L;
}