package com.evops.grade.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 窗口实时口径：库里的配置 + 按当前钟点算出的真实状态。
 * 两个岗都以这个口径回答学员"还能不能复核"。
 */
@Data
public class WindowStatusView {
    private Long batchId;
    private String projectCode;
    private Long ruleId;
    /** 配置状态：NOT_STARTED / OPEN / CLOSED */
    private String windowStatus;
    private LocalDateTime scoreReleaseAt;
    private LocalDateTime windowStartAt;
    private LocalDateTime windowEndAt;
    /** 按当前钟点实时判定的窗口是否开放（超窗即使定时任务还没扫到也是 false） */
    private boolean openNow;
    private Boolean autoClosed;
    private String closedBy;
    private LocalDateTime closedAt;
    private String closeReason;
    private LocalDateTime now;
}
