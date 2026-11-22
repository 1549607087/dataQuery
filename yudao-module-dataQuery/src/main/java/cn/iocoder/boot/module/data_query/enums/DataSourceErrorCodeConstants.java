package cn.iocoder.boot.module.data_query.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 数据源管理（M3）错误码
 *
 * 自定义模块使用 1-099-000-000 段，避开芋道内置模块的 1-001 ~ 1-0xx 段。
 */
public interface DataSourceErrorCodeConstants {

    ErrorCode TENANT_CONTEXT_MISSING = new ErrorCode(1_099_000_001, "无法获取当前租户上下文，禁止写入数据源数据");
    ErrorCode DATASOURCE_ID_REQUIRED = new ErrorCode(1_099_000_002, "数据源编号不能为空");
    ErrorCode DATASOURCE_NOT_EXISTS = new ErrorCode(1_099_000_003, "数据源不存在或无权访问");
    ErrorCode DATASOURCE_CODE_DUPLICATE = new ErrorCode(1_099_000_004, "数据源编码已存在");
    ErrorCode DATASOURCE_OWNERSHIP_MISMATCH = new ErrorCode(1_099_000_005, "不允许跨数据源转移元数据");
    ErrorCode DATASOURCE_DRIVER_NOT_ALLOWED = new ErrorCode(1_099_000_006, "JDBC 驱动类不在允许范围内，请检查配置");
    ErrorCode DATASOURCE_CONNECTION_CONFIG_EMPTY = new ErrorCode(1_099_000_007, "数据源连接配置不完整（缺少 URL 或驱动）");
    ErrorCode DATASOURCE_TABLE_NOT_EXISTS = new ErrorCode(1_099_000_008, "表元数据不存在或无权访问");
    ErrorCode DATASOURCE_COLUMN_NOT_EXISTS = new ErrorCode(1_099_000_009, "字段元数据不存在或无权访问");
    ErrorCode DATASOURCE_COLUMN_ENUM_NOT_EXISTS = new ErrorCode(1_099_000_010, "枚举映射不存在或无权访问");
    ErrorCode DATASOURCE_TABLE_RELATION_NOT_EXISTS = new ErrorCode(1_099_000_011, "Join 路径不存在或无权访问");
    ErrorCode DATASOURCE_SYNC_LOG_NOT_EXISTS = new ErrorCode(1_099_000_012, "同步日志不存在或无权访问");

    // P1：元数据同步 + Schema 向量索引
    ErrorCode SCHEMA_EMBEDDING_NOT_CONFIGURED = new ErrorCode(1_099_000_013,
            "当前租户尚未接入可用的 Embedding 模型，向量已按待构建状态入库（P2 接线后自动补齐）");
    ErrorCode SCHEMA_VECTOR_DIM_MISMATCH = new ErrorCode(1_099_000_014,
            "Embedding 维度与 ai_schema_vector 的 vector(1536) 列不一致，禁止写入");
    ErrorCode SCHEMA_VECTOR_NOT_EXISTS = new ErrorCode(1_099_000_015, "Schema 向量不存在或无权访问");
    ErrorCode METADATA_SYNC_FAILED = new ErrorCode(1_099_000_016,
            "元数据同步失败：无法连接目标数据源或读取元数据，详见同步日志");
    ErrorCode METADATA_SYNC_NO_TABLE = new ErrorCode(1_099_000_017,
            "未采集到任何表，请检查 schema 白名单、表过滤规则与数据库账号权限");
    ErrorCode METADATA_VISIBILITY_IDS_EMPTY = new ErrorCode(1_099_000_018,
            "批量设置可见性时，表编号与字段编号不能同时为空");

