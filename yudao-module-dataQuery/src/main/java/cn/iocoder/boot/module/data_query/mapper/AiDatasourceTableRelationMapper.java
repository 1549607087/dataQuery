package cn.iocoder.boot.module.data_query.mapper;

import cn.iocoder.boot.module.data_query.entity.AiDatasourceTableRelation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 表关系 / Join 路径 Mapper
 *
 * 由 DataQueryAutoConfiguration 的 @MapperScan 统一注册，无需 @Mapper 注解。
 */
public interface AiDatasourceTableRelationMapper extends BaseMapper<AiDatasourceTableRelation> {

}
