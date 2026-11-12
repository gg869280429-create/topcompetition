package com.cie.competition;

import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.main.banner-mode=off","logging.level.org.springframework.boot.autoconfigure.security=ERROR"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class StudentProfileIntegrationTest extends IsolatedDatabaseTest {
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired ObjectMapper json;
 @Autowired JwtUtil jwt; @Autowired PasswordEncoder encoder;
 record Actor(long id,String username,String token) {}
 Actor actor(String role) {
  String username="profile_"+UUID.randomUUID().toString().replace("-","");
  jdbc.update("INSERT INTO sys_user(username,password,real_name,role,status) VALUES (?,?,?, ?,1)",username,encoder.encode("Original-test-123"),"原姓名",role);
  long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,username);
  return new Actor(id,username,jwt.generateToken(id,username,role));
 }
 JsonNode call(String method,String path,String token,Object body,int code)throws Exception {
  var request=MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),URI.create("/api"+path)).contentType(MediaType.APPLICATION_JSON);
  if(token!=null)request.header("Authorization","Bearer "+token);
  if(body!=null)request.content(json.writeValueAsBytes(body));
  var response=mvc.perform(request).andReturn().getResponse();var result=json.readTree(response.getContentAsByteArray());
  assertEquals(code,result.path("code").asInt(),method+" "+path+" "+result.path("message").asText());return result.path("data");
 }
 @Test void onlyStudentsCanReadTheirOwnProfile()throws Exception {
  var student=actor("STUDENT");var info=call("GET","/student/profile",student.token(),null,200);
  assertEquals(student.id(),info.path("id").asLong());assertFalse(info.has("password"));assertFalse(info.has("tokenVersion"));
  for(String role:new String[]{"ADMIN","MAJOR_ADMIN","TEACHER"})call("GET","/student/profile",actor(role).token(),null,403);
  call("GET","/student/profile",null,null,401);
 }

 @Test void teacherPersonalCenterEditsOnlyOwnContactInformation()throws Exception {
  var teacher=actor("TEACHER");var other=actor("TEACHER");
  var profile=call("GET","/profile",teacher.token(),null,200);
  assertEquals(teacher.id(),profile.path("id").asLong());assertFalse(profile.has("password"));assertFalse(profile.has("tokenVersion"));
  var body=new java.util.HashMap<String,Object>(Map.of("realName","教师新姓名","email","teacher@example.org","phone","13800138000","id",other.id(),"role","ADMIN","majorId",12345));
  call("PUT","/profile",teacher.token(),body,200);
  assertEquals("教师新姓名",jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=?",String.class,teacher.id()));
  assertEquals("TEACHER",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,teacher.id()));
  assertEquals("原姓名",jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=?",String.class,other.id()));
 }

 @Test void sharedPersonalCenterIsRestrictedToStudentsAndTeachers()throws Exception {
  for(String role:new String[]{"STUDENT","TEACHER"})call("GET","/profile",actor(role).token(),null,200);
  for(String role:new String[]{"ADMIN","MAJOR_ADMIN"})call("GET","/profile",actor(role).token(),null,403);
  call("GET","/profile",null,null,401);
 }

 @Test void teacherPasswordChangeInvalidatesExistingSessions()throws Exception {
  var teacher=actor("TEACHER");
  call("PUT","/profile/password",teacher.token(),Map.of("newPassword","Teacher-updated-456","confirmPassword","Teacher-updated-456"),200);
  call("GET","/auth/info",teacher.token(),null,401);
  call("POST","/auth/login",null,Map.of("username",teacher.username(),"password","Original-test-123"),400);
  assertTrue(call("POST","/auth/login",null,Map.of("username",teacher.username(),"password","Teacher-updated-456"),200).hasNonNull("token"));
 }
 @Test void profileEditsOnlySelfContactFieldsAndKeepsHistoricalNames()throws Exception {
  var owner=actor("STUDENT");var other=actor("STUDENT");
  jdbc.update("INSERT INTO business_history(biz_type,biz_id,actor_id,operation,after_json) VALUES ('REGISTRATION',999,?,'SUBMIT',?)",owner.id(),"{\"name\":\"历史姓名\"}");
  var body=new java.util.HashMap<String,Object>(Map.of("realName"," 新姓名 ","email","student@example.com","phone","13800138000","id",other.id(),"role","ADMIN","majorId",9999,"username","replacement","status",0));
  call("PUT","/student/profile",owner.token(),body,200);
  var actual=call("GET","/student/profile",owner.token(),null,200);
  assertEquals("新姓名",actual.path("realName").asText());assertEquals("student@example.com",actual.path("email").asText());assertEquals(owner.username(),actual.path("username").asText());
  assertEquals("STUDENT",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,owner.id()));assertNull(jdbc.queryForObject("SELECT major_id FROM sys_user WHERE id=?",Long.class,owner.id()));
  assertEquals("原姓名",jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=?",String.class,other.id()));
  assertTrue(jdbc.queryForObject("SELECT after_json FROM business_history WHERE actor_id=?",String.class,owner.id()).contains("历史姓名"));
  call("PUT","/student/profile",owner.token(),Map.of("realName","新姓名","email","","phone",""),200);
  assertEquals("",jdbc.queryForObject("SELECT email FROM sys_user WHERE id=?",String.class,owner.id()));
 }
 @Test void invalidProfileCannotBeSaved()throws Exception {
  var owner=actor("STUDENT");for(var body:java.util.List.of(Map.of("realName"," "),Map.of("realName","姓名","email","not-email"),Map.of("realName","姓名","phone","abc")))call("PUT","/student/profile",owner.token(),body,400);
  assertEquals("原姓名",jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=?",String.class,owner.id()));
 }
 @Test void passwordChangeRequiresValidMatchingNewPassword()throws Exception {
  var owner=actor("STUDENT");
  for(var body:java.util.List.of(Map.of("newPassword","Original-test-123","confirmPassword","Original-test-123"),Map.of("newPassword","short","confirmPassword","short"),Map.of("newPassword","Updated-test-456","confirmPassword","Mismatch-test-456"),Map.of("newPassword","密".repeat(25),"confirmPassword","密".repeat(25))))call("PUT","/student/profile/password",owner.token(),body,400);
  assertTrue(encoder.matches("Original-test-123",jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,owner.id())));
 }
 @Test void changedPasswordRevokesOldTokensButDoesNotAffectOtherStudents()throws Exception {
  var owner=actor("STUDENT");var other=actor("STUDENT");
  call("PUT","/student/profile/password",owner.token(),Map.of("newPassword","Updated-test-456","confirmPassword","Updated-test-456","id",other.id()),200);
  call("GET","/auth/info",owner.token(),null,401);call("GET","/student/profile",other.token(),null,200);
  call("POST","/auth/login",null,Map.of("username",owner.username(),"password","Original-test-123"),400);
  var login=call("POST","/auth/login",null,Map.of("username",owner.username(),"password","Updated-test-456"),200);
  call("GET","/student/profile",login.path("token").asText(),null,200);
  assertNotEquals("Updated-test-456",jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,owner.id()));
  assertEquals(1L,jdbc.queryForObject("SELECT token_version FROM sys_user WHERE id=?",Long.class,owner.id()));
  assertTrue(encoder.matches("Original-test-123",jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,other.id())));
 }
 @Test void passwordChangeRequiresAnActiveStudentSession()throws Exception {
  var body=Map.of("newPassword","Updated-test-456","confirmPassword","Updated-test-456");
  call("PUT","/student/profile/password",null,body,401);
  for(String role:new String[]{"ADMIN","MAJOR_ADMIN","TEACHER"}) {
   var actor=actor(role);call("PUT","/student/profile/password",actor.token(),body,403);
   assertTrue(encoder.matches("Original-test-123",jdbc.queryForObject("SELECT password FROM sys_user WHERE id=?",String.class,actor.id())));
  }
  var disabled=actor("STUDENT");jdbc.update("UPDATE sys_user SET status=0 WHERE id=?",disabled.id());
  call("PUT","/student/profile/password",disabled.token(),body,401);
 }
}
