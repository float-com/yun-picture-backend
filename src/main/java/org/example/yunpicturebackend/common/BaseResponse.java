package org.example.yunpicturebackend.common;

import lombok.Data;
import org.example.yunpicturebackend.exception.ErrorCode;

import java.io.Serializable;

/**
 * 全局统一响应封装类
 * <p>
 * 用于统一后端接口的返回数据结构，确保前端接收到的数据格式保持一致，
 * 包含状态码、业务数据和响应提示信息。
 * <p>
 * 【设计原理】为什么使用泛型 <T>？
 * 1. 类型安全与复用：由于不同接口返回的数据类型千差万别（比如返回 User 对象、List 集合或者 Boolean），
 * 使用泛型 T 可以让 data 字段动态适应任何类型，避免将其定义为 Object 导致在使用时需要频繁且不安全的强制类型转换。
 *
 * @param <T> 响应业务数据的类型
 */
@Data
public class BaseResponse<T> implements Serializable {

    /**
     * 响应状态码（如：0 表示成功，其他对应具体的业务错误码）
     */
    private int code;

    /**
     * 响应的业务数据
     */
    private T data;

    /**
     * 响应提示信息（如成功提示或具体的错误描述）
     */
    private String message;

    /**
     * 全参构造函数 (核心构造器)
     *
     * @param code    状态码
     * @param data    业务数据
     * @param message 提示信息
     */
    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    /**
     * 部分参数构造函数
     * <p>
     * 【设计原理】为什么使用 this(code, data, "")？
     * 1. 构造器复用（DRY原则）：通过 this() 调用本类的全参构造器，将赋值逻辑集中在一处管理。
     * 如果未来需要修改基础赋值逻辑，只需修改全参构造器即可，降低维护成本。
     * 2. 提供默认值：当接口成功调用且不需要额外提示信息时，默认提供空字符串，避免前端拿到 null 导致空指针异常。
     *
     * @param code 状态码
     * @param data 业务数据
     */
    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    /**
     * 基于错误码枚举的构造函数
     * <p>
     * 【设计原理】为什么写成 this(errorCode.getCode(), null, errorCode.getMessage())？
     * 1. 也是基于 this() 的构造器复用机制。
     * 2. 为什么 data 传 null：当系统抛出错误（如未登录、参数错误）时，说明业务逻辑被中断，
     * 此时并没有有效的业务数据返回给前端，因此强行将 data 置为 null 是符合逻辑的。
     * 3. 规范错误响应：直接接收 ErrorCode 枚举，强制要求开发者使用预定义的错误码和错误信息，防止硬编码带来的混乱。
     *
     * @param errorCode 自定义错误码枚举对象
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}