package com.evops.grade.controller;

import com.evops.common.ApiResponse;
import com.evops.grade.dto.GradeBatchConfigRequest;
import com.evops.grade.entity.GradeBatchConfig;
import com.evops.grade.service.GradeBatchConfigService;
import com.evops.grade.vo.WindowStatusView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 批次复核窗口配置：窗口起止跟批次走，不写死。
 * 查窗口状态一律走实时口径 openNow，两个岗答案一致。
 */
@RestController
@RequestMapping("/api/grade/batches/{batchId}/window")
public class GradeBatchConfigController {

    private final GradeBatchConfigService configService;

    public GradeBatchConfigController(GradeBatchConfigService configService) {
        this.configService = configService;
    }

    /** 配置 / 修改窗口（出分前）：指定规则与起止时间。 */
    @PostMapping
    public ApiResponse<GradeBatchConfig> configure(@PathVariable Long batchId,
                                                   @Valid @RequestBody GradeBatchConfigRequest request) {
        return ApiResponse.ok(configService.configure(batchId, request));
    }

    /** 窗口实时状态（配置值 + 按当前钟点判定，过期顺手落关闭）。 */
    @GetMapping
    public ApiResponse<WindowStatusView> status(@PathVariable Long batchId) {
        return ApiResponse.ok(configService.statusView(batchId));
    }

    /** 人工提前关闭窗口。 */
    @PostMapping("/close")
    public ApiResponse<GradeBatchConfig> close(@PathVariable Long batchId,
                                               @RequestParam(required = false) String reason) {
        return ApiResponse.ok(configService.manualClose(batchId, reason));
    }

    /** 立即执行一次超窗扫描（平时由定时任务每分钟自动跑）。 */
    @PostMapping("/auto-close/run")
    public ApiResponse<Map<String, Object>> runAutoClose() {
        int closed = configService.autoCloseExpired();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("closedCount", closed);
        return ApiResponse.ok(data);
    }
}
