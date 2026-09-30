package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.BatchRequest;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final ExamBatchService batchService;

    public BatchController(ExamBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ApiResponse<ExamBatch> create(@Valid @RequestBody BatchRequest request) {
        return ApiResponse.ok(batchService.createBatch(request));
    }

    @GetMapping
    public ApiResponse<List<ExamBatch>> list() {
        return ApiResponse.ok(batchService.listBatches());
    }
}
