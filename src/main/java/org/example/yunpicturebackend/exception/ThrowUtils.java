package org.example.yunpicturebackend.exception;

/**
 * 异常处理断言工具类
 * <p>
 * 用于简化代码中的条件判断与异常抛出逻辑（将 if-throw 简化为单行代码），提升业务代码的可读性。
 */
public class ThrowUtils {

    /**
     * 校验条件，如果条件成立则抛出指定的运行时异常
     *
     * @param condition        触发异常的条件（传入 true 时抛出）
     * @param runtimeException 待抛出的运行时异常实例
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 校验条件，如果条件成立则根据指定错误码抛出业务异常
     *
     * @param condition 触发异常的条件
     * @param errorCode 业务错误码枚举
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        throwIf(condition, new BusinessException(errorCode));
    }

    /**
     * 校验条件，如果条件成立则根据错误码和自定义信息抛出业务异常
     *
     * @param condition 触发异常的条件
     * @param errorCode 业务错误码枚举
     * @param message   自定义的错误描述信息（用于覆盖默认的错误码信息）
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String message) {
        throwIf(condition, new BusinessException(errorCode, message));
    }
}