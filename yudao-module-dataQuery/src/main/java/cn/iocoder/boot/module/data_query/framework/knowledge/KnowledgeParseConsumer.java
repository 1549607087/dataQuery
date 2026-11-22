package cn.iocoder.boot.module.data_query.framework.knowledge;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * 知识库文档解析消息消费者（原生 rocketmq-client-java 5.x，gRPC Proxy 模式）。
 *
 * 与 Producer 对称：gRPC SDK 不读 starter 配置，全部走 {@link KnowledgeProperties.Mq}；
 * 仅当 data-query.knowledge.mq.enabled=true 时启动（默认 true），mq.enabled=false 时不创建消费者，
 * 由上传链路同步调用 {@link KnowledgeIngestService} 兜底。
 *
 * 消费要点：
 * 1. 租户透传：从消息属性取 tenantId 显式 set 到 TenantContextHolder，finally 精确还原/清除，
 *    否则租户插件查不到数据、或污染消费线程池；
 * 2. 三方校验交给 IngestService（属性 / body / 库）；
 * 3. 业务异常返回 FAILURE 让 broker 重投；超过 maxConsumeAttempts 的毒消息返回 SUCCESS 跳过并告警，
 *    避免无限重投拖垮消费者；
 * 4. 懒加载：构造期不连 Proxy，@PostConstruct 之后由 Spring 初始化时才建 Consumer。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data-query.knowledge.mq", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class KnowledgeParseConsumer {

    @Resource
    private KnowledgeProperties properties;
    @Resource
    private KnowledgeIngestService ingestService;

    private final ClientServiceProvider provider = ClientServiceProvider.loadService();
    private volatile PushConsumer consumer;

    /** Spring 容器启动时建立消费者订阅；连接失败不影响应用其余部分启动 */
    @PostConstruct
    public void init() {
        KnowledgeProperties.Mq mq = properties.getMq();
        try {
            ClientConfiguration conf = ClientConfiguration.newBuilder()
                    .setEndpoints(mq.getEndpoints())
                    .setMaxStartupAttempts(Math.max(1, mq.getMaxAttempts()))
                    .build();
            int threads = mq.getConsumeThreadCount() != null ? mq.getConsumeThreadCount() : 4;
            consumer = provider.newPushConsumerBuilder()
                    .setClientConfiguration(conf)
                    .setConsumerGroup(mq.getConsumerGroup())
                    .setConsumptionThreadCount(Math.max(1, threads))
                    .setSubscriptionExpressions(Collections.singletonMap(
                            mq.getTopic(), new FilterExpression(mq.getTag())))
                    .setMessageListener(this::consume)
                    .build();
            log.info("[知识库 MQ] Consumer 启动成功，endpoints={}, topic={}, tag={}, group={}",
                    mq.getEndpoints(), mq.getTopic(), mq.getTag(), mq.getConsumerGroup());
        } catch (Exception ex) {
            // 不 @PostConstruct 直接失败启动：MQ 不可用时应用仍可启动，由上传侧失败可见
            log.error("[知识库 MQ] Consumer 启动失败（应用继续启动，文档将停留在待解析）：endpoints={}",
                    mq.getEndpoints(), ex);
        }
    }

    private ConsumeResult consume(MessageView messageView) {
        String messageId = messageView.getMessageId().toString();
        Long originTenantId = TenantContextHolder.getTenantId();
        try {
            // 1. 从消息属性取 tenantId，显式设置租户上下文
            String tenantIdStr = messageView.getProperties().get("tenantId");
            if (tenantIdStr == null) {
                log.error("[知识库 MQ] 消息缺少 tenantId 属性，跳过：messageId={}", messageId);
                return skipPoison(messageView, "缺少 tenantId 属性");
            }
            Long tenantId;
            try {
                tenantId = Long.valueOf(tenantIdStr);
            } catch (NumberFormatException ex) {
                log.error("[知识库 MQ] tenantId 属性格式非法，跳过：messageId={}, tenantId={}",
                        messageId, tenantIdStr);
                return skipPoison(messageView, "tenantId 格式非法");
            }
            TenantContextHolder.setTenantId(tenantId);

            // 2. 解析 body
            KnowledgeParseMessage message = parseBody(messageView, messageId);
            if (message == null) {
                return skipPoison(messageView, "消息体解析失败");
            }

            // 3. 调用摄取服务
            ingestService.ingest(message, tenantId);
            return ConsumeResult.SUCCESS;
        } catch (Exception ex) {
            log.error("[知识库 MQ] 消费异常，重投：messageId={}, attempt={}",
                    messageId, messageView.getDeliveryAttempt(), ex);
            return ConsumeResult.FAILURE;
        } finally {
            // 精确还原上下文：原值非 null 则恢复，否则清除，避免污染消费线程池
            if (originTenantId != null) {
                TenantContextHolder.setTenantId(originTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    private KnowledgeParseMessage parseBody(MessageView messageView, String messageId) {
        try {
            ByteBuffer body = messageView.getBody();
            byte[] bytes = new byte[body.remaining()];
            body.get(bytes);
            return JsonUtils.parseObject(bytes, KnowledgeParseMessage.class);
        } catch (Exception ex) {
            log.error("[知识库 MQ] 消息体反序列化失败：messageId={}", messageId, ex);
            return null;
        }
    }

    /** 毒消息处理：未超次数返回 FAILURE 重投；超限返回 SUCCESS 跳过（避免无限重投） */
    private ConsumeResult skipPoison(MessageView messageView, String reason) {
        int attempt = messageView.getDeliveryAttempt();
        int max = properties.getMq().getMaxConsumeAttempts() != null
                ? properties.getMq().getMaxConsumeAttempts() : 5;
        if (attempt >= max) {
            log.error("[知识库 MQ] 毒消息超过最大消费次数({})，跳过：messageId={}, attempt={}, reason={}",
                    max, messageView.getMessageId(), attempt, reason);
            return ConsumeResult.SUCCESS;
        }
        return ConsumeResult.FAILURE;
    }

    @PreDestroy
    public void close() {
        PushConsumer c = consumer;
        if (c != null) {
            try {
                c.close();
                log.info("[知识库 MQ] Consumer 已关闭");
            } catch (Exception ex) {
                log.warn("[知识库 MQ] Consumer 关闭异常", ex);
            }
        }
    }
}
