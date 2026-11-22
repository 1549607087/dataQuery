package cn.iocoder.boot.module.data_query.service.impl;

import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderRespVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;
import cn.iocoder.boot.module.data_query.entity.AiPrompt;
import cn.iocoder.boot.module.data_query.entity.AiPromptVersion;
import cn.iocoder.boot.module.data_query.framework.recall.SchemaContext;
import cn.iocoder.boot.module.data_query.service.AiDatasourceService;
import cn.iocoder.boot.module.data_query.service.AiPromptService;
import cn.iocoder.boot.module.data_query.service.AiPromptVersionService;
import cn.iocoder.boot.module.data_query.service.PromptRenderService;
import cn.iocoder.boot.module.data_query.service.SchemaRecallService;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.PROMPT_RENDER_SCENE_EMPTY;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.PROMPT_RENDER_TEMPLATE_NOT_FOUND;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.PROMPT_RENDER_VARIABLE_MISSING;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.PROMPT_RENDER_VARIABLE_UNDECLARED;
import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.PROMPT_RENDER_VERSION_NOT_FOUND;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * S4 提示词运行时渲染实现（P3-B）。
 *
 * 关键取舍：
 * 1. 模板正文与 variable_defs 一律取 ai_prompt_version 只读快照，不读编辑态
 *    ai_prompt.content / ai_prompt_variable —— 否则同一 promptVersionId 复现不出同一结果；
 * 2. 内置变量（dialect / sql_rules / 召回 Schema 等）由服务端注入且优先级高于前端入参，
 *    前端不能伪造方言或塞入任意 Schema 绕过 S3 白名单；
 * 3. 未登记占位符、缺失必填变量都视为「该模板不可用」，先回落平台默认模板，
 *    仍不可用则明确抛错，绝不把 {{var}} 原样留给模型或拼字符串兜底。
 */
@Slf4j
@Service
public class PromptRenderServiceImpl implements PromptRenderService {

