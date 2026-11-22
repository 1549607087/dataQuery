package cn.iocoder.boot.module.data_query.service;

import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataImportReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataImportRespVO;
import cn.iocoder.boot.module.data_query.controller.admin.metadataimport.vo.MetadataPreviewRespVO;

/**
 * 表结构一键导入 Service（P5-A）
 *
 * 把原本需要 4 次请求、且要在多个页面之间跳转的接入链路收敛成一次提交：
 * 创建/复用数据源 → 连通性检测 → 全量表 / 列 / 主键 / 外键同步 → 向量索引构建。
 *
 * 边界：
 * 1. 不重复实现采集与过滤，预览与正式同步共用 MetadataSyncService，所见即所得；
 * 2. 不绕过既有安全链路：租户归属走 AiDatasourceOwnerValidator，驱动走白名单，
 *    S6 / S7 的表列白名单口径完全不变；
 * 3. 预览（dryRun）零落库副作用；连接失败发生在创建数据源之前，不留半残记录；
 * 4. 执行期失败返回结构化报告并带数据源编号，便于前端直接展示与重试。
 */
public interface MetadataImportService {

    /** 只预览：只读扫描目标库结构，不创建数据源、不写任何元数据行 */
    MetadataPreviewRespVO preview(MetadataImportReqVO reqVO);

    /** 一键导入；reqVO.dryRun = true 时等价于只预览 */
    MetadataImportRespVO importMetadata(MetadataImportReqVO reqVO);

}
