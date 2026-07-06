package org.example.yunpicturebackend.model.dto.space;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.yunpicturebackend.common.PageRequest;
import java.io.Serializable;

/**
 * 图库空间查询请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端在“获取空间列表”时提交的检索条件与分页参数。
 * 设计说明：继承自 PageRequest 获取基础的分页能力（current, pageSize）。
 * 该类同时服务于 C 端（查询我的空间）和 B 端（管理员全局检索），业务层需根据用户角色动态拼接查询条件。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SpaceQueryRequest extends PageRequest implements Serializable {

    /**
     * 空间 ID
     * <p>
     * 作用：基于主键的精确查询。通常用于排查特定空间或数据回显。
     */
    private Long id;

    /**
     * 所属用户 ID
     * <p>
     * 作用：查询特定用户创建的所有空间。
     * - 安全防范：当 C 端普通用户调用时，后端应忽略此字段传入的值，强制覆盖为当前登录用户 ID；仅当管理员调用时，才允许根据此字段查询他人空间。
     */
    private Long userId;

    /**
     * 空间名称
     * <p>
     * 作用：基于空间名称的模糊检索（LIKE 查询）。用于前端搜索框匹配。
     */
    private String spaceName;

    /**
     * 空间类型
     * <p>
     * 作用：基于空间类型精确过滤（0-私有空间 1-团队空间）。
     */
    private Integer spaceType;

    /**
     * 空间级别
     * <p>
     * 作用：基于等级的精确过滤（0-普通版 1-专业版 2-旗舰版）。
     * - 业务场景：常用于后台管理员筛选出所有“旗舰版”用户以进行针对性运营，或系统级的数据统计分析。
     */
    private Integer spaceLevel;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}
