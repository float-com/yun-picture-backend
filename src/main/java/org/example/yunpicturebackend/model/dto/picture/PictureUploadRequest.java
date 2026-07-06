package org.example.yunpicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端在上传图片时携带的表单参数。
 * 设计说明：该类主要为了支持“重复上传（替换图片）”的功能。
 * 即：允许用户对已存在的图片记录重新上传文件进行替换，在此过程中基础信息（如名称、标签等）保持不变，仅更新底层的物理文件和文件元数据（大小、宽高等）。
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 ID
     * <p>
     * 作用：用于区分当前上传操作是“新增”还是“修改”。
     * - 若为空 (null)：代表这是一次全新的图片上传。
     * - 若不为空：代表这是一次“重复上传”操作，系统将根据此 ID 查出原图片记录，并用新上传的物理文件替换旧文件。
     */
    private Long id;

    /**
     * 归属空间 ID
     * <p>
     * 作用：标识当前上传图片所需存放的目标图库空间，用于实现多层级的数据隔离与底层配额管控。
     * - 若为空 (null)：表示这是一次针对系统“公共图库”的上传操作。
     * - 若不为空：关联 space 表的主键，表示图片将专属上传至指定的“私有空间”。系统在上传入库前，将依据此 ID 联动触发该空间的容量 (maxSize) 与数量 (maxCount) 限额校验机制。
     */
    private Long spaceId;

    /**
     * 文件地址（网络图片 URL）
     * <p>
     * 作用：用于支持基于公网 URL 的图片抓取与转存。
     * - 配合底层业务逻辑的扩展，当前端未传递物理文件（MultipartFile）而是传递此网络链接时，
     * 系统会将其作为输入源（inputSource），读取网络文件流并安全上传至云端 COS 存储。
     */
    private String fileUrl;

    /**
     * 图片名称
     * <p>
     * 业务场景：支持用户在上传单张图片或抓取单张网络图片时，主动为其指定一个有意义的名称。
     * 联动逻辑：若前端传递了此值，后端的 uploadPicture 方法将优先采用该名称覆盖从物理文件或云端元数据中解析出的默认缺省名。
     * </p>
     */
    private String picName;

    /**
     * 序列化版本号（用于保证跨服务或 Redis 缓存反序列化时的对象结构兼容性）
     */
    private static final long serialVersionUID = 1L;
}