package org.example.yunpicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 图片审核状态枚举类
 */
@Getter
public enum PictureReviewStatusEnum {

    /**
     * 待审核状态（图片上传进入系统后的默认初始状态）
     */
    REVIEWING("待审核", 0),

    /**
     * 审核通过（允许图片在公共图库或前端正式展示）
     */
    PASS("通过", 1),

    /**
     * 审核拒绝（图片包含违规信息或不符合要求，被管理员驳回）
     */
    REJECT("拒绝", 2);

    /**
     * 状态描述文本（主要用于前端展示、审核反馈或日志记录）
     */
    private final String text;

    /**
     * 状态实际值（主要用于数据库 reviewStatus 字段存储或底层业务逻辑判断）
     */
    private final int value;

    PictureReviewStatusEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /** * 根据 状态值(value) 获取对应的枚举实例
     * * @param value 传入的状态标识（例如：0, 1, 2）
     * @return 匹配的 PictureReviewStatusEnum 枚举实例；未匹配或传入为空则返回 null
     */
    public static PictureReviewStatusEnum getEnumByValue(Integer value) {
        // 防御性编程：拦截空值，避免后续自动拆箱引发 NullPointerException 或无效遍历
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        // 遍历所有枚举项，通过比对 value 找到对应的枚举对象
        for (PictureReviewStatusEnum pictureReviewStatusEnum : PictureReviewStatusEnum.values()) {
            if (pictureReviewStatusEnum.value == value) {
                return pictureReviewStatusEnum;
            }
        }
        // 未匹配到任何合法的枚举项，返回 null 兜底
        return null;
    }
}