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


-- ==========================================
-- 表名称：picture
-- 表描述：图片信息表（存储系统内图片的元数据、分类标签、物理属性及归属信息）
-- ==========================================
CREATE TABLE IF NOT EXISTS picture
(
    -- 基础核心字段
    id           BIGINT AUTO_INCREMENT                  COMMENT '主键 ID（使用 BIGINT 便于后期扩展为雪花算法等分布式 ID）' PRIMARY KEY,
    url          VARCHAR(512)                           NOT NULL COMMENT '图片访问 URL（通常存储 OSS/COS 对象存储的访问链接或 CDN 加速链接）',
    name         VARCHAR(128)                           NOT NULL COMMENT '图片名称（用于前端界面展示和基础搜索）',

    -- 图片元数据字段
    introduction VARCHAR(512)                           NULL COMMENT '图片简介（详细描述图片内容，用于前端展示和详情说明）',
    category     VARCHAR(64)                            NULL COMMENT '图片分类（用于图库频道的分类筛选，如：风景、人物、动漫等）',
    tags         VARCHAR(512)                           NULL COMMENT '图片标签（以 JSON 数组格式存储，方便附加多个标签进行多维度标记）',

    -- 图片物理属性字段
    picSize      BIGINT                                 NULL COMMENT '图片体积（单位：字节/Byte，用于存储容量统计和上传大小限制）',
    picWidth     INT                                    NULL COMMENT '图片宽度（单位：像素/px，用于前端瀑布流等布局预留空间，防止页面抖动）',
    picHeight    INT                                    NULL COMMENT '图片高度（单位：像素/px，用于计算比例和页面排版）',
    picScale     DOUBLE                                 NULL COMMENT '图片宽高比例（宽度除以高度的值，用于前端快速适配和骨架屏展示）',
    picFormat    VARCHAR(32)                            NULL COMMENT '图片格式（如 png, jpg, webp 等，用于格式过滤和图像处理逻辑）',

    -- 归属与权限控制字段
    userId       BIGINT                                 NOT NULL COMMENT '创建用户 ID（关联 user 表主键，标识图片的上传者/拥有者，用于权限控制和个人图库）',

    -- 审计与生命周期字段 (Audit Fields)
    createTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '记录创建时间（系统插入数据时自动写入）',
    editTime     DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '资料最后手动编辑时间（记录用户主动修改图片元数据的时间）',
    updateTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间（底层行数据变更时 MySQL 自动触发更新）',
    isDelete     TINYINT      DEFAULT 0                 NOT NULL COMMENT '逻辑删除标志：0-正常未删除 / 1-已删除（配合 MyBatis-Plus 全局逻辑删除配置使用）',

    -- 索引设计 (Indexes)
    INDEX idx_name (name)                               COMMENT '普通索引：加速基于图片名称的列表检索和基础搜索',
    INDEX idx_introduction (introduction)               COMMENT '普通索引：用于图片简介的基础查询（注：长文本的模糊搜索建议后期引入 Elasticsearch 或改用全文索引）',
    INDEX idx_category (category)                       COMMENT '普通索引：加速基于特定分类的图库筛选查询',
    INDEX idx_tags (tags)                               COMMENT '普通索引：用于标签精确匹配查询（注：若需对 JSON 数组内的元素进行复杂检索，推荐使用 MySQL 5.7+ 虚拟列索引或 ES）',
    INDEX idx_userId (userId)                           COMMENT '普通索引：加速查询某位用户上传的所有图片（个人图库或我的上传功能的核心索引）'
) COMMENT '图片信息表' COLLATE = utf8mb4_unicode_ci;




-- ==========================================
-- 新增字段：图片审核与内容安全管控机制
-- ==========================================
ALTER TABLE picture
    -- 添加新列
    ADD COLUMN reviewStatus  INT          DEFAULT 0 NOT NULL COMMENT '审核状态（枚举值：0-待审核; 1-通过; 2-拒绝。用于内容安全管控，决定图片是否允许在公共图库公开展示，默认上传进入待审核池）',
    ADD COLUMN reviewMessage VARCHAR(512)           NULL     COMMENT '审核反馈信息（当审核状态为拒绝时，记录具体的违规原因或整改建议，便于前端向上传者展示驳回理由）',
    ADD COLUMN reviewerId    BIGINT                 NULL     COMMENT '审核操作人 ID（关联后台管理员/审核人员的主键，用于追溯审核责任人及统计个人的审核工作量）',
    ADD COLUMN reviewTime    DATETIME               NULL     COMMENT '审核操作时间（记录管理员具体执行审核通过或拒绝动作的时间点，用于审核时效性分析）';

-- ==========================================
-- 补充索引：加速审核业务查询
-- ==========================================
CREATE INDEX idx_reviewStatus ON picture (reviewStatus) COMMENT '普通索引：加速后台管理系统筛选待审核或特定状态的图片列表，大幅提升审核工作台的加载性能';


-- ==========================================
-- 新增缩略图字段，用于存储通过 COS 数据万象处理后生成的缩略图地址
ALTER TABLE picture
    ADD COLUMN thumbnailUrl VARCHAR(512) NULL COMMENT '缩略图 URL';
-- ==========================================