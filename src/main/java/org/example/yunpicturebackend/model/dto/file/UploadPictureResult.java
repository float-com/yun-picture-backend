package org.example.yunpicturebackend.model.dto.file;

import lombok.Data;

/**
 * 图片上传结果封装类 (DTO)
 * <p>
 * 业务场景：当文件成功上传到 COS（对象存储）并且完成图片解析后，服务层将抽取的核心元数据封装到此对象中返回。
 * 作用：作为内部数据传输对象，它起到了承上启下的作用——既是对外层（Controller）上传结果的响应，也为后续构造 Picture 实体类入库提供了基础数据。
 */
@Data
public class UploadPictureResult {

    /** * 图片访问地址 (URL)
     * <p>
     * 说明：通常是对象存储（如腾讯云 COS）生成的公网可访问链接，或经过 CDN 加速的域名链接。
     */
    private String url;

    /**
     * 图片缩略图访问地址 (URL)
     * <p>
     * 说明：通常是上传原图时，通过对象存储图片处理服务（如腾讯云 COS 数据万象）同步或异步处理生成的较小尺寸缩略图访问链接。
     */
    private String thumbnailUrl;

    /** * 图片名称
     * <p>
     * 说明：通常为提取的原始文件名（包含扩展名），用于在前端图库列表中展示。
     */
    private String picName;

    /** * 文件体积
     * <p>
     * 单位：字节 (Byte)。
     * 说明：用于前端展示文件大小（可转换为 KB/MB）以及后端的存储容量统计。
     */
    private Long picSize;

    /** * 图片宽度
     * <p>
     * 单位：像素 (px)。
     * 说明：用于前端在图片实际加载完成前预留占位空间，防止页面布局在图片渲染时发生剧烈抖动（Layout Shift）。
     */
    private int picWidth;

    /** * 图片高度
     * <p>
     * 单位：像素 (px)。
     * 说明：配合宽度共同完成前端的排版计算。
     */
    private int picHeight;

    /** * 图片宽高比例
     * <p>
     * 计算公式：宽度 / 高度 (picWidth / picHeight)。
     * 说明：在前端使用瀑布流布局（Masonry Layout）或响应式缩放时，通过该比例可以快速计算出不同容器下的自适应高度。
     */
    private Double picScale;

    /** * 图片格式
     * <p>
     * 示例：png, jpg, jpeg, webp 等。
     * 说明：从文件头或扩展名中提取，用于后续按格式筛选，或判断是否需要进行格式转换（如统一转为 webp 降低带宽消耗）。
     */
    private String picFormat;

}