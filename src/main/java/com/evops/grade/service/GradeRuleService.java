package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.dto.GradeRuleRequest;
import com.evops.grade.dto.GradeRuleVersionRequest;
import com.evops.grade.entity.GradeRule;
import com.evops.grade.entity.GradeRuleVersion;
import com.evops.grade.mapper.GradeRuleMapper;
import com.evops.grade.mapper.GradeRuleVersionMapper;
import com.evops.grade.model.GradeBand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 等级规则与版本：
 * - 卷面分按分数段落等级，分数段冻结在版本里（规则带版本）；
 * - 版本 DRAFT -> EFFECTIVE -> ARCHIVED，发布即冻结，同一规则同时只有一个生效版本；
 * - 已出成绩永久指向命中的版本，后续发新版不改历史等级。
 */
@Service
public class GradeRuleService {

    private final GradeRuleMapper ruleMapper;
    private final GradeRuleVersionMapper versionMapper;
    private final ObjectMapper objectMapper;

    public GradeRuleService(GradeRuleMapper ruleMapper,
                            GradeRuleVersionMapper versionMapper,
                            ObjectMapper objectMapper) {
        this.ruleMapper = ruleMapper;
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
    }

    // ---------- 规则模块 ----------

    @Transactional
    public GradeRule createRule(GradeRuleRequest request) {
        GradeRule rule = new GradeRule();
        rule.setCode(request.getCode().trim());
        rule.setName(request.getName().trim());
        rule.setProjectCode(request.getProjectCode() == null || request.getProjectCode().trim().isEmpty()
                ? GradeConstants.PROJECT_ASSIST : request.getProjectCode().trim().toUpperCase());
        rule.setStatus(GradeConstants.RULE_DRAFT);
        rule.setRemark(request.getRemark());
        try {
            ruleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("DUPLICATE_RULE_CODE", "等级规则编码已存在：" + rule.getCode());
        }
        return rule;
    }

    public List<GradeRule> listRules(String projectCode) {
        QueryWrapper<GradeRule> wrapper = new QueryWrapper<GradeRule>().orderByDesc("create_time");
        if (projectCode != null && !projectCode.trim().isEmpty()) {
            wrapper.eq("project_code", projectCode.trim().toUpperCase());
        }
        return ruleMapper.selectList(wrapper);
    }

