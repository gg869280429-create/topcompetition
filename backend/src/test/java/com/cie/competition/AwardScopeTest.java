package com.cie.competition;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.award.dto.*;
import com.cie.competition.module.award.entity.Award;
import com.cie.competition.module.award.mapper.AwardMapper;
import com.cie.competition.module.award.service.impl.AwardServiceImpl;
import com.cie.competition.module.competition.service.CompetitionService;
import com.cie.competition.module.registration.service.RegistrationService;
import com.cie.competition.module.registration.vo.RegistrationVO;
import com.cie.competition.module.file.service.FileService;
import com.cie.competition.module.history.service.BusinessHistoryService;
import com.cie.competition.module.message.service.MessageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AwardScopeTest {
    AwardMapper mapper; RegistrationService registrations; CompetitionService competitions;
    FileService files; BusinessHistoryService history; MessageService messages; JdbcTemplate jdbc;
    AwardServiceImpl service; Award award; RegistrationVO registration;
    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Award.class);
        mapper=mock(AwardMapper.class); registrations=mock(RegistrationService.class); competitions=mock(CompetitionService.class);
        files=mock(FileService.class); history=mock(BusinessHistoryService.class); messages=mock(MessageService.class); jdbc=mock(JdbcTemplate.class);
        service=new AwardServiceImpl(mapper,registrations,competitions,files,history,messages,jdbc,new ObjectMapper());
        UserContext.set(new LoginUser(17L,"test","ADMIN"));
        award=new Award(); award.setId(1L); award.setRegistrationId(8L); award.setCompetitionId(2L);
        award.setMajorId(3L); award.setStatus("PENDING"); award.setCertificateFileId(4L);
        registration=new RegistrationVO(); registration.setId(8L); registration.setCompetitionId(2L); registration.setStatus("APPROVED");
        lenient().when(mapper.selectById(1L)).thenReturn(award); lenient().when(mapper.lockById(1L)).thenReturn(award);
        lenient().when(mapper.selectMajorId(17L)).thenReturn(3L);
        lenient().when(registrations.getById(8L)).thenReturn(registration);
        lenient().when(registrations.lockForAssignment(8L)).thenReturn(registration);
        lenient().when(jdbc.queryForObject(contains("SELECT status FROM registration"),eq(String.class),eq(8L))).thenReturn("APPROVED");
    }
    @AfterEach void clear(){UserContext.clear();}
    AwardDTO dto(){ AwardDTO d=new AwardDTO(); d.setRegistrationId(8L); d.setAwardLevel("FIRST");d.setCertificateFileId(4L);d.setAwardTime(LocalDateTime.now());return d; }
    @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER","UNKNOWN"})
    void onlyManagersWrite(String role){UserContext.set(new LoginUser(17L,"test",role));assertThrows(BusinessException.class,()->service.create(dto()));verify(mapper,never()).insert(any(Award.class));}
    @Test void dateRequired(){AwardDTO d=dto();d.setAwardTime(null);assertThrows(BusinessException.class,()->service.create(d));}
    @Test void certificateRequired(){AwardDTO d=dto();d.setCertificateFileId(null);assertThrows(BusinessException.class,()->service.create(d));}
    @ParameterizedTest @ValueSource(strings={"PENDING","REJECTED","WITHDRAWN"})
    void invalidRegistrationCannotCreate(String status){registration.setStatus(status);assertThrows(BusinessException.class,()->service.create(dto()));}
    @Test void rejectedEditResubmitsAndClearsReview(){award.setStatus("REJECTED");award.setReviewerId(30L);award.setReviewComment("旧原因");award.setReviewTime(LocalDateTime.now());service.update(1L,dto());assertEquals("PENDING",award.getStatus());assertNull(award.getReviewerId());assertNull(award.getReviewTime());assertNull(award.getReviewComment());verify(history).record(eq("AWARD"),eq(1L),eq("RESUBMIT"),any(),any(),isNull());}
    @Test void mutationsLockCompetitionThenRegistrationThenAward(){service.update(1L,dto());var order=inOrder(competitions,jdbc,mapper);order.verify(competitions).lockById(2L);order.verify(jdbc).queryForObject(contains("SELECT status FROM registration"),eq(String.class),eq(8L));order.verify(mapper).lockById(1L);}
    @Test void approvedCannotEdit(){award.setStatus("APPROVED");assertThrows(BusinessException.class,()->service.update(1L,dto()));}
    @Test void allPhysicalDeletionForbidden(){assertThrows(BusinessException.class,()->service.delete(1L));verify(mapper,never()).deleteById(1L);}
    @Test void rejectRequiresReason(){AwardReviewDTO d=new AwardReviewDTO();d.setResult("REJECTED");d.setComment(" ");assertThrows(BusinessException.class,()->service.review(1L,d));}
    @Test void correctionReturnsApprovedToPending(){award.setStatus("APPROVED");AwardDTO d=dto();d.setReason("奖项更正");service.correct(1L,d);assertEquals("PENDING",award.getStatus());verify(history).record(eq("AWARD"),eq(1L),eq("CORRECT"),any(),any(),eq("奖项更正"));}
    @Test void revokedRecordRetained(){service.revoke(1L,"登记取消");assertEquals("REVOKED",award.getStatus());assertEquals("登记取消",award.getRevocationReason());verify(mapper,never()).deleteById(1L);}
    @Test void revokeRequiresReason(){assertThrows(BusinessException.class,()->service.revoke(1L," "));}
    @Test void majorScopeUsesStoredMajor(){UserContext.set(new LoginUser(17L,"test","MAJOR_ADMIN"));award.setMajorId(99L);assertThrows(BusinessException.class,()->service.getById(1L));}
    @Test void replacingCertificateValidatesNewOwner(){AwardDTO d=dto();d.setCertificateFileId(9L);service.update(1L,d);verify(files).validateAwardCertificate(9L);}
    @Test void unchangedCertificateAllowsAnotherManager(){service.update(1L,dto());verify(files,never()).validateAwardCertificate(any());verify(files).get(4L,"AWARD_CERTIFICATE");}

    @ParameterizedTest @ValueSource(strings={"MAJOR_ADMIN","STUDENT","TEACHER"})
    void paginationScopeRemainsMandatory(String role) {
        UserContext.set(new LoginUser(17L,"test",role));
        when(mapper.selectPage(any(),any())).thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Award>());
        service.page(new AwardQueryDTO());
        var captor=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(mapper).selectPage(any(),captor.capture());String sql=captor.getValue().getSqlSegment();
        if(role.equals("MAJOR_ADMIN"))assertTrue(sql.contains("major_id ="),sql);
        else if(role.equals("TEACHER")){assertTrue(sql.contains("registration_id IN"),sql);assertTrue(captor.getValue().getParamNameValuePairs().containsValue(17L),sql);assertTrue(sql.contains("award_teacher"),sql);}
        if(role.equals("TEACHER"))assertTrue(sql.contains("ACTIVE"),sql);
        if(role.equals("STUDENT"))assertTrue(sql.contains("JSON_CONTAINS"),sql);
    }
    @ParameterizedTest @ValueSource(strings={"STUDENT","TEACHER"})
    void detailMustAuthorizeRelatedRegistration(String role) {
        UserContext.set(new LoginUser(17L,"test",role));
        when(registrations.getById(8L)).thenThrow(new BusinessException(403,"无权"));
        assertThrows(BusinessException.class,()->service.getById(1L));
    }
    @ParameterizedTest @ValueSource(strings={"update","review","revoke"})
    void majorCannotMutateAnotherHistoricalMajor(String action) {
        UserContext.set(new LoginUser(17L,"test","MAJOR_ADMIN"));award.setMajorId(99L);
        assertThrows(BusinessException.class,()->{if(action.equals("update"))service.update(1L,dto());else if(action.equals("revoke"))service.revoke(1L,"原因");else {AwardReviewDTO d=new AwardReviewDTO();d.setResult("APPROVED");service.review(1L,d);}});
        verify(competitions,never()).lockById(any());
    }
    @Test void absentRoleIsForbidden(){UserContext.set(new LoginUser(17L,"test",null));assertThrows(BusinessException.class,()->service.page(new AwardQueryDTO()));}
    @Test void unconfiguredMajorIsForbidden(){UserContext.set(new LoginUser(17L,"test","MAJOR_ADMIN"));when(mapper.selectMajorId(17L)).thenReturn(null);assertThrows(BusinessException.class,()->service.page(new AwardQueryDTO()));}
    @Test void duplicateCannotCreateEvenAfterRevocation(){when(mapper.selectCount(any())).thenReturn(1L);assertThrows(BusinessException.class,()->service.create(dto()));verify(mapper,never()).insert(any(Award.class));}
    @Test void withdrawnCurrentRowWinsOverEarlierRead(){RegistrationVO withdrawn=new RegistrationVO();withdrawn.setStatus("WITHDRAWN");when(jdbc.queryForObject(contains("SELECT status FROM registration"),eq(String.class),eq(8L))).thenReturn("WITHDRAWN");assertThrows(BusinessException.class,()->service.update(1L,dto()));verify(mapper,never()).update(any(Award.class),any());}
    @Test void approvedCorrectionRequiresReason(){award.setStatus("APPROVED");assertThrows(BusinessException.class,()->service.correct(1L,dto()));}
    @Test void revokedAwardCannotBeResubmitted(){award.setStatus("REVOKED");assertThrows(BusinessException.class,()->service.update(1L,dto()));}
    @Test void cannotRebindAward(){AwardDTO d=dto();d.setRegistrationId(99L);assertThrows(BusinessException.class,()->service.update(1L,d));}

    @Test void successfulRegistrationStoresAuditAndNotifiesParticipants() {
        registration.setMajorId(3L); registration.setTeamName("登记团队");
        doAnswer(call->{Award saved=call.getArgument(0);saved.setId(1L);return 1;}).when(mapper).insert(any(Award.class));
        when(jdbc.queryForList(contains("SELECT user_id AS userId"),eq(8L))).thenReturn(java.util.List.of(java.util.Map.of("userId",21L),java.util.Map.of("userId",22L)));
        when(jdbc.queryForList(contains("SELECT teacher_id"),eq(Long.class),eq(8L))).thenReturn(java.util.List.of(30L));
        assertEquals(1L,service.create(dto()));
        var captured=org.mockito.ArgumentCaptor.forClass(Award.class);verify(mapper).insert(captured.capture());
        assertEquals(17L,captured.getValue().getRegistrantId());assertEquals(3L,captured.getValue().getMajorId());assertEquals("登记团队",captured.getValue().getTeamNameSnapshot());
        verify(history).record(eq("AWARD"),eq(1L),eq("CREATE"),isNull(),any(),isNull());
        for(long id:new long[]{21,22,30})verify(messages).sendBusinessMessage(eq(id),anyString(),anyString(),eq("SYSTEM"),eq("AWARD"),eq(1L));
    }
    @Test void historyRequiresSameScopeAsDetail() {
        UserContext.set(new LoginUser(17L,"test","MAJOR_ADMIN"));award.setMajorId(99L);
        assertThrows(BusinessException.class,()->service.history(1L));verifyNoInteractions(history);
    }
}
