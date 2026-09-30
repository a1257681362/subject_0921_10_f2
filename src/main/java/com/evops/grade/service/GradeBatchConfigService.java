package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.GradeBatchConfigRequest;
import com.evops.grade.entity.GradeBatchConfig;
import com.evops.grade.entity.ReviewRequest;
import com.evops.grade.mapper.GradeBatchConfigMapper;
import com.evops.grade.mapper.ReviewRequestMapper;
import com.evops.grade.vo.WindowStatusView;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.service.ExamBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 批次复核窗口：起止跟批次配置走，不写死。
 * 两个岗共用同一口径：窗口状态以当前钟点实时判定为准（懒关闭），
 * 定时任务只负责把过期窗口落成 CLOSED 并发通知，即使任务没扫到，超窗也提不了复核。
 */
@Service
public class GradeBatchConfigService {

    private final GradeBatchConfigMapper configMapper;
    private final ReviewRequestMapper reviewMapper;
    private final ExamBatchService batchService;
    private final GradeRuleService ruleService;
    private final GradeNotificationService notificationService;

    public GradeBatchConfigService(GradeBatchConfigMapper configMapper,
                                   ReviewRequestMapper reviewMapper,
                                   ExamBatchService batchService,
                                   GradeRuleService ruleService,
                                   GradeNotificationService notificationService) {
        this.configMapper = configMapper;
        this.reviewMapper = reviewMapper;
        this.batchService = batchService;
        this.ruleService = ruleService;
        this.notificationService = notificationService;
    }

    // ---------- 配置（出分前可改；出分后窗口锁定） ----------

    @Transactional
    public GradeBatchConfig configure(Long batchId, GradeBatchConfigRequest request) {
        ExamBatch batch = batchService.getBatch(batchId);
        GradeBatchConfig config = findByBatch(batchId);

        Long ruleId = request.getRuleId() != null ? request.getRuleId()
                : (config != null ? config.getRuleId() : null);
        if (ruleId == null) {
            throw new BusinessException("RULE_REQUIRED", "配置复核窗口必须指定等级规则");
        }
        // 规则必须已有生效版本，避免批次配了一条不能出分的规则
        ruleService.requireEffectiveVersion(ruleId);

        LocalDateTime start = request.getWindowStartAt();
        LocalDateTime end = request.getWindowEndAt();
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException("WINDOW_RANGE_INVALID", "复核窗口截止时间不能早于开始时间");
        }

        if (config != null && config.getScoreReleaseAt() != null) {
            throw new BusinessException("WINDOW_LOCKED",
                    "批次已出分，复核窗口已锁定，不能再改起止时间：" + batch.getCode());
        }

        if (config == null) {
            config = new GradeBatchConfig();
            config.setBatchId(batchId);
            config.setProjectCode(GradeConstants.PROJECT_ASSIST);
            config.setAutoClosed(false);
        }
        config.setRuleId(ruleId);
        config.setWindowStartAt(start);
        config.setWindowEndAt(end);
        // 出分前一律未开始；出分钟点记录时再按起止时间统一开放，避免出现"开了窗却没成绩"
        config.setWindowStatus(GradeConstants.WINDOW_NOT_STARTED);
        if (config.getId() == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config;
    }

    public GradeBatchConfig getConfig(Long batchId) {
        GradeBatchConfig config = findByBatch(batchId);
        if (config == null) {
            throw new BusinessException("GRADE_CONFIG_NOT_FOUND", "该批次尚未配置等级规则与复核窗口：batchId=" + batchId);
        }
        return config;
    }

    public GradeBatchConfig findByBatch(Long batchId) {
        return configMapper.selectOne(new QueryWrapper<GradeBatchConfig>().eq("batch_id", batchId));
    }

    // ---------- 实时口径 ----------

