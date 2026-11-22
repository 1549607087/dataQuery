package cn.iocoder.boot.module.data_query.framework.tenant;

import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.util.Assert;

import java.io.Serializable;
import java.util.List;

/**
 * 平台共享表的 Service 基类：统一注入「可见 / 可写」租户范围。
 *
 * 这 4 张表在 yudao.tenant.ignore-tables 中被放行（拦截器不再追加 tenant_id = ?），
 * 若不在业务层收口，分页/导出/更新会读到其它租户的覆盖记录，甚至能改平台模板。
 * 把范围控制收敛到本基类，Controller 的约 50 处调用点无需逐个改动。
 *
 * 规则：
 * 1. 查询（list / page / getById）：tenant_id IN (当前租户, 0)，租户覆盖优先由 Service 层排序保证；
 * 2. 新增（save）：显式 setTenantId(当前租户)。实体是普通 POJO，框架 DefaultDBFieldHandler 不会自动填充；
 * 3. 更新（updateById / update）：只允许当前租户的记录；平台模板与其它租户记录只读。
 *
 * 仅用于提示词 4 表。数据源侧与模型/治理侧均为租户私有，继续使用普通 ServiceImpl。
 */
public abstract class SharedTenantServiceImpl<M extends BaseMapper<T>, T extends SharedEntity>
        extends ServiceImpl<M, T> {

    @Override
    public List<T> list() {
        return getBaseMapper().selectList(new QueryWrapper<T>()
                .inSql("tenant_id", SharedTenantScope.visibleTenantIdSql()));
    }

    @Override
    public List<T> list(Wrapper<T> queryWrapper) {
        applyScope(queryWrapper, SharedTenantScope.visibleTenantIdSql());
        return getBaseMapper().selectList(queryWrapper);
    }

    @Override
    public List<T> list(IPage<T> page, Wrapper<T> queryWrapper) {
        applyScope(queryWrapper, SharedTenantScope.visibleTenantIdSql());
        return getBaseMapper().selectList(page, queryWrapper);
    }

    @Override
    public <E extends IPage<T>> E page(E page, Wrapper<T> queryWrapper) {
        applyScope(queryWrapper, SharedTenantScope.visibleTenantIdSql());
        return getBaseMapper().selectPage(page, queryWrapper);
    }

    @Override
    public T getById(Serializable id) {
        T entity = getBaseMapper().selectById(id);
        if (entity == null) {
            return null;
        }
        for (Long visible : SharedTenantScope.visibleTenantIds()) {
            Long owner = entity.getTenantId() == null ? SharedTenantScope.PLATFORM_TENANT_ID : entity.getTenantId();
            if (visible.equals(owner)) {
                return entity;
            }
        }
        return null;
    }

    @Override
    public boolean save(T entity) {
        // 放行表不会自动补 tenant_id，必须显式写入，否则 DB 默认值 0 会把租户记录变成平台模板
        entity.setTenantId(SharedTenantScope.currentTenantId());
        return getBaseMapper().insert(entity) > 0;
    }

    @Override
    public boolean updateById(T entity) {
        if (entity.getId() == null) {
            return false;
        }
        assertWritable(entity.getId());
        return getBaseMapper().updateById(entity) > 0;
    }

    @Override
    public boolean update(Wrapper<T> updateWrapper) {
        applyScope(updateWrapper, SharedTenantScope.writableTenantIdSql());
        return getBaseMapper().update(null, updateWrapper) > 0;
    }

    /** 平台模板（tenant_id = 0）与其它租户记录对当前租户只读 */
    private void assertWritable(Serializable id) {
        T exists = getBaseMapper().selectById(id);
        if (exists == null) {
            return; // 记录不存在，后续 update 自然返回 false
        }
        Assert.isTrue(SharedTenantScope.isWritable(exists.getTenantId()),
                "平台模板或其它租户的数据只读，不能修改");
    }

    /**
     * 用 SQL 片段注入 tenant_id 范围，避免泛型实体字段引用。
     * 值全部由 SharedTenantScope 从 Long 生成，不含外部输入，无 SQL 注入风险。
     *
     * 注意：这里不能调用 inSql(R column, ...)。AbstractWrapper 的列泛型 R 在
     * LambdaQueryWrapperX / LambdaUpdateWrapper 上是 SFunction，传入字符串列名
     * 会在求 ew.sqlSegment 时触发 ClassCastException(String -> SFunction)，
     * 表现为接口 500。改用 apply 直接拼 SQL 片段，与列泛型无关，
     * 对 QueryWrapper 与 Lambda Wrapper 同样安全。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyScope(Wrapper<T> wrapper, String scopeSql) {
        if (wrapper instanceof AbstractWrapper) {
            ((AbstractWrapper) wrapper).apply(true, "(tenant_id IN (" + scopeSql + "))");
        }
    }

}
