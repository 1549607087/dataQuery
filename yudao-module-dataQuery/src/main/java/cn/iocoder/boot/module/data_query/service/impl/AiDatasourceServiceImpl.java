package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.datasource.vo.datasource.AiDatasourceTestRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiDatasourceSyncLog;
import cn.iocoder.boot.module.data_query.mapper.AiDatasourceMapper;
import cn.iocoder.boot.module.data_query.service.AiDatasourceService;
import cn.iocoder.boot.module.data_query.service.AiDatasourceSyncLogService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_CODE_DUPLICATE;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_CONNECTION_CONFIG_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_DRIVER_NOT_ALLOWED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_NOT_EXISTS;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.TENANT_CONTEXT_MISSING;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 数据源 Service 实现
 *
 * 设计取舍：
 * 1. 密码字段由 AiDatasource#autoResultMap + AiSecretTypeHandler 透明加解密；
 *    RespVO 不含密码字段，只回传 passwordConfigured / readonlyPasswordConfigured 状态位。
 * 2. 连通性检测用 DriverManager 直连而非接入连接池：低频管理操作，用完即关，
 *    不占用业务查询连接池；驱动类做白名单，避免 Class.forName 加载任意类。
 * 3. 检测失败不抛异常：健康状态与错误摘要要落库，接口仍返回可读的结果对象。
 */
