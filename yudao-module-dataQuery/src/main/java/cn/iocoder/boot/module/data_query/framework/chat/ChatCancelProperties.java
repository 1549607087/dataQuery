package cn.iocoder.boot.module.data_query.framework.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 问答取消能力的可调参数。
 *
 * <p>安全相关：取消只影响「是否继续生成」，不绕过任何租户隔离与 SQL 闸门。
 */
@Component
@ConfigurationProperties(prefix = "data-query.cancel")
@Data
public class ChatCancelProperties {

    /**
     * 是否允许前端通过 {@code POST /data-query/chat/cancel} 主动停止。
     *
     * <p>关闭只关掉这个端点；**「客户端断连即取消」不受影响**——后者由 SSE 连接自身的
     * 写失败触发，泄漏的是服务端 token，不存在被滥用的可能，因此不提供关闭入口。
     */
    private Boolean enabled = true;

    /** 在途令牌保留时长（毫秒），超时惰性清理，防止异常路径未 remove 导致泄漏 */
    private Integer tokenTtlMs = 300000;
}