    /** 按当前钟点同步状态后返回；任何接口回答窗口问题都走这里，保证两个岗口径一致。 */
    @Transactional
    public WindowStatusView statusView(Long batchId) {
        GradeBatchConfig config = syncClose(getConfig(batchId), LocalDateTime.now());
        LocalDateTime now = LocalDateTime.now();
        WindowStatusView view = new WindowStatusView();
        view.setBatchId(config.getBatchId());
        view.setProjectCode(config.getProjectCode());
        view.setRuleId(config.getRuleId());
        view.setWindowStatus(config.getWindowStatus());
        view.setScoreReleaseAt(config.getScoreReleaseAt());
        view.setWindowStartAt(config.getWindowStartAt());
        view.setWindowEndAt(config.getWindowEndAt());
        view.setOpenNow(isOpenAt(config, now));
        view.setAutoClosed(config.getAutoClosed());
        view.setClosedBy(config.getClosedBy());
        view.setClosedAt(config.getClosedAt());
        view.setCloseReason(config.getCloseReason());
        view.setNow(now);
        return view;
    }

    /**
     * 窗口是否在指定时刻开放：已出分（有出分记录的前提由调用方保证）、未人工/自动关闭、
     * 已过开始时间（未显式配置则出分即开放）、未过截止时间。
     */
    public boolean isOpenAt(GradeBatchConfig config, LocalDateTime time) {
        if (config == null || GradeConstants.WINDOW_CLOSED.equals(config.getWindowStatus())) {
            return false;
        }
        if (config.getScoreReleaseAt() == null) {
            return false;
        }
        LocalDateTime start = config.getWindowStartAt() != null
                ? config.getWindowStartAt() : config.getScoreReleaseAt();
        if (time.isBefore(start)) {
            return false;
        }
        return config.getWindowEndAt() == null || !time.isAfter(config.getWindowEndAt());
    }

    /** 窗口期内校验：未配置 / 未出分 / 未开放 / 已截止分别给明确中文原因。 */
    public void assertOpenAt(GradeBatchConfig config, LocalDateTime time) {
        if (config == null) {
            throw new BusinessException("WINDOW_NOT_CONFIGURED", "该批次尚未配置复核窗口");
        }
        if (GradeConstants.WINDOW_CLOSED.equals(config.getWindowStatus())) {
            throw new BusinessException("REVIEW_WINDOW_CLOSED",
                    "复核窗口已关闭（" + (Boolean.TRUE.equals(config.getAutoClosed()) ? "超窗自动关闭" : "人工关闭")
                            + (config.getWindowEndAt() != null ? "，截止 " + config.getWindowEndAt() : "") + "）");
        }
        if (config.getScoreReleaseAt() == null) {
            throw new BusinessException("SCORE_NOT_RELEASED", "成绩尚未发布，复核窗口未开放");
        }
        LocalDateTime start = config.getWindowStartAt() != null
                ? config.getWindowStartAt() : config.getScoreReleaseAt();
        if (time.isBefore(start)) {
            throw new BusinessException("REVIEW_WINDOW_NOT_OPEN", "复核窗口尚未开放，开始时间：" + start);
        }
        if (config.getWindowEndAt() != null && time.isAfter(config.getWindowEndAt())) {
            throw new BusinessException("REVIEW_WINDOW_EXPIRED",
                    "已超过复核窗口截止时间：" + config.getWindowEndAt());
        }
    }

    // ---------- 出分时回调：记录出分钟点并开放窗口 ----------

    @Transactional
    public GradeBatchConfig markReleased(Long batchId) {
        GradeBatchConfig config = getConfig(batchId);
        LocalDateTime now = LocalDateTime.now();
        if (config.getScoreReleaseAt() == null) {
            config.setScoreReleaseAt(now);
            if (config.getWindowStartAt() == null) {
                config.setWindowStartAt(now);
            }
        }
        reconcile(config, now);
        configMapper.updateById(config);
        return config;
    }

    /** 人工提前关闭窗口（窗口起止仍保留可查）。 */
    @Transactional
    public GradeBatchConfig manualClose(Long batchId, String reason) {
        GradeBatchConfig config = getConfig(batchId);
        if (GradeConstants.WINDOW_CLOSED.equals(config.getWindowStatus())) {
            throw new BusinessException("WINDOW_ALREADY_CLOSED", "复核窗口已经是关闭状态");
        }
        applyClose(config, false, OperatorHolder.current(),
                reason == null || reason.trim().isEmpty() ? "人工关闭复核窗口" : reason.trim(),
                LocalDateTime.now());
        return config;
    }

