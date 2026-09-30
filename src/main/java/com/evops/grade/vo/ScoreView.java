package com.evops.grade.vo;

import lombok.Data;

/**
 * 成绩视图：成绩当前态 + 名册关键信息 + 落级版本，列表 / 查询统一返回。
 */
@Data
public class ScoreView {
    private Long id;
    private Long batchId;
    private Long registrationId;
    private String regNo;
    private String name;
    private String phone;
    private Integer rawScore;
    private String gradeLevel;
    private String gradeLabel;
    private Long ruleVersionId;
    private Integer ruleVersionNo;
    private String status;
    private String remark;
    private String createTime;
    private String updateTime;
}
