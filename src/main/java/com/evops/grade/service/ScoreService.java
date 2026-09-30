package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.ScoreItemRequest;
import com.evops.grade.dto.ScoreReleaseRequest;
import com.evops.grade.entity.GradeBatchConfig;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.entity.ScoreChangeLog;
import com.evops.grade.entity.ScoreRecord;
import com.evops.grade.entity.ReviewRequest;
import com.evops.grade.mapper.ReviewRequestMapper;
import com.evops.grade.mapper.ScoreChangeLogMapper;
import com.evops.grade.mapper.ScoreRecordMapper;
import com.evops.grade.model.GradeBand;
import com.evops.grade.vo.ScoreView;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.RegistrationMapper;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import com.evops.exam.vo.AffectedItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出分落级：卷面分按批次规则的生效版本分数段落等级。
 * - 批量动作先 preview 影响范围再 execute；
 * - 首次落级写 INITIAL 痕迹；同一学员重新出分按新分重新落级并写 RERUN 痕迹，前后值都在；
 * - 出分即开放复核窗口并给出分通知，从源头减少"电话问等级"。
 */
@Service
public class ScoreService {

    private final ScoreRecordMapper scoreMapper;
    private final ScoreChangeLogMapper changeLogMapper;
    private final RegistrationMapper registrationMapper;
    private final ReviewRequestMapper reviewMapper;
    private final GradeRuleService ruleService;
    private final GradeBatchConfigService configService;
    private final GradeNotificationService notificationService;

    public ScoreService(ScoreRecordMapper scoreMapper,
                        ScoreChangeLogMapper changeLogMapper,
                        RegistrationMapper registrationMapper,
                        ReviewRequestMapper reviewMapper,
                        GradeRuleService ruleService,
                        GradeBatchConfigService configService,
                        GradeNotificationService notificationService) {
        this.scoreMapper = scoreMapper;
        this.changeLogMapper = changeLogMapper;
        this.registrationMapper = registrationMapper;
        this.reviewMapper = reviewMapper;
        this.ruleService = ruleService;
        this.configService = configService;
        this.notificationService = notificationService;
    }

    // ---------- 批量出分：预检 ----------

    @Transactional(readOnly = true)
    public ActionPreview previewRelease(ScoreReleaseRequest request) {
        Long batchId = request.getBatchId();
        // 不指定批次时名单可能跨批次，按学员所在批次分别取生效版本，避免一把尺子量到底
        Map<Long, GradeRuleVersion> versionCache = new HashMap<>();

        List<AffectedItem> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int actionable = 0;
        int skip = 0;
        for (ScoreItemRequest item : request.getScores()) {
            String regNo = item.getRegNo().trim();
            boolean dupInFile = !seen.add(regNo);
            Registration reg = findReg(regNo);

            boolean ok = true;
            String note;
            String currentStatus = "-";
            if (dupInFile) {
                ok = false;
                note = "名单内报名号重复，执行时整批拒收";
            } else if (reg == null) {
                ok = false;
                note = "报名号不存在，将跳过";
            } else if (batchId != null && !batchId.equals(reg.getBatchId())) {
                ok = false;
                note = "该学员不属于目标批次（实际批次 id=" + reg.getBatchId() + "），将跳过";
            } else {
                GradeBatchConfig config = configService.getConfig(reg.getBatchId());
                GradeRuleVersion version = versionCache.computeIfAbsent(reg.getBatchId(),
                        b -> ruleService.requireEffectiveVersion(config.getRuleId()));
                if (item.getRawScore() < version.getMinScore() || item.getRawScore() > version.getMaxScore()) {
                    ok = false;
                    note = String.format("卷面分 %d 超出满分区间 [%d,%d]，将跳过",
                            item.getRawScore(), version.getMinScore(), version.getMaxScore());
                } else {
                    GradeBand band = ruleService.grade(version, item.getRawScore());
                    ScoreRecord existing = findScore(reg.getBatchId(), reg.getId());
                    if (existing == null) {
                        note = "首次出分，落级 " + band.getLevel()
                                + (band.getLabel() != null ? "（" + band.getLabel() + "）" : "");
                    } else {
                        currentStatus = existing.getGradeLevel();
                        if (hasActiveReview(existing.getId())) {
                            ok = false;
                            note = "该学员复核在办，不能重新出分，将跳过";
                        } else {
                            note = String.format("已出分（原 %d 分/%s），将重新落级为 %d 分/%s，前后留痕",
                                    existing.getRawScore(), existing.getGradeLevel(),
                                    item.getRawScore(), band.getLevel());
                        }
                    }
                }
            }
            if (ok) {
                actionable++;
            } else {
                skip++;
            }
            items.add(new AffectedItem(regNo, reg == null ? "-" : reg.getName(), currentStatus, ok, note));
        }

        ActionPreview preview = new ActionPreview();
        preview.setAction("SCORE_RELEASE");
        preview.setTotalScanned(items.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("本次出分 %d 人：可处理 %d 人，跳过 %d 人", items.size(), actionable, skip));
        preview.setItems(items);
        return preview;
    }

