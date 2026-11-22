package cn.iocoder.boot.module.data_query.framework.sqlguard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * S6 校验通过后产出的「可执行 SQL」载体（SafeSql）。
 *
 * 只承载执行所需的最小信息，不含连接串、账号、密码，也不含任何业务数据行。
 * S7 只读执行器只接受本对象，杜绝「绕过校验直接执行模型原始文本」。
 */
@Data
@Schema(description = "S6 校验通过后的可执行 SQL（SafeSql）")
public class SafeSql {

    @Schema(description = "数据源编号")
    private Long datasourceId;

    @Schema(description = "抽取并规范化后的原始 SQL（模型/用户提交）")
    private String originalSql;

    @Schema(description = "经 AST 改写后最终下发的 SQL（强制 LIMIT / 上限收敛）")
    private String executableSql;

    @Schema(description = "executedSql 的 SHA-256，用于审计与缓存 key")
    private String sqlHash;

    @Schema(description = "本次返回行数上限")
    private Integer maxRows;

    @Schema(description = "本次查询超时（毫秒）")
    private Integer queryTimeoutMs;

    @Schema(description = "是否走独立只读通道；false 表示降级复用主连接（会告警）")
    private Boolean readonlyChannel;

    @Schema(description = "依据的数据源结构版本")
    private Integer structVersion;

    @Schema(description = "SQL 实际引用的物理表")
    private List<String> tables = new ArrayList<>();

    @Schema(description = "SQL 实际引用的列")
    private List<String> columns = new ArrayList<>();

    @Schema(description = "结果集需要脱敏的列名")
    private List<String> maskColumns = new ArrayList<>();

}
