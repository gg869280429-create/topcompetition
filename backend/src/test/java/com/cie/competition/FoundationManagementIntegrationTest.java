package com.cie.competition;
import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest(properties={"spring.main.banner-mode=off","logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class FoundationManagementIntegrationTest extends IsolatedDatabaseTest {
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JwtUtil jwt;
 record Actor(long id,long major,String token){}
 Actor actor(String role,long major){String name="foundation_"+UUID.randomUUID().toString().replace("-","");jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES (?,'test-only',?,?,?,1)",name,name,role,major);long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);return new Actor(id,major,jwt.generateToken(id,name,role));}
 long major(){String name="major_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_major(name) VALUES (?)",name);return jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,name);}
 JsonNode call(String method,String path,Actor actor,Object body,int code)throws Exception{var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(method),java.net.URI.create("/api"+path)).contentType(MediaType.APPLICATION_JSON).header("Origin","http://127.0.0.1:5173").header("Authorization","Bearer "+actor.token());if(body!=null)request.content(json.writeValueAsBytes(body));var response=mvc.perform(request).andReturn().getResponse();var result=json.readTree(response.getContentAsString());assertEquals(code,result.path("code").asInt(),method+" "+path+" "+result.path("message").asText());return result.path("data");}
 @Test void majorPermissionsUniquenessRenameAndLinkedDeletion()throws Exception{
  long mid=major();var admin=actor("ADMIN",mid);for(String role:List.of("MAJOR_ADMIN","TEACHER","STUDENT"))call("GET","/major",actor(role,mid),null,403);
  String name="fresh_"+UUID.randomUUID();long id=call("POST","/major",admin,Map.of("name",name),200).asLong();call("POST","/major",admin,Map.of("name",name),400);
  call("PUT","/major/"+id,admin,Map.of("name",name+"_renamed"),200);assertEquals(name+"_renamed",jdbc.queryForObject("SELECT name FROM sys_major WHERE id=?",String.class,id));
  jdbc.update("INSERT INTO competition(title,major_id) VALUES ('Linked classification',?)",id);call("DELETE","/major/"+id,admin,null,400);
  long free=call("POST","/major",admin,Map.of("name","free_"+UUID.randomUUID()),200).asLong();call("DELETE","/major/"+free,admin,null,200);
  call("DELETE","/major/"+mid,admin,null,400);
 }
 @Test void studentCreationScopeDisabledLoginAndHistoricalDeletionProtection()throws Exception{
  long own=major(),other=major();var admin=actor("ADMIN",own);var manager=actor("MAJOR_ADMIN",own);var teacher=actor("TEACHER",own);
  var body=new HashMap<String,Object>(Map.of("username","student_"+UUID.randomUUID().toString().replace("-",""),"realName","New student","password","test-password-only","majorId",own,"status",1));
  call("POST","/student",teacher,body,403);long id=call("POST","/student",manager,body,200).asLong();assertNotEquals(body.get("password"),jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,id));
  body.put("majorId",other);call("PUT","/student/"+id,manager,body,403);call("PUT","/student/"+id,admin,body,200);call("DELETE","/student/"+id,manager,null,403);
  body.put("status",0);call("PUT","/student/"+id,admin,body,200);call("POST","/auth/login",admin,Map.of("username",body.get("username"),"password","test-password-only"),403);
  var foreign=call("GET","/student/page",manager,null,200);assertEquals(0,foreign.path("total").asLong());
  jdbc.update("INSERT INTO competition(title) VALUES ('Historical student competition')");long cid=jdbc.queryForObject("SELECT MAX(id) FROM competition",Long.class);jdbc.update("INSERT INTO registration(competition_id,team_name,leader_id,major_id) VALUES (?,'Historical',?,?)",cid,id,other);call("DELETE","/student/"+id,admin,null,400);
 }
 @Test void competitionClassificationRequiresPublicationButDoesNotRestrictOtherMajors()throws Exception{
  long classification=major(),studentMajor=major();var admin=actor("ADMIN",classification);var teacher=actor("TEACHER",classification);var student=actor("STUDENT",studentMajor);
  var body=new HashMap<String,Object>(Map.of("title","Classification competition","competitionType","PERSONAL","level","SCHOOL","registrationStartTime",LocalDateTime.now().minusDays(1),"registrationEndTime",LocalDateTime.now().plusDays(1),"maxMembers",1,"maxTeachers",1,"allowCrossMajor",0,"teachers",List.of(Map.of("teacherId",teacher.id(),"quota",3))));
  long cid=call("POST","/competition",admin,body,200).asLong();call("PUT","/competition/"+cid+"/status?status=REGISTERING",admin,null,400);
  body.put("majorId",classification);call("PUT","/competition/"+cid,admin,body,200);call("PUT","/competition/"+cid+"/status?status=REGISTERING",admin,null,200);
  assertEquals(classification,call("GET","/competition/"+cid,student,null,200).path("majorId").asLong());
  long rid=call("POST","/registration",student,Map.of("competitionId",cid,"teamName","Other major entrant","preferredTeacherId",teacher.id(),"memberIds",List.of()),200).asLong();
  assertEquals(studentMajor,jdbc.queryForObject("SELECT major_id FROM registration WHERE id=?",Long.class,rid));
 }
 @Test void historicalCompetitionClassificationCanBeFilledWithoutChangingRulesOrState()throws Exception{
  long classification=major();var admin=actor("ADMIN",classification);var manager=actor("MAJOR_ADMIN",classification);var teacher=actor("TEACHER",classification);
  String title="legacy_"+UUID.randomUUID();jdbc.update("INSERT INTO competition(title,status,max_members,allow_cross_major,publisher_id) VALUES (?,'ENDED',3,0,?)",title,admin.id());long id=jdbc.queryForObject("SELECT id FROM competition WHERE title=?",Long.class,title);
  String path="/competition/"+id+"/major?majorId="+classification;
  call("PUT",path,teacher,null,403);call("PUT",path,manager,null,403);call("PUT",path,admin,null,200);
  assertEquals(classification,jdbc.queryForObject("SELECT major_id FROM competition WHERE id=?",Long.class,id));assertEquals("ENDED",jdbc.queryForObject("SELECT status FROM competition WHERE id=?",String.class,id));assertEquals(3,jdbc.queryForObject("SELECT max_members FROM competition WHERE id=?",Integer.class,id));
  assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM business_history WHERE biz_type='COMPETITION' AND biz_id=? AND operation='MAJOR_CLASSIFIED'",Integer.class,id));
 }
}