    // ---------- 批量出分：执行 ----------

    @Transactional
    public ActionResult releaseScores(ScoreReleaseRequest request) {
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        // 文件内重复直接整批拒收，与报名导入口径一致
        Set<String> seen = new HashSet<>();
        for (ScoreItemRequest item : request.getScores()) {
            if (!seen.add(item.getRegNo().trim())) {
                throw new BusinessException("SCORE_DUP_IN_FILE",
                        "出分名单内报名号重复，整批拒收：" + item.getRegNo().trim());
            }
        }

        List<String> affected = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Long batchId = request.getBatchId();
        // 不指定批次时名单可能跨批次，每个批次首次出分只记录一次出分钟点、开放一次窗口
        Set<Long> releasedBatches = new HashSet<>();

        for (ScoreItemRequest item : request.getScores()) {
            String regNo = item.getRegNo().trim();
            Registration reg = findReg(regNo);
            if (reg == null) {
                skipped.add(regNo + "（报名号不存在）");
                continue;
            }
            if (batchId != null && !batchId.equals(reg.getBatchId())) {
                skipped.add(regNo + "（不属于目标批次）");
                continue;
            }
            GradeBatchConfig config = configService.getConfig(reg.getBatchId());
            GradeRuleVersion version = ruleService.requireEffectiveVersion(config.getRuleId());
            int raw = item.getRawScore();
            if (raw < version.getMinScore() || raw > version.getMaxScore()) {
                skipped.add(regNo + String.format("（卷面分 %d 超出满分区间 [%d,%d]）",
                        raw, version.getMinScore(), version.getMaxScore()));
                continue;
            }

            if (releasedBatches.add(reg.getBatchId())) {
                configService.markReleased(reg.getBatchId());
            }

            GradeBand band = ruleService.grade(version, raw);
            ScoreRecord existing = findScore(reg.getBatchId(), reg.getId());
            if (existing == null) {
                ScoreRecord record = new ScoreRecord();
                record.setBatchId(reg.getBatchId());
                record.setRegistrationId(reg.getId());
                record.setRawScore(raw);
                record.setGradeLevel(band.getLevel());
                record.setGradeLabel(band.getLabel());
                record.setRuleVersionId(version.getId());
                record.setStatus(GradeConstants.SCORE_RELEASED);
                record.setRemark(item.getRemark());
                scoreMapper.insert(record);

                writeLog(record, GradeConstants.CHANGE_INITIAL, null, null, raw, band.getLevel(),
                        version.getId(), null, "出分落级", operator, now);
                notificationService.notify(reg.getBatchId(), reg.getId(), null, record.getId(),
                        GradeConstants.NOTIFY_SCORE_RELEASED,
                        "成绩已发布：" + band.getLevel(),
                        String.format("您的卷面分 %d 分，等级 %s%s。如对成绩有异议，请在复核窗口截止前提出。",
                                raw, band.getLevel(),
                                band.getLabel() != null ? "（" + band.getLabel() + "）" : ""),
                        operator);
            } else {
                if (hasActiveReview(existing.getId())) {
                    skipped.add(regNo + "（复核在办，不能重新出分）");
                    continue;
                }
                Integer oldScore = existing.getRawScore();
                String oldLevel = existing.getGradeLevel();
                existing.setRawScore(raw);
                existing.setGradeLevel(band.getLevel());
                existing.setGradeLabel(band.getLabel());
                existing.setRuleVersionId(version.getId());
                existing.setStatus(GradeConstants.SCORE_UPDATED);
                if (item.getRemark() != null) {
                    existing.setRemark(item.getRemark());
                }
                scoreMapper.updateById(existing);

                writeLog(existing, GradeConstants.CHANGE_RERUN, oldScore, oldLevel, raw, band.getLevel(),
                        version.getId(), null,
                        "重新出分更正（原 " + oldScore + " 分/" + oldLevel + "）", operator, now);
                notificationService.notify(reg.getBatchId(), reg.getId(), null, existing.getId(),
                        GradeConstants.NOTIFY_SCORE_RELEASED,
                        "成绩已更正：" + band.getLevel(),
                        String.format("您的成绩已更正为卷面分 %d 分，等级 %s，更正前后均已留痕。",
                                raw, band.getLevel()),
                        operator);
            }
            affected.add(regNo);
        }

        ActionResult result = new ActionResult();
        result.setAction("SCORE_RELEASE");
        result.setAffectedCount(affected.size());
        result.setSkipCount(skipped.size());
        result.setOperator(operator);
        result.setExecutedAt(now.toString());
        result.setAffectedKeys(affected);
        result.setSkipped(skipped);
        return result;
    }

