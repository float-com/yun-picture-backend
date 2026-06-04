package org.example.yunpicturebackend.model.dto.user;

import lombok.Data;
import java.io.Serializable;

/**
 * 用户注册请求参数封装类 (DTO)
 * <p>
 * 专门用于接收并封装前端传递的用户注册表单数据。
 * * 为什么实现 Serializable 接口：
 * 向 JVM 声明该类的对象允许被序列化为二进制字节流，
 * 从而支持在网络中传输（如 Spring Cloud RPC 调用），或持久化到分布式缓存（如 Redis）中。
 * </p>
 */
@Data
public class UserRegisterRequest implements Serializable {

    /**
     * 序列化版本控制标识符
     * <p>
     * 显式定义此版本号，是为了保证在未来业务迭代中（如类的属性发生增删改）时，
     * 依然能够与旧版本缓存数据的序列化字节流保持向下/向上兼容，
     * 避免反序列化时抛出 InvalidClassException 异常引发线上故障。
     * </p>
     */
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户登录密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}