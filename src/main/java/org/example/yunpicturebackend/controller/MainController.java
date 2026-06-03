package org.example.yunpicturebackend.controller;

import org.example.yunpicturebackend.common.BaseResponse;
import org.example.yunpicturebackend.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统基础控制器
 * <p>
 * 用于提供系统级别的通用基础接口（如健康检查、系统全局配置获取等），不包含具体的复杂业务逻辑。
 */
@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 服务健康检查接口
     * <p>
     * 【设计原理】为什么几乎所有的企业级项目都会保留一个简单的 /health 接口？
     * 1. 云原生与容器化编排支持：在 Kubernetes (K8s) 等平台中，需要配置存活探针（Liveness Probe）和就绪探针（Readiness Probe）。
     * K8s 会定时调用此接口，若无响应则会自动重启该 Pod 容器。
     * 2. 负载均衡与网关心跳检测：Nginx、阿里云 SLB 或 Spring Cloud Gateway 等网关会通过该接口进行“心跳检测”。
     * 如果返回异常，网关会自动将该实例从流量分发列表中剔除，防止用户的请求打到已经故障的机器上。
     * 3. 外部监控告警：运维人员通常会配置 Prometheus、UptimeRobot 等监控平台，定时 Ping 这个接口来实现宕机秒级告警。
     *
     * @return 包含 "ok" 的标准响应对象，状态码为 200 即代表应用当前存活且网络通畅
     */
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }
}