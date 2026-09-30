package com.evops.grade.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class GradeRuleRequest {
    @NotBlank(message = "规则编码不能为空")
    private String code;
    @NotBlank(message = "规则名称不能为空")
    private String name;
    /** 归属项目，缺省 ASSIST 助考项目 */
    private String projectCode;
    private String remark;
}
