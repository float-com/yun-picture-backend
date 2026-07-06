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



-- ==========================================
-- 表名称：space
-- 表描述：图库空间表（用于实现私有或共享的图片独立存储空间，支持基于级别的容量与数量限额管控）
-- ==========================================
CREATE TABLE IF NOT EXISTS space
(
    -- 基础核心字段
    id           BIGINT AUTO_INCREMENT                  COMMENT '主键 ID（使用 BIGINT 便于后期扩展为雪花算法等分布式 ID）' PRIMARY KEY,
    spaceName    VARCHAR(128)                           NULL COMMENT '空间名称（用于前端空间列表展示和基础搜索）',
    spaceType    INT          DEFAULT 0                 NOT NULL COMMENT '空间类型（枚举值：0-私有空间; 1-团队空间。用于区分个人私有图库与团队协作空间）',
    spaceLevel   INT          DEFAULT 0                 NULL COMMENT '空间级别（枚举值：0-普通版; 1-专业版; 2-旗舰版。使用整型代替字符串可节约存储空间并提升查询效率）',

    -- 空间配额控制字段 (Quota Fields)
    maxSize      BIGINT       DEFAULT 0                 NULL COMMENT '空间容量配额（单位：字节/Byte，限制该空间下图片的总大小。独立字段便于管理员单独调整特定空间的限额，解耦代码硬编码）',
    maxCount     BIGINT       DEFAULT 0                 NULL COMMENT '空间数量配额（限制该空间下允许上传的图片最大总数，独立字段控制利于业务灵活扩展与查询）',

    -- 空间状态统计字段 (Statistics Fields)
    totalSize    BIGINT       DEFAULT 0                 NULL COMMENT '当前已用容量（单位：字节/Byte，实时记录当前空间下所有图片的总大小，用于快速校验上传是否超限）',
    totalCount   BIGINT       DEFAULT 0                 NULL COMMENT '当前已有数量（实时记录当前空间下的图片总数，配合 maxCount 进行阈值拦截校验）',

    -- 归属与权限控制字段
    userId       BIGINT                                 NOT NULL COMMENT '所属用户 ID（关联 user 表主键，标识该空间的拥有者，实现私有空间的权限隔离）',

    -- 审计与生命周期字段 (Audit Fields)
    createTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '记录创建时间（系统插入数据时自动写入）',
    editTime     DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '资料最后手动编辑时间（记录用户主动修改空间信息的时间）',
    updateTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '记录最后更新时间（底层行数据变更时 MySQL 自动触发更新）',
    isDelete     TINYINT      DEFAULT 0                 NOT NULL COMMENT '逻辑删除标志：0-正常未删除 / 1-已删除（配合 MyBatis-Plus 全局逻辑删除配置使用）',

    -- 索引设计 (Indexes)
    INDEX idx_userId (userId)                           COMMENT '普通索引：加速查询某位用户拥有的所有空间列表（个人空间管理核心索引）',
    INDEX idx_spaceName (spaceName)                     COMMENT '普通索引：加速后台管理系统或前端界面按空间名称进行的检索查询',
    INDEX idx_spaceType (spaceType)                     COMMENT '普通索引：加速按私有空间或团队空间进行筛选',
    INDEX idx_spaceLevel (spaceLevel)                   COMMENT '普通索引：加速后台筛选特定级别的空间，便于进行等级相关的运营统计与批量操作'
) COMMENT '图库空间表' COLLATE = utf8mb4_unicode_ci;


-- ==========================================
-- 业务变更：关联图片与存储空间 (实现多租户/私有库隔离)
-- ==========================================
ALTER TABLE picture
    -- 添加空间关联字段
    ADD COLUMN spaceId BIGINT NULL COMMENT '归属空间 ID（关联 space 表主键，实现图片与特定私有空间的绑定；若为空则表示该图片上传到了公共图库）';

-- ==========================================
-- 补充索引：加速空间内图片的加载与查询
-- ==========================================
CREATE INDEX idx_spaceId ON picture (spaceId) COMMENT '普通索引：加速查询特定私有空间下的所有图片列表，大幅提升空间内瀑布流等业务的加载性能';


-- ==========================================
-- 业务变更：补充空间类型（已存在旧表时按需手动执行）
-- ==========================================
 ALTER TABLE space
     ADD COLUMN spaceType INT DEFAULT 0 NOT NULL COMMENT '空间类型：0-私有空间 1-团队空间';

CREATE INDEX idx_spaceType ON space (spaceType);