package com.evops.grade.model;

import lombok.Data;

/**
 * 分数段：[minScore, maxScore] 闭区间映射到一个等级。
 * 卷面分落级时按规则版本里的分数段顺序匹配，命中即定级。
 */
@Data
public class GradeBand {
    /** 分数下限（含） */
    private Integer minScore;
    /** 分数上限（含） */
    private Integer maxScore;
    /** 等级编码，如 A / B / C / D */
    private String level;
    /** 等级名称，如 优秀 / 合格 */
    private String label;

    public GradeBand() {
    }

    public GradeBand(Integer minScore, Integer maxScore, String level, String label) {
        this.minScore = minScore;
        this.maxScore = maxScore;
        this.level = level;
        this.label = label;
    }

    public boolean contains(int score) {
        return score >= minScore && score <= maxScore;
    }
}
