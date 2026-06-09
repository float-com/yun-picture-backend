package org.example.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片更新请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端在修改/编辑图片基本信息时提交的表单参数。
 * 设计说明：该类主要支持图片上传成功后的“元数据编辑”功能。
 * 使用者（如图片所有者或管理员）可以通过该请求修改图片的名称、简介、分类及标签等非文件属性，而图片的物理文件（URL、大小、宽高）在此过程中保持不变。
 */
@Data
public class PictureUpdateRequest implements Serializable {

    /** * 图片 ID
     * <p>
     * 作用：用于定位需要修改的图片记录。
     * - 业务限制：在更新操作中，此字段为【必传项】。系统将依赖该 ID 检索数据库中的原图片，若 ID 不存在或非法，更新操作将拒绝执行。
     */
    private Long id;

    /** * 图片名称
     * <p>
     * 作用：用户为图片设置的展示标题。
     * - 业务限制：通常具有长度限制（如 1~50 个字符），支持用户自定义修改。
     */
    private String name;

    /** * 图片简介 / 描述
     * <p>
     * 作用：对图片内容的详细补充说明。
     * - 业务限制：选填项。支持长文本输入，用于提升图片的可搜索性和可读性。
     */
    private String introduction;

    /** * 图片分类
     * <p>
     * 作用：图片所属的业务系统分类（如：壁纸、头像、素材等）。
     * - 业务限制：通常需要匹配系统后台预设的合法分类字典或枚举值。
     */
    private String category;

    /** * 图片标签列表
     * <p>
     * 作用：用于对图片进行多维度的贴签（如：["高清", "动漫", "古风"]），便于多条件检索。
     * - 业务限制：选填项。系统处理时需注意防范空集合（Empty List）或重复标签的处理。
     */
    private List<String> tags;

    /**
     * 序列化版本号（用于保证反序列化时的兼容性）
     */
    private static final long serialVersionUID = 1L;
}