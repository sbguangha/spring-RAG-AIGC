package com.aigc.orchestration.config;

import com.aigc.orchestration.util.MdcTaskDecorator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * RAG 引擎专用的自定义线程池配置。
 *
 * 为什么需要自定义线程池？
 *   1. RAG 流程是典型的 IO 密集型场景：VectorStore 检索、Redis 查询、大模型 API 调用都是网络 IO；
 *   2. Spring 默认的 SimpleAsyncTaskExecutor 或 ForkJoinPool.commonPool()
 *      无法针对 IO 场景设置合理的核心线程数、队列长度和拒绝策略；
 *   3. 自定义线程池可以隔离 RAG 任务与系统其他异步任务，避免相互挤占资源；
 *   4. 配置 CallerRunsPolicy 拒绝策略，在流量激增时由调用线程自己执行，
 *      既不会直接丢弃任务，又能通过阻塞起到背压（Back Pressure）效果。
 *
 * 为什么 IO 密集型线程池参数这样设计？
 *   - 核心线程数设置为 CPU 核数的 2~4 倍（默认 8），因为 IO 等待期间线程会释放 CPU；
 *   - 最大线程数设置为 32，作为突发流量的上限；
 *   - 队列容量设置为 200，缓冲短时间的检索尖峰；
 *   - 线程空闲 60 秒回收，避免长期占用内存；
 *   - 线程名前缀统一为 "rag-async-"，便于日志排查和监控。
 *
 * MDC 传递：
 *   通过 setTaskDecorator(new MdcTaskDecorator()) 在线程池提交任务时自动拷贝 MDC，
 *   保证 CompletableFuture 内部打印的日志仍带有 traceId，实现全链路追踪。
 */
@Configuration
public class RagThreadPoolConfig {

    @Value("${rag.executor.core-pool-size:8}")
    private int corePoolSize;

    @Value("${rag.executor.max-pool-size:32}")
    private int maxPoolSize;

    @Value("${rag.executor.queue-capacity:200}")
    private int queueCapacity;

    @Value("${rag.executor.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /**
     * RAG 引擎专用线程池 Bean。
     * 使用 @Primary 标注，使其在需要 Executor 注入但未指定名称时优先被选中。
     */
    @Bean("ragExecutor")
    @Primary
    public Executor ragExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("rag-async-");

        // 拒绝策略：当线程池和队列都满时，由提交任务的线程（调用者线程）自己执行。
        // 优点：不会丢任务；缺点：调用线程会被阻塞，天然形成流量限制，保护下游服务。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 优雅关闭：等待队列中的任务执行完成后再销毁线程池，避免任务丢失。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        // MDC 上下文装饰器：保证子线程可以继承父线程的链路追踪字段。
        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.initialize();
        return executor;
    }
}