    // ---------- 超窗自动关闭 ----------

    /** 懒同步：任何读写路径经过时，顺手把过期窗口落 CLOSED、到点窗口落 OPEN。返回（可能已更新的）配置。 */
    @Transactional
    public GradeBatchConfig syncClose(GradeBatchConfig config, LocalDateTime now) {
        if (config == null || GradeConstants.WINDOW_CLOSED.equals(config.getWindowStatus())) {
            return config;
        }
        boolean expired = config.getScoreReleaseAt() != null
                && config.getWindowEndAt() != null
                && now.isAfter(config.getWindowEndAt());
        if (expired) {
            applyClose(config, true, "system",
                    "超过复核窗口截止时间 " + config.getWindowEndAt() + "，系统自动关闭", now);
            return config;
        }
        // 未关闭的也按当前钟点把 NOT_STARTED/OPEN 落准，避免"已到点却显示未开始"
        String expected = isOpenAt(config, now)
                ? GradeConstants.WINDOW_OPEN : GradeConstants.WINDOW_NOT_STARTED;
        if (!expected.equals(config.getWindowStatus())) {
            config.setWindowStatus(expected);
            configMapper.updateById(config);
        }
        return config;
    }

    /** 定时任务 / 手工触发：扫描所有过期未关窗口并关闭。 */
    @Transactional
    public int autoCloseExpired() {
        return autoCloseExpired(LocalDateTime.now());
    }

    @Transactional
    public int autoCloseExpired(LocalDateTime now) {
        List<GradeBatchConfig> open = configMapper.selectList(new QueryWrapper<GradeBatchConfig>()
                .in("window_status", Arrays.asList(
                        GradeConstants.WINDOW_OPEN, GradeConstants.WINDOW_NOT_STARTED)));
        int closed = 0;
        for (GradeBatchConfig config : open) {
            boolean expired = config.getScoreReleaseAt() != null
                    && config.getWindowEndAt() != null
                    && now.isAfter(config.getWindowEndAt());
            if (expired) {
                applyClose(config, true, "system",
                        "超过复核窗口截止时间 " + config.getWindowEndAt() + "，系统自动关闭", now);
                closed++;
            }
        }
        return closed;
    }

    private void reconcile(GradeBatchConfig config, LocalDateTime now) {
        if (GradeConstants.WINDOW_CLOSED.equals(config.getWindowStatus())) {
            return;
        }
        config.setWindowStatus(isOpenAt(config, now)
                ? GradeConstants.WINDOW_OPEN : GradeConstants.WINDOW_NOT_STARTED);
    }

    /**
     * 落关闭状态并通知在办复核的学员：窗内已提交的申请不作废，继续处理到出结论。
     */
    private void applyClose(GradeBatchConfig config, boolean auto, String operator, String reason,
                            LocalDateTime now) {
        config.setWindowStatus(GradeConstants.WINDOW_CLOSED);
        config.setAutoClosed(auto);
        config.setClosedBy(operator);
        config.setClosedAt(now);
        config.setCloseReason(reason);
        configMapper.updateById(config);

        List<ReviewRequest> active = activeReviews(config.getBatchId());
        for (ReviewRequest review : active) {
            notificationService.notify(config.getBatchId(), review.getRegistrationId(), review.getId(),
                    review.getScoreRecordId(), GradeConstants.NOTIFY_WINDOW_CLOSED,
                    "复核窗口已关闭，您的复核申请继续处理",
                    "本批次复核窗口已于 " + now + " 关闭。您在窗内提交的复核申请（编号 "
                            + review.getId() + "）继续有效，结论作出后另行通知。",
                    operator);
        }
    }

    private List<ReviewRequest> activeReviews(Long batchId) {
        List<ReviewRequest> result = new ArrayList<>();
        result.addAll(reviewMapper.selectList(new QueryWrapper<ReviewRequest>()
                .eq("batch_id", batchId)
                .in("status", Arrays.asList(
                        GradeConstants.REVIEW_SUBMITTED, GradeConstants.REVIEW_PROCESSING))));
        return result;
    }
}
