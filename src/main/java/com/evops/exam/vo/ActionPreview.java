package com.evops.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量动作预检：先展示影响范围，确认后再 execute。
 */
@Data
public class ActionPreview {
    private String action;
    private int totalScanned;
    private int actionableCount;
    private int skipCount;
    private String summary;
    private List<AffectedItem> items;
}
