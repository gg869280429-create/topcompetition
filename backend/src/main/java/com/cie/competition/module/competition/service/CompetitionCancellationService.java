package com.cie.competition.module.competition.service;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.UserContext;
import com.cie.competition.module.guidance.service.GuidanceLifecycleService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompetitionCancellationService {
    private final JdbcTemplate jdbc;
    private final GuidanceLifecycleService guidance;
    private final BusinessHistoryService history;

    // 调用方持有竞赛行锁，与报名、指导和成果提交保持同一锁顺序。
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(Long competitionId, String reason) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE competition_id=? AND status='APPROVED'", Integer.class, competitionId) > 0)
            throw new BusinessException("存在有效获奖成果，请先处理成果撤销");
        for (var registration : jdbc.queryForList("SELECT id,status FROM registration WHERE competition_id=? ORDER BY id FOR UPDATE", competitionId)) {
            long id = ((Number) registration.get("id")).longValue();
            guidance.endActive(id, reason);
            if (!java.util.Set.of("CANCELLED", "WITHDRAWN").contains(registration.get("status"))) {
                jdbc.update("UPDATE registration SET status='CANCELLED' WHERE id=?", id);
                history.record("REGISTRATION", id, "COMPETITION_CANCELLED", registration, Map.of("status", "CANCELLED"), reason);
            }
            jdbc.update("UPDATE registration_member SET active_key=NULL WHERE registration_id=?", id);
            for (var invite : jdbc.queryForList("SELECT id,status FROM team_invitation WHERE registration_id=? AND status='PENDING' FOR UPDATE", id)) {
                long inviteId = ((Number) invite.get("id")).longValue();
                jdbc.update("UPDATE team_invitation SET status='REVOKED',responded_time=NOW() WHERE id=?", inviteId);
                history.record("INVITATION", inviteId, "COMPETITION_CANCELLED", invite, Map.of("status", "REVOKED"), reason);
            }
            for (var withdrawal : jdbc.queryForList("SELECT id,status FROM withdrawal_request WHERE registration_id=? AND status='PENDING' FOR UPDATE", id)) {
                long requestId = ((Number) withdrawal.get("id")).longValue();
                jdbc.update("UPDATE withdrawal_request SET status='CANCELLED',reviewer_id=?,review_time=NOW(),review_comment=? WHERE id=?", UserContext.getUserId(), reason, requestId);
                history.record("WITHDRAWAL", requestId, "COMPETITION_CANCELLED", withdrawal, Map.of("status", "CANCELLED"), reason);
            }
        }
        for (var award : jdbc.queryForList("SELECT id,status FROM award WHERE competition_id=? AND status IN ('DRAFT','PENDING','REJECTED') ORDER BY id FOR UPDATE", competitionId)) {
            long id = ((Number) award.get("id")).longValue();
            jdbc.update("UPDATE award SET status='CANCELLED' WHERE id=?", id);
            history.record("AWARD", id, "COMPETITION_CANCELLED", award, Map.of("status", "CANCELLED"), reason);
        }
    }
}
