package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.dto.BatchRequest;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.mapper.ExamBatchMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ExamBatchService {

    private final ExamBatchMapper batchMapper;
    private final ExamSiteService siteService;

    public ExamBatchService(ExamBatchMapper batchMapper, ExamSiteService siteService) {
        this.batchMapper = batchMapper;
        this.siteService = siteService;
    }

    @Transactional
    public ExamBatch createBatch(BatchRequest request) {
        siteService.getSite(request.getSiteId());
        ExamBatch batch = new ExamBatch();
        batch.setCode(request.getCode().trim());
        batch.setName(request.getName().trim());
        batch.setSiteId(request.getSiteId());
        batch.setSubject(request.getSubject());
        batch.setExamDate(request.getExamDate());
        batch.setStartTime(request.getStartTime());
        batch.setEndTime(request.getEndTime());
        batch.setStatus(ExamConstants.BATCH_CREATED);
        batch.setRemark(request.getRemark());
        try {
            batchMapper.insert(batch);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_BATCH_CODE", "批次编码已存在：" + batch.getCode());
        }
        return batch;
    }

    public List<ExamBatch> listBatches() {
        return batchMapper.selectList(new QueryWrapper<ExamBatch>().orderByDesc("create_time"));
    }

    public ExamBatch getBatch(Long id) {
        ExamBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("BATCH_NOT_FOUND", "批次不存在：id=" + id);
        }
        return batch;
    }

    public void updateBatch(ExamBatch batch) {
        batchMapper.updateById(batch);
    }

    public List<ExamBatch> listByIds(java.util.Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new java.util.ArrayList<>();
        }
        return batchMapper.selectBatchIds(ids);
    }
}
