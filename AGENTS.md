# AGENTS.md — 协作规则（必读）

> 只放**必须遵守的规则**。项目事实（技术栈、目录、启动、配置、测试清单、待确认）见 [`docs/project-notes.md`](docs/project-notes.md)，按需再读。
>
> ⚠️ 本文件经 `project_guidance` 注入有大小上限：旧版 17 KB 在约 11.8 KB 处被静默截断，§6 后半从未进入上下文。
> 现版约 7.6 KB。**新增规则插入前半部分，不要追加到末尾**，否则可能读不到。

---

## 1. 修改原则（按重要度排序，越靠前越不可妥协）

1. **业务口径以 `docs/` 为准**：改问数 / 知识库 / 租户逻辑前先读对应设计文档，勿凭直觉改安全代码。
2. **安全红线只增不减**：SQL 类型白名单、单语句校验、`tenant_id` 硬过滤、只读连接、禁用函数/关键词基线、S3 召回白名单、引用编号越界裁剪——**不得为「跑通」放宽或绕过**。
3. **不伪造成功**：解析/向量化失败要落 `parse_status` / `sync_status` 与错误原因，绝不静默填占位向量。
4. **租户隔离**：新增 `ai_*` 表默认租户私有；`tenant_id` 只取服务端上下文，接口不接收；异步线程（MQ / Embedding）必须显式 `TenantContextHolder.set/clear`；新增平台共享表才登记 `ignore-tables`，且须接入共享基类（如 `SharedTenantServiceImpl`）。
5. **向量维度统一 1536**：Embedding 相关改动不得破坏 `vector(1536)`；非 1536 模型上传应拒绝，不混存。
6. **模型调用收敛**：主链不自发 HTTP，统一走 `ModelGovernanceExecutor` / 租户客户端；Embedding 与 Chat 分键计量。
7. **最小改动、保留回滚**：优先在既有 SPI/扩展点实现（如 `DocumentParser`、`ChunkSplitter`）；保留降级开关语义（`embedding.enabled`、`chat.enabled`、`mq.enabled`、`keyword-fallback-enabled`）。
8. **测试伴随**：核心逻辑改动补单测；提交前至少跑 `mvn -pl yudao-module-dataQuery -am test`。
9. **改完同步文档**：影响设计口径或配置时，同步更新 `docs/` 与 `application.yaml` 注释。

---

## 2. 禁止操作

- ❌ 删除 / 清空 `.workbuddy/`（项目数据）、`.git/`、`.idea/`。
- ❌ 用文件管理器 / `Delete` 键清 `target/`；禁止 `rm -rf */target`、`del /S` 批量删。统一用 `mvn clean`（详见 §3）。
- ❌ 对 `Desktop / Downloads / Documents` 等个人目录批量删除 / 重命名 / 整理（安全策略禁止）。
- ❌ 全局替换包名 `cn.iocoder.yudao` ↔ `cn.iocoder.boot`（两个包根并存，替换会大面积破坏编译）。
- ❌ 把 `ai_schema_vector` 或其它 `ai_*` 私有表加入 `yudao.tenant.ignore-tables`。
- ❌ 重新引入 Milvus / pgvector 的 Spring AI starter，或用平台共享 Embedding Key 代租户处理数据（设计已否决）。
- ❌ 模型原始文本直接进 JDBC；不生成 INSERT/UPDATE/DELETE/DDL；不做跨数据源 JOIN。
- ❌ 配置里明文写真实供应商 API Key；密钥走环境变量（如 `${OPENAI_API_KEY:...}`）与租户凭证表。
- ❌ 用 JDK 8 构建（报 `class file version` 错误）。

---

## 3. 构建 / 测试 / 清理

```bash
mvn -DskipTests compile                                  # 编译
mvn -DskipTests package                                  # 打包
mvn test                                                 # 全量测试
mvn -pl yudao-module-dataQuery -am test                  # 仅本模块（-am 先建依赖）
mvn -pl yudao-module-dataQuery -am -Dtest=ChunkSplitterTest -Dsurefire.failIfNoSpecifiedTests=false test  # 单个测试类
mvn clean                                                # 清理（唯一正确方式）
mvn -pl yudao-module-dataQuery clean                     # 只清本模块
```

**清理与回收站（2026-11-24 实测，旧结论已作废）：**

