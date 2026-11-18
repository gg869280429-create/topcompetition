package com.cie.competition;
import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
@SpringBootTest @AutoConfigureMockMvc @Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class CompetitionOptionsIntegrationTest extends IsolatedDatabaseTest {
 @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JwtUtil jwt;
 long major,admin,teacher,student;
 @BeforeEach void fixture(){String tag="options_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_major(name) VALUES(?)",tag);major=jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,tag);admin=user("ADMIN");teacher=user("TEACHER");student=user("STUDENT");}
 long user(String role){String name="options_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES(?,'test-only',?,?,?,1)",name,name,role,major);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
 JsonNode call(String method,String path,long actor,Object body,int expected)throws Exception{var req=request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api"+path)).header("Authorization","Bearer "+jwt.generateToken(actor,"fixture",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,actor))).contentType("application/json");if(body!=null)req.content(json.writeValueAsBytes(body));var response=mvc.perform(req).andReturn().getResponse();var result=json.readTree(response.getContentAsByteArray());assertEquals(expected,result.path("code").asInt(),path+" "+result.path("message"));return result.path("data");}
 Map<String,Object> body(String type){var data=new HashMap<String,Object>();data.put("title","options_"+UUID.randomUUID());data.put("competitionType",type);data.put("allMajors",true);data.put("level","SCHOOL");data.put("maxMembers",3);data.put("maxTeachers",1);data.put("teachers",List.of(Map.of("teacherId",teacher,"quota",3)));var format=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");data.put("registrationStartTime",java.time.LocalDateTime.now().minusDays(1).format(format));data.put("registrationEndTime",java.time.LocalDateTime.now().plusDays(1).format(format));return data;}
 @Test void allMajorsCanPublishWithoutSpecificMajorAndDefaultsToCrossMajor()throws Exception{
  long id=call("POST","/competition",admin,body("TEAM"),200).asLong();call("PUT","/competition/"+id+"/status?status=REGISTERING",admin,null,200);
  var detail=call("GET","/competition/"+id,student,null,200);assertTrue(detail.path("allMajors").asBoolean());assertTrue(detail.path("majorId").isNull());assertEquals("全部专业",detail.path("majorName").asText());assertEquals(1,detail.path("allowCrossMajor").asInt());
 }
 @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
 void bothTypesCreateIndependentRecordsAndRollBackOnFailure()throws Exception{
  try {
  var first=body("TEAM");var second=body("PERSONAL");second.put("title",first.get("title"));
  var ids=call("POST","/competition/batch",admin,Map.of("competitions",List.of(first,second)),200);assertEquals(2,ids.size());assertNotEquals(ids.get(0).asLong(),ids.get(1).asLong());
  var team=call("GET","/competition/"+ids.get(0).asLong(),admin,null,200);var personal=call("GET","/competition/"+ids.get(1).asLong(),admin,null,200);assertEquals("TEAM",team.path("competitionType").asText());assertEquals(3,team.path("maxMembers").asInt());assertEquals("PERSONAL",personal.path("competitionType").asText());assertEquals(1,personal.path("maxMembers").asInt());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM guidance_quota WHERE competition_id IN (?,?) AND teacher_id=?",Integer.class,ids.get(0).asLong(),ids.get(1).asLong(),teacher));
  first=body("TEAM");second=body("PERSONAL");second.put("teachers",List.of(Map.of("teacherId",999999999,"quota",1)));int count=jdbc.queryForObject("SELECT COUNT(*) FROM competition",Integer.class);call("POST","/competition/batch",admin,Map.of("competitions",List.of(first,second)),400);assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM competition",Integer.class));
  } finally {
   jdbc.update("DELETE FROM business_history WHERE biz_type='COMPETITION' AND biz_id IN (SELECT id FROM competition WHERE publisher_id=?)",admin);
   jdbc.update("DELETE FROM guidance_quota WHERE competition_id IN (SELECT id FROM competition WHERE publisher_id=?)",admin);
   jdbc.update("DELETE FROM competition WHERE publisher_id=?",admin);
   jdbc.update("DELETE FROM sys_user WHERE id IN (?,?,?)",admin,teacher,student);jdbc.update("DELETE FROM sys_major WHERE id=?",major);
  }
 }

 @Test void legacyUnclassifiedIsNotAllMajorsAndClassificationCanBeChangedExplicitly()throws Exception{
  var data=body("TEAM");data.remove("allMajors");long id=call("POST","/competition",admin,data,200).asLong();
  call("PUT","/competition/"+id+"/status?status=REGISTERING",admin,null,400);assertFalse(call("GET","/competition/"+id,admin,null,200).path("allMajors").asBoolean());
  call("PUT","/competition/"+id+"/major?allMajors=true",admin,null,200);call("PUT","/competition/"+id+"/status?status=REGISTERING",admin,null,200);
  call("PUT","/competition/"+id+"/major?majorId="+major,admin,null,200);var detail=call("GET","/competition/"+id,student,null,200);assertFalse(detail.path("allMajors").asBoolean());assertEquals(major,detail.path("majorId").asLong());
  call("PUT","/competition/"+id+"/major?allMajors=true",admin,null,200);assertNull(jdbc.queryForObject("SELECT major_id FROM competition WHERE id=?",Long.class,id));
  call("PUT","/competition/"+id+"/major?allMajors=true&majorId="+major,admin,null,400);call("PUT","/competition/"+id+"/major?allMajors=true",student,null,403);
 }
 @Test void sameStudentCanRegisterBothFormatsAndQuotasAndStatesAreIndependent()throws Exception{
  var team=body("TEAM");var individual=body("PERSONAL");individual.put("title",team.get("title"));var ids=call("POST","/competition/batch",admin,Map.of("competitions",List.of(team,individual)),200);long a=ids.get(0).asLong(),b=ids.get(1).asLong();
  call("PUT","/competition/"+a+"/status?status=REGISTERING",admin,null,200);call("PUT","/competition/"+b+"/status?status=REGISTERING",admin,null,200);
  long ra=call("POST","/registration",student,Map.of("competitionId",a,"teamName","跨赛制团队","preferredTeacherId",teacher,"memberIds",List.of()),200).asLong();
  long rb=call("POST","/registration",student,Map.of("competitionId",b,"teamName","个人参赛","preferredTeacherId",teacher,"memberIds",List.of()),200).asLong();assertNotEquals(ra,rb);
  call("PUT","/registration/"+ra+"/review",admin,Map.of("result","APPROVED","teacherId",teacher),200);
  assertEquals(2,call("GET","/guidance/teachers?competitionId="+a,student,null,200).get(0).path("remaining").asInt());assertEquals(3,call("GET","/guidance/teachers?competitionId="+b,student,null,200).get(0).path("remaining").asInt());
  call("PUT","/competition/"+a+"/status?status=IN_PROGRESS",admin,null,200);assertEquals("REGISTERING",call("GET","/competition/"+b,student,null,200).path("status").asText());
  assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM registration_member WHERE user_id=? AND active_key=1",Integer.class,student));
 }
 @Test void invalidDuplicateAndUnauthorizedBatchesAreRejected()throws Exception{
  var data=body("TEAM");call("POST","/competition/batch",student,Map.of("competitions",List.of(data)),403);call("POST","/competition/batch",teacher,Map.of("competitions",List.of(data)),403);
  call("POST","/competition/batch",admin,Map.of("competitions",List.of(data,data)),400);call("POST","/competition/batch",admin,Map.of("competitions",List.of()),400);call("POST","/competition/batch",admin,Map.of("competitions",List.of(data,data,data)),400);
  data.put("allMajors",true);data.put("majorId",major);call("POST","/competition",admin,data,400);
 }
 @Test void oldCrossMajorSettingIsRetainedOnEditAndNewDefaultAllowsCrossMajor()throws Exception{
  var data=body("TEAM");data.put("allowCrossMajor",0);long id=call("POST","/competition",admin,data,200).asLong();data.put("description","修改简介");call("PUT","/competition/"+id,admin,data,200);assertEquals(0,jdbc.queryForObject("SELECT allow_cross_major FROM competition WHERE id=?",Integer.class,id));
  long newId=call("POST","/competition",admin,body("TEAM"),200).asLong();assertEquals(1,jdbc.queryForObject("SELECT allow_cross_major FROM competition WHERE id=?",Integer.class,newId));
 }

 @Test void defaultCrossMajorTeamKeepsLeaderMajorForReview()throws Exception{
  String name="other_options_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_major(name) VALUES(?)",name);long otherMajor=jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,name);
  long member=user("STUDENT"),manager=user("MAJOR_ADMIN"),otherManager=user("MAJOR_ADMIN");jdbc.update("UPDATE sys_user SET major_id=? WHERE id IN (?,?)",otherMajor,member,otherManager);
  long c=call("POST","/competition",admin,body("TEAM"),200).asLong();call("PUT","/competition/"+c+"/status?status=REGISTERING",admin,null,200);
  long r=call("POST","/registration",student,Map.of("competitionId",c,"teamName","默认跨专业团队","preferredTeacherId",teacher,"memberIds",List.of(member)),200).asLong();
  long invitation=jdbc.queryForObject("SELECT id FROM team_invitation WHERE registration_id=? AND invitee_id=?",Long.class,r,member);call("PUT","/registration/invitations/"+invitation+"/response",member,Map.of("accepted",true),200);call("POST","/registration/"+r+"/submit",student,null,200);
  assertEquals(major,jdbc.queryForObject("SELECT major_id FROM registration WHERE id=?",Long.class,r));assertEquals(1,jdbc.queryForObject("SELECT cross_major FROM registration WHERE id=?",Integer.class,r));
  call("PUT","/registration/"+r+"/review",otherManager,Map.of("result","APPROVED","teacherId",teacher),403);call("PUT","/registration/"+r+"/review",manager,Map.of("result","APPROVED","teacherId",teacher),200);
 }
}
