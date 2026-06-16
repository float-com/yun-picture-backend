package org.example.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片请求 DTO
 * <p>
 * 用于封装管理员或用户通过提供关键词，
 * 从外部图库批量抓取并导入图片时的请求参数。
 * </p>
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {

    /**
     * 搜索词
     * <p>指定需要抓取图片的关键字（例如："风景"、"编程"等），直接用于拼接目标搜索引擎的查询 URL。</p>
     */
    private String searchText;

    /**
     * 抓取数量
     * <p>单次批量抓取和导入的最大图片数量，默认值为 10 条，以防止单次抓取过多导致接口超时。</p>
     */
    private Integer count = 10;

    /**
     * 图片名称前缀
     * <p>
     * 业务场景：用于在批量抓取时统一规范这批同主题图片的命名格式，提升图库的检索体验。
     * 联动逻辑：底层 Service 在遍历抓取时，会自动采用“前缀 + 连续自增序号”的规则（例如：风景1、风景2）生成 pictureName。
     * 容错机制：若前端未传递此值，后端将平滑降级，默认使用当前的 `searchText` 作为命名基础。
     * </p>
     */
    private String namePrefix;

    /**
     * 序列化版本号（用于保证跨服务或 Redis 缓存反序列化时的对象结构兼容性）
     */
    private static final long serialVersionUID = 1L;
}