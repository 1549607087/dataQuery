package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataPreviewRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataSyncStatusRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.sync.vo.MetadataVisibilityReqVO;
import cn.iocoder.boot.module.data_query.entity.AiDatasource;

/**
 * 元数据同步与可见性维护 Service（P1）
 *
 * 采集目标数据源的表 / 列 / 主键 / 外键，写入 M3 元数据 6 张表中的表、列、Join 路径、同步日志。
 * 不采集业务数据行，不覆盖人工补充的业务语义字段。
 */
public interface MetadataSyncService {

    /** 同步元数据；full = true 走全量（sync_type = 2），否则增量（sync_type = 3） */
    MetadataSyncRespVO sync(Long datasourceId, boolean full);

    /**
     * 用调用方给定的数据源配置与过滤规则同步（一键导入用）。
     *
     * 与 {@link #sync(Long, boolean)} 的区别只在「配置从哪来」：这里用入参实体的
     * allowed_schemas / table_filter，而不是回读数据库里的数据源配置，从而保证
     * 「预览看到的表」与「实际落库的表」完全一致。
     *
     * @param ds 已通过归属校验的实体，id 必须非空
     */
    MetadataSyncRespVO sync(AiDatasource ds, boolean full);

    /** 批量维护表 / 列的 visible / queryable，并把对应向量标记为需重建 */
    Integer updateVisibility(MetadataVisibilityReqVO reqVO);

    /** 元数据同步状态与新鲜度概览 */
    MetadataSyncStatusRespVO status(Long datasourceId);

    /**
     * 只读预览：复用与正式同步完全相同的采集与过滤规则扫描目标库，但不写任何表。
     * 供「一键导入」在落库前展示将要导入的表列清单；传入的实体可以是尚未落库的临时对象。
     *
     * @param ds                    连接配置（密码为明文，仅在本次调用内使用）
     * @param importMaxTables       单次导入允许的最大表数，回显给前端
     * @param maxPreviewTables      响应中最多回传多少张表的明细
     * @param maxColumnsPerTable    每张表最多回传多少个字段名
     */
    MetadataPreviewRespVO preview(AiDatasource ds, int importMaxTables, int maxPreviewTables,
                                  int maxColumnsPerTable);

}
