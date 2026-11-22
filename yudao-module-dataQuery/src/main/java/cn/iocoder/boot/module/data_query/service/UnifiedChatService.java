package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.ChatOperateReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatAskReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatAskRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedChatMessageRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.chat.vo.UnifiedSessionRespVO;
import cn.iocoder.boot.module.data_query.framework.chat.ChatStageSink;
import cn.iocoder.yudao.framework.common.pojo.PageResult;

import java.util.List;

/**
 * 统一对话门面 Service。
 *
 * <p>前端只对接一套 API：mode=QUERY 委派智能问数引擎，mode=KNOWLEDGE 委派知识库问答引擎。
 * 引擎与数据表保持双轨（两条归属链互斥，不能合表），本层只做路由、统一 VO 转换与聚合会话列表。
 *
 * <p>职责边界：所有归属校验下沉到各引擎 / 门面，本层只做 mode 与 sessionId 的成对校验，
 * 防止用一个 id 查错表。
 */
public interface UnifiedChatService {

    /** 同步提问 */
    UnifiedChatAskRespVO ask(UnifiedChatAskReqVO reqVO);

    /** 流式提问（sink 由 Controller 注入；断流仍落库） */
    UnifiedChatAskRespVO ask(UnifiedChatAskReqVO reqVO, ChatStageSink sink);

    /** 聚合会话分页（mode 可选过滤） */
    PageResult<UnifiedSessionRespVO> pageSessions(String mode, String title, Integer pageNo, Integer pageSize);

    /** 会话主链消息（按 mode 分流） */
    List<UnifiedChatMessageRespVO> listMessages(String mode, Long sessionId);

    /** 某轮回答的全部版本 */
    List<UnifiedChatMessageRespVO> listVersions(String mode, Long messageId);

    /** 某轮问题的全部版本 */
    List<UnifiedChatMessageRespVO> listQuestionVersions(String mode, Long sessionId, Integer seq);

    /** 切换版本 */
    void switchVersion(String mode, Long sessionId, Long targetMessageId);

    /** 会话改名 */
    void renameSession(String mode, Long sessionId, String title);

    /** 删除会话 */
    void deleteSession(String mode, Long sessionId);

    /** 重新生成（同步） */
    UnifiedChatAskRespVO regenerate(String mode, Long messageId, Long modelConfigId);

    /** 重新生成（流式；QUERY 模式不支持，直接抛明确错误码） */
    UnifiedChatAskRespVO regenerate(String mode, Long messageId, Long modelConfigId, ChatStageSink sink);

    /** 修改问题（同步） */
    UnifiedChatAskRespVO edit(String mode, Long messageId, String question, Long modelConfigId);

    /** 修改问题（流式；QUERY 模式不支持，直接抛明确错误码） */
    UnifiedChatAskRespVO edit(String mode, Long messageId, String question, Long modelConfigId, ChatStageSink sink);

    /** 赞踩 */
    void rate(String mode, Long messageId, Integer rating);
}
