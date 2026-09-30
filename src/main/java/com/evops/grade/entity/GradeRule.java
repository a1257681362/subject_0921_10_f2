package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 等级规则模块：归属一个项目（本批次先跑助考 ASSIST）。
 * 规则本身只描述"这是谁家的规则"，分数段内容全部在版本里，规则带版本。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_rule")
public class GradeRule extends BaseEntity {
    private String code;
    private String name;
    private String projectCode;
    /** DRAFT / ENABLED / DISABLED */
    private String status;
    /** 当前生效版本 id；草稿期为空 */
    private Long currentVersionId;
    private String remark;
}
