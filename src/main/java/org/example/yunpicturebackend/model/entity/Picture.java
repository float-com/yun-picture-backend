package org.example.yunpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 图片信息表
 * @TableName picture
 */
@TableName(value ="picture")
@Data
public class Picture {
    /**
     * 主键 ID
     * [补充说明] 此处舍弃了数据库自增 ID（IdType.AUTO），改为使用 IdType.ASSIGN_ID。
     * 机制：底层采用基于时间戳的雪花算法（Snowflake）生成 64 位全局唯一长整型 ID。
     * 优势：避免了自增 ID 在对外暴露时泄露业务增长规模，同时也为后续潜在的微服务化、分库分表架构提供了天然的分布式主键支持。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 图片访问 URL（通常存储 OSS/COS 对象存储的访问链接或 CDN 加速链接）
     */
    private String url;

    /**
     * 图片名称（用于前端界面展示和基础搜索）
     */
    private String name;

    /**
     * 图片简介（详细描述图片内容，用于前端展示和详情说明）
     */
    private String introduction;

    /**
     * 图片分类（用于图库频道的分类筛选，如：风景、人物、动漫等）
     */
    private String category;

    /**
     * 图片标签（以 JSON 数组格式存储，方便附加多个标签进行多维度标记）
     */
    private String tags;

    /**
     * 图片体积（单位：字节/Byte，用于存储容量统计和上传大小限制）
     */
    private Long picSize;

    /**
     * 图片宽度（单位：像素/px，用于前端瀑布流等布局预留空间，防止页面抖动）
     */
    private Integer picWidth;

    /**
     * 图片高度（单位：像素/px，用于计算比例和页面排版）
     */
    private Integer picHeight;

    /**
     * 图片宽高比例（宽度除以高度的值，用于前端快速适配和骨架屏展示）
     */
    private Double picScale;

    /**
     * 图片格式（如 png, jpg, webp 等，用于格式过滤和图像处理逻辑）
     */
    private String picFormat;

    /**
     * 创建用户 ID（关联 user 表主键，标识图片的上传者/拥有者，用于权限控制和个人图库）
     */
    private Long userId;

    /**
     * 记录创建时间（系统插入数据时自动写入）
     */
    private Date createTime;

    /**
     * 资料最后手动编辑时间（记录用户主动修改图片元数据的时间）
     */
    private Date editTime;

    /**
     * 记录最后更新时间（底层行数据变更时 MySQL 自动触发更新）
     */
    private Date updateTime;

    /**
     * 逻辑删除标志：0-正常未删除 / 1-已删除
     * [补充说明] @TableLogic 为框架的逻辑删除拦截器标识。
     * 机制：标识后，MyBatis-Plus 底层的框架调用将发生行为重载。
     * 1. 物理删除转逻辑更新：执行 BaseMapper.deleteById() 时，底层 SQL 会被重写为 UPDATE picture SET isDelete = 1 WHERE id = ?。
     * 2. 查询自动过滤：执行 SELECT 查询操作时，底层会自动在 WHERE 子句中拼接 isDelete = 0 的过滤条件，对业务代码透明。
     */
    @TableLogic
    private Integer isDelete;
}