    public GradeRule getRule(Long id) {
        GradeRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException("RULE_NOT_FOUND", "等级规则不存在：id=" + id);
        }
        return rule;
    }

    /** 停用：不再用于新批次；已出成绩与历史版本不受影响。停用后可重新启用。 */
    @Transactional
    public GradeRule disableRule(Long id) {
        GradeRule rule = getRule(id);
        rule.setStatus(GradeConstants.RULE_DISABLED);
        ruleMapper.updateById(rule);
        return rule;
    }

    @Transactional
    public GradeRule enableRule(Long id) {
        GradeRule rule = getRule(id);
        if (rule.getCurrentVersionId() == null) {
            throw new BusinessException("RULE_NO_VERSION", "规则尚未发布任何版本，不能启用：" + rule.getCode());
        }
        rule.setStatus(GradeConstants.RULE_ENABLED);
        ruleMapper.updateById(rule);
        return rule;
    }

    // ---------- 规则版本 ----------

    /** 新建草稿版本：分数段必须完整、连续、不重叠地覆盖满分区间，杜绝落级口径空档。 */
    @Transactional
    public GradeRuleVersion createVersion(Long ruleId, GradeRuleVersionRequest request) {
        GradeRule rule = getRule(ruleId);
        int min = request.getMinScore() == null ? 0 : request.getMinScore();
        int max = request.getMaxScore();
        List<GradeBand> bands = validateBands(min, max, request.getBands());

        GradeRuleVersion version = new GradeRuleVersion();
        version.setRuleId(rule.getId());
        version.setVersionNo(nextVersionNo(rule.getId()));
        version.setStatus(GradeConstants.VERSION_DRAFT);
        version.setMinScore(min);
        version.setMaxScore(max);
        version.setBandsJson(writeBands(bands));
        version.setRemark(request.getRemark());
        versionMapper.insert(version);
        return version;
    }

    /**
     * 发布版本：旧生效版本归档，新版本生效并冻结；规则转为启用。
     * 已按旧版本落级的成绩不动，只影响之后出分。
     */
    @Transactional
    public GradeRuleVersion publishVersion(Long versionId) {
        GradeRuleVersion version = requireVersion(versionId);
        if (!GradeConstants.VERSION_DRAFT.equals(version.getStatus())) {
            throw new BusinessException("VERSION_NOT_DRAFT",
                    "只有草稿版本可以发布，当前状态：" + version.getStatus());
        }
        GradeRule rule = getRule(version.getRuleId());
        if (GradeConstants.RULE_DISABLED.equals(rule.getStatus())) {
            throw new BusinessException("RULE_DISABLED", "规则已停用，请先重新启用再发布版本");
        }
        String operator = OperatorHolder.current();
        LocalDateTime now = LocalDateTime.now();

        List<GradeRuleVersion> effective = versionMapper.selectList(new QueryWrapper<GradeRuleVersion>()
                .eq("rule_id", rule.getId())
                .eq("status", GradeConstants.VERSION_EFFECTIVE));
        for (GradeRuleVersion old : effective) {
            old.setStatus(GradeConstants.VERSION_ARCHIVED);
            versionMapper.updateById(old);
        }

        version.setStatus(GradeConstants.VERSION_EFFECTIVE);
        version.setPublishedBy(operator);
        version.setPublishedAt(now);
        versionMapper.updateById(version);

        rule.setStatus(GradeConstants.RULE_ENABLED);
        rule.setCurrentVersionId(version.getId());
        ruleMapper.updateById(rule);
        return version;
    }

    /** 草稿版本可丢弃；生效 / 归档版本冻结，不允许删除。 */
    @Transactional
    public void discardDraft(Long versionId) {
        GradeRuleVersion version = requireVersion(versionId);
        if (!GradeConstants.VERSION_DRAFT.equals(version.getStatus())) {
            throw new BusinessException("VERSION_FROZEN",
                    "版本已发布即冻结，不能删除（状态：" + version.getStatus() + "）");
        }
        versionMapper.deleteById(versionId);
    }

    public List<GradeRuleVersion> listVersions(Long ruleId) {
        getRule(ruleId);
        return versionMapper.selectList(new QueryWrapper<GradeRuleVersion>()
                .eq("rule_id", ruleId)
                .orderByAsc("version_no"));
    }

    public GradeRuleVersion requireVersion(Long versionId) {
        GradeRuleVersion version = versionMapper.selectById(versionId);
        if (version == null) {
            throw new BusinessException("RULE_VERSION_NOT_FOUND", "规则版本不存在：id=" + versionId);
        }
        return version;
    }

    /** 取规则当前生效版本；没有生效版本则不能出分。 */
    public GradeRuleVersion requireEffectiveVersion(Long ruleId) {
        GradeRule rule = getRule(ruleId);
        Long currentId = rule.getCurrentVersionId();
        if (currentId == null) {
            throw new BusinessException("RULE_NO_EFFECTIVE_VERSION",
                    "规则尚无生效版本，请先发布版本再出分：" + rule.getCode());
        }
        GradeRuleVersion version = versionMapper.selectById(currentId);
        if (version == null || !GradeConstants.VERSION_EFFECTIVE.equals(version.getStatus())) {
            throw new BusinessException("RULE_NO_EFFECTIVE_VERSION",
                    "规则当前版本未生效，请重新发布：" + rule.getCode());
        }
        return version;
    }

    // ---------- 落级：卷面分 -> 等级 ----------

    /** 按版本分数段落级；落在区间空档（理论上被校验拦住）或超出满分范围都明确报错。 */
    public GradeBand grade(GradeRuleVersion version, int rawScore) {
        if (rawScore < version.getMinScore() || rawScore > version.getMaxScore()) {
            throw new BusinessException("SCORE_OUT_OF_RANGE", String.format(
                    "卷面分 %d 超出规则版本满分区间 [%d, %d]",
                    rawScore, version.getMinScore(), version.getMaxScore()));
        }
        for (GradeBand band : readBands(version.getBandsJson())) {
            if (band.contains(rawScore)) {
                return band;
            }
        }
        throw new BusinessException("GRADE_BAND_MISSING",
                "卷面分 " + rawScore + " 未命中任何分数段，请检查规则版本配置");
    }

    public List<GradeBand> readBands(String bandsJson) {
        try {
            return objectMapper.readValue(bandsJson, new TypeReference<List<GradeBand>>() {
            });
        } catch (Exception e) {
            throw new BusinessException("BANDS_JSON_BROKEN", "规则版本分数段内容无法解析：" + e.getMessage());
        }
    }

    private String writeBands(List<GradeBand> bands) {
        try {
            return objectMapper.writeValueAsString(bands);
        } catch (Exception e) {
            throw new BusinessException("BANDS_JSON_FAILED", "分数段序列化失败：" + e.getMessage());
        }
    }

    /**
     * 分数段校验：每段上下界合法、等级非空；段间按顺序首尾相接（上一段 max+1 = 下一段 min），
     * 且整段恰好覆盖 [minScore, maxScore]，不允许重叠、断档、留白。
     */
    private List<GradeBand> validateBands(int min, int max, List<GradeBand> input) {
        if (max < min) {
            throw new BusinessException("SCORE_RANGE_INVALID",
                    String.format("满分区间不合法：下限 %d 大于上限 %d", min, max));
        }
        List<GradeBand> bands = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            GradeBand b = input.get(i);
            if (b.getMinScore() == null || b.getMaxScore() == null) {
                throw new BusinessException("BAND_RANGE_REQUIRED", "第 " + (i + 1) + " 段分数上下界不能为空");
            }
            if (b.getMinScore() < min || b.getMaxScore() > max) {
                throw new BusinessException("BAND_OUT_OF_RANGE", String.format(
                        "第 %d 段 [%d,%d] 超出满分区间 [%d,%d]",
                        i + 1, b.getMinScore(), b.getMaxScore(), min, max));
            }
            if (b.getMinScore() > b.getMaxScore()) {
                throw new BusinessException("BAND_RANGE_REVERSED",
                        "第 " + (i + 1) + " 段下限大于上限：" + b.getMinScore() + " > " + b.getMaxScore());
            }
            if (b.getLevel() == null || b.getLevel().trim().isEmpty()) {
                throw new BusinessException("BAND_LEVEL_REQUIRED", "第 " + (i + 1) + " 段等级编码不能为空");
            }
            GradeBand normalized = new GradeBand(b.getMinScore(), b.getMaxScore(),
                    b.getLevel().trim().toUpperCase(),
                    b.getLabel() == null ? null : b.getLabel().trim());
            bands.add(normalized);
        }
        GradeBand first = bands.get(0);
        if (first.getMinScore() != min) {
            throw new BusinessException("BANDS_NOT_COVERED",
                    "分数段必须从满分区间下限 " + min + " 开始覆盖，当前起始 " + first.getMinScore());
        }
        for (int i = 1; i < bands.size(); i++) {
            GradeBand prev = bands.get(i - 1);
            GradeBand cur = bands.get(i);
            if (cur.getMinScore() <= prev.getMaxScore()) {
                throw new BusinessException("BANDS_OVERLAP", String.format(
                        "分数段重叠：[%d,%d] 与 [%d,%d]",
                        prev.getMinScore(), prev.getMaxScore(), cur.getMinScore(), cur.getMaxScore()));
            }
            if (cur.getMinScore() != prev.getMaxScore() + 1) {
                throw new BusinessException("BANDS_GAP", String.format(
                        "分数段断档：%d 与 %d 之间没有覆盖", prev.getMaxScore(), cur.getMinScore()));
            }
        }
        GradeBand last = bands.get(bands.size() - 1);
        if (last.getMaxScore() != max) {
            throw new BusinessException("BANDS_NOT_COVERED",
                    "分数段必须覆盖到满分区间上限 " + max + "，当前截止 " + last.getMaxScore());
        }
        return bands;
    }

    private int nextVersionNo(Long ruleId) {
        Integer max = versionMapper.selectObjs(new QueryWrapper<GradeRuleVersion>()
                .select("COALESCE(MAX(version_no),0) AS v")
                .eq("rule_id", ruleId))
                .stream().findFirst().map(o -> Integer.valueOf(o.toString())).orElse(0);
        return max + 1;
    }
}
