package com.evops.grade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;

/**
 * 出分名单项：按报名号挂成绩，卷面分非负。
 */
@Data
public class ScoreItemRequest {
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotNull(message = "卷面分不能为空")
    @PositiveOrZero(message = "卷面分不能为负")
    private Integer rawScore;
    private String remark;
}
