package org.example.yunpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 图库空间表
 * @TableName space
 */
@TableName(value ="space")
@Data
public class Space {
    /**
     * 主键 ID
     * [补充说明] 此处舍弃了数据库自增 ID（IdType.AUTO），改为使用 IdType.ASSIGN_ID。
     * 机制：底层采用基于时间戳的雪花算法（Snowflake）生成 64 位全局唯一长整型 ID。
     * 优势：避免了自增 ID 在对外暴露时泄露业务增长规模，同时也为后续潜在的微服务化、分库分表架构提供了天然的分布式主键支持。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 空间名称（用于前端空间列表展示、详情标识以及基础的模糊搜索）
     */
    private String spaceName;

    /**
     * 空间级别（枚举值：0-普通版; 1-专业版; 2-旗舰版。用于区分不同级别的服务权益和配额策略，使用整型可节约存储空间并提升查询效率）
     */
    private Integer spaceLevel;

    /**
     * 空间容量配额（单位：字节/Byte，限制该空间下允许存储的图片总大小最大值。独立字段解耦了代码硬编码，便于管理员针对特定空间进行动态限额调整）
     */
    private Long maxSize;

    /**
     * 空间数量配额（限制该空间下允许上传的图片最大总数量，用于控制单个空间的存储文件数上限，利于业务灵活扩展与动态调配）
     */
    private Long maxCount;

    /**
     * 当前已用容量（单位：字节/Byte，采用反范式冗余设计，实时记录当前空间下所有图片的总大小。避免每次上传时执行昂贵的 SUM 聚合查询，用于快速校验上传是否超限）
     */
    private Long totalSize;

    /**
     * 当前已有数量（采用反范式冗余设计，实时记录当前空间下的图片总数。避免每次上传执行 COUNT 聚合查询，配合 maxCount 进行高效的阈值拦截校验）
     */
    private Long totalCount;

    /**
     * 所属用户 ID（关联 user 表主键，标识该图库空间的创建者和唯一拥有者，用于实现私有图库的数据与权限隔离）
     */
    private Long userId;

    /**
     * 记录创建时间（系统插入数据时自动写入）
     */
    private Date createTime;

    /**
     * 资料最后手动编辑时间（记录用户主动修改空间信息的时间）
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
     * 1. 物理删除转逻辑更新：执行 BaseMapper.deleteById() 时，底层 SQL 会被重写为 UPDATE space SET isDelete = 1 WHERE id = ?。
     * 2. 查询自动过滤：执行 SELECT 查询操作时，底层会自动在 WHERE 子句中拼接 isDelete = 0 的过滤条件，对业务代码透明。
     */
    @TableLogic
    private Integer isDelete;
}