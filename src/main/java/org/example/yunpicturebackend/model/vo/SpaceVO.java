package org.example.yunpicturebackend.model.vo;

import lombok.Data;
import org.example.yunpicturebackend.model.entity.Space;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 图库空间视图对象 (VO)
 * <p>
 * 【设计原理】为什么要使用 SpaceVO？
 * 1. 屏蔽底层运转字段：例如屏蔽实体类中的逻辑删除标志（isDelete）等底层状态字段，向前端输出最纯粹的业务展示数据。
 * 2. 支撑页面聚合展示需求：前端在展示空间列表或空间详情时，通常需要一并展示该空间拥有者的头像和昵称。通过在 VO 中嵌套 UserVO，可以用一次 HTTP 响应完成关联数据的下发，避免前端发起多次请求（N+1 查询问题）。
 * 3. 业务状态直观透出：将空间的“配额上限”（maxSize/maxCount）与“当前已用”（totalSize/totalCount）组合返回，极大地便利了前端直接渲染“容量使用进度条”或进行超限预警交互。
 */
@Data
public class SpaceVO implements Serializable {
    
    /**
     * 空间全局唯一主键 ID
     */
    private Long id;

    /**
     * 空间名称
     * <p>
     * 场景：用于前端空间卡片、列表页或详情页的标题展示。
     */
    private String spaceName;

    /**
     * 空间级别（枚举值：0-普通版 1-专业版 2-旗舰版）
     * <p>
     * 场景：前端可根据此级别渲染不同的会员尊贵标识（如 VIP 图标）或区分特权 UI 样式。
     */
    private Integer spaceLevel;

    /**
     * 空间容量配额（单位：字节/Byte）
     * <p>
     * 场景：当前空间允许存储的图片总大小上限，前端通常会配合文件大小过滤器将其转换为 MB/GB 进行可视化展示。
     */
    private Long maxSize;

    /**
     * 空间图片数量配额
     * <p>
     * 场景：当前空间允许上传的图片最大总数上限。
     */
    private Long maxCount;

    /**
     * 当前已用容量（单位：字节/Byte）
     * <p>
     * 场景：结合 maxSize，用于前端计算使用百分比并渲染容量消耗进度条。
     */
    private Long totalSize;

    /**
     * 当前已有数量
     * <p>
     * 场景：结合 maxCount，用于前端展示当前空间内已拥有的图片总数及剩余可上传余量。
     */
    private Long totalCount;

    /**
     * 空间创建者/拥有者用户 ID
     */
    private Long userId;

    /**
     * 空间记录首次创建时间
     */
    private Date createTime;

    /**
     * 空间信息最后一次人工编辑时间
     */
    private Date editTime;

    /**
     * 空间记录底层最后更新时间
     */
    private Date updateTime;

    /**
     * 创建/拥有该空间的用户详细信息
     * <p>
     * 【设计原理】组合模式映射关联：
     * 将关联的脱敏用户信息一并包装返回，满足空间卡片上展示“拥有者头像 + 昵称”的典型 UI 需求。
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
     * 场景：常用于前端提交空间修改请求时，将前端传来的 VO 转换为 Entity 以便入库。
     * * @param spaceVO 前端传入的视图对象
     * @return 转换后的数据库实体对象
     */
    public static Space voToObj(SpaceVO spaceVO) {
        if (spaceVO == null) {
            return null;
        }
        Space space = new Space();
        // 浅拷贝同名、同类型的属性
        BeanUtils.copyProperties(spaceVO, space);
        return space;
    }

    /**
     * 将 Entity 实体对象转换为 VO 封装类
     * <p>
     * 场景：常用于从数据库查出空间数据后，将 Entity 转换为 VO 以便响应给前端。
     * * @param space 数据库查询出的实体对象
     * @return 转换后的视图对象
     */
    public static SpaceVO objToVo(Space space) {
        if (space == null) {
            return null;
        }
        SpaceVO spaceVO = new SpaceVO();
        // 浅拷贝同名、同类型的属性
        BeanUtils.copyProperties(space, spaceVO);
        return spaceVO;
    }
}