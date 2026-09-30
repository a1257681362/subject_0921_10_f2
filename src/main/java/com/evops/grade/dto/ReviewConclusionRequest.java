package com.evops.grade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

/**
 * 复核结论回写：
 * - CONFIRM 维持原判：不用带分数，以原判为准并照样留痕通知；
 * - CHANGE 复核改分：必须带最终卷面分，系统重新落级；
 * - REJECT 不予受理（材料不齐等）：不下成绩变更。
 */
@Data
public class ReviewConclusionRequest {
    /** CONFIRM / CHANGE / REJECT */
    @NotBlank(message = "复核处理动作不能为空")
    private String action;
    /** action=CHANGE 时必填 */
    @PositiveOrZero(message = "最终卷面分不能为负")
    private Integer finalScore;
    private String conclusionRemark;
}
