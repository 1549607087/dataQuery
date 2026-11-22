package cn.iocoder.boot.module.data_query.framework.chat;

/**
 * 用户主动取消问数/问答时抛出的内部控制流异常。
 *
 * <p>它不是对外错误码，而是贯穿「编排器 → 治理层 → 模型客户端」的取消信号：
 * 一旦抛出，各层立即停止后续 LLM 调用与 SQL 执行，最终由编排器以 STOPPED 状态正常收口落库。
 */
public class CancelledException extends RuntimeException {

    private final String clientRequestId;

    public CancelledException(String clientRequestId) {
        super("请求已取消: " + clientRequestId);
        this.clientRequestId = clientRequestId;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }
}
