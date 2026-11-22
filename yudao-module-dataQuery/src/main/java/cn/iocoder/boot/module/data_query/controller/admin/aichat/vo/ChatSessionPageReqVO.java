package cn.iocoder.boot.module.data_query.controller.admin.aichat.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 我的会话分页请求（P7）
 *
 * 刻意不接受 userId / tenantId：用户身份只从登录态取，前端无法查看他人会话。
 */
@Schema(description = "管理后台 - 我的会话分页请求")
@Data
public class ChatSessionPageReqVO extends PageParam {

    @Schema(description = "会话标题（模糊匹配）")
    private String title;
}
