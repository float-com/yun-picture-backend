package org.example.yunpicturebackend.model.vo;

import cn.hutool.json.JSONUtil;
import lombok.Data;
import org.example.yunpicturebackend.model.entity.Picture;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片信息视图对象 (VO)
 * <p>
 * 【设计原理】为什么要使用 PictureVO？
 * 1. 屏蔽敏感与底层字段：例如屏蔽实体类中的逻辑删除标志（isDelete）等底层运转字段，保持向前端暴露出纯粹的业务数据。
 * 2. 解决前后端数据格式差异：数据库通常将多值属性（如 tags）以 JSON 字符串形式存储以节省表空间；但在前端，Vue/React 等框架使用数组 (List) 进行渲染和遍历会更加方便。VO 承担了数据结构的翻译工作。
 * 3. 支撑页面聚合展示需求：前端在展示图片时，通常需要一并展示上传者的头像和昵称。通过在 VO 中嵌套 UserVO，可以用一次 HTTP 响应完成多表关联数据的下发，避免前端发起多次请求（N+1 查询问题）。
 */
@Data
public class PictureVO implements Serializable {

    /**
     * 图片全局唯一主键 ID
     */
    private Long id;

    /**
     * 图片外网访问 URL 地址（通常为对象存储的 CDN 链接）
     */
    private String url;

    /**
     * 图片缩略图访问 URL（通常存储上传原图时通过 COS 数据万象等服务同步处理生成的缩略图访问链接）
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 图片详细简介
     */
    private String introduction;

    /**
     * 图片标签列表
     * <p>
     * 【设计原理】类型适配：
     * 底层 Entity 中此处为 String 类型的 JSON 数组（如 "['风景', '高清']"），
     * 在 VO 中转化为 List<String>，便于前端使用 v-for/map 直接渲染标签组件。
     */
    private List<String> tags;

    /**
     * 图片分类（例如：壁纸、摄影、插画等）
     */
    private String category;

    /**
     * 文件物理体积（单位：字节）
     */
    private Long picSize;

    /**
     * 图片宽度（像素）
     */
    private Integer picWidth;

    /**
     * 图片高度（像素）
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式（如：png, jpg, webp）
     */
    private String picFormat;

    /**
     * 创建者/上传者用户 ID
     */
    private Long userId;

    /**
     * 归属空间 ID（
     */
    private Long spaceId;

    /**
     * 图片首次上传时间
     */
    private Date createTime;

    /**
     * 图片信息最后一次人工编辑时间
     */
    private Date editTime;

    /**
     * 图片记录底层最后更新时间
     */
    private Date updateTime;

    /**
     * 创建/上传该图片的用户详细信息
     * <p>
     * 【设计原理】组合模式映射关联：
     * 将关联的脱敏用户信息一并包装返回，满足图片卡片上展示“作者头像+作者名”的典型 UI 需求。
     */
    private UserVO user;

    /**
     * 序列化版本控制标识
     * <p>
     * 保证该对象在跨网络传输或存入分布式缓存时，反序列化的版本兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 将 VO 封装类转换为 Entity 实体对象
     * <p>
     * 场景：常用于前端提交修改请求时，将前端传来的 VO 转换为 Entity 以便入库。
     * * @param pictureVO 前端传入的视图对象
     * @return 转换后的数据库实体对象
     */
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        // 1. 浅拷贝基础同名、同类型字段
        BeanUtils.copyProperties(pictureVO, picture);
        // 2. 特殊字段手动转换：将 List<String> 集合转换为 JSON 格式的字符串，以适配数据库持久化要求
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;
    }

    /**
     * 将 Entity 实体对象转换为 VO 封装类
     * <p>
     * 场景：常用于从数据库查出数据后，将 Entity 转换为 VO 以便响应给前端。
     * * @param picture 数据库查询出的实体对象
     * @return 转换后的视图对象
     */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        // 1. 浅拷贝基础同名、同类型字段
        BeanUtils.copyProperties(picture, pictureVO);
        // 2. 特殊字段手动转换：使用 Hutool 工具类将底层 JSON 字符串反序列化为 List 集合，适配前端数据结构
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
        return pictureVO;
    }
}