package com.evops.grade.schedule;

import com.evops.grade.service.GradeBatchConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超窗自动关闭：每分钟扫描过期复核窗口并落 CLOSED、发通知。
 * 这只是兜底落账；真正的窗口判定走实时钟点（懒关闭），即使定时任务没扫到，超窗也提不了复核。
 */
@Component
public class ReviewWindowScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewWindowScheduler.class);

    private final GradeBatchConfigService configService;

    public ReviewWindowScheduler(GradeBatchConfigService configService) {
        this.configService = configService;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 10_000L)
    public void autoCloseExpiredWindows() {
        try {
            int closed = configService.autoCloseExpired();
            if (closed > 0) {
                log.info("复核窗口超窗自动关闭 {} 个批次", closed);
            }
        } catch (Exception e) {
            // 调度异常不能打断后续轮次
            log.error("复核窗口自动关闭扫描失败", e);
        }
    }
}
