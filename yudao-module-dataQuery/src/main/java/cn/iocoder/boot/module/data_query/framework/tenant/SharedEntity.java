package cn.iocoder.boot.module.data_query.framework.tenant;

/**
 * 平台共享表实体的公共约定，供 {@link SharedTenantServiceImpl} 统一读写租户编号。
 *
 * 实现类的主键与租户字段由 Lombok @Data 生成 getter/setter，无需额外代码。
 * 仅提示词 4 张表实现本接口；数据源侧与模型侧实体不实现。
 */
public interface SharedEntity {

    Long getId();

    Long getTenantId();

    /**
     * 返回类型声明为 SharedEntity（而非 void）：项目 lombok.config 开启了
     * lombok.accessors.chain=true，Lombok 生成的 setter 返回实体自身，
     * 用协变返回类型才能与链式 setter 匹配。
     */
    SharedEntity setTenantId(Long tenantId);

}
