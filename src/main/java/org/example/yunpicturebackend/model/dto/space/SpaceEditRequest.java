package org.example.yunpicturebackend.model.dto.space;

import lombok.Data;
import java.io.Serializable;

/**
 * 图库空间编辑请求参数封装类 (DTO)
 * <p>
 * 业务场景：用于接收前端 C 端普通用户在“修改本人空间信息”时提交的表单参数。
 * 设计说明：该类主要支持对空间基础元数据的局部修改。
 * 权限控制极为严格，刻意屏蔽了 spaceLevel、maxSize 等配额字段，
 * 确保普通用户只能修改表层展示信息，无法通过接口篡改核心的存储容量与数量限制。
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 ID
     * <p>
     * 作用：用于定位被编辑的目标空间记录。
     * - 业务限制：【必传项】。系统将依赖该 ID 检索空间记录，并强制校验当前登录用户是否为该空间的创建者（防越权漏洞）。
     */
    private Long id;

    /**
     * 空间名称
     * <p>
     * 作用：用户重新定义的空间展示标题。
     * - 业务限制：若传递则更新。建议接入敏感词过滤，并限制最大字符长度（如 VARCHAR(128)）。
     */
    private String spaceName;

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;
}