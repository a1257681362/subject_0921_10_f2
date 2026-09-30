package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.GradeRuleRequest;
import com.evops.grade.dto.GradeRuleVersionRequest;
import com.evops.grade.entity.GradeRule;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.model.GradeBand;
import com.evops.grade.service.GradeRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 等级规则模块与规则版本：规则状态 DRAFT/ENABLED/DISABLED，
 * 版本状态 DRAFT/EFFECTIVE/ARCHIVED；卷面分按版本分数段落等级。
 */
@RestController
@RequestMapping("/api/grade/rules")
public class GradeRuleController {

    private final GradeRuleService ruleService;

    public GradeRuleController(GradeRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ApiResponse<GradeRule> create(@Valid @RequestBody GradeRuleRequest request) {
        return ApiResponse.ok(ruleService.createRule(request));
    }

    @GetMapping
    public ApiResponse<List<GradeRule>> list(@RequestParam(required = false) String projectCode) {
        return ApiResponse.ok(ruleService.listRules(projectCode));
    }

    @GetMapping("/{id}")
    public ApiResponse<GradeRule> get(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.getRule(id));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<GradeRule> disable(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.disableRule(id));
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<GradeRule> enable(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.enableRule(id));
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本（分数段必须连续完整覆盖满分区间）。 */
    @PostMapping("/{id}/versions")
    public ApiResponse<GradeRuleVersion> createVersion(@PathVariable Long id,
                                                       @Valid @RequestBody GradeRuleVersionRequest request) {
        return ApiResponse.ok(ruleService.createVersion(id, request));
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<GradeRuleVersion>> listVersions(@PathVariable Long id) {
        return ApiResponse.ok(ruleService.listVersions(id));
    }

    @GetMapping("/versions/{versionId}")
    public ApiResponse<Map<String, Object>> getVersion(@PathVariable Long versionId) {
        GradeRuleVersion version = ruleService.requireVersion(versionId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", version);
        data.put("bands", ruleService.readBands(version.getBandsJson()));
        return ApiResponse.ok(data);
    }

    /** 发布：旧生效版本归档，本版本生效并冻结。 */
    @PostMapping("/versions/{versionId}/publish")
    public ApiResponse<GradeRuleVersion> publish(@PathVariable Long versionId) {
        return ApiResponse.ok(ruleService.publishVersion(versionId));
    }

    /** 丢弃草稿版本；已发布版本冻结不可删。 */
    @PostMapping("/versions/{versionId}/discard")
    public ApiResponse<Void> discard(@PathVariable Long versionId) {
        ruleService.discardDraft(versionId);
        return ApiResponse.ok(null);
    }

    /** 只读工具：给定版本与卷面分，返回命中的等级（两个岗对口径用）。 */
    @GetMapping("/versions/{versionId}/grade")
    public ApiResponse<GradeBand> grade(@PathVariable Long versionId, @RequestParam Integer rawScore) {
        return ApiResponse.ok(ruleService.grade(ruleService.requireVersion(versionId), rawScore));
    }
}
