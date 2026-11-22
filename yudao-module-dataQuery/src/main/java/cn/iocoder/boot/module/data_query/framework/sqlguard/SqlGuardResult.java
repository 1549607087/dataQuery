package cn.iocoder.boot.module.data_query.framework.sqlguard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * S6 校验报告：通过时给 SafeSql，拒绝时给明确错误码与原因（供 SQL_FIX 回灌、手工编辑）。
 *
 * 拒绝路径**绝不产出 executableSql**，S7 拿不到可执行文本，从结构上保证
 * 「校验失败不会被误执行」。
 */
@Data
@Schema(description = "S6 SQL 静态校验报告")
public class SqlGuardResult {

    @Schema(description = "是否通过")
    private Boolean passed;

    @Schema(description = "拒绝时的业务错误码；通过为 null")
    private Integer errorCode;

    @Schema(description = "拒绝原因 / 修正建议（用户可见）")
    private String errorMsg;

    @Schema(description = "抽取出的原始 SQL（拒绝时也会回显，便于手工编辑）")
    private String originalSql;

    @Schema(description = "校验通过后的可执行 SQL；未通过时为 null")
    private SafeSql safeSql;

    @Schema(description = "逐项校验明细（PASS/REJECT 依据），便于联调与审计")
    private List<String> checks = new ArrayList<>();

    @Schema(description = "告警（例如只读通道未配置、sql_rules 被安全红线覆盖）")
    private List<String> warnings = new ArrayList<>();

    @Schema(description = "本次生效的白名单表名")
    private List<String> whitelistTables = new ArrayList<>();

    @Schema(description = "本次生效的白名单列名")
    private List<String> whitelistColumns = new ArrayList<>();

    @Schema(description = "S3 召回方式")
    private String recallMode;

    @Schema(description = "是否自动追加了 LIMIT")
    private Boolean limitApplied;

    @Schema(description = "是否把超限的 LIMIT 收敛到 max_rows")
    private Boolean limitClamped;

    public static SqlGuardResult reject(int errorCode, String errorMsg, String originalSql) {
        SqlGuardResult result = new SqlGuardResult();
        result.setPassed(false);
        result.setErrorCode(errorCode);
        result.setErrorMsg(errorMsg);
        result.setOriginalSql(originalSql);
        result.setLimitApplied(false);
        result.setLimitClamped(false);
        return result;
    }

    public SqlGuardResult check(String item) {
        this.checks.add(item);
        return this;
    }

    public SqlGuardResult warn(String item) {
        this.warnings.add(item);
        return this;
    }

}
