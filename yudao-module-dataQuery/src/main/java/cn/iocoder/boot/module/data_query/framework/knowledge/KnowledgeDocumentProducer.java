package cn.iocoder.boot.module.data_query.framework.knowledge;

import cn.iocoder.boot.module.data_query.entity.AiKnowledgeDocument;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeDocumentService;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.message.MessageBuilder;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.Transaction;
import org.apache.rocketmq.client.apis.producer.TransactionChecker;
import org.apache.rocketmq.client.apis.producer.TransactionResolution;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 知识库文档解析消息生产者（原生 rocketmq-client-java 5.x，gRPC Proxy 模式）。
 *
 * 设计要点：
 * 1. gRPC SDK 不读 starter 的 rocketmq.* 配置，全部走 {@link KnowledgeProperties.Mq}；
 * 2. 事务消息：beginTransaction → send(message, txn) → commit；半消息阶段消费端不可见，
 *    保证「document 落库成功」与「消息可消费」一致，异常回查由 {@link TransactionChecker} 兜底；
 * 3. 租户透传：gRPC SDK 无芋道 hook，靠 addProperty("tenantId") 显式写入；
 *    回查线程无租户上下文，需从消息属性取出后 set/clear，否则租户插件会过滤掉查询；
 * 4. Producer 懒加载：构造期不连 Proxy，避免 MQ 未就绪时拖垮应用启动；首次发送时构建。
 */
@Slf4j
@Component
public class KnowledgeDocumentProducer {

    @Resource
    private KnowledgeProperties properties;

    @Resource
    private AiKnowledgeDocumentService documentService;

    @Resource
    private KnowledgeIngestService ingestService;

    private final ClientServiceProvider provider = ClientServiceProvider.loadService();
    private volatile Producer producer;
    private final Object initLock = new Object();

    /** 延迟初始化 Producer：仅在首次发送时连接 Proxy */
    private Producer getOrCreateProducer() throws ClientException {
        Producer p = producer;
        if (p != null) {
            return p;
        }
        synchronized (initLock) {
            if (producer == null) {
                KnowledgeProperties.Mq mq = properties.getMq();
                ClientConfiguration conf = ClientConfiguration.newBuilder()
                        .setEndpoints(mq.getEndpoints())
                        .setMaxStartupAttempts(Math.max(1, mq.getMaxAttempts()))
                        .build();
                // 事务检查器：broker 回查时按 documentId 查库决定提交/回滚
                TransactionChecker checker = new TransactionChecker() {
                    @Override
                    public TransactionResolution check(MessageView messageView) {
                        String documentIdStr = messageView.getProperties().get("documentId");
                        String tenantIdStr = messageView.getProperties().get("tenantId");
                        Long originTenantId = TenantContextHolder.getTenantId();
                        try {
                            if (documentIdStr == null || tenantIdStr == null) {
                                log.warn("[知识库 MQ] 事务回查缺少 documentId/tenantId 属性，回滚：{}", messageView.getMessageId());
                                return TransactionResolution.ROLLBACK;
                            }
                            // 回查线程无租户上下文，显式设置后再查（租户插件会追加 tenant_id）
                            TenantContextHolder.setTenantId(Long.valueOf(tenantIdStr));
                            AiKnowledgeDocument document = documentService.getDocument(Long.valueOf(documentIdStr));
                            return document != null ? TransactionResolution.COMMIT : TransactionResolution.ROLLBACK;
                        } catch (Exception ex) {
                            log.error("[知识库 MQ] 事务回查异常，回滚。documentId={}", documentIdStr, ex);
                            return TransactionResolution.ROLLBACK;
                        } finally {
                            // 精确还原上下文：原值非 null 则恢复，否则清除，避免污染回查线程池
                            if (originTenantId != null) {
                                TenantContextHolder.setTenantId(originTenantId);
                            } else {
                                TenantContextHolder.clear();
                            }
                        }
                    }
                };
                producer = provider.newProducerBuilder()
                        .setClientConfiguration(conf)
                        .setTopics(mq.getTopic())
                        .setMaxAttempts(Math.max(1, mq.getMaxAttempts()))
                        .setTransactionChecker(checker)
                        .build();
                log.info("[知识库 MQ] Producer 初始化成功，endpoints={}, topic={}", mq.getEndpoints(), mq.getTopic());
            }
            p = producer;
        }
        return p;
    }

    /**
     * 发送文档解析事务消息，标准顺序：begin → 发半消息 → 执行本地事务（落 document）→ commit。
     *
     * <p>半消息在 commit 前消费端不可见，因此「document 落库成功」与「消息可消费」
     * 要么都成功、要么都失败。documentId 由调用方预生成并写入消息 Key，供事务回查定位。
     *
     * @param message          消息体（含预生成的 documentId）
     * @param localTransaction 本地事务动作（落 document），仅在半消息发出后执行
     */
    public void send(KnowledgeParseMessage message, Runnable localTransaction) {
        KnowledgeProperties.Mq mq = properties.getMq();
        if (!Boolean.TRUE.equals(mq.getEnabled())) {
            // 本地无 MQ 排障：落库后同步调用摄取服务兜底，前端仍按 parse_status 轮询
            localTransaction.run();
            log.warn("[知识库 MQ] mq.enabled=false，走同步摄取兜底。documentId={}", message.getDocumentId());
            try {
                ingestService.ingest(message, message.getTenantId());
            } catch (Exception ex) {
                // 同步摄取失败由 ingest 内部标记 parse_status=3，不向上抛，避免「已落库却报 500」
                log.error("[知识库 MQ] 同步摄取兜底异常，documentId={}", message.getDocumentId(), ex);
            }
            return;
        }
        Transaction txn = null;
        try {
            Producer p = getOrCreateProducer();
            txn = p.beginTransaction();

            MessageBuilder msgBuilder = provider.newMessageBuilder()
                    .setTopic(mq.getTopic())
                    .setTag(mq.getTag())
                    .setKeys(String.valueOf(message.getDocumentId()))
                    .addProperty("tenantId", String.valueOf(message.getTenantId()))
                    .addProperty("documentId", String.valueOf(message.getDocumentId()))
                    .setBody(JsonUtils.toJsonString(message).getBytes(StandardCharsets.UTF_8));
            Message msg = msgBuilder.build();

            p.send(msg, txn);       // 半消息：此时消费端不可见
            localTransaction.run(); // 本地事务：落 ai_knowledge_document
            txn.commit();           // 提交后消费端可见
            log.info("[知识库 MQ] 解析消息发送成功，documentId={}, baseId={}, tenantId={}",
                    message.getDocumentId(), message.getBaseId(), message.getTenantId());
        } catch (Exception ex) {
            // 半消息已发出但落库/提交失败时回滚，让 broker 丢弃半消息；异常回查由 checker 兜底
            if (txn != null) {
                try {
                    txn.rollback();
                } catch (Exception rollbackEx) {
                    log.warn("[知识库 MQ] 事务回滚失败（依赖回查兜底），documentId={}", message.getDocumentId(), rollbackEx);
                }
            }
            throw new IllegalStateException("[知识库 MQ] 解析消息发送失败，documentId=" + message.getDocumentId(), ex);
        }
    }

    @PreDestroy
    public void close() {
        Producer p = producer;
        if (p != null) {
            try {
                p.close();
                log.info("[知识库 MQ] Producer 已关闭");
            } catch (Exception ex) {
                log.warn("[知识库 MQ] Producer 关闭异常", ex);
            }
        }
    }
}
