# 项目事实备忘（project-notes）

> 本文件是从 `AGENTS.md` 拆出的**参考资料**部分：技术栈、目录结构、运行配置、测试清单、待确认事项。
> `AGENTS.md` 只保留「必须遵守的规则」，不预加载本文件——需要具体事实时再查。
>
> 内容基于对仓库的静态分析 + 后续变更同步（初始 2026-11-22，最近更新 2026-11-24）。未验证的信息一律标注「待补充 / 待确认」，请勿臆造；
> 若发现与代码不一致，**以代码为准**并回来更新本文件。

---

## 1. 用途与设计文档索引

### 1.1 用途

本项目是基于 **芋道（yudao / ruoyi-vue-pro）脚手架** 的后端多模块工程，在其之上新增了核心业务模块
**`yudao-module-dataQuery`（智能问数 / NL2SQL + RAG）**。该模块把业务人员的中文问题转换为
「在租户私有数据源上安全执行的只读 SQL + 结果表格/图表 + 口径解释」，并提供独立的知识库问答旁路。

- 脚手架本体定位为通用快速开发平台（系统管理、基础设施、代码生成、多租户等），详见根目录 `README.md`。
- 项目自身的业务价值集中在 `yudao-module-dataQuery` 与 `docs/`、`sql/postgresql/nl2sql-*.sql`。

### 1.2 设计文档（**权威业务口径来源**）

| 文档 | 内容 |
|---|---|
| `docs/intelligent-query-flow.md` | 问数主链 S0~S9 的设计与落地状态 |
| `docs/knowledge-rag-flow.md` | 知识库 RAG 旁路、RocketMQ 异步入库、问答链路 |
| `docs/knowledge-rag-streaming-design.md` | 知识库问答流式输出设计 |
| `docs/knowledge-doc-versioning-design.md` | 文档版本管理设计 |
| `docs/tenant-isolation-design.md` | 多租户凭证隔离方案（已评审落地） |
| `docs/deepseek-web-chat-design.md` | DeepSeek Web 会话相关设计 |
| `docs/knowledge-token-optimization-changes.md` | 知识库问答省 token 变更说明（①②③④⑤，含回退方式与验证结果） |

---

## 2. 技术栈（以实际 pom 为准）

| 维度 | 技术 / 版本 | 依据 |
|---|---|---|
| 语言 / 编译 | Java 17，UTF-8 | 根 `pom.xml` `java.version=17` |
| 构建 | Maven 多模块（无 mvnw wrapper） | 根 `pom.xml` |
| 框架 | Spring Boot（**版本有冲突，见 §6**）：根 pom `4.1.2`；`yudao-dependencies` 与 README 为 `3.5.15` | 两处 pom + README |
| Spring AI | BOM（**版本有冲突**）：根 pom `2.0.1`；`yudao-dependencies` 与文档为 `1.1.8` | 两处 pom + docs |
| ORM / DB | MyBatis Plus + dynamic-datasource + Druid；**当前 local 主库为 PostgreSQL**（`data_query` schema，需 pgvector 扩展） | `application-local.yaml`、设计文档 |
| 缓存 | Redis + Redisson | `application.yaml` |
| 向量 | pgvector `vector(1536)` + HNSW，自建 DAO（`PgVectorTypeHandler`），**不引入任何 Spring AI VectorStore Bean** | 设计文档、`application.yaml` 注释 |
| Embedding / Chat | 每租户独立账号，经 `TenantOpenAiChatModelClient` / `TenantSchemaEmbeddingProvider` 动态构造 | 设计文档 |
| 消息队列 | 知识库入库用原生 `rocketmq-client-java 5.2.0`（gRPC SDK，Proxy 模式）；框架其它模块用 `rocketmq-spring-boot-starter`（Remoting），两者可共存 | `yudao-dependencies/pom.xml`、`knowledge-rag-flow.md` |
| 文档解析 | Apache POI `poi-ooxml 5.2.5`（DOCX）；PDF 用 Spring AI 官方 `spring-ai-pdf-document-reader`（底层 PDFBox 3.0.7，`PdfDocumentParser` 适配）；MD 用官方 `MarkdownDocumentReader`（`SpringAiMarkdownParser` 适配） | `yudao-module-dataQuery/pom.xml:144`、`parser/` 目录 |
| 其他 | Lombok、MapStruct、Flowable 8.0.0、Quartz、Springdoc/Knife4j、JUnit 5 + Mockito + AssertJ | README、pom |

---

## 3. 目录结构与职责

