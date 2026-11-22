package cn.iocoder.boot.module.data_query.framework.chat;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE 心跳调度器：全局一个线程，按连接登记定时任务。
 *
 * <p>两个作用：
 * <ol>
 *   <li><b>防中间设备断连</b>：nginx 的 {@code proxy_read_timeout} 默认 60s，长思考或长回答期间
 *       可能长时间没有业务事件，连接会被判空闲而断开；</li>
 *   <li><b>唯一的写探针</b>：Servlet 侧没有 onClose 回调，只有「往连接里写」才能发现客户端已经消失。
 *       没有心跳时，一次卡在模型上的请求要等到下一次 delta 才知道用户早走了。</li>
 * </ol>
 *
 * <p>发的是 SSE 注释帧（{@code :ping}），浏览器与 {@code curl} 都安全——它不产生
 * {@code event:}/{@code data:} 行，不会触发前端的消息回调，也不会进入事件解析。
 *
 * <p>采用<b>空闲探测</b>而不是定时盲写：调度周期取间隔的一半，到点先看这条连接上次写是什么时候，
 * 没到间隔就跳过。模型高频出字时 delta 本身就是心跳，盲目写只会增加锁竞争与带宽。
 */
@Slf4j
@Component
public class SseHeartbeat {

    private final int intervalMs;
    private final long idleNanos;
    private final ScheduledExecutorService scheduler;

    public SseHeartbeat(ChatOrchestratorProperties properties) {
        Integer configured = properties.getHeartbeatIntervalMs();
        this.intervalMs = configured == null ? 15000 : configured;
        this.idleNanos = Math.max(0, intervalMs) * 1_000_000L;
        AtomicInteger seq = new AtomicInteger();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "chat-sse-heartbeat-" + seq.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean isEnabled() {
        return intervalMs > 0;
    }

    /** 为一条连接登记心跳；关闭（interval <= 0）时什么都不做。 */
    public void start(ChatStreamConnection connection) {
        if (!isEnabled() || connection == null) {
            return;
        }
        long tick = Math.max(500L, intervalMs / 2L);
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                connection.pingIfIdle(idleNanos);
            } catch (Exception ex) {
                // scheduleAtFixedRate 一旦抛出就会永久停止，这里必须兜住
                log.debug("[chat][sse] 心跳执行失败（已忽略）: {}", ex.toString());
            }
        }, tick, tick, TimeUnit.MILLISECONDS);
        connection.attachHeartbeat(future);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        log.info("[chat][sse] 心跳调度器已关闭");
    }
}
