package org.example.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户角色枚举类
 */
@Getter
public enum UserRoleEnum {

    /**
     * 普通用户
     */
    USER("用户", "user"),

    /**
     * 管理员
     */
    ADMIN("管理员", "admin");

    /**
     * 角色描述文本（主要用于前端展示或日志记录）
     */
    private final String text;

    /**
     * 角色实际值（主要用于数据库存储或业务逻辑判断）
     */
    private final String value;

    /**
     * 静态枚举映射缓存
     * 统一缓存 text 和 value 的映射，支持双向查找
     */
    private static final Map<String, UserRoleEnum> CACHE_MAP = new HashMap<>();

    /*
     * 静态代码块
     * 将 value 和 text 都存入 Map 中，指向同一个枚举对象
     */
    static {
        for (UserRoleEnum roleEnum : UserRoleEnum.values()) {
            // 以 value 为 key (例如: "admin" -> ADMIN)
            CACHE_MAP.put(roleEnum.getValue(), roleEnum);
            // 以 text 为 key (例如: "管理员" -> ADMIN)
            CACHE_MAP.put(roleEnum.getText(), roleEnum);
        }
    }

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 角色值(value) 或 角色描述(text) 获取对应的枚举实例
     *
     * @param keyword 传入的标识（"admin" 或 "管理员"均可）
     * @return 匹配的 UserRoleEnum 枚举实例；未匹配则返回 null
     */
    public static UserRoleEnum getEnumByTextOrValue(String keyword) {
        if (ObjUtil.isEmpty(keyword)) {
            return null;
        }
        // 无论传入的是 text 还是 value，都能在 O(1) 时间内从 Map 中精准捞出
        return CACHE_MAP.get(keyword);
    }
}