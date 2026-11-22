package cn.iocoder.boot.module.data_query.framework.recall;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * P3 Schema 召回可调参数。
 *
 * 全部为一期「精确扫描」口径：向量路与关键词路都带 tenant_id 硬过滤，
 * 两路候选按 RRF 融合后取 Top-K 表。参数只影响相关性与 Token 预算，
 * 不影响安全边界（tenant_id 条件、visible / queryable 过滤、PG 二次核验不可配置关闭）。
 */
@Component
@ConfigurationProperties(prefix = "data-query.recall")
@Data
public class SchemaRecallProperties {

    /** 最终返回的表数量上限（Top-K 表） */
    private Integer topKTables = 6;

    /**
     * 向量路：<b>表级与列级各自</b>取多少候选（不是两者相加的总数）。
     *
     * 必须是「每类」口径：全库列级 Document 数量是表级的 10~100 倍，若两类共用
     * 一个 LIMIT，一张宽表的列向量就能占满全部名额，导致 Top-K 表只聚合出 1 张。
     * SQL 侧用 ROW_NUMBER() OVER (PARTITION BY object_type) 分区截断，一次扫描完成。
     */
    private Integer vectorCandidates = 20;

    /** 关键词路：表级与列级各自取多少候选（口径同上） */
    private Integer keywordCandidates = 20;

    /** 每张表最多带多少列进 SchemaContext（同时也是 S6 白名单上限） */
    private Integer maxColumnsPerTable = 20;

    /** 从问题中提取的关键词数量上限（控制 ILIKE 分支数与 SQL 长度） */
    private Integer maxKeywords = 6;

    /**
     * 领域词表：问题命中这些词时优先作为检索关键词，避免被中文 2 字滑窗的碎片
     * （「务部」「部上」「个月」之类）挤掉真正有区分度的词（「迟到」「报销」）。
     *
     * <p>按「最长匹配优先」使用：命中长词后，其子串词不再重复提取。词表只影响
     * 关键词路的召回质量，不改变任何安全边界；可按数据源业务通过配置覆盖。
     */
    private java.util.List<String> domainTerms = new java.util.ArrayList<>(java.util.List.of(
            // 考勤 / 人事
            "迟到", "早退", "旷工", "请假", "加班", "出差", "打卡", "考勤", "调休", "年假",
            "病假", "事假", "外出", "员工", "部门", "岗位", "入职", "离职", "异动", "绩效",
            "工资", "薪资", "劳动合同",
            // 财务
            "报销", "借款", "付款", "预算", "发票", "费用", "金额", "合同", "对账", "结算",
            // 采购 / 资产
            "采购", "供应商", "验收", "订单", "资产", "盘点", "领用", "办公用品", "申领",
            // 行政 / 会议
            "会议", "会议室", "纪要", "参会", "用车", "车辆", "访客", "用印", "印章", "报修",
            "日程", "任务", "工时", "工作日志", "日报", "周报",
            // 项目 / 公文 / 流程
            "客户", "项目", "成员", "公告", "通知", "公文", "收文", "发文", "归档", "文档",
            "流程", "审批"));

    /** RRF 融合常数 K，score = Σ weight / (K + rank) */
    private Integer rrfK = 60;

    /** 向量路权重 */
    private Double vectorWeight = 1.0;

    /** 关键词路权重 */
    private Double keywordWeight = 1.0;

    /** 列级命中聚合到表时的折算系数（列不应盖过表级命中） */
    private Double columnToTableWeight = 0.45;

    /** 事实表（tableRole）加权系数：命中事实表时额外加成 */
    private Double factTableBoost = 0.15;

    /** Schema 文本 Token 硬预算，超出按表 / 列优先级裁剪并标记 truncated */
    private Integer schemaTokenBudget = 6000;

    /** 关键词兜底总开关：关闭后 Embedding 不可用时直接返回空召回（运维排障用） */
    private Boolean keywordFallbackEnabled = true;

    /**
     * 一跳关联扩展开关：候选表通过「已验证 + 已启用」的外键关系，把另一端维表拉进选集。
     * 典型场景：问「谁用过会议室」只命中预约表，需借 booking.user_id → oa_user.id
     * 把用户表带进来，模型才能 JOIN 取 oa_user.real_name 而不是只返回 user_id。
     * 关闭后恢复旧行为（只召回直接命中的表）。
     */
    private Boolean relationExpandEnabled = true;

    /** 一跳最多扩展多少张关联表（防止把整库维表拉进 Schema / 撑爆白名单） */
    private Integer maxExpandTables = 2;

}
