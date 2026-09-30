package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.ReviewConclusionRequest;
import com.evops.grade.dto.ReviewSubmitRequest;
import com.evops.grade.entity.GradeNotification;
import com.evops.grade.entity.ReviewRequest;
import com.evops.grade.service.GradeNotificationService;
import com.evops.grade.service.ReviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 成绩复核：窗口期内学员申请，经办受理 / 回写结论（维持 / 改分 / 不予受理）；
 * 结论回写、成绩改分留痕、结论通知同一事务完成。
 */
@RestController
@RequestMapping("/api/grade/reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final GradeNotificationService notificationService;

    public ReviewController(ReviewService reviewService,
                            GradeNotificationService notificationService) {
        this.reviewService = reviewService;
        this.notificationService = notificationService;
    }

    /** 学员按报名号在窗口内提交复核。 */
    @PostMapping("/submit")
    public ApiResponse<ReviewRequest> submit(@RequestParam String regNo,
                                             @Valid @RequestBody ReviewSubmitRequest request) {
        return ApiResponse.ok(reviewService.submit(regNo, request));
    }

    /** 经办受理（SUBMITTED -> PROCESSING）。 */
    @PostMapping("/{id}/accept")
    public ApiResponse<ReviewRequest> accept(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.accept(id));
    }

    /** 回写结论：CONFIRM 维持 / CHANGE 改分（带 finalScore）/ REJECT 不予受理。 */
    @PostMapping("/{id}/conclusion")
    public ApiResponse<ReviewRequest> conclude(@PathVariable Long id,
                                               @Valid @RequestBody ReviewConclusionRequest request) {
        return ApiResponse.ok(reviewService.conclude(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReviewRequest> get(@PathVariable Long id) {
        return ApiResponse.ok(reviewService.requireReview(id));
    }

    @GetMapping
    public ApiResponse<List<ReviewRequest>> list(@RequestParam Long batchId,
                                                 @RequestParam(required = false) String status) {
        return ApiResponse.ok(reviewService.listByBatch(batchId, status));
    }

    /** 某条复核的通知记录（受理 / 结论 / 窗口关闭）。 */
    @GetMapping("/{id}/notifications")
    public ApiResponse<List<GradeNotification>> notifications(@PathVariable Long id) {
        // 先确认申请存在，不存在给 404 风格业务码而不是空列表
        reviewService.requireReview(id);
        return ApiResponse.ok(notificationService.listByReview(id));
    }
}
