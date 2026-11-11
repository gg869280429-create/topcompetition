package com.cie.competition;

import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
@Transactional
class GuidanceCompletionIntegrationTest extends IsolatedDatabaseTest {
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JwtUtil jwt;
    long major,teacher,student,other,admin,competition,registration,guidance;
    String teacherToken,adminToken,otherToken;
    @BeforeEach void fixture() {
        String tag="complete_"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO sys_major(name) VALUES (?)",tag);
        major=jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,tag);
        teacher=user(tag+"_t","TEACHER"); other=user(tag+"_o","TEACHER"); student=user(tag+"_s","STUDENT");admin=user(tag+"_a","ADMIN");
        teacherToken=jwt.generateToken(teacher,tag+"_t","TEACHER");otherToken=jwt.generateToken(other,tag+"_o","TEACHER");adminToken=jwt.generateToken(admin,tag+"_a","ADMIN");
        jdbc.update("INSERT INTO competition(title,status,major_id,max_members,max_teachers,publisher_id) VALUES (?,'IN_PROGRESS',?,3,1,?)",tag,major,admin);
        competition=jdbc.queryForObject("SELECT id FROM competition WHERE title=?",Long.class,tag);
        jdbc.update("INSERT INTO registration(competition_id,team_name,leader_id,major_id,status) VALUES (?,?,?,?,'APPROVED')",competition,tag,student,major);
        registration=jdbc.queryForObject("SELECT id FROM registration WHERE competition_id=?",Long.class,competition);
        jdbc.update("INSERT INTO registration_member(registration_id,competition_id,user_id,member_role,active_key,name_snapshot) VALUES (?,?,?,'LEADER',1,'学生')",registration,competition,student);
        jdbc.update("INSERT INTO guidance_quota(competition_id,teacher_id,quota) VALUES (?,?,1)",competition,teacher);
        jdbc.update("INSERT INTO guidance(registration_id,competition_id,teacher_id,status) VALUES (?,?,?,'ACTIVE')",registration,competition,teacher);
        guidance=jdbc.queryForObject("SELECT id FROM guidance WHERE registration_id=?",Long.class,registration);
    }
    long user(String name,String role){jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES (?,'test-only',?,?,?,1)",name,name,role,major);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
    com.fasterxml.jackson.databind.JsonNode request(String method,String path,String token,Object body,int expected)throws Exception{
        var b=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api"+path)).header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON);
        if(body!=null)b.content(json.writeValueAsBytes(body));var response=mvc.perform(b).andReturn().getResponse();var result=json.readTree(response.getContentAsString());assertEquals(expected,result.path("code").asInt(),path);return result.path("data");
    }
    @Test void completionReleasesOneQuotaAndRetainsStudentAccess()throws Exception{
        request("POST","/guidance/"+guidance+"/complete",otherToken,null,403);
        request("POST","/guidance/"+guidance+"/complete",teacherToken,null,200);
        request("POST","/guidance/"+guidance+"/complete",teacherToken,null,200);
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM guidance WHERE id=?",String.class,guidance));
        assertNull(jdbc.queryForObject("SELECT active_key FROM guidance WHERE id=?",Integer.class,guidance));
        request("GET","/registration/"+registration,teacherToken,null,200);
        assertEquals(1,request("GET","/guidance/mine",teacherToken,null,200).path("total").asInt());
        assertEquals(1,request("GET","/guidance/quota/mine",teacherToken,null,200).path("records").get(0).path("remaining").asInt());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM business_history WHERE biz_type='GUIDANCE' AND biz_id=? AND operation='COMPLETED'",Integer.class,guidance));
    }
    @Test void perStudentResultsDoNotChangeRegistrationOrCreateAwards()throws Exception{
        request("POST","/guidance/"+guidance+"/complete",teacherToken,null,200);
        String path="/guidance/"+guidance+"/results/"+student;
        request("PUT",path,teacherToken,Map.of("participated",false,"won",true),400);
        request("PUT",path,teacherToken,Map.of("participated",false,"won",false),200);
        request("PUT",path,teacherToken,Map.of("participated",true,"won",false,"remark","完成参赛"),200);
        request("PUT","/guidance/"+guidance+"/results/"+other,teacherToken,Map.of("participated",true,"won",true),403);
        request("GET","/guidance/"+guidance+"/results",otherToken,null,403);
        assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM registration WHERE id=?",String.class,registration));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM award WHERE registration_id=?",Integer.class,registration));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM participation_result WHERE guidance_id=?",Integer.class,guidance));
        var results=request("GET","/guidance/"+guidance+"/results",teacherToken,null,200);
        assertTrue(results.get(0).path("participated").asBoolean());
        assertFalse(results.get(0).path("won").asBoolean());
    }
    @Test void manualQuotaIncreaseIsSeparateFromRelease()throws Exception{
        request("GET","/guidance/teacher/"+teacher+"/quotas",adminToken,null,200);
        request("POST","/guidance/teacher/"+teacher+"/quotas/"+competition+"/increase",teacherToken,Map.of("amount",2),403);
        request("POST","/guidance/teacher/"+teacher+"/quotas/"+competition+"/increase",adminToken,Map.of("amount",2),200);
        request("POST","/guidance/"+guidance+"/complete",teacherToken,null,200);
        assertEquals(3,jdbc.queryForObject("SELECT quota FROM guidance_quota WHERE competition_id=? AND teacher_id=?",Integer.class,competition,teacher));
        assertEquals(3,request("GET","/guidance/quota/mine",teacherToken,null,200).path("records").get(0).path("remaining").asInt());
    }
    @Test void competitionCancellationBlocksAwardsAndClosesPendingWorkWithoutRepeatingRelease()throws Exception{
        jdbc.update("INSERT INTO award(competition_id,registration_id,award_level,status) VALUES (?,?,'FIRST','APPROVED')",competition,registration);
        String path="/competition/"+competition+"/status?status=CANCELLED&reason=organizer_cancelled";
        request("PUT",path,adminToken,null,400);
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM guidance WHERE id=?",String.class,guidance));
        jdbc.update("UPDATE award SET status='REVOKED' WHERE registration_id=?",registration);
        jdbc.update("INSERT INTO withdrawal_request(registration_id,reason) VALUES (?,'withdrawal')",registration);
        request("PUT",path,adminToken,null,200);
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM registration WHERE id=?",String.class,registration));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM withdrawal_request WHERE registration_id=?",String.class,registration));
        assertEquals("ENDED",jdbc.queryForObject("SELECT status FROM guidance WHERE id=?",String.class,guidance));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1",Integer.class,registration));
        request("PUT",path,adminToken,null,400);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM business_history WHERE biz_type='GUIDANCE' AND biz_id=? AND operation='ENDED'",Integer.class,guidance));
    }
    @Test void completedTeacherCannotSeeLaterMembersThroughOtherGuidanceOrWorkflowHistory()throws Exception{
        request("POST","/guidance/"+guidance+"/complete",teacherToken,null,200);
        String tag="later_"+UUID.randomUUID().toString().replace("-","");long newcomer=user(tag,"STUDENT");
        jdbc.update("INSERT INTO registration_member(registration_id,competition_id,user_id,name_snapshot,member_role) VALUES (?,?,?,?,'MEMBER')",registration,competition,newcomer,tag);
        jdbc.update("INSERT INTO guidance(registration_id,competition_id,teacher_id,status,members_snapshot) VALUES (?,?,?,'ACTIVE',?)",registration,competition,other,"[{\"userId\":"+newcomer+",\"name\":\""+tag+"\"}]");
        jdbc.update("INSERT INTO business_history(biz_type,biz_id,operation,after_json) VALUES ('REGISTRATION',?,'SUBMIT',?)",registration,"{\"newMember\":\""+tag+"\"}");
        assertFalse(request("GET","/registration/"+registration,teacherToken,null,200).toString().contains(tag));
        assertFalse(request("GET","/guidance/list?registrationId="+registration,teacherToken,null,200).toString().contains(tag));
        assertFalse(request("GET","/registration/"+registration+"/history",teacherToken,null,200).toString().contains(tag));
    }
}
