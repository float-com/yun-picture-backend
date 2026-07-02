package org.example.yunpicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 系统用户表
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID（使用 BIGINT 便于后期扩展为雪花算法等分布式 ID）
     * [补充说明] 此处舍弃了数据库自增 ID（IdType.AUTO），改为使用 IdType.ASSIGN_ID。
     * 机制：底层采用基于时间戳的雪花算法（Snowflake）生成 64 位全局唯一长整型 ID。
     * 优势：避免了自增 ID 在对外暴露时泄露业务增长规模，同时也为后续潜在的微服务化、分库分表架构提供了天然的分布式主键支持。
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 登录账号（系统内唯一，用于身份认证）
     */
    private String userAccount;

    /**
     * 登录密码（必须存储经 BCrypt 或单向哈希加盐后的密文，禁止明文）
     */
    private String userPassword;

    /**
     * 用户昵称（用于前端界面展示，允许重复）
     */
    private String userName;

    /**
     * 用户头像 URL（建议存储 OSS/COS 对象存储的 CDN 链接）
     */
    private String userAvatar;

    /**
     * 用户个人简介（长度限制在 512 字符以内）
     */
    private String userProfile;

    /**
     * 角色权限标识：user-普通用户 / admin-系统管理员
     */
    private String userRole;

    /**
     * 资料最后手动编辑时间（记录用户主动修改资料的时间）
     */
    private Date editTime;

    /**
     * 记录创建时间（系统插入数据时自动写入）
     */
    private Date createTime;

    /**
     * 记录最后更新时间（底层行数据变更时 MySQL 自动触发更新）
     */
    private Date updateTime;

    /**
     * 逻辑删除标志：0-正常未删除 / 1-已删除（配合 MyBatis-Plus 全局逻辑删除配置使用）
     * [补充说明] @TableLogic 为框架的逻辑删除拦截器标识。
     * 机制：标识后，MyBatis-Plus 底层的框架调用将发生行为重载。
     * 1. 物理删除转逻辑更新：执行 BaseMapper.deleteById() 时，底层 SQL 会被重写为 UPDATE user SET isDelete = 1 WHERE id = ?。
     * 2. 查询自动过滤：执行 SELECT 查询操作时，底层会自动在 WHERE 子句中拼接 isDelete = 0 的过滤条件，对业务代码透明。
     */
    @TableLogic
    private Integer isDelete;
}
