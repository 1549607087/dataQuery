# AGENTS.md — 协作规则（必读）

> **本文件只放「必须遵守的规则」。** 项目事实（技术栈、目录树、启动步骤、配置项、测试类清单、待确认事项）
> 已拆到 [`docs/project-notes.md`](docs/project-notes.md)，需要时再读，不必预加载。
>
> ⚠️ **加载限制（重要）**：本文件经 `project_guidance` 注入时有大小上限——实测旧版 17 KB 在约 11.8 KB 处
> 被静默截断、无任何告警，其 §6 后半、§7、§8 从未进入模型上下文。
> 现版约 7.7 KB，距截断线余量约 4 KB。**新增规则请插入前半部分，不要追加到末尾**，否则可能根本读不到。

---

## 1. 修改原则

> 按重要度排序：越靠前越不可能被截断，也越不该被妥协。

1. **业务口径以 `docs/` 为准**：改问数 / 知识库 / 租户逻辑前，先读对应设计文档，勿凭直觉改安全相关代码。
2. **安全红线只增不减**：SQL 语句类型白名单、单语句校验、`tenant_id` 硬过滤、只读连接、内置禁用函数/关键词基线、
   S3 召回白名单、引用编号越界裁剪——这些都是代码硬约束，**不得为了「让功能跑通」而放宽或绕过**。
3. **不伪造成功**：解析/向量化失败要落状态与错误原因（`parse_status` / `sync_status`），
   绝不静默填占位向量或假装完成。
4. **租户隔离**：新增 `ai_*` 表默认租户私有；`tenant_id` 只取服务端上下文，接口不接收；
   异步线程（MQ consumer / Embedding 子线程）必须显式 `TenantContextHolder.set/clear`，防止串租户。
   新增平台共享表才登记 `ignore-tables`，且必须接入共享基类（如 `SharedTenantServiceImpl`）。
5. **向量维度统一 1536**：任何 Embedding 相关改动都不得破坏与 `vector(1536)` 列的一致性；非 1536 模型上传应拒绝，不混存。
6. **模型调用收敛**：主链不自行发 HTTP，统一走治理抽象（`ModelGovernanceExecutor` / 租户客户端）；Embedding 与 Chat 分键计量。
7. **最小改动、保留回滚路径**：优先在既有 SPI/扩展点内实现（如 `DocumentParser`、`ChunkSplitter`）；
   运维降级开关（`embedding.enabled`、`chat.enabled`、`mq.enabled`、`keyword-fallback-enabled`）语义要保留。
8. **测试伴随**：新增/修改核心逻辑应补或更新对应单元测试；提交前至少跑 `mvn -pl yudao-module-dataQuery -am test`。
9. **改完同步文档**：若变更影响设计口径或配置，同步更新 `docs/` 对应文档与 `application.yaml` 注释。

---

## 2. 禁止操作

- ❌ 删除或清空 `.workbuddy/`（项目数据，非缓存）、`.git/`、`.idea/`。
- ❌ 用文件管理器 / `Delete` 键 / 回收站清理 `target/` 等编译产物——**统一用 `mvn clean`**。
  同理禁止 `rm -rf */target`、`del /S` 等绕过 Maven 的批量删除。原因见 §3 末。
- ❌ 对 `Desktop / Downloads / Documents` 等个人目录做批量删除 / 重命名 / 整理（安全策略禁止）。
- ❌ 全局替换包名 `cn.iocoder.yudao` ↔ `cn.iocoder.boot`（两个包根并存且分工不同，替换会大面积破坏编译）。
- ❌ 把 `ai_schema_vector` 或其它 `ai_*` 租户私有表加入 `yudao.tenant.ignore-tables`。
- ❌ 重新引入 Milvus / pgvector 的 Spring AI starter，或用平台共享 Embedding Key 代租户处理数据（设计已明确否决）。
- ❌ 让模型原始文本直接进入 JDBC；不生成 INSERT/UPDATE/DELETE/DDL；不做跨数据源 JOIN（主链边界）。
- ❌ 在配置里明文新增真实供应商 API Key；密钥应走环境变量（如 `${OPENAI_API_KEY:...}`）与租户自有凭证表。
- ❌ 使用 JDK 8 构建（会报 `class file version` 错误）。

---

## 3. 构建 / 测试 / 清理

