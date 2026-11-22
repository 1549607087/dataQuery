package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.prompt.runtime.vo.PromptRenderRespVO;

/**
 * S4 提示词运行时渲染（P3-B）
 *
 * 与「编辑态调试渲染」的区别：按 sceneCode 取启用的 ai_prompt（租户覆盖优先、
 * 平台 0 兜底，走 SharedTenantServiceImpl 的可见范围），锁定 current_version_id
 * 对应的 ai_prompt_version <b>只读快照</b>，按快照的 variable_defs 校验并渲染，
 * 不使用编辑态的 ai_prompt_variable，保证可复现、可回滚、可 diff。
 *
 * 失败分支：租户模板停用 / 变量缺失 → 回落平台默认模板；仍缺失 → 明确报错，
 * 不用拼字符串兜底生成，也不把未登记占位符静默留在正文里。
 */
public interface PromptRenderService {

    PromptRenderRespVO render(PromptRenderReqVO reqVO);

}
