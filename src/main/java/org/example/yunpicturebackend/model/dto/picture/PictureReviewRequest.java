package org.example.yunpicturebackend.model.dto.picture;

import lombok.Data;
import java.io.Serializable;

/**
 * 图片审核请求参数 DTO (Data Transfer Object)
 * <p>
 * 该类用于封装前端后台管理系统在提交“图片审核”操作时传递的参数。
 * </p>
 * <p>
 * 【架构设计与防御性编程说明】
 * 为什么本请求对象中刻意排除了 reviewerId (审核人ID) 和 reviewTime (审核时间)？
 * * 1. 防止越权与数据篡改 (reviewerId)：
 * 后端开发的黄金准则——“永远不要信任前端传来的权限类数据”。如果允许前端传递 reviewerId，
 * 恶意攻击者可以通过抓包篡改该 ID（例如将其修改为超级管理员的 ID），从而伪造虚假的审核记录。
 * 标准做法：后端应在 Service 层处理时，直接从当前运行环境的登录上下文（如 Session 或 Token 解析）
 * 中安全地获取当前操作者的真实 ID 并主动赋值。
 * * 2. 保证审计时间的绝对准确性 (reviewTime)：
 * 若由前端传递时间，系统将强依赖于用户本地设备的系统时间。如果用户的电脑时间不准，
 * 或者恶意调改了本地时钟，传给后端的审计时间就会完全错乱。
 * 标准做法：后端在执行数据库 Update 操作的瞬间，统一由服务器系统生成当前时间（new Date()），
 * 或完全交由数据库（如 CURRENT_TIMESTAMP）生成，以确保审核时序链路的绝对真实和一致性。
 * </p>
 */
@Data
public class PictureReviewRequest implements Serializable {

    /**
     * 被审核的图片主键 ID
     * <p>
     * 必传参数。后端需根据此 ID 查询数据库中对应的图片记录，
     * 确认图片是否存在，以及判断当前图片状态是否允许被执行审核操作。
     * </p>
     */
    private Long id;

    /**
     * 审核目标状态
     * <p>
     * 必传参数。对应 PictureReviewStatusEnum 枚举类的 value。
     * 允许前端传入的合法值通常为：
     * 1 - 审核通过 (PASS)
     * 2 - 审核拒绝 (REJECT)
     * </p>
     */
    private Integer reviewStatus;

    /**
     * 审核反馈信息 / 驳回原因
     * <p>
     * 选传参数。
     * 业务逻辑建议：当 reviewStatus = 1 (通过) 时，该字段可为空；
     * 当 reviewStatus = 2 (拒绝) 时，强烈建议前端提示并要求管理员填写驳回原因，
     * 以便后续向上传图片的普通用户展示清晰的整改建议。
     * </p>
     */
    private String reviewMessage;

    private static final long serialVersionUID = 1L;
}