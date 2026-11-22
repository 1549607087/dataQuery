package cn.iocoder.boot.module.data_query.controller.admin.aichat;

import cn.iocoder.boot.module.data_query.controller.admin.aichat.vo.*;
import cn.iocoder.boot.module.data_query.entity.AiQueryMessage;
import cn.iocoder.boot.module.data_query.entity.AiQuerySession;
import cn.iocoder.boot.module.data_query.framework.chat.ChatConversationService;
import cn.iocoder.boot.module.data_query.framework.chat.ChartSpec;
import cn.iocoder.boot.module.data_query.framework.sqlguard.QueryResult;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 智能问数对话（P7）
 *
 * DeepSeek 网页端式的对话交互后端：会话列表 / 会话改名与删除 / 消息历史 / 回答版本 /
 * 重新生成 / 修改问题 / 赞踩。所有归属校验在服务端完成：用户身份只取登录态，
 * 会话与消息查询依赖租户拦截器 + 显式 userId 过滤，跨租户记录与「不存在」同口径。
 *
 * 重新生成 / 修改问题复用同一个 QueryOrchestrator（S3 白名单 + S6/S7 闸门 + 审计），
 * 绝不另开绕过闸门的执行路径。
 */
@Tag(name = "管理后台 - 智能问数对话")
@RestController
@RequestMapping("/data-query/ai-chat/conversation")
@Validated
public class AiChatConversationController {

    @Resource
    private ChatConversationService conversationService;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 会话 ====================

    @GetMapping("/session/page")
    @Operation(summary = "我的会话列表（分页，服务端固化userId）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<PageResult<AiQuerySession>> listSessions(@Valid ChatSessionPageReqVO reqVO) {
        return success(conversationService.listSessions(reqVO, reqVO.getTitle(), currentUserId()));
    }

    @PutMapping("/session/rename")
    @Operation(summary = "会话改名")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<Boolean> renameSession(@Valid @RequestBody ChatSessionRenameReqVO reqVO) {
        conversationService.renameSession(reqVO.getSessionId(), reqVO.getTitle(), currentUserId());
        return success(true);
    }

    @DeleteMapping("/session/delete")
    @Operation(summary = "删除会话（级联软删消息）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<Boolean> deleteSession(@RequestParam("sessionId") Long sessionId) {
        conversationService.deleteSession(sessionId, currentUserId());
        return success(true);
    }

    // ==================== 消息 ====================

    @GetMapping("/message/list")
    @Operation(summary = "会话消息历史（当前主链，按 seq 升序）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<List<ChatMessageRespVO>> listMessages(@RequestParam("sessionId") Long sessionId) {
        List<ChatMessageRespVO> result = new ArrayList<>();
        for (AiQueryMessage m : conversationService.listMessages(sessionId, currentUserId())) {
            result.add(toVO(m));
        }
        return success(result);
    }

    @GetMapping("/message/versions")
    @Operation(summary = "某轮回答的全部版本（含历史，供切换版本）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<List<ChatMessageRespVO>> listVersions(@RequestParam("messageId") Long messageId) {
        List<AiQueryMessage> versions = conversationService.listVersions(messageId, currentUserId());
        List<ChatMessageRespVO> result = new ArrayList<>();
        for (AiQueryMessage m : versions) {
            result.add(toVO(m));
        }
        return success(result);
    }

    /**
     * 切换版本：复用 versions 资源，GET 列版本、PUT 切版本。
     * 切换只改兄弟选中与 current_leaf_id，不重跑链路、不新增消息。
     */
    @PutMapping("/message/versions")
    @Operation(summary = "切换版本：选中目标版本并推进当前分支叶子")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<Boolean> switchVersion(@Valid @RequestBody ChatSwitchVersionReqVO reqVO) {
        conversationService.switchVersion(reqVO.getSessionId(), reqVO.getTargetMessageId(), currentUserId());
        return success(true);
    }

    /**
     * DeepSeek 式「‹ 2/3 ›」问题版本翻页的数据源：
     * 返回同一 seq 下被改写过的全部 USER 问题及其回答（含已退出主链的旧版本）。
     */
    @GetMapping("/message/question-versions")
    @Operation(summary = "某一轮问题的全部版本（含历史，供问题版本翻页）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<List<ChatMessageRespVO>> listQuestionVersions(
            @RequestParam("sessionId") Long sessionId,
            @RequestParam("seq") Integer seq) {
        List<ChatMessageRespVO> result = new ArrayList<>();
        for (AiQueryMessage m : conversationService.listQuestionVersions(sessionId, seq, currentUserId())) {
            result.add(toVO(m));
        }
        return success(result);
    }

    // ==================== 重新生成 / 修改问题 / 赞踩 ====================

    @PostMapping("/regenerate")
    @Operation(summary = "重新生成：以原问题重跑整条链路，旧版本作废")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<AiChatAskRespVO> regenerate(@Valid @RequestBody ChatRegenerateReqVO reqVO) {
        return success(conversationService.regenerate(reqVO.getMessageId(), reqVO.getDatasourceId(),
                reqVO.getModelConfigId(), currentUserId()));
    }

    @PostMapping("/edit")
    @Operation(summary = "修改问题：改写问题后从该轮重跑，生成新分支")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<AiChatAskRespVO> edit(@Valid @RequestBody ChatEditReqVO reqVO) {
        return success(conversationService.edit(reqVO.getMessageId(), reqVO.getQuestion(),
                reqVO.getDatasourceId(), reqVO.getModelConfigId(), currentUserId()));
    }

    @PostMapping("/rating")
    @Operation(summary = "赞踩：-1 踩 / 0 取消 / 1 赞（幂等，仅助手消息）")
    @PreAuthorize("@ss.hasPermission('data-query:ai-chat:query')")
    public CommonResult<Boolean> rate(@Valid @RequestBody ChatRatingReqVO reqVO) {
        conversationService.rate(reqVO.getMessageId(), reqVO.getRating(), currentUserId());
        return success(true);
    }

    // ==================== 工具 ====================

    private static Long currentUserId() {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw new cn.iocoder.yudao.framework.common.exception.ServiceException(401, "未登录");
        }
        return userId;
    }

    private ChatMessageRespVO toVO(AiQueryMessage m) {
        ChatMessageRespVO vo = new ChatMessageRespVO();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setParentId(m.getParentId());
        vo.setVersion(m.getVersion());
        vo.setSeq(m.getSeq());
        vo.setActive(m.getIsActive() != null && m.getIsActive() == 1);
        vo.setQuestion(m.getQuestion());
        vo.setAnswer(m.getAnswer());
        vo.setGeneratedSql(m.getGeneratedSql());
        vo.setExecutedSql(m.getExecutedSql());
        vo.setResult(parse(m.getResultJson(), QueryResult.class));
        vo.setChartSpec(parse(m.getChartSpecJson(), ChartSpec.class));
        vo.setExplain(m.getExplain());
        vo.setStatus(m.getStatus());
        vo.setErrorCode(m.getErrorCode());
        vo.setErrorMsg(m.getErrorMsg());
        vo.setRating(m.getRating());
        vo.setTraceId(m.getTraceId());
        vo.setAuditId(m.getAuditId());
        vo.setCreateTime(m.getCreateTime());
        return vo;
    }

    private <T> T parse(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            return null;
        }
    }
}
