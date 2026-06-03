package org.example.yunpicturebackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局跨域资源共享（CORS）配置类
 * <p>
 * 【设计原理】解决前后端分离架构下的跨域限制：
 * 现代浏览器出于安全考虑，默认存在“同源策略”，会拦截不同域名、端口或协议之间的 Ajax/Fetch 请求。
 * 通过实现 WebMvcConfigurer 接口并重写该方法，可以在服务端统一向响应中添加 CORS 相关的 HTTP 头，
 * 从而优雅、全局地放行前端的合法跨域请求。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 配置跨域映射规则
     *
     * @param registry 跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 1. 匹配所有后端接口请求路径（如 /api/user, /api/picture 等）
        registry.addMapping("/**")
                
                // 2. 【设计原理】允许前端发送 Cookie 和身份凭证：
                // 前后端分离时，如果使用基于 Session 或 Cookie 的认证机制，必须开启此项。
                // 否则浏览器跨域请求时不会携带当前域的 Cookie，导致后端始终判定为“未登录”。
                .allowCredentials(true)
                
                // 3. 【设计原理】放行指定的来源域名：
                // 为什么用 allowedOriginPatterns 而不是 allowedOrigins？
                // 根据 W3C 规范与高版本 Spring 的严格安全校验：当 allowCredentials(true) 时，
                // 直接使用 allowedOrigins("*") 会报错。使用 patterns 是一种兼容且安全的写法，
                // 也方便后续扩展为类似 "*.yupi.com" 的正则匹配。
                .allowedOriginPatterns("*")
                
                // 4. 【设计原理】允许的 HTTP 方法：
                // 除了常规的 GET/POST/PUT/DELETE，必须包含 "OPTIONS"。
                // 浏览器在发送复杂的跨域请求（如带自定义 Header 或使用 PUT/DELETE）前，
                // 会先发送一个 OPTIONS 预检请求（Preflight），确保后端支持此次通信。
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                
                // 5. 允许前端在请求头中携带任何自定义字段（例如 Authorization、自定义 Token 等）
                .allowedHeaders("*")
                
                // 6. 【设计原理】暴露响应头：
                // 默认情况下，前端浏览器的 JS 只能读取到少数几个基础响应头。
                // 设置为 "*" 或特定字段，可以允许前端提取我们在后端放入的特殊 Header 信息。
                .exposedHeaders("*");
    }
}