    // ---------- 查询 ----------

    @Transactional(readOnly = true)
    public List<ScoreView> listByBatch(Long batchId) {
        List<ScoreRecord> records = scoreMapper.selectList(new QueryWrapper<ScoreRecord>()
                .eq("batch_id", batchId).orderByAsc("id"));
        return toViews(records);
    }

    @Transactional(readOnly = true)
    public ScoreView getByRegNo(String regNo) {
        Registration reg = requireReg(regNo);
        ScoreRecord record = findScore(reg.getBatchId(), reg.getId());
        if (record == null) {
            throw new BusinessException("SCORE_NOT_FOUND", "该学员尚未出分：" + regNo.trim());
        }
        ScoreView view = toView(record, reg);
        view.setRuleVersionNo(ruleService.requireVersion(record.getRuleVersionId()).getVersionNo());
        return view;
    }

    public ScoreRecord getScoreRecord(Long id) {
        ScoreRecord record = scoreMapper.selectById(id);
        if (record == null) {
            throw new BusinessException("SCORE_NOT_FOUND", "成绩记录不存在：id=" + id);
        }
        return record;
    }

    public ScoreRecord findScore(Long batchId, Long registrationId) {
        return scoreMapper.selectOne(new QueryWrapper<ScoreRecord>()
                .eq("batch_id", batchId)
                .eq("registration_id", registrationId));
    }

    public Registration requireReg(String regNo) {
        Registration reg = findReg(regNo);
        if (reg == null) {
            throw new BusinessException("REG_NOT_FOUND", "报名号不存在：" + regNo.trim());
        }
        return reg;
    }

    public List<ScoreChangeLog> listChangeLogs(Long scoreRecordId) {
        getScoreRecord(scoreRecordId);
        return changeLogMapper.selectList(new QueryWrapper<ScoreChangeLog>()
                .eq("score_record_id", scoreRecordId)
                .orderByAsc("changed_at", "id"));
    }

    // ---------- 复核结论回写（供 ReviewService 调用，改分逻辑只此一处） ----------

    /**
     * 按复核结论回写成绩：
     * CHANGE 用原落级版本重新定级（同一把尺子），前后分值 / 等级都写痕迹；
     * CONFIRM 不改成绩但照样写一条维持痕迹；REJECT 不动成绩、不写痕迹。
     */
    @Transactional
    public void applyReviewConclusion(ScoreRecord record, String action, Integer finalScore,
                                      Long reviewId, String reason, String operator, LocalDateTime now) {
        if (GradeConstants.REVIEW_CHANGED.equals(action)) {
            if (finalScore == null) {
                throw new BusinessException("FINAL_SCORE_REQUIRED", "复核改分必须填写最终卷面分");
            }
            GradeRuleVersion version = ruleService.requireVersion(record.getRuleVersionId());
            GradeBand band;
            try {
                band = ruleService.grade(version, finalScore);
            } catch (BusinessException e) {
                throw new BusinessException("FINAL_SCORE_INVALID",
                        "复核最终分无法落级：" + e.getMessage());
            }
            Integer oldScore = record.getRawScore();
            String oldLevel = record.getGradeLevel();
            record.setRawScore(finalScore);
            record.setGradeLevel(band.getLevel());
            record.setGradeLabel(band.getLabel());
            record.setStatus(GradeConstants.SCORE_UPDATED);
            scoreMapper.updateById(record);
            writeLog(record, GradeConstants.CHANGE_REVIEW_CHANGE, oldScore, oldLevel,
                    finalScore, band.getLevel(), version.getId(), reviewId,
                    "复核改分：" + reason, operator, now);
        } else if (GradeConstants.REVIEW_CONFIRMED.equals(action)) {
            writeLog(record, GradeConstants.CHANGE_REVIEW_CONFIRM,
                    record.getRawScore(), record.getGradeLevel(),
                    record.getRawScore(), record.getGradeLevel(),
                    record.getRuleVersionId(), reviewId,
                    "复核维持原判：" + reason, operator, now);
        }
        // REJECT 不落成绩变更
    }

