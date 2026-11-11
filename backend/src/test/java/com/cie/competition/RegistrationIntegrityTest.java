package com.cie.competition;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.guidance.mapper.GuidanceMapper;
import com.cie.competition.module.registration.dto.*;
import com.cie.competition.module.registration.entity.*;
import com.cie.competition.module.registration.mapper.*;
import com.cie.competition.module.registration.service.impl.RegistrationServiceImpl;
import com.cie.competition.module.user.service.UserService;
import com.cie.competition.module.user.vo.UserInfoVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationIntegrityTest {
    @Mock RegistrationMapper registrations;
    @Mock RegistrationMemberMapper members;
    @Mock CompetitionService competitions;
    @Mock UserService users;
    @Mock GuidanceMapper guidance;
    @Mock com.cie.competition.module.registration.service.RegistrationWorkflowService workflow;
    @InjectMocks RegistrationServiceImpl service;

    @BeforeAll static void initializeTableMetadata() {
        var builder = new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(), "registration-unit-test");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(builder, Registration.class);
    }
    @BeforeEach void setup() {
        UserContext.set(new LoginUser(1L, "student", "STUDENT"));
    }
    @AfterEach void clear() { UserContext.clear(); }
    @ParameterizedTest @ValueSource(strings = {"TEACHER", "STUDENT", "UNKNOWN"})
    void onlyAdministratorsMayReview(String role) {
        UserContext.set(new LoginUser(1L, "user", role));
        Registration r = new Registration(); r.setId(20L); r.setCompetitionId(10L); r.setStatus("PENDING");
        lenient().when(registrations.lockById(20L)).thenReturn(r);
        lenient().when(registrations.selectById(20L)).thenReturn(r);
        ReviewDTO review = new ReviewDTO(); review.setResult("APPROVED");
        assertThrows(BusinessException.class, () -> service.review(20L, review));
    }
    @Test void rejectsUnrelatedTeacherRead() {
        UserContext.set(new LoginUser(9L, "teacher", "TEACHER"));
        Registration r = new Registration(); r.setId(20L); r.setCompetitionId(10L); r.setLeaderId(1L);
        when(registrations.selectById(20L)).thenReturn(r);
        assertThrows(BusinessException.class, () -> service.getById(20L));
    }

    @Test void onlyAdministratorsMayPageAll() {
        assertThrows(BusinessException.class, () -> service.page(new RegistrationQueryDTO()));
    }
    @Test void rejectsUnrelatedTeacherBatchRead() {
        UserContext.set(new LoginUser(9L, "teacher", "TEACHER"));
        Registration r = new Registration(); r.setId(20L); r.setCompetitionId(10L); r.setLeaderId(1L);
        when(registrations.selectBatchIds(anyCollection())).thenReturn(List.of(r));
        assertThrows(BusinessException.class, () -> service.listByIds(List.of(20L)));
    }
    Registration existingRegistration(String status) {
        Registration r = new Registration(); r.setId(20L); r.setCompetitionId(10L); r.setLeaderId(1L);
        r.setMajorId(100L); r.setStatus(status);
        when(registrations.selectById(20L)).thenReturn(r);
        lenient().when(registrations.lockById(20L)).thenReturn(r);
        return r;
    }

    @Test void allowsTeacherWithActiveGuidance() {
        existingRegistration("APPROVED");
        UserContext.set(new LoginUser(9L, "teacher", "TEACHER"));
        when(guidance.selectCount(any())).thenReturn(1L);
        assertEquals(20L, service.getById(20L).getId());
    }
    @Test void allowsStudentMemberRead() {
        existingRegistration("PENDING");
        when(members.selectCount(any())).thenReturn(1L);
        assertEquals(20L, service.getById(20L).getId());
    }
    @Test void rejectsUnrelatedStudentRead() {
        existingRegistration("PENDING");
        assertThrows(BusinessException.class, () -> service.getById(20L));
    }
    @Test void rejectsForeignMajorAdministratorRead() {
        existingRegistration("PENDING");
        UserContext.set(new LoginUser(5L, "admin", "MAJOR_ADMIN"));
        when(users.getById(5L)).thenReturn(new UserInfoVO(5L, "a", "a", "MAJOR_ADMIN", 200L));
        assertThrows(BusinessException.class, () -> service.getById(20L));
    }
    @Test void rejectsForeignMajorAdministratorReview() {
        existingRegistration("PENDING");
        UserContext.set(new LoginUser(5L, "admin", "MAJOR_ADMIN"));
        when(users.getById(5L)).thenReturn(new UserInfoVO(5L, "a", "a", "MAJOR_ADMIN", 200L));
        ReviewDTO review = new ReviewDTO(); review.setResult("APPROVED");
        assertThrows(BusinessException.class, () -> service.review(20L, review));
    }
    @Test void allowsReviewAfterRegistrationDeadline() {
        Registration r = existingRegistration("PENDING");
        UserContext.set(new LoginUser(5L, "admin", "MAJOR_ADMIN"));
        when(users.getById(5L)).thenReturn(new UserInfoVO(5L, "a", "a", "MAJOR_ADMIN", 100L));
        ReviewDTO review = new ReviewDTO(); review.setResult("APPROVED");
        service.review(20L, review);
        assertEquals("APPROVED", r.getStatus());
        verify(competitions, never()).requireRegistrationOpen(anyLong());
        verify(competitions, never()).requireManageable(anyLong());
    }
    @Test void assignmentLocksCompetitionBeforeRegistration() {
        existingRegistration("APPROVED");
        UserContext.set(new LoginUser(5L, "admin", "ADMIN"));
        service.lockForAssignment(20L);
        var order = inOrder(competitions, registrations);
        order.verify(competitions).lockById(10L);
        order.verify(registrations).lockById(20L);
    }

    @Test void serviceRequiresRejectionReason() {
        UserContext.set(new LoginUser(1L, "admin", "ADMIN"));
        Registration r = new Registration(); r.setId(20L); r.setCompetitionId(10L); r.setStatus("PENDING");
        lenient().when(registrations.lockById(20L)).thenReturn(r);
        lenient().when(registrations.selectById(20L)).thenReturn(r);
        ReviewDTO review = new ReviewDTO(); review.setResult("REJECTED"); review.setComment(" ");
        assertThrows(BusinessException.class, () -> service.review(20L, review));
    }
}
