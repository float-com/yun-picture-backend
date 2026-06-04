package org.example.yunpicturebackend.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.yunpicturebackend.annotation.AuthCheck;
import org.example.yunpicturebackend.exception.BusinessException;
import org.example.yunpicturebackend.exception.ErrorCode;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.enums.UserRoleEnum;
import org.example.yunpicturebackend.service.UserService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 权限校验 AOP 拦截器
 * <p>
 * 拦截带有 {@link AuthCheck} 注解的方法，进行全局权限校验。
 * 校验逻辑：先获取当前登录用户，再比对注解要求的角色与用户实际拥有的角色。
 * </p>
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截与权限校验逻辑
     *
     * @param joinPoint 切入点对象，包含了被拦截方法的信息和执行上下文
     * @param authCheck 目标方法上标注的权限校验注解信息
     * @return 目标方法的执行结果
     * @throws Throwable 认证失败抛出业务异常，或目标方法执行本身的异常
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 1. 获取当前登录用户（若未登录，userService 内部通常会抛出未登录异常）
        User loginUser = userService.getLoginUser(request);

        // 2. 获取注解要求达到的权限枚举
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByTextOrValue(mustRole);

        // 3. 如果注解未指定特定的强制权限要求（如默认值 ""），则视为“仅需登录即可访问”，直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // --- 以下为：必须有特定角色权限才能通过 ---

        // 4. 获取当前用户实际拥有的角色权限枚举
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByTextOrValue(loginUser.getUserRole());

        // 5. 如果当前用户没有任何合法角色映射，直接拒绝访问
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 6. 具体的权限越权校验：
        // 要求必须有管理员权限，但当前用户不是管理员，拒绝访问
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 7. 满足所有权限要求，放行并执行目标方法
        return joinPoint.proceed();
    }
}