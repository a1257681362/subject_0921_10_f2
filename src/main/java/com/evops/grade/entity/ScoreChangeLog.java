package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 改分痕迹：只追加。无论首次落级、复核改分还是重新出分，
 * 改分前后的卷面分与等级都写一条，谁改的、为什么、哪条复核都在。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_score_change_log")
public class ScoreChangeLog extends BaseEntity {
    private Long scoreRecordId;
    private Long batchId;
    private Long registrationId;
    /** INITIAL / REVIEW_CONFIRM / REVIEW_CHANGE / RERUN */
    private String changeType;
    private Integer oldRawScore;
    private String oldGradeLevel;
    private Integer newRawScore;
    private String newGradeLevel;
    private Long ruleVersionId;
    /** 触发本次变化的复核申请（首次落级 / 重新出分为空） */
    private Long reviewId;
    private String reason;
    private String operator;
    private LocalDateTime changedAt;
}