    // P2：按租户动态 Embedding 接线
    ErrorCode SCHEMA_EMBEDDING_BINDING_MISSING = new ErrorCode(1_099_000_019,
            "当前租户未配置启用的 EMBEDDING 场景绑定，请先初始化模型配置");
    ErrorCode SCHEMA_EMBEDDING_CONFIG_MISSING = new ErrorCode(1_099_000_020,
            "EMBEDDING 场景绑定未指向本租户可用的 model_type=2 模型配置");
    ErrorCode SCHEMA_EMBEDDING_PROVIDER_MISSING = new ErrorCode(1_099_000_021,
            "Embedding 模型配置未关联本租户的供应商记录");
    ErrorCode SCHEMA_EMBEDDING_CREDENTIAL_MISSING = new ErrorCode(1_099_000_022,
            "本租户 Embedding 凭证 / API 地址 / 模型代号不完整，请由租户管理员录入");
    ErrorCode SCHEMA_EMBEDDING_PROTOCOL_UNSUPPORTED = new ErrorCode(1_099_000_023,
            "该供应商协议暂不支持，P2 仅支持 OPENAI 兼容的 embeddings 接口");
    ErrorCode SCHEMA_EMBEDDING_RATE_LIMITED = new ErrorCode(1_099_000_024,
            "本租户 Embedding 调用被限流或并发队列已满，请稍后重试");
    ErrorCode SCHEMA_EMBEDDING_CIRCUIT_OPEN = new ErrorCode(1_099_000_025,
            "本租户 Embedding 熔断已打开，请检查供应商可用性后再重试");
    ErrorCode SCHEMA_EMBEDDING_CALL_FAILED = new ErrorCode(1_099_000_026,
            "Embedding 调用失败，向量保留待构建状态，下次同步自动补齐，详见调用日志");

    // P2-B：问数运行时（会话 / 查询审计 / 查询模板 / 反馈）
    ErrorCode QUERY_SESSION_NOT_EXISTS = new ErrorCode(1_099_000_027, "问数会话不存在或无权访问");
    ErrorCode QUERY_AUDIT_NOT_EXISTS = new ErrorCode(1_099_000_028, "查询审计不存在或无权访问");
    ErrorCode QUERY_TEMPLATE_NOT_EXISTS = new ErrorCode(1_099_000_029, "查询模板不存在或无权访问");
    ErrorCode QUERY_FEEDBACK_NOT_EXISTS = new ErrorCode(1_099_000_030, "反馈不存在或无权访问");

    // P3-A：S3 Schema 召回
    ErrorCode SCHEMA_RECALL_QUESTION_EMPTY = new ErrorCode(1_099_000_031, "召回问题不能为空");

    // P3-B：S4 提示词运行时渲染
    ErrorCode PROMPT_RENDER_SCENE_EMPTY = new ErrorCode(1_099_000_032, "提示词场景编码不能为空");
    ErrorCode PROMPT_RENDER_TEMPLATE_NOT_FOUND = new ErrorCode(1_099_000_033,
            "该场景没有启用的提示词模板（租户覆盖与平台默认均不可用）");
    ErrorCode PROMPT_RENDER_VERSION_NOT_FOUND = new ErrorCode(1_099_000_034,
            "提示词模板未锁定到已发布的版本快照，请先发布版本");
    ErrorCode PROMPT_RENDER_VARIABLE_UNDECLARED = new ErrorCode(1_099_000_035,
            "提示词快照含未在 variable_defs 登记的占位符，拒绝渲染");
    ErrorCode PROMPT_RENDER_VARIABLE_MISSING = new ErrorCode(1_099_000_036,
            "提示词必填变量缺失，模板停用或变量缺失时不允许拼字符串兜底生成");

