package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.ReviewConclusionRequest;
import com.evops.grade.dto.ReviewSubmitRequest;
import com.evops.grade.entity.GradeBatchConfig;
import com.evops.grade.entity.ReviewRequest;
import com.evops.grade.entity.ScoreRecord;
import com.evops.grade.mapper.ReviewRequestMapper;
import com.evops.exam.entity.Registration;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 复核申请与结论：
 * - 窗口判定只有一个口径（GradeBatchConfigService 实时钟点），窗口期内学员才能提；
 *   窗口关闭只拦新申请，窗内已提交的在办申请照常处理到结论。
 * - 结论分维持 / 改分 / 不予受理；改分前后留痕、成绩回写、结论通知都在同一事务完成。
 */
@Service
public class ReviewService {

    private final ReviewRequestMapper reviewMapper;
    private final ScoreService scoreService;
    private final GradeBatchConfigService configService;
    private final GradeNotificationService notificationService;

    public ReviewService(ReviewRequestMapper reviewMapper,
                         ScoreService scoreService,
                         GradeBatchConfigService configService,
                         GradeNotificationService notificationService) {
        this.reviewMapper = reviewMapper;
        this.scoreService = scoreService;
        this.configService = configService;
        this.notificationService = notificationService;
    }

    // ---------- 学员提交（窗口期内） ----------

    @Transactional
    public ReviewRequest submit(String regNo, ReviewSubmitRequest request) {
        Registration reg = scoreService.requireReg(regNo);
        ScoreRecord score = scoreService.findScore(reg.getBatchId(), reg.getId());
        if (score == null) {
            throw new BusinessException("SCORE_NOT_FOUND",
                    "成绩尚未发布，暂不能申请复核：" + reg.getRegNo());
        }

        GradeBatchConfig config = configService.getConfig(reg.getBatchId());
        LocalDateTime now = LocalDateTime.now();
        // 实时口径：过期即使定时任务还没扫到，这里也会顺手落 CLOSED 并拒绝新申请
        configService.syncClose(config, now);
        configService.assertOpenAt(config, now);

        List<ReviewRequest> active = reviewMapper.selectList(new QueryWrapper<ReviewRequest>()
                .eq("batch_id", reg.getBatchId())
                .eq("registration_id", reg.getId())
                .in("status", Arrays.asList(
                        GradeConstants.REVIEW_SUBMITTED, GradeConstants.REVIEW_PROCESSING)));
        if (!active.isEmpty()) {
            throw new BusinessException("REVIEW_ALREADY_ACTIVE",
                    "该学员本批次已有在办复核申请（编号 " + active.get(0).getId() + "），请勿重复提交");
        }

        ReviewRequest review = new ReviewRequest();
        review.setBatchId(reg.getBatchId());
        review.setScoreRecordId(score.getId());
        review.setRegistrationId(reg.getId());
        review.setStatus(GradeConstants.REVIEW_SUBMITTED);
        review.setRequestReason(request.getRequestReason().trim());
        review.setContactPhone(request.getContactPhone());
        review.setWindowStartAt(config.getWindowStartAt() != null
                ? config.getWindowStartAt() : config.getScoreReleaseAt());
        review.setWindowEndAt(config.getWindowEndAt());
        review.setSubmittedAt(now);
        reviewMapper.insert(review);

        notificationService.notify(reg.getBatchId(), reg.getId(), review.getId(), score.getId(),
                GradeConstants.NOTIFY_REVIEW_RECEIVED,
                "复核申请已受理",
                "您于 " + now + " 提交的成绩复核申请（编号 " + review.getId()
                        + "）已受理，结论作出后将通知您。",
                OperatorHolder.current());
        return review;
    }

    // ---------- 受理（可省略的中间态，便于两个岗分活） ----------

    @Transactional
    public ReviewRequest accept(Long reviewId) {
        ReviewRequest review = requireReview(reviewId);
        if (!GradeConstants.REVIEW_SUBMITTED.equals(review.getStatus())) {
            throw new BusinessException("REVIEW_NOT_SUBMITTED",
                    "只有已提交待受理的申请可以受理，当前状态：" + review.getStatus());
        }
        review.setStatus(GradeConstants.REVIEW_PROCESSING);
        reviewMapper.updateById(review);
        return review;
    }

    // ---------- 结论回写 ----------

