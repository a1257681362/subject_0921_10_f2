package com.evops.grade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 学员提交复核申请。
 */
@Data
public class ReviewSubmitRequest {
    @NotBlank(message = "复核申请理由不能为空")
    private String requestReason;
    private String contactPhone;
}
