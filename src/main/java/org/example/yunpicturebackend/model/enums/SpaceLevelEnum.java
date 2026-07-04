package org.example.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 图库空间级别枚举类
 * <p>
 * 业务场景：用于定义系统支持的空间权益等级，并直接绑定对应等级的容量与数量配额上限。
 * 将配额硬编码在枚举中，既能作为系统默认初始化的基准配置，又避免了复杂的关联查询。
 */
@Getter
public enum SpaceLevelEnum {

    /**
     * 普通版（新用户默认初始化的基础免费空间）
     * 配额限制：最多 100 张图片，总容量上限 100MB
     */
    COMMON("普通版", 0, 100, 100L * 1024 * 1024),

    /**
     * 专业版（面向进阶用户的付费或高级权益空间）
     * 配额限制：最多 1000 张图片，总容量上限 1000MB（约 1GB）
     */
    PROFESSIONAL("专业版", 1, 1000, 1000L * 1024 * 1024),

    /**
     * 旗舰版（面向高需求创作者或企业用户的顶配空间）
     * 配额限制：最多 10000 张图片，总容量上限 10000MB（约 10GB）
     */
    FLAGSHIP("旗舰版", 2, 10000, 10000L * 1024 * 1024);

    /**
     * 等级描述文本（主要用于前端展示、VIP 标识渲染或操作日志记录）
     */
    private final String text;

    /**
     * 等级实际值（主要用于数据库 spaceLevel 字段存储或底层业务逻辑判断）
     */
    private final int value;

    /**
     * 空间图片的最大总数量（限制当前级别空间内允许上传的最大图片张数）
     */
    private final long maxCount;

    /**
     * 空间图片的最大总大小（限制当前级别空间允许存储的总容量上限，单位：字节/Byte）
     */
    private final long maxSize;

    /**
     * 构造函数：初始化枚举实例的属性映射
     *
     * @param text     等级描述文本
     * @param value    等级实际标识值
     * @param maxCount 最大图片总数量配额
     * @param maxSize  最大容量总大小配额（字节）
     */
    SpaceLevelEnum(String text, int value, long maxCount, long maxSize) {
        this.text = text;
        this.value = value;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    /**
     * 根据 状态值(value) 获取对应的枚举实例
     *
     * @param value 传入的等级标识（例如：0, 1, 2）
     * @return 匹配的 SpaceLevelEnum 枚举实例；未匹配或传入为空则返回 null
     */
    public static SpaceLevelEnum getEnumByValue(Integer value) {
        // 防御性编程：拦截空值，避免后续自动拆箱引发 NullPointerException 或无效遍历
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历所有枚举项，通过比对 value 找到对应的枚举对象
        for (SpaceLevelEnum spaceLevelEnum : SpaceLevelEnum.values()) {
            if (spaceLevelEnum.value == value) {
                return spaceLevelEnum;
            }
        }
        // 未匹配到任何合法的枚举项，返回 null 兜底
        return null;
    }
}