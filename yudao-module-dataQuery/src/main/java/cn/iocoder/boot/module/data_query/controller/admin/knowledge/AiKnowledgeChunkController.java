package cn.iocoder.boot.module.data_query.controller.admin.knowledge;

import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChunkPageReqVO;
import cn.iocoder.boot.module.data_query.controller.admin.knowledge.vo.AiKnowledgeChunkRespVO;
import cn.iocoder.boot.module.data_query.entity.AiKnowledgeChunk;
import cn.iocoder.boot.module.data_query.service.AiKnowledgeChunkService;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.util.MyBatisUtils;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.boot.module.data_query.enums.DataSourceErrorCodeConstants.KNOWLEDGE_CHUNK_NOT_EXISTS;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 知识库切片（P9.2）
 *
 * <p>只读接口，用途有两个：一是排查「上传成功但召回为空」（切块是否生成、是否已完成向量化），
 * 二是在问答引用里核对 chunkId 对应的原文与定位信息（页码 / 标题路径 / 字符区间）。
 *
 * <p>刻意不提供编辑接口：切片是解析产物，人工改动会与 content_hash、向量不一致，
 * 需要修正内容时应重新上传文档或触发文档 rebuild。
 */
@Tag(name = "管理后台 - 知识库切片")
@RestController
@RequestMapping("/data-query/knowledge-chunk")
@Validated
public class AiKnowledgeChunkController {

    @Resource
    private AiKnowledgeChunkService chunkService;

    @GetMapping("/page")
    @Operation(summary = "获得知识库切片分页")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chunk:query')")
    public CommonResult<PageResult<AiKnowledgeChunkRespVO>> pageChunk(@Valid AiKnowledgeChunkPageReqVO reqVO) {
        IPage<AiKnowledgeChunk> page = chunkService.page(MyBatisUtils.buildPage(reqVO),
                new LambdaQueryWrapperX<AiKnowledgeChunk>()
                        .eqIfPresent(AiKnowledgeChunk::getBaseId, reqVO.getBaseId())
                        .eqIfPresent(AiKnowledgeChunk::getDocumentId, reqVO.getDocumentId())
                        .likeIfPresent(AiKnowledgeChunk::getFileName, reqVO.getFileName())
                        .eqIfPresent(AiKnowledgeChunk::getSyncStatus, reqVO.getSyncStatus())
                        .eq(AiKnowledgeChunk::getDeleted, 0)
                        .orderByAsc(AiKnowledgeChunk::getDocumentId)
                        .orderByAsc(AiKnowledgeChunk::getChunkIndex));
        PageResult<AiKnowledgeChunk> pageResult = new PageResult<>(page.getRecords(), page.getTotal());
        return success(BeanUtils.toBean(pageResult, AiKnowledgeChunkRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获得切片详情（问答引用埋点核对原文用）")
    @Parameter(name = "id", description = "切片编号", required = true, example = "4096")
    @PreAuthorize("@ss.hasPermission('data-query:knowledge-chunk:query')")
    public CommonResult<AiKnowledgeChunkRespVO> getChunk(@RequestParam("id") Long id) {
        AiKnowledgeChunk chunk = chunkService.getById(id);
        if (chunk == null || (chunk.getDeleted() != null && chunk.getDeleted() == 1)) {
            throw exception(KNOWLEDGE_CHUNK_NOT_EXISTS);
        }
        return success(BeanUtils.toBean(chunk, AiKnowledgeChunkRespVO.class));
    }
}