    // P4-A：S6 SQL 静态校验
    ErrorCode SQL_GUARD_SQL_EMPTY = new ErrorCode(1_099_000_037, "待校验的 SQL 文本为空");
    ErrorCode SQL_GUARD_SQL_TOO_LONG = new ErrorCode(1_099_000_038, "SQL 文本超出长度上限，请缩减后重试");
    ErrorCode SQL_GUARD_WHITELIST_MISSING = new ErrorCode(1_099_000_039,
            "缺少 S3 召回白名单：必须提供 schemaContext 或 question 由服务端自行召回，禁止无白名单执行");
    ErrorCode SQL_GUARD_WHITELIST_EMPTY = new ErrorCode(1_099_000_040,
            "S3 召回白名单为空（无可见可查的表列），拒绝执行，请先做元数据同步或调整问题");
    ErrorCode SQL_GUARD_PARSE_FAILED = new ErrorCode(1_099_000_041, "SQL 解析失败，仅接受单条标准 SELECT 语句");
    ErrorCode SQL_GUARD_MULTI_STATEMENT = new ErrorCode(1_099_000_042, "检测到多语句（分号拼接），拒绝执行");
    ErrorCode SQL_GUARD_UNSUPPORTED_STATEMENT = new ErrorCode(1_099_000_043,
            "语句类型不受支持：仅允许单条 SELECT / WITH ... SELECT，写操作、DDL、UNION、VALUES、TABLE 一律拒绝");
    ErrorCode SQL_GUARD_FORBIDDEN_CLAUSE = new ErrorCode(1_099_000_044,
            "SQL 含被禁止的子句（SELECT INTO / FOR UPDATE / FOR SHARE / FETCH / 锁等待），只读闸门拒绝执行");
    ErrorCode SQL_GUARD_SELECT_STAR = new ErrorCode(1_099_000_045,
            "禁止 SELECT * / table.*：必须显式列出白名单内的字段（count(*) 等聚合不受影响）");
    ErrorCode SQL_GUARD_DENY_KEYWORD = new ErrorCode(1_099_000_046, "SQL 命中禁用关键字，拒绝执行");
    ErrorCode SQL_GUARD_DENY_FUNCTION = new ErrorCode(1_099_000_047, "SQL 命中禁用函数，拒绝执行");
    ErrorCode SQL_GUARD_PARAMETER_NOT_ALLOWED = new ErrorCode(1_099_000_048,
            "SQL 含参数占位符，静态闸门不接受未绑定参数的 SQL，请改写为字面量");
    ErrorCode SQL_GUARD_TABLE_NOT_ALLOWED = new ErrorCode(1_099_000_049,
            "SQL 引用了不在 S3 白名单内的表（越权表、幻觉表或已下线表）");
    ErrorCode SQL_GUARD_COLUMN_NOT_ALLOWED = new ErrorCode(1_099_000_050,
            "SQL 引用了不在 S3 白名单内的字段，或命中 mask_rule=forbid 的敏感字段");
    ErrorCode SQL_GUARD_COMPLEXITY_EXCEEDED = new ErrorCode(1_099_000_051,
            "SQL 复杂度超限（子查询数 / 表数 / 列数 / JOIN 数），请缩小查询范围");
    ErrorCode SQL_GUARD_LIMIT_INVALID = new ErrorCode(1_099_000_052,
            "LIMIT 必须是正整数常量且不超过 max_rows，LIMIT ALL / 参数 / 表达式一律拒绝");

    // P4-B：S7 只读执行
    ErrorCode SQL_GUARD_CONNECTION_FAILED = new ErrorCode(1_099_000_053,
            "只读数据源连接失败，请检查连接配置与只读账号（错误摘要已脱敏）");
    ErrorCode SQL_GUARD_EXECUTE_FAILED = new ErrorCode(1_099_000_054,
            "只读查询执行失败，请检查 SQL 与数据源状态（错误摘要已脱敏）");
    ErrorCode SQL_GUARD_QUERY_TIMEOUT = new ErrorCode(1_099_000_055,
            "查询超时，请缩小时间范围或增加过滤条件");

    // P5-A：表结构一键导入（参数 / 归属类错误走业务异常；连接 / 同步 / 向量失败走结构化报告）
    ErrorCode METADATA_IMPORT_MODE_INVALID = new ErrorCode(1_099_000_056,
            "一键导入缺少定位信息：请提供 datasourceId，或提供新建数据源所需的连接信息");
    ErrorCode METADATA_IMPORT_NEW_MISSING_FIELDS = new ErrorCode(1_099_000_057,
            "新建数据源缺少必填项：name / code / jdbcUrl（schemaName 建议显式指定以收窄扫描范围）");
    ErrorCode METADATA_IMPORT_CODE_CONFLICT = new ErrorCode(1_099_000_058,
            "数据源编码已被占用且连接配置与本次提交不一致：请显式传 datasourceId 重试，或换一个 code");
    ErrorCode METADATA_IMPORT_TABLE_LIMIT = new ErrorCode(1_099_000_059,
            "扫描到的表数量超过单次导入上限：请配置 schema 白名单或表过滤规则后重试，避免误导入整库");
    ErrorCode METADATA_IMPORT_DRIVER_UNKNOWN = new ErrorCode(1_099_000_060,
            "无法按数据库类型推断 JDBC 驱动，请显式提供 driverClass");
    ErrorCode METADATA_IMPORT_CONNECTION_FAILED = new ErrorCode(1_099_000_061,
            "目标库连接失败，未导入任何表结构（错误摘要已脱敏）");
    ErrorCode METADATA_IMPORT_SYNC_FAILED = new ErrorCode(1_099_000_062,
            "元数据同步失败，数据源已创建但表结构未完整导入（错误摘要已脱敏）");
    ErrorCode METADATA_IMPORT_VECTOR_FAILED = new ErrorCode(1_099_000_063,
            "向量索引构建异常，元数据导入已完成（非阻断，可稍后重建）");

