package org.example.yunpicturebackend.common;

import lombok.Data;

/**
 * 基础分页请求参数类
 * <p>
 * 【设计原理】通用化与 DRY 原则：
 * 将分页和动态排序的核心字段抽取为一个基础类。后续所有的具体业务查询请求（如 UserQueryRequest、PictureQueryRequest）
 * 只需继承该类，即可自动拥有分页和排序能力，避免代码冗余。
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     * <p>
     * 【设计原理】安全兜底：
     * 默认值为 1。确保当前端未传递此参数（或传递丢失）时，接口依然能正常返回第一页的数据，
     * 避免因空指针引发异常或查出非预期数据。
     */
    private int current = 1;

    /**
     * 页面大小（每页显示的条数）
     * <p>
     * 【设计原理】系统性能保护：
     * 默认值为 10。强制限制单次数据库查询返回的数据量，防止由于前端传参漏洞或恶意请求，
     * 导致一次性查出几十万条数据，从而引发数据库 CPU 飙升或服务端 OOM（内存溢出）。
     */
    private int pageSize = 10;

    /**
     * 排序字段（对应数据库中的列名或实体的属性名）
     * <p>
     * 【设计原理】动态控制：
     * 将排序的决定权交给前端（例如点击表格表头排序）。
     * （安全提示：在 Service 层拼接 SQL 时，务必对该字段进行合法性校验或使用 MyBatis Plus 的安全包装器，防止 SQL 注入漏洞）。
     */
    private String sortField;

    /**
     * 排序顺序（ascend-升序，descend-降序）
     * <p>
     * 【设计原理】符合用户直觉的默认值：
     * 大多数主流业务系统（如图片流、帖子列表、日志列表）的首要展现逻辑都是“最新鲜的数据排在最前面”，
     * 因此将默认值设为降序（descend）是最符合常规用户体验的。
     */
    private String sortOrder = "descend";
}