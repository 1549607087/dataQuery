package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P6 SSE 流式问数的独立线程池 + 上下文传播。
 *
 * 为什么必须自己做传播（P5 已实测踩过）：
 * - ModelGovernanceExecutor 用自建 ThreadPoolExecutor（非 TransmittableThreadLocal 包装）；
 * - TenantContextHolder 是 TransmittableThreadLocal，SecurityContextHolder 是普通 ThreadLocal，
 *   两者都不会自动跨线程；若直接在异步线程里跑 QueryOrchestrator，
 *   S0 取不到 tenantId/userId、后续查 provider 与写审计会 NPE 或串租户。
 * 这里在请求线程捕获 tenantId / ignore / LoginUser，在任务线程显式还原，finally 全量清理。
 *
 * 容量按「并发流数量」而非 QPS 估算：固定大小线程池（core=max，空闲 60s 回收），
 * 队列满抛 RejectedExecutionException，由 Controller 转成 072 错误事件，不占 Tomcat 线程等待。
 */
@Slf4j
@Component
public class ChatSseExecutor {

    private final ThreadPoolExecutor pool;

    public ChatSseExecutor(ChatOrchestratorProperties properties) {
        // 固定大小线程池：core 必须等于 max。
        // ThreadPoolExecutor 只在队列满时才创建超过 core 的线程，若 core < max，
        // 「并发流上限」实际等于 core——流式连接每条独占线程 10~30s，
        // 这条链路曾因此把并发压在 2，队列里排满 64 个请求也扩不出第 3 个线程。
        int workers = positive(properties.getSseMaxThreads(), 32);
        int queue = positive(properties.getSseQueueCapacity(), 64);
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger seq = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "ai-chat-sse-" + seq.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        this.pool = new ThreadPoolExecutor(workers, workers, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue), factory, new ThreadPoolExecutor.AbortPolicy());
        // 空闲回收：突发过后不留 32 个闲置线程常驻（allowCoreThreadTimeOut 需要 keepAlive > 0）
        this.pool.allowCoreThreadTimeOut(true);
    }

    /**
     * 提交任务；队列满时抛 RejectedExecutionException，由调用方转成明确的错误事件。
     */
    public void submit(Long tenantId, boolean tenantIgnore, LoginUser loginUser, Runnable task)
            throws RejectedExecutionException {
        pool.execute(() -> {
            TenantContextHolder.setTenantId(tenantId);
            TenantContextHolder.setIgnore(tenantIgnore);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
            try {
                task.run();
            } finally {
                SecurityContextHolder.clearContext();
                TenantContextHolder.clear();
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        pool.shutdown();
        log.info("[chat][sse] 线程池已关闭");
    }

    /** 供单测验证「并发上限真实生效」与空闲回收，不对外暴露。 */
    int poolSize() {
        return pool.getPoolSize();
    }

    int maxPoolSize() {
        return pool.getMaximumPoolSize();
    }

    int queueSize() {
        return pool.getQueue().size();
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}
