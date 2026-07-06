package org.example.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 图库空间类型枚举类
 * <p>
 * 业务场景：用于区分系统中不同类型的图库空间，以支持差异化的业务流转与权限隔离策略。
 * 设计说明：目前系统将图库划分为“个人独享”与“多人协作”两种基础形态，该枚举作为底层核心标识，
 * 决定了图片操作时的权限校验路由（例如：私有空间走属主强隔离，团队空间走 RBAC 角色权限）。
 */
@Getter
public enum SpaceTypeEnum {

    /**
     * 私有空间
     * 业务规则：面向个人用户的独立专属图库，遵循极其严格的“数据孤岛”隐私隔离策略。
     * 仅允许空间创建者本人进行操作，其他任何人（通常包含全站超级管理员）均无权窥探或越权篡改。
     */
    PRIVATE("私有空间", 0),

    /**
     * 团队空间
     * 业务规则：面向多人协作的共享型图库。
     * 允许多个用户加入同一个空间，并根据分配的团队角色（如空间管理员、编辑者、查看者）协同管理空间内的图片资产。
     */
    TEAM("团队空间", 1);

    /**
     * 空间类型描述文本（主要用于前端 UI 标识渲染、操作日志记录或异常提示文案）
     */
    private final String text;

    /**
     * 空间类型实际值（主要用于数据库 spaceType 字段物理存储以及底层核心业务分支判断）
     */
    private final int value;

    /**
     * 构造函数：初始化空间类型枚举实例的属性映射
     *
     * @param text  空间类型描述文本
     * @param value 空间类型实际标识值
     */
    SpaceTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 状态值(value) 获取对应的枚举实例
     *
     * @param value 传入的类型标识（例如：0, 1）
     * @return 匹配的 SpaceTypeEnum 枚举实例；未匹配或传入为空则返回 null
     */
    public static SpaceTypeEnum getEnumByValue(Integer value) {
        // 1. 防御性编程：拦截空值请求，避免后续自动拆箱引发 NullPointerException 或无效的循环遍历
        if (ObjUtil.isEmpty(value)) {
            return null;
        }

        // 2. 遍历所有空间类型枚举项，通过比对 value 找到对应的枚举对象
        for (SpaceTypeEnum spaceTypeEnum : SpaceTypeEnum.values()) {
            if (spaceTypeEnum.value == value) {
                return spaceTypeEnum;
            }
        }

        // 3. 未匹配到任何合法的枚举项，返回 null 兜底，交由上层调用方处理异常逻辑
        return null;
    }
}