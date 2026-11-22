package cn.iocoder.boot.module.data_query.framework.crypto;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * 凭证字段加密 TypeHandler：在框架 {@link EncryptTypeHandler} 之上补两个上线必需的兼容能力。
 *
 * 1. 空串安全：框架 Handler 的 decrypt 只对 null 短路，存量 api_key = '' 会进入 AES 解密并抛异常。
 *    这里把空串写为 NULL、读为空串，避免存量数据导致接口 500。
 * 2. 存量明文平滑迁移：若库中已是历史明文（非密文、或密钥不匹配），解密失败时按明文返回，
 *    并在下次更新时由本 Handler 自动写成密文。避免上线前必须停机刷一遍历史 Key。
 *
 * 密钥仍读取 mybatis-plus.encryptor.password（application.yaml），与框架保持一致。
 * 换密钥需全表重加密，轮换流程见 docs/tenant-isolation-design.md 第 11 章。
 */
@Slf4j
public class AiSecretTypeHandler extends BaseTypeHandler<String> {

    private static final EncryptTypeHandler DELEGATE = new EncryptTypeHandler();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        if (parameter.isEmpty()) {
            ps.setNull(i, Types.VARCHAR);
            return;
        }
        DELEGATE.setNonNullParameter(ps, i, parameter, jdbcType);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String raw = rs.getString(columnName);
        return readSafely(raw, () -> DELEGATE.getNullableResult(rs, columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String raw = rs.getString(columnIndex);
        return readSafely(raw, () -> DELEGATE.getNullableResult(rs, columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String raw = cs.getString(columnIndex);
        return readSafely(raw, () -> DELEGATE.getNullableResult(cs, columnIndex));
    }

    private String readSafely(String raw, SqlSupplier<String> delegate) throws SQLException {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        try {
            return delegate.get();
        } catch (Exception ex) {
            // 存量明文或密钥不匹配：按原文返回，不阻断查询；下次写入会自动加密
            log.warn("[readSafely][凭证解密失败，按存量明文返回，请尽快轮换密钥或重新录入] {}", ex.getMessage());
            return raw;
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<V> {

        V get() throws SQLException;

    }

}