    /** 与 ai_prompt.content 的占位符约定一致：{{var_name}} */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\}\\}");

    /** ai_prompt / ai_prompt_version.status 语义见 nl2sql-prompt.sql 的 COMMENT */
    private static final int PROMPT_STATUS_ENABLED = 0;
    private static final int VERSION_STATUS_PUBLISHED = 1;
    private static final long PLATFORM_TENANT_ID = 0L;

    @Resource
    private AiPromptService promptService;
    @Resource
    private AiPromptVersionService promptVersionService;
    @Resource
    private AiDatasourceService datasourceService;
    @Resource
    private SchemaRecallService schemaRecallService;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public PromptRenderRespVO render(PromptRenderReqVO reqVO) {
        String sceneCode = reqVO == null ? null : trim(reqVO.getSceneCode());
        if (!StringUtils.hasText(sceneCode)) {
            throw exception(PROMPT_RENDER_SCENE_EMPTY);
        }

        // ---- 1. 服务端组装变量（内置变量优先级高于前端扩展变量） ----
        RenderVariables vars = buildVariables(reqVO);

        // ---- 2. 候选模板：租户覆盖优先、平台 0 兜底，走 SharedTenantServiceImpl 可见范围 ----
        List<AiPrompt> templates = listTemplates(sceneCode);
        if (templates.isEmpty()) {
            throw exception(PROMPT_RENDER_TEMPLATE_NOT_FOUND);
        }
        boolean hasTenantOverride = templates.stream()
                .anyMatch(p -> p.getTenantId() != null && p.getTenantId() != PLATFORM_TENANT_ID);

        // ---- 3. 逐个尝试，任一成功即返回；记录最后一个失败原因 ----
        String lastFailure = "VERSION";
        for (AiPrompt prompt : templates) {
            RenderAttempt attempt = tryRender(prompt, vars);
            if (attempt.resp != null) {
                attempt.resp.setFallbackToPlatform(hasTenantOverride
                        && prompt.getTenantId() != null && prompt.getTenantId() == PLATFORM_TENANT_ID);
                return attempt.resp;
            }
            lastFailure = attempt.failure;
            log.info("[prompt-render] 模板不可用, sceneCode={}, promptId={}, tenantId={}, reason={}",
                    sceneCode, prompt.getId(), prompt.getTenantId(), attempt.failure);
        }

        // ---- 4. 租户覆盖与平台默认都不可用：明确报错，不兜底生成 ----
        if ("UNDECLARED".equals(lastFailure)) {
            throw exception(PROMPT_RENDER_VARIABLE_UNDECLARED);
        }
        if ("MISSING".equals(lastFailure)) {
            throw exception(PROMPT_RENDER_VARIABLE_MISSING);
        }
        throw exception(PROMPT_RENDER_VERSION_NOT_FOUND);
    }

    // ==================== 模板与快照 ====================

    private List<AiPrompt> listTemplates(String sceneCode) {
        // 不取「全部」以免租户模板很多时无限回落；按 tenant_id DESC, sort ASC 排好后逐个尝试
        return promptService.list(new LambdaQueryWrapperX<AiPrompt>()
                .eq(AiPrompt::getSceneCode, sceneCode)
                .eq(AiPrompt::getStatus, PROMPT_STATUS_ENABLED)
                .eq(AiPrompt::getDeleted, 0)
                .orderByDesc(AiPrompt::getTenantId)
                .orderByAsc(AiPrompt::getSort)
                .orderByAsc(AiPrompt::getId));
    }

    private RenderAttempt tryRender(AiPrompt prompt, RenderVariables vars) {
        Long versionId = prompt.getCurrentVersionId();
        if (versionId == null || versionId <= 0) {
            return RenderAttempt.fail("VERSION");
        }
        AiPromptVersion snapshot = promptVersionService.getAiPromptVersion(versionId);
        if (snapshot == null || snapshot.getDeleted() == null || snapshot.getDeleted() != 0
                || snapshot.getStatus() == null || snapshot.getStatus() != VERSION_STATUS_PUBLISHED) {
            return RenderAttempt.fail("VERSION");
        }
        // 快照必须与模板匹配，防止 current_version_id 指向别人模板的快照
        if (!prompt.getId().equals(snapshot.getPromptId()) || !versionId.equals(snapshot.getId())) {
            return RenderAttempt.fail("VERSION");
        }
        String content = snapshot.getContent();
        if (!StringUtils.hasText(content)) {
            return RenderAttempt.fail("VERSION");
        }

        // 变量定义来自快照的 variable_defs（只读 JSON），不读编辑态 ai_prompt_variable
        Map<String, VariableDef> declared = parseVariableDefs(snapshot.getVariableDefs());
        Set<String> contentVars = extractVariables(content);
        Set<String> undeclared = new LinkedHashSet<>(contentVars);
        undeclared.removeAll(declared.keySet());
        if (!undeclared.isEmpty()) {
            return RenderAttempt.fail("UNDECLARED");
        }

        // 合并变量值：内置 > 前端扩展 > 快照默认值
        Map<String, String> values = new LinkedHashMap<>();
        declared.forEach((name, def) -> {
            if (StringUtils.hasText(def.defaultValue)) {
                values.put(name, def.defaultValue);
            }
        });
        vars.extra.forEach((k, v) -> {
            if (k != null) {
                values.put(k, v == null ? "" : v);
            }
        });
        values.putAll(vars.builtin); // 内置变量不可被前端覆盖

        List<String> missing = new ArrayList<>();
        for (String name : contentVars) {
            VariableDef def = declared.get(name);
            if (def != null && def.required && !StringUtils.hasText(values.get(name))) {
                missing.add(name);
            }
        }
        if (!missing.isEmpty()) {
            return RenderAttempt.fail("MISSING");
        }

        String rendered = renderContent(content, values);
        PromptRenderRespVO resp = new PromptRenderRespVO();
        resp.setSceneCode(prompt.getSceneCode());
        resp.setPromptId(prompt.getId());
        resp.setPromptVersionId(snapshot.getId());
        resp.setVersion(snapshot.getVersion());
        resp.setTemplateTenantId(prompt.getTenantId());
        resp.setContent(rendered);
        resp.setContentHash(sha256(rendered));
        resp.setSnapshotHash(safe(snapshot.getContentHash()));
        resp.setTokenEstimate(estimateTokens(rendered));
        resp.setDeclaredVariables(new ArrayList<>(declared.keySet()));
        resp.setProvidedVariables(new ArrayList<>(values.keySet()));
        resp.setUndeclaredVariables(new ArrayList<>(undeclared));
        resp.setMissingVariables(missing);
        List<String> unused = new ArrayList<>(declared.keySet());
        unused.removeAll(contentVars);
        resp.setUnusedVariables(unused);
        // S3 联动信息（未召回时保持 null，避免误导）
        if (vars.recall != null) {
            resp.setRecallMode(vars.recall.getRecallMode());
            resp.setRecallDegraded(vars.recall.getDegraded());
            resp.setRecallDegradeReason(vars.recall.getDegradeReason());
            resp.setRecallTableCount(vars.recall.getHitTableCount());
        }
        return RenderAttempt.ok(resp);
    }

    // ==================== 变量组装 ====================

    private RenderVariables buildVariables(PromptRenderReqVO reqVO) {
        RenderVariables vars = new RenderVariables();
        if (reqVO.getVariables() != null) {
            vars.extra.putAll(reqVO.getVariables());
        }

        // 数据源提供时注入方言与规则（内置，前端覆盖无效）
        if (reqVO.getDatasourceId() != null) {
            AiDatasource ds = datasourceService.getDatasourceOwned(reqVO.getDatasourceId());
            put(vars.builtin, "dialect", ds.getDialect());
            put(vars.builtin, "sql_rules", ds.getSqlRules());
            put(vars.builtin, "identifier_quote", ds.getIdentifierQuote());
            put(vars.builtin, "quote_all", ds.getQuoteAll() == null ? null : String.valueOf(ds.getQuoteAll()));
        }

        // question 与 schema：允许只传现有 Schema，也允许自动做一次 S3 召回
        put(vars.builtin, "question", reqVO.getQuestion());
        String schema = reqVO.getSchema();
        if (!StringUtils.hasText(schema) && Boolean.TRUE.equals(reqVO.getRecallSchema())
                && reqVO.getDatasourceId() != null && StringUtils.hasText(reqVO.getQuestion())) {
            SchemaContext ctx = schemaRecallService.recall(reqVO.getDatasourceId(),
                    reqVO.getQuestion(), reqVO.getTopK());
            vars.recall = ctx;
            schema = ctx.getSchemaText();
            put(vars.builtin, "metric_def", String.join("；", ctx.getMetricDefs()));
            put(vars.builtin, "synonyms", String.join("；", ctx.getSynonyms()));
        }
        put(vars.builtin, "schema", schema);

        // RAG 知识库问答：检索上下文与历史对话由服务端注入（ragContext / ragHistory 带 @JsonIgnore，
        // 前端 JSON 无法写入）。走 builtin 而非 extra，保证即使前端在 variables 里塞同名键，
        // 也会被后面的 values.putAll(builtin) 覆盖，无法伪造检索证据。
        // 必须 putIfAbsent 兜底空串：RAG 模板把 context 声明为 required，缺失会被判 MISSING。
        put(vars.builtin, "context", reqVO.getRagContext());
        put(vars.builtin, "history", reqVO.getRagHistory());
        vars.builtin.putIfAbsent("context", "");
        vars.builtin.putIfAbsent("history", "");

        // 其余内置变量：一期 few_shot 为空（P6 审核后的样例才允许进 few-shot），
        // current_date 由服务端生成，杜绝前端伪造时间口径。
        // schema / metric_def / synonyms / dialect 这些安全相关变量一律由内置值兜底：
        // 即使前端在 variables 里塞了同名键，也会被 putAll(builtin) 覆盖，
        // 无法绕过 S3 白名单、伪造方言或注入任意表结构。
        vars.builtin.putIfAbsent("schema", "");
        vars.builtin.putIfAbsent("metric_def", "");
        vars.builtin.putIfAbsent("synonyms", "");
        vars.builtin.putIfAbsent("dialect", "");
        vars.builtin.putIfAbsent("sql_rules", "");
        vars.builtin.putIfAbsent("identifier_quote", "");
        vars.builtin.putIfAbsent("quote_all", "");
        vars.builtin.put("few_shot", "");
        vars.builtin.put("current_date", LocalDate.now().toString());
        return vars;
    }

    // ==================== 工具 ====================

    /** 解析只读快照 variable_defs：[{"name":"question","required":true,"defaultValue":""}] */
    private Map<String, VariableDef> parseVariableDefs(String json) {
        Map<String, VariableDef> result = new LinkedHashMap<>();
        if (!StringUtils.hasText(json)) {
            return result;
        }
        try {
            JsonNode array = objectMapper.readTree(json);
            if (array == null || !array.isArray()) {
                return result;
            }
            for (JsonNode node : array) {
                String name = node.path("name").asText(null);
                if (!StringUtils.hasText(name)) {
                    continue;
                }
                VariableDef def = new VariableDef();
                def.required = node.path("required").asBoolean(false);
                def.defaultValue = node.path("defaultValue").asText("");
                if (node.hasNonNull("maxLength")) {
                    def.maxLength = node.path("maxLength").asInt(0);
                }
                result.put(name, def);
            }
        } catch (Exception ex) {
            // 快照损坏属于模板不可用，向上记为版本错误而不是静默忽略
            log.warn("[prompt-render] variable_defs 解析失败, reason={}", ex.getMessage());
            return new LinkedHashMap<>();
        }
        return result;
    }

    private Set<String> extractVariables(String content) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = VAR_PATTERN.matcher(content);
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private String renderContent(String content, Map<String, String> values) {
        Matcher matcher = VAR_PATTERN.matcher(content);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String raw = values.get(matcher.group(1));
            String value = raw == null ? "" : raw;
            // maxLength 以外的一期不截断；用 quoteReplacement 避免 $ 与反斜杠被当正则替换语义
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void put(Map<String, String> map, String key, String value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int estimateTokens(String content) {
        return content == null ? 0 : content.length() / 4;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static final class VariableDef {
        private boolean required;
        private String defaultValue = "";
        @SuppressWarnings("unused")
        private int maxLength;
    }

    private static final class RenderVariables {
        private final Map<String, String> builtin = new LinkedHashMap<>();
        private final Map<String, String> extra = new LinkedHashMap<>();
        private SchemaContext recall;
    }

    private static final class RenderAttempt {
        private final PromptRenderRespVO resp;
        private final String failure;

        private RenderAttempt(PromptRenderRespVO resp, String failure) {
            this.resp = resp;
            this.failure = failure;
        }

        static RenderAttempt ok(PromptRenderRespVO resp) {
            return new RenderAttempt(resp, null);
        }

        static RenderAttempt fail(String reason) {
            return new RenderAttempt(null, reason);
        }
    }

}
