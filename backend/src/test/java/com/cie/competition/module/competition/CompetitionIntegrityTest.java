package com.cie.competition.module.competition;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.competition.dto.CompetitionDTO;
import com.cie.competition.module.competition.entity.Competition;
import com.cie.competition.module.competition.mapper.CompetitionMapper;
import com.cie.competition.module.competition.service.impl.CompetitionServiceImpl;
import com.cie.competition.module.file.service.FileService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompetitionIntegrityTest {
    @Mock CompetitionMapper mapper;
    @Mock FileService files;
    @Mock com.cie.competition.module.message.service.MessageService messages;
    @Mock com.cie.competition.module.history.service.BusinessHistoryService history;
    @Mock com.cie.competition.module.competition.service.CompetitionCancellationService cancellation;
    @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
    @InjectMocks CompetitionServiceImpl service;
    @AfterEach void clear() { UserContext.clear(); }
    CompetitionDTO dto() {
        var dto = new CompetitionDTO(); dto.setCompetitionType("TEAM"); dto.setLevel("SCHOOL");
        dto.setMaxMembers(3); dto.setAllowCrossMajor(0);
        dto.setRegistrationStartTime(LocalDateTime.now().minusDays(1));
        dto.setRegistrationEndTime(LocalDateTime.now().plusDays(1)); return dto;
    }
    @Test void savingRequiresRegistrationWindow() {
        UserContext.set(new LoginUser(1L,"admin","ADMIN"));
        var dto=dto(); dto.setRegistrationStartTime(null);
        assertThrows(BusinessException.class, () -> service.create(dto));
        verify(mapper, never()).insert(any(Competition.class));
    }
    @Test void savingRequiresIncreasingWindow() {
        UserContext.set(new LoginUser(1L,"admin","ADMIN"));
        var dto=dto(); dto.setRegistrationEndTime(dto.getRegistrationStartTime());
        assertThrows(BusinessException.class, () -> service.create(dto));
    }
    @Test void cannotSkipStatus() {
        UserContext.set(new LoginUser(1L,"admin","ADMIN"));
        var c=new Competition(); c.setId(1L); c.setStatus("DRAFT");
        lenient().when(mapper.lockById(1L)).thenReturn(c);
        assertThrows(BusinessException.class, () -> service.updateStatus(1L,"ENDED"));
    }
    @Test void otherMajorAdminCannotDelete() {
        UserContext.set(new LoginUser(2L,"other","MAJOR_ADMIN"));
        var c=new Competition(); c.setId(1L); c.setPublisherId(1L);
        lenient().when(mapper.lockById(1L)).thenReturn(c);
        assertThrows(BusinessException.class, () -> service.delete(1L));
    }
    Competition managed(String status) {
        UserContext.set(new LoginUser(1L,"admin","ADMIN"));
        var c=new Competition(); c.setId(1L); c.setStatus(status); c.setPublisherId(1L);
        c.setCompetitionType("TEAM"); c.setAllowCrossMajor(0); c.setMaxMembers(3);
        when(mapper.lockById(1L)).thenReturn(c); return c;
    }
    @Test void registrationWindowIncludesBothEndpoints() {
        var c=managed("REGISTERING"); var now=LocalDateTime.of(2026,11,10,12,0);
        c.setRegistrationStartTime(now); c.setRegistrationEndTime(now.plusHours(1));
        try (var time=mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            time.when(LocalDateTime::now).thenReturn(now);
            assertDoesNotThrow(() -> service.requireRegistrationOpen(1L));
            time.when(LocalDateTime::now).thenReturn(c.getRegistrationEndTime());
            assertDoesNotThrow(() -> service.requireRegistrationOpen(1L));
            time.when(LocalDateTime::now).thenReturn(now.minusNanos(1));
            assertThrows(BusinessException.class, () -> service.requireRegistrationOpen(1L));
            time.when(LocalDateTime::now).thenReturn(c.getRegistrationEndTime().plusNanos(1));
            assertThrows(BusinessException.class, () -> service.requireRegistrationOpen(1L));
        }
    }
    @Test void registrationsPreventDelete() {
        managed("DRAFT"); when(mapper.countRegistrations(1L)).thenReturn(1L);
        assertThrows(BusinessException.class, () -> service.delete(1L));
        verify(mapper,never()).deleteById(anyLong());
        verify(mapper,never()).deleteCompetitionQuotas(anyLong());
    }
    @Test void deletingUnregisteredCompetitionRemovesQuotaFirst() {
        managed("DRAFT");
        service.delete(1L);
        var order = inOrder(mapper);
        order.verify(mapper).lockById(1L);
        order.verify(mapper).countRegistrations(1L);
        order.verify(mapper).deleteCompetitionQuotas(1L);
        order.verify(mapper).deleteById(1L);
    }
    @Test void registrationsProtectRules() {
        managed("DRAFT"); when(mapper.countRegistrations(1L)).thenReturn(1L);
        var dto=dto(); dto.setCompetitionType("PERSONAL");
        assertThrows(BusinessException.class, () -> service.update(1L,dto));
        dto.setCompetitionType("TEAM"); dto.setAllowCrossMajor(1);
        assertThrows(BusinessException.class, () -> service.update(1L,dto));
        dto.setAllowCrossMajor(0); dto.setMaxMembers(2);
        assertThrows(BusinessException.class, () -> service.update(1L,dto));
    }
    @Test void cancellationRequiresReasonAndNotifiesAllParticipants() {
        var c=managed("REGISTERING");
        assertThrows(BusinessException.class, () -> service.updateStatus(1L,"CANCELLED"," "));
        when(mapper.listParticipantIds(1L)).thenReturn(java.util.List.of(5L,6L));
        service.updateStatus(1L,"CANCELLED","天气原因");
        assertEquals("CANCELLED",c.getStatus()); assertEquals("天气原因",c.getCancellationReason());
        assertNotNull(c.getCancelledAt());
        verify(messages).sendBusinessMessage(eq(5L),anyString(),contains("天气原因"),eq("NOTICE"),eq("COMPETITION"),eq(1L));
        verify(messages).sendBusinessMessage(eq(6L),anyString(),contains("天气原因"),eq("NOTICE"),eq("COMPETITION"),eq(1L));
        verify(mapper,never()).deleteById(anyLong());
    }
    @Test void endedAndCancelledRejectGuidance() {
        var c=managed("ENDED");
        assertThrows(BusinessException.class, () -> service.requireGuidanceAssignable(1L));
        c.setStatus("CANCELLED");
        assertThrows(BusinessException.class, () -> service.requireGuidanceAssignable(1L));
    }
    @Test void publisherAndManagerCanManageButCannotAppointManager() {
        var c=managed("DRAFT"); UserContext.set(new LoginUser(1L,"publisher","MAJOR_ADMIN"));
        assertDoesNotThrow(() -> service.requireManageable(1L));
        c.setManagerId(2L); UserContext.set(new LoginUser(2L,"manager","MAJOR_ADMIN"));
        assertDoesNotThrow(() -> service.requireManageable(1L));
        var dto=dto(); dto.setManagerId(3L);
        assertThrows(BusinessException.class, () -> service.update(1L,dto));
    }
    @Test void appointedManagerMustBeActiveAdministrator() {
        managed("DRAFT"); var dto=dto(); dto.setManagerId(8L);
        assertThrows(BusinessException.class, () -> service.update(1L,dto));
    }
    @Test void statusesAdvanceOneStepAndNeverReturn() {
        var c=managed("DRAFT"); c.setRegistrationStartTime(LocalDateTime.now().minusHours(1)); c.setRegistrationEndTime(LocalDateTime.now().plusHours(1));
        c.setMajorId(2L);
        when(jdbc.queryForList(contains("SELECT id FROM sys_major"),eq(2L))).thenReturn(java.util.List.of(java.util.Map.of("id",2L)));
        when(mapper.countTeacherQuota(1L)).thenReturn(1L);
        service.updateStatus(1L,"REGISTERING");
        assertThrows(BusinessException.class, () -> service.updateStatus(1L,"DRAFT"));
        service.updateStatus(1L,"IN_PROGRESS"); service.updateStatus(1L,"ENDED");
        assertThrows(BusinessException.class, () -> service.updateStatus(1L,"REGISTERING"));
    }
}

