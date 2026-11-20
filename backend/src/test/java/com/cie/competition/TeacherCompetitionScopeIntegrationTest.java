package com.cie.competition;

import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties={"spring.main.banner-mode=off","logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class TeacherCompetitionScopeIntegrationTest extends IsolatedDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    String prefix;
    long teacher, otherTeacher, emptyTeacher, student, admin, majorAdmin;
    long own, shared, other, historical, draft, zeroQuota;

    @BeforeEach void fixtures() {
        prefix="scope_"+UUID.randomUUID().toString().replace("-","");
        teacher=user("TEACHER");otherTeacher=user("TEACHER");emptyTeacher=user("TEACHER");student=user("STUDENT");admin=user("ADMIN");majorAdmin=user("MAJOR_ADMIN");
        own=competition("own","REGISTERING");shared=competition("shared","REGISTERING");other=competition("other","REGISTERING");historical=competition("historical","ENDED");draft=competition("draft","DRAFT");zeroQuota=competition("zero","REGISTERING");
        quota(own,teacher,3);quota(shared,teacher,2);quota(shared,otherTeacher,7);quota(other,otherTeacher,4);quota(draft,teacher,1);quota(zeroQuota,teacher,0);
        long registration=registration(historical);
        jdbc.update("INSERT INTO guidance(registration_id,competition_id,teacher_id,status,active_key) VALUES(?,NULL,?,'ENDED',NULL)",registration,teacher);
    }
    long user(String role) {
        String name=prefix+UUID.randomUUID().toString().substring(0,8);
        jdbc.update("INSERT INTO sys_user(username,password,real_name,role,status) VALUES(?,'test-only',?,?,1)",name,name,role);
        return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);
    }
    long competition(String suffix,String status) {
        String title=prefix+suffix;
        jdbc.update("INSERT INTO competition(title,status,publisher_id) VALUES(?,?,?)",title,status,admin);
        return jdbc.queryForObject("SELECT id FROM competition WHERE title=?",Long.class,title);
    }
    long registration(long competition) {
        jdbc.update("INSERT INTO registration(competition_id,leader_id,team_name,status) VALUES(?,?,?,'APPROVED')",competition,student,prefix);
        return jdbc.queryForObject("SELECT id FROM registration WHERE competition_id=? AND leader_id=?",Long.class,competition,student);
    }
    void quota(long competition,long actor,int quota) {jdbc.update("INSERT INTO guidance_quota(competition_id,teacher_id,quota) VALUES(?,?,?)",competition,actor,quota);}
    JsonNode call(String path,long actor,int expected)throws Exception {
        String role=jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,actor);
        var response=mvc.perform(get("/api"+path).header("Authorization","Bearer "+jwt.generateToken(actor,"fixture",role))).andReturn().getResponse();
        var result=json.readTree(response.getContentAsByteArray());
        assertEquals(expected,result.path("code").asInt(),path+": "+result.path("message").asText());
        return result.path("data");
    }
    Set<Long> ids(JsonNode records) {var ids=new HashSet<Long>();records.forEach(r->ids.add(r.path("id").asLong()));return ids;}

    @Test void teacherPageUsesOwnRelationshipsBeforePaginationAndIgnoresForgedTeacherId()throws Exception {
        var page=call("/competition/page?keyword="+prefix+"&size=100&teacherId="+otherTeacher,teacher,200);
        assertEquals(Set.of(own,shared,historical,zeroQuota),ids(page.path("records")));assertEquals(4,page.path("total").asInt());
        var first=call("/competition/page?keyword="+prefix+"&size=1&current=1",teacher,200);
        var second=call("/competition/page?keyword="+prefix+"&size=1&current=2",teacher,200);
        assertEquals(4,first.path("total").asInt());assertEquals(1,first.path("records").size());
        assertNotEquals(first.path("records").get(0).path("id"),second.path("records").get(0).path("id"));
        assertEquals(Set.of(historical),ids(call("/competition/page?keyword="+prefix+"&status=ENDED",teacher,200).path("records")));
        assertEquals(0,call("/competition/page?keyword="+prefix,emptyTeacher,200).path("total").asInt());
    }

    @Test void teacherCannotReadOtherCompetitionsResourcesOrTeacherProfiles()throws Exception {
        call("/competition/"+other,teacher,403);
        call("/competition/"+other+"/document",teacher,403);
        call("/guidance/teachers?competitionId="+other,teacher,403);
        call("/competition/"+draft,teacher,404);
        assertEquals(own,call("/competition/"+own,teacher,200).path("id").asLong());
        var profiles=call("/guidance/teachers?competitionId="+shared+"&teacherId="+otherTeacher,teacher,200);
        assertEquals(1,profiles.size());assertEquals(teacher,profiles.get(0).path("teacherId").asLong());
        assertEquals(2,profiles.get(0).path("quota").asInt());assertEquals(2,profiles.get(0).path("remaining").asInt());
        assertEquals(otherTeacher,call("/guidance/teachers?competitionId="+shared,otherTeacher,200).get(0).path("teacherId").asLong());
    }

    @Test void completedGuidanceWithoutQuotaAndZeroQuotaRetainOnlyOwnProfile()throws Exception {
        call("/competition/"+historical,teacher,200);
        for(long id:List.of(historical,zeroQuota)) {
            var profiles=call("/guidance/teachers?competitionId="+id,teacher,200);
            assertEquals(1,profiles.size());assertEquals(teacher,profiles.get(0).path("teacherId").asLong());
            assertEquals(0,profiles.get(0).path("quota").asInt());assertEquals(0,profiles.get(0).path("remaining").asInt());
        }
    }

    @Test void studentsAndAdministratorsKeepTheirExistingPreviewScope()throws Exception {
        assertEquals(Set.of(own,shared,other,historical,zeroQuota),ids(call("/competition/page?keyword="+prefix+"&size=100",student,200).path("records")));
        call("/competition/"+other,student,200);
        assertEquals(2,call("/guidance/teachers?competitionId="+shared,student,200).size());
        for(long actor:List.of(admin,majorAdmin)) {
            assertEquals(6,call("/competition/page?keyword="+prefix+"&size=100",actor,200).path("total").asInt());
            assertEquals(2,call("/guidance/teachers?competitionId="+shared,actor,200).size());
        }
    }
}