    public boolean hasActiveReview(Long scoreRecordId) {
        Long count = reviewMapper.selectCount(new QueryWrapper<ReviewRequest>()
                .eq("score_record_id", scoreRecordId)
                .in("status", Arrays.asList(
                        GradeConstants.REVIEW_SUBMITTED, GradeConstants.REVIEW_PROCESSING)));
        return count != null && count > 0;
    }

    // ---------- 内部工具 ----------

    private void writeLog(ScoreRecord record, String type, Integer oldScore, String oldLevel,
                          Integer newScore, String newLevel, Long versionId, Long reviewId,
                          String reason, String operator, LocalDateTime now) {
        ScoreChangeLog log = new ScoreChangeLog();
        log.setScoreRecordId(record.getId());
        log.setBatchId(record.getBatchId());
        log.setRegistrationId(record.getRegistrationId());
        log.setChangeType(type);
        log.setOldRawScore(oldScore);
        log.setOldGradeLevel(oldLevel);
        log.setNewRawScore(newScore);
        log.setNewGradeLevel(newLevel);
        log.setRuleVersionId(versionId);
        log.setReviewId(reviewId);
        log.setReason(reason);
        log.setOperator(operator);
        log.setChangedAt(now);
        changeLogMapper.insert(log);
    }

    private Registration findReg(String regNo) {
        if (regNo == null || regNo.trim().isEmpty()) {
            return null;
        }
        return registrationMapper.selectOne(new QueryWrapper<Registration>().eq("reg_no", regNo.trim()));
    }

    private List<ScoreView> toViews(List<ScoreRecord> records) {
        if (records.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> regIds = new ArrayList<>();
        for (ScoreRecord r : records) {
            regIds.add(r.getRegistrationId());
        }
        Map<Long, Registration> regs = new HashMap<>();
        for (Registration reg : registrationMapper.selectBatchIds(regIds)) {
            regs.put(reg.getId(), reg);
        }
        Map<Long, Integer> versionNos = new HashMap<>();
        List<ScoreView> views = new ArrayList<>();
        for (ScoreRecord r : records) {
            Registration reg = regs.get(r.getRegistrationId());
            ScoreView view = toView(r, reg);
            view.setRuleVersionNo(versionNos.computeIfAbsent(r.getRuleVersionId(), id -> {
                try {
                    return ruleService.requireVersion(id).getVersionNo();
                } catch (BusinessException e) {
                    return null;
                }
            }));
            views.add(view);
        }
        return views;
    }

    private ScoreView toView(ScoreRecord r, Registration reg) {
        ScoreView view = new ScoreView();
        view.setId(r.getId());
        view.setBatchId(r.getBatchId());
        view.setRegistrationId(r.getRegistrationId());
        if (reg != null) {
            view.setRegNo(reg.getRegNo());
            view.setName(reg.getName());
            view.setPhone(reg.getPhone());
        }
        view.setRawScore(r.getRawScore());
        view.setGradeLevel(r.getGradeLevel());
        view.setGradeLabel(r.getGradeLabel());
        view.setRuleVersionId(r.getRuleVersionId());
        view.setStatus(r.getStatus());
        view.setRemark(r.getRemark());
        view.setCreateTime(r.getCreateTime() == null ? null : r.getCreateTime().toString());
        view.setUpdateTime(r.getUpdateTime() == null ? null : r.getUpdateTime().toString());
        return view;
    }
}
