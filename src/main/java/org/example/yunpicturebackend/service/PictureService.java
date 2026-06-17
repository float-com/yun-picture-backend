package org.example.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureReviewRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import org.example.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import org.example.yunpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import org.example.yunpicturebackend.model.entity.User;
import org.example.yunpicturebackend.model.vo.PictureVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;


/**
* @author 24042
* @description 针对表【picture(图片信息表)】的数据库操作Service
* @createDate 2026-06-08 19:33:54
*/
public interface PictureService extends IService<Picture> {

    /**
     * 上传图片（统一处理新增和更新逻辑）
     * <p>
     * 业务流程：
     * 1. 接收前端传递的图片输入源（支持物理文件流或图片 URL）和请求参数。
     * 2. 根据 pictureUploadRequest 中的 id 判断当前操作是“全新上传”还是“更新替换旧图”。
     * 3. 调度底层的上传模块将图片安全上传至 COS，并解析获取图片的宽高、体积等元数据。
     * 4. 组装 Picture 实体类数据，并绑定当前登录用户的归属信息。
     * 5. 执行数据库的插入或更新操作，最终返回脱敏后的视图对象 (VO) 供前端渲染。
     *
     * @param inputSource          图片输入源（支持前端传入的 MultipartFile 物理文件对象，或 String 类型的图片 URL 地址）
     * @param pictureUploadRequest 图片上传扩展请求参数（核心用于携带图片 id：若为 null 代表新增上传；若有值代表对已有记录进行图片替换）
     * @param loginUser            当前已认证的登录用户对象（用于绑定图片归属权 userId，记录操作人，保证数据安全和溯源）
     * @return PictureVO           图片上传并成功入库后，返回给前端的图片视图对象（已剔除底层敏感字段，并包含完整的图片展示信息）
     */
    PictureVO uploadPicture(Object inputSource,
                            PictureUploadRequest pictureUploadRequest,
                            User loginUser);


    /**
     * 构建图片查询的 MyBatis-Plus 包装类 (QueryWrapper)
     * <p>
     * 业务场景：在执行图片的分页查询、列表检索或导出等操作前，将前端传入的查询条件转换为数据库可执行的 SQL 条件。
     * 转换规则：
     * 1. 聚合搜索：若存在 searchText，需将其拆解并应用到多字段的模糊查询中（如 name LIKE %searchText% OR introduction LIKE %searchText%）。
     * 2. 模糊匹配：对 name, introduction 等常规文本字段进行 LIKE 查询。
     * 3. 精确匹配：对 id, category, picFormat, userId 等业务标识进行 EQ (等于) 查询。
     * 4. 集合匹配：若存在 tags 列表，需结合数据库存储格式解析并拼装相应的查询逻辑（如 JSON_CONTAINS 或多次 LIKE）。
     * * @param pictureQueryRequest 前端传入的查询请求封装对象（允许包含空字段，方法内部会自动进行判空拦截）
     * @return QueryWrapper<Picture> 组装完毕的 MyBatis-Plus 查询包装类，可直接交由 Mapper 或 Service 执行
     */
    QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取单张图片的脱敏视图对象 (VO)
     * <p>
     * 业务场景：在查看图片详情时，将数据库底层的 Picture 实体转换为前端展示所需的 VO 对象。
     * 包含逻辑：除图片基础信息外，还会自动关联查询并填充上传该图片的用户信息 (UserVO)。
     *
     * @param picture 原始图片实体对象
     * @param request HTTP 请求对象（可用于获取当前登录用户等上下文信息）
     * @return PictureVO 包含脱敏图片信息及关联用户信息的视图对象
     */
    PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取分页图片的脱敏视图对象 (VO)
     * <p>
     * 业务场景：在图片列表、搜索广场等分页场景下，将底层的分页实体对象转换为前端展示的分页 VO 对象。
     * 性能说明：内部采用“批量查询 + 内存拼接”的方式获取关联用户信息，避免了传统 for 循环查库带来的 N+1 性能问题。
     *
     * @param picturePage 原始图片分页查询结果
     * @param request     HTTP 请求对象
     * @return Page<PictureVO> 包含脱敏图片信息及关联用户信息的分页视图对象
     */
    Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 分页获取图片视图列表（多级缓存版）
     * <p>
     * 业务场景：面向 C 端首页、空间图片列表等高频读场景，在保持原有分页、搜索和 VO 装配能力的前提下，
     * 优先使用 JVM 本地缓存 (L1) 承接热点请求，再使用 Redis (L2) 承接跨节点共享数据，最后才回源数据库。
     * </p>
     *
     * @param pictureQueryRequest 图片分页查询请求参数
     * @param request             HTTP 请求对象（用于 VO 装配时继续复用原有登录上下文）
     * @return Page<PictureVO> 包含脱敏图片信息的分页视图结果
     */
    Page<PictureVO> listPictureVOByPageWithCache(PictureQueryRequest pictureQueryRequest, HttpServletRequest request);

    /**
     * 校验图片参数的合法性
     * <p>
     * 业务场景：在图片信息入库或更新前调用，确保实体对象的核心字段（如主键、文本长度等）满足数据库约束与业务规范。
     * 注意事项：
     * 1. 异常阻断：该方法无返回值，当校验到非法参数时，会直接抛出业务异常（RuntimeException）中断当前请求流程。
     * 2. 适用场景：当前规则强制要求图片 ID 不能为空，主要适用于“修改/更新”场景的提前拦截。
     *
     * @param picture 待校验的图片实体对象（若传入 null 方法内部亦会抛出异常）
     */
    void validPicture(Picture picture);


    /**
     * 核心业务：图片审核
     * <p>
     * 由后台管理员执行，对上传的图片进行内容安全审核（通过/拒绝）。
     * </p>
     *
     * @param pictureReviewRequest 审核请求参数（包含被审核图片 ID、目标审核状态、审核驳回信息）
     * @param loginUser            当前登录的用户上下文（即执行审核操作的管理员，用于安全提取 reviewerId）
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 自动填充图片审核相关参数
     * <p>
     * 根据当前操作用户的角色（管理员/普通用户），自动为图片实体装配对应的审核状态及附加审计信息。
     * </p>
     *
     * @param picture   需要填充审核参数的图片实体对象
     * @param loginUser 当前执行操作的登录用户
     */
    void fillReviewParams(Picture picture, User loginUser);

    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest 批量抓取图片的请求参数（包含搜索词和抓取数量）
     * @param loginUser                   当前登录用户信息
     * @return 成功抓取并创建的图片总数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

}