```
dataQuery/
├── pom.xml                      根聚合 pom：版本属性、dependencyManagement（导入 yudao-dependencies + spring-ai-bom）、编译器/flatten 插件
├── README.md                    上游芋道脚手架说明（功能/技术栈/版本），非本项目业务口径
├── AGENTS.md                    ★ 协作规则（必读，精简版）
├── docs/project-notes.md        本文件：项目事实参考
├── yudao-dependencies/pom.xml   全项目依赖版本管理（BOM）
├── yudao-framework/             框架拓展 starter（common / mybatis / redis / web / security / tenant / mq / websocket / excel / job / ...）
├── yudao-server/                启动与主配置模块
│   └── src/main/resources/      application.yaml（主配置）+ application-{local,dev}.yaml + logback-spring.xml
├── yudao-module-system/         系统功能（用户/角色/菜单/租户等，芋道原生）
├── yudao-module-infra/          基础设施（文件/代码生成/任务/日志等，芋道原生）
├── yudao-module-dataQuery/      ★ 业务核心模块（智能问数 + 知识库 RAG）
│   └── src/main/java/cn/iocoder/boot/module/data_query/
│       ├── controller/admin/    REST 接口，按领域分包（aichat / datasource / knowledge / model / prompt / recall / sqlguard / queryruntime / metadataimport / tenant / vector / sync）
│       │   └── */vo/            请求/响应 VO
│       ├── entity/              数据对象（DO/POJO，@TableName，如 AiKnowledgeBase）
│       ├── mapper/              MyBatis Mapper
│       ├── service/ + impl/     业务服务接口与实现
│       ├── enums/               错误码常量（如 DataSourceErrorCodeConstants）
│       ├── framework/           领域能力组件：chat / recall / sqlguard / vector / importmeta / model / tenant / knowledge / crypto / datasource
│       │   └── knowledge/       文档解析（parser/）、切块（chunk/）、表格叙述（table/）、摄取（KnowledgeIngestService）
│       └── DataQueryAutoConfiguration.java  模块自动配置
├── sql/
│   ├── postgresql/              ★ 本项目业务 DDL（nl2sql-*.sql：数据源、向量、知识库、模型治理、提示词、问数运行时、菜单等）
│   └── {mysql,oracle,dm,...}/   芋道各数据库初始化脚本（脚手架自带）
├── docs/                        ★ 本项目设计文档（见 §1.2）
├── script/                      部署/中间件脚本（docker-compose、seata、sentinel、sharding、shell、jenkins、idea 等；部分为芋道自带）
├── yudao-ui/                    前端目录（**当前为空壳，无前端工程**；设计文档标注「待建」）
└── .workbuddy/                  会话工作数据（非缓存，禁止删除）
```

---

## 4. 本地运行

### 4.1 前置条件

- **JDK 17**（根 pom 要求）。
- **Maven 3.9+**（仓库未附 wrapper；本机已验证可用的 Maven 位于 `D:\DevelopmentRelated\apache-maven-3.9.9`）。
- **PostgreSQL**（local 配置指向 `jdbc:postgresql://localhost:5432/postgres?currentSchema=data_query`，账号 `postgres/root`）
  并启用 **pgvector** 扩展；需导入 `sql/postgresql/` 下的初始化脚本（`ruoyi-vue-pro.sql`、`quartz.sql` 及各 `nl2sql-*.sql`）。
  脚本实际执行方式与顺序：**待补充**（仓库内无一键初始化脚本说明）。
- **Redis**（local 配置 `127.0.0.1:6379`，database 0）。
- 启动 profile 由 `application.yaml` 中 `spring.profiles.active: local` 决定。

### 4.2 启动

主启动类：`yudao-server` 模块的 `cn.iocoder.yudao.server.YudaoServerApplication`；其 `scanBasePackages` 额外包含
`cn.iocoder.boot.module`（dataQuery 业务模块包根）。默认端口 **48080**（local）。

```bash
# 方式一：在 yudao-server 模块直接运行（IDE 中 Run Main 亦可）
mvn -pl yudao-server -am spring-boot:run
# 方式二：打包后运行（可执行 jar 产物路径以实际构建为准——待确认）
mvn -DskipTests package
```

> Swagger 入口 `/swagger-ui`、接口文档 `/v3/api-docs`，详见 `application.yaml`。

### 4.3 关键配置（`application.yaml` 的 `data-query.*`）

- `data-query.embedding.enabled` / `dimension=1536`：租户 Embedding 开关与维度（必须与 `ai_schema_vector` 的 `vector(1536)` 一致）。
- `data-query.vector.auto-rebuild-enabled` / `debounce-ms`：元数据变更后是否自动增量重建 Schema 向量索引（默认开启、防抖 2000ms）。
  关闭后仍打 `sync_status=2` 脏标，需人工调 `POST /data-query/schema-vector/sync`。
