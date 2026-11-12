package com.cie.competition;
import com.cie.competition.common.security.*;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.registration.service.RegistrationWorkflowService;
import com.cie.competition.module.registration.entity.Registration;
import com.cie.competition.module.registration.mapper.RegistrationMapper;
import com.cie.competition.module.competition.service.CompetitionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@ExtendWith(MockitoExtension.class)
class RegistrationWorkflowTest {
 @Mock RegistrationMapper registrations;
 @Mock CompetitionService competitions;
 @Mock com.cie.competition.module.user.mapper.UserMapper users;
 @Mock com.cie.competition.module.registration.mapper.RegistrationMemberMapper members;
 @Mock com.cie.competition.module.registration.mapper.TeamInvitationMapper invitations;
 @Mock com.cie.competition.module.registration.mapper.WithdrawalRequestMapper withdrawals;
 @Mock com.cie.competition.module.history.service.BusinessHistoryService history;
 @Mock com.cie.competition.module.guidance.service.GuidanceLifecycleService guidance;
 @Mock com.cie.competition.module.message.service.MessageService messages;
 @Mock org.springframework.jdbc.core.JdbcTemplate jdbc;
 @InjectMocks RegistrationWorkflowService workflow;
 @BeforeEach void wireMemberValidation(){org.springframework.test.util.ReflectionTestUtils.setField(workflow,"memberValidator",new com.cie.competition.module.registration.service.RegistrationMemberValidator(competitions,users));}
 @AfterEach void clear(){UserContext.clear();}
 @Test void strangerCannotSubmitDraft(){
  UserContext.set(new LoginUser(2L,"s","STUDENT"));
  Registration r=new Registration();r.setId(1L);r.setCompetitionId(10L);r.setLeaderId(3L);r.setStatus("DRAFT");
  when(registrations.selectById(1L)).thenReturn(r);when(registrations.lockById(1L)).thenReturn(r);
  assertThrows(BusinessException.class,()->workflow.submit(1L));
 }
 @Test void approvedTeamCannotOrdinarilyResubmit(){
  UserContext.set(new LoginUser(3L,"s","STUDENT"));
  Registration r=new Registration();r.setId(1L);r.setCompetitionId(10L);r.setLeaderId(3L);r.setStatus("APPROVED");
  when(registrations.selectById(1L)).thenReturn(r);when(registrations.lockById(1L)).thenReturn(r);
  assertThrows(BusinessException.class,()->workflow.submit(1L));
 }
 @Test void administratorCannotSubmitStudentTeam(){
  UserContext.set(new LoginUser(3L,"s","ADMIN"));
  assertThrows(BusinessException.class,()->workflow.submit(1L));
  verifyNoInteractions(registrations);
 }

 @Test void disabledMemberCannotCreateDraft(){
  UserContext.set(new LoginUser(3L,"s","STUDENT"));
  var c=new com.cie.competition.module.competition.vo.CompetitionVO();c.setMaxMembers(4);c.setCompetitionType("TEAM");c.setAllowCrossMajor(1);
  when(competitions.getById(10L)).thenReturn(c);
  var leader=new com.cie.competition.module.user.entity.User();leader.setId(3L);leader.setRole("STUDENT");leader.setStatus(1);leader.setMajorId(100L);
  when(users.selectById(3L)).thenReturn(leader);
  var member=new com.cie.competition.module.user.entity.User();member.setId(4L);member.setRole("STUDENT");member.setStatus(0);member.setMajorId(100L);
  when(users.selectById(4L)).thenReturn(member);
  var dto=new com.cie.competition.module.registration.dto.RegistrationDTO();dto.setCompetitionId(10L);dto.setMemberIds(java.util.List.of(4L));
  assertThrows(BusinessException.class,()->workflow.create(dto));
  verify(registrations,never()).insert(any(Registration.class));
 }
 @Test void unconfirmedMembersBlockSubmission(){
  UserContext.set(new LoginUser(3L,"s","STUDENT"));
  Registration r=new Registration();r.setId(1L);r.setCompetitionId(10L);r.setLeaderId(3L);r.setStatus("DRAFT");
  when(registrations.selectById(1L)).thenReturn(r);when(registrations.lockById(1L)).thenReturn(r);
  var c=new com.cie.competition.module.competition.vo.CompetitionVO();c.setMaxMembers(4);c.setCompetitionType("TEAM");c.setAllowCrossMajor(1);when(competitions.getById(10L)).thenReturn(c);
  var records=new java.util.ArrayList<com.cie.competition.module.registration.entity.RegistrationMember>();
  for(long id:new long[]{3,4}){var m=new com.cie.competition.module.registration.entity.RegistrationMember();m.setUserId(id);records.add(m);var u=new com.cie.competition.module.user.entity.User();u.setId(id);u.setRole("STUDENT");u.setStatus(1);u.setMajorId(100L);when(users.selectById(id)).thenReturn(u);}
  when(members.selectList(any())).thenReturn(records);
  assertThrows(BusinessException.class,()->workflow.submit(1L));
  verify(registrations,never()).updateById(any(Registration.class));
 }

 @Test void nullWithdrawalDecisionIsBusinessError(){
  UserContext.set(new LoginUser(3L,"s","ADMIN"));
  var request=new com.cie.competition.module.registration.entity.WithdrawalRequest();request.setId(1L);request.setRegistrationId(2L);request.setStatus("PENDING");
  when(withdrawals.selectById(1L)).thenReturn(request);
  var r=new Registration();r.setId(2L);r.setCompetitionId(10L);r.setStatus("APPROVED");
  when(registrations.selectById(2L)).thenReturn(r);when(registrations.lockById(2L)).thenReturn(r);
  assertThrows(BusinessException.class,()->workflow.reviewWithdrawal(1L,null,null));
 }
 @Test void excessiveApprovalCommentIsRejectedBeforeWriting(){
  UserContext.set(new LoginUser(3L,"s","ADMIN"));
  var request=new com.cie.competition.module.registration.entity.WithdrawalRequest();request.setId(1L);request.setRegistrationId(2L);request.setStatus("PENDING");request.setReason("退出");
  when(withdrawals.selectById(1L)).thenReturn(request);
  var r=new Registration();r.setId(2L);r.setCompetitionId(10L);r.setStatus("APPROVED");
  when(registrations.selectById(2L)).thenReturn(r);when(registrations.lockById(2L)).thenReturn(r);
  lenient().when(jdbc.queryForObject(anyString(),eq(Long.class),eq(2L))).thenReturn(0L);
  assertThrows(BusinessException.class,()->workflow.reviewWithdrawal(1L,"APPROVED","字".repeat(1001)));
  verify(withdrawals,never()).updateById(any(com.cie.competition.module.registration.entity.WithdrawalRequest.class));
 }
}
