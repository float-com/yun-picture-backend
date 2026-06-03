package org.example.yunpicturebackend.constant;

/**
 * 用户全局常量接口
 * <p>
 * 【设计原理】消除“魔法值”与集中管理：
 * 严禁在业务代码（如 Service 层或 Controller 层）中到处硬编码（Hardcode）像 "user_login" 或 "admin" 这样的字符串。
 * 1. 防患未然：避免纯手打引起的拼写错误（例如将 "admin" 错拼成 "admim"），这种错误编译器无法察觉，极易引发线上生产事故（如越权漏洞）。
 * 2. 提升可维护性：如果未来重构时需要更改 Session 的 Key，只需在此处修改一行代码，整个项目都会同步生效，符合“一处定义，多处复用”的原则。
 * <p>
 * （注：在 Java 语法规范中，interface 内声明的变量隐式自带 public static final 修饰符，因此常被用来定义全局常量。）
 */
public interface UserConstant {

    /**
     * 用户登录态存储键 (Session Key)
     * <p>
     * 【应用场景】
     * 用于 request.getSession().setAttribute() 和 getAttribute() 操作，
     * 作为在服务端内存（或分布式 Session 存储如 Redis）中标记当前用户登录凭证的唯一标识。
     */
    String USER_LOGIN_STATE = "user_login";

    // ==================== region 角色权限配置 ====================
    // 【架构提示】基于角色的访问控制 (RBAC, Role-Based Access Control)：
    // 以下常量通常配合自定义的 AOP 注解（例如 @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)）
    // 或者全局拦截器使用，用于实现接口级别的权限鉴权。

    /**
     * 默认普通用户角色
     * <p>
     * 新用户注册时系统默认赋予的角色，通常仅拥有查看、修改自身数据等基础权限。
     */
    String DEFAULT_ROLE = "user";

    /**
     * 系统管理员角色
     * <p>
     * 拥有系统最高权限，通常用于放行后台管理界面的专属接口，允许进行敏感数据的全量查看、物理删除或对其他用户的封禁操作。
     */
    String ADMIN_ROLE = "admin";

}