- `data-query.recall.*`：Schema 召回的 Top-K、RRF 权重、Token 预算、一跳关联扩展。
- `data-query.sql-guard.*`：SQL 静态校验与只读执行的上限/脱敏（安全红线在代码中，**不可通过配置关闭**）。
- `data-query.chat.*`：问数编排、SSE、超时；`enabled=false` 时回退 `EchoModelClient`（联调/故障降级）。
- `data-query.knowledge.*`：知识库问答与 MQ 异步摄取；`mq.enabled=false` 走同步兜底（无 MQ 本地调试用）。
  - 注意：`knowledge.mq.endpoints` 当前为 `127.0.0.1:8080`，而设计文档写 Proxy 端口 `8081`（**存在不一致，见 §6**）。
  - 检索现值（2026-11-24 省 token 调整）：`top-k=4`、`context-token-budget=6000`、`history-max-messages=4`（2 轮）、`history-token-budget=800`；token 估算统一按中文 1 字≈1 token。
  - 拼接去重（≥16 字且 ≤60%）与精排截断（每片开头 256 字）已代码固化、**无配置开关**；详见 `docs/knowledge-token-optimization-changes.md`。
- `data-query.knowledge.table-narrate.*`：表格 LLM 叙述**已固化启用**（无 enabled 开关）、模型场景 `TABLE_NARRATE`、分批行数、桥接重叠比例 0.2。
- `data-query.cancel.*`：前端「停止回答」联动取消（`enabled` 默认 true、`token-ttl-ms`）。
- 多租户：`yudao.tenant.enable=true`；`ignore-tables` **仅**登记提示词 4 表。数据源、元数据、向量等 `ai_*` 表一律租户私有，
  **严禁**加入 `ignore-tables`。

---

## 5. 测试类清单

`yudao-module-dataQuery/src/test` 下**当前实际存在 10 个**测试类（2026-11-25 核对，与磁盘逐文件确认）：

| 分组 | 测试类 | 用例数 |
|---|---|---|
| 知识库召回与 token | `KnowledgeRecallTokenBudgetTest`、`KnowledgeRecallDedupeTest` | 5 + 13 |
| 知识库问答策略 | `PromotionPolicyTest` | 6 |
| 流式与在途去重 | `ChatInflightGuardTest`、`StreamAbortSignalGovernanceTest` | 4 + 2 |
| Schema 召回与向量同步 | `SchemaRecallKeywordExtractTest`、`SchemaRecallJoinRenderTest`、`SchemaVectorIndexSyncServiceImplTest` | 6 + 5 + 9 |
| 表关系配置 | `AiDatasourceTableRelationServiceImplTest` | 4 |
| PDF 解析 | `PdfDocumentParserTest` | 8 |

全模块测试：**Tests run: 62, Failures: 0, Errors: 0**（`mvn -pl yudao-module-dataQuery -am test`，2026-11-25）。

> **重要**：2026-11-22 版曾记录 30 个测试类（含 Controller、切块、摄取、版本管理、问数编排、知识库问答等），
> 但这些源文件**当前不在磁盘、也不在 git 中**，无法恢复。上述 30 个清单已作废，勿再按它判断回归覆盖度。
> 若需补齐这些测试，须按当前代码重新编写。

测试栈：JUnit 5 + AssertJ（`assertThat`）+ Spring Boot Starter Test；用 `@DisplayName` 描述用例。

---

## 6. 待补充 / 待确认（勿臆造，落实后再更新本文件）

| # | 事项 | 现状 |
|---|---|---|
| 1 | Spring Boot 版本 | 根 pom `4.1.2` vs `yudao-dependencies`/README `3.5.15`；`mvn help:evaluate` 解析属性为 **`4.1.2`**（根 pom 生效），但该版本真实性/依赖能否解析需以 `mvn dependency:tree` 与运行时为准 |
| 2 | Spring AI BOM 版本 | 根 pom `2.0.1` vs `yudao-dependencies`/docs `1.1.8`；`help:evaluate` 属性为 **`2.0.1`**，而本地仓库历史构建曾使用 `1.1.8` 的 jar，存在差异，实际以依赖树确认为准 |
| 3 | RocketMQ Proxy 端口 | `application.yaml` 为 `127.0.0.1:8080`，文档为 `8081`；且与 Docker `--enable-proxy` 前置相关 |
| 4 | 数据库初始化脚本的执行方式与顺序、pgvector 安装步骤 | 仓库无一键脚本，待补充 |
| 5 | 打包后可执行 jar 的确切产物路径与部署命令 | 待构建后确认 |
| 6 | 前端工程方案（构建/部署约定） | `yudao-ui/*` 为空壳，待立项 |
| 7 | ~~PDF 解析依赖缺失~~ **已解决** | 改用 Spring AI 官方 `spring-ai-pdf-document-reader`（底层 PDFBox 3.0.7），由 `PdfDocumentParser` 适配；`PdfDocumentParserTest` 8 条用例通过。设计文档写「pdfbox 3.0.x」的旧表述已不适用 |
| 8 | 真实供应商 Key 的获取与联调复测流程 | 设计文档标注「待真实 Key 后复测」 |
| 9 | 本地/开发环境之外（dev/prod）的部署与配置管理 | 待补充 |
