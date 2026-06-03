package org.example.yunpicturebackend.exception;

import lombok.Getter;

/**
 * 自定义错误码枚举类
 * <p>
 * 错误码设计规范：
 * 1. 前缀与标准的 HTTP 响应状态码保持一致，便于快速定位问题；
 * 2. 错误码之间预留适当数字间隔（如 40100、40101），以提升后续业务的可扩展性。
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "ok"),
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
