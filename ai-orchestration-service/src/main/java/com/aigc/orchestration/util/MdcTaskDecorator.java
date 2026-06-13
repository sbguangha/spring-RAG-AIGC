package com.aigc.orchestration.util;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Spring ThreadPoolTaskExecutor 专用的 MDC 上下文装饰器。
 *
 * 与 MdcUtil 的区别：
 *   MdcUtil 用于包装单个 CompletableFuture 任务；
 *   MdcTaskDecorator 用于在线程池级别统一装饰所有提交的任务，
 *   适合需要全局生效的自定义 Executor（如 @Async("ragExecutor")）。
 *
 * 设计要点：
 *   1. 实现 Spring 的 TaskDecorator 接口，复用线程池的任务装饰机制；
 *   2. 在任务执行前 setContextMap，执行后 clear，避免污染；
 *   3. 对 contextMap 为 null 的场景做防御，防止 NPE。
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 提交任务时由调用线程执行，此时可获取父线程 MDC
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
