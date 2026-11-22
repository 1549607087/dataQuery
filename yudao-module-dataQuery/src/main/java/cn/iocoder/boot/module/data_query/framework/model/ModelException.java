package cn.iocoder.boot.module.data_query.framework.model;

import lombok.Getter;

/**
 * 模型调用异常，携带分类供重试 / 降级 / 熔断决策使用
 */
@Getter
public class ModelException extends RuntimeException {

    private final ModelErrorCategory category;

    private final String errorCode;

    public ModelException(ModelErrorCategory category, String message) {
        this(category, category.name(), message, null);
    }

    public ModelException(ModelErrorCategory category, String errorCode, String message) {
        this(category, errorCode, message, null);
    }

    public ModelException(ModelErrorCategory category, String message, Throwable cause) {
        this(category, category.name(), message, cause);
    }

    public ModelException(ModelErrorCategory category, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.errorCode = errorCode;
    }

}
