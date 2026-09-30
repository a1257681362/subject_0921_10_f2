package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 复核申请：窗口期内学员可提，超窗未结论自动置 WINDOW_CLOSED。
 * 申请提交时快照当时窗口起止，避免后续改配置说不清"当时是否在窗内"。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_review_request")
public class ReviewRequest extends BaseEntity {
    private Long batchId;
    private Long scoreRecordId;
    private Long registrationId;
    /** SUBMITTED / PROCESSING / CONFIRMED / CHANGED / REJECTED / WINDOW_CLOSED */
    private String status;
    private String requestReason;
    private String contactPhone;
    /** 复核结论分数与等级（维持时等于原判） */
    private Integer finalScore;
    private String finalGradeLevel;
    private String finalGradeLabel;
    private String conclusionRemark;
    private String concludedBy;
    private LocalDateTime concludedAt;
    /** 结论通知时间（结论回写即通知） */
    private LocalDateTime notifiedAt;
    /** 提交时的窗口起止快照 */
    private LocalDateTime windowStartAt;
    private LocalDateTime windowEndAt;
    private LocalDateTime submittedAt;
}
