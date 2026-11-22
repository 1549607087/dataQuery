package cn.iocoder.boot.module.data_query.framework.vector;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL pgvector 列与 Java String 之间的 TypeHandler。
 *
 * 为什么必须用 Types.OTHER（而不是 setString）：
 * pgvector 0.8.7 只注册了「数组 / halfvec / sparsevec -> vector」的 cast，
 * 没有 varchar -> vector 的 cast。若用 setString 传 "[0.1,0.2]"，
 * PG 会报 column "embedding" is of type vector but expression is of type character varying。
 * 用 setObject(i, value, Types.OTHER) 让 PG JDBC 把参数作为 unspecified（oid=0）发送，
 * 服务端再按目标列的 input function 解析，这也是 pgvector 官方 JDBC 写法。
 *
 * 读取侧直接用 getString 拿回 "[0.1,0.2,...]" 字面量，不依赖 PG vector 的 Java 类型。
 * 该 Handler 只用于 ai_schema_vector.embedding；其它列仍是普通 MyBatis 映射。
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class PgVectorTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }

}
