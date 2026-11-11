package com.cie.competition.module.guidance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.cie.competition.module.guidance.entity.Guidance;
import com.cie.competition.module.guidance.mapper.GuidanceMapper;
import com.cie.competition.module.guidance.mapper.GuidanceQuotaMapper;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.cie.competition.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeSet;

@Service
@RequiredArgsConstructor
public class GuidanceLifecycleService {
    private final GuidanceMapper guidanceMapper;
    private final GuidanceQuotaMapper quotaMapper;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbc;
    private final BusinessHistoryService history;
    private final MessageService messages;

    // 调用方先锁竞赛、报名；名额由ACTIVE关系计数，结束关系即原子释放。
    @Transactional(propagation = Propagation.MANDATORY)
    public void endActive(Long registrationId, String reason) {
        var candidates = guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getRegistrationId, registrationId).eq(Guidance::getStatus, "ACTIVE"));
        if (candidates.isEmpty()) return;
        var teacherIds = candidates.stream().map(Guidance::getTeacherId).distinct().sorted().toList();
        for (Long teacherId : teacherIds) userMapper.lockById(teacherId);
        Long competitionId = candidates.get(0).getCompetitionId();
        for (Long teacherId : teacherIds) quotaMapper.lockQuota(competitionId, teacherId);
        var current = guidanceMapper.selectList(new LambdaQueryWrapper<Guidance>()
                .eq(Guidance::getRegistrationId, registrationId).eq(Guidance::getStatus, "ACTIVE").last("FOR UPDATE"));
        if (current.isEmpty()) return;
        var recipients = new TreeSet<>(jdbc.queryForList("SELECT user_id FROM registration_member WHERE registration_id=? AND current_member=1", Long.class, registrationId));
        LocalDateTime ended = LocalDateTime.now();
        for (var guidance : current) {
            var before = Map.of("status", guidance.getStatus(), "teacherId", guidance.getTeacherId());
            guidanceMapper.update(null, new LambdaUpdateWrapper<Guidance>().eq(Guidance::getId, guidance.getId())
                    .set(Guidance::getStatus, "ENDED").set(Guidance::getActiveKey, null)
                    .set(Guidance::getEndedTime, ended).set(Guidance::getEndReason, reason));
            history.record("GUIDANCE", guidance.getId(), "ENDED", before,
                    Map.of("status", "ENDED", "teacherId", guidance.getTeacherId(), "endedTime", ended), reason);
            recipients.add(guidance.getTeacherId());
        }
        for (Long receiver : recipients) messages.sendBusinessMessage(receiver, "指导关系结束", "团队指导关系已结束。原因：" + reason,
                "NOTICE", "REGISTRATION", registrationId);
    }
}
