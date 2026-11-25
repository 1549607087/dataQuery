-- =============================================================================
-- dataquery-install.sql - dataQuery 全新库一键初始化
-- =============================================================================
-- 适用：全新 PostgreSQL 库；原 ruoyi-vue-pro.sql 已导入到同一 schema；quartz.sql 可省略。
-- 不要在有业务数据的库上直接执行：model/prompt/datasource 三段会 DROP 并重建表。
-- 若确需强制重建，请先执行： SET dataquery.allow_rebuild = on;
--
-- 推荐执行方式（psql）：
--   $env:PGOPTIONS='-c search_path=data_query'
--   psql -h <host> -U <user> -d <db> -v ON_ERROR_STOP=1 -f sql/postgresql/dataquery-install.sql
--
-- 前置最小表/序列：system_menu / system_role_menu / system_menu_seq / system_role_menu_seq /
--                   system_tenant / system_user / system_role / system_users
-- =============================================================================
CREATE SCHEMA IF NOT EXISTS data_query;
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA data_query;
SET search_path TO data_query, public;

DO $$
BEGIN
  IF to_regclass('system_menu') IS NULL OR to_regclass('system_role_menu') IS NULL
     OR to_regclass('system_menu_seq') IS NULL THEN
    RAISE EXCEPTION '缺少 ruoyi 基础表/序列：请先导入原 ruoyi-vue-pro.sql 到 data_query schema';
  END IF;
END
$$;

DO $$
DECLARE
  n bigint := 0;
BEGIN
  IF to_regclass('ai_model_provider') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM ai_model_provider' INTO n;
  END IF;
  IF n = 0 AND to_regclass('ai_prompt_scene') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM ai_prompt_scene' INTO n;
  END IF;
  IF n = 0 AND to_regclass('ai_datasource') IS NOT NULL THEN
    EXECUTE 'SELECT count(*) FROM ai_datasource' INTO n;
  END IF;
  IF n > 0 AND current_setting('dataquery.allow_rebuild', true) IS DISTINCT FROM 'on' THEN
    RAISE EXCEPTION '检测到已有 dataQuery 业务数据；如确需重建请 SET dataquery.allow_rebuild = on';
  END IF;
END
$$;

-- ============================================================
-- 01/32 nl2sql-model.sql
-- ============================================================
/*
 ai 平台 —— 管理端「模型配置」数据模型
 Target Server Type : PostgreSQL
 版本               : 2026-11-12

 约定（与 sql/postgresql/ruoyi-vue-pro.sql 保持一致）：
 1) 表前缀 ai_，对应新模块 yudao-module-ai；如需改名，全局替换前缀即可。
 2) 每张表含 BaseDO 五字段（creator/create_time/updater/update_time/deleted），
    并含 tenant_id 对应 TenantBaseDO（yudao.tenant.enable = true）。
 3) 不建物理外键，关联关系由业务层维护；状态/枚举用 int2，布尔开关也用 int2。
 4) 主键由各表的 xxx_seq 序列提供，DO 上需标注 @KeySequence("xxx_seq")。
 5) api_key / api_secret 存密文（AES，读 mybatis-plus.encryptor.password），接口层禁止回显；
    列允许为 NULL：框架的 EncryptTypeHandler 只对 null 短路，空串会进 AES 解密并抛异常，
    因此「未配置」一律用 NULL 表示，不用空串。初始化与租户模板复制都保持 NULL。

 ！！导入前务必确认 search_path 与 application-local.yaml 的 currentSchema 一致，
 否则会重演「dual 表在 public、应用查 dataQuery」的问题：
 PGOPTIONS='-c search_path=dataQuery' psql -h localhost -p 5432 -U postgres -d postgres -f sql/postgresql/ai-model.sql
*/


