package cn.iocoder.boot.module.data_query.framework;

import cn.iocoder.boot.module.data_query.framework.model.EchoModelClient;
import cn.iocoder.boot.module.data_query.framework.model.ModelClient;
import cn.iocoder.boot.module.data_query.framework.vector.SchemaEmbeddingProvider;
import cn.iocoder.boot.module.data_query.framework.vector.UnconfiguredSchemaEmbeddingProvider;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * dataQuery 模块装配类
 *
 * 框架的 YudaoMybatisAutoConfiguration 只扫描 cn.iocoder.yudao 下带 @Mapper 注解的接口，
 * 而本模块 Mapper 位于 cn.iocoder.boot.module.data_query.mapper 且未标注 @Mapper。
 * 这里通过 Spring Boot 3 的 AutoConfiguration.imports 自动生效，单独注册本模块的 Mapper；
 * Controller / Service 由启动类 scanBasePackages 追加的 cn.iocoder.boot.module 负责扫描。
 *
 * 若后续把模块包名统一为 cn.iocoder.yudao.module.data_query，并给 Mapper 加上 @Mapper，
 * 可删除本类及对应的 imports 文件。
 */
@AutoConfiguration
@MapperScan(basePackages = "cn.iocoder.boot.module.data_query.mapper")
public class DataQueryAutoConfiguration {

    /**
     * 联调占位实现：仅在没有真实 ModelClient 时生效。
     * 接入 OpenAI 兼容 / 通义 / DeepSeek 等真实客户端后，注册自己的 ModelClient Bean 即可自动覆盖，
     * 治理链路（路由、限流、熔断、重试、降级、日志）无需任何改动。
     *
     * <p>返回类型刻意写成具体类而不是 {@code ModelClient}：EchoModelClient 同时实现了
     * {@code StreamModelClient}，返回具体类后同一个 Bean 才能按两种类型被注入，
     * 避免 chat.enabled=false 的降级路径缺少流式 SPI 实现而启动失败。
     */
    @Bean
    @ConditionalOnMissingBean(ModelClient.class)
    public EchoModelClient echoModelClient() {
        return new EchoModelClient();
    }

    /**
     * P1 的 Embedding 占位实现：仅在没有真实「按租户动态 Embedding」Bean 时生效。
     * P2 的 TenantSchemaEmbeddingProvider 是 @Component，默认注册并覆盖本兜底 Bean；
     * 设置 data-query.embedding.enabled=false 后该 Component 不再注册，这里自动回退到占位实现
     * （供应商故障时的运维降级开关，不需要改代码或刷数据库）。
     * 占位期间 Document 仍完整落库，但 embedding 为 NULL、sync_status = 0（待构建），
     * status 接口会如实暴露待构建数量，不伪造向量、不假装成功。
     */
    @Bean
    @ConditionalOnMissingBean(SchemaEmbeddingProvider.class)
    public SchemaEmbeddingProvider unconfiguredSchemaEmbeddingProvider() {
        return new UnconfiguredSchemaEmbeddingProvider();
    }

}