1. **`target/` 只用 `mvn clean` 清**：按 Maven 生命周期删得完整、不误删源码、不留半旧半新 class（避免「代码改了却跑旧 class」）。`target/` 已被 `.gitignore` 忽略。
2. **`mvn clean` 不保证「不进回收站」**：沙箱在更底层拦截删除，`rm`、`rm -rf`、`command rm -rf`、`mvn clean` 删**目录**时都会送回收站。实测回收站索引 4340 → 4342（一次 `mvn clean`）→ 4343（`command rm -rf`）。旧说法「`mvn clean` 走 Java 绕过 shell 所以干净」「`command rm -rf` 跳过安全网」**均不成立**。它保证的是「删得对」，不是「不进回收站」。
3. **一次性诊断文件走 `%TEMP%`**（`C:\Users\admin\AppData\Local\Temp`）单独编译，**不要放进 `src/test/java`**。实测：临时目录**不会自动清**（存储感知的临时文件清理关闭），但**单个文件 `rm -f` 删除不进回收站**（4343 → 4343，文件确实消失）。删**目录**仍进回收站，故诊断文件用完按单文件删，别整目录删。
4. **回收站堆积是独立问题**：不靠换删除命令解决；堆积到碍事时先列清单、**经用户明确确认后**才清，不得擅自清空。

---

## 4. 代码风格与命名

- 遵循《阿里巴巴 Java 开发手册》；注释详尽。项目未配 checkstyle/spotless/editorconfig，靠自觉与 review。
- **包名**：脚手架 `cn.iocoder.yudao.*`；dataQuery 业务 `cn.iocoder.boot.module.data_query.*`（勿混用）。
- **分层命名**：`AiXxx`（`@TableName` 映射 snake_case）/ `AiXxxMapper` / `AiXxxService`+`Impl` / `AiXxxController` + VO（`SaveReqVO` / `PageReqVO` / `RespVO` / `SimpleRespVO`，放 `controller/admin/<domain>/vo/`）。
- **Controller**：`@RestController` + `@RequestMapping("/data-query/...")` + Swagger `@Tag`/`@Operation` + `@PreAuthorize("@ss.hasPermission('data-query:...:...')")`，返回 `CommonResult<T>`。
- **其他**：注入用 `jakarta.annotation.Resource`；`BeanUtils.toBean`；分页 `PageResult` / `LambdaQueryWrapperX`；错误码 `enums/DataSourceErrorCodeConstants`，抛 `ServiceExceptionUtil.exception(...)`；实体多为普通 POJO（未继承 `BaseDO`），审计字段 Controller/Service 显式填充。
- **单测**：`XxxTest`，方法名表达场景（如 `smallDoc_singleChunk`），配 `@DisplayName`；JUnit 5 + AssertJ。

---

## 5. 环境与事实陷阱

- **JDK 必须 17**：`JAVA_HOME` 可能指向 8；报 `class file version 61.0` 时显式指定 `JAVA_HOME=D:\Program Files\Java\jdk-17`。Maven 在 `D:\DevelopmentRelated\apache-maven-3.9.9`，无 wrapper。
- **版本冲突未消解**：根 pom 写 Spring Boot `4.1.2` / Spring AI `2.0.1`，`yudao-dependencies` 与 README 写 `3.5.15` / `1.1.8`；引入 starter 前先确认实际生效版本（project-notes §2、§6）。
- **文档可能滞后**：实现现状以代码为准（例：`knowledge-rag-flow.md` 仍写「Java 实现未开始」）。
- **DDL 未连库验证**：`sql/postgresql/nl2sql-*.sql` 是产出稿，导入前核对。
- **端口不一致**：`knowledge.mq.endpoints` 配 `8080`，设计文档写 Proxy `8081`，联调前确认。
- **前端为空壳**：`yudao-ui/*` 无前端工程，页面交付需先立项（一期只做后端）。

---

## 6. 事实索引

| 想了解 | 去看 |
|---|---|
| 技术栈与版本依据 | `docs/project-notes.md` §2 |
| 目录结构 / 模块职责 | `docs/project-notes.md` §3 |
| 本地启动 / 依赖 / `data-query.*` 配置 | `docs/project-notes.md` §4 |
| 测试类清单（30 个） | `docs/project-notes.md` §5 |
| 尚未确认的事项（勿臆造） | `docs/project-notes.md` §6 |
| 问数主链 S0~S9 | `docs/intelligent-query-flow.md` |
| 知识库 RAG / 异步入库 / 问答 | `docs/knowledge-rag-flow.md` |
| 多租户凭证隔离 | `docs/tenant-isolation-design.md` |
| 脚手架本体功能 | `README.md` |
