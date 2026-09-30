package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学员成绩：卷面分 + 按生效版本分数段落下的等级，一人一批次一条当前态。
 * 前后变化全部进 t_score_change_log，主表只存当前值。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_score_record")
public class ScoreRecord extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    /** 卷面分 */
    private Integer rawScore;
    /** 等级编码 */
    private String gradeLevel;
    /** 等级名称 */
    private String gradeLabel;
    /** 落级时命中的规则版本（历史可追溯） */
    private Long ruleVersionId;
    /** RELEASED / UPDATED */
    private String status;
    private String remark;
}
