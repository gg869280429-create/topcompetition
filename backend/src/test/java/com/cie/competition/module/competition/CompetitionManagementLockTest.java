package com.cie.competition.module.competition;

import com.cie.competition.common.result.PageResult;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.dto.CompetitionTeacherDTO;
import com.cie.competition.module.competition.service.CompetitionManagementService;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.guidance.service.GuidanceService;
import com.cie.competition.module.guidance.vo.GuidanceQuotaVO;
import com.cie.competition.module.user.entity.User;
import com.cie.competition.module.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetitionManagementLockTest {
    @Mock CompetitionService competitions;
    @Mock GuidanceService guidance;
    @Mock UserMapper users;
    @InjectMocks CompetitionManagementService service;

    @Test void locksAllOldAndNewTeachersBeforeChangingAnyQuota() {
        var dto = new CompetitionDTO();
        var teacher3 = new CompetitionTeacherDTO(); teacher3.setTeacherId(3L); teacher3.setQuota(5);
        var teacher2 = new CompetitionTeacherDTO(); teacher2.setTeacherId(2L); teacher2.setQuota(5);
        dto.setTeachers(List.of(teacher3, teacher2));
        var old3 = new GuidanceQuotaVO(); old3.setId(30L); old3.setTeacherId(3L);
        var old1 = new GuidanceQuotaVO(); old1.setId(10L); old1.setTeacherId(1L);
        when(guidance.quotaPage(9L, 1, Integer.MAX_VALUE))
                .thenReturn(new PageResult<>(List.of(old3, old1), 2, 1, Integer.MAX_VALUE));

        service.save(9L, dto);

        var order = inOrder(competitions, guidance, users);
        order.verify(competitions).requireManageable(9L);
        order.verify(competitions).update(9L, dto);
        order.verify(guidance).quotaPage(9L, 1, Integer.MAX_VALUE);
        order.verify(users).lockById(1L);
        order.verify(users).lockById(2L);
        order.verify(users).lockById(3L);
        order.verify(guidance).deleteQuota(10L);
        order.verify(guidance).saveQuota(argThat(q -> q.getTeacherId().equals(2L) && q.getCompetitionId().equals(9L)));
        order.verify(guidance).saveQuota(argThat(q -> q.getTeacherId().equals(3L) && q.getCompetitionId().equals(9L)));
        order.verifyNoMoreInteractions();
        verify(users, times(1)).lockById(3L);
    }

    @Test void locksNewCompetitionBeforeReadingOrChangingQuotas() {
        var dto = new CompetitionDTO();
        var teacher = new CompetitionTeacherDTO(); teacher.setTeacherId(2L); teacher.setQuota(5);
        dto.setTeachers(List.of(teacher));
        when(competitions.create(dto)).thenReturn(9L);
        when(guidance.quotaPage(9L, 1, Integer.MAX_VALUE))
                .thenReturn(new PageResult<>(List.of(), 0, 1, Integer.MAX_VALUE));

        service.save(null, dto);

        var order = inOrder(competitions, guidance, users);
        order.verify(competitions).create(dto);
        order.verify(competitions).requireManageable(9L);
        order.verify(guidance).quotaPage(9L, 1, Integer.MAX_VALUE);
        order.verify(users).lockById(2L);
        order.verify(guidance).saveQuota(argThat(q -> q.getTeacherId().equals(2L)));
    }

    @Test void removalLocksDisabledOldTeacherWithoutRequiringActiveStatus() {
        var dto = new CompetitionDTO();
        var teacher = new CompetitionTeacherDTO(); teacher.setTeacherId(2L); teacher.setQuota(5);
        dto.setTeachers(List.of(teacher));
        var old = new GuidanceQuotaVO(); old.setId(10L); old.setTeacherId(1L);
        var disabled = new User(); disabled.setId(1L); disabled.setRole("TEACHER"); disabled.setStatus(0);
        when(users.lockById(1L)).thenReturn(disabled);
        when(guidance.quotaPage(9L, 1, Integer.MAX_VALUE))
                .thenReturn(new PageResult<>(List.of(old), 1, 1, Integer.MAX_VALUE));

        service.save(9L, dto);

        var order = inOrder(users, guidance);
        order.verify(users).lockById(1L);
        order.verify(users).lockById(2L);
        order.verify(guidance).deleteQuota(10L);
        order.verify(guidance).saveQuota(argThat(q -> q.getTeacherId().equals(2L)));
    }
}
