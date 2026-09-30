package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_registration")
public class Registration extends BaseEntity {
    private String regNo;
    private String name;
    private String idType;
    private String idNo;
    private String gender;
    private String phone;
    private Long batchId;
    private String subject;
    /** REGISTERED 已报名 / ARRANGED 已编排 */
    private String status;
    private Integer correctionCount;
    private String remark;
}