    // P5：问数编排器（ask 同步接口 + 真实 LLM 客户端）
    ErrorCode AI_CHAT_QUESTION_EMPTY = new ErrorCode(1_099_000_064, "问题不能为空");
    ErrorCode AI_CHAT_QUESTION_TOO_LONG = new ErrorCode(1_099_000_065, "问题长度超过上限，请精简后重试");
    ErrorCode AI_CHAT_DATASOURCE_DISABLED = new ErrorCode(1_099_000_066, "数据源已停用，请先启用后再问数");
    ErrorCode AI_CHAT_DATASOURCE_UNHEALTHY = new ErrorCode(1_099_000_067, "数据源连通性异常，请先检测连接");
    ErrorCode AI_CHAT_MODEL_NOT_READY = new ErrorCode(1_099_000_068,
            "当前租户尚未接入可用的 Chat 大模型（或仍未替换 EchoModelClient），无法生成 SQL");
    ErrorCode AI_CHAT_RECALL_EMPTY = new ErrorCode(1_099_000_069,
            "未在数据源中召回到与问题相关的表，请补充问题或先同步元数据");
    ErrorCode AI_CHAT_PROMPT_INJECTION = new ErrorCode(1_099_000_070,
            "提问包含疑似提示词注入 / 越权指令，已拒绝处理，请只描述要查询的业务问题");
    ErrorCode AI_CHAT_MODEL_CALL_FAILED = new ErrorCode(1_099_000_071,
            "大模型调用失败（限流 / 鉴权 / 超时 / 供应商异常已按治理链重试降级），请稍后重试或检查模型配置与凭证");

    // P6：SSE 流式与反馈闭环
    ErrorCode AI_CHAT_SSE_DISABLED = new ErrorCode(1_099_000_072, "流式问数当前未启用，请使用同步问数接口");
    ErrorCode AI_CHAT_SSE_BUSY = new ErrorCode(1_099_000_073, "当前流式问数请求过多，请稍后重试");
    ErrorCode AI_CHAT_FEEDBACK_AUDIT_NOT_FOUND = new ErrorCode(1_099_000_074,
            "反馈关联的查询记录不存在或无权访问");
    ErrorCode AI_CHAT_FEEDBACK_NOT_OWNED = new ErrorCode(1_099_000_075, "只能评价本人发起的问数结果");

    // P7：对话式交互（会话列表 / 消息历史 / 重新生成 / 修改问题 / 赞踩）
    ErrorCode AI_CHAT_SESSION_NOT_OWNED = new ErrorCode(1_099_000_076, "会话不存在或无权访问");
    ErrorCode AI_CHAT_MESSAGE_NOT_FOUND = new ErrorCode(1_099_000_077, "消息不存在或无权访问");
    ErrorCode AI_CHAT_MESSAGE_ROLE_INVALID = new ErrorCode(1_099_000_078,
            "消息角色不符合操作要求（重新生成需助手消息，修改问题需用户消息）");
    ErrorCode AI_CHAT_REGENERATE_DISABLED = new ErrorCode(1_099_000_079, "该消息暂不可重新生成");
    ErrorCode AI_CHAT_RATING_INVALID = new ErrorCode(1_099_000_080, "评分值只能是 -1（踩）、0（取消）、1（赞）");

