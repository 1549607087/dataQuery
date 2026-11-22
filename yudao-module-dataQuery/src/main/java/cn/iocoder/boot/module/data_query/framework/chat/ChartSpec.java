package cn.iocoder.boot.module.data_query.framework.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * P6 图表推荐结果（纯规则生成，不调用模型）。
 *
 * 这是「建议」而不是「执行」：服务端不重新聚合数据、不发起第二次查询，
 * 前端拿到 type / xField / yFields 后直接复用 /ask 返回的原始行渲染。
 */
@Data
@Schema(description = "图表推荐（规则生成，不额外查询数据库）")
public class ChartSpec {

    /** line 折线 / bar 柱状 / pie 饼图 / metric 指标卡 / table 仅表格 / none 结果为空 */
    @Schema(description = "推荐图表类型")
    private String type;

    @Schema(description = "是否可直接渲染（table / none 为 false）")
    private Boolean renderable;

    @Schema(description = "建议标题")
    private String title;

    // Jackson 默认把 getXField() 推断成属性 "xfield"。注解必须加在 getter 上而不是字段上：
    // 加在字段上会让「字段属性 xField」与「getter 属性 xfield」同时出现，序列化出重复键。
    // 这里显式给出 getter，Lombok @Data 检测到同名方法后不再重复生成。
    @Schema(description = "横轴 / 分类字段")
    private String xField;

    @Schema(description = "纵轴 / 数值字段（最多 5 个）")
    private List<String> yFields = new ArrayList<>();

    @JsonProperty("xField")
    public String getXField() {
        return xField;
    }

    @JsonProperty("yFields")
    public List<String> getYFields() {
        return yFields;
    }

    /** 一期恒为 none：不重新聚合，直接复用原始行 */
    @Schema(description = "聚合方式，一期恒为 none")
    private String aggregation = "none";

    @Schema(description = "参与推荐的结果行数")
    private Integer rowCount;

    @Schema(description = "推荐理由（可展示给用户）")
    private String reason;
}
