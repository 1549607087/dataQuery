package cn.iocoder.boot.module.data_query.framework.datasource;

import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_CONNECTION_CONFIG_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.DATASOURCE_DRIVER_NOT_ALLOWED;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 目标数据源 JDBC 连接支持（元数据同步 / 连通性检测共用口径）。
 *
 * 三点约束：
 * 1. 驱动白名单，防止 driver_class 被改成任意类名后 Class.forName 加载；
 * 2. 元数据同步优先走只读通道（readonly_enabled = 1 且配置了 readonly_url），
 *    没配只读通道时降级用主连接，但连接本身 setReadOnly(true)；
 * 3. 用完即关，低频管理操作不占用业务查询连接池；登录超时 5 秒。
 */
public final class DatasourceJdbcSupport {

    /** 与 AiDatasourceServiceImpl 的驱动白名单保持一致 */
    public static final Set<String> ALLOWED_DRIVERS = Set.of(
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

    public static final int LOGIN_TIMEOUT_SECONDS = 5;

    private DatasourceJdbcSupport() {
    }

    /**
     * 打开目标数据源连接。密码已由 AiSecretTypeHandler 在查询侧解密，此处拿到的即为明文。
     *
     * @param preferReadonly 元数据同步传 true；连通性检测由调用方自行决定
     */
    public static Connection open(AiDatasource ds, boolean preferReadonly) throws SQLException {
        String driver = StringUtils.hasText(ds.getDriverClass()) ? ds.getDriverClass().trim() : "";
        if (!ALLOWED_DRIVERS.contains(driver)) {
            throw exception(DATASOURCE_DRIVER_NOT_ALLOWED);
        }
        boolean useReadonly = preferReadonly
                && ds.getReadonlyEnabled() != null && ds.getReadonlyEnabled() == 1
                && StringUtils.hasText(ds.getReadonlyUrl());
        String url = useReadonly ? ds.getReadonlyUrl().trim() : nullToEmpty(ds.getJdbcUrl()).trim();
        String username = useReadonly ? nullToEmpty(ds.getReadonlyUsername()) : nullToEmpty(ds.getUsername());
        String password = useReadonly ? nullToEmpty(ds.getReadonlyPassword()) : nullToEmpty(ds.getPassword());
        if (url.isEmpty()) {
            throw exception(DATASOURCE_CONNECTION_CONFIG_EMPTY);
        }
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException ex) {
            throw new SQLException("JDBC 驱动未引入：" + driver, ex);
        }
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        Connection connection = DriverManager.getConnection(url, username, password);
        if (preferReadonly) {
            // 即使只读通道未配置，也把连接设为只读；个别驱动不支持时忽略，不阻断同步
            try {
                connection.setReadOnly(true);
            } catch (SQLException ignored) {
                // no-op
            }
        }
        return connection;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

}
