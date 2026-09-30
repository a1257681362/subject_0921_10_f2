package com.evops.grade.dto;

import com.evops.grade.model.GradeBand;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * 新建规则版本（草稿）：卷面分满分区间 + 分数段落级表。
 */
@Data
public class GradeRuleVersionRequest {
    /** 满分区间下界，缺省 0 */
    private Integer minScore;
    @NotNull(message = "卷面满分上限不能为空")
    private Integer maxScore;
    @Valid
    @NotEmpty(message = "分数段至少配置一段")
    @Size(max = 32, message = "分数段数量不能超过 32")
    private List<GradeBand> bands;
    private String remark;
}
