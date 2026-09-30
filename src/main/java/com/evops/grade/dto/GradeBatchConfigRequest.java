package com.evops.grade.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 批次出分与复核窗口配置。窗口起止显式给，跟批次走，不写死。
 * 出分时刻不要求预填：执行出分时自动记录，窗口据此开放。
 */
@Data
public class GradeBatchConfigRequest {
    /** 出分使用的规则；不填则沿用批次原有规则 */
    private Long ruleId;
    /** 窗口开始时间；不填默认出分时刻即开放 */
    private LocalDateTime windowStartAt;
    /** 窗口截止时间；不填表示不限时（只能人工关闭） */
    private LocalDateTime windowEndAt;
    private String remark;
}
