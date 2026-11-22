package cn.iocoder.boot.module.data_query.controller.admin.knowledge;

import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChatRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.KnowledgeChatMessageRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.KnowledgeSessionPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.KnowledgeSessionRenameReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.KnowledgeSessionRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeMessage;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeSession;
import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeChatOrchestrator;
import cn.iocoder.boot.module.data_query.framework.knowledge.chat.KnowledgeReference;
import cn.iocoder.boot.module.data_query.service.KnowledgeConversationService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 知识库问答（P9.2）
 *
 * <p>层级与智能问数对齐：一个知识库问答页 = 会话列表 + 单会话消息流 + 提问入口。
 * 归属校验全部在服务端完成——身份只取登录态，会话 / 消息查询强制 userId 过滤，
 * 跨租户或他人会话一律按「不存在」处理。
 *
 * <p>提问复用 {@link KnowledgeChatOrchestrator}：检索 → 提示词 → 受治理模型调用 → 引用校验 → 对话树落库，
 * 同步返回可渲染结果。重新生成 / 修改问题 / 赞踩 / SSE 由下一期接入，接口与表结构已预留。
 */
@Tag(name = "管理后台 - 知识库问答")
@RestController
@RequestMapping("/data-query/knowledge-chat")
@Validated
public class AiKnowledgeChatController {

    @Resource
    private KnowledgeChatOrchestrator orchestrator;

    @Resource
    private KnowledgeConversationService conversationService;

    @Resource
    private ObjectMapper objectMapper;

    // ==================== 提问 ====================

    @PostMapping("/ask")
    @Operation(summary = "知识库问答（同步，返回回答与引用溯源）")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chat:ask')")
    public CommonResult<AiKnowledgeChatRespVO> ask(@Valid @RequestBody AiKnowledgeChatAskReqVO reqVO) {
        return success(orchestrator.ask(reqVO));
    }

    // ==================== 会话 ====================

    @GetMapping("/session/page")
    @Operation(summary = "我的会话列表（分页，服务端固化 userId）")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chat:query')")
    public CommonResult<PageResult<KnowledgeSessionRespVO>> pageSession(@Valid KnowledgeSessionPageReqVO reqVO) {
        PageResult<AiKnowledgeSession> page = conversationService.pageSession(
                reqVO.getBaseId(), reqVO.getPageNo(), reqVO.getPageSize());
        return success(BeanUtils.toBean(page, KnowledgeSessionRespVO.class));
    }

    @GetMapping("/session/get")
    @Operation(summary = "获得会话详情")
    @Parameter(name = "id", description = "会话编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chat:query')")
    public CommonResult<KnowledgeSessionRespVO> getSession(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(conversationService.requireSession(id), KnowledgeSessionRespVO.class));
    }

    @PutMapping("/session/rename")
    @Operation(summary = "会话改名")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chat:query')")
    public CommonResult<Boolean> renameSession(@Valid @RequestBody KnowledgeSessionRenameReqVO reqVO) {
        conversationService.renameSession(reqVO.getSessionId(), reqVO.getTitle());
        return success(true);
    }

    @DeleteMapping("/session/delete")
    @Operation(summary = "删除会话（级联软删消息）")
    @Parameter(name = "id", description = "会话编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chat:delete')")
    public CommonResult<Boolean> deleteSession(@RequestParam("id") Long id) {
        conversationService.deleteSession(id);
        return success(true);
    }

    // ==================== 消息 ====================

    @GetMapping("/message/list")
    @Operation(summary = "会话消息历史（当前主链，按 seq 升序）")
    @Parameter(name = "sessionId", description = "会话编号", required = true, example = "2048")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chat:query')")
    public CommonResult<List<KnowledgeChatMessageRespVO>> listMessages(@RequestParam("sessionId") Long sessionId) {
        List<KnowledgeChatMessageRespVO> result = new ArrayList<>();
        for (AiKnowledgeMessage message : conversationService.listMessages(sessionId)) {
            result.add(toVO(message));
        }
        return success(result);
    }

    // ==================== 工具 ====================

    private KnowledgeChatMessageRespVO toVO(AiKnowledgeMessage m) {
        KnowledgeChatMessageRespVO vo = BeanUtils.toBean(m, KnowledgeChatMessageRespVO.class);
        // active 的实体是 Integer，VO 用 Boolean，BeanUtils 不做语义转换，这里显式赋值
        vo.setActive(m.getIsActive() != null && m.getIsActive() == 1);
        vo.setRecallDegraded(m.getRecallDegraded() != null && m.getRecallDegraded() == 1);
        vo.setReferences(parseReferences(m.getReferencesJson()));
        return vo;
    }

    private List<KnowledgeReference> parseReferences(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<KnowledgeReference>>() {
            });
        } catch (Exception ex) {
            // references_json 损坏不影响消息渲染，按无引用处理
            return List.of();
        }
    }
}
