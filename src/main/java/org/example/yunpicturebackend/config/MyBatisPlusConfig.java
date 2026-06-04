package org.example.yunpicturebackend.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 全局配置类
 * <p>
 * 【出处说明】
 * 此段分页拦截器的核心配置代码来源于 MyBatis Plus 官方文档。
 * 根据官方规范，在 Spring Boot 项目中使用 `selectPage` 等分页功能时，
 * 必须显式地向 Spring 容器中注册带有 `PaginationInnerInterceptor` 的拦截器，否则分页将不生效（退化为全量查询）。
 */
@Configuration // 标识这是一个 Spring 配置类，Spring 启动时会自动解析其内部的 @Bean 定义
@MapperScan("org.example.yunpicturebackend.mapper") // 扫描指定包下的所有 Mapper 接口，为其生成代理实现类并注册到 Spring 容器中
public class MyBatisPlusConfig {

    /**
     * 配置并注册 MyBatis Plus 的核心拦截器
     *
     * @return {@link MybatisPlusInterceptor} 包含多个插件的拦截器链对象
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 1. 实例化一个核心拦截器总闸
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 2. 添加分页插件 (InnerInterceptor)
        // 此处明确指定底层的数据库方言为 MySQL (DbType.MYSQL)。
        // 这样 MyBatis Plus 在执行 SQL 拦截时，才能准确地使用 MySQL 专用的 LIMIT 和 OFFSET 语法来重写物理分页 SQL。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        return interceptor;
    }
}