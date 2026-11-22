package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.vector.vo.SchemaVectorBuildRespVO;
import cn.iocoder.boot.module.data_query.entity.AiSchemaVector;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaVectorRebuildProperties;
import cn.iocoder.boot.module.data_query.mapper.AiSchemaVectorMapper;
import cn.iocoder.boot.module.data_query.service.SchemaVectorBuildService;
import cn.iocoder.boot.module.data_query.service.SchemaVectorIndexSyncService;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 元数据变更 → Schema 向量索引联动实现。
 *
 * <p>线程模型：单线程调度池 + 每数据源一个待执行任务（防抖）。单线程天然串行，
 * 不需要额外按 datasourceId 加锁，同库不会出现两次构建并发写同一批向量行。
 *
 * <p>租户上下文：{@link TenantContextHolder} 是 TransmittableThreadLocal，但本池没有用
 * TTL 包装任务，因此必须显式 set / clear —— 否则 {@code build()} 里的
 * {@code requireOwned(datasourceId)} 会抛 {@code TENANT_CONTEXT_MISSING}，
 * 而线程复用留下的旧租户号更会串到别的租户头上。
 */
@Slf4j
@Service
public class SchemaVectorIndexSyncServiceImpl implements SchemaVectorIndexSyncService {

    /** 异步构建线程名，便于在 jstack / 线程转储里定位 */
    private static final String THREAD_NAME = "schema-vector-rebuild";

    @Resource
    private AiSchemaVectorMapper vectorMapper;

    @Resource
    private SchemaVectorBuildService vectorBuildService;

    @Resource
    private SchemaVectorRebuildProperties properties;

    /** datasourceId → 待执行的防抖任务；新请求取消旧任务，只保留最后一次 */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    private ScheduledExecutorService executor;

    @PostConstruct
    public void init() {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, THREAD_NAME);
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy());
        // 被防抖取消的任务立刻从队列移除，否则连续修改会在队列里留下大量空任务
        pool.setRemoveOnCancelPolicy(true);
        pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.executor = pool;
    }

    @PreDestroy
    public void destroy() {
        for (ScheduledFuture<?> future : pending.values()) {
            future.cancel(false);
        }
        pending.clear();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Override
    public void onTablesChanged(Long datasourceId, Collection<Long> tableIds) {
        if (datasourceId == null || tableIds == null || tableIds.isEmpty()) {
            return;
        }
        markDirty(datasourceId, wrapper -> wrapper.in(AiSchemaVector::getTableId, tableIds));
        requestRebuild(datasourceId);
    }

    @Override
    public void onColumnsChanged(Long datasourceId, Collection<Long> columnIds) {
        if (datasourceId == null || columnIds == null || columnIds.isEmpty()) {
            return;
        }
        markDirty(datasourceId, wrapper -> wrapper.in(AiSchemaVector::getColumnId, columnIds));
        requestRebuild(datasourceId);
    }

    @Override
    public void onStructureChanged(Long datasourceId) {
        requestRebuild(datasourceId);
    }

    @Override
    public void requestRebuild(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        if (!Boolean.TRUE.equals(properties.getAutoRebuildEnabled())) {
            log.debug("[requestRebuild][datasourceId({})] 自动重建已关闭，仅保留脏标", datasourceId);
            return;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            // 没有租户上下文就无法校验数据源归属，宁可跳过也不猜
            log.warn("[requestRebuild][datasourceId({})] 无租户上下文，跳过自动重建", datasourceId);
            return;
        }
        Runnable submit = () -> schedule(datasourceId, tenantId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 元数据还在未提交事务里时立即构建会读到旧快照，必须等提交后再触发
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    /**
     * 标脏：只更新存活行，且限定在本次变更命中的表 / 字段上。
     *
     * <p>失败只记日志 —— 元数据已经写成功，不能因为向量表更新失败把整次保存回滚；
     * 构建侧的 content_hash 比对是兜底，脏标只是让「待重算」这件事可见、可重试。
     */
    private void markDirty(Long datasourceId, Consumer<LambdaUpdateWrapper<AiSchemaVector>> scope) {
        try {
            LambdaUpdateWrapper<AiSchemaVector> wrapper = new LambdaUpdateWrapper<AiSchemaVector>()
                    .eq(AiSchemaVector::getDatasourceId, datasourceId)
                    .eq(AiSchemaVector::getDeleted, 0);
            scope.accept(wrapper);
            wrapper.set(AiSchemaVector::getSyncStatus, 2)
                    .set(AiSchemaVector::getUpdateTime, LocalDateTime.now());
            int rows = vectorMapper.update(null, wrapper);
            log.debug("[markDirty][datasourceId({})] 标记 {} 行待重建", datasourceId, rows);
        } catch (Exception ex) {
            log.warn("[markDirty][datasourceId({})] 标脏失败，依赖 content_hash 兜底：{}",
                    datasourceId, ex.getMessage());
        }
    }

    private void schedule(Long datasourceId, Long tenantId) {
        if (executor == null || executor.isShutdown()) {
            log.warn("[schedule][datasourceId({})] 调度器已关闭，跳过自动重建", datasourceId);
            return;
        }
        long delay = properties.getDebounceMs() == null ? 0 : Math.max(0, properties.getDebounceMs());
        try {
            pending.compute(datasourceId, (key, existing) -> {
                if (existing != null) {
                    existing.cancel(false);
                }
                return executor.schedule(() -> {
                    pending.remove(datasourceId);
                    rebuild(datasourceId, tenantId);
                }, delay, TimeUnit.MILLISECONDS);
            });
        } catch (RejectedExecutionException ex) {
            log.warn("[schedule][datasourceId({})] 调度被拒绝，脏标保留待下次构建", datasourceId);
        }
    }

    private void rebuild(Long datasourceId, Long tenantId) {
        try {
            // 先清再设：线程复用可能残留上一次任务的租户号
            TenantContextHolder.clear();
            TenantContextHolder.setTenantId(tenantId);
            SchemaVectorBuildRespVO resp = vectorBuildService.build(datasourceId, false);
            log.info("[rebuild][datasourceId({})] 自动增量重建完成：created={} updated={} skipped={} "
                            + "retired={} embedded={} pendingEmbedding={}",
                    datasourceId, resp.getCreated(), resp.getUpdated(), resp.getSkipped(),
                    resp.getRetired(), resp.getEmbedded(), resp.getPendingEmbedding());
        } catch (Exception ex) {
            // 不重试、不外抛：脏标仍在，下一次变更或人工 rebuild 会带上这批行
            log.warn("[rebuild][datasourceId({})] 自动增量重建失败，脏标保留待重试：{}",
                    datasourceId, ex.getMessage(), ex);
        } finally {
            TenantContextHolder.clear();
        }
    }

}
