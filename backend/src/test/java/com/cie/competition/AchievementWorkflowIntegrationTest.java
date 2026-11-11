package com.cie.competition;
import com.cie.competition.common.security.*;
import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.module.award.dto.*;
import com.cie.competition.module.award.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.main.banner-mode=off","logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class AchievementWorkflowIntegrationTest extends IsolatedDatabaseTest {
 @Autowired JdbcTemplate jdbc;
 @Autowired AchievementWorkflowService workflow;
 @Autowired AwardService awards;
 @Autowired com.cie.competition.module.statistics.service.StatisticsService statistics;
 Long teacher,coTeacher,student,otherStudent,admin,registration;
 @BeforeEach void setup(){String key=UUID.randomUUID().toString();jdbc.update("INSERT INTO sys_major(name) VALUES(?)",key);Long major=jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,key);teacher=user(key+"t","TEACHER",major);coTeacher=user(key+"c","TEACHER",major);student=user(key+"s","STUDENT",major);otherStudent=user(key+"o","STUDENT",major);admin=user(key+"a","ADMIN",major);jdbc.update("INSERT INTO competition(title,status,publisher_id) VALUES(?,'ENDED',?)",key,admin);Long competition=jdbc.queryForObject("SELECT id FROM competition WHERE title=?",Long.class,key);jdbc.update("INSERT INTO registration(competition_id,team_name,leader_id,major_id,status,leader_name_snapshot) VALUES(?,?,?,?,'APPROVED','captain')",competition,key,student,major);registration=jdbc.queryForObject("SELECT id FROM registration WHERE team_name=?",Long.class,key);for(Long s:List.of(student,otherStudent))jdbc.update("INSERT INTO registration_member(registration_id,competition_id,user_id,current_member,name_snapshot,member_role) VALUES(?,?,?,1,?,'MEMBER')",registration,competition,s,"Student "+s);for(Long t:List.of(teacher,coTeacher))jdbc.update("INSERT INTO guidance(registration_id,competition_id,teacher_id,status,active_key) VALUES(?,?,?,'COMPLETED',NULL)",registration,competition,t);as(teacher,"TEACHER");}
 Long user(String n,String r,Long m){jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES(?,'unusable',?,?,?,1)",n,n,r,m);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,n);}
 void as(Long id,String role){UserContext.set(new LoginUser(id,"integration",role));}
 Long file(String type){String storage=UUID.randomUUID().toString();jdbc.update("INSERT INTO sys_file(original_name,storage_name,url,size,biz_type,uploader_id) VALUES('proof',?,'private/test',10,?,?)",storage,type,teacher);return jdbc.queryForObject("SELECT id FROM sys_file WHERE storage_name=?",Long.class,storage);}
 AwardDTO dto(boolean draft){AwardDTO d=new AwardDTO();d.setRegistrationId(registration);d.setAchievementName("Robotics winner");d.setAwardLevel("FIRST");d.setAwardTime(java.time.LocalDateTime.now().minusDays(1));d.setStudentIds(List.of(student));d.setTeacherIds(List.of(teacher,coTeacher));d.setDraft(draft);return d;}
 AwardDTO complete(){AwardDTO d=dto(false);d.setCertificateFileId(file("AWARD_CERTIFICATE"));d.setRegistrationScreenshotFileId(file("AWARD_REGISTRATION_SCREENSHOT"));d.setProcessPhotoFileIds(List.of(file("AWARD_PROCESS_PHOTO"),file("AWARD_PROCESS_PHOTO")));return d;}
 @AfterEach void clear(){UserContext.clear();}
 @Test void completedTeacherMaySaveIncompleteDraftButCannotSubmitMissingEvidence(){AwardDTO d=dto(true);Long id=workflow.save(null,d,false);assertEquals("DRAFT",awards.getById(id).getStatus());assertThrows(BusinessException.class,()->workflow.submit(id));assertEquals("DRAFT",awards.getById(id).getStatus());}
 @Test void sharedAchievementReviewHistoryPersonalScopeAndCelebrationAreConsistent(){AwardDTO d=complete();Long id=workflow.save(null,d,false);assertThrows(BusinessException.class,()->workflow.save(id,d,false));as(admin,"ADMIN");AwardReviewDTO r=new AwardReviewDTO();r.setResult("REJECTED");assertThrows(BusinessException.class,()->workflow.review(id,r));r.setComment("Replace evidence");workflow.review(id,r);as(teacher,"TEACHER");workflow.save(id,d,false);as(admin,"ADMIN");r.setResult("APPROVED");workflow.review(id,r);assertEquals(2,awards.getById(id).getTeachers().size());as(student,"STUDENT");assertEquals(id,awards.getById(id).getId());as(otherStudent,"STUDENT");assertThrows(BusinessException.class,()->awards.getById(id));as(teacher,"TEACHER");assertEquals(1,workflow.pendingCelebrations().size());assertTrue(workflow.claimCelebration(id));assertFalse(workflow.claimCelebration(id));assertTrue(workflow.pendingCelebrations().isEmpty());as(coTeacher,"TEACHER");assertEquals(1,workflow.pendingCelebrations().size());as(admin,"ADMIN");awards.revoke(id,"Invalidated outcome");as(coTeacher,"TEACHER");assertTrue(workflow.pendingCelebrations().isEmpty());assertFalse(workflow.claimCelebration(id));}
 @Test void materialsAndRealAchievementUniquenessAreEnforced(){AwardDTO incomplete=complete();incomplete.setProcessPhotoFileIds(List.of(incomplete.getProcessPhotoFileIds().get(0)));assertThrows(BusinessException.class,()->workflow.save(null,incomplete,false));AwardDTO d=complete();workflow.save(null,d,false);assertThrows(BusinessException.class,()->workflow.save(null,d,false));d.setAchievementName("Distinct second competition result");assertNotNull(workflow.save(null,d,false));}
 @Test void correctionRetainsOriginalPeopleAndMajorSnapshotsAndAllowsManagerToReuseMaterials(){
  AwardDTO d=complete();Long id=workflow.save(null,d,false);as(admin,"ADMIN");AwardReviewDTO review=new AwardReviewDTO();review.setResult("APPROVED");workflow.review(id,review);
  var before=awards.getById(id);String studentName=(String)before.getStudents().get(0).get("name"),teacherName=(String)before.getTeachers().get(0).get("name");
  jdbc.update("UPDATE sys_user SET real_name='Changed later',major_id=NULL WHERE id IN (?,?)",student,teacher);
  Long major=jdbc.queryForObject("SELECT major_id FROM registration WHERE id=?",Long.class,registration);Long majorAdmin=user(UUID.randomUUID().toString(),"MAJOR_ADMIN",major);as(majorAdmin,"MAJOR_ADMIN");
  d.setReason("Correct level");d.setAwardLevel("SECOND");workflow.save(id,d,true);var after=awards.getById(id);
  assertEquals(before.getParticipantsSnapshot(),after.getParticipantsSnapshot());assertEquals(studentName,after.getStudents().get(0).get("name"));assertEquals(teacherName,after.getTeachers().get(0).get("name"));assertEquals("PENDING",after.getStatus());
  assertTrue(awards.history(id).stream().anyMatch(h->"Correct level".equals(h.getReason())));
 }
 @Test void unconfiguredMajorAdminCannotHandleUnclassifiedRegistration(){
  jdbc.update("UPDATE registration SET major_id=NULL WHERE id=?",registration);jdbc.update("UPDATE sys_user SET role='MAJOR_ADMIN',major_id=NULL WHERE id=?",admin);as(admin,"MAJOR_ADMIN");
  BusinessException error=assertThrows(BusinessException.class,()->workflow.save(null,complete(),false));assertEquals(403,error.getCode());
 }
 @Test void approvalRevalidatesExactNumberOfStoredProcessPhotos(){
  AwardDTO d=complete();Long id=workflow.save(null,d,false);jdbc.update("UPDATE award SET process_photo_file_ids=? WHERE id=?","["+d.getProcessPhotoFileIds().get(0)+"]",id);as(admin,"ADMIN");AwardReviewDTO review=new AwardReviewDTO();review.setResult("APPROVED");
  assertThrows(BusinessException.class,()->workflow.review(id,review));assertEquals("PENDING",awards.getById(id).getStatus());
 }
 @Test void multipleSharedOutcomesCountAchievementsTeamsStudentsAndTeachersSeparately(){
  AwardDTO first=complete();Long a=workflow.save(null,first,false);first.setAchievementName("Another actual outcome");first.setStudentIds(List.of(student,otherStudent));Long b=workflow.save(null,first,false);
  as(admin,"ADMIN");AwardReviewDTO review=new AwardReviewDTO();review.setResult("APPROVED");workflow.review(a,review);workflow.review(b,review);
  var counts=statistics.overview();assertEquals(2L,counts.getAwardCount());assertEquals(1L,counts.getAwardTeamCount());assertEquals(2L,counts.getAwardStudentCount());assertEquals(4L,counts.getTeacherAchievementCount());
  awards.revoke(b,"duplicate evidence revoked");counts=statistics.overview();assertEquals(1L,counts.getAwardCount());assertEquals(1L,counts.getAwardTeamCount());assertEquals(1L,counts.getAwardStudentCount());assertEquals(2L,counts.getTeacherAchievementCount());
 }
 @Test void actualWinnersCanReadTheirReviewNotificationButPersonalListOnlyShowsApproved(){
  Long id=workflow.save(null,complete(),false);as(student,"STUDENT");assertEquals(id,awards.getById(id).getId());assertEquals(0L,awards.page(new AwardQueryDTO()).getTotal());
  as(otherStudent,"STUDENT");assertThrows(BusinessException.class,()->awards.getById(id));
 }
}
