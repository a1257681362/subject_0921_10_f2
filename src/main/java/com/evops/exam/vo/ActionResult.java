package com.evops.exam.vo;

import lombok.Data;

import java.util.List;

/**
 * 批量动作执行结果：成功与跳过分开报，跳过附原因。
 */
@Data
public class ActionResult {
    private String action;
    private int affectedCount;
    private int skipCount;
    private String operator;
    private String executedAt;
    private List<String> affectedKeys;
    private List<String> skipped;
}
