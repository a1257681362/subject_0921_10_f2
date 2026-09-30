package com.evops.exam.controller;

import com.evops.common.ApiResponse;
import com.evops.exam.dto.ArrangeRequest;
import com.evops.exam.service.ArrangeService;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/arrange")
public class ArrangeController {

    private final ArrangeService arrangeService;

    public ArrangeController(ArrangeService arrangeService) {
        this.arrangeService = arrangeService;
    }

    /** 批量动作第一步：展示每人考场 / 座位 / 证号方案及容量缺口，不落库。 */
    @PostMapping("/preview")
    public ApiResponse<ActionPreview> preview(@Valid @RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.previewArrange(request));
    }

    /** 批量动作第二步：确认后生成准考证，证号 / 座位撞号整批拒收。 */
    @PostMapping
    public ApiResponse<ActionResult> arrange(@Valid @RequestBody ArrangeRequest request) {
        return ApiResponse.ok(arrangeService.arrange(request));
    }
}
