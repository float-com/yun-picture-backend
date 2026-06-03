package org.example.yunpicturebackend.common;

import org.example.yunpicturebackend.exception.ErrorCode;

/**
 * 全局统一响应工具类 (工厂模式)
 * <p>
 * 提供静态方法用于快捷构建标准的响应对象（成功或失败），
 * 使得 Controller 层代码更加简洁优雅，避免每次都手动 new BaseResponse()。
 */
public class ResultUtils {

    /**
     * 构建成功响应
     * <p>
     * 【设计原理】为什么使用泛型方法 <T>？
     * 配合 BaseResponse 的泛型设计，使得编译器能自动推断传入的 data 类型。
     * 这样 Controller 返回的对象类型明确，保证了类型安全。
     *
     * @param data 业务数据
     * @param <T>  业务数据的具体类型
     * @return 包含成功状态码（0）、默认提示（ok）和业务数据的标准响应对象
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, data, "ok");
    }

    /**
     * 构建失败响应（基于错误码枚举）
     * <p>
     * 【设计原理】为什么失败响应返回 BaseResponse<?>？
     * 因为失败时业务流程已被中断，不会返回具体的业务数据（data 置为 null）。
     * 使用通配符 <?> 表示这里不关心具体的泛型类型，既符合语义，又能避免编译器的类型警告。
     *
     * @param errorCode 自定义错误码枚举
     * @return 包含枚举状态码和默认错误信息的标准响应对象
     */
    public static BaseResponse<?> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode);
    }

    /**
     * 构建失败响应（基于自定义状态码和自定义信息）
     * <p>
     * 【设计原理】方法重载提供灵活性：
     * 适用于一些无法提前预定义在枚举中，或者需要由第三方服务动态返回错误码的异常场景。
     *
     * @param code    自定义错误状态码
     * @param message 自定义错误提示信息
     * @return 包含自定义错误码和信息的标准响应对象
     */
    public static BaseResponse<?> error(int code, String message) {
        return new BaseResponse<>(code, null, message);
    }

    /**
     * 构建失败响应（基于错误码枚举 + 自定义信息）
     * <p>
     * 【设计原理】状态码复用与信息定制：
     * 复用了枚举中的标准状态码，但允许上层覆盖具体的错误描述。
     * （例如：枚举是大类“PARAMS_ERROR(40000)”，这里传入的具体 message 可以是“密码长度不能少于8位”，让提示更精准）。
     *
     * @param errorCode 自定义错误码枚举
     * @param message   用于覆盖枚举默认信息的具体错误提示
     * @return 包含枚举状态码和自定义信息的标准响应对象
     */
    public static BaseResponse<?> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), null, message);
    }
}