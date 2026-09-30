package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 等级规则版本：分数段内容冻结在版本里，只追加不删改。
 * 已出成绩永久指向当时命中的版本，规则再发新版也不改历史等级。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_rule_version")
public class GradeRuleVersion extends BaseEntity {
    private Long ruleId;
    private Integer versionNo;
    /** DRAFT / EFFECTIVE / ARCHIVED */
    private String status;
    /** 卷面分满分区间下界（一般 0） */
    private Integer minScore;
    /** 卷面分满分区间上界（满分） */
    private Integer maxScore;
    /** 分数段 JSON（List&lt;GradeBand&gt; 原样冻结） */
    private String bandsJson;
    private String publishedBy;
    private LocalDateTime publishedAt;
    private String remark;
}
