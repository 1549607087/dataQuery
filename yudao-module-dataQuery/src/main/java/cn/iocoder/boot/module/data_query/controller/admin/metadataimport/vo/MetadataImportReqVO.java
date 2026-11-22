package cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 管理后台 - 表结构一键导入 Request VO
 *
 * 只需一个表单一次提交，服务端自动完成「创建/复用数据源 → 测连接 → 全量表列导入 → 向量索引」。
 * 两种模式二选一：
 * 1) 已有数据源：只传 datasourceId；
 * 2) 新建数据源：最少只需 name / code / jdbcUrl（其余按 dsType 自动推断并给安全默认值）。
 *
 * 租户 ID 一律取服务端上下文，禁止前端传入；密码只在本次请求内使用，不回显、不写日志。
 */
@Schema(description = "管理后台 - 表结构一键导入 Request VO")
@Data
public class MetadataImportReqVO {

    // ==================== 模式选择 ====================

    @Schema(description = "已有数据源编号；传入即走「已有数据源」模式，忽略新建连接字段")
    private Long datasourceId;

    // ==================== 新建数据源（最少 3 个必填） ====================

    @Schema(description = "数据源显示名称（新建模式必填）", example = "本地业务库")
    private String name;

    @Schema(description = "数据源编码，租户内唯一（新建模式必填）", example = "biz_pg")
    private String code;

    @Schema(description = "数据库类型：1 MySQL、2 PostgreSQL、3 Oracle、4 SQLServer、5 达梦、6 人大金仓、7 openGauss、8 ClickHouse、9 Hive；默认 2", example = "2")
    private Integer dsType;

    @Schema(description = "管理连接 JDBC URL（新建模式必填）", example = "jdbc:postgresql://127.0.0.1:5432/postgres")
    private String jdbcUrl;

    @Schema(description = "管理连接账号；本地 trust 认证可留空")
    private String username;

    @Schema(description = "管理连接密码，加密存储且接口不回显；留空表示空密码")
    private String password;

    @Schema(description = "默认 schema / database；PostgreSQL 系默认 public，其余留空表示全库扫描（建议显式指定）")
    private String schemaName;

    @Schema(description = "默认 catalog；MySQL / SQLServer 等需要按库名填写")
    private String catalogName;

    @Schema(description = "JDBC 驱动类名；不填按 dsType 自动推断（仅允许驱动白名单内的值）")
    private String driverClass;

    @Schema(description = "SQL 方言；不填按 dsType 自动推断")
    private String dialect;

    @Schema(description = "只读查询连接 URL；不填表示暂不启用独立只读账号（生产上线前必须配置）")
    private String readonlyUrl;

    @Schema(description = "只读连接账号")
    private String readonlyUsername;

    @Schema(description = "只读连接密码")
    private String readonlyPassword;

    // ==================== 扫描范围（不填则由服务端按系统 schema 规则收窄） ====================

    @Schema(description = "schema 白名单，如 [\"public\"]；为空表示全部非系统 schema", example = "[\"public\"]")
    private List<String> allowedSchemas;

    @Schema(description = "表名包含正则数组，命中才导入", example = "[\"^p4reg_\"]")
    private List<String> includeTables;

    @Schema(description = "表名排除正则数组，命中即跳过")
    private List<String> excludeTables;

    // ==================== 导入选项 ====================

    @Schema(description = "单次查询最大返回行数，默认 1000")
    private Integer maxRows;

    @Schema(description = "只预览不落库：不创建数据源、不写任何元数据行；默认 false")
    private Boolean dryRun;

    @Schema(description = "导入完成后是否构建向量索引；默认 true。构建失败只告警不回滚")
    private Boolean buildVector;

    @Schema(description = "编码已存在且连接配置一致时复用该数据源（幂等）；默认 true。置 false 时重复编码直接报错")
    private Boolean reuseExisting;

}
