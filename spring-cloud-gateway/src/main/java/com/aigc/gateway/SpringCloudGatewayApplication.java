package com.aigc.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Spring Cloud Gateway 启动类
 * 职责：统一路由、负载均衡、鉴权、限流
 */
@Slf4j
@EnableDiscoveryClient
@SpringBootApplication
public class SpringCloudGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringCloudGatewayApplication.class, args);

        // 演示 JDK8 Stream + Lambda + Optional：打印已注册路由服务
        List<String> routes = Arrays.asList(
                "knowledge-parse-service",
                "ai-orchestration-service",
                "content-gen-service"
        );

        String routeList = routes.stream()
                .map(route -> "/api/" + route.replace("-service", ""))
                .collect(Collectors.joining(" | "));

        Optional.of(routeList)
                .filter(s -> !s.isEmpty())
                .ifPresent(s -> log.info("Gateway 路由已加载: {}", s));

        log.info("Spring Cloud Gateway 启动成功");
    }

    /**
     * 基于请求 IP 的限流 KeyResolver
     */
    @Bean
    public KeyResolver ipKeyResolver() {
        return (ServerWebExchange exchange) -> {
            String ip = Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                    .map(addr -> addr.getAddress().getHostAddress())
                    .orElse("unknown");
            return Mono.just(ip);
        };
    }
}
