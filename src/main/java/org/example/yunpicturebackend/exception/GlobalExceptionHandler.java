package org.example.yunpicturebackend.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.ResultUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * <p>
 * 【设计原理】基于 Spring AOP 思想：
 * 使用 @RestControllerAdvice 全局拦截 Controller 层抛出的异常。
 * 将异常集中处理、统一记录日志，并最终包装成标准的 BaseResponse 返回给前端。
 * 这样可以避免前端收到大段难以解析的服务器报错堆栈（如默认的 500 错误页面），提升系统健壮性。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 捕获并处理自定义业务异常
     * <p>
     * 【设计原理】处理“预期内”的异常：
     * BusinessException 是我们在代码中主动断言并抛出的异常（如参数校验不通过、无权限等）。
     * 此时需要提取异常内部携带的精准错误码和定制化提示信息，原样返回给前端，以指导用户操作。
     *
     * @param e 捕获到的自定义业务异常对象
     * @return 包含具体业务错误码和提示信息的标准响应
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 捕获并处理未知的运行时异常
     * <p>
     * 【设计原理】处理“预期外”的异常与安全兜底：
     * RuntimeException 通常代表代码 Bug（如 NullPointerException）或基础设施故障（如数据库挂了）。
     * 1. 保护系统安全：绝不能将真实的报错堆栈抛给前端，防止泄露底层框架或数据库细节；
     * 2. 保障用户体验：统一转换为友好的“系统错误”提示；
     * 3. 便于排查问题：在服务器后台通过日志记录完整的异常堆栈信息，供开发人员进行事后溯源。
     *
     * @param e 捕获到的系统运行时异常对象
     * @return 包含系统统一错误码（50000）和兜底提示的标准响应
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}