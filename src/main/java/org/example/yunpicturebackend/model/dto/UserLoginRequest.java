package org.example.yunpicturebackend.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求参数封装类 (DTO)
 * <p>
 * 【设计原理】为什么要专门写一个 DTO 类，而不是直接用 User 实体类接收请求？
 * 1. 安全隔离：User 实体类直接映射数据库表，包含很多底层敏感字段（如密码盐值 salt、权限角色 role、逻辑删除状态等）。
 * 如果直接用 User 类接收前端参数，存在“批量赋值/过度发布 (Mass Assignment)”漏洞，恶意用户可能会在请求体中伪造这些敏感字段。
 * 独立出 DTO 可以严格限制前端只能传递我们允许的参数（仅账号和密码）。
 * 2. 职责单一：使得 Controller 层的接口契约非常明确，看一眼类名就知道这是专门用于登录的请求载体。
 */
@Data
public class UserLoginRequest implements Serializable {

    /**
     * 序列化版本号
     * <p>
     * 【设计原理】
     * 显式固定一个 serialVersionUID，防止该对象在跨网络传输（如微服务 RPC 调用）过程中，
     * 因后续给此类增加或删除了某个字段，导致系统认为类版本不一致从而抛出 InvalidClassException 反序列化异常。
     */
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户密码
     */
    private String userPassword;
}