package com.cie.competition;

import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
@Transactional
class TeamRosterIntegrationTest extends IsolatedDatabaseTest {
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired JwtUtil jwt; @Autowired ObjectMapper json;
    long major,leader,member,newcomer,teacher,otherTeacher,admin,competition,registration,guidance;
    @BeforeEach void fixture() {
        String tag="roster_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_major(name) VALUES(?)",tag);major=jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,tag);
        leader=user("STUDENT");member=user("STUDENT");newcomer=user("STUDENT");teacher=user("TEACHER");otherTeacher=user("TEACHER");admin=user("ADMIN");
        jdbc.update("INSERT INTO competition(title,status,major_id,max_members,max_teachers,allow_cross_major,publisher_id,registration_start_time,registration_end_time) VALUES (?,'IN_PROGRESS',?,3,1,0,?,DATE_SUB(NOW(),INTERVAL 2 DAY),DATE_SUB(NOW(),INTERVAL 1 DAY))",tag,major,admin);
        competition=jdbc.queryForObject("SELECT id FROM competition WHERE title=?",Long.class,tag);
        jdbc.update("INSERT INTO registration(competition_id,team_name,leader_id,major_id,status,reviewer_id,review_comment) VALUES (?,?,?,?,'APPROVED',?,'原审核通过意见')",competition,tag,leader,major,admin);
        registration=jdbc.queryForObject("SELECT id FROM registration WHERE competition_id=?",Long.class,competition);
        for(long student:List.of(leader,member))jdbc.update("INSERT INTO registration_member(registration_id,competition_id,user_id,member_role,active_key,name_snapshot,major_id_snapshot) VALUES (?,?,?,?,1,?,?)",registration,competition,student,student==leader?"LEADER":"MEMBER","学生"+student,major);
        jdbc.update("INSERT INTO guidance_quota(competition_id,teacher_id,quota) VALUES (?,?,2)",competition,teacher);
        jdbc.update("INSERT INTO guidance(registration_id,competition_id,teacher_id,status) VALUES (?,?,?,'ACTIVE')",registration,competition,teacher);
        guidance=jdbc.queryForObject("SELECT id FROM guidance WHERE registration_id=?",Long.class,registration);
    }
    long user(String role){String name="roster_user_"+UUID.randomUUID().toString().replace("-","");jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES(?,'test-only',?,?,?,1)",name,name,role,major);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
    String token(long user){return jwt.generateToken(user,"test",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,user));}
    JsonNode call(String method,String path,long user,Object body,int code)throws Exception {
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api"+path)).header("Authorization","Bearer "+token(user)).contentType("application/json");
        if(body!=null)request.content(json.writeValueAsBytes(body));
        var response=json.readTree(mvc.perform(request).andReturn().getResponse().getContentAsByteArray());assertEquals(code,response.path("code").asInt(),path+" "+response.path("message"));return response.path("data");
    }
    void edit(long user,List<Long> members,int code)throws Exception{call("PUT","/registration/"+registration+"/members",user,Map.of("memberIds",members,"reason","调整队员"),code);}
    long invitation(){return jdbc.queryForObject("SELECT MAX(id) FROM team_invitation WHERE registration_id=? AND invitee_id=?",Long.class,registration,newcomer);}
    @Test void captainCanChangeApprovedRosterAfterDeadlineWithoutAnotherReview()throws Exception {
        edit(leader,List.of(newcomer),200);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND current_member=1",Integer.class,registration));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND user_id=? AND active_key=1",Integer.class,registration,newcomer));
        call("PUT","/registration/invitations/"+invitation()+"/response",newcomer,Map.of("accepted",true),200);
        assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM registration WHERE id=?",String.class,registration));
        assertEquals("原审核通过意见",jdbc.queryForObject("SELECT review_comment FROM registration WHERE id=?",String.class,registration));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1",Integer.class,registration));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM guidance WHERE id=?",String.class,guidance));
        var results=call("GET","/guidance/"+guidance+"/results",teacher,null,200);
        assertEquals(Set.of(leader,newcomer),new HashSet<>(List.of(results.get(0).path("studentId").asLong(),results.get(1).path("studentId").asLong())));
    }
    @Test void onlyCaptainCanChangeRosterAndQualificationIsCheckedAgainOnAccept()throws Exception {
        edit(member,List.of(newcomer),403);edit(teacher,List.of(newcomer),403);edit(admin,List.of(newcomer),403);
        edit(leader,List.of(member,newcomer),200);jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",newcomer);
        call("PUT","/registration/invitations/"+invitation()+"/response",newcomer,Map.of("accepted",true),401);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1",Integer.class,registration));
    }
    @Test void confirmationLocksRosterPermanentlyAndPendingInvitationsMustBeResolved()throws Exception {
        edit(leader,List.of(member,newcomer),200);
        call("PUT","/guidance/"+guidance+"/results/team",teacher,Map.of("participated",true,"won",false),400);
        call("PUT","/registration/invitations/"+invitation()+"/response",newcomer,Map.of("accepted",true),200);
        call("PUT","/guidance/"+guidance+"/results/team",teacher,Map.of("participated",true,"won",true),200);
        assertNotNull(jdbc.queryForObject("SELECT roster_locked_at FROM registration WHERE id=?",java.sql.Timestamp.class,registration));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM participation_result WHERE guidance_id=? AND participated=1 AND won=1",Integer.class,guidance));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE registration_id=?",Integer.class,registration));
        call("PUT","/guidance/"+guidance+"/results/"+leader,teacher,Map.of("participated",false,"won",false),200);
        edit(leader,List.of(member),400);
        call("POST","/registration/"+registration+"/adjustment",leader,Map.of("reason","不能绕过锁定"),400);
    }
    @Test void negativeResultDoesNotLockAndRejectedOrRevokedInvitationsDoNotJoin()throws Exception {
        call("PUT","/guidance/"+guidance+"/results/team",teacher,Map.of("participated",false,"won",false),200);
        edit(leader,List.of(member,newcomer),200);
        call("PUT","/registration/invitations/"+invitation()+"/response",newcomer,Map.of("accepted",false),200);
        edit(leader,List.of(member,newcomer),200);
        call("DELETE","/registration/invitations/"+invitation(),leader,null,200);
        call("PUT","/registration/invitations/"+invitation()+"/response",newcomer,Map.of("accepted",true),400);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1",Integer.class,registration));
    }
    @Test void teamBatchRejectsOtherTeachersInvalidValuesAndPersonalCompetitions()throws Exception {
        call("PUT","/guidance/"+guidance+"/results/team",otherTeacher,Map.of("participated",true,"won",true),403);
        call("PUT","/guidance/"+guidance+"/results/team",teacher,Map.of("participated",false,"won",true),400);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM participation_result WHERE guidance_id=?",Integer.class,guidance));
    }
    @Test void teamBatchIsNotAvailableForPersonalCompetitions()throws Exception {
        jdbc.update("UPDATE competition SET competition_type='PERSONAL' WHERE id=?",competition);
        call("PUT","/guidance/"+guidance+"/results/team",teacher,Map.of("participated",true,"won",false),400);
    }
    @Test void endedAndCancelledCompetitionsRejectRosterChanges()throws Exception {
        jdbc.update("UPDATE competition SET status='ENDED' WHERE id=?",competition);edit(leader,List.of(member),400);
        jdbc.update("UPDATE competition SET status='CANCELLED' WHERE id=?",competition);edit(leader,List.of(member),400);
    }
    @Test void crossMajorAndDuplicateMembersAreRejected()throws Exception {
        edit(leader,List.of(leader),400);edit(leader,List.of(member,member),400);
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?",major+100000,newcomer);edit(leader,List.of(newcomer),400);
    }
    int concurrentCall(String path,long actor,Object body,CountDownLatch start)throws Exception {
        String auth=token(actor);start.await(10,TimeUnit.SECONDS);
        return json.readTree(mvc.perform(put("/api"+path).header("Authorization","Bearer "+auth).contentType("application/json").content(json.writeValueAsBytes(body))).andReturn().getResponse().getContentAsByteArray()).path("code").asInt();
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void simultaneousAcceptancesAcrossTeamsAllowOnlyOneQualification()throws Exception {
        long secondLeader=user("STUDENT");String team="concurrent_"+UUID.randomUUID();
        jdbc.update("INSERT INTO registration(competition_id,team_name,leader_id,major_id,status) VALUES (?,?,?,?,'APPROVED')",competition,team,secondLeader,major);
        long second=jdbc.queryForObject("SELECT id FROM registration WHERE team_name=?",Long.class,team);
        jdbc.update("INSERT INTO registration_member(registration_id,competition_id,user_id,member_role,active_key,name_snapshot,major_id_snapshot) VALUES (?,?,?,'LEADER',1,'第二队长',?)",second,competition,secondLeader,major);
        edit(leader,List.of(member,newcomer),200);
        call("PUT","/registration/"+second+"/members",secondLeader,Map.of("memberIds",List.of(newcomer)),200);
        long firstInvite=invitation(),secondInvite=jdbc.queryForObject("SELECT MAX(id) FROM team_invitation WHERE registration_id=?",Long.class,second);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->concurrentCall("/registration/invitations/"+firstInvite+"/response",newcomer,Map.of("accepted",true),start));
            var other=pool.submit(()->concurrentCall("/registration/invitations/"+secondInvite+"/response",newcomer,Map.of("accepted",true),start));start.countDown();
            assertEquals(Set.of(200,400),Set.of(first.get(20,TimeUnit.SECONDS),other.get(20,TimeUnit.SECONDS)));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE competition_id=? AND user_id=? AND active_key=1",Integer.class,competition,newcomer));
        }finally{pool.shutdownNow();}
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentRosterEditAndTeacherConfirmationKeepResultsConsistent()throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var edit=pool.submit(()->concurrentCall("/registration/"+registration+"/members",leader,Map.of("memberIds",List.of()),start));
            var confirm=pool.submit(()->concurrentCall("/guidance/"+guidance+"/results/team",teacher,Map.of("participated",true,"won",false),start));start.countDown();
            assertEquals(200,confirm.get(20,TimeUnit.SECONDS));assertTrue(Set.of(200,400).contains(edit.get(20,TimeUnit.SECONDS)));
            assertNotNull(jdbc.queryForObject("SELECT roster_locked_at FROM registration WHERE id=?",java.sql.Timestamp.class,registration));
            assertEquals(new HashSet<>(jdbc.queryForList("SELECT user_id FROM registration_member WHERE registration_id=? AND current_member=1",Long.class,registration)),new HashSet<>(jdbc.queryForList("SELECT student_id FROM participation_result WHERE guidance_id=? AND participated=1",Long.class,guidance)));
        }finally{pool.shutdownNow();}
    }
}
