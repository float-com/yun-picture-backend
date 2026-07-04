package org.example.yunpicturebackend.model.dto.space;

import lombok.Data;
import java.io.Serializable;

/**
 * 图库空间更新请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收后台管理系统（Admin 端）在“全量更新/强制修改空间信息”时提交的表单参数。
 * 设计说明：相较于 EditRequest，该类暴露了系统的底层核心控制字段。
 * 机制：调用此相关的接口必须经过严格的管理员角色鉴权。管理员可通过此接口强制干预某个图库空间的容量配额或级别。
 */
@Data
public class SpaceUpdateRequest implements Serializable {

    /**
     * 空间 ID
     * <p>
     * 作用：用于定位需要更新的目标空间。
     * - 业务限制：【必传项】。
     */
    private Long id;

    /**
     * 空间名称
     * <p>
     * 作用：管理员修改或纠正违规的空间名称。
     */
    private String spaceName;

    /**
     * 空间级别
     * <p>
     * 作用：管理员手动调整该空间的权益等级（0-普通版 1-专业版 2-旗舰版）。
     * - 业务说明：变更级别后，业务层通常需要根据新级别，自动重新计算并覆写下方的 maxSize 和 maxCount 字段。
     */
    private Integer spaceLevel;

    /**
     * 空间图片的最大总大小（容量配额）
     * <p>
     * 作用：强制覆盖当前空间的存储容量上限（单位：字节/Byte）。
     * - 业务说明：支持管理员针对特殊用户进行“破格”扩容或降级惩罚，解耦全局统一配置。
     */
    private Long maxSize;

    /**
     * 空间图片的最大数量（数量配额）
     * <p>
     * 作用：强制覆盖当前空间允许上传的文件数量上限。
     */
    private Long maxCount;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}