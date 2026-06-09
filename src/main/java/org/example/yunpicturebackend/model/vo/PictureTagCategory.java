package org.example.yunpicturebackend.model.vo;

import lombok.Data;

import java.util.List;

/**
 * 图片标签与分类聚合视图对象 (VO)
 * <p>
 * 业务场景：作为配置或字典类数据模型，用于向前端一次性暴露系统当前支持的所有标签和分类选项。
 * 作用域：通常仅用于数据展示层，不参与底层的数据库逻辑。
 */
@Data
public class PictureTagCategory {

    /**
     * 系统预设的图片标签列表
     * <p>
     * 业务说明：用于对图片进行多维度的细粒度描述（如图元特征、风格等）。
     * 示例：["热门", "高清", "艺术"]
     */
    private List<String> tagList;

    /**
     * 系统预设的图片分类列表
     * <p>
     * 业务说明：用于定义图片所属的宏观业务板块或专区。
     * 示例：["模板", "电商", "表情包"]
     */
    private List<String> categoryList;
}