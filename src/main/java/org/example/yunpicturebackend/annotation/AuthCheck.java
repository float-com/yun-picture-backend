package org.example.yunpicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验自定义注解
 * <p>
 * 用于在 Controller 等方法级别进行用户角色控制。
 * 底层由 {@link org.example.yunpicturebackend.aop.AuthInterceptor} 提供 AOP 拦截支持。
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 访问该方法必须具备的角色
     * <p>
     * <b>校验规则：</b><br>
     * 1. 默认情况下（为空字符串），拦截器会校验用户是否已登录，但不限制具体角色，所有已登录用户均可访问。<br>
     * 2. 指定具体角色时（如 {@code @AuthCheck(mustRole = "admin")}），则要求当前登录用户必须具备对应角色。
     * </p>
     *
     * @return 要求的角色标识（对应 UserRoleEnum 中的 value）
     */
    String mustRole() default "";
}