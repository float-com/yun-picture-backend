package org.example.yunpicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 通用用户信息视图对象 (VO)
 * <p>
 * 【业务场景】
 * 与代表“当前正在登录用户”的 LoginUserVO 不同，此 VO 通常用于更加通用的场景：
 * 例如：在后台管理系统中分页展示用户列表、在前端展示某个作者的公开个人主页等。
 * <p>
 * 【设计原理】
 * 1. 数据脱敏：作为向前端暴露的通用对象，严格剔除了 User 实体类中的密码、盐值、逻辑删除等敏感及系统级字段。
 * 2. 复用与解耦：独立出通用的 UserVO，可以保证后续底层数据库表结构（User 实体）发生变动时，
 * 只要前端展示需求不变，接口返回的数据结构就能保持稳定，做到前后端解耦。
 */
@Data
public class UserVO implements Serializable {

    /**
     * 用户全局唯一主键 ID
     */
    private Long id;

    /**
     * 用户账号
     * <p>
     * 注意：在某些严格面向外部游客的公开主页场景中，如果不希望暴露用户的登录账号，
     * 可以在通过 DTO/Entity 转换到此 VO 时对该字段进行脱敏（如：打码处理）或置空。
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
     * 【业务用途】
     * 1. 在管理后台列表中：用于区分和高亮展示不同权限级别的用户。
     * 2. 在公开展示中：前端可据此字段在用户昵称旁挂载“管理员”、“官方人员”等专属身份徽章（Badge）。
     */
    private String userRole;

    /**
     * 账号创建时间
     * <p>
     * 常用于前端展示用户的“注册天数”或列表排序。
     */
    private Date createTime;

    /**
     * 序列化版本控制标识
     * <p>
     * 保证该对象在跨网络传输（如 RPC 接口调用）或对象缓存时，反序列化的版本兼容性，防止抛出 InvalidClassException。
     */
    private static final long serialVersionUID = 1L;
}