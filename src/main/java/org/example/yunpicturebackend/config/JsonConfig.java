package org.example.yunpicturebackend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring MVC JSON 序列化全局配置
 * <p>
 * 【业务场景与设计原理】
 * Java 中的 Long 类型最大值为 2^63 - 1 (9223372036854775807)。
 * 而前端 JavaScript 中所有的数字都使用 64 位双精度浮点数表示，其最大安全整数范围只有 2^53 - 1 (9007199254740991)。
 * * 当后端返回的 Long 类型数据（尤其是 MyBatis Plus 默认生成的雪花算法 ID，通常有 19 位）直接转为 JSON 数字时，
 * 前端 JS 接收后会发生末尾几位数字被截断、四舍五入的“精度丢失”现象，导致根据 ID 查询、更新数据时频频报找不到记录的错误。
 * * 【解决方案】
 * 利用此配置拦截 Jackson 的序列化过程，强制将 Java 中的 Long 型统统转换为 String 类型返回给前端。
 */
@JsonComponent // 这是一个被 Spring Boot 专门用来注册 JSON 序列化/反序列化组件的注解
public class JsonConfig {

    /**
     * 自定义并注册 ObjectMapper (Jackson 的核心处理类)
     *
     * @param builder Spring Boot 自动装配的 Jackson 构造器
     * @return 重新配置过 Long 型处理策略的 ObjectMapper
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 1. 初始化标准 JSON Mapper（屏蔽 XML 解析）
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        
        // 2. 创建一个自定义的 Jackson 序列化模块
        SimpleModule module = new SimpleModule();
        
        // 3. 注册序列化规则：将所有的 Long 转为 String
        // 处理 Long 包装类型 (如：Long id = 123456L;)
        module.addSerializer(Long.class, ToStringSerializer.instance);
        // 处理 long 基本类型 (如：long id = 123456L;)
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        
        // 4. 将自定义模块装载入核心配置中
        objectMapper.registerModule(module);
        
        return objectMapper;
    }
}