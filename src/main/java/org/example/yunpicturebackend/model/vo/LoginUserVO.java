package org.example.yunpicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 已登录用户信息视图对象 (VO)
 * <p>
 * 【设计原理】为什么要使用 VO 而不是直接返回 User 实体类？
 * 1. 数据脱敏与绝对安全：数据库中的 User 实体往往包含密码 (password)、加密盐值 (salt)、逻辑删除状态 (isDelete) 等绝对不能暴露给前端的字段。VO 充当了“滤网”，只暴露允许被外部看到的信息。
 * 2. 减少网络开销：按需按端返回数据，剔除前端不需要的冗余字段，使接口响应报文更小、更轻量。
 */
@Data
public class LoginUserVO implements Serializable {

    /**
     * 用户全局唯一主键 ID
     */
    private Long id;

    /**
     * 用户登录账号
     */
    private String userAccount;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像 URL 地址
     */
    private String userAvatar;

    /**
     * 用户个人简介
     */
    private String userProfile;

    /**
     * 用户角色（例如：user-普通用户, admin-管理员）
     * <p>
     * 【设计原理】前端动态路由与权限控制：
     * 前端拿到此字段后，会根据具体的角色值动态生成侧边栏菜单（路由表），
     * 或者在页面上控制“删除”、“封号”等高危操作按钮的显隐。
     */
    private String userRole;

    /**
     * 账号创建时间
     */
    private Date createTime;

    /**
     * 账号最后更新时间
     */
    private Date updateTime;

    /**
     * 序列化版本控制标识
     * <p>
     * 保证该对象在跨网络传输或存入分布式缓存（如 Redis 中缓存当前登录用户信息）时，反序列化的版本兼容性。
     */
    private static final long serialVersionUID = 1L;
}