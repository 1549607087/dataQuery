package cn.iocoder.boot.module.data_query.framework.sqlguard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * S6 的「唯一可执行表列来源」。
 *
 * 构造过程有三道约束，任何一道都不能被前端入参放宽：
 * 1. 元数据从 PG 现查（MP 租户拦截器 + 显式 tenant_id 比对 + datasource_id 归属），
 *    只看 visible=1 / queryable=1 / sync_status&lt;&gt;4 / deleted=0 的表列；
 * 2. 与 S3 召回结果 tableIds / columnIds **取交集**——前端即使伪造更大的 ID 集合，
 *    也只能收窄到「本租户该数据源本来就可见可查」的集合，无法凭空放行一张表；
 * 3. mask_rule=forbid 的列从允许集剔除并单列记录，SQL 引用时给出明确拒绝原因。
 *
 * 与 P3 的区别：P3 的 SchemaContext.tables 用于「给模型看」，本对象用于「给闸门判」，
 * 二者都不接受前端传的 schema 文本或表名，名字一律以 PG 元数据为准。
 */
@Data
public class SchemaWhitelist {

    private Long datasourceId;

    private Integer structVersion;

    /** 白名单内的表名（schema.table，用于回显与审计） */
    private Set<String> tableNames = new LinkedHashSet<>();

    /** 白名单内的列名（小写，用于回显与审计） */
    private Set<String> columnNames = new LinkedHashSet<>();

    /** mask_rule=forbid 的列名（小写） */
    private Set<String> forbiddenColumns = new LinkedHashSet<>();

    /** 需要脱敏的列名（小写） -> 规则文本 */
    private Map<String, String> maskColumns = new HashMap<>();

    /** 归一化表名 -> 元数据表编号；带 schema 与不带 schema 两个键都登记 */
    @JsonIgnore
    private Map<String, Long> tableLookup = new HashMap<>();

    /** 不带 schema 的表名 -> 是否重名；重名时 SQL 必须带 schema 限定 */
    @JsonIgnore
    private Set<String> ambiguousTableNames = new HashSet<>();

    /** 归一化列名 -> 是否允许 */
    @JsonIgnore
    private Set<String> columnLookup = new HashSet<>();

    /** SELECT 列表里的别名（ORDER BY 引用别名时不误杀），由校验器填充 */
    @JsonIgnore
    private Set<String> selectAliases = new HashSet<>();

    public boolean isEmpty() {
        return tableLookup.isEmpty() || columnLookup.isEmpty();
    }

    /** 归一化标识符：去引号、去方括号、转小写 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '`' && last == '`')
                    || (first == '[' && last == ']')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        // 处理带引号的多段标识符，例如 "data_query"."sales"
        StringBuilder sb = new StringBuilder();
        for (String part : value.split("\\.")) {
            String p = part.trim();
            if (p.length() >= 2) {
                char f = p.charAt(0);
                char l = p.charAt(p.length() - 1);
                if ((f == '"' && l == '"') || (f == '`' && l == '`') || (f == '[' && l == ']')) {
                    p = p.substring(1, p.length() - 1);
                }
            }
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(p);
        }
        return sb.toString().toLowerCase();
    }

    /** 从「召回 ID 集合」构造回显用的 ID 列表（不改变内部 lookup） */
    public static Set<Long> toIdSet(List<Long> ids) {
        Set<Long> set = new HashSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    set.add(id);
                }
            }
        }
        return set;
    }

}
