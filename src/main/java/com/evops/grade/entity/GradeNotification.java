package com.evops.grade.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知记录：出分、受理、复核结论、窗口关闭都落一条。
 * 当前版本没有外发渠道，写完即可在系统内查询；后续接短信 / 站内信时按 status 推送。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_grade_notification")
public class GradeNotification extends BaseEntity {
    private Long batchId;
    private Long registrationId;
    private Long reviewId;
    private Long scoreRecordId;
    /** SCORE_RELEASED / REVIEW_RECEIVED / REVIEW_CONCLUDED / WINDOW_CLOSED */
    private String category;
    private String title;
    private String content;
    /** PENDING 待外发 */
    private String status;
    private String phone;
    private String operator;
    private LocalDateTime sentAt;
}
