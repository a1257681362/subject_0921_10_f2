package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.ScoreReleaseRequest;
import com.evops.grade.entity.ScoreChangeLog;
import com.evops.grade.service.ScoreService;
import com.evops.grade.vo.ScoreView;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 出分落级：批量动作先 preview 再 execute；出分即开放复核窗口、发出分通知。
 */
@RestController
@RequestMapping("/api/grade/scores")
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    @PostMapping("/release/preview")
    public ApiResponse<ActionPreview> preview(@Valid @RequestBody ScoreReleaseRequest request) {
        return ApiResponse.ok(scoreService.previewRelease(request));
    }

    @PostMapping("/release")
    public ApiResponse<ActionResult> release(@Valid @RequestBody ScoreReleaseRequest request) {
        return ApiResponse.ok(scoreService.releaseScores(request));
    }

    @GetMapping
    public ApiResponse<List<ScoreView>> listByBatch(@RequestParam Long batchId) {
        return ApiResponse.ok(scoreService.listByBatch(batchId));
    }

    /** 学员 / 坐席按报名号查成绩与等级（含落级版本号）。 */
    @GetMapping("/by-reg-no")
    public ApiResponse<ScoreView> byRegNo(@RequestParam String regNo) {
        return ApiResponse.ok(scoreService.getByRegNo(regNo));
    }

    /** 改分前后痕迹：首次落级 / 重新出分 / 复核维持 / 复核改分都在。 */
    @GetMapping("/{id}/change-logs")
    public ApiResponse<List<ScoreChangeLog>> changeLogs(@PathVariable Long id) {
        return ApiResponse.ok(scoreService.listChangeLogs(id));
    }
}
