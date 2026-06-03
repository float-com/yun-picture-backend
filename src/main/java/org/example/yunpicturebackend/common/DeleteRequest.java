package org.example.yunpicturebackend.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 通用删除请求参数类
 * <p>
 * 【设计原理】为什么仅仅传递一个 id 也要专门封装成一个类？
 * 1. 极强的扩展性：如果未来删除逻辑变得复杂，需要前端补充参数（例如增加操作人的 userId、删除原因备注、或是批量删除的 ids 列表），
 * 只需修改此类即可，Controller 层的接口签名（形参）完全不需要变动，符合面向对象的“开闭原则”。
 * 2. 统一传参规范：通常配合 POST 请求和 @RequestBody 使用，相比在 URL 中直接暴露形如 `?id=123` 的方式，
 * 封装在请求体中更加安全、统一，且不受 URL 长度的限制。
 */
@Data
public class DeleteRequest implements Serializable {

    /**
     * 待删除记录的主键 ID
     * <p>
     * 【设计原理】为什么使用包装类型 Long 而不是基本数据类型 long？
     * 防御性编程：如果前端在调用接口时由于 Bug 漏传了该参数，包装类 Long 的默认值是 null，
     * 此时我们在后端可以轻易通过参数校验（如 @NotNull）将其拦截并提示“参数错误”。
     * 如果使用基本类型 long，其默认值为 0，极有可能被底层误认为前端真的想要删除 id 为 0 的那条业务数据，从而引发严重的“误删”事故。
     */
    private Long id;

    /**
     * 序列化版本控制标识
     * <p>
     * 【设计原理】
     * 显式声明 serialVersionUID，主要是为了保证该对象在跨网络传输（如分布式环境下的 RPC 调用）
     * 或存入外部缓存（如 Redis）时，即使后续类的结构发生微调，反序列化时依然能保持版本的兼容性，避免引发 InvalidClassException 异常。
     */
    private static final long serialVersionUID = 1L;
}