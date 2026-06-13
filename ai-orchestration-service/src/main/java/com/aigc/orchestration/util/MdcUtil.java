package com.aigc.orchestration.util;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * MDC（Mapped Diagnostic Context）上下文传递工具类。
 *
 * 设计背景：
 *   CompletableFuture、ThreadPoolTaskExecutor 等异步执行框架在提交任务时，
 *   子线程不会自动继承父线程的 MDC（例如 traceId、userId 等链路追踪字段）。
 *   这会导致日志分散、链路断裂，排查线上问题时极其困难。
 *
 * 解决方案：
 *   在提交异步任务前，先通过 MDC.getCopyOfContextMap() 拷贝父线程的 MDC；
 *   在子线程真正执行前，通过 MDC.setContextMap() 还原；
 *   任务结束后在 finally 块中执行 MDC.clear()，避免线程复用时上下文污染。
 *
 * JDK8 特性应用：
 *   1. 函数式接口 Supplier<T>：将业务逻辑包装为可被延迟执行的供给型函数；
 *   2. Lambda 表达式：简洁地生成带 MDC 拷贝的新 Supplier/Runnable；
 *   3. Optional 与三元运算符：对空 MDC 场景做防御性处理。
 */
public final class MdcUtil {

    private MdcUtil() {
        // 工具类禁止实例化
    }

    /**
     * 包装 Supplier，使其在异步线程中保留父线程 MDC。
     *
     * @param supplier 原始业务逻辑（例如 VectorStore 检索、Redis 查询）
     * @param <T>      返回值类型
     * @return 包装后的 Supplier
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier) {
        // 在父线程中立即快照 MDC；如果父线程没有 MDC，则返回 null
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                // 子线程执行前还原 MDC；若父线程无 MDC，则保持当前线程干净状态
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                return supplier.get();
            } finally {
                // 必须清理，防止线程池复用导致上下文串扰
                MDC.clear();
            }
        };
    }

    /**
     * 包装 Runnable，使其在异步线程中保留父线程 MDC。
     *
     * @param runnable 原始无返回值任务
     * @return 包装后的 Runnable
     */
    public static Runnable wrap(Runnable runnable) {
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
