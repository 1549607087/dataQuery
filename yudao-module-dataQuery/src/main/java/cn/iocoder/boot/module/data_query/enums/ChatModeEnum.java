package cn.iocoder.boot.module.data_query.enums;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.CHAT_MODE_INVALID;

/**
 * 统一对话门面的业务类型。
 *
 * <p>一个会话只能属于一种模式：QUERY 绑定 datasourceId，KNOWLEDGE 绑定 baseId，
 * 两条归属链互斥。前端新建会话时选定 mode，续聊时 mode 由会话决定、前端回传用于路由。
 */
@Getter
@AllArgsConstructor
public enum ChatModeEnum {

    QUERY("QUERY", "智能问数"),
    KNOWLEDGE("KNOWLEDGE", "知识库问答");

    private final String code;
    private final String name;

    public static ChatModeEnum of(String code) {
        if (code == null) {
            throw ServiceExceptionUtil.exception(CHAT_MODE_INVALID);
        }
        for (ChatModeEnum mode : values()) {
            if (mode.code.equalsIgnoreCase(code.trim())) {
                return mode;
            }
        }
        throw ServiceExceptionUtil.exception(CHAT_MODE_INVALID);
    }

    public static boolean isQuery(String code) {
        return QUERY.code.equalsIgnoreCase(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean isKnowledge(String code) {
        return KNOWLEDGE.code.equalsIgnoreCase(code == null ? "" : code.trim().toUpperCase(Locale.ROOT));
    }
}
