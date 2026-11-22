package cn.iocoder.boot.module.data_query.framework.chat;

import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P6 S8 结果加工：图表推荐 + 口径解释（纯规则，不调用模型、不发起第二次查询）。
 *
 * 为什么用规则而不是 CHART / EXPLAIN 模型：
 * 1. 确定性可断言（同一列类型组合必得同一结果），回归用例不会因模型波动而假失败；
 * 2. 零额外模型调用成本，单次问数模型调用次数不受影响（成功标准 ≤ 3 次）；
 * 3. 不依赖有效供应商凭证——当前租户真实 Key 返回 401 时增强项仍可用。
 *
 * 两项都是「可选增强」：任何异常都由编排器吞掉并降级为「仅表格 + SQL + 命中表列」，
 * 绝不阻断主结果，也不改写 QueryResult 里的任何数据。
 */
@Slf4j
@Component
public class ResultAdvisor {

    private static final int MAX_Y_FIELDS = 5;
    private static final List<String> RATIO_WORDS = List.of("占比", "比例", "构成", "分布", "百分比", "percent", "ratio");

    /**
     * 按列类型推荐图表。columnTypes 来自 ResultSetMetaData#getColumnTypeName，
     * PG 返回 varchar/numeric/int4/timestamp，MySQL 返回 VARCHAR/DECIMAL/INT/DATETIME，统一小写前缀匹配。
     */
    public ChartSpec recommend(QueryResult result, String question) {
        ChartSpec spec = new ChartSpec();
        spec.setType("none");
        spec.setRenderable(false);
        spec.setRowCount(result.getRowCount());
        spec.setAggregation("none");

        List<String> names = result.getColumnNames();
        List<String> types = result.getColumnTypes();
        int colCount = names == null ? 0 : names.size();
        int rows = result.getRowCount() == null ? 0 : result.getRowCount();
        if (colCount == 0 || rows == 0) {
            spec.setReason("结果为空或没有列，仅展示表格");
            return spec;
        }

        List<String> numeric = new ArrayList<>();
        List<String> temporal = new ArrayList<>();
        List<String> categorical = new ArrayList<>();
        for (int i = 0; i < colCount; i++) {
            String name = names.get(i);
            String type = types != null && i < types.size() ? types.get(i) : "";
            if (isNumeric(type)) {
                numeric.add(name);
            } else if (isTemporal(type)) {
                temporal.add(name);
            } else {
                categorical.add(name);
            }
        }

        if (numeric.isEmpty()) {
            spec.setType("table");
            spec.setRenderable(false);
            spec.setReason("结果集没有数值列，适合表格展示");
            return spec;
        }

        List<String> yFields = new ArrayList<>(numeric.subList(0, Math.min(MAX_Y_FIELDS, numeric.size())));
        spec.setYFields(yFields);
        spec.setRenderable(true);

        if (colCount == 1 && rows == 1) {
            spec.setType("metric");
            spec.setTitle(yFields.get(0) + " 指标卡");
            spec.setReason("单列单行数值，适合指标卡");
            return spec;
        }

        if (!temporal.isEmpty()) {
            spec.setType("line");
            spec.setXField(temporal.get(0));
            spec.setTitle(yFields.get(0) + " 随 " + temporal.get(0) + " 的趋势");
            spec.setReason("含时间列与数值列，推荐折线图展示趋势");
            return spec;
        }

        if (!categorical.isEmpty()) {
            String lower = question == null ? "" : question.toLowerCase(Locale.ROOT);
            boolean ratio = RATIO_WORDS.stream().anyMatch(lower::contains);
            if (ratio && rows <= 20 && yFields.size() == 1) {
                spec.setType("pie");
                spec.setXField(categorical.get(0));
                spec.setTitle(yFields.get(0) + " 按 " + categorical.get(0) + " 的占比");
                spec.setReason("问题含占比语义、类别数 ≤ 20 且单数值列，推荐饼图");
            } else {
                spec.setType("bar");
                spec.setXField(categorical.get(0));
                spec.setTitle(yFields.get(0) + " 按 " + categorical.get(0) + " 对比");
                spec.setReason("含类别列与数值列，推荐柱状图对比");
            }
            return spec;
        }

        // 全是数值列：用第一列作横轴
        spec.setType("bar");
        spec.setXField(names.get(0));
        spec.setTitle(String.join(" / ", yFields) + " 对比");
        spec.setReason("结果集全为数值列，以首列分组做柱状图");
        return spec;
    }

    /**
     * 结构化口径说明：只描述「查了什么 / 命中范围 / 实际 SQL / 指标口径 / 结果规模与脱敏」，
     * 不输出任何业务数据行，不编造数值结论。
     */
    public String explain(QueryResult result, SchemaContext schema, String question) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询目标：").append(clip(question, 120)).append('\n');

        List<String> tableNames = new ArrayList<>();
        if (schema != null && schema.getTables() != null) {
            for (SchemaContext.TableItem table : schema.getTables()) {
                if (tableNames.size() >= 3) {
                    break;
                }
                String label = StringUtils.hasText(table.getNameZh())
                        ? table.getNameZh() + "(" + table.getTableName() + ")"
                        : table.getTableName();
                tableNames.add(label);
            }
        }
        sb.append("命中范围：").append(tableNames.isEmpty() ? "（无）" : String.join("、", tableNames));
        if (schema != null && schema.getHitTableCount() != null && schema.getHitTableCount() > 3) {
            sb.append(" 等 ").append(schema.getHitTableCount()).append(" 张表");
        }
        sb.append('\n');

        sb.append("实际执行：").append(scrub(clip(result.getExecutedSql(), 300))).append('\n');
        if (schema != null && schema.getMetricDefs() != null && !schema.getMetricDefs().isEmpty()) {
            sb.append("指标口径：").append(clip(String.join("；", schema.getMetricDefs().subList(0,
                    Math.min(2, schema.getMetricDefs().size()))), 200)).append('\n');
        }
        int cols = result.getColumnNames() == null ? 0 : result.getColumnNames().size();
        sb.append("结果规模：").append(result.getRowCount() == null ? 0 : result.getRowCount())
                .append(" 行 / ").append(cols).append(" 列");
        if (Boolean.TRUE.equals(result.getTruncated())) {
            sb.append("，已按行数上限截断");
        }
        if (Boolean.TRUE.equals(result.getMasked())) {
            sb.append("，敏感字段已脱敏");
        }
        sb.append('\n');
        sb.append("说明：SQL 由服务端 S6 静态校验后经 S7 独立只读连接执行；本说明不含业务数据行。");
        return sb.toString();
    }

    private static boolean isNumeric(String type) {
        String v = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return v.startsWith("int") || v.startsWith("integer") || v.startsWith("bigint")
                || v.startsWith("smallint") || v.startsWith("tinyint") || v.startsWith("serial")
                || v.startsWith("numeric") || v.startsWith("decimal") || v.startsWith("number")
                || v.startsWith("float") || v.startsWith("double") || v.startsWith("real")
                || v.startsWith("money") || v.startsWith("long");
    }

    private static boolean isTemporal(String type) {
        String v = type == null ? "" : type.toLowerCase(Locale.ROOT);
        return v.startsWith("date") || v.startsWith("time") || v.startsWith("timestamp")
                || v.equals("year");
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "（无）";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    /** 不回显连接串与凭证（SQL 字面量里理论上不该出现，做纵深防御） */
    private static String scrub(String value) {
        if (value == null) {
            return "（无）";
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("jdbc:") || lower.contains("password") || lower.contains("api_key")) {
            return "（SQL 含敏感字面量，已隐藏）";
        }
        return value;
    }
}
