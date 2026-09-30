package com.evops.exam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_exam_batch")
public class ExamBatch extends BaseEntity {
    private String code;
    private String name;
    private Long siteId;
    private String subject;
    private LocalDate examDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** CREATED 建档 / ARRANGED 已编排 / FINALIZED 已办结 */
    private String status;
    private String remark;
}
