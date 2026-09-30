package com.evops.grade.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.grade.common.GradeConstants;
import com.evops.grade.entity.GradeNotification;
import com.evops.grade.mapper.GradeNotificationMapper;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.RegistrationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知落库：出分、受理、复核结论、窗口关闭统一从这里出，
 * 两个岗不再各发各的、口径不一致。当前无外发渠道，先落库可查；
 * 后续接短信 / 站内信时扫 status=PENDING 推送即可。
 */
@Service
public class GradeNotificationService {

    private final GradeNotificationMapper notificationMapper;
    private final RegistrationMapper registrationMapper;

    public GradeNotificationService(GradeNotificationMapper notificationMapper,
                                    RegistrationMapper registrationMapper) {
        this.notificationMapper = notificationMapper;
        this.registrationMapper = registrationMapper;
    }

    public GradeNotification notify(Long batchId, Long registrationId, Long reviewId, Long scoreRecordId,
                                    String category, String title, String content, String operator) {
        GradeNotification n = new GradeNotification();
        n.setBatchId(batchId);
        n.setRegistrationId(registrationId);
        n.setReviewId(reviewId);
        n.setScoreRecordId(scoreRecordId);
        n.setCategory(category);
        n.setTitle(title);
        n.setContent(content);
        n.setStatus(GradeConstants.NOTIFY_PENDING);
        n.setPhone(lookupPhone(registrationId));
        n.setOperator(operator);
        notificationMapper.insert(n);
        return n;
    }

    private String lookupPhone(Long registrationId) {
        if (registrationId == null) {
            return null;
        }
        Registration reg = registrationMapper.selectById(registrationId);
        return reg == null ? null : reg.getPhone();
    }

    public List<GradeNotification> listByReview(Long reviewId) {
        return notificationMapper.selectList(new QueryWrapper<GradeNotification>()
                .eq("review_id", reviewId)
                .orderByAsc("id"));
    }

    public List<GradeNotification> listByRegistration(Long registrationId) {
        return notificationMapper.selectList(new QueryWrapper<GradeNotification>()
                .eq("registration_id", registrationId)
                .orderByDesc("id"));
    }
}