```bash
# 全量编译（跳过测试）
mvn -DskipTests compile

# 全量打包
mvn -DskipTests package

# 全量测试
mvn test

# 仅跑 dataQuery 模块的测试（-am 用于先构建其依赖的框架模块）
mvn -pl yudao-module-dataQuery -am test

# 指定单个测试类（本机 JDK17 已验证）
mvn -pl yudao-module-dataQuery -am -Dtest=ChunkSplitterTest -Dsurefire.failIfNoSpecifiedTests=false test

# 清理构建产物（唯一的正确方式）
mvn clean
mvn -pl yudao-module-dataQuery clean          # 只清某个模块
```

**为什么 `target/` 必须走 `mvn clean` 而不是删到回收站：**

1. 一个 `target/` 里有几千到几万个 class / jar / surefire 报告，走回收站既慢又占空间；回收站不会自动回收，
   长期累积可留数 GB 残留。
2. 从回收站恢复回来的是**半旧半新的产物**，容易造成「代码已改但跑的是旧 class」的假象，排查极费时间。
   `mvn clean` 是原子删除，语义明确。
3. 各模块 `target/` 已被 `.gitignore` 的 `target/` 规则忽略，删除不影响版本库，但仍应走 Maven。

---

## 4. 代码风格与命名

- 遵循《阿里巴巴 Java 开发手册》；注释详尽。**项目未配置 checkstyle/spotless/editorconfig**，靠自觉与 code review。
- **包名**：脚手架为 `cn.iocoder.yudao.*`；dataQuery 业务模块为 `cn.iocoder.boot.module.data_query.*`（勿混用）。
- **分层命名**：`AiXxx`（`@TableName` 映射 snake_case）/ `AiXxxMapper` / `AiXxxService`+`Impl` /
  `AiXxxController` + VO（`SaveReqVO` / `PageReqVO` / `RespVO` / `SimpleRespVO`，置于 `controller/admin/<domain>/vo/`）。
- **Controller 约定**：`@RestController` + `@RequestMapping("/data-query/...")` + Swagger `@Tag`/`@Operation` +
  `@PreAuthorize("@ss.hasPermission('data-query:...:...')")`，返回 `CommonResult<T>`。
- **其他**：依赖注入用 `jakarta.annotation.Resource`；Bean 转换 `BeanUtils.toBean`；分页 `PageResult` / `LambdaQueryWrapperX`；
  错误码集中 `enums/DataSourceErrorCodeConstants`，抛 `ServiceExceptionUtil.exception(...)`。
  实体多为普通 POJO（未继承 `BaseDO`），审计字段在 Controller/Service 显式填充。
- **单测**：类名 `XxxTest`，方法名表达场景（如 `smallDoc_singleChunk`），配 `@DisplayName`；JUnit 5 + AssertJ。

---

## 5. 环境与事实陷阱

- **JDK**：必须 17。系统 `JAVA_HOME` 可能指向 8，编译报 `class file version 61.0` 时显式指定
  `JAVA_HOME=D:\Program Files\Java\jdk-17`。Maven 位于 `D:\DevelopmentRelated\apache-maven-3.9.9`，仓库无 wrapper。
- **版本冲突未消解**：根 pom 写 Spring Boot `4.1.2` / Spring AI `2.0.1`，而 `yudao-dependencies` 与 README 写
  `3.5.15` / `1.1.8`。升级或引入 starter 前先确认实际生效版本（详见 project-notes §2、§6）。
- **文档状态可能滞后**：部分设计文档的实现状态标注与代码不符（例：`knowledge-rag-flow.md` 仍写「Java 实现未开始」）。
  **判断实现现状以代码为准。**
- **DDL 未连库验证**：`sql/postgresql/nl2sql-*.sql` 多为产出稿，导入前先核对。
- **端口不一致**：`knowledge.mq.endpoints` 配置为 `8080`，设计文档写 Proxy `8081`，联调前先确认。
- **前端为空壳**：`yudao-ui/*` 无前端工程，涉及页面交付需先立项（一期口径：只做后端）。

---

## 6. 事实索引

| 想了解 | 去看 |
|---|---|
| 技术栈与版本依据 | `docs/project-notes.md` §2 |
| 目录结构 / 各模块职责 | `docs/project-notes.md` §3 |
| 本地启动、依赖前置、`data-query.*` 配置 | `docs/project-notes.md` §4 |
| 测试类清单（30 个） | `docs/project-notes.md` §5 |
| 尚未确认的事项（勿臆造） | `docs/project-notes.md` §6 |
| 问数主链 S0~S9 设计 | `docs/intelligent-query-flow.md` |
| 知识库 RAG / 异步入库 / 问答链路 | `docs/knowledge-rag-flow.md` |
| 多租户凭证隔离 | `docs/tenant-isolation-design.md` |
| 脚手架本体功能说明 | `README.md` |
