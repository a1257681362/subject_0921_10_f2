package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 批次等级与复核窗口配置：窗口起止跟批次走，不写死。
 * 两个岗都从这里读同一套窗口口径。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_batch_config")
public class GradeBatchConfig extends BaseEntity {
    private Long batchId;
    private String projectCode;
    private Long ruleId;
    /** NOT_STARTED / OPEN / CLOSED */
    private String windowStatus;
    /** 出分时刻：出分后窗口才开放 */
    private LocalDateTime scoreReleaseAt;
    private LocalDateTime windowStartAt;
    private LocalDateTime windowEndAt;
    /** 是否超窗自动关闭（人工关闭为 false） */
    private Boolean autoClosed;
    private String closedBy;
    private LocalDateTime closedAt;
    private String closeReason;
}
