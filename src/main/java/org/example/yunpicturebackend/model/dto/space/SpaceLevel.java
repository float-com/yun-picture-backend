package org.example.yunpicturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 图库空间级别信息封装类 (DTO)
 * <p>
 * 业务场景：用于向前端暴露系统当前支持的空间级别、展示文案以及对应的容量/数量配额。
 * 设计说明：该类仅作为枚举转换后的只读展示数据载体，不参与数据库的持久化过程。
 * 核心目的在于将后端配置的空间权益规则统一动态下发，避免前端硬编码导致的前后端权益脱节。
 */
@Data
@AllArgsConstructor
public class SpaceLevel implements Serializable {

    /**
     * 空间级别值
     * <p>
     * 作用：系统底层约定的级别数字标识（如 0-普通版, 1-专业版, 2-旗舰版）。
     * - 业务说明：作为核心标识，用于前后端进行逻辑判断、表单提交和接口交互时的传参依据。
     */
    private int value;

    /**
     * 空间级别文案
     * <p>
     * 作用：对应空间级别的中文展示名称。
     * - 业务说明：直接提供给前端用于 UI 渲染（如升级弹窗、下拉选项），降低前端的文字维护与解析成本。
     */
    private String text;

    /**
     * 最大图片数量配额
     * <p>
     * 作用：定义该级别空间内允许上传的总图片张数上限。
     * - 业务限制：仅用于向用户展示当前权益边界。实际防超卖与限制逻辑由后端业务代码及数据库并发控制共同保障。
     */
    private long maxCount;

    /**
     * 最大空间容量配额
     * <p>
     * 作用：定义该级别空间内允许占用的总物理存储空间上限（通常以 Byte 为单位）。
     * - 业务限制：与 maxCount 共同构成空间的核心权益。前端可基于此字段与当前已用量计算并展示空间进度条。
     */
    private long maxSize;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}