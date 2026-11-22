package cn.iocoder.boot.module.data_query.controller.admin.prompt.vo.prompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理后台 - 提示词渲染调试 Request VO
 *
 * content 与 promptId 二选一：传 content 用于编辑态试渲染（尚未保存），
 * 传 promptId 则读取库中当前正文与变量定义，校验必填变量与占位符登记情况。
 */
@Schema(description = "管理后台 - 提示词渲染调试 Request VO")
@Data
public class AiPromptRenderReqVO {

    @Schema(description = "提示词编号；不传 content 时必填", example = "1024")
    private Long promptId;

    @Schema(description = "待渲染的模板正文；不传则读取 promptId 对应模板")
    private String content;

    @Schema(description = "变量值，key 为占位符名（不含双花括号）")
    private Map<String, String> variables = new HashMap<>();

    @Schema(description = "是否在缺少必填变量值时直接报错，默认 false（仅返回缺失清单）")
    private Boolean strict = false;

}
