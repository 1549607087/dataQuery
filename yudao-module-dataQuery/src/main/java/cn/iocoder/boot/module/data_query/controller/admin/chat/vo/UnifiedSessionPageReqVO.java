package cn.iocoder.boot.module.data_query.controller.admin.chat.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - 统一会话分页请求。
 *
 * <p>mode 可选：不传则聚合两种模式；传 QUERY / KNOWLEDGE 则只看该模式。
 * 刻意不接受 userId / tenantId：身份只从登录态取。
 */
@Schema(description = "管理后台 - 统一会话分页请求")
@Data
public class UnifiedSessionPageReqVO extends PageParam {

    @Schema(description = "业务类型过滤：QUERY / KNOWLEDGE；不传则全部", example = "QUERY")
    private String mode;

    @Schema(description = "会话标题（模糊匹配）")
    private String title;
}
