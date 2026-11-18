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
class AdminPanelRefactorIntegrationTest extends IsolatedDatabaseTest {
 @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc; @Autowired JwtUtil jwt; @Autowired ObjectMapper json;
 long admin,majorAdmin,major,otherMajor;
 @BeforeEach void fixture(){major=major();otherMajor=major();admin=user("ADMIN",major);majorAdmin=user("MAJOR_ADMIN",major);}
 long major(){String tag="refactor_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_major(name) VALUES(?)",tag);return jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?",Long.class,tag);}
 long user(String role,long profession){String name="refactor_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES(?,'test-only',?,?,?,1)",name,name,role,profession);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
 JsonNode call(String method,String path,long actor,Object body,int code)throws Exception{var req=request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api"+path)).header("Authorization","Bearer "+jwt.generateToken(actor,"fixture",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,actor))).contentType("application/json");if(body!=null)req.content(json.writeValueAsBytes(body));var result=json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsByteArray());assertEquals(code,result.path("code").asInt(),path+" "+result.path("message"));return result.path("data");}
 Map<String,Object> student(String name,long profession,String classroom){return Map.of("username",name,"password","test-only-password","realName","班级验收学生","majorId",profession,"className",classroom,"status",1);}
 @Test void studentClassIsSavedAndFilteredWithinMajorScope()throws Exception{
  String name="class_"+UUID.randomUUID().toString().replace("-","");
  long id=call("POST","/student",admin,student(name,major,"2026计科1班"),200).asLong();
  call("POST","/student",admin,student(name+"x",otherMajor,"2026计科1班"),200);
  call("POST","/student",admin,student(name+"y",major,"2026计科2班"),200);
  var own=call("GET","/student/page?className=2026%E8%AE%A1%E7%A7%911%E7%8F%AD",majorAdmin,null,200);
  assertEquals(1,own.path("total").asInt());assertEquals("2026计科1班",own.path("records").get(0).path("className").asText());
  assertEquals(2,call("GET","/student/page?className=2026%E8%AE%A1%E7%A7%911%E7%8F%AD",admin,null,200).path("total").asInt());
  call("PUT","/student/"+id,majorAdmin,student(name,major,"2026计科3班"),200);
  assertEquals(0,call("GET","/student/page?className=2026%E8%AE%A1%E7%A7%911%E7%8F%AD",majorAdmin,null,200).path("total").asInt());
 }

 @Test void majorPresetIsValidatedSavedAndReadableOnlyByAuthorizedUsers()throws Exception{
  long id=call("POST","/major",admin,Map.of("name","logo_"+UUID.randomUUID(),"iconCode","Cpu"),200).asLong();
  assertEquals("Cpu",jdbc.queryForObject("SELECT icon_code FROM sys_major WHERE id=?",String.class,id));
  call("PUT","/major/"+id,majorAdmin,Map.of("name","forbidden","iconCode","Tools"),403);
  call("PUT","/major/"+id,admin,Map.of("name","invalid","iconCode","invalid"),400);
 }
 Map<String,Object> news(String title){var data=new HashMap<String,Object>();data.put("title",title);data.put("category","NEWS");data.put("officialUrl","https://example.com/competition");return data;}
 @Test void futureNewsIsScheduledAndExpiryIsEnforcedOnRead()throws Exception{
  var data=news("schedule_"+UUID.randomUUID());
  data.put("publishStartTime",java.time.LocalDateTime.now().plusHours(1).withNano(0).toString().replace('T',' '));
  data.put("publishEndTime",java.time.LocalDateTime.now().plusHours(2).withNano(0).toString().replace('T',' '));
  long id=call("POST","/content",admin,data,200).asLong();
  call("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  assertEquals("SCHEDULED",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
  long student=user("STUDENT",major);call("GET","/content/"+id,student,null,403);
  jdbc.update("UPDATE content SET status='PUBLISHED',publish_start_time=DATE_SUB(NOW(),INTERVAL 2 HOUR),publish_end_time=DATE_SUB(NOW(),INTERVAL 1 HOUR) WHERE id=?",id);
  call("GET","/content/"+id,student,null,403);
  assertEquals(0,call("GET","/content/page?keyword="+data.get("title"),student,null,200).path("total").asInt());
 }
 @Test void invalidNewsAndCarouselWindowsAreRejected()throws Exception{
  var data=news("invalid-window");data.put("publishStartTime","2027-01-02 00:00:00");data.put("publishEndTime","2027-01-01 00:00:00");
  call("POST","/content",admin,data,400);
  data=news("invalid-carousel");data.put("carouselStartTime","2027-01-02 00:00:00");data.put("carouselEndTime","2027-01-01 00:00:00");
  call("POST","/content",admin,data,400);
 }

 @Autowired com.cie.competition.module.content.service.ContentScheduleService schedule;
 long image(long actor,String kind)throws Exception{
  var file=new org.springframework.mock.web.MockMultipartFile("file","logo.png","image/png",Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg=="));
  var response=mvc.perform(multipart("/api/file/"+kind).file(file).header("Authorization","Bearer "+jwt.generateToken(actor,"fixture",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,actor)))).andReturn().getResponse();
  var body=json.readTree(response.getContentAsByteArray());assertEquals(200,body.path("code").asInt());return body.path("data").path("id").asLong();
 }
 @Test void customMajorLogoUsesPrivateFileServiceAndRoleChecks()throws Exception{
  long file=image(admin,"major-logo"),student=user("STUDENT",major);
  long id=call("POST","/major",admin,Map.of("name","custom_"+UUID.randomUUID(),"iconCode","Tools","logoFileId",file),200).asLong();
  var response=mvc.perform(get("/api/major/"+id+"/logo").header("Authorization","Bearer "+jwt.generateToken(student,"fixture","STUDENT"))).andReturn().getResponse();
  assertEquals(200,response.getStatus());assertEquals("image/png",response.getContentType());assertTrue(response.getContentAsByteArray().length>8);
  var fake=new org.springframework.mock.web.MockMultipartFile("file","fake.png","image/png","not-an-image".getBytes());
  var invalid=json.readTree(mvc.perform(multipart("/api/file/major-logo").file(fake).header("Authorization","Bearer "+jwt.generateToken(admin,"fixture","ADMIN"))).andReturn().getResponse().getContentAsByteArray());assertEquals(400,invalid.path("code").asInt());
  var forbidden=mvc.perform(multipart("/api/file/major-logo").file(fake).header("Authorization","Bearer "+jwt.generateToken(majorAdmin,"fixture","MAJOR_ADMIN"))).andReturn().getResponse();assertEquals(403,json.readTree(forbidden.getContentAsByteArray()).path("code").asInt());
  long wrong=image(admin,"content-cover");call("PUT","/major/"+id,admin,Map.of("name","wrong","logoFileId",wrong),400);
  call("PUT","/major/"+id,admin,Map.of("name","preserved","iconCode","Cpu"),200);assertEquals(id,jdbc.queryForObject("SELECT id FROM sys_major WHERE name='preserved'",Long.class));assertNull(jdbc.queryForObject("SELECT logo_file_id FROM sys_major WHERE id=?",Long.class,id));
 }
 @Test void carouselHasIndependentWindowAndOfflineNewsStopsIt()throws Exception{
  long cover=image(admin,"content-cover"),student=user("STUDENT",major);var data=news("carousel_"+UUID.randomUUID());data.put("coverFileId",cover);
  long id=call("POST","/content",admin,data,200).asLong();call("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  var config=new HashMap<String,Object>();config.put("carouselEnabled",true);config.put("sortOrder",9);config.put("carouselStartTime",future(1));config.put("carouselEndTime",future(2));
  call("PUT","/content/"+id+"/carousel",admin,config,200);assertFalse(has(call("GET","/content/carousel",student,null,200),id));
  config.put("carouselStartTime",null);config.put("carouselEndTime",null);call("PUT","/content/"+id+"/carousel",admin,config,200);assertTrue(has(call("GET","/content/carousel",student,null,200),id));
  call("PUT","/content/"+id+"/carousel",student,config,403);
  call("PUT","/content/"+id+"/status?status=DRAFT",admin,null,200);assertFalse(has(call("GET","/content/carousel",student,null,200),id));call("GET","/content/"+id+"/cover",student,null,403);
  call("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  jdbc.update("UPDATE content SET carousel_end_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",id);
  assertFalse(has(call("GET","/content/carousel",student,null,200),id));call("GET","/content/"+id,student,null,200);schedule.refresh();assertFalse(jdbc.queryForObject("SELECT carousel_enabled FROM content WHERE id=?",Boolean.class,id));
 }
 String future(int days){return java.time.LocalDateTime.now().plusDays(days).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));}
 boolean has(JsonNode list,long id){for(var row:list)if(row.path("id").asLong()==id)return true;return false;}
 @Test void schedulerPublishesDueNewsExpiresItAndDoesNotRepublishManuallyOfflineNews()throws Exception{
  var data=news("schedule-tick");data.put("publishStartTime",future(1));data.put("publishEndTime",future(2));long id=call("POST","/content",admin,data,200).asLong();
  call("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  jdbc.update("UPDATE content SET publish_start_time=DATE_SUB(NOW(),INTERVAL 1 HOUR),publish_end_time=DATE_ADD(NOW(),INTERVAL 1 HOUR) WHERE id=?",id);schedule.refresh();assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
  call("PUT","/content/"+id+"/status?status=DRAFT",admin,null,200);schedule.refresh();assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
  call("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);jdbc.update("UPDATE content SET publish_end_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",id);schedule.refresh();assertEquals("DRAFT",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));call("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,400);
 }
 @Test void quotaEditorRespectsOccupancyMajorScopeAndClosedCompetition()throws Exception{
  long teacher=user("TEACHER",major),other=user("TEACHER",otherMajor);
  jdbc.update("INSERT INTO competition(title,publisher_id,status) VALUES('quota-editor',?,'REGISTERING')",majorAdmin);long c=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  call("POST","/guidance/quota",majorAdmin,Map.of("competitionId",c,"teacherId",teacher,"quota",3),200);
  call("POST","/guidance/quota",majorAdmin,Map.of("competitionId",c,"teacherId",other,"quota",3),403);
  long student=user("STUDENT",major);jdbc.update("INSERT INTO registration(competition_id,leader_id,major_id,team_name,status) VALUES(?,?,?,'quota-team','APPROVED')",c,student,major);long r=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
  jdbc.update("INSERT INTO guidance(registration_id,competition_id,teacher_id,status,active_key) VALUES(?,?,?,'ACTIVE',1)",r,c,teacher);
  call("POST","/guidance/quota",majorAdmin,Map.of("competitionId",c,"teacherId",teacher,"quota",0),400);
  call("POST","/guidance/quota",majorAdmin,Map.of("competitionId",c,"teacherId",teacher,"quota",2),200);
  var list=call("GET","/guidance/teacher/"+teacher+"/quotas",majorAdmin,null,200);assertEquals(2,list.get(0).path("quota").asInt());assertEquals(1,list.get(0).path("assigned").asInt());assertEquals(1,list.get(0).path("remaining").asInt());
  jdbc.update("UPDATE competition SET status='ENDED' WHERE id=?",c);call("POST","/guidance/quota",admin,Map.of("competitionId",c,"teacherId",teacher,"quota",4),400);
 }
}