    @Transactional
    public ReviewRequest conclude(Long reviewId, ReviewConclusionRequest request) {
        ReviewRequest review = requireReview(reviewId);
        if (!isActive(review.getStatus())) {
            throw new BusinessException("REVIEW_NOT_ACTIVE",
                    "该复核申请已结论，不能重复回写，当前状态：" + review.getStatus());
        }
        String action = request.getAction().trim().toUpperCase();
        if (!"CONFIRM".equals(action) && !"CHANGE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("REVIEW_ACTION_INVALID",
                    "复核处理动作只能是 CONFIRM（维持）/ CHANGE（改分）/ REJECT（不予受理）");
        }

        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();
        String remark = request.getConclusionRemark() == null ? "" : request.getConclusionRemark().trim();

        ScoreRecord score = scoreService.getScoreRecord(review.getScoreRecordId());
        // 改分 / 维持都在 ScoreService 一处回写成绩并留痕（维持也留痕）；REJECT 不动成绩
        scoreService.applyReviewConclusion(score, normalizeStatus(action), request.getFinalScore(),
                review.getId(), remark, operator, now);
        // score 在同一事务内已被回写为当前值，直接据此记结论
        review.setStatus(normalizeStatus(action));
        review.setConclusionRemark(remark);
        review.setConcludedBy(operator);
        review.setConcludedAt(now);
        review.setNotifiedAt(now);
        if ("CONFIRM".equals(action) || "CHANGE".equals(action)) {
            review.setFinalScore(score.getRawScore());
            review.setFinalGradeLevel(score.getGradeLevel());
            review.setFinalGradeLabel(score.getGradeLabel());
        }
        reviewMapper.updateById(review);

        notificationService.notify(review.getBatchId(), review.getRegistrationId(), review.getId(),
                review.getScoreRecordId(), GradeConstants.NOTIFY_REVIEW_CONCLUDED,
                conclusionTitle(action, review),
                conclusionContent(action, review),
                operator);
        return review;
    }

    // ---------- 查询 ----------

    public ReviewRequest requireReview(Long reviewId) {
        ReviewRequest review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("REVIEW_NOT_FOUND", "复核申请不存在：id=" + reviewId);
        }
        return review;
    }

    public List<ReviewRequest> listByBatch(Long batchId, String status) {
        QueryWrapper<ReviewRequest> wrapper = new QueryWrapper<ReviewRequest>()
                .eq("batch_id", batchId)
                .orderByDesc("submitted_at", "id");
        if (status != null && !status.trim().isEmpty()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        return reviewMapper.selectList(wrapper);
    }

    public List<ReviewRequest> listByRegistration(Long registrationId) {
        return reviewMapper.selectList(new QueryWrapper<ReviewRequest>()
                .eq("registration_id", registrationId)
                .orderByDesc("submitted_at", "id"));
    }

    private boolean isActive(String status) {
        return GradeConstants.REVIEW_SUBMITTED.equals(status)
                || GradeConstants.REVIEW_PROCESSING.equals(status);
    }

    private String normalizeStatus(String action) {
        switch (action) {
            case "CONFIRM": return GradeConstants.REVIEW_CONFIRMED;
            case "CHANGE": return GradeConstants.REVIEW_CHANGED;
            case "REJECT": return GradeConstants.REVIEW_REJECTED;
            default: throw new BusinessException("REVIEW_ACTION_INVALID", "不支持的复核动作：" + action);
        }
    }

    private String conclusionTitle(String action, ReviewRequest review) {
        switch (action) {
            case "CONFIRM": return "复核结论：维持原成绩 " + review.getFinalScore() + " 分/"
                    + review.getFinalGradeLevel();
            case "CHANGE": return "复核结论：成绩更正为 " + review.getFinalScore() + " 分/"
                    + review.getFinalGradeLevel();
            default: return "复核结论：不予受理";
        }
    }

    private String conclusionContent(String action, ReviewRequest review) {
        String base = "您的复核申请（编号 " + review.getId() + "）";
        switch (action) {
            case "CONFIRM":
                return base + "经复核维持原判：卷面分 " + review.getFinalScore() + " 分，等级 "
                        + review.getFinalGradeLevel() + "。";
            case "CHANGE":
                return base + "经复核予以更正：最终卷面分 " + review.getFinalScore() + " 分，等级 "
                        + review.getFinalGradeLevel() + "，更正前后成绩均已留痕。";
            default:
                return base + "经审核不予受理。" + (review.getConclusionRemark().isEmpty()
                        ? "" : "原因：" + review.getConclusionRemark());
        }
    }
}
