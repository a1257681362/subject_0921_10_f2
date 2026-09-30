package com.evops.grade.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量出分：可带批次限定（报名号全局唯一，批次用于二次校验与窗口开放）。
 */
@Data
public class ScoreReleaseRequest {
    private Long batchId;
    @Valid
    @NotEmpty(message = "出分名单不能为空")
    private List<ScoreItemRequest> scores;
}
