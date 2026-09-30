package com.evops.grade.common;

/**
 * 出分落级与复核窗口业务常量。两个岗共用一套口径，不再各说各话。
 */
public final class GradeConstants {

    private GradeConstants() {
    }

    /** 项目编码：本批次先跑助考项目 */
    public static final String PROJECT_ASSIST = "ASSIST";

    // ---------- 等级规则状态 ----------
    /** 草稿：尚未发布任何生效版本 */
    public static final String RULE_DRAFT = "DRAFT";
    /** 启用：存在生效版本，可用于出分 */
    public static final String RULE_ENABLED = "ENABLED";
    /** 停用：不再用于新批次出分（历史版本与成绩仍保留） */
    public static final String RULE_DISABLED = "DISABLED";

    // ---------- 等级规则版本状态 ----------
    /** 草稿版本：可改，不能用于出分 */
    public static final String VERSION_DRAFT = "DRAFT";
    /** 生效中：同一规则同时只有一个生效版本 */
    public static final String VERSION_EFFECTIVE = "EFFECTIVE";
    /** 已归档：被新发布版本顶替，冻结留档，已落成绩仍指向它 */
    public static final String VERSION_ARCHIVED = "ARCHIVED";

    // ---------- 批次复核窗口状态 ----------
    public static final String WINDOW_NOT_STARTED = "NOT_STARTED";
    public static final String WINDOW_OPEN = "OPEN";
    public static final String WINDOW_CLOSED = "CLOSED";

    // ---------- 成绩状态 ----------
    /** 已出分（含首次落级） */
    public static final String SCORE_RELEASED = "RELEASED";
    /** 复核改分或重新出分后更新 */
    public static final String SCORE_UPDATED = "UPDATED";

    // ---------- 改分痕迹类型 ----------
    public static final String CHANGE_INITIAL = "INITIAL";
    public static final String CHANGE_REVIEW_CONFIRM = "REVIEW_CONFIRM";
    public static final String CHANGE_REVIEW_CHANGE = "REVIEW_CHANGE";
    public static final String CHANGE_RERUN = "RERUN";

    // ---------- 复核申请状态 ----------
    public static final String REVIEW_SUBMITTED = "SUBMITTED";
    public static final String REVIEW_PROCESSING = "PROCESSING";
    public static final String REVIEW_CONFIRMED = "CONFIRMED";
    public static final String REVIEW_CHANGED = "CHANGED";
    public static final String REVIEW_REJECTED = "REJECTED";

    // ---------- 通知类别 ----------
    public static final String NOTIFY_SCORE_RELEASED = "SCORE_RELEASED";
    public static final String NOTIFY_REVIEW_RECEIVED = "REVIEW_RECEIVED";
    public static final String NOTIFY_REVIEW_CONCLUDED = "REVIEW_CONCLUDED";
    public static final String NOTIFY_WINDOW_CLOSED = "WINDOW_CLOSED";

    /** 通知待外发；当前版本落库即可见，后续接短信 / 站内信时再推送 */
    public static final String NOTIFY_PENDING = "PENDING";
}