@Service
public class AiDatasourceServiceImpl extends ServiceImpl<AiDatasourceMapper, AiDatasource>
        implements AiDatasourceService {

    /** 允许显式加载的 JDBC 驱动，防止 driver_class 被配置成任意类名 */
    private static final Set<String> ALLOWED_DRIVERS = Set.of(
            "org.postgresql.Driver",
            "com.mysql.cj.jdbc.Driver",
            "com.mysql.jdbc.Driver",
            "oracle.jdbc.OracleDriver",
            "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "dm.jdbc.driver.DmDriver",
            "com.kingbase8.Driver",
            "org.opengauss.Driver",
            "com.clickhouse.jdbc.ClickHouseDriver",
            "ru.yandex.clickhouse.ClickHouseDriver",
            "org.apache.hive.jdbc.HiveDriver");

    private static final int LOGIN_TIMEOUT_SECONDS = 5;
    private static final int QUERY_TIMEOUT_SECONDS = 5;
    private static final String DEFAULT_VALIDATION_SQL = "SELECT 1";

    @Resource
    private AiDatasourceSyncLogService syncLogService;

    @Override
    public AiDatasource getDatasource(Long id) {
        if (id == null) {
            return null;
        }
        List<AiDatasource> list = list(new LambdaQueryWrapperX<AiDatasource>()
                .eq(AiDatasource::getId, id)
                .eq(AiDatasource::getDeleted, 0)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AiDatasource getDatasourceOwned(Long id) {
        AiDatasource datasource = getDatasource(id);
        if (datasource == null) {
            throw exception(DATASOURCE_NOT_EXISTS);
        }
        Long currentTenantId = TenantContextHolder.getTenantId();
        if (currentTenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        if (datasource.getTenantId() == null || !currentTenantId.equals(datasource.getTenantId())) {
            // 纵深防御：拦截器理论上已过滤，统一用「不存在」口径，不泄露其它租户的存在性
            throw exception(DATASOURCE_NOT_EXISTS);
        }
        return datasource;
    }

    @Override
    public Long createDatasource(AiDatasource entity) {
        Assert.notNull(entity, "entity 不能为空");
        validateCodeUnique(entity.getCode(), null);
        fillCreateFields(entity);
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateDatasource(AiDatasource entity) {
        Assert.notNull(entity, "entity 不能为空");
        AiDatasource exists = getDatasourceOwned(entity.getId());
        validateCodeUnique(entity.getCode() == null ? exists.getCode() : entity.getCode(), entity.getId());
        // 密钥留空表示不修改：MP 默认只更新非 null 字段，置 null 后不会覆盖已存密文
        if (!StringUtils.hasText(entity.getPassword())) {
            entity.setPassword(null);
        }
        if (!StringUtils.hasText(entity.getReadonlyPassword())) {
            entity.setReadonlyPassword(null);
        }
        // 运行时字段一律由服务端回写，杜绝前端伪造健康状态 / 同步状态 / 结构版本
        entity.setHealthStatus(null);
        entity.setLastCheckTime(null);
        entity.setLastCheckResult(null);
        entity.setSyncStatus(null);
        entity.setLastSyncTime(null);
        entity.setStructVersion(null);
        fillUpdateFields(entity);
        updateById(entity);
    }

    @Override
    public void deleteDatasource(Long id) {
        getDatasourceOwned(id);
        update(new LambdaUpdateWrapper<AiDatasource>()
                .eq(AiDatasource::getId, id)
                .set(AiDatasource::getDeleted, 1));
    }

    @Override
    public void deleteDatasourceList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            getDatasourceOwned(id);
        }
        update(new LambdaUpdateWrapper<AiDatasource>()
                .in(AiDatasource::getId, ids)
                .set(AiDatasource::getDeleted, 1));
    }

    @Override
    public List<AiDatasource> listEnabledDatasources() {
        return list(new LambdaQueryWrapperX<AiDatasource>()
                .eq(AiDatasource::getStatus, 0)
                .eq(AiDatasource::getDeleted, 0)
                .orderByAsc(AiDatasource::getSort)
                .orderByAsc(AiDatasource::getId));
    }

    @Override
    public AiDatasourceTestRespVO testConnection(Long id) {
        AiDatasource datasource = getDatasourceOwned(id);
        String driver = StringUtils.hasText(datasource.getDriverClass())
                ? datasource.getDriverClass().trim() : "";
        String url = StringUtils.hasText(datasource.getJdbcUrl())
                ? datasource.getJdbcUrl().trim() : "";
        if (!ALLOWED_DRIVERS.contains(driver)) {
            throw exception(DATASOURCE_DRIVER_NOT_ALLOWED);
        }
        if (url.isEmpty()) {
            throw exception(DATASOURCE_CONNECTION_CONFIG_EMPTY);
        }

        String validationSql = StringUtils.hasText(datasource.getPoolValidationSql())
                ? datasource.getPoolValidationSql() : DEFAULT_VALIDATION_SQL;
        LocalDateTime startTime = LocalDateTime.now();
        long begin = System.currentTimeMillis();
        boolean success = false;
        String errorMsg = "";
        try {
            Class.forName(driver);
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            try (Connection connection = DriverManager.getConnection(url,
                    nullToEmpty(datasource.getUsername()), nullToEmpty(datasource.getPassword()));
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                try (ResultSet ignored = statement.executeQuery(validationSql)) {
                    // 只需成功打开结果集即可判定连通；validation SQL 固定为 SELECT 1 类语句
                }
            }
            success = true;
        } catch (Exception ex) {
            String rawMessage = ex.getClass().getSimpleName() + ": " + nullToEmpty(ex.getMessage());
            errorMsg = truncate(sanitizeError(rawMessage, ex.getClass().getSimpleName()), 1000);
        }
        long latencyMs = System.currentTimeMillis() - begin;
        LocalDateTime endTime = LocalDateTime.now();

        update(new LambdaUpdateWrapper<AiDatasource>()
                .eq(AiDatasource::getId, id)
                .set(AiDatasource::getHealthStatus, success ? 1 : 2)
                .set(AiDatasource::getLastCheckTime, endTime)
                .set(AiDatasource::getLastCheckResult,
                        success ? "连接正常（" + validationSql + "）" : truncate(errorMsg, 1000)));

        AiDatasourceSyncLog log = new AiDatasourceSyncLog();
        log.setDatasourceId(id);
        log.setTaskNo("CHK" + System.currentTimeMillis());
        log.setSyncType(1);       // 1 连通性检测
        log.setTriggerType(1);    // 1 手动
        log.setScope("连通性检测");
        log.setStartTime(startTime);
        log.setEndTime(endTime);
        log.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
        log.setStatus(success ? 1 : 3);
        log.setErrorMsg(success ? "" : truncate(errorMsg, 2000));
        log.setStructVersionBefore(datasource.getStructVersion());
        log.setStructVersionAfter(datasource.getStructVersion());
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        log.setOperatorId(userId == null ? 0L : userId);
        log.setOperatorName(userId == null ? "" : userId.toString());
        syncLogService.createLog(log);

        AiDatasourceTestRespVO result = new AiDatasourceTestRespVO();
        result.setDatasourceId(id);
        result.setSuccess(success);
        result.setHealthStatus(success ? 1 : 2);
        result.setLatencyMs(latencyMs);
        result.setMessage(success ? "连接正常" : errorMsg);
        result.setCheckedAt(endTime);
        return result;
    }

    /** 租户内 code 唯一；excludeId 非空时排除自身（更新场景） */
    private void validateCodeUnique(String code, Long excludeId) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        LambdaQueryWrapperX<AiDatasource> query = new LambdaQueryWrapperX<AiDatasource>()
                .eq(AiDatasource::getCode, code)
                .eq(AiDatasource::getDeleted, 0);
        if (excludeId != null) {
            query.ne(AiDatasource::getId, excludeId);
        }
        if (!list(query.last("LIMIT 1")).isEmpty()) {
            throw exception(DATASOURCE_CODE_DUPLICATE);
        }
    }

    /** 实体是普通 POJO，框架不会自动填充；数据源侧全租户私有，必须显式写 tenant_id */
    private void fillCreateFields(AiDatasource entity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        String user = userId == null ? "" : userId.toString();
        LocalDateTime now = LocalDateTime.now();
        entity.setId(null); // 由 ASSIGN_ID 生成
        entity.setTenantId(tenantId);
        entity.setCreator(user);
        entity.setUpdater(user);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
    }

    private void fillUpdateFields(AiDatasource entity) {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw exception(TENANT_CONTEXT_MISSING);
        }
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        entity.setTenantId(tenantId);
        entity.setUpdater(userId == null ? "" : userId.toString());
        entity.setUpdateTime(LocalDateTime.now());
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 数据库返回的本地化错误消息可能不是 UTF-8（典型：PostgreSQL lc_messages 为中文 GBK），
     * JDBC 解码后会产生 U+FFFD / 拉丁扩展字符等乱码。这些乱码会被写进
     * last_check_result、ai_datasource_sync_log.error_msg 并回显给前端，必须拦截。
     *
     * 只放行 ASCII 可打印字符、CJK 汉字与常用中文标点；出现其它字符时降级为结构化摘要，
     * 保留异常类名（足以判断是认证失败、连接超时还是网络不可达），不保留不可读原文。
     */
    private static String sanitizeError(String message, String exceptionName) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            boolean asciiPrintable = c >= 0x20 && c <= 0x7E;
            boolean cjk = c >= 0x4E00 && c <= 0x9FFF;
            boolean cjkPunctuation = c == 0xFF0C || c == 0x3002 || c == 0xFF1A
                    || c == 0xFF08 || c == 0xFF09 || c == 0x3001;
            if (!asciiPrintable && !cjk && !cjkPunctuation) {
                return "连接失败（" + exceptionName
                        + "；数据库返回的本地化错误消息含非 UTF-8 字符，已省略原文）";
            }
        }
        return message;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= maxLength ? flat : flat.substring(0, maxLength);
    }

}