    // P9.1：知识库 RAG（租户私有的知识库/文档/切片）
    ErrorCode KNOWLEDGE_BASE_NOT_EXISTS = new ErrorCode(1_099_000_081, "知识库不存在或无权访问");
    ErrorCode KNOWLEDGE_BASE_CODE_DUPLICATE = new ErrorCode(1_099_000_082, "知识库业务编码已存在");
    ErrorCode KNOWLEDGE_BASE_DISABLED = new ErrorCode(1_099_000_083, "知识库已停用，不能上传文档");
    ErrorCode KNOWLEDGE_DOCUMENT_FILE_TYPE_NOT_ALLOWED = new ErrorCode(1_099_000_084,
            "不支持的文件类型，仅允许：TXT、MD、PDF、DOC、DOCX");
    ErrorCode KNOWLEDGE_DOCUMENT_FILE_TOO_LARGE = new ErrorCode(1_099_000_085, "文件大小超出上限（{0} MB）");
    ErrorCode KNOWLEDGE_DOCUMENT_FILE_EMPTY = new ErrorCode(1_099_000_086, "文件内容不能为空");
    ErrorCode KNOWLEDGE_DOCUMENT_UPLOAD_FAILED = new ErrorCode(1_099_000_087, "文档上传失败，请稍后重试");
    ErrorCode KNOWLEDGE_DOCUMENT_NOT_EXISTS = new ErrorCode(1_099_000_088, "知识库文档不存在或无权访问");

    // P9.2：知识库会话式问答
    ErrorCode KNOWLEDGE_CHAT_BASE_ID_REQUIRED = new ErrorCode(1_099_000_089, "知识库编号不能为空");
    ErrorCode KNOWLEDGE_CHAT_SESSION_NOT_EXISTS = new ErrorCode(1_099_000_090,
            "知识库会话不存在或无权访问");
    ErrorCode KNOWLEDGE_CHAT_SESSION_BASE_MISMATCH = new ErrorCode(1_099_000_091,
            "会话绑定的知识库与本次请求不一致，请新建会话");
    ErrorCode KNOWLEDGE_CHAT_BASE_DISABLED = new ErrorCode(1_099_000_092,
            "知识库已停用，不能进行问答");
    ErrorCode KNOWLEDGE_CHAT_RECALL_EMPTY = new ErrorCode(1_099_000_093,
            "未在知识库中检索到与问题相关的内容，请补充关键词或先完成文档解析");
    ErrorCode KNOWLEDGE_CHAT_MODEL_NOT_READY = new ErrorCode(1_099_000_094,
            "当前租户尚未接入可用的 Chat 大模型（或仍未替换 EchoModelClient），无法生成知识问答");
    ErrorCode KNOWLEDGE_CHAT_MODEL_CALL_FAILED = new ErrorCode(1_099_000_095,
            "知识问答模型调用失败，请稍后重试或检查模型配置与凭证");
    ErrorCode KNOWLEDGE_CHAT_PROMPT_INJECTION = new ErrorCode(1_099_000_096,
            "提问包含疑似提示词注入 / 越权指令，已拒绝处理");
    ErrorCode KNOWLEDGE_CHAT_MESSAGE_NOT_FOUND = new ErrorCode(1_099_000_097,
            "知识库消息不存在或无权访问");
    ErrorCode KNOWLEDGE_CHAT_EMPTY_ANSWER = new ErrorCode(1_099_000_098, "模型返回空回答，请重试");
    ErrorCode KNOWLEDGE_CHUNK_NOT_EXISTS = new ErrorCode(1_099_000_099, "知识库切片不存在或无权访问");

    // 表格叙述化失败仅落日志、降级保留原表文本，不抛给前端；此处登记错误码便于日志/监控按码聚合
    ErrorCode KNOWLEDGE_TABLE_NARRATE_FAILED = new ErrorCode(1_099_000_100, "表格叙述化失败，已保留原表格内容");

