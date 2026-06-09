package org.example.yunpicturebackend.model.dto.picture;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.example.yunpicturebackend.common.PageRequest;

import java.io.Serializable;
import java.util.List;

/**
 * 图片查询请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端在图片广场、管理后台等页面进行“条件检索”和“分页查询”时提交的参数。
 * 设计说明：该类继承自 {@link PageRequest}，默认具备分页能力。
 * 提供了丰富的查询维度，支持基本信息检索、物理元数据筛选、多标签组合检索，以及多字段的聚合搜索。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PictureQueryRequest extends PageRequest implements Serializable {

    /** * 图片 ID
     * <p>
     * 作用：根据唯一标识进行精确查询。
     * - 业务限制：通常用于特定场景下的精准定位，优先级最高。
     */
    private Long id;

    /** * 图片名称
     * <p>
     * 作用：根据用户设置的图片名称进行检索。
     * - 业务限制：通常作为模糊查询（LIKE）条件处理。
     */
    private String name;

    /** * 图片简介 / 描述
     * <p>
     * 作用：根据图片的详细描述内容进行检索。
     * - 业务限制：通常作为模糊查询（LIKE）条件处理。
     */
    private String introduction;

    /** * 图片分类
     * <p>
     * 作用：检索特定分类（如：壁纸、头像等）下的图片列表。
     * - 业务限制：通常作为精确匹配（=）条件处理。
     */
    private String category;

    /** * 图片标签列表
     * <p>
     * 作用：支持通过多标签组合筛选图片（如：同时包含“高清”和“动漫”的图片）。
     * - 业务限制：在 SQL 构建时，需要解析为包含匹配或集合交集查询。
     */
    private List<String> tags;

    /** * 文件体积 (单位：字节)
     * <p>
     * 作用：根据文件大小进行筛选。
     * - 业务限制：基础为精确匹配，若业务需要可配合其他参数扩展为范围查询（如大小于某阈值）。
     */
    private Long picSize;

    /** * 图片宽度 (像素)
     * <p>
     * 作用：筛选特定宽度的图片。
     */
    private Integer picWidth;

    /** * 图片高度 (像素)
     * <p>
     * 作用：筛选特定高度的图片。
     */
    private Integer picHeight;

    /** * 图片宽高比例
     * <p>
     * 作用：根据图片的长宽比（如 16:9, 4:3）进行筛选，常用于适配特定屏幕尺寸的场景。
     */
    private Double picScale;

    /** * 图片格式
     * <p>
     * 作用：检索特定封装格式的图片（如：webp, png, jpg）。
     * - 业务限制：精确匹配，需忽略大小写差异。
     */
    private String picFormat;

    /** * 聚合搜索词
     * <p>
     * 作用：前端顶部搜索框的通用输入源，用于提升用户体验。
     * - 业务限制：在服务层需将该字段解析为多字段的 OR 条件查询（例如：名称 LIKE search OR 简介 LIKE search）。
     */
    private String searchText;

    /** * 上传用户 ID
     * <p>
     * 作用：检索特定用户上传的所有图片资产。
     * - 业务限制：常用于“我的空间”功能或管理员审查指定用户数据时使用。
     */
    private Long userId;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}