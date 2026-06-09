package org.example.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片编辑请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端（通常是 C 端普通用户或创作者中心）在“编辑图片”时提交的表单参数。
 * 设计说明：该类主要支持对图片基础元数据的局部修改。
 * 与 Update 相比，Edit 操作通常需要配合更严格的【权限校验】（如：仅允许图片拥有者或管理员进行编辑），
 * 且前端可能只传递需要修改的字段，未传递的字段后端需保持原值不变。
 */
@Data
public class PictureEditRequest implements Serializable {

    /** * 图片 ID
     * <p>
     * 作用：用于定位被编辑的目标图片记录。
     * - 业务限制：【必传项】。系统将依赖该 ID 检索图片，并进行操作权限校验（越权拦截）。
     */
    private Long id;

    /** * 图片名称
     * <p>
     * 作用：用户重新定义的图片展示标题。
     * - 业务限制：若传递则更新，通常需满足系统的字符长度限制及敏感词过滤要求。
     */
    private String name;

    /** * 图片简介 / 描述
     * <p>
     * 作用：更新图片的详细补充说明内容。
     * - 业务限制：支持长文本，修改后可用于优化全文检索（如 ES）的命中率。
     */
    private String introduction;

    /** * 图片分类
     * <p>
     * 作用：修改图片所属的业务专区或分类。
     * - 业务限制：需校验传入的分类名称是否在当前系统预设的有效分类列表中。
     */
    private String category;

    /** * 图片标签列表
     * <p>
     * 作用：更新图片的分类标签集，通常用于替换旧标签（全量覆盖更新）。
     * - 业务限制：需限制最大标签数量，并对用户输入的自定义标签进行去重和非法字符清洗。
     */
    private List<String> tags;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}