    // ==================== 统一对话门面（/data-query/chat/*）====================
    ErrorCode CHAT_MODE_INVALID = new ErrorCode(1_099_000_101, "对话类型无效，仅支持 QUERY / KNOWLEDGE");
    ErrorCode CHAT_SESSION_MODE_MISMATCH = new ErrorCode(1_099_000_102,
            "会话的类型与本次请求不一致，切换对话方式请新建会话");
    ErrorCode CHAT_RESOURCE_REQUIRED = new ErrorCode(1_099_000_103,
            "智能问数需指定数据源，知识库问答需指定知识库");
    ErrorCode CHAT_SESSION_NOT_FOUND = new ErrorCode(1_099_000_104, "会话不存在或无权访问");
    ErrorCode CHAT_MESSAGE_NOT_FOUND = new ErrorCode(1_099_000_105, "消息不存在或无权访问");
    ErrorCode CHAT_PREVIEW_BASE_REQUIRED = new ErrorCode(1_099_000_106, "检索调试需指定知识库编号");
    ErrorCode CHAT_RATING_INVALID = new ErrorCode(1_099_000_107, "评分值只能是 -1（踩）、0（取消）、1（赞）");
    ErrorCode CHAT_MESSAGE_ROLE_INVALID = new ErrorCode(1_099_000_108, "该操作仅能作用于指定角色的消息");

    // ==================== 知识库 Rerank 精排（默认关闭，失败降级 RRF 原序）====================
    // 这些错误码只落调用日志 / 召回 skipReason，不直接抛给前端；登记便于日志按码聚合
    ErrorCode KNOWLEDGE_RERANK_BINDING_MISSING = new ErrorCode(1_099_000_109,
            "当前租户未配置启用的 RERANK 场景绑定");
    ErrorCode KNOWLEDGE_RERANK_CONFIG_MISSING = new ErrorCode(1_099_000_110,
            "RERANK 绑定未指向可用的 model_type=3 模型配置");
    ErrorCode KNOWLEDGE_RERANK_PROVIDER_MISSING = new ErrorCode(1_099_000_111,
            "Rerank 模型配置未关联本租户供应商");
    ErrorCode KNOWLEDGE_RERANK_CREDENTIAL_MISSING = new ErrorCode(1_099_000_112,
            "本租户 Rerank 凭证 / base_url / model_id 不完整");
    ErrorCode KNOWLEDGE_RERANK_PROTOCOL_UNSUPPORTED = new ErrorCode(1_099_000_113,
            "Rerank 供应商协议暂不支持（仅 DASHSCOPE / COHERE）");
    ErrorCode KNOWLEDGE_RERANK_RATE_LIMITED = new ErrorCode(1_099_000_114,
            "本租户 Rerank 调用被限流或并发队列已满");
    ErrorCode KNOWLEDGE_RERANK_CIRCUIT_OPEN = new ErrorCode(1_099_000_115,
            "本租户 Rerank 熔断已打开");
    ErrorCode KNOWLEDGE_RERANK_CALL_FAILED = new ErrorCode(1_099_000_116,
            "Rerank 调用失败，已按 RRF 原序降级");
    ErrorCode KNOWLEDGE_RERANK_SCORE_MISMATCH = new ErrorCode(1_099_000_117,
            "Rerank 返回分数数量不符或含非法值，已按 RRF 原序降级");

    // ==================== 知识库文档版本链（增量更新与版本回溯）====================
    ErrorCode KNOWLEDGE_DOC_VERSION_NOT_FOUND = new ErrorCode(1_099_000_118, "指定的文档版本不存在或已删除");
    ErrorCode KNOWLEDGE_DOC_DOCKEY_REQUIRED = new ErrorCode(1_099_000_119, "切换版本需指定文档标识 docKey");
    ErrorCode KNOWLEDGE_DOC_VERSION_SWITCH_SAME = new ErrorCode(1_099_000_120, "目标版本已是当前生效版本");
    // 以下两个只落日志 / 召回 skipReason，不抛给前端
    ErrorCode KNOWLEDGE_DOC_VERSION_PREVIOUS_MISSING = new ErrorCode(1_099_000_121,
            "增量复用的上一版切片缺失，已按全量重建");
    ErrorCode KNOWLEDGE_RECALL_VERSION_INVALID = new ErrorCode(1_099_000_122,
            "回溯版本值非法，已降级为默认最新版本");

    ErrorCode CHAT_CANCEL_NOT_FOUND = new ErrorCode(1_099_000_123,
            "请求已结束或不存在，无法停止");

}
