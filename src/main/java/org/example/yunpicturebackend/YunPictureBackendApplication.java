package org.example.yunpicturebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
// 开启 Spring 异步执行支持（配合 @Async 注解使用）
@EnableAsync
// 指定 Mapper 接口扫描路径，自动创建 Bean
@MapperScan("org.example.yunpicturebackend.mapper")
// 启用 AspectJ 自动代理 开启 AOP
@EnableAspectJAutoProxy(exposeProxy = true)
public class YunPictureBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(YunPictureBackendApplication.class, args);
    }

}