-- ----------------------------
-- 1. 模型供应商 / 接入凭证
-- ----------------------------
DROP TABLE IF EXISTS ai_model_provider;
CREATE TABLE ai_model_provider (
    id int8 NOT NULL,
  name varchar(100) NOT NULL DEFAULT '',
  code varchar(64) NOT NULL DEFAULT '',
  protocol varchar(32) NOT NULL DEFAULT 'OPENAI',
  base_url varchar(512) NOT NULL DEFAULT '',
  api_key varchar(1024) DEFAULT NULL,
  api_secret varchar(1024) DEFAULT NULL,
  headers text NULL,
  proxy_url varchar(255) NOT NULL DEFAULT '',
  daily_token_limit int8 NOT NULL DEFAULT 0,
  monthly_budget numeric(12,2) NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_model_provider ADD CONSTRAINT pk_ai_model_provider PRIMARY KEY (id);
CREATE INDEX idx_ai_model_provider_01 ON ai_model_provider (tenant_id, code);
CREATE INDEX idx_ai_model_provider_02 ON ai_model_provider (status);

COMMENT ON TABLE ai_model_provider IS '模型供应商（接入凭证）表';
COMMENT ON COLUMN ai_model_provider.id IS '主键';
COMMENT ON COLUMN ai_model_provider.name IS '供应商名称';
COMMENT ON COLUMN ai_model_provider.code IS '供应商编码：openai/qwen/deepseek/ollama/vllm/azure 等';
COMMENT ON COLUMN ai_model_provider.protocol IS '协议：OPENAI / OLLAMA / AZURE / CUSTOM';
COMMENT ON COLUMN ai_model_provider.base_url IS 'API 基础地址';
COMMENT ON COLUMN ai_model_provider.api_key IS 'API Key（加密存储，接口不回显）';
COMMENT ON COLUMN ai_model_provider.api_secret IS '备用凭证（Azure / 阿里云等需要）';
COMMENT ON COLUMN ai_model_provider.headers IS '自定义请求头，JSON 字符串';
COMMENT ON COLUMN ai_model_provider.proxy_url IS '代理地址';
COMMENT ON COLUMN ai_model_provider.daily_token_limit IS '每日 Token 上限，0 表示不限';
COMMENT ON COLUMN ai_model_provider.monthly_budget IS '月度预算（元），0 表示不限';
COMMENT ON COLUMN ai_model_provider.status IS '状态：0 启用、1 停用';
COMMENT ON COLUMN ai_model_provider.sort IS '排序';
COMMENT ON COLUMN ai_model_provider.remark IS '备注';
COMMENT ON COLUMN ai_model_provider.tenant_id IS '租户编号';

-- ----------------------------
-- 2. 模型配置主表
-- ----------------------------
DROP TABLE IF EXISTS ai_model_config;
CREATE TABLE ai_model_config (
    id int8 NOT NULL,
  provider_id int8 NOT NULL DEFAULT 0,
  name varchar(100) NOT NULL DEFAULT '',
  model_id varchar(128) NOT NULL DEFAULT '',
  model_type int2 NOT NULL DEFAULT 1,
  protocol varchar(32) NOT NULL DEFAULT 'OPENAI',
  capability text NULL,
  context_window int4 NOT NULL DEFAULT 0,
  max_output_tokens int4 NOT NULL DEFAULT 0,
  default_params text NULL,
  support_stream int2 NOT NULL DEFAULT 1,
  input_price numeric(12,4) NOT NULL DEFAULT 0,
  output_price numeric(12,4) NOT NULL DEFAULT 0,
  currency varchar(16) NOT NULL DEFAULT 'CNY',
  qps_limit int4 NOT NULL DEFAULT 0,
  concurrent_limit int4 NOT NULL DEFAULT 0,
  timeout_ms int4 NOT NULL DEFAULT 60000,
  retry_count int2 NOT NULL DEFAULT 2,
  status int2 NOT NULL DEFAULT 0,
  gray_ratio int2 NOT NULL DEFAULT 0,
  health_status int2 NOT NULL DEFAULT 0,
  last_check_time timestamp NULL,
  last_check_result varchar(1000) NULL DEFAULT '',
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_model_config ADD CONSTRAINT pk_ai_model_config PRIMARY KEY (id);
CREATE INDEX idx_ai_model_config_01 ON ai_model_config (tenant_id, provider_id);
CREATE INDEX idx_ai_model_config_02 ON ai_model_config (model_type, status);
CREATE INDEX idx_ai_model_config_03 ON ai_model_config (model_id);

COMMENT ON TABLE ai_model_config IS '模型配置表';
COMMENT ON COLUMN ai_model_config.id IS '主键';
COMMENT ON COLUMN ai_model_config.provider_id IS '关联 ai_model_provider.id';
COMMENT ON COLUMN ai_model_config.name IS '模型显示名称';
COMMENT ON COLUMN ai_model_config.model_id IS '调用时使用的模型标识，如 qwen-plus / gpt-4o / bge-m3';
COMMENT ON COLUMN ai_model_config.model_type IS '类型：1 Chat、2 Embedding、3 Rerank、4 SQL 专用';
COMMENT ON COLUMN ai_model_config.protocol IS '协议（模型级）：Chat/Embedding/SQL 固定 OPENAI；Rerank 可选 DASHSCOPE / COHERE';
COMMENT ON COLUMN ai_model_config.capability IS '能力标签 JSON：streaming / function_call / json_mode / vision';
COMMENT ON COLUMN ai_model_config.context_window IS '上下文窗口（token）';
COMMENT ON COLUMN ai_model_config.max_output_tokens IS '最大输出 token';
COMMENT ON COLUMN ai_model_config.default_params IS '默认参数 JSON：temperature / top_p / presence_penalty 等';
COMMENT ON COLUMN ai_model_config.support_stream IS '是否支持流式：0 否、1 是';
COMMENT ON COLUMN ai_model_config.input_price IS '输入单价（元 / 百万 token）';
COMMENT ON COLUMN ai_model_config.output_price IS '输出单价（元 / 百万 token）';
COMMENT ON COLUMN ai_model_config.currency IS '币种';
COMMENT ON COLUMN ai_model_config.qps_limit IS 'QPS 上限，0 表示不限';
COMMENT ON COLUMN ai_model_config.concurrent_limit IS '并发上限，0 表示不限';
COMMENT ON COLUMN ai_model_config.timeout_ms IS '单次调用超时（毫秒）';
COMMENT ON COLUMN ai_model_config.retry_count IS '失败重试次数';
COMMENT ON COLUMN ai_model_config.status IS '状态：0 启用、1 停用、2 灰度';
COMMENT ON COLUMN ai_model_config.gray_ratio IS '灰度流量比例 0-100';
COMMENT ON COLUMN ai_model_config.health_status IS '健康状态：0 未检测、1 健康、2 异常';
COMMENT ON COLUMN ai_model_config.last_check_time IS '最近一次连通性检测时间';
COMMENT ON COLUMN ai_model_config.last_check_result IS '最近一次检测结果';
COMMENT ON COLUMN ai_model_config.sort IS '排序';
COMMENT ON COLUMN ai_model_config.remark IS '备注';
COMMENT ON COLUMN ai_model_config.tenant_id IS '租户编号';

-- ----------------------------
-- 3. 场景绑定 / 路由与降级链
-- ----------------------------
DROP TABLE IF EXISTS ai_model_scene_binding;
CREATE TABLE ai_model_scene_binding (
    id int8 NOT NULL,
  scene_code varchar(64) NOT NULL DEFAULT '',
  scene_name varchar(100) NOT NULL DEFAULT '',
  primary_model_id int8 NOT NULL DEFAULT 0,
  fallback_model_ids varchar(500) NOT NULL DEFAULT '',
  route_strategy int2 NOT NULL DEFAULT 1,
  prompt_id int8 NOT NULL DEFAULT 0,
  temperature_override numeric(4,2) NULL,
  enabled int2 NOT NULL DEFAULT 1,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_model_scene_binding ADD CONSTRAINT pk_ai_model_scene_binding PRIMARY KEY (id);
CREATE INDEX idx_ai_model_scene_binding_01 ON ai_model_scene_binding (tenant_id, scene_code, enabled);

COMMENT ON TABLE ai_model_scene_binding IS '模型场景绑定（路由与降级）表';
COMMENT ON COLUMN ai_model_scene_binding.id IS '主键';
COMMENT ON COLUMN ai_model_scene_binding.scene_code IS '场景编码：INTENT / REWRITE / SCHEMA_RECALL / SQL_GEN / SQL_FIX / EXPLAIN / RESULT_SUMMARY / REPORT / CHART / RECOMMEND / RAG / EMBEDDING / RERANK / PREDICT';
COMMENT ON COLUMN ai_model_scene_binding.scene_name IS '场景名称';
COMMENT ON COLUMN ai_model_scene_binding.primary_model_id IS '主模型，关联 ai_model_config.id';
COMMENT ON COLUMN ai_model_scene_binding.fallback_model_ids IS '降级链，模型 ID 逗号分隔，按顺序尝试';
COMMENT ON COLUMN ai_model_scene_binding.route_strategy IS '路由策略：1 固定、2 优先级、3 权重、4 成本优先';
COMMENT ON COLUMN ai_model_scene_binding.prompt_id IS '关联提示词模板（逻辑关联，M2 模块）';
COMMENT ON COLUMN ai_model_scene_binding.temperature_override IS '场景级温度覆盖，为空则用模型默认值';
COMMENT ON COLUMN ai_model_scene_binding.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN ai_model_scene_binding.sort IS '排序';
COMMENT ON COLUMN ai_model_scene_binding.remark IS '备注';
COMMENT ON COLUMN ai_model_scene_binding.tenant_id IS '租户编号';

-- ----------------------------
-- 4. 模型调用日志 / 用量与成本（配套表，P1）
-- ----------------------------
DROP TABLE IF EXISTS ai_model_call_log;
CREATE TABLE ai_model_call_log (
    id int8 NOT NULL,
  trace_id varchar(64) NOT NULL DEFAULT '',
  conversation_id int8 NOT NULL DEFAULT 0,
  scene_code varchar(64) NOT NULL DEFAULT '',
  provider_id int8 NOT NULL DEFAULT 0,
  model_config_id int8 NOT NULL DEFAULT 0,
  model_id varchar(128) NOT NULL DEFAULT '',
  request_tokens int4 NOT NULL DEFAULT 0,
  response_tokens int4 NOT NULL DEFAULT 0,
  total_tokens int4 NOT NULL DEFAULT 0,
  cost numeric(12,6) NOT NULL DEFAULT 0,
  latency_ms int4 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  fallback_from_model_id int8 NOT NULL DEFAULT 0,
  error_code varchar(64) NOT NULL DEFAULT '',
  error_msg varchar(1000) NOT NULL DEFAULT '',
  user_id int8 NOT NULL DEFAULT 0,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_model_call_log ADD CONSTRAINT pk_ai_model_call_log PRIMARY KEY (id);
CREATE INDEX idx_ai_model_call_log_01 ON ai_model_call_log (trace_id);
CREATE INDEX idx_ai_model_call_log_02 ON ai_model_call_log (tenant_id, create_time);
CREATE INDEX idx_ai_model_call_log_03 ON ai_model_call_log (model_config_id, create_time);

COMMENT ON TABLE ai_model_call_log IS '模型调用日志（用量与成本）表';
COMMENT ON COLUMN ai_model_call_log.trace_id IS '全链路追踪 ID';
COMMENT ON COLUMN ai_model_call_log.conversation_id IS '会话 ID';
COMMENT ON COLUMN ai_model_call_log.scene_code IS '场景编码';
COMMENT ON COLUMN ai_model_call_log.model_id IS '模型标识快照，避免配置变更影响历史';
COMMENT ON COLUMN ai_model_call_log.cost IS '本次调用费用';
COMMENT ON COLUMN ai_model_call_log.status IS '0 成功、1 失败、2 超时、3 降级';
COMMENT ON COLUMN ai_model_call_log.fallback_from_model_id IS '降级前的模型 ID';

-- ----------------------------
-- 初始化数据：无（2026-11-26 移除 tenant_id = 0 的平台模板）
--
-- ！！此前本段预置 1 个 provider + 3 个 config + 3 个 scene_binding（tenant_id = 0），
--    由 AiTenantInitializer 在租户开通时复制成私有配置。该机制已废弃：
--    ① 平台租户 0 会在管理端模型列表里看到不属于任何业务租户的模板数据；
--    ② 模板与真实租户配置共用同一套表，易被误当成可用配置。
--    现在供应商 / 模型配置 / 场景绑定一律由租户管理员在管理端自行创建，
--    AiTenantInitializer.initDefaultConfig 保留为空操作以兼容旧调用方。



-- ============================================================
-- 02/32 nl2sql-model-governance.sql
-- ============================================================
/*
 ai 平台 —— M1 模型管理「路由 / 降级 / 熔断 / 灰度 / 限流」补字段 DDL
 Target Server Type : PostgreSQL
 版本               : 2026-11-13

 【依赖】必须先已导入 sql/postgresql/nl2sql-model.sql（4 张 ai_model_* 表）。
        本脚本不建那 4 张表，只在其上补列；表不存在会直接报错，属预期行为。

 【本脚本解决的问题】上一轮静态核对发现以下配置「只有字段、没有运行时消费方」，
  且部分关键语义在数据模型里根本无法表达：
  1) route_strategy = 3 权重 / 4 成本优先，需要候选模型列表 + 权重 / 优先级，
     而 ai_model_scene_binding 只有 primary_model_id 与 fallback_model_ids 一条链。
  2) 熔断需要「失败计数窗口 / 状态 / 打开时间 / 探测数」，现有 health_status
     只是分钟级主动检测的静态快照，替代不了基于真实调用失败率的熔断器。
  3) gray_ratio 只标了比例，没有定义对照组是谁；call_log 也没有 is_gray 标记，
     算不出 A/B 对照指标；模型与提示词双灰度同时开会污染数据。
  4) 交互式问答需要按场景覆盖超时（60s 默认只适合离线 REPORT），
     现在 timeout_ms 只在模型级，无法按 SQL_GEN / REPORT 分别设值。
  5) 重试的成本与延迟必须按 trace 聚合，且要区分错误分类
     （401/400/内容审核拒绝绝不重试），现有日志只有 error_code 字符串。
  6) 降级链若放同供应商的另一个模型等于没降级，需要可校验的供应商维度。

 【本脚本内容】
  A. 补列：ai_model_config(+11) / ai_model_scene_binding(+9) / ai_model_call_log(+13)
  B. 新表：ai_model_route_candidate（路由候选集）
           ai_model_circuit_state（熔断状态持久化快照）
           ai_model_gray_experiment（灰度实验定义与对照组）
  C. 补索引 4 个、初始化候选数据 2 行、注释形式的自检与运行时推荐查询

 【幂等性与安全】
  ！！本脚本不含任何 DROP TABLE / TRUNCATE / UPDATE 已有业务数据，可重复执行：
     - 补列一律 ALTER TABLE ... ADD COLUMN IF NOT EXISTS，且全部带 DEFAULT，
       已有行会被 PG 回填中性默认值，不改变任何现有行为。
     - 新表一律 CREATE TABLE IF NOT EXISTS，索引用 CREATE ... INDEX IF NOT EXISTS。
     - COMMENT ON COLUMN 重复执行只是覆盖注释，无副作用。
  ！！第 9 节「可选的配置修正」默认全部注释掉，因为会改变运行行为，需人工确认后单独执行。
  ！！生产执行前请先备份；先在测试库跑一遍第 10 节自检 SQL。
  ！！大表锁风险（ai_model_call_log 是日志表，可能已有大量数据）：
     - ADD COLUMN ... NOT NULL DEFAULT 在 PG 11+ 是 fast default，不重写表；
       PG 10 及以下会重写整表并持有 ACCESS EXCLUSIVE 锁，须在维护窗口执行。
     - 第 C 节 4 个 CREATE INDEX 在事务外默认持 SHARE 锁阻塞写入，
       生产大表建议改为在事务外单独执行 `CREATE INDEX CONCURRENTLY IF NOT EXISTS ...`。
     - 本脚本的 BEGIN/COMMIT 只包住第 D 节初始化 INSERT，DDL 均为 autocommit，
       所以拆开逐段执行是安全的。
  ！！语法要求 PostgreSQL >= 9.6（ADD COLUMN IF NOT EXISTS）；部分唯一索引要求 >= 9.5。

 【约定】沿用 nl2sql-model.sql / nl2sql-datasource.sql：
  int8 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不建数据库序列；
  BaseDO 五字段 + tenant_id int8 DEFAULT 0；逻辑外键不建物理约束；
  状态 / 枚举 / 布尔开关统一 int2；唯一约束用部分索引 WHERE deleted = 0
  （迁 MySQL 时去掉 WHERE，改业务层校验）；「场景级覆盖」用 NULL 表示继承模型级，
  与既有 temperature_override numeric(4,2) NULL 保持一致。

 【租户坑（未解决，勿忘）】TenantDatabaseInterceptor 默认对所有表追加
  tenant_id = 当前租户（通常 1），tenant_id = 0 的初始化数据查不出来
  （此前「模型分页 SQL 有 1 条、接口返回空」即此原因）。本脚本沿用 tenant_id=0，
  修复方式二选一：平台级共享 -> 表加入 yudao.tenant.ignore-tables 或 DO 加
  @TenantIgnore 并由业务层显式 tenant_id IN (当前租户, 0)；严格隔离 ->
  创建时写入当前租户 ID 并刷新历史数据。本脚本不替你做这个选择。

 ！！导入时 search_path 必须与 application-local.yaml:52 的 currentSchema 一致，
    否则对象落到 public，应用看不到（即此前 dual 同类问题）：
 PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres -f sql/postgresql/nl2sql-model-governance.sql
*/


-- ----------------------------
-- 0. 执行前预检（应先返回 4 行，且 table_count 与本脚本要补的表一致）
-- ----------------------------
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = current_schema()
--    AND table_name IN ('ai_model_config','ai_model_scene_binding','ai_model_call_log','ai_model_provider')
--  ORDER BY table_name;


-- ============================================================
-- A-1. ai_model_config：熔断策略参数 + 可重试错误分类 + 退避参数
--      说明：这些是「策略配置」，不是运行态计数；运行态见 B-2 ai_model_circuit_state。
--      默认值全部取保守值（circuit_enabled=0 默认不启用），保证升级后行为不变。
-- ============================================================
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_enabled int2 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_failure_rate int2 NOT NULL DEFAULT 50;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_min_sample int4 NOT NULL DEFAULT 20;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_window_seconds int4 NOT NULL DEFAULT 60;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_open_seconds int4 NOT NULL DEFAULT 30;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_half_open_probes int4 NOT NULL DEFAULT 3;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS retryable_errors varchar(500) NOT NULL DEFAULT '429,5xx,TIMEOUT,CONNECT';
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS retry_base_backoff_ms int4 NOT NULL DEFAULT 200;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS retry_max_backoff_ms int4 NOT NULL DEFAULT 2000;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS retry_jitter_ratio numeric(4,2) NOT NULL DEFAULT 0.30;
ALTER TABLE ai_model_config ADD COLUMN IF NOT EXISTS circuit_key varchar(128) NOT NULL DEFAULT '';

COMMENT ON COLUMN ai_model_config.circuit_enabled IS '是否启用熔断：0 否、1 是；默认关闭，避免升级后误伤';
COMMENT ON COLUMN ai_model_config.circuit_failure_rate IS '熔断失败率阈值（百分比），窗口内失败率超过该值且样本数足够才打开';
COMMENT ON COLUMN ai_model_config.circuit_min_sample IS '窗口内最小请求数，不足则不熔断，防止小样本误判';
COMMENT ON COLUMN ai_model_config.circuit_window_seconds IS '熔断统计窗口长度（秒），建议 30~120';
COMMENT ON COLUMN ai_model_config.circuit_open_seconds IS 'OPEN 状态持续时长（秒），到期自动进入 HALF_OPEN';
COMMENT ON COLUMN ai_model_config.circuit_half_open_probes IS 'HALF_OPEN 允许的探测请求数，成功达标才回到 CLOSED';
COMMENT ON COLUMN ai_model_config.retryable_errors IS '可重试错误分类白名单，逗号分隔；401 / 400 / CONTENT_FILTER / INVALID_OUTPUT 绝不重试';
COMMENT ON COLUMN ai_model_config.retry_base_backoff_ms IS '指数退避基数（毫秒），200 -> 400 -> 800，配合抖动避免同步风暴';
COMMENT ON COLUMN ai_model_config.retry_max_backoff_ms IS '单次退避上限（毫秒）';
COMMENT ON COLUMN ai_model_config.retry_jitter_ratio IS '退避抖动系数 0.00-1.00，建议 0.30';
COMMENT ON COLUMN ai_model_config.circuit_key IS '熔断维度键，默认 provider_id:model_config_id；多实例共享 Redis 状态用同一 key';


-- ============================================================
-- A-2. ai_model_scene_binding：场景级覆盖 + 降级约束 + 灰度分桶
--      NULL 表示「继承模型级配置」，与既有 temperature_override 语义一致。
-- ============================================================
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS timeout_ms_override int4 NULL;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS qps_limit_override int4 NULL;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS concurrent_limit_override int4 NULL;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS retry_count_override int2 NULL;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS fallback_cross_provider int2 NOT NULL DEFAULT 1;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS fallback_max_depth int2 NOT NULL DEFAULT 2;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS gray_ratio int2 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS gray_bucket_key varchar(32) NOT NULL DEFAULT 'TENANT';
ALTER TABLE ai_model_scene_binding ADD COLUMN IF NOT EXISTS gray_experiment_id int8 NOT NULL DEFAULT 0;

COMMENT ON COLUMN ai_model_scene_binding.timeout_ms_override IS '场景级超时覆盖（毫秒），NULL 用模型默认；交互式 SQL_GEN 建议 4000~8000，离线 REPORT 可 60000';
COMMENT ON COLUMN ai_model_scene_binding.qps_limit_override IS '场景级 QPS 覆盖，NULL 用模型默认，0 表示不限';
COMMENT ON COLUMN ai_model_scene_binding.concurrent_limit_override IS '场景级并发上限覆盖，NULL 用模型默认，0 表示不限';
COMMENT ON COLUMN ai_model_scene_binding.retry_count_override IS '场景级重试次数覆盖，NULL 用模型默认';
COMMENT ON COLUMN ai_model_scene_binding.fallback_cross_provider IS '降级链是否强制跨供应商：0 否、1 是；同供应商双模型等于没降级';
COMMENT ON COLUMN ai_model_scene_binding.fallback_max_depth IS '单次请求最多降级跳数，防止降级链过长把延迟和成本放大';
COMMENT ON COLUMN ai_model_scene_binding.gray_ratio IS '场景级灰度比例 0-100，优先于 ai_model_config.gray_ratio；一次只灰度一个变量';
COMMENT ON COLUMN ai_model_scene_binding.gray_bucket_key IS '灰度稳定分桶维度：TENANT / USER / SESSION；必须稳定哈希，否则同一用户两次命中不同模型，A/B 无法归因';
COMMENT ON COLUMN ai_model_scene_binding.gray_experiment_id IS '当前生效的灰度实验，关联 ai_model_gray_experiment.id，0 表示无实验';


-- ============================================================
-- A-3. ai_model_call_log：灰度 / 重试 / 熔断 / 路由归因埋点
--      全部带默认值，历史日志回填中性值，A/B 统计时按 gray_experiment_id > 0 过滤即可隔离旧数据。
-- ============================================================
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS is_gray int2 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS gray_group varchar(16) NOT NULL DEFAULT '';
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS gray_experiment_id int8 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS gray_bucket_key varchar(32) NOT NULL DEFAULT '';
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS retry_of_trace_id varchar(64) NOT NULL DEFAULT '';
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS retry_index int2 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS circuit_state varchar(16) NOT NULL DEFAULT 'CLOSED';
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS circuit_key varchar(128) NOT NULL DEFAULT '';
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS error_category varchar(32) NOT NULL DEFAULT '';
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS fallback_depth int2 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS route_strategy int2 NOT NULL DEFAULT 1;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS queue_wait_ms int4 NOT NULL DEFAULT 0;
ALTER TABLE ai_model_call_log ADD COLUMN IF NOT EXISTS request_id varchar(128) NOT NULL DEFAULT '';

COMMENT ON COLUMN ai_model_call_log.is_gray IS '是否命中灰度流量：0 否、1 是';
COMMENT ON COLUMN ai_model_call_log.gray_group IS '灰度分组：A 对照组、B 灰度组；未参与实验留空';
COMMENT ON COLUMN ai_model_call_log.gray_experiment_id IS '关联 ai_model_gray_experiment.id，0 表示非实验流量';
COMMENT ON COLUMN ai_model_call_log.gray_bucket_key IS '本次实际使用的分桶维度快照，便于复盘归因';
COMMENT ON COLUMN ai_model_call_log.retry_of_trace_id IS '重试 / 降级调用指向的原始 trace_id，首次调用留空；成本按 COALESCE(NULLIF(retry_of_trace_id,空串), trace_id) 聚合';
COMMENT ON COLUMN ai_model_call_log.retry_index IS '第几次重试，0 = 首次调用';
COMMENT ON COLUMN ai_model_call_log.circuit_state IS '本次调用发生时的熔断状态：CLOSED / OPEN / HALF_OPEN；OPEN 且被快速失败时不产生供应商调用';
COMMENT ON COLUMN ai_model_call_log.circuit_key IS '熔断维度键快照 provider_id:model_config_id';
COMMENT ON COLUMN ai_model_call_log.error_category IS '失败细分类：RATE_LIMIT / SERVER_ERROR / TIMEOUT / CONNECT / AUTH / BAD_REQUEST / CONTENT_FILTER / INVALID_OUTPUT / CIRCUIT_OPEN；决定是否重试、是否计入熔断';
COMMENT ON COLUMN ai_model_call_log.fallback_depth IS '降级链第几跳，0 = 未降级';
COMMENT ON COLUMN ai_model_call_log.route_strategy IS '本次实际路由策略快照：1 固定、2 优先级、3 权重、4 成本优先';
COMMENT ON COLUMN ai_model_call_log.queue_wait_ms IS '进入模型客户端前的排队等待时长（毫秒），用于验证限流是否生效';
COMMENT ON COLUMN ai_model_call_log.request_id IS '供应商侧请求 ID，用于对账与排查「首次已成功但响应丢失」导致的重复扣费';

-- 与既有 status 的关系：status = 3 降级成功时，error_category 仍记录上游失败原因；
-- circuit_state = OPEN 且被快速失败时，status 记 1、error_category 记 CIRCUIT_OPEN、cost 记 0。


-- ============================================================
-- B-1. 路由候选集（补全 route_strategy 3 权重 / 4 成本优先所需的数据）
--      与旧字段 fallback_model_ids 的兼容策略：
--        - 运行时优先读本表 binding_id + enabled = 1 的行；查不到再回落解析 fallback_model_ids。
--        - 策略 2 优先级按 priority 升序；策略 3 权重按 weight 归一化；
--          策略 4 成本优先在本表候选集内 JOIN ai_model_config，按 (input_price + output_price) 升序。
--        - provider_id 冗余存快照，供 fallback_cross_provider = 1 时校验降级链必须跨供应商。
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_model_route_candidate (
    id int8 NOT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  binding_id int8 NOT NULL DEFAULT 0,
  model_id int8 NOT NULL DEFAULT 0,
  provider_id int8 NOT NULL DEFAULT 0,
  weight int4 NOT NULL DEFAULT 0,
  priority int4 NOT NULL DEFAULT 0,
  max_qps int4 NOT NULL DEFAULT 0,
  enabled int2 NOT NULL DEFAULT 1,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_model_route_candidate PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_route_candidate_01 ON ai_model_route_candidate (tenant_id, binding_id, model_id) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_model_route_candidate_01 ON ai_model_route_candidate (tenant_id, binding_id, enabled, priority);

COMMENT ON TABLE ai_model_route_candidate IS '模型路由候选集（权重 / 优先级 / 成本优先）表';
COMMENT ON COLUMN ai_model_route_candidate.id IS '主键（雪花 ID）';
COMMENT ON COLUMN ai_model_route_candidate.tenant_id IS '租户编号';
COMMENT ON COLUMN ai_model_route_candidate.binding_id IS '关联 ai_model_scene_binding.id';
COMMENT ON COLUMN ai_model_route_candidate.model_id IS '候选模型，关联 ai_model_config.id';
COMMENT ON COLUMN ai_model_route_candidate.provider_id IS '供应商 ID 快照，关联 ai_model_provider.id，用于跨供应商降级校验';
COMMENT ON COLUMN ai_model_route_candidate.weight IS '权重（策略 3 用），相对值，运行时按候选集归一化';
COMMENT ON COLUMN ai_model_route_candidate.priority IS '优先级（策略 2 用），数值越小越优先；也作为降级链顺序';
COMMENT ON COLUMN ai_model_route_candidate.max_qps IS '该候选单独可承载的 QPS 上限，0 表示不限';
COMMENT ON COLUMN ai_model_route_candidate.enabled IS '是否参与路由：0 否、1 是';
COMMENT ON COLUMN ai_model_route_candidate.sort IS '排序';
COMMENT ON COLUMN ai_model_route_candidate.remark IS '备注';


-- ============================================================
-- B-2. 熔断状态持久化快照
--      ！！架构约束：高频状态必须以 Redis 为准（多实例共享、原子自增），
--         本表只做「审计 / 管理端展示 / 重启恢复」的快照，禁止每次调用都 UPDATE，
--         否则同一 circuit_key 会形成热点行，把数据库打爆。
--         建议每次状态跃迁（CLOSED->OPEN、OPEN->HALF_OPEN、HALF_OPEN->CLOSED）时写一次，
--         或由定时任务每 N 秒批量同步。version 用于乐观锁防止并发覆盖。
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_model_circuit_state (
    id int8 NOT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  model_config_id int8 NOT NULL DEFAULT 0,
  circuit_key varchar(128) NOT NULL DEFAULT '',
  state varchar(16) NOT NULL DEFAULT 'CLOSED',
  window_start_time timestamp NULL,
  window_total int4 NOT NULL DEFAULT 0,
  window_failed int4 NOT NULL DEFAULT 0,
  window_succeeded int4 NOT NULL DEFAULT 0,
  failure_rate numeric(5,2) NOT NULL DEFAULT 0,
  consecutive_failures int4 NOT NULL DEFAULT 0,
  consecutive_successes int4 NOT NULL DEFAULT 0,
  last_failure_time timestamp NULL,
  last_failure_category varchar(32) NOT NULL DEFAULT '',
  opened_time timestamp NULL,
  open_until timestamp NULL,
  half_open_probes int4 NOT NULL DEFAULT 0,
  half_open_succeeded int4 NOT NULL DEFAULT 0,
  version int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_model_circuit_state PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_model_circuit_state_01 ON ai_model_circuit_state (tenant_id, circuit_key) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_model_circuit_state_01 ON ai_model_circuit_state (tenant_id, state, open_until);

COMMENT ON TABLE ai_model_circuit_state IS '模型熔断状态持久化快照表（运行时以 Redis 为准，本表不承载高频写入）';
COMMENT ON COLUMN ai_model_circuit_state.id IS '主键（雪花 ID）';
COMMENT ON COLUMN ai_model_circuit_state.tenant_id IS '租户编号';
COMMENT ON COLUMN ai_model_circuit_state.model_config_id IS '关联 ai_model_config.id';
COMMENT ON COLUMN ai_model_circuit_state.circuit_key IS '熔断维度键 provider_id:model_config_id，与 Redis key 一致';
COMMENT ON COLUMN ai_model_circuit_state.state IS '熔断状态：CLOSED 闭合、OPEN 打开（快速失败）、HALF_OPEN 半开（探测）';
COMMENT ON COLUMN ai_model_circuit_state.window_start_time IS '当前统计窗口起始时间';
COMMENT ON COLUMN ai_model_circuit_state.window_total IS '窗口内总请求数（含成功与失败）';
COMMENT ON COLUMN ai_model_circuit_state.window_failed IS '窗口内失败数（仅计入可归责于下游的失败）';
COMMENT ON COLUMN ai_model_circuit_state.window_succeeded IS '窗口内成功数';
COMMENT ON COLUMN ai_model_circuit_state.failure_rate IS '窗口失败率（百分比）';
COMMENT ON COLUMN ai_model_circuit_state.consecutive_failures IS '连续失败次数';
COMMENT ON COLUMN ai_model_circuit_state.consecutive_successes IS '连续成功次数（HALF_OPEN 判定用）';
COMMENT ON COLUMN ai_model_circuit_state.last_failure_time IS '最近一次失败时间';
COMMENT ON COLUMN ai_model_circuit_state.last_failure_category IS '最近一次失败分类';
COMMENT ON COLUMN ai_model_circuit_state.opened_time IS '进入 OPEN 的时间';
COMMENT ON COLUMN ai_model_circuit_state.open_until IS 'OPEN 到期时间，到期转 HALF_OPEN';
COMMENT ON COLUMN ai_model_circuit_state.half_open_probes IS 'HALF_OPEN 已发出的探测请求数';
COMMENT ON COLUMN ai_model_circuit_state.half_open_succeeded IS 'HALF_OPEN 探测成功数';
COMMENT ON COLUMN ai_model_circuit_state.version IS '乐观锁版本号，防止多实例并发写覆盖';
COMMENT ON COLUMN ai_model_circuit_state.remark IS '备注';


-- ============================================================
-- B-3. 灰度实验定义（对照组 + 观察指标 + 观察窗口）
--      解决「gray_ratio 没定义对照组是谁」「模型与提示词双灰度互相污染」：
--      gray_object 一次只能是 MODEL 或 PROMPT，不允许同一实验同时改两个变量。
--      min_sample_size 用于防止 5% 流量 × 少量样本就下结论。
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_model_gray_experiment (
    id int8 NOT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  name varchar(100) NOT NULL DEFAULT '',
  gray_object varchar(16) NOT NULL DEFAULT 'MODEL',
  scene_code varchar(64) NOT NULL DEFAULT '',
  binding_id int8 NOT NULL DEFAULT 0,
  baseline_model_id int8 NOT NULL DEFAULT 0,
  gray_model_id int8 NOT NULL DEFAULT 0,
  baseline_prompt_id int8 NOT NULL DEFAULT 0,
  gray_prompt_id int8 NOT NULL DEFAULT 0,
  gray_ratio int2 NOT NULL DEFAULT 5,
  bucket_key varchar(32) NOT NULL DEFAULT 'TENANT',
  start_time timestamp NULL,
  end_time timestamp NULL,
  min_sample_size int4 NOT NULL DEFAULT 1000,
  metrics text NULL,
  status int2 NOT NULL DEFAULT 0,
  conclusion varchar(1000) NOT NULL DEFAULT '',
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_model_gray_experiment PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_ai_model_gray_experiment_01 ON ai_model_gray_experiment (tenant_id, status, scene_code);

COMMENT ON TABLE ai_model_gray_experiment IS '模型 / 提示词灰度实验表';
COMMENT ON COLUMN ai_model_gray_experiment.id IS '主键（雪花 ID）';
COMMENT ON COLUMN ai_model_gray_experiment.tenant_id IS '租户编号';
COMMENT ON COLUMN ai_model_gray_experiment.name IS '实验名称';
COMMENT ON COLUMN ai_model_gray_experiment.gray_object IS '灰度变量：MODEL / PROMPT；一次实验只允许一个变量，避免归因污染';
COMMENT ON COLUMN ai_model_gray_experiment.scene_code IS '实验生效场景，关联 ai_model_scene_binding.scene_code';
COMMENT ON COLUMN ai_model_gray_experiment.binding_id IS '关联 ai_model_scene_binding.id';
COMMENT ON COLUMN ai_model_gray_experiment.baseline_model_id IS '对照组 A 模型 ID，关联 ai_model_config.id';
COMMENT ON COLUMN ai_model_gray_experiment.gray_model_id IS '灰度组 B 模型 ID，关联 ai_model_config.id';
COMMENT ON COLUMN ai_model_gray_experiment.baseline_prompt_id IS '对照组 A 提示词 ID（gray_object=PROMPT 时用）';
COMMENT ON COLUMN ai_model_gray_experiment.gray_prompt_id IS '灰度组 B 提示词 ID（gray_object=PROMPT 时用）';
COMMENT ON COLUMN ai_model_gray_experiment.gray_ratio IS '灰度组流量比例 0-100，建议从 5 起步';
COMMENT ON COLUMN ai_model_gray_experiment.bucket_key IS '稳定分桶维度：TENANT / USER / SESSION';
COMMENT ON COLUMN ai_model_gray_experiment.start_time IS '实验开始时间';
COMMENT ON COLUMN ai_model_gray_experiment.end_time IS '实验结束时间';
COMMENT ON COLUMN ai_model_gray_experiment.min_sample_size IS '每组最小样本量，未达标不允许下结论；5% × 1000 问答/天 = 50 样本，判断不了 1% 级准确率差异';
COMMENT ON COLUMN ai_model_gray_experiment.metrics IS '观察指标 JSON：sql_executable_rate / result_accuracy / p95_latency_ms / cost_per_query / fallback_rate';
COMMENT ON COLUMN ai_model_gray_experiment.status IS '状态：0 草稿、1 进行中、2 已结束、3 已全量、4 已回滚';
COMMENT ON COLUMN ai_model_gray_experiment.conclusion IS '实验结论';
COMMENT ON COLUMN ai_model_gray_experiment.sort IS '排序';
COMMENT ON COLUMN ai_model_gray_experiment.remark IS '备注';


-- ============================================================
-- C. 补充索引（支撑 A/B 统计、重试聚合、失败率窗口、熔断探活扫描）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_ai_model_call_log_04 ON ai_model_call_log (tenant_id, retry_of_trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_call_log_05 ON ai_model_call_log (tenant_id, gray_experiment_id, is_gray, gray_group);
CREATE INDEX IF NOT EXISTS idx_ai_model_call_log_06 ON ai_model_call_log (tenant_id, error_category, create_time);
CREATE INDEX IF NOT EXISTS idx_ai_model_call_log_07 ON ai_model_call_log (tenant_id, circuit_key, create_time);


-- ============================================================
-- D. 初始化数据：无（2026-11-26 移除 tenant_id = 0 的平台模板）
--    此前预置的 2 条 route_candidate 引用 provider 1 / model 1,2 / binding 1，
--    均属已废弃的 tenant_id = 0 平台模板，保留会造成悬空引用。
--    熔断状态表与灰度实验表同样不预置数据：由运行时 / 管理端按需创建。
--    路由候选一律由租户管理员在管理端录入，跨供应商候选也需人工确认。
-- ============================================================


-- ============================================================
-- 9. 可选的配置修正（默认全部注释，需人工确认业务语义后单独执行）
--    交互式问答端到端给 15~20s，一次问答 4~6 次 LLM 调用，
--    SQL_GEN 单次超时建议 4000~8000ms，而不是模型级默认 60000ms。
-- ============================================================
-- ALTER TABLE ai_model_config ALTER COLUMN timeout_ms SET DEFAULT 8000;
-- UPDATE ai_model_scene_binding SET timeout_ms_override = 8000  WHERE scene_code IN ('INTENT','REWRITE','SCHEMA_RECALL','SQL_GEN','SQL_FIX','EXPLAIN','RESULT_SUMMARY','CHART','RECOMMEND','RAG','PREDICT');
-- UPDATE ai_model_scene_binding SET timeout_ms_override = 60000 WHERE scene_code = 'REPORT';
-- UPDATE ai_model_config SET circuit_enabled = 1, circuit_key = provider_id::varchar || ':' || id::varchar WHERE status = 0;
-- UPDATE ai_model_scene_binding SET gray_bucket_key = 'TENANT', gray_experiment_id = 0 WHERE gray_experiment_id IS NULL;


-- ============================================================
-- 10. 自检 SQL（执行完应返回：3 张表补列数 11 / 9 / 13，新表 3 张，索引齐全）
-- ============================================================
-- SELECT table_name, count(*) AS new_columns FROM information_schema.columns
--  WHERE table_schema = current_schema()
--    AND table_name IN ('ai_model_config','ai_model_scene_binding','ai_model_call_log')
--    AND column_name IN (
--      'circuit_enabled','circuit_failure_rate','circuit_min_sample','circuit_window_seconds',
--      'circuit_open_seconds','circuit_half_open_probes','retryable_errors','retry_base_backoff_ms',
--      'retry_max_backoff_ms','retry_jitter_ratio','circuit_key',
--      'timeout_ms_override','qps_limit_override','concurrent_limit_override','retry_count_override',
--      'fallback_cross_provider','fallback_max_depth','gray_ratio','gray_bucket_key','gray_experiment_id',
--      'is_gray','gray_group','retry_of_trace_id','retry_index',
--      'circuit_state','error_category','fallback_depth','route_strategy','queue_wait_ms','request_id')
--  GROUP BY table_name ORDER BY table_name;
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = current_schema() AND table_name LIKE 'ai_model_%' ORDER BY table_name;


-- ============================================================
-- 11. 运行时推荐查询（供 Service / 编排层实现时参考）
-- ============================================================

-- 11.1 路由候选：策略 2 优先级 / 策略 3 权重 / 策略 4 成本优先
-- SELECT c.id AS model_config_id, c.model_id, c.provider_id, r.weight, r.priority,
--        c.input_price + c.output_price AS unit_cost, c.qps_limit, c.concurrent_limit,
--        c.timeout_ms, c.status, c.model_type
--   FROM ai_model_route_candidate r
--   JOIN ai_model_config c ON c.id = r.model_id AND c.deleted = 0
--  WHERE r.binding_id = :bindingId AND r.enabled = 1 AND r.deleted = 0
--    AND c.status IN (0, 2)                       -- 0 启用、2 灰度；1 停用直接排除
--  ORDER BY r.priority ASC, unit_cost ASC;
--  策略 3 由应用层按 weight 归一化分桶；策略 4 直接取 unit_cost 最小者。
--  跨供应商校验：fallback_cross_provider = 1 时，要求主备候选的 provider_id 不同。

-- 11.2 灰度 A/B 对比（一次实验只看一个变量，按 min_sample_size 过滤）
-- SELECT gray_group,
--        count(*) AS samples,
--        count(*) FILTER (WHERE status = 0)::numeric / NULLIF(count(*), 0) AS success_rate,
--        percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms) AS p95_latency_ms,
--        avg(cost) AS avg_cost,
--        count(*) FILTER (WHERE fallback_depth > 0)::numeric / NULLIF(count(*), 0) AS fallback_rate
--   FROM ai_model_call_log
--  WHERE gray_experiment_id = :experimentId AND is_gray = 1 AND deleted = 0
--    AND create_time BETWEEN :startTime AND :endTime
--  GROUP BY gray_group;

-- 11.3 熔断窗口失败率（真实数据源，Redis 为准时此查询用于核对 / 离线复盘）
-- SELECT circuit_key,
--        count(*) AS total,
--        count(*) FILTER (WHERE error_category IN ('RATE_LIMIT','SERVER_ERROR','TIMEOUT','CONNECT')) AS failed,
--        count(*) FILTER (WHERE error_category IN ('RATE_LIMIT','SERVER_ERROR','TIMEOUT','CONNECT'))::numeric
--          / NULLIF(count(*), 0) AS failure_rate
--   FROM ai_model_call_log
--  WHERE create_time >= now() - interval '60 seconds' AND deleted = 0
--  GROUP BY circuit_key;

-- 11.4 重试成本按原始 trace 聚合，避免「首次已成功但响应丢失」被记成 3 条独立成功
-- SELECT COALESCE(NULLIF(retry_of_trace_id, ''), trace_id) AS root_trace_id,
--        count(*) AS call_count, sum(cost) AS total_cost, sum(total_tokens) AS total_tokens,
--        max(retry_index) AS max_retry, max(fallback_depth) AS max_fallback
--   FROM ai_model_call_log
--  WHERE create_time >= :startTime AND deleted = 0
--  GROUP BY 1
-- HAVING count(*) > 1
--  ORDER BY total_cost DESC;


-- ============================================================
-- 03/32 nl2sql-model-protocol.sql
-- ============================================================
-- ============================================================
-- 模型协议下沉：ai_model_provider.protocol -> ai_model_config.protocol
-- 背景：一个供应商可能同时提供聊天（OpenAI 格式）与重排（Cohere 格式）能力，
--       协议挂在供应商层会导致二选一冲突，故下沉到模型配置层。
-- 适用：PostgreSQL / data_query schema
-- 执行时机：升级后端代码【之前】执行（实体会读取新列，缺列会导致查询报错）
-- 幂等：可重复执行
-- ============================================================

-- 1. 加列（默认 OPENAI，存量 Chat / Embedding / SQL 记录天然正确）
ALTER TABLE ai_model_config
    ADD COLUMN IF NOT EXISTS protocol varchar(32) NOT NULL DEFAULT 'OPENAI';

COMMENT ON COLUMN ai_model_config.protocol IS
    '协议（模型级）：Chat/Embedding/SQL 固定 OPENAI；Rerank 可选 DASHSCOPE / COHERE';

-- 2. Rerank 存量回填：优先沿用原供应商协议（仅接受 DASHSCOPE / COHERE）
UPDATE ai_model_config c
   SET protocol = UPPER(TRIM(p.protocol))
  FROM ai_model_provider p
 WHERE c.provider_id = p.id
   AND c.model_type = 3
   AND c.deleted = 0
   AND UPPER(TRIM(p.protocol)) IN ('DASHSCOPE', 'COHERE');

-- 3. 其余 Rerank 记录统一回填 COHERE（硅基流动等 /v1/rerank 兼容供应商的默认格式）
UPDATE ai_model_config
   SET protocol = 'COHERE'
 WHERE model_type = 3
   AND deleted = 0
   AND (protocol IS NULL OR UPPER(TRIM(protocol)) NOT IN ('DASHSCOPE', 'COHERE'));

-- 4. 非 Rerank 记录统一 OPENAI（Chat / Embedding / SQL 专用共用 OpenAI 兼容格式）
UPDATE ai_model_config
   SET protocol = 'OPENAI'
 WHERE model_type IN (1, 2, 4)
   AND deleted = 0
   AND (protocol IS NULL OR UPPER(TRIM(protocol)) <> 'OPENAI');

-- 5. 验证：按类型看协议分布
-- SELECT model_type, protocol, count(*) FROM ai_model_config WHERE deleted = 0 GROUP BY model_type, protocol ORDER BY model_type;


-- ============================================================
-- 04/32 nl2sql-prompt.sql
-- ============================================================
/*
 ai 平台 —— 管理端「提示词管理」数据模型（M2）
 Target Server Type : PostgreSQL
 版本               : 2026-11-12

 本期范围：按场景分类（12 个场景）+ 模板变量与占位符；
 同时预留版本管理、灰度发布、租户级覆盖的落点，避免后续改表。

 约定（与 sql/postgresql/nl2sql-model.sql、ruoyi-vue-pro.sql 保持一致）：
 1) 表前缀 ai_，与既有 ai_model_* 同族；字段类型、BaseDO 五字段、tenant_id、int2 枚举一致。
 2) 不建物理外键，关联由业务层维护；scene_code 直接复用 ai_model_scene_binding.scene_code，
    不另造编码体系。ai_model_scene_binding.prompt_id -> ai_prompt.id（既有表已预留该字段）。
 3) 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不依赖数据库序列；
    既有 4 张 ai_model_* 表同样未建序列。若以后改为芋道 BaseDO + @KeySequence，需补建 seq。
 4) 占位符语法统一 {{var_name}}，与 ai_prompt_variable.var_name 一一对应；
    不使用 ${...}，避免与 SQL 文本、SpEL 表达式冲突。
 5) ai_prompt_variable 是「编辑态」；ai_prompt.variable_defs / ai_prompt_version.variable_defs
    是发布时由应用生成的只读 JSON 快照。运行时按快照渲染，保证版本可回滚、diff 可对比。
 6) 唯一约束用 PostgreSQL 部分索引（WHERE deleted = 0），既防重复编码，
    又允许逻辑删除后重建同编码；迁移到 MySQL 时需改为业务层校验。

 ！！本脚本含 DROP TABLE IF EXISTS，只能对空库 / 测试库执行，已有数据请先备份。
 ！！导入时 search_path 必须与 application-local.yaml 的 currentSchema 一致（当前为 data_query），
    否则表落到 public，应用看不到（即此前 dual 报同类问题）：
 PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres -f sql/postgresql/nl2sql-prompt.sql
*/


-- ----------------------------
-- 1. 提示词场景字典（12 个场景，平台级）
-- ----------------------------
DROP TABLE IF EXISTS ai_prompt_scene;
CREATE TABLE ai_prompt_scene (
    id int8 NOT NULL,
  scene_code varchar(64) NOT NULL DEFAULT '',
  scene_name varchar(100) NOT NULL DEFAULT '',
  description varchar(500) NOT NULL DEFAULT '',
  default_variables text NULL,
  default_model_type int2 NOT NULL DEFAULT 1,
  status int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_prompt_scene ADD CONSTRAINT pk_ai_prompt_scene PRIMARY KEY (id);
CREATE INDEX idx_ai_prompt_scene_01 ON ai_prompt_scene (tenant_id, scene_code, status);
CREATE UNIQUE INDEX uk_ai_prompt_scene_01 ON ai_prompt_scene (tenant_id, scene_code) WHERE deleted = 0;

COMMENT ON TABLE ai_prompt_scene IS '提示词场景字典表';
COMMENT ON COLUMN ai_prompt_scene.id IS '主键';
COMMENT ON COLUMN ai_prompt_scene.scene_code IS '场景编码，与 ai_model_scene_binding.scene_code 一致：INTENT / REWRITE / SCHEMA_RECALL / SQL_GEN / SQL_FIX / EXPLAIN / RESULT_SUMMARY / REPORT / CHART / RECOMMEND / RAG / PREDICT';
COMMENT ON COLUMN ai_prompt_scene.scene_name IS '场景名称';
COMMENT ON COLUMN ai_prompt_scene.description IS '场景用途说明';
COMMENT ON COLUMN ai_prompt_scene.default_variables IS '该场景默认占位符集合，JSON 数组，如 ["question","schema"]';
COMMENT ON COLUMN ai_prompt_scene.default_model_type IS '建议模型类型：1 Chat、4 SQL 专用，对应 ai_model_config.model_type';
COMMENT ON COLUMN ai_prompt_scene.status IS '状态：0 启用、1 停用';
COMMENT ON COLUMN ai_prompt_scene.sort IS '排序';
COMMENT ON COLUMN ai_prompt_scene.remark IS '备注';
COMMENT ON COLUMN ai_prompt_scene.tenant_id IS '租户编号；场景字典为平台级，恒为 0，租户不复制';

-- ----------------------------
-- 2. 提示词主表（当前生效内容）
-- ----------------------------
DROP TABLE IF EXISTS ai_prompt;
CREATE TABLE ai_prompt (
    id int8 NOT NULL,
  scene_code varchar(64) NOT NULL DEFAULT '',
  name varchar(100) NOT NULL DEFAULT '',
  code varchar(64) NOT NULL DEFAULT '',
  prompt_type int2 NOT NULL DEFAULT 1,
  content text NOT NULL,
  variable_defs text NULL,
  model_id int8 NOT NULL DEFAULT 0,
  params text NULL,
  source int2 NOT NULL DEFAULT 1,
  parent_id int8 NOT NULL DEFAULT 0,
  version int4 NOT NULL DEFAULT 1,
  current_version_id int8 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  gray_ratio int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_prompt ADD CONSTRAINT pk_ai_prompt PRIMARY KEY (id);
CREATE INDEX idx_ai_prompt_01 ON ai_prompt (tenant_id, scene_code, status);
CREATE INDEX idx_ai_prompt_02 ON ai_prompt (tenant_id, code);
CREATE UNIQUE INDEX uk_ai_prompt_01 ON ai_prompt (tenant_id, code) WHERE deleted = 0;

COMMENT ON TABLE ai_prompt IS '提示词模板主表（当前生效内容）';
COMMENT ON COLUMN ai_prompt.id IS '主键';
COMMENT ON COLUMN ai_prompt.scene_code IS '场景编码，关联 ai_prompt_scene.scene_code；同一场景可有多条模板（默认/灰度/A-B）';
COMMENT ON COLUMN ai_prompt.name IS '提示词名称';
COMMENT ON COLUMN ai_prompt.code IS '提示词编码，租户内唯一，供程序按编码引用';
COMMENT ON COLUMN ai_prompt.prompt_type IS '类型：1 System、2 User、3 完整模板（含多段消息）';
COMMENT ON COLUMN ai_prompt.content IS '模板正文，占位符统一使用 {{var_name}}';
COMMENT ON COLUMN ai_prompt.variable_defs IS '变量定义只读快照 JSON，由 ai_prompt_variable 发布时生成；运行时按此渲染';
COMMENT ON COLUMN ai_prompt.model_id IS '默认模型，关联 ai_model_config.id；0 表示跟随 ai_model_scene_binding 的路由结果';
COMMENT ON COLUMN ai_prompt.params IS '模型参数覆盖 JSON：temperature / top_p / max_tokens';
COMMENT ON COLUMN ai_prompt.source IS '来源：1 平台内置、2 租户自定义、3 复制自平台模板';
COMMENT ON COLUMN ai_prompt.parent_id IS '继承/复制来源 ai_prompt.id，0 表示原始模板';
COMMENT ON COLUMN ai_prompt.version IS '当前版本号，与 ai_prompt_version.version 对应';
COMMENT ON COLUMN ai_prompt.current_version_id IS '当前生效版本，关联 ai_prompt_version.id';
COMMENT ON COLUMN ai_prompt.status IS '状态：0 启用、1 停用、2 灰度';
COMMENT ON COLUMN ai_prompt.gray_ratio IS '灰度流量比例 0-100，仅 status=2 时生效';
COMMENT ON COLUMN ai_prompt.sort IS '同场景内的优先级，数值越小越优先';
COMMENT ON COLUMN ai_prompt.remark IS '备注';
COMMENT ON COLUMN ai_prompt.tenant_id IS '租户编号；0 为平台级默认，租户可复制同 code 覆盖';

-- ----------------------------
-- 3. 提示词版本（内容快照 / diff / 一键回滚）
-- ----------------------------
DROP TABLE IF EXISTS ai_prompt_version;
CREATE TABLE ai_prompt_version (
    id int8 NOT NULL,
  prompt_id int8 NOT NULL DEFAULT 0,
  version int4 NOT NULL DEFAULT 1,
  content text NOT NULL,
  variable_defs text NULL,
  params text NULL,
  change_log varchar(500) NOT NULL DEFAULT '',
  content_hash varchar(64) NOT NULL DEFAULT '',
  token_estimate int4 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  publish_time timestamp NULL,
  publisher varchar(64) NOT NULL DEFAULT '',
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_prompt_version ADD CONSTRAINT pk_ai_prompt_version PRIMARY KEY (id);
CREATE INDEX idx_ai_prompt_version_01 ON ai_prompt_version (tenant_id, prompt_id, version);
CREATE INDEX idx_ai_prompt_version_02 ON ai_prompt_version (prompt_id, status);
CREATE UNIQUE INDEX uk_ai_prompt_version_01 ON ai_prompt_version (tenant_id, prompt_id, version) WHERE deleted = 0;

COMMENT ON TABLE ai_prompt_version IS '提示词版本表（内容快照）';
COMMENT ON COLUMN ai_prompt_version.id IS '主键';
COMMENT ON COLUMN ai_prompt_version.prompt_id IS '关联 ai_prompt.id';
COMMENT ON COLUMN ai_prompt_version.version IS '版本号，同一提示词内单调递增';
COMMENT ON COLUMN ai_prompt_version.content IS '该版本的模板正文快照';
COMMENT ON COLUMN ai_prompt_version.variable_defs IS '该版本的变量定义快照，用于版本 diff';
COMMENT ON COLUMN ai_prompt_version.params IS '该版本的模型参数快照';
COMMENT ON COLUMN ai_prompt_version.change_log IS '变更说明';
COMMENT ON COLUMN ai_prompt_version.content_hash IS '正文哈希，用于快速判断两版本是否相同';
COMMENT ON COLUMN ai_prompt_version.token_estimate IS 'Token 预估，调试台展示用';
COMMENT ON COLUMN ai_prompt_version.status IS '状态：0 草稿、1 已发布、2 已回滚、3 归档';
COMMENT ON COLUMN ai_prompt_version.publish_time IS '发布时间';
COMMENT ON COLUMN ai_prompt_version.publisher IS '发布人';
COMMENT ON COLUMN ai_prompt_version.remark IS '备注';
COMMENT ON COLUMN ai_prompt_version.tenant_id IS '租户编号';

-- ----------------------------
-- 4. 模板变量 / 占位符定义（编辑态）
-- ----------------------------
DROP TABLE IF EXISTS ai_prompt_variable;
CREATE TABLE ai_prompt_variable (
    id int8 NOT NULL,
  prompt_id int8 NOT NULL DEFAULT 0,
  scene_code varchar(64) NOT NULL DEFAULT '',
  var_name varchar(64) NOT NULL DEFAULT '',
  var_label varchar(100) NOT NULL DEFAULT '',
  var_type int2 NOT NULL DEFAULT 1,
  source_type int2 NOT NULL DEFAULT 1,
  source_key varchar(128) NOT NULL DEFAULT '',
  required int2 NOT NULL DEFAULT 1,
  default_value varchar(1000) NOT NULL DEFAULT '',
  sample_value varchar(1000) NOT NULL DEFAULT '',
  validate_regex varchar(255) NOT NULL DEFAULT '',
  max_length int4 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_prompt_variable ADD CONSTRAINT pk_ai_prompt_variable PRIMARY KEY (id);
CREATE INDEX idx_ai_prompt_variable_01 ON ai_prompt_variable (tenant_id, prompt_id, var_name);
CREATE INDEX idx_ai_prompt_variable_02 ON ai_prompt_variable (scene_code, prompt_id);
CREATE UNIQUE INDEX uk_ai_prompt_variable_01 ON ai_prompt_variable (tenant_id, prompt_id, var_name) WHERE deleted = 0;

COMMENT ON TABLE ai_prompt_variable IS '提示词模板变量（占位符）定义表';
COMMENT ON COLUMN ai_prompt_variable.id IS '主键';
COMMENT ON COLUMN ai_prompt_variable.prompt_id IS '关联 ai_prompt.id';
COMMENT ON COLUMN ai_prompt_variable.scene_code IS '场景编码冗余，便于按场景检索变量';
COMMENT ON COLUMN ai_prompt_variable.var_name IS '占位符名，不含双花括号，如 question / schema';
COMMENT ON COLUMN ai_prompt_variable.var_label IS '占位符中文名';
COMMENT ON COLUMN ai_prompt_variable.var_type IS '值类型：1 文本、2 数字、3 日期、4 枚举、5 JSON、6 SQL 片段、7 表结构、8 结果集';
COMMENT ON COLUMN ai_prompt_variable.source_type IS '注入来源：1 系统上下文、2 用户输入、3 元数据召回、4 上一步结果、5 字典/配置';
COMMENT ON COLUMN ai_prompt_variable.source_key IS '来源键，如 user.question / metadata.schema / context.current_date';
COMMENT ON COLUMN ai_prompt_variable.required IS '是否必填：0 否、1 是；必填变量无值时禁止渲染';
COMMENT ON COLUMN ai_prompt_variable.default_value IS '默认值，非必填变量缺省时使用';
COMMENT ON COLUMN ai_prompt_variable.sample_value IS '示例值，调试台与文档展示用';
COMMENT ON COLUMN ai_prompt_variable.validate_regex IS '值校验正则，为空则不校验';
COMMENT ON COLUMN ai_prompt_variable.max_length IS '渲染时最大字符数，0 表示不限；用于防止拼接超长 Schema 撑爆上下文';
COMMENT ON COLUMN ai_prompt_variable.status IS '状态：0 启用、1 停用';
COMMENT ON COLUMN ai_prompt_variable.sort IS '排序';
COMMENT ON COLUMN ai_prompt_variable.remark IS '备注';
COMMENT ON COLUMN ai_prompt_variable.tenant_id IS '租户编号';

-- ----------------------------
-- 初始化数据：12 个场景
-- ----------------------------
-- @formatter:off
BEGIN;

INSERT INTO ai_prompt_scene (id, scene_code, scene_name, description, default_variables, default_model_type, status, sort, creator, updater, tenant_id) VALUES
(1,  'INTENT',         '意图识别',     '判断提问属于问数 / 知识问答 / 报告 / 图表 / 分析 / 预测 / 闲聊 / 越权', '["question","history"]',                                  1, 0, 1,  '1', '1', 0),
(2,  'REWRITE',        '问题改写',     '多轮指代消解、省略补全、术语归一、时间表达式解析',                     '["question","history","domain_terms"]',                    1, 0, 2,  '1', '1', 0),
(3,  'SCHEMA_RECALL',  'Schema 召回',  '检索并裁剪表结构、列、样例值与 Join 路径',                           '["question","table_candidates","top_k"]',                  1, 0, 3,  '1', '1', 0),
(4,  'SQL_GEN',        'SQL 生成',     '按方言生成只读 SQL',                                                '["question","schema","dialect","few_shot","current_date"]', 4, 0, 4,  '1', '1', 0),
(5,  'SQL_FIX',        'SQL 修正',     '执行报错后的自修复与有限次重试',                                     '["sql","error_msg","schema","dialect"]',                   4, 0, 5,  '1', '1', 0),
(6,  'EXPLAIN',        '口径解释',     '把 SQL 与指标口径翻译成业务白话',                                   '["question","sql","metric_def"]',                          1, 0, 6,  '1', '1', 0),
(7,  'RESULT_SUMMARY', '结果解读',     '结果摘要、异常点提示、结论生成',                                     '["question","sql","columns","rows"]',                      1, 0, 7,  '1', '1', 0),
(8,  'REPORT',         '智能报告',     '按主题与时间范围生成分章节报告',                                     '["topic","time_range","sections","analysis_result"]',      1, 0, 8,  '1', '1', 0),
(9,  'CHART',          '图表推荐',     '推荐图表类型并生成可视化配置',                                       '["question","columns","rows","chart_types"]',              1, 0, 9,  '1', '1', 0),
(10, 'RECOMMEND',      '问题推荐',     '基于历史 / 热门问题 / 元数据生成推荐问题',                           '["topic","history","hot_questions"]',                      1, 0, 10, '1', '1', 0),
(11, 'RAG',            '知识问答',     '带引用的知识库检索增强回答',                                         '["question","context","history"]',                         1, 0, 11, '1', '1', 0),
(12, 'PREDICT',        '数据预测',     '时序预测、区间与置信说明',                                           '["question","series","horizon"]',                          1, 0, 12, '1', '1', 0);

-- ----------------------------
-- 初始化数据：3 条示例提示词（内容仅作占位，正式模板由管理端录入）
-- ----------------------------
INSERT INTO ai_prompt (id, scene_code, name, code, prompt_type, model_id, content, variable_defs, params, source, parent_id, version, current_version_id, status, gray_ratio, sort, remark, tenant_id, creator, updater) VALUES
(1, 'SQL_GEN', 'SQL 生成·默认模板', 'PROMPT_SQL_GEN_DEFAULT', 1, 2,
 E'你是资深数据分析师，请根据下表结构生成一条 {{dialect}} 只读查询语句。\n表结构：\n{{schema}}\n参考示例：\n{{few_shot}}\n当前日期：{{current_date}}\n用户问题：{{question}}\n约束：仅输出 SQL；禁止 INSERT / UPDATE / DELETE / DDL；结果必须带 LIMIT。',
 '[{"name":"question","required":true},{"name":"schema","required":true},{"name":"dialect","required":true},{"name":"few_shot","required":false},{"name":"current_date","required":true}]',
 '{"temperature":0,"top_p":1,"max_tokens":2048}', 1, 0, 1, 1, 0, 0, 1, NULL, 0, '1', '1'),
(2, 'INTENT', '意图识别·默认模板', 'PROMPT_INTENT_DEFAULT', 1, 1,
 E'判断用户提问的意图，从 [问数, 知识问答, 报告, 图表, 分析, 预测, 闲聊, 越权] 中选择一个。\n历史对话：{{history}}\n用户问题：{{question}}\n仅输出英文意图编码。',
 '[{"name":"question","required":true},{"name":"history","required":false}]',
 '{"temperature":0,"max_tokens":64}', 1, 0, 1, 2, 0, 0, 1, NULL, 0, '1', '1'),
(3, 'EXPLAIN', '口径解释·默认模板', 'PROMPT_EXPLAIN_DEFAULT', 1, 0,
 E'请用业务人员能听懂的话解释下面这条 SQL 的统计口径。\n用户问题：{{question}}\nSQL：{{sql}}\n指标定义：{{metric_def}}\n按「查了什么 / 过滤条件 / 计算逻辑 / 时间范围」四段输出。',
 '[{"name":"question","required":true},{"name":"sql","required":true},{"name":"metric_def","required":false}]',
 '{"temperature":0.3,"max_tokens":1024}', 1, 0, 1, 3, 0, 0, 1, NULL, 0, '1', '1');

-- ----------------------------
-- 初始化数据：3 条 v1 版本快照（与上方 content 一致）
-- ----------------------------
INSERT INTO ai_prompt_version (id, prompt_id, version, content, variable_defs, params, change_log, content_hash, token_estimate, status, publish_time, publisher, remark, tenant_id, creator, updater) VALUES
(1, 1, 1,
 E'你是资深数据分析师，请根据下表结构生成一条 {{dialect}} 只读查询语句。\n表结构：\n{{schema}}\n参考示例：\n{{few_shot}}\n当前日期：{{current_date}}\n用户问题：{{question}}\n约束：仅输出 SQL；禁止 INSERT / UPDATE / DELETE / DDL；结果必须带 LIMIT。',
 '[{"name":"question","required":true},{"name":"schema","required":true},{"name":"dialect","required":true},{"name":"few_shot","required":false},{"name":"current_date","required":true}]',
 '{"temperature":0,"top_p":1,"max_tokens":2048}', '初始版本', '', 512, 1, CURRENT_TIMESTAMP, '1', NULL, 0, '1', '1'),
(2, 2, 1,
 E'判断用户提问的意图，从 [问数, 知识问答, 报告, 图表, 分析, 预测, 闲聊, 越权] 中选择一个。\n历史对话：{{history}}\n用户问题：{{question}}\n仅输出英文意图编码。',
 '[{"name":"question","required":true},{"name":"history","required":false}]',
 '{"temperature":0,"max_tokens":64}', '初始版本', '', 128, 1, CURRENT_TIMESTAMP, '1', NULL, 0, '1', '1'),
(3, 3, 1,
 E'请用业务人员能听懂的话解释下面这条 SQL 的统计口径。\n用户问题：{{question}}\nSQL：{{sql}}\n指标定义：{{metric_def}}\n按「查了什么 / 过滤条件 / 计算逻辑 / 时间范围」四段输出。',
 '[{"name":"question","required":true},{"name":"sql","required":true},{"name":"metric_def","required":false}]',
 '{"temperature":0.3,"max_tokens":1024}', '初始版本', '', 256, 1, CURRENT_TIMESTAMP, '1', NULL, 0, '1', '1');

-- ----------------------------
-- 初始化数据：占位符定义（编辑态，发布时生成 variable_defs 快照）
-- var_type: 1文本 2数字 3日期 4枚举 5JSON 6SQL片段 7表结构 8结果集
-- source_type: 1系统上下文 2用户输入 3元数据召回 4上一步结果 5字典/配置
-- ----------------------------
INSERT INTO ai_prompt_variable (id, prompt_id, scene_code, var_name, var_label, var_type, source_type, source_key, required, default_value, sample_value, validate_regex, max_length, status, sort, remark, tenant_id, creator, updater) VALUES
(1,  1, 'SQL_GEN', 'question',      '用户问题',   1, 2, 'user.question',          1, '', '',            '',    2000,  0, 1, NULL, 0, '1', '1'),
(2,  1, 'SQL_GEN', 'schema',        '表结构',     7, 3, 'metadata.schema',        1, '', 'orders(id,amount)', '',   16000, 0, 2, NULL, 0, '1', '1'),
(3,  1, 'SQL_GEN', 'dialect',       'SQL 方言',   4, 5, 'datasource.dialect',    1, 'postgresql', 'postgresql', '', 64,    0, 3, NULL, 0, '1', '1'),
(4,  1, 'SQL_GEN', 'few_shot',      '相似问答样例', 1, 3, 'rag.similar_qa',        0, '无', '问题：... SQL：...', '', 4000,  0, 4, NULL, 0, '1', '1'),
(5,  1, 'SQL_GEN', 'current_date',  '当前日期',   3, 1, 'context.current_date',  1, '', '2026-11-12',     '',    32,    0, 5, NULL, 0, '1', '1'),
(6,  2, 'INTENT',  'question',      '用户问题',   1, 2, 'user.question',          1, '', '',            '',    2000,  0, 1, NULL, 0, '1', '1'),
(7,  2, 'INTENT',  'history',       '历史对话',   5, 1, 'context.history',        0, '[]', '[]',          '',    8000,  0, 2, NULL, 0, '1', '1'),
(8,  3, 'EXPLAIN', 'question',      '用户问题',   1, 2, 'user.question',          1, '', '',            '',    2000,  0, 1, NULL, 0, '1', '1'),
(9,  3, 'EXPLAIN', 'sql',           '待解释 SQL', 6, 4, 'engine.generated_sql',  1, '', 'SELECT ...',    '',    8000,  0, 2, NULL, 0, '1', '1'),
(10, 3, 'EXPLAIN', 'metric_def',    '指标口径',   5, 3, 'metadata.metric_def',   0, '{}', '{}',          '',    8000,  0, 3, NULL, 0, '1', '1');

COMMIT;
-- @formatter:on

-- ----------------------------
-- 运行时取模板的推荐查询（应用逻辑，非本脚本执行内容）
-- 优先级：租户级覆盖 > 平台级默认；同场景内按 sort 取第一条启用/灰度命中项。
-- SELECT id, content, variable_defs, params, version FROM ai_prompt
--  WHERE scene_code = :sceneCode AND tenant_id IN (:tenantId, 0)
--    AND status = 0 AND deleted = 0
--  ORDER BY tenant_id DESC, sort ASC LIMIT 1;
-- 若 ai_model_scene_binding.prompt_id <> 0，则优先按该 ID 精确取模板（显式覆盖）。
-- 渲染前必须校验：content 中出现的每个 {{var}} 都在 variable_defs 中登记，且必填变量已有值。


-- ============================================================
-- 05/32 nl2sql-prompt-rag.sql
-- ============================================================
-- ============================================================================
-- P9.2 知识库问答：RAG 场景提示词种子 + 模型场景绑定
--
-- 用途：让知识库问答链路（KnowledgeChatOrchestrator）第一次跑起来就有可用模板。
-- 依赖：nl2sql-prompt.sql（ai_prompt_scene 已含 RAG 场景字典）、nl2sql-model.sql（模型与绑定）。
--
-- 变量口径（渲染时由服务端注入，前端无法传入）：
--   {{question}} 用户问题          来源：请求参数
--   {{context}}  检索上下文         来源：KnowledgeRecallService（带 [1..K] 编号）
--   {{history}}  对话历史           来源：对话树主链回溯
--
-- 幂等：全部按主键 ON CONFLICT DO NOTHING，可重复执行。
-- 注意：本脚本只插平台级（tenant_id=0）模板；租户若要自定义，在管理端建租户级模板覆盖即可。
-- 兼容性：多行模板用显式 || 连接，不依赖 SQL 标准「跨行字符串隐式拼接」（该规则要求两个
--         字符串常量之间必须保留换行，部分客户端把 SQL 压平成单行后会直接语法报错）。
-- 重跑提示：若上一次执行失败且未关闭连接，事务会停留在 aborted 状态，先执行 ROLLBACK;
--           或换一个新连接 / 新查询标签页，否则仍会报 25P02。
-- @formatter:off
BEGIN;

-- ----------------------------
-- 1. RAG 提示词主表（id=4，与既有 3 条示例提示词不冲突）
-- ----------------------------
INSERT INTO ai_prompt (id, scene_code, name, code, prompt_type, model_id, content, variable_defs, params,
                       source, parent_id, version, current_version_id, status, gray_ratio, sort, remark,
                       tenant_id, creator, updater)
VALUES (4, 'RAG', '知识问答·默认模板', 'PROMPT_RAG_DEFAULT', 1, 0,
        E'你是企业知识库问答助手。只能依据【参考资料】回答，禁止使用资料以外的知识，也不要凭常识推测。\n\n' ||
        E'【参考资料】\n{{context}}\n\n' ||
        E'【历史对话】\n{{history}}\n\n' ||
        E'【用户问题】\n{{question}}\n\n' ||
        E'回答要求：\n' ||
        E'1. 只使用【参考资料】中的信息作答；资料没有覆盖的内容，明确回答「知识库中未找到相关内容」，不要编造。\n' ||
        E'2. 引用资料时在对应句子末尾标注编号，格式为 [n]，可连续标注多个如 [1][3]。\n' ||
        E'3. n 必须是【参考资料】中真实存在的编号，禁止编造编号，也不要输出其他方括号数字。\n' ||
        E'4. 用中文回答，结构清晰；涉及步骤、条件、金额、时限、责任方等关键信息时保留资料原文表述。\n' ||
        E'5. 不要提及「参考资料」「上下文」「资料编号」等系统词汇，直接给出答案即可。',
 '[{"name":"question","required":true},{"name":"context","required":true},{"name":"history","required":false}]',
 '{"temperature":0.2,"top_p":0.9,"max_tokens":2048}',
 1, 0, 1, 4, 0, 0, 11,
 '知识库 RAG 默认模板：只依据检索上下文作答并强制引用编号', 0, '1', '1')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 2. v1 版本快照（内容与上面完全一致，保证「发布态」可复现）
-- ----------------------------
INSERT INTO ai_prompt_version (id, prompt_id, version, content, variable_defs, params, change_log,
                               content_hash, token_estimate, status, publish_time, publisher, remark,
                               tenant_id, creator, updater)
VALUES (4, 4, 1,
        E'你是企业知识库问答助手。只能依据【参考资料】回答，禁止使用资料以外的知识，也不要凭常识推测。\n\n' ||
        E'【参考资料】\n{{context}}\n\n' ||
        E'【历史对话】\n{{history}}\n\n' ||
        E'【用户问题】\n{{question}}\n\n' ||
        E'回答要求：\n' ||
        E'1. 只使用【参考资料】中的信息作答；资料没有覆盖的内容，明确回答「知识库中未找到相关内容」，不要编造。\n' ||
        E'2. 引用资料时在对应句子末尾标注编号，格式为 [n]，可连续标注多个如 [1][3]。\n' ||
        E'3. n 必须是【参考资料】中真实存在的编号，禁止编造编号，也不要输出其他方括号数字。\n' ||
        E'4. 用中文回答，结构清晰；涉及步骤、条件、金额、时限、责任方等关键信息时保留资料原文表述。\n' ||
        E'5. 不要提及「参考资料」「上下文」「资料编号」等系统词汇，直接给出答案即可。',
 '[{"name":"question","required":true},{"name":"context","required":true},{"name":"history","required":false}]',
 '{"temperature":0.2,"top_p":0.9,"max_tokens":2048}',
 '初始版本', '', 768, 1, CURRENT_TIMESTAMP, '1', NULL, 0, '1', '1')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 3. 占位符定义（编辑态）：var_type 1文本 5JSON；source_type 1系统上下文 2用户输入 3元数据召回
-- ----------------------------
INSERT INTO ai_prompt_variable (id, prompt_id, scene_code, var_name, var_label, var_type, source_type,
                                source_key, required, default_value, sample_value, validate_regex, max_length,
                                status, sort, remark, tenant_id, creator, updater)
VALUES (11, 4, 'RAG', 'question', '用户问题',   1, 2, 'user.question',       1, '', '',                     '', 2000, 0, 1, NULL, 0, '1', '1'),
       (12, 4, 'RAG', 'context',  '检索上下文', 5, 3, 'rag.context',         1, '', '【资料 1】...',          '', 12000, 0, 2, '由服务端召回并编号，前端不可传', 0, '1', '1'),
       (13, 4, 'RAG', 'history',  '历史对话',   1, 1, 'context.history',     0, '', E'用户：...\n助手：...', '', 4000, 0, 3, NULL, 0, '1', '1')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 4. 模型场景绑定：已移除（2026-11-26）
--    原 INSERT 预置 tenant_id=0 的 RAG 绑定并指向平台模板模型 id=1；
--    模板已整体删除，现由租户管理员在管理端自行创建模型与场景绑定。
-- ----------------------------

COMMIT;
-- @formatter:on

-- ----------------------------
-- 执行后自检（人工核对，不自动跑）：
-- 1) 模板是否就位且启用
-- SELECT id, scene_code, code, version, current_version_id, status, tenant_id
--   FROM ai_prompt WHERE scene_code = 'RAG' AND deleted = 0;
-- 2) 版本快照是否存在
-- SELECT id, prompt_id, version, status FROM ai_prompt_version WHERE prompt_id = 4 AND deleted = 0;
-- 3) 绑定是否启用且指向真实模型
-- SELECT b.id, b.scene_code, b.primary_model_id, m.name, m.model, m.status
--   FROM ai_model_scene_binding b LEFT JOIN ai_model_config m ON m.id = b.primary_model_id
--  WHERE b.scene_code = 'RAG' AND b.deleted = 0;
-- 4) 变量是否齐全（缺 context 会渲染失败）
-- SELECT var_name, required FROM ai_prompt_variable WHERE prompt_id = 4 AND deleted = 0 ORDER BY sort;
-- ----------------------------


-- ============================================================
-- 06/32 nl2sql-table-narrate.sql
-- ============================================================
-- ============================================================================
-- P9.3 知识库摄取：表格 LLM 叙述化 场景种子（TABLE_NARRATE）
--
-- 用途：DOCX 表格在摄取阶段由轻量模型读成自然语言描述，替代扁平 " | " 文本。
-- 依赖：nl2sql-prompt.sql（ai_prompt_scene 表）、nl2sql-model.sql（绑定表）、
--       nl2sql-prompt-rag.sql（沿用 RAG 的提示词/版本/变量表结构约定）。
--
-- 关键：模型绑定 enabled=0、primary_model_id=0 占位 —— 由 AiTenantInitializer 复制到各租户后，
--       管理员在后台为 TABLE_NARRATE 选择轻量模型并启用。未启用时 ModelRouter 抛 CONFIG_ERROR，
--       TableNarrateService 捕获后降级保留原表文本，零成本、不影响摄取成功。
--
-- 变量口径（由 TableNarrateService 服务端注入，前端无法传入）：
--   {{table_markdown}} 规范化 Markdown 表格   {{heading_path}} 标题路径
--   {{batch_no}} 第几批                        {{batch_total}} 共几批
--
-- 幂等：全部按主键 ON CONFLICT DO NOTHING，可重复执行。
-- 兼容性：多行模板用显式 || 连接，不依赖跨行字符串隐式拼接。
-- 重跑提示：若上次执行失败事务停在 aborted 状态，先 ROLLBACK 或换新连接。
-- @formatter:off
BEGIN;

-- ----------------------------
-- 1. 场景字典（id=13，与既有 12 个场景不冲突）
-- ----------------------------
INSERT INTO ai_prompt_scene (id, scene_code, scene_name, description, default_variables, default_model_type, status, sort, creator, updater, tenant_id) VALUES
(13, 'TABLE_NARRATE', '表格叙述化', '摄取阶段把 DOCX 表格整理成分段自然语言描述',
 '["table_markdown","heading_path","batch_no","batch_total"]', 1, 0, 13, '1', '1', 0)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 2. 提示词主表（id=5，current_version_id=5）
-- ----------------------------
INSERT INTO ai_prompt (id, scene_code, name, code, prompt_type, model_id, content, variable_defs, params,
                       source, parent_id, version, current_version_id, status, gray_ratio, sort, remark,
                       tenant_id, creator, updater)
VALUES (5, 'TABLE_NARRATE', '表格叙述化·默认模板', 'PROMPT_TABLE_NARRATE_DEFAULT', 1, 0,
        E'你是文档整理助手。下面是一张从文档中抽取的表格（Markdown 格式），请把它整理成若干段自包含的中文自然语言描述。\n\n' ||
        E'【所属章节】\n{{heading_path}}\n\n' ||
        E'【批次】第 {{batch_no}} / {{batch_total}} 批\n\n' ||
        E'【表格】\n{{table_markdown}}\n\n' ||
        E'要求：\n' ||
        E'1. 逐行叙述：把每一行表格数据写成一句或几句完整的话，字段（表头）与值必须配对写清，例如「申请人是张三，申请日期是 2024-07-01」。\n' ||
        E'2. 忠实原文：所有单元格的文字、数字、金额、日期、编号都必须原样保留，禁止改写、省略、四舍五入或编造；表格里没有的信息不要补充。\n' ||
        E'3. 用中文分段输出，每段之间用空行分隔；不要输出 Markdown 表格、不要加项目符号、不要写解释性前言后语。\n' ||
        E'4. 对「左列是字段名、右列是填写内容」的申请表，务必把「字段名：填写内容」的对应关系写清楚，不要写成一串并列的词。',
 '[{"name":"table_markdown","required":true},{"name":"heading_path","required":false},{"name":"batch_no","required":false},{"name":"batch_total","required":false}]',
 '{"temperature":0.1,"top_p":0.9,"max_tokens":2048}',
 1, 0, 1, 5, 0, 0, 13,
 '表格叙述化默认模板：逐行转成字段-值配对的自然语言，忠实保留所有单元格', 0, '1', '1')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 3. v1 版本快照（内容与主表完全一致，保证发布态可复现）
-- ----------------------------
INSERT INTO ai_prompt_version (id, prompt_id, version, content, variable_defs, params, change_log,
                               content_hash, token_estimate, status, publish_time, publisher, remark,
                               tenant_id, creator, updater)
VALUES (5, 5, 1,
        E'你是文档整理助手。下面是一张从文档中抽取的表格（Markdown 格式），请把它整理成若干段自包含的中文自然语言描述。\n\n' ||
        E'【所属章节】\n{{heading_path}}\n\n' ||
        E'【批次】第 {{batch_no}} / {{batch_total}} 批\n\n' ||
        E'【表格】\n{{table_markdown}}\n\n' ||
        E'要求：\n' ||
        E'1. 逐行叙述：把每一行表格数据写成一句或几句完整的话，字段（表头）与值必须配对写清，例如「申请人是张三，申请日期是 2024-07-01」。\n' ||
        E'2. 忠实原文：所有单元格的文字、数字、金额、日期、编号都必须原样保留，禁止改写、省略、四舍五入或编造；表格里没有的信息不要补充。\n' ||
        E'3. 用中文分段输出，每段之间用空行分隔；不要输出 Markdown 表格、不要加项目符号、不要写解释性前言后语。\n' ||
        E'4. 对「左列是字段名、右列是填写内容」的申请表，务必把「字段名：填写内容」的对应关系写清楚，不要写成一串并列的词。',
 '[{"name":"table_markdown","required":true},{"name":"heading_path","required":false},{"name":"batch_no","required":false},{"name":"batch_total","required":false}]',
 '{"temperature":0.1,"top_p":0.9,"max_tokens":2048}',
 '初始版本', '', 900, 1, CURRENT_TIMESTAMP, '1', NULL, 0, '1', '1')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 4. 占位符定义（编辑态）：var_type 1文本；source_type 1系统上下文 2用户输入 3元数据召回
-- ----------------------------
INSERT INTO ai_prompt_variable (id, prompt_id, scene_code, var_name, var_label, var_type, source_type,
                                source_key, required, default_value, sample_value, validate_regex, max_length,
                                status, sort, remark, tenant_id, creator, updater)
VALUES (14, 5, 'TABLE_NARRATE', 'table_markdown', 'Markdown 表格', 1, 3, 'doc.table_markdown', 1, '', '| 字段 | 值 |\n| --- | --- |\n| 申请人 | 张三 |', '', 12000, 0, 1, '由解析器序列化后服务端注入，前端不可传', 0, '1', '1'),
       (15, 5, 'TABLE_NARRATE', 'heading_path',  '所属章节',     1, 1, 'doc.heading_path',    0, '', '报销制度 > 差旅费',              '', 500,   0, 2, NULL, 0, '1', '1'),
       (16, 5, 'TABLE_NARRATE', 'batch_no',      '当前批次',     1, 1, 'doc.batch_no',        0, '', '1',                              '', 16,    0, 3, NULL, 0, '1', '1'),
       (17, 5, 'TABLE_NARRATE', 'batch_total',   '总批次数',     1, 1, 'doc.batch_total',     0, '', '1',                              '', 16,    0, 4, NULL, 0, '1', '1')
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 5. 模型场景绑定：已移除（2026-11-26）
--    原 INSERT 预置 tenant_id=0 的 TABLE_NARRATE 占位绑定；模板已整体删除，
--    现由租户管理员在管理端自行创建场景绑定并配置轻量模型。
-- ----------------------------

COMMIT;
-- @formatter:on

-- ----------------------------
-- 执行后自检（人工核对，不自动跑）：
-- SELECT id, scene_code, current_version_id, status FROM ai_prompt WHERE scene_code = 'TABLE_NARRATE' AND deleted = 0;
-- SELECT id, scene_code, primary_model_id, enabled FROM ai_model_scene_binding WHERE scene_code = 'TABLE_NARRATE' AND deleted = 0;
-- ----------------------------


-- ============================================================
-- 07/32 nl2sql-rerank.sql
-- ============================================================
-- ============================================================================
-- P9.3b 知识库 RAG：Top-K 专用 Rerank（Cross-Encoder）场景种子
--
-- 用途：知识库 S2 召回在 RRF 融合、裁剪出 Top-K 之后，可选一次专用 Rerank 模型精排，
--       提升进 prompt 的上下文相关性。仅重排这 K 条，不扩大候选池。
--
-- 依赖：nl2sql-prompt.sql（ai_prompt_scene 表）、nl2sql-model.sql（绑定表）。
-- 不需要 ai_prompt / ai_prompt_version / ai_prompt_variable：专用 rerank API 的输入是
-- query + documents 结构化 JSON，不是模板提示词。
--
-- 关键：模型绑定 enabled=0、primary_model_id=0 占位 —— 由 AiTenantInitializer 复制到各租户后，
--       管理员在后台新建一条 model_type=3（Rerank）的模型配置、录入本租户凭证，
--       再把 RERANK 绑定指向它并启用。同时需在 application.yaml 打开
--       data-query.knowledge.rerank.enabled=true。
--       未启用时召回侧直接跳过精排，行为与改造前逐字节一致，零成本。
--
-- 幂等：全部 ON CONFLICT (id) DO NOTHING；场景字典额外加 WHERE NOT EXISTS 防止
--       (tenant_id, scene_code) 唯一索引在重复执行时报冲突。
-- 重跑提示：若上次执行失败事务停在 aborted 状态，先 ROLLBACK 或换新连接。
-- @formatter:off
BEGIN;

-- ----------------------------
-- 1. 场景字典（id=14，与既有 1..13 不冲突）
-- ----------------------------
INSERT INTO ai_prompt_scene
 (id, scene_code, scene_name, description, default_variables, default_model_type,
  status, sort, creator, updater, tenant_id)
SELECT 14, 'RERANK', '重排序',
       '知识库召回 Top-K 专用 Cross-Encoder 精排（query + documents → relevance_score[]）',
       '["query","documents"]', 3, 0, 14, '1', '1', 0
WHERE NOT EXISTS (
    SELECT 1 FROM ai_prompt_scene WHERE tenant_id = 0 AND scene_code = 'RERANK' AND deleted = 0
)
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 2. 场景绑定：已移除（2026-11-26）
--    原预置 tenant_id=0 的 RERANK 占位绑定，模板已整体删除；
--    现由租户管理员在管理端自行创建 RERANK 场景绑定并绑定模型。
-- ----------------------------

COMMIT;

-- ----------------------------
-- 自检
-- ----------------------------
-- SELECT id, scene_code, enabled, primary_model_id FROM ai_model_scene_binding
-- WHERE scene_code = 'RERANK' AND tenant_id = <当前租户> AND deleted = 0;
-- 预期：由租户管理员自行创建的绑定；平台租户 0 下应无记录。


-- ============================================================
-- 08/32 nl2sql-datasource.sql
-- ============================================================
/*
 ai 平台 —— 管理端「数据源管理」数据模型（M3）
 Target Server Type : PostgreSQL
 版本               : 2026-11-13

 本期范围（对应功能清单 M3 的 13 个功能点）：
   连接配置与驱动 / 连通性测试 / 只读账号与连接隔离 /
   Schema-表-字段元数据同步（手动 + 定时）/ 表与字段中文名、业务描述、样例值 /
   枚举值与数据字典映射 / 表关系与 Join 路径配置 / 敏感字段标记 /
   表与字段可见性开关 / SQL 方言与标识符规则 / 连接池与查询超时 /
   数据预览与采样 / 结构变更检测

 表清单（6 张）：
   ai_datasource                  数据源主表：双连接、方言、SQL 规则、限流、健康状态
   ai_datasource_table            表 / 视图元数据：中文名、业务描述、可见性、采样、结构指纹
   ai_datasource_column           字段元数据：语义角色、指标口径、样例值、同义词、敏感标记
   ai_datasource_column_enum      枚举值与数据字典映射
   ai_datasource_table_relation   表关系 / Join 路径
   ai_datasource_sync_log         同步、检测、采样任务日志与结构变更明细

 与既有模块 / 待建模块的挂接点（逻辑外键，不建物理约束）：
   ai_prompt_variable.source_key = 'datasource.dialect'   -> ai_datasource.dialect
   ai_prompt_variable.source_key = 'metadata.schema'      -> ai_datasource_table / ai_datasource_column
   ai_prompt_variable.source_key = 'metadata.metric_def'  -> ai_datasource_column.metric_def
   ai_prompt_scene.SCHEMA_RECALL                          -> 消费本模块元数据（table_candidates）
   待建 M4  ai_topic.datasource_id                        -> ai_datasource.id
   待建 M4  ai_topic_table.table_id                       -> ai_datasource_table.id
   待建 M6  行列级授权 table_id / column_id                -> ai_datasource_table / ai_datasource_column.id

 约定（与 nl2sql-model.sql、nl2sql-prompt.sql、ruoyi-vue-pro.sql 保持一致）：
 1) 表前缀 ai_；int8 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不建数据库序列。
 2) BaseDO 五字段（creator/create_time/updater/update_time/deleted）+ tenant_id int8 DEFAULT 0。
    ！！本轮已确认（2026-11-14）：数据源连接、4 张元数据表、同步日志共 6 张表【全部租户私有，无平台共享】。
      6 张表一律不登记 yudao.tenant.ignore-tables，也不加 @TenantIgnore，
      继续由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户。
      不使用 tenant_id IN (当前租户, 0) 共享查询：元数据描述的是某个具体业务库，
      不同租户同名表的字段/口径/枚举/Join 可能完全不同，共享会生成错误 SQL 并泄露样例数据。
      写入时必须显式 setTenantId(当前租户)（实体为普通 POJO，框架不会自动填充），
      并按 datasource_id 校验归属，防止给别人的数据源写元数据。
      tenant_id = 0 的初始化数据仅作模板/演示，不参与任何业务读取（拦截器天然查不到）。
 3) 不建物理外键，关联由业务层维护；状态 / 枚举 / 布尔开关统一 int2。
 4) 唯一约束用 PostgreSQL 部分索引 WHERE deleted = 0；迁移到 MySQL 需改为业务层校验。
 5) password / readonly_password 存密文，接口层禁止回显；初始化数据一律留空，由管理端录入。
 6) pool_validation_sql 默认 'SELECT 1'，不要用 'SELECT 1 FROM DUAL'：
    PostgreSQL 没有 dual 伪表（application-local.yaml:41 的历史问题）。
 7) readonly_enabled = 1 时，用户 SQL 一律走只读连接；管理连接仅用于元数据同步与连通性检测。

 ！！本脚本含 DROP TABLE IF EXISTS，只能对空库 / 测试库执行，已有数据请先备份。
 ！！导入时 search_path 必须与 application-local.yaml:52 的 currentSchema=data_query 一致，
    否则表落到 public，应用看不到（即此前 dual 同类问题）：
 PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres -f sql/postgresql/nl2sql-datasource.sql
*/


-- ----------------------------
-- 1. 数据源主表（连接、方言、只读隔离、限流、健康状态）
-- ----------------------------
DROP TABLE IF EXISTS ai_datasource;
CREATE TABLE ai_datasource (
    id int8 NOT NULL,
  name varchar(100) NOT NULL DEFAULT '',
  code varchar(64) NOT NULL DEFAULT '',
  ds_type int2 NOT NULL DEFAULT 1,
  dialect varchar(32) NOT NULL DEFAULT 'postgresql',
  driver_class varchar(255) NOT NULL DEFAULT '',
  jdbc_url varchar(1024) NOT NULL DEFAULT '',
  username varchar(128) NOT NULL DEFAULT '',
  password varchar(1024) NOT NULL DEFAULT '',
  readonly_url varchar(1024) NOT NULL DEFAULT '',
  readonly_username varchar(128) NOT NULL DEFAULT '',
  readonly_password varchar(1024) NOT NULL DEFAULT '',
  readonly_enabled int2 NOT NULL DEFAULT 1,
  schema_name varchar(128) NOT NULL DEFAULT '',
  catalog_name varchar(128) NOT NULL DEFAULT '',
  identifier_quote varchar(16) NOT NULL DEFAULT '"',
  quote_all int2 NOT NULL DEFAULT 0,
  sql_rules text NULL,
  max_rows int4 NOT NULL DEFAULT 10000,
  max_scan_rows int8 NOT NULL DEFAULT 0,
  query_timeout_ms int4 NOT NULL DEFAULT 30000,
  pool_min_idle int4 NOT NULL DEFAULT 1,
  pool_max_active int4 NOT NULL DEFAULT 20,
  pool_validation_sql varchar(255) NOT NULL DEFAULT 'SELECT 1',
  allow_ddl int2 NOT NULL DEFAULT 0,
  allowed_schemas text NULL,
  table_filter text NULL,
  sync_cron varchar(64) NOT NULL DEFAULT '',
  sync_status int2 NOT NULL DEFAULT 0,
  last_sync_time timestamp NULL,
  struct_version int4 NOT NULL DEFAULT 0,
  health_status int2 NOT NULL DEFAULT 0,
  last_check_time timestamp NULL,
  last_check_result varchar(1000) NOT NULL DEFAULT '',
  status int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_datasource ADD CONSTRAINT pk_ai_datasource PRIMARY KEY (id);
CREATE INDEX idx_ai_datasource_01 ON ai_datasource (tenant_id, status);
CREATE INDEX idx_ai_datasource_02 ON ai_datasource (ds_type, dialect);
CREATE UNIQUE INDEX uk_ai_datasource_01 ON ai_datasource (tenant_id, code) WHERE deleted = 0;

COMMENT ON TABLE ai_datasource IS '数据源主表（连接配置与查询策略）';
COMMENT ON COLUMN ai_datasource.id IS '主键';
COMMENT ON COLUMN ai_datasource.name IS '数据源显示名称';
COMMENT ON COLUMN ai_datasource.code IS '数据源编码，租户内唯一，供主题与程序引用';
COMMENT ON COLUMN ai_datasource.ds_type IS '数据库类型：1 MySQL、2 PostgreSQL、3 Oracle、4 SQLServer、5 达梦、6 人大金仓、7 openGauss、8 ClickHouse、9 Hive';
COMMENT ON COLUMN ai_datasource.dialect IS 'SQL 方言：postgresql / mysql / oracle / sqlserver / dm / clickhouse；供 ai_prompt_variable.source_key=datasource.dialect 注入';
COMMENT ON COLUMN ai_datasource.driver_class IS 'JDBC 驱动类名';
COMMENT ON COLUMN ai_datasource.jdbc_url IS '管理连接 URL，仅用于元数据同步、连通性检测与数据预览';
COMMENT ON COLUMN ai_datasource.username IS '管理连接账号';
COMMENT ON COLUMN ai_datasource.password IS '管理连接密码（加密存储，接口不回显）';
COMMENT ON COLUMN ai_datasource.readonly_url IS '只读查询连接 URL，为空表示复用 jdbc_url（不推荐）';
COMMENT ON COLUMN ai_datasource.readonly_username IS '只读连接账号，应为仅授予 SELECT 权限的数据库用户';
COMMENT ON COLUMN ai_datasource.readonly_password IS '只读连接密码（加密存储，接口不回显）';
COMMENT ON COLUMN ai_datasource.readonly_enabled IS '是否强制用户 SQL 走只读连接：0 否、1 是；生产环境必须为 1';
COMMENT ON COLUMN ai_datasource.schema_name IS '默认 schema / database，元数据同步的起始范围';
COMMENT ON COLUMN ai_datasource.catalog_name IS '默认 catalog，部分数据库（MySQL / SQLServer）需要';
COMMENT ON COLUMN ai_datasource.identifier_quote IS '标识符引号：PG 双引号、MySQL 反引号、SQLServer 方括号';
COMMENT ON COLUMN ai_datasource.quote_all IS '生成 SQL 时是否为所有标识符加引号：0 否、1 是';
COMMENT ON COLUMN ai_datasource.sql_rules IS 'SQL 安全规则 JSON：deny_keywords / allowed_functions / deny_functions / require_limit / max_joins';
COMMENT ON COLUMN ai_datasource.max_rows IS '单次查询最大返回行数，超限强制 LIMIT 或截断';
COMMENT ON COLUMN ai_datasource.max_scan_rows IS '单次查询最大扫描行数，0 表示不限（依赖 Explain 预估）';
COMMENT ON COLUMN ai_datasource.query_timeout_ms IS '查询超时（毫秒），超时由 JDBC cancel 中止';
COMMENT ON COLUMN ai_datasource.pool_min_idle IS '只读连接池最小空闲连接数';
COMMENT ON COLUMN ai_datasource.pool_max_active IS '只读连接池最大活跃连接数';
COMMENT ON COLUMN ai_datasource.pool_validation_sql IS '连接有效性校验 SQL，默认 SELECT 1，禁止 SELECT 1 FROM DUAL';
COMMENT ON COLUMN ai_datasource.allow_ddl IS '是否允许该数据源执行 DDL：0 否、1 是；元数据同步只读，恒为 0';
COMMENT ON COLUMN ai_datasource.allowed_schemas IS '允许同步的 schema 白名单，JSON 数组，空表示全部';
COMMENT ON COLUMN ai_datasource.table_filter IS '表过滤规则 JSON：include / exclude 正则数组，用于排除系统表与临时表';
COMMENT ON COLUMN ai_datasource.sync_cron IS '定时元数据同步 Quartz cron，空表示不自动同步';
COMMENT ON COLUMN ai_datasource.sync_status IS '同步状态：0 未同步、1 同步中、2 成功、3 失败';
COMMENT ON COLUMN ai_datasource.last_sync_time IS '最近一次元数据同步完成时间';
COMMENT ON COLUMN ai_datasource.struct_version IS '结构版本号，每次检测到表结构变更自增';
COMMENT ON COLUMN ai_datasource.health_status IS '健康状态：0 未检测、1 健康、2 异常';
COMMENT ON COLUMN ai_datasource.last_check_time IS '最近一次连通性检测时间';
COMMENT ON COLUMN ai_datasource.last_check_result IS '最近一次连通性检测结果或错误摘要';
COMMENT ON COLUMN ai_datasource.status IS '状态：0 启用、1 停用、2 维护中';
COMMENT ON COLUMN ai_datasource.sort IS '排序';
COMMENT ON COLUMN ai_datasource.remark IS '备注';
COMMENT ON COLUMN ai_datasource.tenant_id IS '租户编号；数据源连接配置租户私有，0 仅为模板/演示数据，不参与业务读取';

-- ----------------------------
-- 2. 表 / 视图元数据（Schema 召回的主体）
-- ----------------------------
DROP TABLE IF EXISTS ai_datasource_table;
CREATE TABLE ai_datasource_table (
    id int8 NOT NULL,
  datasource_id int8 NOT NULL DEFAULT 0,
  schema_name varchar(128) NOT NULL DEFAULT '',
  table_name varchar(128) NOT NULL DEFAULT '',
  table_type int2 NOT NULL DEFAULT 1,
  name_zh varchar(200) NOT NULL DEFAULT '',
  description varchar(1000) NOT NULL DEFAULT '',
  table_role int2 NOT NULL DEFAULT 5,
  primary_key text NULL,
  default_time_column varchar(128) NOT NULL DEFAULT '',
  row_count_estimate int8 NOT NULL DEFAULT 0,
  column_count int4 NOT NULL DEFAULT 0,
  sample_rows text NULL,
  sample_time timestamp NULL,
  visible int2 NOT NULL DEFAULT 1,
  queryable int2 NOT NULL DEFAULT 1,
  sensitive_level int2 NOT NULL DEFAULT 0,
  sync_status int2 NOT NULL DEFAULT 1,
  struct_hash varchar(64) NOT NULL DEFAULT '',
  last_sync_time timestamp NULL,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_datasource_table ADD CONSTRAINT pk_ai_datasource_table PRIMARY KEY (id);
CREATE INDEX idx_ai_datasource_table_01 ON ai_datasource_table (tenant_id, datasource_id, visible, queryable);
CREATE INDEX idx_ai_datasource_table_02 ON ai_datasource_table (datasource_id, sync_status);
CREATE INDEX idx_ai_datasource_table_03 ON ai_datasource_table (table_name);
CREATE UNIQUE INDEX uk_ai_datasource_table_01 ON ai_datasource_table (tenant_id, datasource_id, schema_name, table_name) WHERE deleted = 0;

COMMENT ON TABLE ai_datasource_table IS '数据源表 / 视图元数据表';
COMMENT ON COLUMN ai_datasource_table.id IS '主键';
COMMENT ON COLUMN ai_datasource_table.datasource_id IS '关联 ai_datasource.id';
COMMENT ON COLUMN ai_datasource_table.schema_name IS '所属 schema，冗余存储以支持同一数据源多 schema';
COMMENT ON COLUMN ai_datasource_table.table_name IS '物理表名';
COMMENT ON COLUMN ai_datasource_table.table_type IS '对象类型：1 表、2 视图、3 物化视图、4 外部表';
COMMENT ON COLUMN ai_datasource_table.name_zh IS '业务中文名，喂给 LLM 的 Schema 描述';
COMMENT ON COLUMN ai_datasource_table.description IS '业务描述与统计注意事项，Schema 召回时注入提示词';
COMMENT ON COLUMN ai_datasource_table.table_role IS '表角色：1 事实表、2 维表、3 桥表、4 字典/配置表、5 其它';
COMMENT ON COLUMN ai_datasource_table.primary_key IS '主键列名，JSON 数组';
COMMENT ON COLUMN ai_datasource_table.default_time_column IS '默认时间字段，用于「本月 / 近 7 天」等时间口径解析';
COMMENT ON COLUMN ai_datasource_table.row_count_estimate IS '行数估算，用于 Explain 成本预估与 SQL 生成提示';
COMMENT ON COLUMN ai_datasource_table.column_count IS '字段数，元数据同步写入';
COMMENT ON COLUMN ai_datasource_table.sample_rows IS '数据预览采样结果快照 JSON（已脱敏，最多 N 行）';
COMMENT ON COLUMN ai_datasource_table.sample_time IS '最近一次采样时间';
COMMENT ON COLUMN ai_datasource_table.visible IS '管理端是否可见：0 否、1 是';
COMMENT ON COLUMN ai_datasource_table.queryable IS '是否允许 LLM 生成 SQL 时引用：0 否、1 是；静态校验的表白名单';
COMMENT ON COLUMN ai_datasource_table.sensitive_level IS '整表敏感级别：0 无、1 内部、2 敏感、3 高敏';
COMMENT ON COLUMN ai_datasource_table.sync_status IS '同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除（失效）';
COMMENT ON COLUMN ai_datasource_table.struct_hash IS '结构指纹（列名+类型+约束的哈希），用于结构变更检测';
COMMENT ON COLUMN ai_datasource_table.last_sync_time IS '最近一次该表的同步时间';
COMMENT ON COLUMN ai_datasource_table.sort IS 'Schema 召回时的排序权重';
COMMENT ON COLUMN ai_datasource_table.remark IS '备注';
COMMENT ON COLUMN ai_datasource_table.tenant_id IS '租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致';

-- ----------------------------
-- 3. 字段元数据（列裁剪、样例值、口径、敏感标记）
-- ----------------------------
DROP TABLE IF EXISTS ai_datasource_column;
CREATE TABLE ai_datasource_column (
    id int8 NOT NULL,
  datasource_id int8 NOT NULL DEFAULT 0,
  table_id int8 NOT NULL DEFAULT 0,
  schema_name varchar(128) NOT NULL DEFAULT '',
  table_name varchar(128) NOT NULL DEFAULT '',
  column_name varchar(128) NOT NULL DEFAULT '',
  name_zh varchar(200) NOT NULL DEFAULT '',
  description varchar(1000) NOT NULL DEFAULT '',
  data_type varchar(64) NOT NULL DEFAULT '',
  jdbc_type varchar(64) NOT NULL DEFAULT '',
  column_size int4 NOT NULL DEFAULT 0,
  nullable int2 NOT NULL DEFAULT 1,
  primary_key int2 NOT NULL DEFAULT 0,
  auto_increment int2 NOT NULL DEFAULT 0,
  default_value varchar(500) NOT NULL DEFAULT '',
  ordinal_position int4 NOT NULL DEFAULT 0,
  semantic_role int2 NOT NULL DEFAULT 6,
  aggregation varchar(32) NOT NULL DEFAULT '',
  unit varchar(32) NOT NULL DEFAULT '',
  time_format varchar(64) NOT NULL DEFAULT '',
  is_default_time int2 NOT NULL DEFAULT 0,
  metric_def text NULL,
  synonyms text NULL,
  sample_values text NULL,
  distinct_count_estimate int8 NOT NULL DEFAULT 0,
  enum_enabled int2 NOT NULL DEFAULT 0,
  sensitive_type int2 NOT NULL DEFAULT 0,
  mask_rule varchar(128) NOT NULL DEFAULT '',
  visible int2 NOT NULL DEFAULT 1,
  queryable int2 NOT NULL DEFAULT 1,
  filterable int2 NOT NULL DEFAULT 1,
  sync_status int2 NOT NULL DEFAULT 1,
  struct_hash varchar(64) NOT NULL DEFAULT '',
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_datasource_column ADD CONSTRAINT pk_ai_datasource_column PRIMARY KEY (id);
CREATE INDEX idx_ai_datasource_column_01 ON ai_datasource_column (tenant_id, table_id, queryable);
CREATE INDEX idx_ai_datasource_column_02 ON ai_datasource_column (datasource_id, column_name);
CREATE INDEX idx_ai_datasource_column_03 ON ai_datasource_column (semantic_role, queryable);
CREATE INDEX idx_ai_datasource_column_04 ON ai_datasource_column (sync_status);
CREATE UNIQUE INDEX uk_ai_datasource_column_01 ON ai_datasource_column (tenant_id, table_id, column_name) WHERE deleted = 0;

COMMENT ON TABLE ai_datasource_column IS '数据源字段元数据表';
COMMENT ON COLUMN ai_datasource_column.id IS '主键';
COMMENT ON COLUMN ai_datasource_column.datasource_id IS '关联 ai_datasource.id，冗余便于按数据源检索';
COMMENT ON COLUMN ai_datasource_column.table_id IS '关联 ai_datasource_table.id';
COMMENT ON COLUMN ai_datasource_column.schema_name IS '所属 schema，冗余';
COMMENT ON COLUMN ai_datasource_column.table_name IS '物理表名，冗余便于拼接 SQL';
COMMENT ON COLUMN ai_datasource_column.column_name IS '物理列名';
COMMENT ON COLUMN ai_datasource_column.name_zh IS '字段业务中文名，口径解释与列裁剪用';
COMMENT ON COLUMN ai_datasource_column.description IS '字段业务描述，喂给 LLM';
COMMENT ON COLUMN ai_datasource_column.data_type IS '数据库原始类型，如 numeric(12,2) / varchar(64)';
COMMENT ON COLUMN ai_datasource_column.jdbc_type IS 'JDBC 类型名，用于结果集解析';
COMMENT ON COLUMN ai_datasource_column.column_size IS '长度 / 精度';
COMMENT ON COLUMN ai_datasource_column.nullable IS '是否允许为空：0 否、1 是';
COMMENT ON COLUMN ai_datasource_column.primary_key IS '是否主键列：0 否、1 是';
COMMENT ON COLUMN ai_datasource_column.auto_increment IS '是否自增：0 否、1 是';
COMMENT ON COLUMN ai_datasource_column.default_value IS '数据库默认值';
COMMENT ON COLUMN ai_datasource_column.ordinal_position IS '字段在表中的物理顺序';
COMMENT ON COLUMN ai_datasource_column.semantic_role IS '语义角色：1 维度、2 指标、3 时间、4 主键、5 外键、6 标识/无关、7 敏感';
COMMENT ON COLUMN ai_datasource_column.aggregation IS '默认聚合方式：SUM / COUNT / AVG / MAX / MIN / NONE';
COMMENT ON COLUMN ai_datasource_column.unit IS '业务单位：元 / 人 / 次 / % / 件';
COMMENT ON COLUMN ai_datasource_column.time_format IS '时间格式：yyyy-MM-dd / yyyy-MM-dd HH:mm:ss / unix_ms';
COMMENT ON COLUMN ai_datasource_column.is_default_time IS '是否为该表默认时间字段：0 否、1 是';
COMMENT ON COLUMN ai_datasource_column.metric_def IS '指标口径定义（计算逻辑 + 过滤条件），供 ai_prompt_variable.source_key=metadata.metric_def 注入口径解释';
COMMENT ON COLUMN ai_datasource_column.synonyms IS '业务术语与同义词 JSON 数组，供问题改写与召回归一';
COMMENT ON COLUMN ai_datasource_column.sample_values IS '样例值 JSON 数组（已脱敏），供 LLM 判断枚举与取值范围';
COMMENT ON COLUMN ai_datasource_column.distinct_count_estimate IS '去重值数量估算，用于判断是否枚举列';
COMMENT ON COLUMN ai_datasource_column.enum_enabled IS '是否维护枚举映射：0 否、1 是，指向 ai_datasource_column_enum';
COMMENT ON COLUMN ai_datasource_column.sensitive_type IS '敏感类型：0 无、1 手机号、2 身份证、3 姓名、4 邮箱、5 银行卡、6 金额、9 自定义';
COMMENT ON COLUMN ai_datasource_column.mask_rule IS '脱敏规则：phone / idcard / partial / hash / forbid；forbid 表示该列禁止出现在结果中';
COMMENT ON COLUMN ai_datasource_column.visible IS '管理端是否可见：0 否、1 是';
COMMENT ON COLUMN ai_datasource_column.queryable IS '是否允许被 LLM 选中：0 否、1 是；静态校验的列白名单，越权列的最后一道防线';
COMMENT ON COLUMN ai_datasource_column.filterable IS '是否允许作为 WHERE 条件：0 否、1 是';
COMMENT ON COLUMN ai_datasource_column.sync_status IS '同步状态：1 新发现、2 已同步、3 结构已变更、4 源端已删除';
COMMENT ON COLUMN ai_datasource_column.struct_hash IS '字段结构指纹，用于结构变更检测';
COMMENT ON COLUMN ai_datasource_column.sort IS 'Schema 召回时的列裁剪优先级';
COMMENT ON COLUMN ai_datasource_column.remark IS '备注';
COMMENT ON COLUMN ai_datasource_column.tenant_id IS '租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致';

-- ----------------------------
-- 4. 枚举值与数据字典映射
-- ----------------------------
DROP TABLE IF EXISTS ai_datasource_column_enum;
CREATE TABLE ai_datasource_column_enum (
    id int8 NOT NULL,
  datasource_id int8 NOT NULL DEFAULT 0,
  table_id int8 NOT NULL DEFAULT 0,
  column_id int8 NOT NULL DEFAULT 0,
  source_value varchar(255) NOT NULL DEFAULT '',
  display_name varchar(255) NOT NULL DEFAULT '',
  dict_type varchar(128) NOT NULL DEFAULT '',
  dict_value varchar(128) NOT NULL DEFAULT '',
  description varchar(500) NOT NULL DEFAULT '',
  source int2 NOT NULL DEFAULT 1,
  sample_count int8 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_datasource_column_enum ADD CONSTRAINT pk_ai_datasource_column_enum PRIMARY KEY (id);
CREATE INDEX idx_ai_datasource_column_enum_01 ON ai_datasource_column_enum (tenant_id, column_id, status);
CREATE INDEX idx_ai_datasource_column_enum_02 ON ai_datasource_column_enum (dict_type);
CREATE UNIQUE INDEX uk_ai_datasource_column_enum_01 ON ai_datasource_column_enum (tenant_id, column_id, source_value) WHERE deleted = 0;

COMMENT ON TABLE ai_datasource_column_enum IS '字段枚举值与数据字典映射表';
COMMENT ON COLUMN ai_datasource_column_enum.id IS '主键';
COMMENT ON COLUMN ai_datasource_column_enum.datasource_id IS '关联 ai_datasource.id';
COMMENT ON COLUMN ai_datasource_column_enum.table_id IS '关联 ai_datasource_table.id';
COMMENT ON COLUMN ai_datasource_column_enum.column_id IS '关联 ai_datasource_column.id';
COMMENT ON COLUMN ai_datasource_column_enum.source_value IS '数据库中存储的原值';
COMMENT ON COLUMN ai_datasource_column_enum.display_name IS '业务显示名，拼入提示词让 LLM 理解「1 = 已支付」';
COMMENT ON COLUMN ai_datasource_column_enum.dict_type IS '关联 system_dict_type.dict_type，空表示纯本地映射';
COMMENT ON COLUMN ai_datasource_column_enum.dict_value IS '关联 system_dict_data.value';
COMMENT ON COLUMN ai_datasource_column_enum.description IS '该枚举值的业务说明';
COMMENT ON COLUMN ai_datasource_column_enum.source IS '来源：1 采样自动识别、2 手工录入、3 数据字典同步';
COMMENT ON COLUMN ai_datasource_column_enum.sample_count IS '采样中该值出现次数，用于判断有效性';
COMMENT ON COLUMN ai_datasource_column_enum.status IS '状态：0 启用、1 停用';
COMMENT ON COLUMN ai_datasource_column_enum.sort IS '排序';
COMMENT ON COLUMN ai_datasource_column_enum.remark IS '备注';
COMMENT ON COLUMN ai_datasource_column_enum.tenant_id IS '租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致';

-- ----------------------------
-- 5. 表关系 / Join 路径
-- ----------------------------
DROP TABLE IF EXISTS ai_datasource_table_relation;
CREATE TABLE ai_datasource_table_relation (
    id int8 NOT NULL,
  datasource_id int8 NOT NULL DEFAULT 0,
  left_table_id int8 NOT NULL DEFAULT 0,
  right_table_id int8 NOT NULL DEFAULT 0,
  left_table_name varchar(128) NOT NULL DEFAULT '',
  right_table_name varchar(128) NOT NULL DEFAULT '',
  join_type int2 NOT NULL DEFAULT 2,
  join_pairs text NOT NULL DEFAULT '[]',
  join_condition varchar(1000) NOT NULL DEFAULT '',
  cardinality int2 NOT NULL DEFAULT 2,
  cost int4 NOT NULL DEFAULT 100,
  description varchar(500) NOT NULL DEFAULT '',
  source int2 NOT NULL DEFAULT 2,
  verified int2 NOT NULL DEFAULT 0,
  enabled int2 NOT NULL DEFAULT 1,
  status int2 NOT NULL DEFAULT 0,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_datasource_table_relation ADD CONSTRAINT pk_ai_datasource_table_relation PRIMARY KEY (id);
CREATE INDEX idx_ai_datasource_table_relation_01 ON ai_datasource_table_relation (tenant_id, datasource_id, enabled);
CREATE INDEX idx_ai_datasource_table_relation_02 ON ai_datasource_table_relation (datasource_id, left_table_id, right_table_id);
CREATE UNIQUE INDEX uk_ai_datasource_table_relation_01 ON ai_datasource_table_relation (tenant_id, left_table_id, right_table_id, md5(join_pairs)) WHERE deleted = 0;

COMMENT ON TABLE ai_datasource_table_relation IS '表关系 / Join 路径配置表';
COMMENT ON COLUMN ai_datasource_table_relation.id IS '主键';
COMMENT ON COLUMN ai_datasource_table_relation.datasource_id IS '关联 ai_datasource.id；Join 路径不跨数据源';
COMMENT ON COLUMN ai_datasource_table_relation.left_table_id IS '左表，关联 ai_datasource_table.id';
COMMENT ON COLUMN ai_datasource_table_relation.right_table_id IS '右表，关联 ai_datasource_table.id';
COMMENT ON COLUMN ai_datasource_table_relation.left_table_name IS '左表物理名，冗余便于生成 SQL';
COMMENT ON COLUMN ai_datasource_table_relation.right_table_name IS '右表物理名，冗余便于生成 SQL';
COMMENT ON COLUMN ai_datasource_table_relation.join_type IS '连接类型：1 INNER、2 LEFT、3 RIGHT、4 FULL、5 CROSS';
COMMENT ON COLUMN ai_datasource_table_relation.join_pairs IS '关联字段对 JSON 数组，如 [{"left":"customer_id","right":"id"}]';
COMMENT ON COLUMN ai_datasource_table_relation.join_condition IS '额外 ON 条件表达式，必须经静态校验，禁止子查询与函数注入';
COMMENT ON COLUMN ai_datasource_table_relation.cardinality IS '基数：1 一对一、2 一对多、3 多对一、4 多对多';
COMMENT ON COLUMN ai_datasource_table_relation.cost IS '路径代价 / 优先级，数值越小越优先推荐给 LLM';
COMMENT ON COLUMN ai_datasource_table_relation.description IS '关系业务说明，如「订单归属客户」';
COMMENT ON COLUMN ai_datasource_table_relation.source IS '来源：1 外键自动推断、2 手工配置、3 LLM 建议后人工审核';
COMMENT ON COLUMN ai_datasource_table_relation.verified IS '是否已用真实 SQL 验证可执行：0 否、1 是';
COMMENT ON COLUMN ai_datasource_table_relation.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN ai_datasource_table_relation.status IS '状态：0 正常、1 停用';
COMMENT ON COLUMN ai_datasource_table_relation.sort IS '排序';
COMMENT ON COLUMN ai_datasource_table_relation.remark IS '备注';
COMMENT ON COLUMN ai_datasource_table_relation.tenant_id IS '租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致';

-- ----------------------------
-- 6. 同步 / 检测 / 采样任务日志与结构变更明细
-- ----------------------------
DROP TABLE IF EXISTS ai_datasource_sync_log;
CREATE TABLE ai_datasource_sync_log (
    id int8 NOT NULL,
  datasource_id int8 NOT NULL DEFAULT 0,
  task_no varchar(64) NOT NULL DEFAULT '',
  sync_type int2 NOT NULL DEFAULT 2,
  trigger_type int2 NOT NULL DEFAULT 1,
  scope varchar(500) NOT NULL DEFAULT '',
  start_time timestamp NULL,
  end_time timestamp NULL,
  latency_ms int4 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  table_added int4 NOT NULL DEFAULT 0,
  table_changed int4 NOT NULL DEFAULT 0,
  table_removed int4 NOT NULL DEFAULT 0,
  column_added int4 NOT NULL DEFAULT 0,
  column_changed int4 NOT NULL DEFAULT 0,
  column_removed int4 NOT NULL DEFAULT 0,
  relation_added int4 NOT NULL DEFAULT 0,
  enum_added int4 NOT NULL DEFAULT 0,
  change_detail text NULL,
  struct_version_before int4 NOT NULL DEFAULT 0,
  struct_version_after int4 NOT NULL DEFAULT 0,
  error_code varchar(64) NOT NULL DEFAULT '',
  error_msg varchar(2000) NOT NULL DEFAULT '',
  operator_id int8 NOT NULL DEFAULT 0,
  operator_name varchar(64) NOT NULL DEFAULT '',
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

ALTER TABLE ai_datasource_sync_log ADD CONSTRAINT pk_ai_datasource_sync_log PRIMARY KEY (id);
CREATE INDEX idx_ai_datasource_sync_log_01 ON ai_datasource_sync_log (tenant_id, datasource_id, start_time);
CREATE INDEX idx_ai_datasource_sync_log_02 ON ai_datasource_sync_log (sync_type, status);
CREATE INDEX idx_ai_datasource_sync_log_03 ON ai_datasource_sync_log (task_no);

COMMENT ON TABLE ai_datasource_sync_log IS '数据源同步 / 连通性检测 / 采样任务日志表';
COMMENT ON COLUMN ai_datasource_sync_log.id IS '主键';
COMMENT ON COLUMN ai_datasource_sync_log.datasource_id IS '关联 ai_datasource.id';
COMMENT ON COLUMN ai_datasource_sync_log.task_no IS '任务批次号，同一批次的多条明细共用';
COMMENT ON COLUMN ai_datasource_sync_log.sync_type IS '任务类型：1 连通性检测、2 全量元数据同步、3 增量元数据同步、4 结构变更检测、5 数据预览采样、6 枚举值采集';
COMMENT ON COLUMN ai_datasource_sync_log.trigger_type IS '触发方式：1 手动、2 定时、3 创建后自动、4 结构变更告警';
COMMENT ON COLUMN ai_datasource_sync_log.scope IS '本次任务范围：schema / 表清单';
COMMENT ON COLUMN ai_datasource_sync_log.start_time IS '开始时间';
COMMENT ON COLUMN ai_datasource_sync_log.end_time IS '结束时间';
COMMENT ON COLUMN ai_datasource_sync_log.latency_ms IS '耗时（毫秒）';
COMMENT ON COLUMN ai_datasource_sync_log.status IS '状态：0 进行中、1 成功、2 部分成功、3 失败';
COMMENT ON COLUMN ai_datasource_sync_log.table_added IS '本次新增表数';
COMMENT ON COLUMN ai_datasource_sync_log.table_changed IS '本次结构变更表数';
COMMENT ON COLUMN ai_datasource_sync_log.table_removed IS '本次源端删除表数';
COMMENT ON COLUMN ai_datasource_sync_log.column_added IS '本次新增字段数';
COMMENT ON COLUMN ai_datasource_sync_log.column_changed IS '本次变更字段数';
COMMENT ON COLUMN ai_datasource_sync_log.column_removed IS '本次删除字段数';
COMMENT ON COLUMN ai_datasource_sync_log.relation_added IS '本次新增 Join 路径数';
COMMENT ON COLUMN ai_datasource_sync_log.enum_added IS '本次新增枚举映射数';
COMMENT ON COLUMN ai_datasource_sync_log.change_detail IS '结构变更明细 JSON：表名/字段名/变更前后类型，管理端变更告警的数据来源';
COMMENT ON COLUMN ai_datasource_sync_log.struct_version_before IS '任务前结构版本号';
COMMENT ON COLUMN ai_datasource_sync_log.struct_version_after IS '任务后结构版本号';
COMMENT ON COLUMN ai_datasource_sync_log.error_code IS '错误码';
COMMENT ON COLUMN ai_datasource_sync_log.error_msg IS '错误摘要，禁止记录完整密码与连接串';
COMMENT ON COLUMN ai_datasource_sync_log.operator_id IS '操作人用户 ID，定时任务为 0';
COMMENT ON COLUMN ai_datasource_sync_log.operator_name IS '操作人名称';
COMMENT ON COLUMN ai_datasource_sync_log.tenant_id IS '租户私有，按 datasource_id 归属，必须与所属数据源的 tenant_id 一致';

-- ----------------------------
-- 初始化数据（连接密码一律留空；示例元数据用于演示，非真实表）
--
-- ！！本轮口径：数据源侧全部租户私有，以下 tenant_id = 0 的行【仅作模板/演示】，
--    业务查询时会被拦截器追加 tenant_id = 当前租户，天然读不到，平台也不对外共享。
--    生产环境请用管理端由各租户自行创建数据源并同步元数据；若要彻底隐藏，
--    可执行 sql/postgresql/nl2sql-tenant-isolation.sql 中可选的 deleted = 1 语句。
-- ----------------------------
-- @formatter:off
BEGIN;

INSERT INTO ai_datasource (id, name, code, ds_type, dialect, driver_class, jdbc_url, username, schema_name, readonly_enabled, identifier_quote, sql_rules, max_rows, query_timeout_ms, pool_validation_sql, allowed_schemas, sync_cron, health_status, status, sort, remark, tenant_id, creator, updater) VALUES
(1, '本地业务库（PostgreSQL 只读）', 'pg_biz_local', 2, 'postgresql', 'org.postgresql.Driver', 'jdbc:postgresql://localhost:5432/postgres?currentSchema=data_query', 'postgres', 'data_query', 1, '"', '{"deny_keywords":["INSERT","UPDATE","DELETE","DROP","ALTER","TRUNCATE","GRANT"],"require_limit":true,"max_joins":5}', 10000, 30000, 'SELECT 1', '["data_query"]', '0 0 3 * * ?', 0, 0, 1, '示例：只读账号与密码由管理端补齐并加密存储', 0, '1', '1'),
(2, '示例 MySQL 数据源', 'mysql_demo', 1, 'mysql', 'com.mysql.cj.jdbc.Driver', 'jdbc:mysql://localhost:3306/ruoyi-vue-pro?useSSL=false&serverTimezone=Asia/Shanghai', 'root', 'ruoyi-vue-pro', 1, '`', NULL, 5000, 20000, 'SELECT 1', NULL, '', 0, 1, 2, '停用示例，仅用于演示多方言配置', 0, '1', '1');

INSERT INTO ai_datasource_table (id, datasource_id, schema_name, table_name, table_type, name_zh, description, table_role, default_time_column, primary_key, sample_rows, visible, queryable, struct_hash, sync_status, sort, remark, tenant_id, creator, updater) VALUES
(1, 1, 'data_query', 'ai_demo_order', 1, '订单主表', '每笔订单一行，金额单位为元，口径统计仅含已支付订单', 1, 'order_time', '["id"]', NULL, 1, 1, 'demo_hash_order', 2, 1, '示例元数据，非真实表', 0, '1', '1'),
(2, 1, 'data_query', 'ai_demo_customer', 1, '客户维表', '客户基础信息，与订单通过 customer_id 关联', 2, '', '["id"]', NULL, 1, 1, 'demo_hash_customer', 2, 2, NULL, 0, '1', '1'),
(3, 1, 'data_query', 'ai_demo_order_item', 1, '订单明细表', '订单下每个商品一行，与订单通过 order_id 关联', 1, '', '["id"]', NULL, 1, 1, 'demo_hash_item', 2, 3, NULL, 0, '1', '1');

-- semantic_role: 1维度 2指标 3时间 4主键 5外键 6标识/无关 7敏感
INSERT INTO ai_datasource_column (id, datasource_id, table_id, table_name, column_name, name_zh, data_type, semantic_role, aggregation, unit, is_default_time, metric_def, sensitive_type, queryable, ordinal_position, sort, tenant_id, creator, updater) VALUES
(1,  1, 1, 'ai_demo_order',       'id',            '订单ID',     'int8',          4, '',     '',  0, NULL, 0, 1, 1, 1, 0, '1', '1'),
(2,  1, 1, 'ai_demo_order',       'order_no',      '订单号',     'varchar(64)',   6, '',     '',  0, NULL, 0, 1, 2, 2, 0, '1', '1'),
(3,  1, 1, 'ai_demo_order',       'customer_id',   '客户ID',     'int8',          5, '',     '',  0, NULL, 0, 1, 3, 3, 0, '1', '1'),
(4,  1, 1, 'ai_demo_order',       'order_amount',  '订单金额',   'numeric(12,2)', 2, 'SUM', '元', 0, '已支付订单的实付金额合计，不含退款单；以财务确认口径为准', 0, 1, 4, 4, 0, '1', '1'),
(5,  1, 1, 'ai_demo_order',       'order_status',  '订单状态',   'int2',          1, '',     '',  0, NULL, 0, 1, 5, 5, 0, '1', '1'),
(6,  1, 1, 'ai_demo_order',       'order_time',    '下单时间',   'timestamp',     3, '',     '',  1, NULL, 0, 1, 6, 6, 0, '1', '1'),
(7,  1, 2, 'ai_demo_customer',    'id',            '客户ID',     'int8',          4, '',     '',  0, NULL, 0, 1, 1, 1, 0, '1', '1'),
(8,  1, 2, 'ai_demo_customer',    'customer_name', '客户名称',   'varchar(128)',  1, '',     '',  0, NULL, 3, 1, 2, 2, 0, '1', '1'),
(9,  1, 3, 'ai_demo_order_item',  'id',            '明细ID',     'int8',          4, '',     '',  0, NULL, 0, 1, 1, 1, 0, '1', '1'),
(10, 1, 3, 'ai_demo_order_item',  'order_id',      '订单ID',     'int8',          5, '',     '',  0, NULL, 0, 1, 2, 2, 0, '1', '1'),
(11, 1, 3, 'ai_demo_order_item',  'quantity',      '购买数量',   'int4',          2, 'SUM', '件', 0, NULL, 0, 1, 3, 3, 0, '1', '1');

INSERT INTO ai_datasource_column_enum (id, datasource_id, table_id, column_id, source_value, display_name, dict_type, dict_value, source, sample_count, status, sort, tenant_id, creator, updater) VALUES
(1, 1, 1, 5, '1', '待支付', 'ai_order_status', '1', 1, 120, 0, 1, 0, '1', '1'),
(2, 1, 1, 5, '2', '已支付', 'ai_order_status', '2', 1, 860, 0, 2, 0, '1', '1'),
(3, 1, 1, 5, '3', '已取消', 'ai_order_status', '3', 1, 45,  0, 3, 0, '1', '1'),
(4, 1, 1, 5, '4', '已退款', 'ai_order_status', '4', 2, 18,  0, 4, 0, '1', '1');

INSERT INTO ai_datasource_table_relation (id, datasource_id, left_table_id, right_table_id, left_table_name, right_table_name, join_type, join_pairs, cardinality, cost, source, verified, enabled, status, sort, tenant_id, creator, updater) VALUES
(1, 1, 1, 2, 'ai_demo_order',      'ai_demo_customer', 2, '[{"left":"customer_id","right":"id"}]', 3, 10, 1, 1, 1, 0, 1, 0, '1', '1'),
(2, 1, 3, 1, 'ai_demo_order_item', 'ai_demo_order',    1, '[{"left":"order_id","right":"id"}]',    2, 20, 1, 1, 1, 0, 2, 0, '1', '1');

-- sync_type: 1连通性检测 2全量同步 3增量同步 4变更检测 5采样预览 6枚举采集
INSERT INTO ai_datasource_sync_log (id, datasource_id, task_no, sync_type, trigger_type, scope, status, table_added, column_added, relation_added, struct_version_after, latency_ms, start_time, end_time, tenant_id, creator, updater) VALUES
(1, 1, 'SYNC202611130001', 1, 1, '连通性检测',        1, 0, 0,  0, 0, 128, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '1', '1'),
(2, 1, 'SYNC202611130002', 2, 1, 'data_query.ai_demo_*', 1, 3, 11, 2, 1, 860, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, '1', '1');

COMMIT;
-- @formatter:on

-- ----------------------------
-- 运行时推荐查询（应用逻辑，非本脚本执行内容）
-- ----------------------------
-- ！！检索口径已变更（2026-11-14）：Schema 主召回路径为「向量库 + Embedding + 混合检索」，
--    见 docs/intelligent-query-flow.md 第 3.1 节。下列 SQL 不再是主检索实现，仅用于：
--      a) 向量库 / Embedding 不可用时的关键词兜底召回；
--      b) 静态校验前，以 PG 元数据为准二次核验 queryable / visible（向量索引可能陈旧）；
--      c) 生成向量 Document 时的元数据取数来源。
-- 1) 关键词兜底召回 / PG 二次核验：限定数据源，拼接注入 ai_prompt_variable.source_key = 'metadata.schema' 的表结构。
--    只取可见可查的表与列，并按 sort / ordinal_position 控制裁剪顺序与上下文长度。
--    向量检索时此查询等价于 metadata 硬过滤：tenant_id + datasource_id + visible + queryable + sync_status <> 4。
-- SELECT t.table_name, t.name_zh, t.description, t.default_time_column,
--        c.column_name, c.name_zh, c.data_type, c.semantic_role, c.aggregation,
--        c.sample_values, c.metric_def, c.synonyms
--   FROM ai_datasource_table t
--   JOIN ai_datasource_column c ON c.table_id = t.id AND c.deleted = 0
--  WHERE t.datasource_id = :datasourceId AND t.deleted = 0
--    AND t.visible = 1 AND t.queryable = 1 AND t.sync_status <> 4
--    AND c.visible = 1 AND c.queryable = 1 AND c.sync_status <> 4
--  ORDER BY t.sort, t.id, c.sort, c.ordinal_position;
--
-- 2) Join 路径召回：同数据源内按 cost 取已启用且已验证的路径，注入多表 SQL 生成提示。
-- SELECT * FROM ai_datasource_table_relation
--  WHERE datasource_id = :datasourceId AND enabled = 1 AND verified = 1 AND deleted = 0
--    AND left_table_id IN (:tableIds) AND right_table_id IN (:tableIds)
--  ORDER BY cost ASC;
--
-- 3) 静态校验（SQL 生成后、执行前，不可用前端控制替代）：
--    a. 解析出所有表名，必须命中向量召回（或兜底召回）产出的白名单，并以第 1 条查询二次核验；
--    b. 解析出所有列名，必须 queryable = 1；sensitive_type <> 0 的列按 mask_rule 处理，
--       mask_rule = 'forbid' 时直接拒绝该 SQL；
--    c. 校验 ai_datasource.sql_rules 的 deny_keywords / allowed_functions / require_limit；
--    d. 强制注入租户与行列级权限条件（待建 M6），再经 readonly_url 执行，
--       并设置 query_timeout_ms 与 max_rows。
--
-- 4) 结构变更检测：每次同步用 struct_hash 比对，差异写入 ai_datasource_sync_log.change_detail，
--    并推进 ai_datasource.struct_version；变更表标记 sync_status = 3 后在管理端人工确认，
--    避免线上表加列导致 LLM 依据过期 Schema 生成错误 SQL。
--
-- 5) 向量索引同步（pgvector，见 sql/postgresql/nl2sql-vector.sql）：
--    元数据同步完成后，按 content_hash + embedding_model_code 判断是否需要重新 Embedding；
--    Embedding 账号【每租户独立】：走 ai_model_config.model_type = 2 与 EMBEDDING 场景绑定，
--    凭证来自该租户自己的 provider；经 M1 治理但用独立限流 / 熔断键，不占 Chat 配额；
--    全平台统一维度 1536 属技术约束，换模型只重建该租户向量，其他租户不受影响。
--    向量写入 ai_schema_vector（平台库 data_query，租户私有、无平台共享向量，严禁登记 ignore-tables）；
--    每行带 tenant_id / datasource_id / object_type / visible / queryable / struct_version /
--    sensitive_type / mask_rule，检索时 SQL 的 tenant_id = 当前租户 条件先行，
--    并由 TenantDatabaseInterceptor 再追加一次，禁止只按相似度跨租户召回。
--    全局 HNSW 对该过滤是 post-filter，租户占比低时 Top-K 可能返回不足；
--    一期用 B-tree 预过滤 + 精确距离计算，或 pgvector >= 0.8.0 时事务内
--    SET LOCAL hnsw.iterative_scan = relaxed_order。
--    任务类型在 ai_datasource_sync_log.sync_type 中增补；维度或 Embedding 模型变更需全量重建。


-- ============================================================
-- 09/32 nl2sql-vector.sql
-- ============================================================
/*
 ai 平台 —— 智能问数「Schema 向量索引」数据模型
 Target Server Type : PostgreSQL + pgvector 扩展
 版本               : 2026-11-14
 状态               : P1 已落地（2026-11-14 在本机 PG 18.6 + pgvector 0.8.7 执行通过）；
                     表结构与检索口径见 docs/intelligent-query-flow.md 第 3.1 节

 本机实测环境（2026-11-14，P1 落地时核对）：
   PostgreSQL 18.6；pgvector 0.8.7【已预装在 public schema，relocatable = true】；
   pg_depend 中无任何对象依赖 public.vector；ai_schema_vector 表原本不存在。
   因应用 JDBC 的 currentSchema=data_query、脚本 search_path=data_query，若扩展留在 public，
   则 vector(1536) 类型、<=> 操作符、vector_cosine_ops 都无法解析（public 不在 search_path）。
   故第 0 节增加【幂等的 ALTER EXTENSION ... SET SCHEMA data_query】把扩展对齐到业务 schema：
   迁移后应用侧 SQL 无需任何 public. 前缀，且 <=> 可被正常解析。
   代价：修改了既有扩展所属 schema（本机无其它使用者，已确认零依赖）；生产库由 DBA 评估后执行。

 本轮决策（2026-11-14 拍板）：
   1) 向量库 = 项目自有 PostgreSQL 的 pgvector 扩展，表建在【平台库 data_query schema】，
      不部署 Milvus / Qdrant / Redis 向量库，也不把向量写进租户业务数据源。
   2) Embedding 账号【每租户独立】：走 ai_model_config.model_type = 2 +
      ai_model_scene_binding(EMBEDDING)，凭证来自该租户自己的 ai_model_provider
      （AiSecretTypeHandler 解密），经 M1 治理但用独立限流 / 熔断键。
      AiTenantInitializer 已复制模板并把 Key 置 NULL 由租户录入，这套机制无需改动。
      Chat / SQL 生成模型同样为每租户独立 AI 账号。
   3) 本表是数据源元数据的【派生物】，继承「数据源侧全租户私有、无平台共享」口径：
      每行带真实 tenant_id，严禁登记 yudao.tenant.ignore-tables，
      库里不存在 tenant_id = 0 的平台共享向量。

 表清单（1 张）：
   ai_schema_vector   Schema / 术语描述的向量索引：表级、列级、术语级 Document

 与既有模块的挂接点（逻辑外键，不建物理约束）：
   ai_schema_vector.datasource_id -> ai_datasource.id
   ai_schema_vector.table_id      -> ai_datasource_table.id
   ai_schema_vector.column_id     -> ai_datasource_column.id
   ai_datasource_sync_log.sync_type 增补「向量索引构建 / 增量 / 全量重建」

 约定（与 nl2sql-model.sql / nl2sql-prompt.sql / nl2sql-datasource.sql 一致）：
 1) 表前缀 ai_；int8 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不建数据库序列。
 2) BaseDO 五字段（creator/create_time/updater/update_time/deleted）+ tenant_id int8 DEFAULT 0。
    写入时必须显式 setTenantId(当前租户)（实体为普通 POJO，框架不会自动填充），
    并按 datasource_id 校验归属，防止给别人数据源写向量。
    tenant_id = 0 仅作占位默认值，业务上禁止写入；拦截器天然查不到。
 3) 【本表一律不登记 yudao.tenant.ignore-tables，也不加 @TenantIgnore】：
    MyBatis 查询由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；
    DAO 的 SQL 里再显式写一次 tenant_id = ?，形成双保险。
 4) 不建物理外键；状态 / 枚举 / 布尔开关统一 int2；
    唯一约束用 PostgreSQL 部分索引 WHERE deleted = 0。
 5) 向量文本 content 只放元数据描述与脱敏后的 sample_values 摘要，
    禁止放 sample_rows 原始行业务数据；mask_rule = 'forbid' 的列整列不入库。
 6) embedding 维度固定 1536（text-embedding-3-small）。pgvector HNSW 索引上限 2000 维。
    换模型 / 换维度必须全量重建本表与索引，不允许不同维度向量混存。
 7) Embedding 输入是租户私有元数据描述文本，由租户自有凭证调用；若走外部 API，
    外发责任归租户并需披露，租户可指向私有化 / 内网端点。见设计文档 3.1.3。
 8) 全平台统一维度 1536（vector(1536) 列与 HNSW 索引要求固定维度），这是技术约束，
    不是账号口径；租户可换供应商 / 模型，但写入侧与查询侧必须同租户同模型，
    换模型只重建该租户向量，其他租户不受影响。

 前置条件（执行本脚本前必须确认）：
   a. PostgreSQL 已安装 pgvector 扩展文件（通常需 superuser 执行 CREATE EXTENSION，
      或由 DBA 预装）；SELECT * FROM pg_available_extensions WHERE name = 'vector';
   b. 扩展必须落在 data_query（与业务表同 schema），这样 vector 类型、<=> 操作符与
      vector_cosine_ops 都能在 search_path=data_query 下解析，应用 SQL 无需 public. 前缀。
      若扩展已预装在 public，本脚本第 0 节会自动幂等迁移（需 relocatable = true）；
      迁移失败会直接中断，此时应由 DBA 决定是迁移还是改用 public.vector(1536)
      + OPERATOR(public.<=>) + public.vector_cosine_ops 的全前缀方案。
   c. pgvector >= 0.8.0 才支持 hnsw.iterative_scan（HNSW 带租户过滤时召回不足的修复）。
      本机 0.8.7 满足；版本不足时一期走「B-tree 预过滤 + 精确距离计算」，不使用迭代扫描。
   d. 若改用 Spring AI PgVectorStore 自动配置（默认 vector_store 表 + metadata json），
      还需安装 hstore 与 uuid-ossp 扩展；本设计稿采用自建表方案，不需要它们。

 ！！本脚本只做 CREATE IF NOT EXISTS，不含 DROP，可重复执行。
 ！！导入时 search_path 必须与 application-local.yaml:52 的 currentSchema=data_query 一致，
    否则表落到 public，应用看不到（即此前 dual 同类问题）：
 PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres -f sql/postgresql/nl2sql-vector.sql
*/


-- ----------------------------
-- 0. 扩展（需 DBA 权限；已预装时 IF NOT EXISTS 直接跳过）
-- ----------------------------
CREATE EXTENSION IF NOT EXISTS vector;

-- 把扩展对齐到业务 schema（幂等）。本机实测扩展原本装在 public，
-- 若留在 public，后面的 vector(1536) 会在 search_path=data_query 下解析失败。
-- ALTER EXTENSION ... SET SCHEMA 要求扩展 relocatable = true；pgvector 0.8.7 满足。
DO $$
DECLARE
    v_schema text;
BEGIN
    SELECT n.nspname INTO v_schema
      FROM pg_extension e
      JOIN pg_namespace n ON n.oid = e.extnamespace
     WHERE e.extname = 'vector';
    IF v_schema IS NOT NULL AND v_schema <> 'data_query' THEN
        RAISE NOTICE 'pgvector 扩展从 % 迁移到 data_query', v_schema;
        EXECUTE 'ALTER EXTENSION vector SET SCHEMA data_query';
    END IF;
END $$;

-- 关键词兜底 / 混合重排需要 pg_trgm 时再启用（可选，索引较大）。
-- 注意：pg_trgm 若也预装在 public，同理需评估；一期关键词路用 ILIKE，不强依赖该索引。
-- CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ----------------------------
-- 1. Schema 向量索引表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_schema_vector (
    id int8 NOT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  datasource_id int8 NOT NULL DEFAULT 0,
  object_type int2 NOT NULL DEFAULT 1,
  table_id int8 NULL,
  column_id int8 NULL,
  schema_name varchar(128) NOT NULL DEFAULT '',
  table_name varchar(128) NOT NULL DEFAULT '',
  column_name varchar(128) NOT NULL DEFAULT '',
  content text NOT NULL DEFAULT '',
  content_hash varchar(64) NOT NULL DEFAULT '',
  embedding vector(1536) NULL,
  embedding_model_code varchar(64) NOT NULL DEFAULT '',
  embedding_dim int4 NOT NULL DEFAULT 1536,
  struct_version int4 NOT NULL DEFAULT 0,
  visible int2 NOT NULL DEFAULT 1,
  queryable int2 NOT NULL DEFAULT 1,
  sensitive_type int2 NOT NULL DEFAULT 0,
  mask_rule varchar(32) NOT NULL DEFAULT '',
  sync_status int2 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0,
  CONSTRAINT pk_ai_schema_vector PRIMARY KEY (id)
);

-- 租户 + 数据源 + 对象类型：支撑精确预过滤（一期精确扫描的主要路径）
CREATE INDEX IF NOT EXISTS idx_ai_schema_vector_01
  ON ai_schema_vector (tenant_id, datasource_id, object_type);

-- 增量判断：content_hash 未变则跳过该租户 Embedding 调用
CREATE INDEX IF NOT EXISTS idx_ai_schema_vector_02
  ON ai_schema_vector (tenant_id, datasource_id, content_hash);

-- 新鲜度监控：按 struct_version 找落后于 ai_datasource 的行
CREATE INDEX IF NOT EXISTS idx_ai_schema_vector_03
  ON ai_schema_vector (tenant_id, datasource_id, struct_version);

-- 幂等 upsert：表级 Document 的 column_id 为 NULL，用 COALESCE 参与唯一性
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_schema_vector_01
  ON ai_schema_vector (tenant_id, datasource_id, object_type,
                       COALESCE(table_id, 0), COALESCE(column_id, 0), embedding_model_code)
  WHERE deleted = 0;

-- 近似最近邻：余弦距离。NULL embedding（待构建）不进索引。
-- 注意：全局 HNSW 对 WHERE tenant_id = ? 是 post-filter，租户占比低时 Top-K 可能返回不足，
--       一期用精确扫描，或 pgvector >= 0.8.0 时事务内 SET LOCAL hnsw.iterative_scan。
CREATE INDEX IF NOT EXISTS idx_ai_schema_vector_hnsw
  ON ai_schema_vector USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

-- 关键词兜底路（可选）：先装 pg_trgm，再建 GIN 索引；content 较长，索引体积需评估
-- CREATE INDEX IF NOT EXISTS idx_ai_schema_vector_trgm
--   ON ai_schema_vector USING gin (content gin_trgm_ops);

COMMENT ON TABLE ai_schema_vector IS 'Schema 向量索引：元数据描述的 Embedding，租户私有，无平台共享向量';
COMMENT ON COLUMN ai_schema_vector.tenant_id IS '租户 ID，必须等于 datasource_id 所属租户；禁止写 0；严禁登记 ignore-tables';
COMMENT ON COLUMN ai_schema_vector.datasource_id IS '数据源 ID，检索时与 tenant_id 一起硬过滤';
COMMENT ON COLUMN ai_schema_vector.object_type IS '对象类型：1 表级 / 2 列级 / 3 术语口径级';
COMMENT ON COLUMN ai_schema_vector.content IS '向量化文本：表名+中文名+描述+指标口径+同义词+脱敏枚举值；禁止放 sample_rows 原始行';
COMMENT ON COLUMN ai_schema_vector.content_hash IS 'content + embedding_model_code 的哈希，未变则跳过重新 Embedding';
COMMENT ON COLUMN ai_schema_vector.embedding IS '1536 维向量；换模型或换维度必须全量重建本表与 HNSW 索引';
COMMENT ON COLUMN ai_schema_vector.embedding_model_code IS '模型代号指纹，由当前租户 EMBEDDING 配置拼成 provider.code:model_id:dimension（如 openai:text-embedding-3-small:1536）；取 ai_model_config(model_type=2).model_id 而非其主键 id；换模型/供应商/维度时按 tenant_id 重建本租户向量';
COMMENT ON COLUMN ai_schema_vector.embedding_dim IS '向量维度，与 embedding 列定义一致，写入时校验';
COMMENT ON COLUMN ai_schema_vector.struct_version IS '来源 ai_datasource.struct_version，用于索引新鲜度监控';
COMMENT ON COLUMN ai_schema_vector.visible IS '是否可见（冗余自元数据），检索硬过滤条件之一';
COMMENT ON COLUMN ai_schema_vector.queryable IS '是否可查（冗余自元数据），检索硬过滤条件之一；S6 还要以 PG 元数据二次核验';
COMMENT ON COLUMN ai_schema_vector.sensitive_type IS '敏感级别，冗余自 ai_datasource_column';
COMMENT ON COLUMN ai_schema_vector.mask_rule IS '脱敏规则：forbid 的列整列不入库';
COMMENT ON COLUMN ai_schema_vector.sync_status IS '0 待构建 / 1 已构建 / 2 需重建 / 4 已下线；4 不得被召回';
COMMENT ON COLUMN ai_schema_vector.status IS '0 正常 / 1 停用';


-- ----------------------------
-- 1.1 与既有同步日志的挂接：增补任务类型枚举（只改注释，不动表结构）
--     P1 向量写入任务复用 ai_datasource_sync_log，不另建任务表。
-- ----------------------------
COMMENT ON COLUMN ai_datasource_sync_log.sync_type IS
  '任务类型：1 连通性检测、2 全量元数据同步、3 增量元数据同步、4 结构变更检测、5 数据预览采样、6 枚举值采集、7 向量索引增量构建、8 向量索引全量重建、9 向量下线清理';


-- ----------------------------
-- 2. 运行时推荐 SQL（应用逻辑示例，非本脚本执行内容）
--    以下全部为注释，落地时由 Mapper / DAO 实现，且 SQL 必须显式带 tenant_id 条件。
--    MyBatis 侧 TenantDatabaseInterceptor 会再自动追加一次 tenant_id = 当前租户。
-- ----------------------------
--
-- 2.1 检索（一期：精确扫描，召回 100%，不依赖 pgvector 版本）
--     先用 B-tree 缩小到本租户本数据源的可见可查候选，再算距离：
-- SELECT id, object_type, table_id, column_id, table_name, column_name,
--        content, struct_version,
--        embedding <=> :queryVector AS distance
--   FROM ai_schema_vector
--  WHERE tenant_id = :tenantId
--    AND datasource_id = :datasourceId
--    AND object_type IN (1, 2)
--    AND visible = 1 AND queryable = 1 AND sync_status <> 4
--    AND status = 0 AND deleted = 0
--    AND embedding IS NOT NULL
--  ORDER BY distance ASC
--  LIMIT 20;
--
-- 2.2 检索（平台总量上来后：HNSW + 事务内 iterative scan，要求 pgvector >= 0.8.0）
--     SET LOCAL 只在当前事务生效，避免连接池串味；max_scan_tuples 是扫描预算上限。
-- BEGIN;
-- SET LOCAL hnsw.iterative_scan = 'relaxed_order';
-- SET LOCAL hnsw.max_scan_tuples = 20000;
-- SELECT id, object_type, table_id, column_id, embedding <=> :queryVector AS distance
--   FROM ai_schema_vector
--  WHERE tenant_id = :tenantId AND datasource_id = :datasourceId
--    AND visible = 1 AND queryable = 1 AND sync_status <> 4
--    AND status = 0 AND deleted = 0
--  ORDER BY embedding <=> :queryVector
--  LIMIT 20;
-- COMMIT;
--     relaxed_order 结果可能轻微乱序，混合重排阶段会重新排序，不影响正确性。
--
-- 2.3 关键词路（混合重排 / 故障兜底）——【必须同样带 tenant_id 条件】
-- SELECT id, object_type, table_id, column_id, similarity(content, :keyword) AS score
--   FROM ai_schema_vector
--  WHERE tenant_id = :tenantId AND datasource_id = :datasourceId
--    AND visible = 1 AND queryable = 1 AND sync_status <> 4
--    AND status = 0 AND deleted = 0
--    AND (content ILIKE '%' || :keyword || '%' OR table_name ILIKE '%' || :keyword || '%')
--  ORDER BY score DESC
--  LIMIT 20;
--     两路候选按 RRF 融合；任一路漏掉 tenant_id 都是跨租户数据暴露，不是相关性问题。
--
-- 2.4 增量写入：先比对 content_hash，未变则跳过该租户 Embedding 调用
-- SELECT id, content_hash FROM ai_schema_vector
--  WHERE tenant_id = :tenantId AND datasource_id = :datasourceId
--    AND object_type = :objectType
--    AND COALESCE(table_id, 0) = COALESCE(:tableId, 0)
--    AND COALESCE(column_id, 0) = COALESCE(:columnId, 0)
--    AND embedding_model_code = :modelCode
--    AND deleted = 0;
--     命中且 hash 一致 -> 跳过；否则调用【当前租户】的 Embedding 模型后按 uk_ai_schema_vector_01 做 upsert。
--     写入必须显式 setTenantId，并按 datasource_id 校验归属。
--
-- 2.5 同步源端删除 / 租户清理 / 索引新鲜度
-- UPDATE ai_schema_vector SET deleted = 1, sync_status = 4, update_time = CURRENT_TIMESTAMP
--  WHERE tenant_id = :tenantId AND datasource_id = :datasourceId AND table_id = :tableId;
-- DELETE FROM ai_schema_vector WHERE tenant_id = :tenantId;  -- 租户注销时由平台管理端执行
-- SELECT COUNT(*) FROM ai_schema_vector v
--   JOIN ai_datasource d ON d.id = v.datasource_id
--  WHERE v.tenant_id = :tenantId AND v.struct_version <> d.struct_version;
--
-- 2.6 维度 / 模型变更：不允许 ALTER COLUMN 混存不同维度，走「新建表 -> 全量写入 -> 切表名」
--     CREATE INDEX CONCURRENTLY 建索引，避免阻塞主库；大批量重建避开业务高峰。
--
-- 2.7 校验与回归
--     a. EXPLAIN 确认向量表查询计划带 tenant_id 过滤；
--     b. 确认本表未出现在 yudao.tenant.ignore-tables（application.yaml:336-339）；
--     c. 确认不存在 tenant_id = 0 的业务向量：
--        SELECT COUNT(*) FROM ai_schema_vector WHERE tenant_id = 0;
--     d. 构造租户占比 1% 的数据分布，验证 HNSW post-filter 是否返回不足 Top-K；
--     e. 确认扩展与 vector 类型都在 data_query（否则应用侧 <=> 无法解析）：
--        SELECT n.nspname FROM pg_extension e JOIN pg_namespace n ON n.oid=e.extnamespace
--         WHERE e.extname='vector';
--        SELECT n.nspname FROM pg_type t JOIN pg_namespace n ON n.oid=t.typnamespace
--         WHERE t.typname='vector';
--     f. pgvector 版本 >= 0.8.0 才支持 hnsw.iterative_scan：
--        SELECT extversion FROM pg_extension WHERE extname='vector';
--
-- 2.8 回滚（仅在确认无数据依赖时由 DBA 手动执行，默认不执行）
-- DROP INDEX IF EXISTS idx_ai_schema_vector_hnsw;
-- DROP INDEX IF EXISTS uk_ai_schema_vector_01;
-- DROP TABLE IF EXISTS ai_schema_vector;
--     CREATE EXTENSION 一般不做 DROP（可能被其他对象依赖）。


-- ============================================================
-- 10/32 nl2sql-query-runtime.sql
-- ============================================================
/*
 ai 平台 —— 管理端「问数运行时(P2-B)」数据模型
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 本期范围（P2-B）：会话 / 查询审计 / 查询模板 / 反馈 4 张表 + 完整 Java CRUD 层 + 菜单权限。
 表清单（4 张）：
   ai_query_session   问数会话：一次多轮问数的上下文容器
   ai_query_audit     查询审计：一次问答的全链路留痕（append-only，管理端只读）
   ai_query_template  查询模板：参数化问题/SQL 模板，P4 校验器落地前不得被引用执行
   ai_query_feedback  反馈：用户对回答的评分与纠正，人工审核后才可进 few-shot

 与既有模块 / 待建模块的挂接点（逻辑外键，不建物理约束）：
   ai_query_session.datasource_id          -> ai_datasource.id
   ai_query_session.scene_code             -> ai_prompt_scene.scene_code
   ai_query_audit.trace_id                 -> ai_query_session.trace_id
   ai_query_audit.prompt_version_id        -> ai_prompt_version.id
   ai_query_audit.model_config_id          -> ai_model_config.id
   ai_query_feedback.audit_id              -> ai_query_audit.id
   待建 P4  静态 SQL 校验器 / 只读执行器    -> 消费 ai_query_template.sql_template
   待建 P5  few-shot 样本库                -> 消费 reviewed = 1 的 ai_query_feedback

 约定（与 nl2sql-datasource.sql、nl2sql-model.sql 保持一致）：
 1) 表前缀 ai_；int8 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不建数据库序列。
 2) BaseDO 五字段（creator/create_time/updater/update_time/deleted）+ tenant_id int8 DEFAULT 0。
    ！！4 张表【全部租户私有，无平台共享】：一律不登记 yudao.tenant.ignore-tables，
      也不加 @TenantIgnore，由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户。
      写入时必须显式 setTenantId(当前租户)（实体为普通 POJO，框架不会自动填充）。
      tenant_id = 0 的行仅作模板/演示，不参与任何业务读取。
 3) 不建物理外键，关联由业务层维护；状态 / 枚举 / 布尔开关统一 int2，文本用 text / varchar。
 4) 全部使用 CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，主键用行内约束，
    本脚本可重复执行（幂等）；COMMENT ON COLUMN 重复执行仅覆盖注释，不会报错。
 5) ai_query_audit 为 append-only：管理端 Controller 只暴露 query / export，
    审计内容可能含 SQL 文本，RespVO 完整回显但绝不回显 tenantId / deleted。
 6) 导入时 search_path 必须指向 data_query schema（与 application-local.yaml 的 currentSchema 一致），
    否则表落到 public，应用看不到：
    PGOPTIONS='-c search_path=data_query,public' psql -h localhost -p 5432 -U postgres -d postgres -f -
 ！！本脚本不含 DROP、不含初始化数据，可对已有库重复执行。
*/

-- ----------------------------
-- 1. 问数会话（一次多轮问数的上下文容器）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_query_session (
    id int8 NOT NULL PRIMARY KEY,
  user_id int8 NOT NULL DEFAULT 0,
  datasource_id int8 NOT NULL DEFAULT 0,
  title varchar(200) NOT NULL DEFAULT '',
  session_no varchar(64) NOT NULL DEFAULT '',
  scene_code varchar(64) NOT NULL DEFAULT '',
  status int2 NOT NULL DEFAULT 1,
  message_count int4 NOT NULL DEFAULT 0,
  last_question text NULL,
  last_ask_time timestamp NULL,
  trace_id varchar(64) NOT NULL DEFAULT '',
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_query_session_01 ON ai_query_session (tenant_id, user_id, status);
CREATE INDEX IF NOT EXISTS idx_ai_query_session_02 ON ai_query_session (tenant_id, datasource_id, last_ask_time);
CREATE INDEX IF NOT EXISTS idx_ai_query_session_03 ON ai_query_session (session_no);
CREATE INDEX IF NOT EXISTS idx_ai_query_session_04 ON ai_query_session (trace_id);

COMMENT ON TABLE ai_query_session IS '问数会话表：一次多轮问数的上下文容器';
COMMENT ON COLUMN ai_query_session.id IS '主键';
COMMENT ON COLUMN ai_query_session.user_id IS '提问用户 ID';
COMMENT ON COLUMN ai_query_session.datasource_id IS '关联 ai_datasource.id，本次会话绑定的数据源';
COMMENT ON COLUMN ai_query_session.title IS '会话标题，可由首问自动生成或用户修改';
COMMENT ON COLUMN ai_query_session.session_no IS '会话编号，租户内唯一，便于检索与对外引用';
COMMENT ON COLUMN ai_query_session.scene_code IS '场景编码，关联 ai_prompt_scene.scene_code';
COMMENT ON COLUMN ai_query_session.status IS '状态：1 进行中、2 已结束、3 归档';
COMMENT ON COLUMN ai_query_session.message_count IS '消息条数，含用户提问与助手回答';
COMMENT ON COLUMN ai_query_session.last_question IS '最近一次用户问题，用于会话列表预览';
COMMENT ON COLUMN ai_query_session.last_ask_time IS '最近一次提问时间';
COMMENT ON COLUMN ai_query_session.trace_id IS '链路追踪 ID，关联 ai_query_audit.trace_id';
COMMENT ON COLUMN ai_query_session.remark IS '备注';
COMMENT ON COLUMN ai_query_session.tenant_id IS '租户私有，全表不登记 ignore-tables，写入显式 setTenantId';

-- ----------------------------
-- 2. 查询审计（一次问答的全链路留痕，append-only，管理端只读）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_query_audit (
    id int8 NOT NULL PRIMARY KEY,
  session_id int8 NOT NULL DEFAULT 0,
  user_id int8 NOT NULL DEFAULT 0,
  datasource_id int8 NOT NULL DEFAULT 0,
  trace_id varchar(64) NOT NULL DEFAULT '',
  question text NULL,
  normalized_question text NULL,
  intent_code varchar(64) NOT NULL DEFAULT '',
  recalled_tables text NULL,
  prompt_version_id int8 NOT NULL DEFAULT 0,
  model_config_id int8 NOT NULL DEFAULT 0,
  generated_sql text NULL,
  validated_sql text NULL,
  executed_sql text NULL,
  sql_status varchar(32) NOT NULL DEFAULT '',
  result_rows int8 NOT NULL DEFAULT 0,
  result_truncated int2 NOT NULL DEFAULT 0,
  latency_ms int4 NOT NULL DEFAULT 0,
  status int2 NOT NULL DEFAULT 1,
  error_code varchar(64) NOT NULL DEFAULT '',
  error_msg varchar(2000) NOT NULL DEFAULT '',
  readonly_connection int2 NOT NULL DEFAULT 0,
  client_ip varchar(64) NOT NULL DEFAULT '',
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_query_audit_01 ON ai_query_audit (tenant_id, session_id);
CREATE INDEX IF NOT EXISTS idx_ai_query_audit_02 ON ai_query_audit (tenant_id, user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_ai_query_audit_03 ON ai_query_audit (trace_id);
CREATE INDEX IF NOT EXISTS idx_ai_query_audit_04 ON ai_query_audit (status);

COMMENT ON TABLE ai_query_audit IS '查询审计表：一次问答的全链路留痕，append-only，管理端只读；内容可能含 SQL 文本，RespVO 完整回显但不回显 tenantId/deleted';
COMMENT ON COLUMN ai_query_audit.id IS '主键';
COMMENT ON COLUMN ai_query_audit.session_id IS '关联 ai_query_session.id，独立提问可为 0';
COMMENT ON COLUMN ai_query_audit.user_id IS '提问用户 ID';
COMMENT ON COLUMN ai_query_audit.datasource_id IS '关联 ai_datasource.id，本次问答使用的数据源';
COMMENT ON COLUMN ai_query_audit.trace_id IS '链路追踪 ID，贯穿会话、模型调用与本次审计';
COMMENT ON COLUMN ai_query_audit.question IS '用户原始问题文本';
COMMENT ON COLUMN ai_query_audit.normalized_question IS '改写 / 归一化后的问题文本';
COMMENT ON COLUMN ai_query_audit.intent_code IS '意图识别结果编码';
COMMENT ON COLUMN ai_query_audit.recalled_tables IS 'Schema 召回命中表清单，JSON 数组';
COMMENT ON COLUMN ai_query_audit.prompt_version_id IS '关联 ai_prompt_version.id，本次使用的提示词版本';
COMMENT ON COLUMN ai_query_audit.model_config_id IS '关联 ai_model_config.id，本次使用的模型配置';
COMMENT ON COLUMN ai_query_audit.generated_sql IS 'LLM 生成的原始 SQL';
COMMENT ON COLUMN ai_query_audit.validated_sql IS '经静态校验 / 改写后的 SQL';
COMMENT ON COLUMN ai_query_audit.executed_sql IS '最终下发只读连接执行的 SQL';
COMMENT ON COLUMN ai_query_audit.sql_status IS 'SQL 校验状态：PASS / REJECT / REWRITE 等，由静态校验器写入';
COMMENT ON COLUMN ai_query_audit.result_rows IS '结果返回行数';
COMMENT ON COLUMN ai_query_audit.result_truncated IS '结果集是否被 max_rows 截断：0 否、1 是';
COMMENT ON COLUMN ai_query_audit.latency_ms IS '端到端耗时（毫秒）';
COMMENT ON COLUMN ai_query_audit.status IS '结果状态：1 成功、2 澄清、3 拒绝、4 降级、5 失败、6 已停止';
COMMENT ON COLUMN ai_query_audit.error_code IS '错误码，成功为空';
COMMENT ON COLUMN ai_query_audit.error_msg IS '错误摘要，禁止记录完整密码与连接串';
COMMENT ON COLUMN ai_query_audit.readonly_connection IS '是否走只读连接执行：0 否、1 是';
COMMENT ON COLUMN ai_query_audit.client_ip IS '客户端 IP';
COMMENT ON COLUMN ai_query_audit.tenant_id IS '租户私有，全表不登记 ignore-tables，写入显式 setTenantId';

-- ----------------------------
-- 3. 查询模板（参数化问题/SQL 模板）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_query_template (
    id int8 NOT NULL PRIMARY KEY,
  name varchar(100) NOT NULL DEFAULT '',
  code varchar(64) NOT NULL DEFAULT '',
  category varchar(64) NOT NULL DEFAULT '',
  question_template text NULL,
  sql_template text NULL,
  params_def text NULL,
  datasource_id int8 NOT NULL DEFAULT 0,
  enabled int2 NOT NULL DEFAULT 1,
  sort int4 NOT NULL DEFAULT 0,
  remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_query_template_01 ON ai_query_template (tenant_id, category, enabled);
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_query_template_01 ON ai_query_template (tenant_id, code) WHERE deleted = 0;

COMMENT ON TABLE ai_query_template IS '查询模板表：sql_template 仅存储参数化模板文本，绝不在此执行；P4 校验器与只读执行器落地后才允许被引用';
COMMENT ON COLUMN ai_query_template.id IS '主键';
COMMENT ON COLUMN ai_query_template.name IS '模板名称';
COMMENT ON COLUMN ai_query_template.code IS '模板编码，租户内唯一，供程序引用';
COMMENT ON COLUMN ai_query_template.category IS '模板分类，如 销售 / 财务 / 运营';
COMMENT ON COLUMN ai_query_template.question_template IS '参数化问题模板文本，含占位符';
COMMENT ON COLUMN ai_query_template.sql_template IS 'sql_template 仅存储参数化模板文本，绝不在此执行；P4 校验器与只读执行器落地后才允许被引用';
COMMENT ON COLUMN ai_query_template.params_def IS '参数定义 JSON：名称、类型、必填、默认值、校验规则';
COMMENT ON COLUMN ai_query_template.datasource_id IS '关联 ai_datasource.id，模板适用的数据源，0 表示通用';
COMMENT ON COLUMN ai_query_template.enabled IS '是否启用：0 否、1 是';
COMMENT ON COLUMN ai_query_template.sort IS '排序';
COMMENT ON COLUMN ai_query_template.remark IS '备注';
COMMENT ON COLUMN ai_query_template.tenant_id IS '租户私有，全表不登记 ignore-tables，写入显式 setTenantId';

-- ----------------------------
-- 4. 反馈（评分与纠正，人工审核后才可进 few-shot）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_query_feedback (
    id int8 NOT NULL PRIMARY KEY,
  audit_id int8 NOT NULL DEFAULT 0,
  session_id int8 NOT NULL DEFAULT 0,
  user_id int8 NOT NULL DEFAULT 0,
  rating int2 NOT NULL DEFAULT 5,
  feedback_type varchar(64) NOT NULL DEFAULT '',
  comment text NULL,
  corrected_sql text NULL,
  reviewed int2 NOT NULL DEFAULT 0,
  reviewer_id int8 NOT NULL DEFAULT 0,
  review_time timestamp NULL,
  review_remark varchar(500) NULL DEFAULT NULL,
  tenant_id int8 NOT NULL DEFAULT 0,
  creator varchar(64) NULL DEFAULT '',
  create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater varchar(64) NULL DEFAULT '',
  update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted int2 NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_ai_query_feedback_01 ON ai_query_feedback (tenant_id, audit_id);
CREATE INDEX IF NOT EXISTS idx_ai_query_feedback_02 ON ai_query_feedback (tenant_id, reviewed);
CREATE INDEX IF NOT EXISTS idx_ai_query_feedback_03 ON ai_query_feedback (session_id);

COMMENT ON TABLE ai_query_feedback IS '反馈表：反馈不自动改写线上提示词版本，人工审核后才可进 few-shot';
COMMENT ON COLUMN ai_query_feedback.id IS '主键';
COMMENT ON COLUMN ai_query_feedback.audit_id IS '关联 ai_query_audit.id，本次反馈对应的问答';
COMMENT ON COLUMN ai_query_feedback.session_id IS '关联 ai_query_session.id';
COMMENT ON COLUMN ai_query_feedback.user_id IS '反馈用户 ID';
COMMENT ON COLUMN ai_query_feedback.rating IS '评分：1~5，5 为最满意';
COMMENT ON COLUMN ai_query_feedback.feedback_type IS '反馈类型：如 结果不准 / SQL 错误 / 口径疑问 / 其它';
COMMENT ON COLUMN ai_query_feedback.comment IS '用户文字反馈';
COMMENT ON COLUMN ai_query_feedback.corrected_sql IS '用户纠正后的 SQL，仅作参考；反馈不自动改写线上提示词版本，人工审核后才可进 few-shot';
COMMENT ON COLUMN ai_query_feedback.reviewed IS '审核状态：0 待审、1 已采纳、2 已驳回';
COMMENT ON COLUMN ai_query_feedback.reviewer_id IS '审核人用户 ID';
COMMENT ON COLUMN ai_query_feedback.review_time IS '审核时间';
COMMENT ON COLUMN ai_query_feedback.review_remark IS '审核备注';
COMMENT ON COLUMN ai_query_feedback.tenant_id IS '租户私有，全表不登记 ignore-tables，写入显式 setTenantId';

-- ----------------------------
-- 末尾说明
-- ----------------------------
-- 1) 4 张表全部租户私有：不登记 yudao.tenant.ignore-tables、不加 @TenantIgnore，
--    TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户；写入显式 setTenantId。
-- 2) ai_query_audit 为 append-only：管理端仅 query / export，切勿开放 create/update/delete 接口；
--    审计内容含 SQL 文本，导出即敏感数据，需按权限控制；运维归档走独立清理脚本。
-- 3) ai_query_template.sql_template 仅存参数化模板文本，P4 校验器与只读执行器落地前禁止被引用执行。
-- 4) ai_query_feedback 不自动改写线上提示词版本，人工审核（reviewed = 1）后才可进 few-shot。
-- 5) 本脚本幂等：CREATE TABLE/INDEX IF NOT EXISTS + 行内主键，可重复执行；不含 DROP、不含初始化数据。
-- 6) 幂等自检：
--    SELECT tablename FROM pg_tables WHERE schemaname = 'data_query' AND tablename LIKE 'ai_query_%' ORDER BY tablename;  -- 期望 4 行
--    SELECT indexname FROM pg_indexes WHERE schemaname = 'data_query' AND tablename LIKE 'ai_query_%' ORDER BY indexname;


-- ============================================================
-- 11/32 nl2sql-message.sql
-- ============================================================
/*
 ai 平台 —— 智能问数「对话消息」表（P7）
 Target Server Type : PostgreSQL
 版本               : 2026-11-16

 背景：P5/P6 的会话（ai_query_session）只存标题与最近问题，逐轮问答从未持久化，
       无法支撑 DeepSeek 式对话（会话列表 / 消息历史 / 重新生成 / 修改问题 / 赞踩）。
 本脚本新增 1 张表 ai_query_message：

   ai_query_session  1 ── *  ai_query_message
     USER 消息：role=USER，  parent_id=NULL， question=用户问题
     ASSISTANT 消息：role=ASSISTANT，parent_id=对应 USER 消息 id，
                      answer/sql/result_json/chart/explain/status 为回答快照

 分支与版本（DeepSeek 风格主链模型）：
   - is_active=1 且按 seq 升序 = 当前对话主链；
   - 重新生成：新建同 parent 的 ASSISTANT 新版本(version+1)，旧版本 is_active=0；
   - 修改问题：新建 USER 新版本(version+1) + 其 ASSISTANT，旧 USER 及旧 ASSISTANT is_active=0；
   - 旧分支数据 deleted=0 保留，可追溯；消息历史接口额外返回 alternatives（同 parent 的版本集）。
   - rating：-1 踩 / 0 未评价 / 1 赞，即时幂等切换（仅 ASSISTANT 有效）。

 安全：全表租户私有，不登记 yudao.tenant.ignore-tables，由 TenantDatabaseInterceptor
       自动追加 tenant_id；写入必须显式 setTenantId。result_json 只存本租户已执行过的
       只读结果快照，不含连接串 / 凭证。

 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-message.sql
*/
-- @formatter:off
CREATE TABLE IF NOT EXISTS ai_query_message (
    id              bigint        NOT NULL,
    session_id      bigint        NOT NULL,
    role            varchar(16)   NOT NULL,
    parent_id       bigint        DEFAULT 0 NOT NULL,
    root_message_id bigint        DEFAULT 0 NOT NULL,
    version         integer       DEFAULT 1 NOT NULL,
    seq             integer       DEFAULT 0 NOT NULL,
    is_active       smallint      DEFAULT 1 NOT NULL,
    question        text          NULL,
    answer          text          NULL,
    generated_sql   text          NULL,
    executed_sql    text          NULL,
    result_json     text          NULL,
    chart_spec_json text          NULL,
    explain         text          NULL,
    status          varchar(16)   NULL,
    error_code      varchar(32)   NULL,
    error_msg       text          NULL,
    trace_id        varchar(64)   NULL,
    audit_id        bigint        NULL,
    datasource_id   bigint        NULL,
    rating          smallint      DEFAULT 0 NOT NULL,
    tenant_id       bigint        NOT NULL,
    user_id         bigint        NOT NULL,
    creator         varchar(64)   NULL,
    create_time     timestamp     NOT NULL,
    updater         varchar(64)   NULL,
    update_time     timestamp     NOT NULL,
    deleted         smallint      DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_ai_query_message_01 ON ai_query_message (tenant_id, session_id, is_active, seq);
CREATE INDEX IF NOT EXISTS idx_ai_query_message_02 ON ai_query_message (tenant_id, user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_ai_query_message_03 ON ai_query_message (tenant_id, parent_id, version);
CREATE INDEX IF NOT EXISTS idx_ai_query_message_04 ON ai_query_message (tenant_id, audit_id);
CREATE INDEX IF NOT EXISTS idx_ai_query_message_05 ON ai_query_message (tenant_id, session_id, role);

COMMENT ON TABLE ai_query_message IS '问数对话消息表：会话内逐轮问答快照，支撑重新生成/修改问题/赞踩';
COMMENT ON COLUMN ai_query_message.role IS '消息角色：USER 用户提问 / ASSISTANT 助手回答';
COMMENT ON COLUMN ai_query_message.parent_id IS '父消息 id：ASSISTANT 指向对应 USER；USER 为 0';
COMMENT ON COLUMN ai_query_message.root_message_id IS '所在分支根 USER 消息 id，便于按分支聚合';
COMMENT ON COLUMN ai_query_message.version IS '版本：同一问题重新生成/修改后递增';
COMMENT ON COLUMN ai_query_message.seq IS '会话内展示序号';
COMMENT ON COLUMN ai_query_message.is_active IS '是否当前主链：1 是 / 0 历史版本';
COMMENT ON COLUMN ai_query_message.result_json IS 'QueryResult 结果快照 JSON（只读执行结果，不含连接串/凭证）';
COMMENT ON COLUMN ai_query_message.rating IS '赞踩：-1 踩 / 0 未评价 / 1 赞（仅 ASSISTANT 有效）';
COMMENT ON COLUMN ai_query_message.tenant_id IS '租户私有，写入显式 setTenantId';
-- @formatter:on


-- ============================================================
-- 12/32 nl2sql-chat-tree-migration.sql
-- ============================================================
/*
 ai 平台 —— 智能问数「对话树模型」阶段 0：数据库结构迁移
 Target Server Type : PostgreSQL
 版本               : 2026-11-18
 对应设计           : docs/deepseek-web-chat-design.md §3.3 / §3.4 / §4 / §5
 范围               : 仅 DAL（表结构 + 存量回填），不改任何 Java 代码、不删任何数据

 ─────────────────────────────────────────────────────────────────────────
 背景与现状
 ─────────────────────────────────────────────────────────────────────────
 现有实现是「is_active=1 + seq 升序」的扁平主链，按 seq 截断分支；
 设计文档要求改成不可变消息树：parent_id 管结构 / is_active 只管兄弟选中 /
 current_leaf_id 决定路径起点 / version 只做前端展示。

 本阶段落地其中三项结构前置：
   1) ai_query_session 新增 current_leaf_id（当前激活分支末端指针）
   2) ai_query_message.parent_id 由 DEFAULT 0 NOT NULL 改为可空，NULL 表示根节点
      —— 存量 parent_id=0 的 USER 消息原地改为 NULL（只改标记，不删数据）
   3) 新增树索引 + 兄弟版本唯一索引；回填每个会话的 current_leaf_id

 ─────────────────────────────────────────────────────────────────────────
 刻意「不做」的部分（避免照搬设计文档而破坏存量，留待后续阶段）
 ─────────────────────────────────────────────────────────────────────────
 - 不建 (session_id, seq) 唯一索引：存量中同一会话的 seq 大量重复（重新生成/
   修改问题共用 seq），加唯一约束会直接失败。阶段 2 改造 Recorder 后，seq
   改为「路径内单调递增」再评估。
 - 不照搬 UUID / UUID 外键 / JSONB / boolean / 小写 role：本项目是 bigint 雪花
   主键 + 租户私有表 + smallint is_active + 大写 USER/ASSISTANT，迁移只对齐
   「语义」，不改字段类型与数据取值。
 - 不加 current_leaf_id 的物理外键：消息是软删（deleted）而非物理删除，加
   外键无收益且会说不清「指向已软删消息」的状态；由应用层校验。
 - 不重排 version：实测存量非根兄弟组 version 无重复（1..n 连续），唯一索引
   可安全建立。脚本内置前置校验，若未来出现重复会中止并提示。
 - 不修改任何存量行的内容字段（question/answer/sql/result_json 等一律不动）。

 ─────────────────────────────────────────────────────────────────────────
 幂等与安全
 ─────────────────────────────────────────────────────────────────────────
 - ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS / 条件 UPDATE，可重复执行
 - 全程单事务 BEGIN..COMMIT：任何一步失败整体回滚
 - 含前置校验：兄弟版本若已重复则 RAISE EXCEPTION 中止，不留半截状态
 - 无 DROP / TRUNCATE / DELETE；存量数据 100% 保留

 执行：
   PGPASSWORD=<pwd> PGOPTIONS='-c search_path=data_query' \
     psql -h localhost -p 5432 -U postgres -d postgres -v ON_ERROR_STOP=1 \
     -f sql/postgresql/nl2sql-chat-tree-migration.sql
*/

BEGIN;

-- ============================================================================
-- 0. 前置校验：非根消息的 (tenant_id, parent_id, role, version) 必须唯一
--    用当前值 parent_id<>0 判断（本脚本稍后才把 0 改成 NULL）
-- ============================================================================
DO $$
DECLARE
    dup_groups bigint;
BEGIN
    SELECT count(*) INTO dup_groups
    FROM (
        SELECT 1
        FROM ai_query_message
        WHERE parent_id <> 0 AND deleted = 0
        GROUP BY tenant_id, parent_id, role, version
        HAVING count(*) > 1
    ) d;
    IF dup_groups > 0 THEN
        RAISE EXCEPTION
            '迁移中止：检测到 % 组重复的兄弟版本(parent_id, role, version)，请先人工核对 version',
            dup_groups;
    END IF;
END $$;

-- ============================================================================
-- 1. 会话表新增当前分支末端指针
-- ============================================================================
ALTER TABLE ai_query_session
    ADD COLUMN IF NOT EXISTS current_leaf_id bigint NULL;

COMMENT ON COLUMN ai_query_session.current_leaf_id IS
    '当前激活分支末端消息id(ai_query_message.id)，上下文回溯的唯一起点；NULL 表示会话尚无消息';

-- ============================================================================
-- 2. 消息表 parent_id 可空化：NULL = 根节点（原 0 = 根）
--    只改「根标记」，不改变消息内容；存量 USER 消息 parent_id=0 → NULL。
-- ============================================================================
ALTER TABLE ai_query_message ALTER COLUMN parent_id DROP NOT NULL;
ALTER TABLE ai_query_message ALTER COLUMN parent_id DROP DEFAULT;

UPDATE ai_query_message
   SET parent_id = NULL
 WHERE parent_id = 0;

COMMENT ON COLUMN ai_query_message.parent_id IS
    '直接前驱消息id(树结构)：ASSISTANT 指向触发它的 USER，USER 根节点为 NULL；重新生成/修改问题的新版本为兄弟节点，共享同一 parent';

-- ============================================================================
-- 3. 树结构索引
--    idx_06：向下找分支末端（按 parent_id 遍历孩子）
--    uk_sibling_version：并发 MAX+1 的数据库兜底（设计文档 uk_message_sibling_version）
--    注意：租户私有表，唯一索引必须带 tenant_id；软删消息不参与唯一性判断。
-- ============================================================================
CREATE INDEX IF NOT EXISTS idx_ai_query_message_06
    ON ai_query_message (tenant_id, session_id, parent_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_query_message_sibling_version
    ON ai_query_message (tenant_id, parent_id, role, version)
    WHERE parent_id IS NOT NULL AND deleted = 0;

COMMENT ON INDEX uk_ai_query_message_sibling_version IS
    '同一直接前驱下、同角色的 version 唯一：并发重新生成/修改问题的兜底约束；根消息(parent_id IS NULL)与软删消息不参与';

-- ============================================================================
-- 4. 回填 current_leaf_id = 当前主链末条消息（is_active=1,deleted=0,seq 最大）
--    seq 相同则取 id 最大（雪花 id 近似创建序），保证一个会话只有一个叶子。
-- ============================================================================
UPDATE ai_query_session s
   SET current_leaf_id = leaf.id
  FROM (
      SELECT DISTINCT ON (session_id) session_id, id
        FROM ai_query_message
       WHERE is_active = 1 AND deleted = 0
       ORDER BY session_id, seq DESC, id DESC
  ) leaf
 WHERE leaf.session_id = s.id;

COMMIT;

-- ============================================================================
-- 附：迁移后人工校验 SQL（不随脚本执行，供核对）
--   -- 列与索引
--   SELECT column_name, is_nullable, column_default FROM information_schema.columns
--    WHERE table_schema='data_query' AND table_name='ai_query_message' AND column_name='parent_id';
--   SELECT indexname FROM pg_indexes WHERE schemaname='data_query' AND tablename='ai_query_message' ORDER BY indexname;
--   -- 根消息已无 parent_id=0，且有 NULL 根
--   SELECT count(*) FILTER (WHERE parent_id=0) AS zero_parent,
--          count(*) FILTER (WHERE parent_id IS NULL) AS null_parent FROM ai_query_message;
--   -- 每个会话的叶子指向一条 active 消息
--   SELECT s.id, s.current_leaf_id, m.role, m.seq
--     FROM ai_query_session s LEFT JOIN ai_query_message m ON m.id=s.current_leaf_id
--    ORDER BY s.id;
--   -- 行数守恒（迁移前基线：session=2, message=26）
--   SELECT (SELECT count(*) FROM ai_query_session) AS sessions,
--          (SELECT count(*) FROM ai_query_message) AS messages;
-- ============================================================================


-- ============================================================
-- 13/32 nl2sql-knowledge.sql
-- ============================================================
/*
 ai 平台 —— 知识库 RAG「知识库 / 文档 / 切片向量」数据模型
 Target Server Type : PostgreSQL + pgvector 扩展
 版本               : 2026-11-17
 状态               : 方案 P9.1 数据模型（尚未在库执行）

 定位：智能问数的【独立问答旁路】，不接入 S0~S9 主链，不改 Schema 白名单。
       三张表全部【租户私有】，与 ai_schema_vector 同口径：
       - 每行带真实 tenant_id，严禁登记 yudao.tenant.ignore-tables，严禁 @TenantIgnore；
       - MyBatis 查询由 TenantDatabaseInterceptor 自动追加 tenant_id = 当前租户，
         DAO 的 SQL 里再显式写一次 tenant_id = ?，形成双保险；
       - 写入必须显式 setTenantId 并按 base_id 校验归属，禁止 tenant_id = 0 的共享知识库。

 关系（逻辑外键，不建物理约束）：
   ai_knowledge_base  1 ── *  ai_knowledge_document
   ai_knowledge_document 1 ── *  ai_knowledge_chunk
   chunk 冗余 base_id，便于检索时直接按 (tenant_id, base_id) 硬过滤，免去 join。

 Embedding 口径（沿用定稿决策）：
   - 【复用现有 EMBEDDING 场景绑定】，不新增场景；凭证来自租户自己的 ai_model_provider，
     经 M1 治理、独立限流/熔断键、写 ai_model_call_log；
   - 维度硬约束 vector(1536)，与 ai_schema_vector 一致（HNSW 索引要求固定维度）。
   - 若租户所配 Embedding 模型非 1536 维，上传时直接拒绝，绝不混存；
   - 换模型按 (tenant_id, base_id) 全量重建该库向量，chunk.content_hash 含 model_code 会自动触发。
   - model_code 口径：模型代号指纹 = provider.code:model_id:dimension（如 openai:text-embedding-3-small:1536），
     由 TenantSchemaEmbeddingProvider.currentModelCode 生成，取 ai_model_config(model_type=2).model_id 而非主键 id；
     base.embedding_model_code 冗余自 chunk，仅供展示与重建判断，不开放接口手填。

 引用溯源（带引用回答的关键字段，都在 chunk 上）：
   page_no / heading_path / char_start / char_end —— 回答中的 [n] 指向 chunk.id，
   前端据此定位到文档页码、标题路径与原文字符区间做高亮。

 约定（与 nl2sql-vector.sql / nl2sql-message.sql 一致）：
 1) 表前缀 ai_；int8 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不建数据库序列。
 2) BaseDO 五字段（creator/create_time/updater/update_time/deleted）+ tenant_id int8。
 3) 不建物理外键；状态 / 枚举 / 布尔开关统一 int2；
    唯一约束用 PostgreSQL 部分索引 WHERE deleted = 0。
 4) 两阶段状态机分离：document.parse_status（解析）/ chunk.sync_status（向量化），
    异步管线「上传→解析→切块→Embedding」各阶段可独立重试，失败只降级不假装成功。
 5) content_hash 幂等：文档级 = 文件 SHA256；切片级 = SHA256(content + "|" + embedding_model_code)。
 6) 本脚本只做 CREATE IF NOT EXISTS / COMMENT，不含 DROP，可重复执行。

 前置条件（执行前必须确认，与 nl2sql-vector.sql 相同）：
   a. pgvector 扩展已安装在 data_query schema（不是 public），否则 vector(1536) / <=> /
      vector_cosine_ops 在 search_path=data_query 下无法解析。
      校验：SELECT n.nspname FROM pg_extension e JOIN pg_namespace n
            ON n.oid=e.extnamespace WHERE e.extname='vector';  -- 期望 data_query
   b. pgvector >= 0.8.0 才支持 hnsw.iterative_scan（HNSW 带租户过滤时召回不足的修复）；
      版本不足时走「B-tree 预过滤 + 精确距离计算」，不使用迭代扫描。
   c. 若 nl2sql-vector.sql 已建 ai_schema_vector，则本脚本直接共用已迁移好的 pgvector 扩展，
      无需重复执行第 0 节。

 执行（search_path 必须与 application-local.yaml 的 currentSchema=data_query 一致）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-knowledge.sql
*/
-- @formatter:off


-- ----------------------------
-- 1. 知识库表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_base (
    id                  int8         NOT NULL,
    tenant_id           int8         NOT NULL DEFAULT 0,
    name                varchar(128) NOT NULL DEFAULT '',
    code                varchar(64)  NOT NULL DEFAULT '',
    description         varchar(512) NOT NULL DEFAULT '',
    status              int2         NOT NULL DEFAULT 0,
    document_count      int4         NOT NULL DEFAULT 0,
    chunk_count         int4         NOT NULL DEFAULT 0,
    embedding_model_code varchar(64) NOT NULL DEFAULT '',
    creator             varchar(64)  NULL DEFAULT '',
    create_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater             varchar(64)  NULL DEFAULT '',
    update_time         timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_knowledge_base PRIMARY KEY (id)
);

-- 租户内业务编码唯一（幂等 upsert 依据）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_base_01
  ON ai_knowledge_base (tenant_id, code) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_base_01
  ON ai_knowledge_base (tenant_id, status, deleted);

COMMENT ON TABLE ai_knowledge_base IS '知识库：租户私有的文档集合，独立问答旁路的顶层实体';
COMMENT ON COLUMN ai_knowledge_base.tenant_id IS '租户 ID，必须真实、禁止写 0；严禁登记 ignore-tables';
COMMENT ON COLUMN ai_knowledge_base.code IS '业务编码，租户内唯一，供接口按 code 定位知识库';
COMMENT ON COLUMN ai_knowledge_base.status IS '0 正常 / 1 停用；停用后该库不参与检索';
COMMENT ON COLUMN ai_knowledge_base.document_count IS '文档数冗余统计，上传/删除时在同事务内维护';
COMMENT ON COLUMN ai_knowledge_base.chunk_count IS '切片数冗余统计，重建时在同事务内维护';
COMMENT ON COLUMN ai_knowledge_base.embedding_model_code IS '该库向量所用模型代号指纹（冗余自 chunk），格式 provider.code:model_id:dimension（如 openai:text-embedding-3-small:1536）；非 ai_model_config.id；系统写入，便于展示与判断是否需要重建';


-- ----------------------------
-- 2. 文档表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_document (
    id             int8         NOT NULL,
    tenant_id      int8         NOT NULL DEFAULT 0,
    base_id        int8         NOT NULL DEFAULT 0,
    file_name      varchar(255) NOT NULL DEFAULT '',
    file_type      varchar(16)  NOT NULL DEFAULT '',
    file_size      int8         NOT NULL DEFAULT 0,
    file_url       varchar(2048) NOT NULL DEFAULT '',
    content_hash   varchar(64)  NOT NULL DEFAULT '',
    parse_status   int2         NOT NULL DEFAULT 0,
    parse_error    varchar(512) NOT NULL DEFAULT '',
    chunk_count    int4         NOT NULL DEFAULT 0,
    char_count     int8         NOT NULL DEFAULT 0,
    enabled        int2         NOT NULL DEFAULT 1,
    creator        varchar(64)  NULL DEFAULT '',
    create_time    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater        varchar(64)  NULL DEFAULT '',
    update_time    timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_knowledge_document PRIMARY KEY (id)
);

-- 同一知识库内，相同内容的文件不重复入库（上传幂等）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_document_01
  ON ai_knowledge_document (tenant_id, base_id, content_hash) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_01
  ON ai_knowledge_document (tenant_id, base_id, parse_status, deleted);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_02
  ON ai_knowledge_document (tenant_id, content_hash);

COMMENT ON TABLE ai_knowledge_document IS '知识库文档：原始文件与解析状态，逻辑外键 base_id -> ai_knowledge_base.id';
COMMENT ON COLUMN ai_knowledge_document.tenant_id IS '租户 ID，必须等于 base_id 所属租户；禁止写 0；严禁登记 ignore-tables';
COMMENT ON COLUMN ai_knowledge_document.base_id IS '逻辑外键 -> ai_knowledge_base.id，写入时校验同租户归属';
COMMENT ON COLUMN ai_knowledge_document.file_type IS '扩展名：TXT / MD / PDF / DOC / DOCX，一期全支持';
COMMENT ON COLUMN ai_knowledge_document.file_url IS '文件存储位置（本地路径或对象存储 key），不含凭证';
COMMENT ON COLUMN ai_knowledge_document.content_hash IS '文件字节 SHA256，用于上传幂等与内容变更判定';
COMMENT ON COLUMN ai_knowledge_document.parse_status IS '0 待解析 / 1 解析中 / 2 已完成 / 3 失败 / 4 已下线；失败只降级，可重试';
COMMENT ON COLUMN ai_knowledge_document.parse_error IS '解析失败原因摘要，便于前端展示与排障';
COMMENT ON COLUMN ai_knowledge_document.chunk_count IS '解析后生成的切片数';
COMMENT ON COLUMN ai_knowledge_document.char_count IS '解析后纯文本字符数';
COMMENT ON COLUMN ai_knowledge_document.enabled IS '是否参与检索：1 是 / 0 否（停用不删数据，重建时跳过）';


-- ----------------------------
-- 3. 切片向量表
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_chunk (
    id                   int8         NOT NULL,
    tenant_id            int8         NOT NULL DEFAULT 0,
    base_id              int8         NOT NULL DEFAULT 0,
    document_id          int8         NOT NULL DEFAULT 0,
    file_name            varchar(255) NOT NULL DEFAULT '',
    chunk_index          int4         NOT NULL DEFAULT 0,
    content              text         NOT NULL DEFAULT '',
    content_hash         varchar(64)  NOT NULL DEFAULT '',
    token_count          int4         NOT NULL DEFAULT 0,
    embedding            vector(1536) NULL,
    embedding_model_code varchar(64)  NOT NULL DEFAULT '',
    embedding_dim        int4         NOT NULL DEFAULT 1536,
    sync_status          int2         NOT NULL DEFAULT 0,
    page_no              int4         NULL,
    heading_path         varchar(512) NOT NULL DEFAULT '',
    char_start           int4         NULL,
    char_end             int4         NULL,
    status               int2         NOT NULL DEFAULT 0,
    creator              varchar(64)  NULL DEFAULT '',
    create_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater              varchar(64)  NULL DEFAULT '',
    update_time          timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted              int2         NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_knowledge_chunk PRIMARY KEY (id)
);

-- 幂等 upsert：同一切片的同一模型版本只存一条
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_chunk_01
  ON ai_knowledge_chunk (tenant_id, document_id, chunk_index, embedding_model_code)
  WHERE deleted = 0;

-- 检索主路径：先按租户 + 知识库 + 状态做 B-tree 预过滤，再算距离
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_01
  ON ai_knowledge_chunk (tenant_id, base_id, sync_status, status);

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_02
  ON ai_knowledge_chunk (tenant_id, document_id, chunk_index);

-- 增量判定：content_hash 未变则跳过 Embedding 调用
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_03
  ON ai_knowledge_chunk (tenant_id, base_id, content_hash);

-- 近似最近邻：余弦距离。NULL embedding（待构建）不进索引。
-- 注意：全局 HNSW 对 WHERE tenant_id = ? 是 post-filter，租户占比低时 Top-K 可能返回不足，
--       一期用 B-tree 预过滤 + 精确距离计算，或 pgvector >= 0.8.0 时事务内 SET LOCAL
--       hnsw.iterative_scan = 'relaxed_order'，绝不放宽 tenant_id 硬过滤。
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_hnsw
  ON ai_knowledge_chunk USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 64);

-- 关键词兜底路（可选）：需先安装 pg_trgm。本地 PG 未装 pg_trgm，一期用 ILIKE + CASE 打分，
-- 不依赖此索引；生产装了 pg_trgm 后可启用以加速长文本关键词召回。
-- CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_trgm
--   ON ai_knowledge_chunk USING gin (content gin_trgm_ops);

COMMENT ON TABLE ai_knowledge_chunk IS '知识库切片：带向量与引用定位信息，pgvector 检索的最小单位，租户私有';
COMMENT ON COLUMN ai_knowledge_chunk.tenant_id IS '租户 ID，必须等于 document_id / base_id 所属租户；禁止写 0；严禁登记 ignore-tables';
COMMENT ON COLUMN ai_knowledge_chunk.base_id IS '冗余自 document，检索时与 tenant_id 一起硬过滤，免去 join';
COMMENT ON COLUMN ai_knowledge_chunk.document_id IS '逻辑外键 -> ai_knowledge_document.id';
COMMENT ON COLUMN ai_knowledge_chunk.file_name IS '冗余自 document，供召回结果直接展示引用来源';
COMMENT ON COLUMN ai_knowledge_chunk.content IS '切片正文，按「结构优先 + 500token 滑窗/overlap 50」切分';
COMMENT ON COLUMN ai_knowledge_chunk.content_hash IS 'SHA256(content + "|" + embedding_model_code)，换模型自动触发重建';
COMMENT ON COLUMN ai_knowledge_chunk.embedding IS '1536 维向量；换模型或换维度必须全量重建该库向量与索引，不允许混存';
COMMENT ON COLUMN ai_knowledge_chunk.embedding_model_code IS '模型代号指纹，由当前租户 EMBEDDING 配置拼成 provider.code:model_id:dimension（如 openai:text-embedding-3-small:1536）；取 ai_model_config(model_type=2).model_id 而非主键 id；参与唯一键与 content_hash，写入与查询必须同租户同模型';
COMMENT ON COLUMN ai_knowledge_chunk.embedding_dim IS '向量维度，与 vector(1536) 列定义一致，写入时校验';
COMMENT ON COLUMN ai_knowledge_chunk.sync_status IS '0 待构建 / 1 已构建 / 2 需重建 / 4 已下线；4 不得被召回';
COMMENT ON COLUMN ai_knowledge_chunk.page_no IS 'PDF 页码（从 1 开始）；TXT/MD 为 NULL；引用溯源用';
COMMENT ON COLUMN ai_knowledge_chunk.heading_path IS '标题层级路径，如 "2.1 报销标准 > 交通费"；引用溯源用';
COMMENT ON COLUMN ai_knowledge_chunk.char_start IS '切片在解析后全文中的起始字符下标，前端高亮原文用';
COMMENT ON COLUMN ai_knowledge_chunk.char_end IS '切片在解析后全文中的结束字符下标（不含），前端高亮原文用';
COMMENT ON COLUMN ai_knowledge_chunk.status IS '0 正常 / 1 停用；停用不物理删除，重建时保留历史';


-- ----------------------------
-- 4. 运行时检索 SQL（应用逻辑示例，非本脚本执行内容）
--    所有 SQL 必须显式带 tenant_id 条件；TenantDatabaseInterceptor 会再自动追加一次。
-- ----------------------------
--
-- 4.1 向量检索（一期：B-tree 预过滤 + 精确距离，不依赖 pgvector 版本）
-- SELECT id, document_id, file_name, content, page_no, heading_path,
--        char_start, char_end, embedding <=> CAST(:queryVector AS vector) AS distance
--   FROM ai_knowledge_chunk
--  WHERE tenant_id = :tenantId AND base_id = :baseId
--    AND sync_status = 1 AND status = 0 AND deleted = 0
--    AND embedding IS NOT NULL
--  ORDER BY distance ASC
--  LIMIT :topK;
--     检索侧参数用 CAST(? AS vector) + setString；写入侧用 PgVectorTypeHandler(Types.OTHER)，两侧不同。
--
-- 4.2 关键词兜底路（本地 PG 无 pg_trgm，用 ILIKE + CASE 打分）
-- SELECT id, ..., CASE WHEN content ILIKE '%' || :kw || '%' THEN 1 ELSE 0 END AS score
--   FROM ai_knowledge_chunk
--  WHERE tenant_id = :tenantId AND base_id = :baseId
--    AND sync_status = 1 AND status = 0 AND deleted = 0
--    AND content ILIKE '%' || :kw || '%'
--  ORDER BY score DESC LIMIT :topK;
--
-- 4.3 两路候选按 RRF 融合（复用 SchemaRecallService 的融合逻辑），score = Σ w/(K + rank)
-- 4.4 引用溯源：回答中的 [n] -> chunk.id，取 page_no/heading_path/char_start/char_end 定位原文。
--     服务端必须校验回答里出现的 [n] 都在本次召回的 chunk 序号范围内，越界编号直接裁剪，
--     防止模型编造不存在的引用。
--
-- 4.5 增量写入：先比对 content_hash + embedding_model_code，未变则跳过该切片 Embedding 调用。
-- 4.6 换模型重建：按 (tenant_id, base_id) 把旧 model_code 的 chunk 置 sync_status=2，
--     用新模型重算；确认无旧向量依赖后再清理，避免检索期间空缺。
-- 4.7 删除文档：UPDATE document SET parse_status=4, deleted=1；
--     UPDATE chunk SET sync_status=4, deleted=1 WHERE tenant_id=:t AND document_id=:id；
--     同事务维护 base.document_count / chunk_count。

-- @formatter:on


-- ============================================================
-- 14/32 nl2sql-knowledge-version.sql
-- ============================================================
-- ============================================================
-- 知识库文档版本链：doc_key / version / is_latest
-- 用途：同名文档多次上传形成版本链；切片按 content_hash 增量复用；问答可按版本水位回溯。
-- 幂等：全部 IF NOT EXISTS；回填只处理未回填行，可重复执行。
-- 注意：大表回填 UPDATE 建议低峰执行；生产可按需拆成分批。
-- ============================================================

-- 0. file_url 加宽：原 varchar(512) 会把「含中文的长文件名」经 encodeUrlPath
--    （UTF-8 每字节转 %XX，膨胀 3 倍）后的 URL 截断，导致上传直接报「值太长(512)」。
--    上传侧已改用纯数字 documentId 做存储文件名，这里同步加宽存量列兜底。
--    PostgreSQL 重复执行无副作用。
ALTER TABLE ai_knowledge_document
    ALTER COLUMN file_url TYPE varchar(2048);

-- 1. document 加版本列
ALTER TABLE ai_knowledge_document
    ADD COLUMN IF NOT EXISTS doc_key   varchar(255) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS version   int4         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_latest int2         NOT NULL DEFAULT 1;

-- 2. chunk 加冗余版本列（检索免 join document）
ALTER TABLE ai_knowledge_chunk
    ADD COLUMN IF NOT EXISTS doc_key   varchar(255) NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS version   int4         NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS is_latest int2         NOT NULL DEFAULT 1;

-- 3. 回填 document.doc_key：取文件名最后一段 + 去首尾空白 + 转小写，
--    与 KnowledgeVersionUtil.normalizeFileName 完全一致（含 Windows 反斜杠路径兼容）。
--    只处理空值保证幂等。
UPDATE ai_knowledge_document
   SET doc_key = lower(btrim(regexp_replace(replace(file_name, '\', '/'), '^.*/', '')))
 WHERE doc_key = '';

-- 4. 回填 document.version / is_latest：同 doc_key 内按创建时间倒序编号，最新的一条为生效版本。
--    存量「同名多版本」被识别成版本链：老记录 version 递增、is_latest=0，避免检索重复召回。
UPDATE ai_knowledge_document d
   SET version   = x.rn,
       is_latest = CASE WHEN x.rn = 1 THEN 1 ELSE 0 END
  FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY tenant_id, base_id, doc_key
                   ORDER BY create_time DESC, id DESC
               ) AS rn
          FROM ai_knowledge_document
         WHERE deleted = 0
       ) x
 WHERE d.id = x.id
   AND d.deleted = 0;

-- 5. 已删除文档不作为生效版本（DEFAULT 1 的残留清掉）
UPDATE ai_knowledge_document SET is_latest = 0 WHERE deleted = 1;

-- 6. 回填 chunk 的版本维度：从所属 document 继承，保证检索字段一致。
UPDATE ai_knowledge_chunk c
   SET doc_key   = d.doc_key,
       version   = d.version,
       is_latest = CASE WHEN c.deleted = 0 AND d.is_latest = 1 THEN 1 ELSE 0 END
  FROM ai_knowledge_document d
 WHERE c.document_id = d.id;

-- 7. 替换唯一键：老键 (base_id, content_hash) 会挡住「v1=A → v2=B → v3 改回 A」，
--    改为版本维度唯一；内容幂等下沉到应用层按 (doc_key, 最新版 content_hash) 判定。
DROP INDEX IF EXISTS uk_ai_knowledge_document_01;
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_document_01
    ON ai_knowledge_document (tenant_id, base_id, doc_key, version) WHERE deleted = 0;

-- 8. 每个逻辑文档仅一个生效版本（步骤 4 已收敛存量，可安全建唯一索引）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_document_02
    ON ai_knowledge_document (tenant_id, base_id, doc_key) WHERE deleted = 0 AND is_latest = 1;

-- 9. 检索索引：默认路走 is_latest，水位回溯路走 doc_key + version
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_document_03
    ON ai_knowledge_document (tenant_id, base_id, doc_key, version DESC);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_04
    ON ai_knowledge_chunk (tenant_id, base_id, is_latest, sync_status, status) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_chunk_05
    ON ai_knowledge_chunk (tenant_id, doc_key, version) WHERE deleted = 0;

-- 10. 字段注释
COMMENT ON COLUMN ai_knowledge_document.doc_key IS
    '逻辑文档标识，缺省取规范化文件名；同 base 同 doc_key 视为同一文档的多个版本';
COMMENT ON COLUMN ai_knowledge_document.version IS '文档版本号，同 doc_key 内从 1 递增';
COMMENT ON COLUMN ai_knowledge_document.is_latest IS '1=当前生效版本，0=历史版本';
COMMENT ON COLUMN ai_knowledge_chunk.doc_key IS '冗余文档逻辑标识，检索按版本水位过滤用';
COMMENT ON COLUMN ai_knowledge_chunk.version IS '冗余文档版本号，随引用返回供溯源';
COMMENT ON COLUMN ai_knowledge_chunk.is_latest IS '冗余生效标记，默认检索 WHERE is_latest=1';


-- ============================================================
-- 15/32 nl2sql-knowledge-chat.sql
-- ============================================================
/*
 ai 平台 —— 知识库会话式问答「会话 / 消息」数据模型
 Target Server Type : PostgreSQL
 版本               : 2026-11-18
 状态               : P9.2 阶段一数据模型（尚未在库执行）

 定位：知识库问答采用【与智能问数类似的会话式架构】（会话列表 / 消息历史 / 对话树），
       但使用【知识库专属的两张表】，不复用 ai_query_session / ai_query_message：
       - 问数会话表 datasource_id NOT NULL，知识库问答没有数据源，只能写哨兵 0，
         而 0 会被 AiDatasourceOwnerValidator 判为"数据源不存在"，regenerate/edit 必然报错；
       - 问数消息表没有引用溯源列，而引用溯源（chunkId / 页码 / 标题路径 / 字符区间）是本功能的产物；
       - 问数会话列表只按 user_id 过滤，复用会导致两类会话在侧边栏串台。

 与问数对话树的口径完全一致（便于两端行为对齐）：
   - ai_knowledge_session.current_leaf_id：当前激活分支末端消息 id，上下文回溯的唯一起点；
   - ai_knowledge_message.parent_id：ASSISTANT 指向触发它的 USER，USER 根节点为 NULL；
   - version / seq / is_active：同一 parent+role 下的兄弟版本、会话内展示序号、兄弟组内是否选中；
   - 主链可见性由 current_leaf_id 沿 parent_id 回溯决定，is_active 只表达"兄弟组内谁被选中"。

 租户私有（与 ai_knowledge_base/document/chunk 同口径）：
   - 每行带真实 tenant_id，严禁登记 yudao.tenant.ignore-tables，严禁 @TenantIgnore；
   - MyBatis 查询由 TenantDatabaseInterceptor 自动追加 tenant_id，DAO SQL 里再显式写一次，双保险；
   - 写入必须显式 setTenantId；user_id 只由登录态写入，接口不接受。

 约定：
 1) int8 主键由 MyBatis-Plus IdType.ASSIGN_ID（雪花 ID）生成，不建数据库序列；
 2) BaseDO 五字段 + tenant_id int8；不建物理外键；枚举/开关统一 int2；
 3) 唯一约束用 PostgreSQL 部分索引 WHERE deleted = 0；
 4) 本脚本为增量脚本，表无存量数据，parent_id 直接可空、current_leaf_id 直接建列，无需迁移；
 5) 幂等：CREATE TABLE/INDEX IF NOT EXISTS + ADD CONSTRAINT 用 DO 块判重，可重复执行。
*/

-- ----------------------------
-- 1. 知识库问答会话
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_session
(
    id              int8         NOT NULL,                -- 雪花 ID
    user_id         int8         NOT NULL,                -- 提问用户 ID，只由登录态写入
    base_id         int8         NOT NULL,                -- 绑定知识库 ai_knowledge_base.id，会话内不可切换（不设默认值，漏写即报错）
    title           varchar(200) NOT NULL DEFAULT '',     -- 会话标题，首问前 30 字自动生成，可改名
    session_no      varchar(64)  NOT NULL DEFAULT '',     -- 会话编号，KS + 毫秒时间戳 + 6 位随机
    scene_code      varchar(64)  NOT NULL DEFAULT 'RAG',  -- 场景编码，固定 RAG
    status          int2         NOT NULL DEFAULT 1,      -- 1 进行中 / 2 已结束 / 3 归档
    message_count   int4         NOT NULL DEFAULT 0,      -- 消息条数，ASK 每轮 +2
    last_question   text         NULL,                    -- 最近一次用户问题，会话列表预览用
    last_ask_time   timestamp    NULL,                    -- 最近一次提问时间，会话列表排序键
    current_leaf_id int8         NULL,                    -- 当前激活分支末端消息 id，回溯唯一起点；NULL 表示尚无消息
    trace_id        varchar(64)  NOT NULL DEFAULT '',     -- 首问链路编号，便于串联日志
    remark          varchar(500) NULL DEFAULT NULL,
    tenant_id       int8         NOT NULL DEFAULT 0,      -- 租户私有
    creator         varchar(64)  NULL DEFAULT '',
    create_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater         varchar(64)  NULL DEFAULT '',
    update_time     timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         int2         NOT NULL DEFAULT 0
);

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                         FROM pg_constraint
                        WHERE conname = 'pk_ai_knowledge_session') THEN
            ALTER TABLE ai_knowledge_session ADD CONSTRAINT pk_ai_knowledge_session PRIMARY KEY (id);
        END IF;
    END
$$;

-- 会话编号租户内唯一（新表无存量负担，补上问数表只有普通索引的缺口）
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_session_01
    ON ai_knowledge_session (tenant_id, session_no) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_session_01
    ON ai_knowledge_session (tenant_id, user_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_session_02
    ON ai_knowledge_session (tenant_id, user_id, last_ask_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_session_03
    ON ai_knowledge_session (tenant_id, base_id, last_ask_time DESC);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_session_04
    ON ai_knowledge_session (tenant_id, trace_id);

COMMENT ON TABLE ai_knowledge_session IS '知识库问答会话：一次多轮知识问答的上下文容器，租户私有';
COMMENT ON COLUMN ai_knowledge_session.id IS '主键，雪花 ID';
COMMENT ON COLUMN ai_knowledge_session.user_id IS '提问用户 ID，只由登录态写入，接口不接受';
COMMENT ON COLUMN ai_knowledge_session.base_id IS '绑定知识库 ai_knowledge_base.id；会话内不可切换，写入时校验同租户归属';
COMMENT ON COLUMN ai_knowledge_session.title IS '会话标题，首问前 30 字自动生成，可改名';
COMMENT ON COLUMN ai_knowledge_session.session_no IS '会话编号，租户内唯一（KS + 毫秒时间戳 + 6 位随机）';
COMMENT ON COLUMN ai_knowledge_session.scene_code IS '场景编码，固定 RAG，对应 ai_prompt_scene.scene_code';
COMMENT ON COLUMN ai_knowledge_session.status IS '状态：1 进行中 / 2 已结束 / 3 归档';
COMMENT ON COLUMN ai_knowledge_session.message_count IS '消息条数，含用户提问与助手回答，ASK 每轮 +2';
COMMENT ON COLUMN ai_knowledge_session.last_question IS '最近一次用户问题，会话列表预览用';
COMMENT ON COLUMN ai_knowledge_session.last_ask_time IS '最近一次提问时间，会话列表排序键';
COMMENT ON COLUMN ai_knowledge_session.current_leaf_id IS '当前激活分支末端消息 id(ai_knowledge_message.id)，上下文回溯的唯一起点；NULL 表示会话尚无消息';
COMMENT ON COLUMN ai_knowledge_session.trace_id IS '首问链路编号，与应用日志 / 模型调用日志串联';
COMMENT ON COLUMN ai_knowledge_session.tenant_id IS '租户编号；租户私有，严禁登记 ignore-tables';

-- ----------------------------
-- 2. 知识库问答消息（对话树节点）
-- ----------------------------
CREATE TABLE IF NOT EXISTS ai_knowledge_message
(
    id                     int8        NOT NULL,               -- 雪花 ID
    session_id             int8        NOT NULL,               -- 关联 ai_knowledge_session.id
    role                   varchar(16) NOT NULL,               -- USER / ASSISTANT
    parent_id              int8        NULL,                   -- 直接前驱消息 id：ASSISTANT 指向 USER，USER 根节点为 NULL
    root_message_id        int8        NOT NULL DEFAULT 0,     -- 所在分支根 USER 消息 id
    version                int4        NOT NULL DEFAULT 1,     -- 同一 parent+role 下的版本号，重新生成/修改问题时递增
    seq                    int4        NOT NULL DEFAULT 0,     -- 会话内展示序号；ASK 新轮 = 会话 MAX(seq)+1
    is_active              int2        NOT NULL DEFAULT 1,     -- 兄弟组内是否选中：1 是 / 0 历史版本
    question               text        NULL,                   -- 用户问题（USER 消息）
    answer                 text        NULL,                   -- 助手文本回答（ASSISTANT 消息）
    references_json        text        NULL,                   -- 引用溯源 JSON 数组
    prompt_version_id      int8        NULL,                   -- 本轮 RAG 提示词版本快照 id
    model_id               varchar(128) NULL,                  -- 实际使用的模型标识快照
    model_config_id        int8        NULL,                   -- 实际使用的模型配置编号快照
    recall_mode            varchar(32) NULL,                   -- VECTOR / HYBRID / KEYWORD_DEGRADED / EMPTY
    recall_degraded        int2        NOT NULL DEFAULT 0,     -- 是否降级召回：1 是 / 0 否
    retrieved_count        int4        NOT NULL DEFAULT 0,     -- 本轮进入上下文的切片数（裁剪后）
    citation_dropped_count int2        NOT NULL DEFAULT 0,     -- 回答中被裁剪的越界引用编号数量
    latency_ms             int4        NULL,                   -- 本轮耗时（毫秒）
    status                 varchar(16) NULL,                   -- DONE / CLARIFY / REJECT / DEGRADED / FAILED / STOPPED
    error_code             varchar(32) NULL,
    error_msg              text        NULL,
    trace_id               varchar(64) NULL,                   -- 链路编号
    rating                 int2        NOT NULL DEFAULT 0,     -- -1 踩 / 0 未评价 / 1 赞（仅 ASSISTANT 有效）
    tenant_id              int8        NOT NULL DEFAULT 0,
    user_id                int8        NOT NULL,
    creator                varchar(64) NULL DEFAULT '',
    create_time            timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updater                varchar(64) NULL DEFAULT '',
    update_time            timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted                int2        NOT NULL DEFAULT 0
);

DO
$$
    BEGIN
        IF NOT EXISTS (SELECT 1
                         FROM pg_constraint
                        WHERE conname = 'pk_ai_knowledge_message') THEN
            ALTER TABLE ai_knowledge_message ADD CONSTRAINT pk_ai_knowledge_message PRIMARY KEY (id);
        END IF;
    END
$$;

CREATE INDEX IF NOT EXISTS idx_ai_knowledge_message_01
    ON ai_knowledge_message (tenant_id, session_id, is_active, seq);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_message_02
    ON ai_knowledge_message (tenant_id, session_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_message_03
    ON ai_knowledge_message (tenant_id, user_id, create_time);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_message_04
    ON ai_knowledge_message (tenant_id, session_id, role);
CREATE INDEX IF NOT EXISTS idx_ai_knowledge_message_05
    ON ai_knowledge_message (tenant_id, session_id, seq DESC);

-- 同一父节点下的兄弟版本唯一；根消息（parent_id IS NULL）与软删消息不参与
CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_knowledge_message_sibling_version
    ON ai_knowledge_message (tenant_id, parent_id, role, version)
    WHERE parent_id IS NOT NULL AND deleted = 0;

COMMENT ON TABLE ai_knowledge_message IS '知识库问答消息：对话树节点，支持重新生成/修改问题/版本切换，租户私有';
COMMENT ON COLUMN ai_knowledge_message.role IS 'USER 用户提问 / ASSISTANT 助手回答';
COMMENT ON COLUMN ai_knowledge_message.parent_id IS '直接前驱消息 id：ASSISTANT 指向 USER，USER 根节点为 NULL；兄弟版本共享同一 parent';
COMMENT ON COLUMN ai_knowledge_message.root_message_id IS '所在分支根 USER 消息 id，便于按分支聚合';
COMMENT ON COLUMN ai_knowledge_message.version IS '同一 parent + role 下的版本号，从 1 递增；重新生成/修改问题时使用';
COMMENT ON COLUMN ai_knowledge_message.seq IS '会话内展示序号；ASK 新轮 = 会话 MAX(seq)+1';
COMMENT ON COLUMN ai_knowledge_message.is_active IS '兄弟组内是否选中：1 是 / 0 历史版本；主链可见性由 current_leaf_id 回溯决定';
COMMENT ON COLUMN ai_knowledge_message.references_json IS '引用溯源 JSON 数组，元素含 refNo/chunkId/documentId/fileName/chunkIndex/pageNo/headingPath/charStart/charEnd/content';
COMMENT ON COLUMN ai_knowledge_message.prompt_version_id IS '本轮 RAG 提示词版本快照 id，复现与排障用';
COMMENT ON COLUMN ai_knowledge_message.model_id IS '实际使用的模型标识快照';
COMMENT ON COLUMN ai_knowledge_message.model_config_id IS '实际使用的模型配置编号快照';
COMMENT ON COLUMN ai_knowledge_message.recall_mode IS '召回方式：VECTOR / HYBRID / KEYWORD_DEGRADED / EMPTY';
COMMENT ON COLUMN ai_knowledge_message.recall_degraded IS '是否降级召回：1 是 / 0 否';
COMMENT ON COLUMN ai_knowledge_message.retrieved_count IS '本轮进入上下文的切片数（裁剪后）';
COMMENT ON COLUMN ai_knowledge_message.citation_dropped_count IS '回答中被裁剪的越界引用编号数量';
COMMENT ON COLUMN ai_knowledge_message.status IS '终态：DONE / CLARIFY / REJECT / DEGRADED / FAILED / STOPPED';
COMMENT ON COLUMN ai_knowledge_message.rating IS '赞踩：-1 踩 / 0 未评价 / 1 赞（仅 ASSISTANT 有效）';
COMMENT ON COLUMN ai_knowledge_message.tenant_id IS '租户编号；租户私有，严禁登记 ignore-tables';

-- ----------------------------
-- 3. 执行后人工校验（不随脚本执行）
-- ----------------------------
-- 表与索引：
-- SELECT tablename, indexname FROM pg_indexes
--  WHERE schemaname = 'data_query'
--    AND tablename IN ('ai_knowledge_session', 'ai_knowledge_message')
--  ORDER BY tablename, indexname;
-- 唯一索引谓词：
-- SELECT indexdef FROM pg_indexes
--  WHERE schemaname = 'data_query' AND indexname = 'uk_ai_knowledge_message_sibling_version';
-- 确认新表不在 yudao.tenant.ignore-tables（application.yaml 只应列 4 张提示词表）。


-- ============================================================
-- 16/32 nl2sql-tenant-isolation.sql
-- ============================================================
/*
 ai 平台 —— 多租户凭证隔离 / 数据源全租户私有 存量清洗与校验脚本
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 本轮口径（docs/tenant-isolation-design.md）：
   1. 数据源连接（ai_datasource）、元数据 4 表、同步日志共 6 张表【全部租户私有，无平台共享】；
      它们不登记 yudao.tenant.ignore-tables，继续由 TenantDatabaseInterceptor 追加 tenant_id = 当前租户。
      tenant_id = 0 的初始化示例数据只是模板，业务侧天然读不到，无需物理删除。
   2. 平台共享仅提示词 4 表（ai_prompt_scene / ai_prompt / ai_prompt_version / ai_prompt_variable），
      由 SharedTenantServiceImpl 显式限定 IN (当前租户, 0) 与写入范围。
   3. 凭证字段加密：AiSecretTypeHandler 写密文、读解密；「未配置」一律用 NULL 表示，
      因为框架 EncryptTypeHandler 只对 null 短路，空串会进 AES 解密并抛异常。

 脚本特性：幂等、可重复执行；对尚不存在的表自动跳过（DO + to_regclass 判断），
           不会 DROP 任何表，不删除任何业务数据。可选的变更语句默认注释掉，需人工确认后执行。

 ！！导入时 search_path 必须与 application-local.yaml 的 currentSchema=data_query 一致：
 PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
   -f sql/postgresql/nl2sql-tenant-isolation.sql
*/

-- ----------------------------
-- 1. 凭证列：允许 NULL + 存量空串刷 NULL（幂等）
-- ----------------------------
DO $$
BEGIN
    -- 模型供应商凭证
    IF to_regclass('ai_model_provider') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE ai_model_provider ALTER COLUMN api_key DROP NOT NULL';
        EXECUTE 'ALTER TABLE ai_model_provider ALTER COLUMN api_secret DROP NOT NULL';
        EXECUTE 'UPDATE ai_model_provider SET api_key = NULL WHERE api_key = ''''';
        EXECUTE 'UPDATE ai_model_provider SET api_secret = NULL WHERE api_secret = ''''';
    END IF;

    -- 数据源连接密码（Java 层尚未生成，先按同样规范铺好，后续接 AiSecretTypeHandler 时无需再刷）
    IF to_regclass('ai_datasource') IS NOT NULL THEN
        EXECUTE 'ALTER TABLE ai_datasource ALTER COLUMN password DROP NOT NULL';
        EXECUTE 'ALTER TABLE ai_datasource ALTER COLUMN readonly_password DROP NOT NULL';
        EXECUTE 'UPDATE ai_datasource SET password = NULL WHERE password = ''''';
        EXECUTE 'UPDATE ai_datasource SET readonly_password = NULL WHERE readonly_password = ''''';
    END IF;
END $$;

-- 注意：存量「明文 Key」无法自动判断是明文还是密文，本脚本不改写。
-- AiSecretTypeHandler 已做兼容：解密失败时按原文返回，下次通过管理端重新录入即自动写成密文。
-- 上线前建议导出凭证清单、由各租户管理员重新录入一遍，再核对库中是否还有非 Base64 的值。

-- ----------------------------
-- 2. 【可选】私有表 tenant_id 处置 —— 默认不执行，需人工确认
--
-- 现状：初始化脚本写入的是 tenant_id = 0，拦截器追加 tenant_id = 当前租户（通常 1）后查不到，
--       这正是「分页 SQL 查出 1 条、接口返回空」的根因。
-- 两条路线二选一：
--   A（推荐，与「独立账号」一致）：不动 0 数据，由 AiTenantInitializer 为每个租户复制一套配置，
--     调用 POST /admin-api/data-query/tenant-init/init?tenantId=xxx 或新建租户自动触发。
--   B（仅限当前确实只有单租户、想最小改动过渡）：把模型组整组一起刷成该租户 ID。
--     ！！必须整组一起刷，只刷一张表会让 provider_id / primary_model_id 变成跨租户引用。
-- ----------------------------
-- BEGIN;
-- UPDATE ai_model_provider      SET tenant_id = 1 WHERE tenant_id = 0;
-- UPDATE ai_model_config        SET tenant_id = 1 WHERE tenant_id = 0;
-- UPDATE ai_model_scene_binding SET tenant_id = 1 WHERE tenant_id = 0;
-- UPDATE ai_model_route_candidate SET tenant_id = 1 WHERE tenant_id = 0;
-- UPDATE ai_model_call_log      SET tenant_id = 1 WHERE tenant_id = 0;
-- UPDATE ai_model_circuit_state SET tenant_id = 1 WHERE tenant_id = 0;
-- UPDATE ai_model_gray_experiment SET tenant_id = 1 WHERE tenant_id = 0;
-- COMMIT;

-- ----------------------------
-- 3. 【可选】数据源示例数据降级 —— 默认不执行
-- 让平台管理员（tenant_id = 0）也看不到演示用的连接串与元数据样例。
-- ----------------------------
-- BEGIN;
-- UPDATE ai_datasource                SET deleted = 1 WHERE tenant_id = 0;
-- UPDATE ai_datasource_table          SET deleted = 1 WHERE tenant_id = 0;
-- UPDATE ai_datasource_column         SET deleted = 1 WHERE tenant_id = 0;
-- UPDATE ai_datasource_column_enum    SET deleted = 1 WHERE tenant_id = 0;
-- UPDATE ai_datasource_table_relation SET deleted = 1 WHERE tenant_id = 0;
-- UPDATE ai_datasource_sync_log       SET deleted = 1 WHERE tenant_id = 0;
-- COMMIT;

-- ----------------------------
-- 4. 校验 SQL（按需手动执行，不在脚本中自动输出）
-- ----------------------------
-- 4.1 各私有表的租户分布，确认 0 数据已由初始化器复制到业务租户：
-- SELECT 'ai_model_provider' AS tbl, tenant_id, count(*) FROM ai_model_provider WHERE deleted = 0 GROUP BY tenant_id;
-- SELECT 'ai_model_config'   AS tbl, tenant_id, count(*) FROM ai_model_config   WHERE deleted = 0 GROUP BY tenant_id;
-- SELECT 'ai_datasource'     AS tbl, tenant_id, count(*) FROM ai_datasource     WHERE deleted = 0 GROUP BY tenant_id;
-- SELECT 'ai_datasource_table' AS tbl, tenant_id, count(*) FROM ai_datasource_table WHERE deleted = 0 GROUP BY tenant_id;
--
-- 4.2 凭证列是否还有空串（应为 0 行）：
-- SELECT id, tenant_id FROM ai_model_provider WHERE api_key = '' OR api_secret = '';
--
-- 4.3 共享表（提示词）是否有 tenant_id 不属于 {0, 各业务租户} 的脏数据：
-- SELECT DISTINCT tenant_id FROM ai_prompt_scene ORDER BY tenant_id;
--
-- 4.4 数据源元数据归属一致性（应返回 0 行；后续生成 Java 层时按此规则校验）：
-- SELECT m.id, m.datasource_id, m.tenant_id AS meta_tenant, d.tenant_id AS ds_tenant
--   FROM ai_datasource_table m JOIN ai_datasource d ON d.id = m.datasource_id
--  WHERE d.deleted = 0 AND m.deleted = 0 AND m.tenant_id <> d.tenant_id;
--
-- 4.5 跨数据源引用（应返回 0 行）：
-- SELECT c.id FROM ai_datasource_column c
--   JOIN ai_datasource_table t ON t.id = c.table_id
--  WHERE c.deleted = 0 AND t.deleted = 0 AND c.datasource_id <> t.datasource_id;

-- ----------------------------
-- 5. 回滚
-- ----------------------------
-- 5.1 配置回滚：移除 application.yaml 中 ignore-tables 的 4 项，恢复全部表被租户过滤。
-- 5.2 数据回滚：把本次由 AiTenantInitializer 复制生成的记录置 deleted = 1（不要物理删除，保留审计线索）：
-- UPDATE ai_model_provider  SET deleted = 1 WHERE tenant_id = :tenantId AND creator = 'tenant-init';
-- UPDATE ai_model_config    SET deleted = 1 WHERE tenant_id = :tenantId AND creator = 'tenant-init';
-- UPDATE ai_model_scene_binding SET deleted = 1 WHERE tenant_id = :tenantId AND creator = 'tenant-init';
-- 5.3 凭证列回滚：ALTER TABLE ... ALTER COLUMN api_key SET NOT NULL 前必须先确认没有 NULL 值；
--     加密项一旦上线不建议单独回滚（回滚代码会把密文当明文用），需同步准备数据解密脚本。


-- ============================================================
-- 17/32 nl2sql-menu.sql
-- ============================================================
/*
 ai 平台 —— 管理端「模型配置(M1) + 提示词管理(M2)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-12

 依据（全部来自本仓库实际代码，未臆造接口）：
   yudao-module-dataQuery/src/main/java/cn/iocoder/boot/module/data_query/controller/admin/model/[*].java
   yudao-module-dataQuery/src/main/java/cn/iocoder/boot/module/data_query/controller/admin/prompt/[*].java
   8 个 Controller 的 @PreAuthorize 共提取出 35 个不重复权限串，见下方「按钮权限」段，一一对应。

 菜单树：
   12940 智能问数平台 (目录, /data-query)
     ├─ 12941 模型管理 (目录, model)
     │   ├─ 12943 模型供应商  /data-query/ai-model-provider
     │   ├─ 12944 模型配置    /data-query/ai-model-config
     │   ├─ 12945 场景绑定    /data-query/ai-model-scene-binding
     │   └─ 12946 调用日志    /data-query/ai-model-call-log
     └─ 12942 提示词管理 (目录, prompt)
         ├─ 12947 提示词模板  /data-query/ai-prompt
         ├─ 12948 场景字典    /data-query/ai-prompt-scene
         ├─ 12949 版本管理    /data-query/ai-prompt-version
         └─ 12950 模板变量    /data-query/ai-prompt-variable

 ID 分配：12940 ~ 12985（共 46 条 = 3 目录 + 8 菜单 + 35 按钮）
   ruoyi-vue-pro.sql 中 system_menu 最大 id = 12939，且 system_menu_seq START 12940，
   本脚本正好接续；末尾 setval 把序列推进到全表 max(id)，避免之后在管理端新建菜单时主键冲突。

 幂等策略：本脚本【不删表、不 DROP】，可重复执行。
   仅清理「本脚本占用的 12940~12985」+「ID < 12940 且带 data-query: 前缀的历史误插记录」，再重新插入。
   ⚠️ 会覆盖这 46 条菜单的本地改动（如改名、排序），不会触碰其它模块菜单。
   ⚠️ 后续新增模块请使用 12986 起的 ID（管理端新建菜单由序列自动取该段），不要落在 12940~12985。

 与既有脚本的字段约定（参照 ruoyi-vue-pro.sql 的 system_menu INSERT）：
   type: 1 目录 / 2 菜单 / 3 按钮；status: 0 正常；
   bool 列 visible / keep_alive / always_show 沿用 '1'；deleted 用 '0'；
   按钮的 path / icon / component / component_name 全部置空（MenuServiceImpl 对按钮类型也会强制置空）。
   菜单 permission 留空，与 infra/demo/demo01 的示例菜单一致；接口鉴权完全由 35 个按钮权限串覆盖。

 前端说明：本仓库没有前端代码（yudao-ui 只是占位 README）。
   component 指向独立前端仓库 src/views/ 下的路径，需要自行创建，例如
   src/views/dataQuery/model/provider/index.vue；component_name 需全局唯一（MenuServiceImpl 有校验）。

 执行（依赖 ruoyi-vue-pro.sql 已导入；search_path 必须与 application-local.yaml 的 currentSchema 一致）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu.sql

 MySQL 迁移差异：'1'/'0' 布尔字面量改为 1/0；本脚本的 setval 语句删除（改 AUTO_INCREMENT）；
   其余 INSERT / DELETE 语法通用。
*/

BEGIN;

-- ----------------------------
-- 0. 幂等清理（仅限本模块 ID 段与权限串）
-- ----------------------------
DELETE FROM system_role_menu
WHERE menu_id IN (
    SELECT id FROM system_menu
    WHERE id BETWEEN 12940 AND 12985
       OR (permission LIKE 'data-query:%' AND id < 12940)
);
DELETE FROM system_menu
WHERE id BETWEEN 12940 AND 12985
   OR (permission LIKE 'data-query:%' AND id < 12940);

-- ----------------------------
-- 1. 目录（3 条）
-- ----------------------------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12940, '智能问数平台', '', 1, 410, 0, '/data-query', 'tabler:ai', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12941, '模型管理', '', 1, 1, 12940, 'model', 'ep:cpu', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12942, '提示词管理', '', 1, 2, 12940, 'prompt', 'ep:edit-pen', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- ----------------------------
-- 2. 菜单（8 条，对应 8 个 Controller）
-- ----------------------------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12943, '模型供应商', '', 2, 1, 12941, 'provider', 'ep:connection', 'dataQuery/model/provider/index', 'DataQueryModelProvider', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12944, '模型配置', '', 2, 2, 12941, 'config', 'ep:setting', 'dataQuery/model/config/index', 'DataQueryModelConfig', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12945, '场景绑定', '', 2, 3, 12941, 'scene-binding', 'ep:link', 'dataQuery/model/sceneBinding/index', 'DataQueryModelSceneBinding', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12946, '调用日志', '', 2, 4, 12941, 'call-log', 'ep:document', 'dataQuery/model/callLog/index', 'DataQueryModelCallLog', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12947, '提示词模板', '', 2, 1, 12942, 'template', 'ep:document-copy', 'dataQuery/prompt/template/index', 'DataQueryPromptTemplate', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12948, '场景字典', '', 2, 2, 12942, 'scene', 'ep:collection', 'dataQuery/prompt/scene/index', 'DataQueryPromptScene', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12949, '版本管理', '', 2, 3, 12942, 'version', 'ep:files', 'dataQuery/prompt/version/index', 'DataQueryPromptVersion', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12950, '模板变量', '', 2, 4, 12942, 'variable', 'ep:key', 'dataQuery/prompt/variable/index', 'DataQueryPromptVariable', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- ----------------------------
-- 3. 按钮权限（35 条 = 8 个 Controller 的全部 @PreAuthorize 去重）
--    特殊接口复用已有权限串，不另建按钮：
--      update-status / update-enabled / publish / batch-save -> :update
--      simple-list / get-by-scene / get-by-code / validate / render / diff -> :query
--      copy -> :create   rollback -> ai-prompt-version:update
-- ----------------------------

-- 3.1 模型供应商 AiModelProviderController（5）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12951, '模型供应商查询', 'data-query:ai-model-provider:query', 3, 1, 12943, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12952, '模型供应商新增', 'data-query:ai-model-provider:create', 3, 2, 12943, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12953, '模型供应商修改', 'data-query:ai-model-provider:update', 3, 3, 12943, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12954, '模型供应商删除', 'data-query:ai-model-provider:delete', 3, 4, 12943, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12955, '模型供应商导出', 'data-query:ai-model-provider:export', 3, 5, 12943, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.2 模型配置 AiModelConfigController（5）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12956, '模型配置查询', 'data-query:ai-model-config:query', 3, 1, 12944, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12957, '模型配置新增', 'data-query:ai-model-config:create', 3, 2, 12944, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12958, '模型配置修改', 'data-query:ai-model-config:update', 3, 3, 12944, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12959, '模型配置删除', 'data-query:ai-model-config:delete', 3, 4, 12944, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12960, '模型配置导出', 'data-query:ai-model-config:export', 3, 5, 12944, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.3 模型场景绑定 AiModelSceneBindingController（5）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12961, '场景绑定查询', 'data-query:ai-model-scene-binding:query', 3, 1, 12945, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12962, '场景绑定新增', 'data-query:ai-model-scene-binding:create', 3, 2, 12945, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12963, '场景绑定修改', 'data-query:ai-model-scene-binding:update', 3, 3, 12945, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12964, '场景绑定删除', 'data-query:ai-model-scene-binding:delete', 3, 4, 12945, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12965, '场景绑定导出', 'data-query:ai-model-scene-binding:export', 3, 5, 12945, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.4 模型调用日志 AiModelCallLogController（2，只读，不开放增删改）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12966, '调用日志查询', 'data-query:ai-model-call-log:query', 3, 1, 12946, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12967, '调用日志导出', 'data-query:ai-model-call-log:export', 3, 2, 12946, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.5 提示词模板 AiPromptController（5）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12968, '提示词查询', 'data-query:ai-prompt:query', 3, 1, 12947, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12969, '提示词新增', 'data-query:ai-prompt:create', 3, 2, 12947, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12970, '提示词修改', 'data-query:ai-prompt:update', 3, 3, 12947, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12971, '提示词删除', 'data-query:ai-prompt:delete', 3, 4, 12947, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12972, '提示词导出', 'data-query:ai-prompt:export', 3, 5, 12947, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.6 提示词场景字典 AiPromptSceneController（5）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12973, '场景字典查询', 'data-query:ai-prompt-scene:query', 3, 1, 12948, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12974, '场景字典新增', 'data-query:ai-prompt-scene:create', 3, 2, 12948, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12975, '场景字典修改', 'data-query:ai-prompt-scene:update', 3, 3, 12948, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12976, '场景字典删除', 'data-query:ai-prompt-scene:delete', 3, 4, 12948, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12977, '场景字典导出', 'data-query:ai-prompt-scene:export', 3, 5, 12948, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.7 提示词版本 AiPromptVersionController（3，只读 + 回滚，不开放增删改）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12978, '版本查询', 'data-query:ai-prompt-version:query', 3, 1, 12949, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12979, '版本回滚', 'data-query:ai-prompt-version:update', 3, 2, 12949, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12980, '版本导出', 'data-query:ai-prompt-version:export', 3, 3, 12949, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- 3.8 提示词模板变量 AiPromptVariableController（5）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12981, '模板变量查询', 'data-query:ai-prompt-variable:query', 3, 1, 12950, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12982, '模板变量新增', 'data-query:ai-prompt-variable:create', 3, 2, 12950, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12983, '模板变量修改', 'data-query:ai-prompt-variable:update', 3, 3, 12950, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12984, '模板变量删除', 'data-query:ai-prompt-variable:delete', 3, 4, 12950, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12985, '模板变量导出', 'data-query:ai-prompt-variable:export', 3, 5, 12950, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-12 00:00:00', 'admin', '2026-11-12 00:00:00', '0');

-- ----------------------------
-- 4. 推进主键序列（MenuDO 使用 @KeySequence("system_menu_seq")）
--    GREATEST 取「全表最大 id / 序列当前值 / 12940」三者最大值，保证绝不倒退。
-- ----------------------------
SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    12940), true);

COMMIT;

-- ----------------------------
-- 5. 可选：给普通角色（role_id = 2）一次性授权
--    超级管理员无需此步：PermissionServiceImpl:190-191 对 super_admin 直接返回全量菜单。
--    放开注释即可执行；也可改为在管理端「角色管理 -> 菜单权限」勾选。
-- ----------------------------
-- DELETE FROM system_role_menu WHERE role_id = 2 AND menu_id BETWEEN 12940 AND 12985;
-- INSERT INTO system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
-- SELECT nextval('system_role_menu_seq'), 2, id, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, 0, 1
-- FROM system_menu WHERE id BETWEEN 12940 AND 12985;

-- ----------------------------
-- 6. 可选：多租户套餐追加菜单（menu_ids 为 "[1, 2, ...]" 形式的 varchar(4096)）
--    普通套餐 id=111 当前约 1203 字符，追加 46 个 ID 约 330 字符，未超上限。
-- ----------------------------
-- WITH new_ids AS (
--     SELECT string_agg(id::text, ', ' ORDER BY id) AS s FROM system_menu WHERE id BETWEEN 12940 AND 12985
-- )
-- UPDATE system_tenant_package
-- SET menu_ids = regexp_replace(menu_ids, ']$', ', ' || (SELECT s FROM new_ids) || ']')
-- WHERE id = 111 AND menu_ids NOT LIKE '%12940%';

-- ----------------------------
-- 7. 导入后自检（可单独执行）
-- ----------------------------
-- SELECT count(*) AS total FROM system_menu WHERE id BETWEEN 12940 AND 12985;  -- 期望 46
-- SELECT count(*) AS buttons FROM system_menu WHERE permission LIKE 'data-query:%' AND type = 3;  -- 期望 35
-- SELECT permission, count(*) FROM system_menu WHERE permission LIKE 'data-query:%' AND type = 3
--   GROUP BY permission HAVING count(*) > 1;  -- 期望 0 行（权限串无重复）
-- SELECT last_value FROM system_menu_seq;  -- 应 >= 12985
-- 注意：菜单/权限有缓存，导入后需重新登录或重启应用使角色权限缓存刷新。


-- ============================================================
-- 18/32 nl2sql-menu-governance.sql
-- ============================================================
/*
 ai 平台 —— M1「模型治理」增量菜单与按钮权限（依赖 nl2sql-menu.sql 已导入）
 Target Server Type : PostgreSQL
 版本               : 2026-11-13

 覆盖本轮新增的 4 个 Controller：
   AiModelRouteCandidateController   /data-query/ai-model-route-candidate
   AiModelCircuitStateController     /data-query/ai-model-circuit-state
   AiModelGrayExperimentController   /data-query/ai-model-gray-experiment
   ModelGovernanceController         /data-query/ai-model-governance

 菜单树（挂在 nl2sql-menu.sql 已建的 12941 模型管理目录下）：
   12941 模型管理
     ├─ 12986 路由候选   route-candidate
     ├─ 12987 熔断状态   circuit-state
     ├─ 12988 灰度实验   gray-experiment
     └─ 12989 治理链路   governance

 ID 分配：12986 ~ 13005（共 20 条 = 4 菜单 + 16 按钮）
   主菜单脚本占用 12940~12985，本脚本正好接续，不与其重叠。
   ⚠️ 后续新增模块请使用 >= 13006 的 ID。

 幂等策略：不删表、不 DROP，可重复执行。
   仅清理本脚本占用的 12986~13005。！！刻意不按 permission 前缀清理，
   否则会把主脚本 12940~12985 里的 data-query: 按钮一起误删。

 权限串与接口映射（复用关系，无需额外按钮）：
   /update-status、/update-enabled 复用 :update；/list-by-binding、/get-by-key、
   /simple-list、/get-by-scene 复用 :query；/reset 复用 :update（人工止血）。
   /route-preview -> :query；/invoke -> :create；/reset-circuit -> :update。

 字段约定与 nl2sql-menu.sql 一致：type 1/2/3、status 0、bool 列 '1'、deleted '0'、
 按钮的 path/icon/component/component_name 置空、菜单 permission 留空。
 前端 component 指向独立前端仓库 src/views/ 下路径，本仓库无前端代码，需自行创建。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-governance.sql
*/

BEGIN;

-- ----------------------------
-- 0. 幂等清理（仅限本脚本 ID 段）
-- ----------------------------
DELETE FROM system_role_menu WHERE menu_id BETWEEN 12986 AND 13005;
DELETE FROM system_menu WHERE id BETWEEN 12986 AND 13005;

-- ----------------------------
-- 1. 菜单（4 条）
-- ----------------------------
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12986, '路由候选', '', 2, 5, 12941, 'route-candidate', 'ep:share', 'dataQuery/model/routeCandidate/index', 'DataQueryModelRouteCandidate', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12987, '熔断状态', '', 2, 6, 12941, 'circuit-state', 'ep:switch-button', 'dataQuery/model/circuitState/index', 'DataQueryModelCircuitState', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12988, '灰度实验', '', 2, 7, 12941, 'gray-experiment', 'ep:data-analysis', 'dataQuery/model/grayExperiment/index', 'DataQueryModelGrayExperiment', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12989, '治理链路', '', 2, 8, 12941, 'governance', 'ep:monitor', 'dataQuery/model/governance/index', 'DataQueryModelGovernance', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');

-- ----------------------------
-- 2. 按钮权限（16 条）
-- ----------------------------
-- 路由候选（12986）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12990, '路由候选-新增', 'data-query:ai-model-route-candidate:create', 3, 1, 12986, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12991, '路由候选-修改', 'data-query:ai-model-route-candidate:update', 3, 2, 12986, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12992, '路由候选-删除', 'data-query:ai-model-route-candidate:delete', 3, 3, 12986, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12993, '路由候选-查询', 'data-query:ai-model-route-candidate:query', 3, 4, 12986, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12994, '路由候选-导出', 'data-query:ai-model-route-candidate:export', 3, 5, 12986, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');

-- 熔断状态（12987）：只读 + 人工重置，不开放增删改
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12995, '熔断状态-查询', 'data-query:ai-model-circuit-state:query', 3, 1, 12987, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12996, '熔断状态-导出', 'data-query:ai-model-circuit-state:export', 3, 2, 12987, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12997, '熔断状态-重置', 'data-query:ai-model-circuit-state:update', 3, 3, 12987, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');

-- 灰度实验（12988）
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12998, '灰度实验-新增', 'data-query:ai-model-gray-experiment:create', 3, 1, 12988, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (12999, '灰度实验-修改', 'data-query:ai-model-gray-experiment:update', 3, 2, 12988, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13000, '灰度实验-删除', 'data-query:ai-model-gray-experiment:delete', 3, 3, 12988, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13001, '灰度实验-查询', 'data-query:ai-model-gray-experiment:query', 3, 4, 12988, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13002, '灰度实验-导出', 'data-query:ai-model-gray-experiment:export', 3, 5, 12988, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');

-- 治理链路（12989）：route-preview / invoke / reset-circuit
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13003, '治理链路-路由预览', 'data-query:ai-model-governance:query', 3, 1, 12989, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13004, '治理链路-联调调用', 'data-query:ai-model-governance:create', 3, 2, 12989, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES (13005, '治理链路-熔断重置', 'data-query:ai-model-governance:update', 3, 3, 12989, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-13 00:00:00', 'admin', '2026-11-13 00:00:00', '0');

-- ----------------------------
-- 3. 推进主键序列（MenuDO 使用 @KeySequence("system_menu_seq")）
-- ----------------------------
SELECT setval('system_menu_seq', GREATEST((SELECT COALESCE(MAX(id), 0) FROM system_menu), 13005), true);

COMMIT;

-- ----------------------------
-- 4. 可选：给普通角色（role_id = 2）追加授权（超管无需此步）
-- ----------------------------
-- DELETE FROM system_role_menu WHERE role_id = 2 AND menu_id BETWEEN 12986 AND 13005;
-- INSERT INTO system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
-- SELECT nextval('system_role_menu_seq'), 2, id, 'admin', CURRENT_TIMESTAMP, 'admin', CURRENT_TIMESTAMP, 0, 1
-- FROM system_menu WHERE id BETWEEN 12986 AND 13005;

-- ----------------------------
-- 5. 导入后自检
-- ----------------------------
-- SELECT count(*) FROM system_menu WHERE id BETWEEN 12986 AND 13005;  -- 期望 20
-- SELECT count(*) FROM system_menu WHERE id BETWEEN 12940 AND 12985;  -- 期望仍是 46（确认没误删主脚本）
-- SELECT permission, count(*) FROM system_menu WHERE permission LIKE 'data-query:%' AND type = 3
--   GROUP BY permission HAVING count(*) > 1;  -- 期望 0 行（全库权限串无重复）
-- SELECT last_value FROM system_menu_seq;  -- 应 >= 13005
-- 注意：菜单/权限有缓存，导入后需重新登录或重启应用刷新角色权限缓存。


-- ============================================================
-- 19/32 nl2sql-menu-tenant-init.sql
-- ============================================================
/*
 ai 平台 —— 租户初始化接口菜单与按钮权限（依赖 nl2sql-menu.sql 已导入）
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 覆盖本轮新增的 AiTenantInitController：
   POST /admin-api/data-query/tenant-init/init     初始化指定租户的 AI 配置
   GET  /admin-api/data-query/tenant-init/status  查询指定租户的初始化状态

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13006 租户初始化   tenant-init
          ├─ 13007 执行初始化  data-query:tenant-init:create
          └─ 13008 查询状态    data-query:tenant-init:query

 ID 分配：13006 ~ 13008（共 3 条 = 1 菜单 + 2 按钮）
   主菜单脚本占用 12940~12985，治理脚本占用 12986~13005，本脚本正好接续，不与其重叠。
   ⚠️ 后续新增模块请使用 >= 13009 的 ID。

 幂等策略：不删表、不 DROP，只清理本脚本占用的 13006~13008，可重复执行。
   ！！刻意不按 permission 前缀清理，否则会误删其它脚本的 data-query: 按钮。

 字段约定与 nl2sql-menu.sql 一致：type 1/2/3、status 0、bool 列 '1'、deleted '0'、
 按钮的 path/icon/component/component_name 置空、菜单 permission 留空。
 前端 component 指向独立前端仓库 src/views/ 下路径，本仓库无前端代码，需自行创建。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-tenant-init.sql

 ！！导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_menu WHERE id BETWEEN 13006 AND 13008;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13006, '租户初始化', '', 2, 5, 12940, 'tenant-init', 'ep:refresh', 'dataQuery/system/tenantInit/index', 'DataQueryTenantInit', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13007, '租户初始化-执行', 'data-query:tenant-init:create', 3, 1, 13006, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13008, '租户初始化-查询', 'data-query:tenant-init:query', 3, 2, 13006, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

COMMIT;
-- @formatter:on


-- ============================================================
-- 20/32 nl2sql-menu-datasource.sql
-- ============================================================
/*
 ai 平台 —— 管理端「数据源管理(M3)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 覆盖 6 个新增 Controller 的权限串（test-connection / simple-list 复用已有权限，不另建按钮）：
   /data-query/ai-datasource                    AiDatasourceController
   /data-query/ai-datasource-table             AiDatasourceTableController
   /data-query/ai-datasource-column            AiDatasourceColumnController
   /data-query/ai-datasource-column-enum       AiDatasourceColumnEnumController
   /data-query/ai-datasource-table-relation    AiDatasourceTableRelationController
   /data-query/ai-datasource-sync-log          AiDatasourceSyncLogController（只读）

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13009 数据源管理 (目录)
         ├─ 13010~13015 六个菜单
         └─ 13016~13042 按钮权限（每个菜单 query/create/update/delete/export，同步日志仅 query/export）

 ID 分配：13009 ~ 13042（共 34 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008 已占用，
   本脚本接续，不与前序脚本重叠。后续新增模块请使用 >= 13043 的 ID。

 幂等策略：不删表、不 DROP，只清理本脚本占用的 13009~13042，可重复执行。
   ！！刻意不按 permission 前缀清理，否则会误删其它脚本的 data-query: 按钮。
   同时清理 system_role_menu 中的对应引用，避免重复导入留下孤儿记录。

 字段约定与 nl2sql-menu.sql 一致：type 1/2/3、status 0、bool 列 '1'、deleted '0'、
 按钮的 path/icon/component/component_name 置空、菜单 permission 留空。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-datasource.sql

 ！！导入后需重新登录（或刷新权限缓存）才会生效。
 ！！本脚本只登记菜单权限，不修改 yudao.tenant.ignore-tables：
    数据源 6 张表全部租户私有，严禁加入忽略列表。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13009 AND 13042;
DELETE FROM system_menu WHERE id BETWEEN 13009 AND 13042;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13009, '数据源管理', '', 1, 3, 12940, 'datasource', 'ep:coin', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13010, '数据源', '', 2, 1, 13009, 'ai-datasource', 'ep:document', 'dataQuery/datasource/index', 'DataQueryDatasource', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13011, '数据源表元数据', '', 2, 2, 13009, 'ai-datasource-table', 'ep:document', 'dataQuery/datasource/table/index', 'DataQueryDatasourceTable', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13012, '数据源字段元数据', '', 2, 3, 13009, 'ai-datasource-column', 'ep:document', 'dataQuery/datasource/column/index', 'DataQueryDatasourceColumn', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13013, '字段枚举映射', '', 2, 4, 13009, 'ai-datasource-column-enum', 'ep:document', 'dataQuery/datasource/enumvalue/index', 'DataQueryDatasourceEnum', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13014, '表关系 / Join 路径', '', 2, 5, 13009, 'ai-datasource-table-relation', 'ep:document', 'dataQuery/datasource/relation/index', 'DataQueryDatasourceRelation', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13015, '数据源同步日志', '', 2, 6, 13009, 'ai-datasource-sync-log', 'ep:document', 'dataQuery/datasource/synclog/index', 'DataQueryDatasourceSyncLog', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13016, '数据源-查询', 'data-query:ai-datasource:query', 3, 1, 13010, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13017, '数据源-新增', 'data-query:ai-datasource:create', 3, 2, 13010, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13018, '数据源-修改', 'data-query:ai-datasource:update', 3, 3, 13010, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13019, '数据源-删除', 'data-query:ai-datasource:delete', 3, 4, 13010, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13020, '数据源-导出', 'data-query:ai-datasource:export', 3, 5, 13010, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13021, '数据源表元数据-查询', 'data-query:ai-datasource-table:query', 3, 1, 13011, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13022, '数据源表元数据-新增', 'data-query:ai-datasource-table:create', 3, 2, 13011, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13023, '数据源表元数据-修改', 'data-query:ai-datasource-table:update', 3, 3, 13011, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13024, '数据源表元数据-删除', 'data-query:ai-datasource-table:delete', 3, 4, 13011, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13025, '数据源表元数据-导出', 'data-query:ai-datasource-table:export', 3, 5, 13011, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13026, '数据源字段元数据-查询', 'data-query:ai-datasource-column:query', 3, 1, 13012, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13027, '数据源字段元数据-新增', 'data-query:ai-datasource-column:create', 3, 2, 13012, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13028, '数据源字段元数据-修改', 'data-query:ai-datasource-column:update', 3, 3, 13012, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13029, '数据源字段元数据-删除', 'data-query:ai-datasource-column:delete', 3, 4, 13012, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13030, '数据源字段元数据-导出', 'data-query:ai-datasource-column:export', 3, 5, 13012, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13031, '字段枚举映射-查询', 'data-query:ai-datasource-column-enum:query', 3, 1, 13013, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13032, '字段枚举映射-新增', 'data-query:ai-datasource-column-enum:create', 3, 2, 13013, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13033, '字段枚举映射-修改', 'data-query:ai-datasource-column-enum:update', 3, 3, 13013, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13034, '字段枚举映射-删除', 'data-query:ai-datasource-column-enum:delete', 3, 4, 13013, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13035, '字段枚举映射-导出', 'data-query:ai-datasource-column-enum:export', 3, 5, 13013, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13036, '表关系 / Join 路径-查询', 'data-query:ai-datasource-table-relation:query', 3, 1, 13014, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13037, '表关系 / Join 路径-新增', 'data-query:ai-datasource-table-relation:create', 3, 2, 13014, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13038, '表关系 / Join 路径-修改', 'data-query:ai-datasource-table-relation:update', 3, 3, 13014, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13039, '表关系 / Join 路径-删除', 'data-query:ai-datasource-table-relation:delete', 3, 4, 13014, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13040, '表关系 / Join 路径-导出', 'data-query:ai-datasource-table-relation:export', 3, 5, 13014, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13041, '数据源同步日志-查询', 'data-query:ai-datasource-sync-log:query', 3, 1, 13015, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13042, '数据源同步日志-导出', 'data-query:ai-datasource-sync-log:export', 3, 2, 13015, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13009), true);

COMMIT;
-- @formatter:on

-- 导入后自检：
-- SELECT count(*) FROM system_menu WHERE id BETWEEN 13009 AND 13042;  -- 期望 34
-- SELECT last_value FROM system_menu_seq;  -- 应 >= 13042


-- ============================================================
-- 21/32 nl2sql-menu-p1.sql
-- ============================================================
/*
 ai 平台 —— 管理端「元数据同步 + Schema 向量索引(P1)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 覆盖 2 个新增 Controller：
   /data-query/metadata-sync    MetadataSyncController（sync / visibility / status）
   /data-query/schema-vector    AiSchemaVectorController（sync / rebuild / status / page / delete）

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13043 元数据与向量索引 (目录)
         ├─ 13044 元数据同步
         │   └─ 13046~13048 按钮
         └─ 13045 Schema 向量索引
             └─ 13049~13052 按钮

 ID 分配：13043 ~ 13052（共 10 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008、
   P0 数据源 13009~13042 已占用。后续新增模块请使用 >= 13053 的 ID。

 幂等策略：只清理本脚本占用的 13043~13052，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 租户口径：元数据 6 张表与 ai_schema_vector 全部租户私有，
   ！！本脚本不修改 yudao.tenant.ignore-tables，向量表严禁登记。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql + nl2sql-menu-datasource.sql + nl2sql-vector.sql）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-p1.sql

 ！！导入后需重新登录（或刷新权限缓存）才会生效；超管走权限兜底，普通角色需手动分配。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13043 AND 13052;
DELETE FROM system_menu WHERE id BETWEEN 13043 AND 13052;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13043, '元数据与向量索引', '', 1, 4, 12940, 'schema-index', 'ep:connection', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13044, '元数据同步', '', 2, 1, 13043, 'metadata-sync', 'ep:refresh', 'dataQuery/metadata/sync/index', 'DataQueryMetadataSync', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13045, 'Schema 向量索引', '', 2, 2, 13043, 'schema-vector', 'ep:aim', 'dataQuery/metadata/vector/index', 'DataQuerySchemaVector', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13046, '元数据同步-查询', 'data-query:metadata-sync:query', 3, 1, 13044, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13047, '元数据同步-执行', 'data-query:metadata-sync:sync', 3, 2, 13044, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13048, '元数据同步-可见性维护', 'data-query:metadata-sync:update', 3, 3, 13044, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13049, 'Schema 向量索引-查询', 'data-query:schema-vector:query', 3, 1, 13045, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13050, 'Schema 向量索引-增量构建', 'data-query:schema-vector:sync', 3, 2, 13045, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13051, 'Schema 向量索引-全量重建', 'data-query:schema-vector:rebuild', 3, 3, 13045, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13052, 'Schema 向量索引-下线', 'data-query:schema-vector:delete', 3, 4, 13045, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13043), true);

COMMIT;
-- @formatter:on

-- 导入后自检：
-- SELECT count(*) FROM system_menu WHERE id BETWEEN 13043 AND 13052;  -- 期望 10
-- SELECT last_value FROM system_menu_seq;  -- 应 >= 13052


-- ============================================================
-- 22/32 nl2sql-menu-query-runtime.sql
-- ============================================================
/*
 ai 平台 —— 管理端「问数运行时(P2-B)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 覆盖 4 个新增 Controller 的权限串：
   /data-query/ai-query-session    AiQuerySessionController（会话）
   /data-query/ai-query-audit      AiQueryAuditController（查询审计，只读：query / export）
   /data-query/ai-query-template   AiQueryTemplateController（查询模板）
   /data-query/ai-query-feedback   AiQueryFeedbackController（反馈）

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13053 问数运行时 (目录)
         ├─ 13054 问数会话
         ├─ 13055 查询审计（只读）
         ├─ 13056 查询模板
         └─ 13057 反馈
             └─ 各菜单按钮权限（会话/模板/反馈 5 个，审计 2 个）

 ID 分配：13053 ~ 13074（共 22 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008、
   P0 数据源 13009~13042、P1 13043~13052 已占用。后续新增模块请使用 >= 13075 的 ID。

 幂等策略：只清理本脚本占用的 13053~13074，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 租户口径：会话 / 查询审计 / 查询模板 / 反馈 4 张表全部租户私有，
   ！！本脚本不修改 yudao.tenant.ignore-tables，4 张表严禁登记。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-query-runtime.sql

 ！！导入后需重新登录（或刷新权限缓存）才会生效；超管走权限兜底，普通角色需手动分配。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13053 AND 13074;
DELETE FROM system_menu WHERE id BETWEEN 13053 AND 13074;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13053, '问数运行时', '', 1, 5, 12940, 'query-runtime', 'ep:chat-line-square', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13054, '问数会话', '', 2, 1, 13053, 'ai-query-session', 'ep:chat-dot-round', 'dataQuery/queryruntime/session/index', 'DataQueryQuerySession', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13055, '查询审计', '', 2, 2, 13053, 'ai-query-audit', 'ep:document', 'dataQuery/queryruntime/audit/index', 'DataQueryQueryAudit', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13056, '查询模板', '', 2, 3, 13053, 'ai-query-template', 'ep:collection', 'dataQuery/queryruntime/template/index', 'DataQueryQueryTemplate', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13057, '反馈', '', 2, 4, 13053, 'ai-query-feedback', 'ep:star', 'dataQuery/queryruntime/feedback/index', 'DataQueryQueryFeedback', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13058, '问数会话-查询', 'data-query:ai-query-session:query', 3, 1, 13054, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13059, '问数会话-新增', 'data-query:ai-query-session:create', 3, 2, 13054, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13060, '问数会话-修改', 'data-query:ai-query-session:update', 3, 3, 13054, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13061, '问数会话-删除', 'data-query:ai-query-session:delete', 3, 4, 13054, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13062, '问数会话-导出', 'data-query:ai-query-session:export', 3, 5, 13054, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13063, '查询审计-查询', 'data-query:ai-query-audit:query', 3, 1, 13055, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13064, '查询审计-导出', 'data-query:ai-query-audit:export', 3, 2, 13055, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13065, '查询模板-查询', 'data-query:ai-query-template:query', 3, 1, 13056, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13066, '查询模板-新增', 'data-query:ai-query-template:create', 3, 2, 13056, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13067, '查询模板-修改', 'data-query:ai-query-template:update', 3, 3, 13056, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13068, '查询模板-删除', 'data-query:ai-query-template:delete', 3, 4, 13056, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13069, '查询模板-导出', 'data-query:ai-query-template:export', 3, 5, 13056, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13070, '反馈-查询', 'data-query:ai-query-feedback:query', 3, 1, 13057, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13071, '反馈-新增', 'data-query:ai-query-feedback:create', 3, 2, 13057, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13072, '反馈-修改', 'data-query:ai-query-feedback:update', 3, 3, 13057, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13073, '反馈-删除', 'data-query:ai-query-feedback:delete', 3, 4, 13057, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13074, '反馈-导出', 'data-query:ai-query-feedback:export', 3, 5, 13057, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13053), true);

COMMIT;
-- @formatter:on

-- 导入后自检：
-- SELECT count(*) FROM system_menu WHERE id BETWEEN 13053 AND 13074;  -- 期望 22
-- SELECT last_value FROM system_menu_seq;  -- 应 >= 13074


-- ============================================================
-- 23/32 nl2sql-menu-p3.sql
-- ============================================================
/*
 ai 平台 —— 管理端「S3 召回 + S4 提示词渲染(P3)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-14

 覆盖 2 个新增 Controller：
   /data-query/schema-recall    SchemaRecallController（preview）
   /data-query/prompt-runtime   PromptRuntimeController（render）

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13075 召回与装配 (目录)
         ├─ 13076 Schema 召回预览
         │   └─ 13078 按钮：query
         └─ 13077 提示词渲染
             └─ 13079 按钮：query

 ID 分配：13075 ~ 13079（共 5 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008、
   P0 数据源 13009~13042、P1 13043~13052、P2-B 13053~13074 已占用。
   后续新增模块请使用 >= 13080 的 ID。

 幂等策略：只清理本脚本占用的 13075~13079，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 ！！这两个接口是「联调预览 / 自检」端点，P5 的正式 ask 接口会复用其 Service，
    不会直接暴露给终端用户。导入后需重新登录（或刷新权限缓存）才会生效。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-p3.sql
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13075 AND 13079;
DELETE FROM system_menu WHERE id BETWEEN 13075 AND 13079;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13075, '召回与装配', '', 1, 6, 12940, 'recall-render', 'ep:connection', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13076, 'Schema 召回预览', '', 2, 1, 13075, 'schema-recall', 'ep:search', 'dataQuery/recall/preview/index', 'DataQuerySchemaRecall', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13077, '提示词渲染', '', 2, 2, 13075, 'prompt-runtime', 'ep:document-checked', 'dataQuery/prompt/runtime/index', 'DataQueryPromptRuntime', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13078, 'Schema 召回-查询', 'data-query:schema-recall:query', 3, 1, 13076, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0'),
(13079, '提示词渲染-查询', 'data-query:prompt-runtime:query', 3, 1, 13077, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-14 00:00:00', 'admin', '2026-11-14 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13079));

COMMIT;
-- @formatter:on


-- ============================================================
-- 24/32 nl2sql-menu-p4.sql
-- ============================================================
/*
 ai 平台 —— 管理端「S6 SQL 静态校验 + S7 只读执行(P4)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-15

 覆盖 1 个新增 Controller：
   /data-query/sql-guard    SqlGuardController（validate / execute）

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13080 SQL 安全闸门 (目录)
         ├─ 13081 仅校验
         │   └─ 13083 按钮：validate
         └─ 13082 只读执行
             └─ 13084 按钮：execute

 ID 分配：13080 ~ 13084（共 5 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008、
   P0 数据源 13009~13042、P1 13043~13052、P2-B 13053~13074、P3 13075~13079 已占用。
   后续新增模块请使用 >= 13085 的 ID。

 幂等策略：只清理本脚本占用的 13080~13084，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 ！！这两个接口是「闸门自检 / 手工编辑 SQL 后复用」端点，P5 的正式 ask 接口会
    直接复用 SqlGuardService / ReadOnlyQueryService，不另开绕过闸门的执行路径。
    导入后需重新登录（或刷新权限缓存）才会生效。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-p4.sql
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13080 AND 13084;
DELETE FROM system_menu WHERE id BETWEEN 13080 AND 13084;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13080, 'SQL 安全闸门', '', 1, 7, 12940, 'sql-guard', 'ep:lock', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13081, '仅校验 SQL', '', 2, 1, 13080, 'sql-validate', 'ep:circle-check', 'dataQuery/sqlguard/validate/index', 'DataQuerySqlValidate', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13082, '只读执行', '', 2, 2, 13080, 'sql-execute', 'ep:data-analysis', 'dataQuery/sqlguard/execute/index', 'DataQuerySqlExecute', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13083, 'SQL 校验-查询', 'data-query:sql-guard:validate', 3, 1, 13081, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13084, '只读执行-查询', 'data-query:sql-guard:execute', 3, 1, 13082, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13084));

COMMIT;
-- @formatter:on


-- ============================================================
-- 25/32 nl2sql-menu-metadata-import.sql
-- ============================================================
/*
 ai 平台 —— 管理端「表结构一键导入(P5-A)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-15

 覆盖 1 个新增 Controller：
   /data-query/metadata-import    MetadataImportController（preview / import）

 交互：一个表单填连接信息 → 预览将导入的表列 → 一键导入完成
       （创建/复用数据源 → 测连接 → 全量表列同步 → 向量索引）。
 原有「数据源 / 表元数据 / 字段元数据」单条 CRUD 与 /metadata-sync/sync 全部保留，
 仅用于导入后的微调，不再作为接入主链路。

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13009 数据源管理
         └─ 13085 表结构一键导入
             ├─ 13086 按钮：preview
             └─ 13087 按钮：import

 ID 分配：13085 ~ 13087（共 3 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008、
   P0 数据源 13009~13042、P1 13043~13052、P2-B 13053~13074、P3 13075~13079、
   P4 13080~13084 已占用。后续新增模块请使用 >= 13088 的 ID。

 幂等策略：只清理本脚本占用的 13085~13087，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query,public' psql -h localhost -p 5432 -U postgres \
     -d postgres -f sql/postgresql/nl2sql-menu-metadata-import.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13085 AND 13087;
DELETE FROM system_menu WHERE id BETWEEN 13085 AND 13087;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13085, '表结构一键导入', '', 2, 7, 13009, 'metadata-import', 'ep:upload-filled', 'dataQuery/datasource/import/index', 'DataQueryMetadataImport', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13086, '表结构一键导入-预览', 'data-query:metadata-import:preview', 3, 1, 13085, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13087, '表结构一键导入-执行', 'data-query:metadata-import:import', 3, 2, 13085, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13087));

COMMIT;
-- @formatter:on


-- ============================================================
-- 26/32 nl2sql-menu-p5.sql
-- ============================================================
/*
 ai 平台 —— 管理端「智能问数 ask(P5)」菜单与按钮权限初始化
 Target Server Type : PostgreSQL
 版本               : 2026-11-15

 覆盖 1 个新增 Controller：
   /data-query/ai-chat    AiChatController（ask 同步问数）

 菜单树（挂在 nl2sql-menu.sql 已建的 12940 智能问数平台目录下）：
   12940 智能问数平台
     └─ 13088 智能问数
         └─ 13089 按钮：ask

 ID 分配：13088 ~ 13089（共 2 条）
   主菜单 12940~12985、治理 12986~13005、租户初始化 13006~13008、
   P0 数据源 13009~13042、P1 13043~13052、P2-B 13053~13074、P3 13075~13079、
   P4 13080~13084、P5-A 一键导入 13085~13087 已占用。后续新增模块请使用 >= 13090 的 ID。

 幂等策略：只清理本脚本占用的 13088~13089，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 执行（依赖 ruoyi-vue-pro.sql + nl2sql-menu.sql 已导入）：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-p5.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13088 AND 13089;
DELETE FROM system_menu WHERE id BETWEEN 13088 AND 13089;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13088, '智能问数', '', 2, 8, 12940, 'ai-chat', 'ep:chat-dot-round', 'dataQuery/aichat/index', 'DataQueryAiChat', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'),
(13089, '智能问数-提问', 'data-query:ai-chat:query', 3, 1, 13088, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13089));

COMMIT;
-- @formatter:on


-- ============================================================
-- 27/32 nl2sql-menu-p6.sql
-- ============================================================
/*
 ai 平台 —— 管理端「智能问数 P6」菜单与按钮权限增量
 Target Server Type : PostgreSQL
 版本               : 2026-11-15

 本期新增 1 个按钮（SSE 流式问数复用已有的 data-query:ai-chat:query 权限，不另开按钮）：
   12940 智能问数平台
     └─ 13088 智能问数
         ├─ 13089 按钮：ask（P5）
         └─ 13090 按钮：feedback（P6，反馈闭环）

 ID 分配：13090（共 1 条）。13088~13089 已由 nl2sql-menu-p5.sql 占用，
 后续新增模块请使用 >= 13091 的 ID。

 幂等策略：只清理本脚本占用的 13090，不按 permission 前缀清理，
   避免误删其它脚本的 data-query: 按钮；同时清理 system_role_menu 的对应引用。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-p6.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id = 13090;
DELETE FROM system_menu WHERE id = 13090;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13090, '智能问数-结果反馈', 'data-query:ai-chat:feedback', 3, 2, 13088, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13090));

COMMIT;
-- @formatter:on


-- ============================================================
-- 28/32 nl2sql-menu-p7.sql
-- ============================================================
/*
 ai 平台 —— 管理端「智能问数 P7 对话交互」菜单与按钮权限增量
 Target Server Type : PostgreSQL
 版本               : 2026-11-16

 本期新增 0 个菜单（对话交互接口全部挂在已有 13088 智能问数下），
 仅需确认 data-query:ai-chat:query 按钮（13089，P5 已导入）存在即可 ——
 会话列表 / 消息历史 / 重新生成 / 修改问题 / 赞踩 全部复用该权限串，
 前端 DeepSeek 式对话页与智能问数页共用同一权限点，不另开权限。

 ID 分配：无新增菜单 ID，13091+ 保留给后续模块。

 幂等策略：仅做一次存在性校验与权限对齐，不重复插入，不改历史菜单。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-p7.sql
*/
-- @formatter:off
BEGIN;

-- 若 13089 智能问数-查询按钮缺失（极端情况：只跑过 P7 未跑 P5），补一条幂等记录
INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted)
SELECT 13089, '智能问数-查询', 'data-query:ai-chat:query', 3, 1, 13088, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-15 00:00:00', 'admin', '2026-11-15 00:00:00', '0'
WHERE NOT EXISTS (SELECT 1 FROM system_menu WHERE id = 13089);

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13090));

COMMIT;
-- @formatter:on


-- ============================================================
-- 29/32 nl2sql-menu-knowledge.sql
-- ============================================================
/*
 ai 平台 —— 管理端「知识库 RAG」菜单与按钮权限增量（P9.1）
 Target Server Type : PostgreSQL
 版本               : 2026-11-17

 本期调整知识库菜单层级：新增父级目录「知识库管理」，原页面菜单改名为「知识库构建」，
 并使「知识库构建」与「知识库文档」成为同级子菜单：
   12940 智能问数平台 (目录, /data-query)
     └─ 13107 知识库管理 (目录, /data-query/knowledge-base)
         ├─ 13100 知识库构建 (菜单, /data-query/knowledge-base/knowledge)
         │   ├─ 13101 按钮：create  data-query:knowledge-base:create
         │   ├─ 13102 按钮：update  data-query:knowledge-base:update
         │   ├─ 13103 按钮：delete  data-query:knowledge-base:delete
         │   ├─ 13104 按钮：query   data-query:knowledge-base:query
         │   └─ 13105 按钮：export  data-query:knowledge-base:export
         └─ 13106 知识库文档 (菜单, /data-query/knowledge-base/knowledge-document) ← 见 nl2sql-menu-knowledge-document.sql

 说明：
 - simple-list / get-by-code 不单独设权限，沿用 query 按钮或按接口开放（Controller 未加 @PreAuthorize）；
 - 13100 已从「知识库管理」改名为「知识库构建」，但 component = dataQuery/knowledge/index、
   component_name = DataQueryKnowledgeBase 不变（对应前端 src/views/dataQuery/knowledge/index.vue
   与页面 defineOptions({ name })），必须一致，否则路由空白；
 - 新增 13107 为 type=1 目录，path='knowledge-base' 作为子路由前缀，component 为空；
 - 前端源码不在本仓库：component 路径相对 views 与菜单层级无关，改名/调层级不影响页面加载，
   但 URL 增加 /knowledge-base 前缀，引用旧 URL 的地方需同步更新。

 ID 分配：13100 ~ 13105 + 13107（共 7 条）。13107~13109 在 PG 侧为空号，无冲突。

 幂等策略：只清理本脚本占用的 13100~13105 与 13107（不清理 13106，它属于文档脚本），
   不按 permission 前缀清理，避免误删其它脚本的 data-query: 按钮；
   同时清理 system_role_menu 的对应引用。重跑本脚本会清空上述菜单的授权，需重新勾选（super_admin 除外）。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-knowledge.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id BETWEEN 13100 AND 13105 OR menu_id = 13107;
DELETE FROM system_menu WHERE id BETWEEN 13100 AND 13105 OR id = 13107;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13107, '知识库管理', '', 1, 10, 12940, 'knowledge-base', 'ep:collection', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13100, '知识库构建', '', 2, 1, 13107, 'knowledge', 'ep:collection', 'dataQuery/knowledge/index', 'DataQueryKnowledgeBase', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13101, '知识库-创建', 'data-query:knowledge-base:create', 3, 1, 13100, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13102, '知识库-更新', 'data-query:knowledge-base:update', 3, 2, 13100, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13103, '知识库-删除', 'data-query:knowledge-base:delete', 3, 3, 13100, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13104, '知识库-查询', 'data-query:knowledge-base:query', 3, 4, 13100, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13105, '知识库-导出', 'data-query:knowledge-base:export', 3, 5, 13100, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13107));

COMMIT;
-- @formatter:on


-- ============================================================
-- 30/32 nl2sql-menu-knowledge-document.sql
-- ============================================================
/*
 ai 平台 —— 管理端「知识库文档」按钮权限增量（P9.1）
 Target Server Type : PostgreSQL
 版本               : 2026-11-17

 本期调整知识库文档菜单层级 + 修正按钮归属：
   12940 智能问数平台 (目录, /data-query)
     └─ 13107 知识库管理 (目录, /data-query/knowledge-base)
         ├─ 13100 知识库构建 (菜单) ← 操作列「文档管理」带 baseId 跳转到 13106 文档页
         └─ 13106 知识库文档 (菜单, /data-query/knowledge-base/knowledge-document)
             ├─ 13110 按钮：upload  data-query:knowledge-document:upload
             ├─ 13111 按钮：query   data-query:knowledge-document:query  （get/page/export-excel 轮询与列表）
             ├─ 13112 按钮：delete  data-query:knowledge-document:delete
             └─ 13113 按钮：update  data-query:knowledge-document:update  （rebuild 重建复用）

 说明：
 - 13106 改挂到 13107「知识库管理」目录下，与「知识库构建」(13100) 同级；
 - 文档按钮 13110~13113 的 parent_id 从 13100 修正为 13106（原错挂到知识库构建下），权限归属与页面一致；
 - 13106 为文档列表页，component=dataQuery/knowledge/document/index，
   component_name=DataQueryKnowledgeDocument，与前端 defineOptions 一致（均未改动）；
 - 前端源码不在本仓库：component 路径相对 views 与菜单层级无关，调层级不影响页面加载，
   但 URL 增加 /knowledge-base 前缀，引用旧 URL 的地方需同步更新；
 - upload 上传；query 用于 get/page/export-excel；delete 删除；update 用于 rebuild 重建；
 - 切片 page-by-document 与问答接口在后续子步骤补齐。

 ID 分配：13106（页面）+ 13110 ~ 13113（按钮）。13101~13105 已被知识库按钮占用，无冲突。

 幂等策略：只清理本脚本占用的 13106 与 13110~13113，不按 permission 前缀清理，
   同时清理 system_role_menu 的对应引用。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-knowledge-document.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id = 13106 OR menu_id BETWEEN 13110 AND 13113;
DELETE FROM system_menu WHERE id = 13106 OR id BETWEEN 13110 AND 13113;

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13106, '知识库文档', '', 2, 2, 13107, 'knowledge-document', 'ep:document', 'dataQuery/knowledge/document/index', 'DataQueryKnowledgeDocument', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13110, '知识库文档-上传', 'data-query:knowledge-document:upload', 3, 6, 13106, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13111, '知识库文档-查询', 'data-query:knowledge-document:query', 3, 7, 13106, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13112, '知识库文档-删除', 'data-query:knowledge-document:delete', 3, 8, 13106, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0'),
(13113, '知识库文档-重建', 'data-query:knowledge-document:update', 3, 9, 13106, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-17 00:00:00', 'admin', '2026-11-17 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13113));

COMMIT;
-- @formatter:on


-- ============================================================
-- 31/32 nl2sql-menu-knowledge-chat.sql
-- ============================================================
/*
 ai 平台 —— 管理端「知识库问答」菜单与按钮权限增量（P9.2）
 Target Server Type : PostgreSQL
 版本               : 2026-11-18

 本次新增知识库问答页，挂在既有「知识库管理」目录下，与「知识库构建」「知识库文档」同级：
   12940 智能问数平台 (目录, /data-query)
     └─ 13107 知识库管理 (目录, /data-query/knowledge-base)
         ├─ 13100 知识库构建   (菜单, /data-query/knowledge-base/knowledge)
         ├─ 13106 知识库文档   (菜单, /data-query/knowledge-base/knowledge-document)
         └─ 13108 知识库问答   (菜单, /data-query/knowledge-base/knowledge-chat) ← 本脚本
             ├─ 13114 按钮：ask          data-query:knowledge-chat:ask
             ├─ 13115 按钮：query        data-query:knowledge-chat:query
             ├─ 13116 按钮：delete       data-query:knowledge-chat:delete
             └─ 13117 按钮：chunk-query  data-query:knowledge-chunk:query

 说明：
 - 13108 的 component 对应前端 src/views/dataQuery/knowledge/chat/index.vue，
   component_name 必须与页面 defineOptions({ name: 'DataQueryKnowledgeChat' }) 一致，否则路由空白；
 - 前端源码不在本仓库，本脚本只落菜单与权限串；页面未实现时菜单会 404，可先 visible='1' 隐藏再打开；
 - 权限串与 Controller 完全对应：
     AiKnowledgeChatController  /ask     → knowledge-chat:ask
     AiKnowledgeChatController  会话/消息 → knowledge-chat:query
     AiKnowledgeChatController  /session/delete → knowledge-chat:delete
     AiKnowledgeChunkController /page,/get      → knowledge-chunk:query
 - knowledge-chunk 的按钮挂在问答页下而不是文档页下：切片查询的实际用途是核对问答引用，
   放在问答页更符合使用路径（运营在引用里点进去看原文切片）。

 ID 分配：13108、13114 ~ 13117（共 5 条）。13109~13113、13118 在 PG 侧为空号，无冲突。

 幂等策略：只清理本脚本占用的 5 个 id 与其 system_role_menu 引用，
   不按 permission 前缀清理，避免误删其它脚本的 data-query: 按钮。
   重跑会清空上述菜单的授权，需重新勾选（super_admin 除外）。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-knowledge-chat.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id IN (13108, 13114, 13115, 13116, 13117);
DELETE FROM system_menu WHERE id IN (13108, 13114, 13115, 13116, 13117);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13108, '知识库问答', '', 2, 3, 13107, 'knowledge-chat', 'ep:chat-dot-round', 'dataQuery/knowledge/chat/index', 'DataQueryKnowledgeChat', 0, '1', '1', '1', 'admin', '2026-11-18 00:00:00', 'admin', '2026-11-18 00:00:00', '0'),
(13114, '知识库问答-提问',     'data-query:knowledge-chat:ask',    3, 1, 13108, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-18 00:00:00', 'admin', '2026-11-18 00:00:00', '0'),
(13115, '知识库问答-查询',     'data-query:knowledge-chat:query',  3, 2, 13108, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-18 00:00:00', 'admin', '2026-11-18 00:00:00', '0'),
(13116, '知识库问答-删除会话', 'data-query:knowledge-chat:delete', 3, 3, 13108, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-18 00:00:00', 'admin', '2026-11-18 00:00:00', '0'),
(13117, '知识库切片-查询',     'data-query:knowledge-chunk:query', 3, 4, 13108, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-18 00:00:00', 'admin', '2026-11-18 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13117));

COMMIT;
-- @formatter:on


-- ============================================================
-- 32/32 nl2sql-menu-unified-chat.sql
-- ============================================================
/*
 ai 平台 —— 管理端「统一对话」菜单与按钮权限（P9.3）
 Target Server Type : PostgreSQL
 版本               : 2026-11-19

 背景：智能问数（/data-query/ai-chat，13088）与知识库问答（/data-query/knowledge-chat，13108）
 原为两个对话页。本脚本新增一个「统一对话」入口，前端在一个页面里放模式选择器
 （mode=QUERY 智能问数 / KNOWLEDGE 知识库问答），后端统一走 /data-query/chat 下的端点：

   12940 智能问数平台 (目录)
     ├─ 13088 智能问数   (旧，保留兼容)
     ├─ 13108 知识库问答 (旧，保留兼容)
     └─ 13118 统一对话   (菜单, /data-query/chat) ← 本脚本
         └─ 13119 按钮：query  data-query:chat:query

 说明：
- 前端 component 对应 src/views/dataQuery/chat/index.vue，component_name 必须与页面
  defineOptions({ name: 'DataQueryChat' }) 一致；页面已实现，visible='1' 才会显示在侧边栏
  （前端 hidden = !visible，写成 '0' 会被隐藏，切勿改成 '0'）；
 - 后端所有端点权限统一 data-query:chat:query：
     UnifiedChatController  /ask、/ask-stream、/session 与 message 各端点、
                            /regenerate、/edit、/rating、/retrieve-preview
 - 旧两个对话页与权限串保留一个过渡期，前端迁移完成后再下线，不做破坏性删除；
 - 本脚本只占用 13118、13119 两个 id（PG 侧为空号，无冲突）。

 幂等策略：只清理本脚本占用的 2 个 id 与其 system_role_menu 引用，
   不按 permission 前缀清理，避免误删其它脚本的 data-query: 按钮。
   重跑会清空该菜单的授权，需重新勾选（super_admin 除外）。

 执行：
   PGOPTIONS='-c search_path=data_query' psql -h localhost -p 5432 -U postgres -d postgres \
     -f sql/postgresql/nl2sql-menu-unified-chat.sql

 导入后需重新登录（或刷新权限缓存）才会生效。
*/
-- @formatter:off
BEGIN;

DELETE FROM system_role_menu WHERE menu_id IN (13118, 13119);
DELETE FROM system_menu WHERE id IN (13118, 13119);

INSERT INTO system_menu (id, name, permission, type, sort, parent_id, path, icon, component, component_name, status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted) VALUES
(13118, '统一对话', '', 2, 2, 12940, 'chat', 'ep:chat-line-round', 'dataQuery/chat/index', 'DataQueryChat', 0, '1', '1', '1', 'admin', '2026-11-19 00:00:00', 'admin', '2026-11-19 00:00:00', '0'),
(13119, '统一对话-查询', 'data-query:chat:query', 3, 1, 13118, '', '', '', '', 0, '1', '1', '1', 'admin', '2026-11-19 00:00:00', 'admin', '2026-11-19 00:00:00', '0')
ON CONFLICT (id) DO NOTHING;

-- 默认授权给 super_admin（role_id=1），避免导入后菜单不可见；其他角色按业务自行勾选
INSERT INTO system_role_menu (id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT nextval('system_role_menu_seq'), 1, m.id, 'admin', NOW(), 'admin', NOW(), '0', 1
FROM system_menu m
WHERE m.id IN (13118, 13119)
  AND NOT EXISTS (
    SELECT 1 FROM system_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id
  );

SELECT setval('system_menu_seq', GREATEST(
    (SELECT COALESCE(MAX(id), 0) FROM system_menu),
    (SELECT last_value FROM system_menu_seq),
    13119));

COMMIT;
-- @formatter:on

