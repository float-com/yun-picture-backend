package org.example.yunpicturebackend.model.dto.space;

import lombok.Data;
import java.io.Serializable;

/**
 * 图库空间创建请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端（如 C 端普通用户或后台管理员）在“新建图库空间”时提交的表单参数。
 * 设计说明：该类仅暴露允许用户侧初始化的基础属性。
 * 核心的配额控制字段（如 maxSize, maxCount）以及状态统计字段被刻意排除在外。
 * 机制：后端业务代码会根据此处的 spaceLevel（空间级别），从系统配置中加载对应的权益配额进行底层落库，
 * 从而严格防止用户通过抓包篡改参数来突破容量和数量限制。
 */
@Data
public class SpaceAddRequest implements Serializable {

    /**
     * 空间名称
     * <p>
     * 作用：用户自定义的空间展示标题，用于前端空间列表页的展示与标识。
     * - 业务限制：通常允许为空（后端业务逻辑可自动生成默认名称，如“默认私有空间”）；若传入则需符合系统的字符长度规范（底层数据库限制为 VARCHAR(128)），且建议接入敏感词过滤。
     */
    private String spaceName;

    /**
     * 空间类型
     * <p>
     * 作用：区分私有空间和团队空间（枚举值：0-私有空间; 1-团队空间）。
     * - 业务限制：若前端未传递，后端默认创建私有空间。
     */
    private Integer spaceType;

    /**
     * 空间级别
     * <p>
     * 作用：决定新创建空间的权益与配额策略（枚举值：0-普通版; 1-专业版; 2-旗舰版）。
     * - 业务限制：
     * 1. 默认降级：若前端未传递，后端应默认初始化为 0（普通版）。
     * 2. 权限校验：若传入高阶级别（1 或 2），后端必须配合严格的权限校验（如判断用户是否购买了 VIP 服务或是否拥有管理员角色），防止越权创建高级别空间。
     */
    private Integer spaceLevel;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
