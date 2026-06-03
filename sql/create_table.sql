-- 创建图库业务核心数据库
-- 指定字符集为 utf8mb4（支持完整 Unicode 字符，包括 Emoji 表情）
-- 排序规则 utf8mb4_unicode_ci 能够基于标准 Unicode 规则进行排序和比较（不区分大小写）
CREATE DATABASE IF NOT EXISTS `yun_picture` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 切换到当前数据库
USE `yun_picture`;

-- ==========================================
-- 表名称：user
-- 表描述：系统用户信息表（存储系统用户的鉴权信息、基本资料及状态）
-- ==========================================
CREATE TABLE IF NOT EXISTS user
(
    -- 基础核心字段
    id           BIGINT AUTO_INCREMENT COMMENT '主键 ID（使用 BIGINT 便于后期扩展为雪花算法等分布式 ID）' PRIMARY KEY,
    userAccount  VARCHAR(256)                           NOT NULL COMMENT '登录账号（系统内唯一，用于身份认证）',
    userPassword VARCHAR(512)                           NOT NULL COMMENT '登录密码（必须存储经 BCrypt 或单向哈希加盐后的密文，禁止明文）',

    -- 用户资料字段
    userName     VARCHAR(256)                           NULL COMMENT '用户昵称（用于前端界面展示，允许重复）',
    userAvatar   VARCHAR(1024)                          NULL COMMENT '用户头像 URL（建议存储 OSS/COS 对象存储的 CDN 链接）',
    userProfile  VARCHAR(512)                           NULL COMMENT '用户个人简介（长度限制在 512 字符以内）',

    -- 权限与状态控制字段
    userRole     VARCHAR(256) DEFAULT 'user'            NOT NULL COMMENT '角色权限标识：user-普通用户 / admin-系统管理员',

    -- 审计与生命周期字段 (Audit Fields)
    editTime     DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '资料最后手动编辑时间（记录用户主动修改资料的时间）',
    createTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '记录创建时间（系统插入数据时自动写入）',
    updateTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间（底层行数据变更时 MySQL 自动触发更新）',
    isDelete     TINYINT      DEFAULT 0                 NOT NULL COMMENT '逻辑删除标志：0-正常未删除 / 1-已删除（配合 MyBatis-Plus 全局逻辑删除配置使用）',

    -- 索引设计 (Indexes)
    UNIQUE KEY uk_userAccount (userAccount) COMMENT '唯一索引：确保注册账号的全局唯一性，同时加速登录时的鉴权查询',
    INDEX idx_userName (userName) COMMENT '普通索引：加速后台管理系统中按用户昵称进行的检索查询'
) COMMENT '系统用户表' COLLATE = utf8mb4_unicode_ci;