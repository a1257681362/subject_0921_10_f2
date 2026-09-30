package com.evops.exam.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class RegistrationRequest {
    @NotBlank(message = "报名号不能为空")
    private String regNo;
    @NotBlank(message = "姓名不能为空")
    private String name;
    private String idType;
    private String idNo;
    private String gender;
    private String phone;
    private String subject;
    private String remark;
}
