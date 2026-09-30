package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class BatchRequest {
    @NotBlank(message = "批次编码不能为空")
    private String code;
    @NotBlank(message = "批次名称不能为空")
    private String name;
    @NotNull(message = "考点不能为空")
    private Long siteId;
    private String subject;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String remark;
}
