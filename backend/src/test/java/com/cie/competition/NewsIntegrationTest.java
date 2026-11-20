package com.cie.competition;
import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"spring.main.banner-mode=off","logging.level.org.springframework.boot.autoconfigure.security=ERROR","app.upload-directory=${java.io.tmpdir}/competition-news-tests"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class NewsIntegrationTest extends IsolatedDatabaseTest {
 @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired JwtUtil jwt; @Autowired ObjectMapper json;
 List<String> createdFiles=new ArrayList<>();
 String token(String role){String name="news_"+UUID.randomUUID().toString().replace("-","");jdbc.update("INSERT INTO sys_user(username,password,role,status) VALUES(?,'test-only',?,1)",name,role);Long id=jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);return jwt.generateToken(id,name,role);}
 JsonNode request(String method,String path,String token,Object body,int expected)throws Exception{
  var req=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(org.springframework.http.HttpMethod.valueOf(method),java.net.URI.create("/api"+path)).header("Authorization","Bearer "+token).contentType("application/json");
  if(body!=null)req.content(json.writeValueAsBytes(body));
  var result=json.readTree(mvc.perform(req).andReturn().getResponse().getContentAsString());assertEquals(expected,result.path("code").asInt(),method+path);return result.path("data");
 }
 @AfterEach void cleanup()throws Exception{for(String name:createdFiles)java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"competition-news-tests",name));}
 @Test void publishedCarouselAndProtectedCoverFollowNewsLifecycle()throws Exception{
  String admin=token("ADMIN"),student=token("STUDENT");
  byte[] image=new byte[]{(byte)137,80,78,71,13,10,26,10};
  var uploaded=json.readTree(mvc.perform(multipart("/api/file/content-cover").file(new MockMultipartFile("file","cover.png","image/png",image)).header("Authorization","Bearer "+admin)).andReturn().getResponse().getContentAsString());
  assertEquals(200,uploaded.path("code").asInt());long file=uploaded.path("data").path("id").asLong();createdFiles.add(jdbc.queryForObject("SELECT storage_name FROM sys_file WHERE id=?",String.class,file));
  var draft=new HashMap<String,Object>(Map.of("title","News lifecycle","content","News body","category","NEWS","coverFileId",file,"carouselEnabled",true,"sortOrder",2,"officialUrl","https://example.org/competition"));
  long id=request("POST","/content",admin,draft,200).asLong();
  request("GET","/content/"+id+"/cover",student,null,403);
  assertTrue(request("GET","/content/carousel",student,null,200).isEmpty());
  request("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  assertEquals(id,request("GET","/content/carousel",student,null,200).get(0).path("id").asLong());
  assertEquals("https://example.org/competition",request("GET","/content/"+id,student,null,200).path("officialUrl").asText());
  var cover=mvc.perform(get("/api/content/"+id+"/cover").header("Authorization","Bearer "+student)).andReturn().getResponse();assertEquals("image/png",cover.getContentType());assertArrayEquals(image,cover.getContentAsByteArray());
  request("PUT","/content/"+id+"/status?status=DRAFT",admin,null,200);
  assertTrue(request("GET","/content/carousel",student,null,200).isEmpty());
  draft.put("carouselEnabled",false);draft.put("coverFileId",null);draft.put("officialUrl",null);
  request("PUT","/content/"+id,admin,draft,200);
  assertNull(jdbc.queryForObject("SELECT cover_file_id FROM content WHERE id=?",Long.class,id));
  request("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,400);
  request("DELETE","/content/"+id,student,null,403);
  request("DELETE","/content/"+id,admin,null,200);
 }
 @Test void teacherUploadsMaterialsButCoverAndForgedContentRemainRestricted()throws Exception{
  String teacher=token("TEACHER");
  var forbidden=json.readTree(mvc.perform(multipart("/api/file/content-cover").file(new MockMultipartFile("file","cover.png","image/png",new byte[]{1})).header("Authorization","Bearer "+teacher)).andReturn().getResponse().getContentAsString());assertEquals(403,forbidden.path("code").asInt());
  for(String type:List.of("AWARD_PROCESS_PHOTO","AWARD_REGISTRATION_SCREENSHOT")){
   var result=json.readTree(mvc.perform(multipart("/api/file/award-material").file(new MockMultipartFile("file","proof.pdf","application/pdf","%PDF-test".getBytes())).param("bizType",type).header("Authorization","Bearer "+teacher)).andReturn().getResponse().getContentAsString());assertEquals(400,result.path("code").asInt());
  }
 }
 long uploadCover(String admin,String name)throws Exception{
  byte[] image=java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5WQAAAAASUVORK5CYII=");
  var result=json.readTree(mvc.perform(multipart("/api/file/content-cover").file(new MockMultipartFile("file",name,"image/png",image)).header("Authorization","Bearer "+admin)).andReturn().getResponse().getContentAsString());
  assertEquals(200,result.path("code").asInt());long id=result.path("data").path("id").asLong();createdFiles.add(jdbc.queryForObject("SELECT storage_name FROM sys_file WHERE id=?",String.class,id));return id;
 }
 @Test void publishedNewsCanReplaceHomepageCoverUsingTheNewsUpdateEndpoint()throws Exception{
  String admin=token("ADMIN"),student=token("STUDENT"),teacher=token("TEACHER");
  long original=uploadCover(admin,"original.png"),replacement=uploadCover(admin,"replacement.png");
  var body=new HashMap<String,Object>(Map.of("title","Replace homepage cover","category","NEWS","officialUrl","https://example.org/contest","carouselEnabled",true,"coverFileId",original));
  long id=request("POST","/content",admin,body,200).asLong();request("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  var publishTime=jdbc.queryForObject("SELECT publish_time FROM content WHERE id=?",java.sql.Timestamp.class,id);
  body.put("coverFileId",replacement);body.put("sortOrder",3);
  request("PUT","/content/"+id,admin,body,200);
  assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
  assertEquals(publishTime,jdbc.queryForObject("SELECT publish_time FROM content WHERE id=?",java.sql.Timestamp.class,id));
  assertEquals(replacement,request("GET","/content/"+id,student,null,200).path("coverFileId").asLong());
  assertTrue(java.util.stream.StreamSupport.stream(request("GET","/content/carousel",student,null,200).spliterator(),false).anyMatch(n->n.path("id").asLong()==id&&n.path("coverFileId").asLong()==replacement));
  assertEquals("image/png",mvc.perform(get("/api/content/"+id+"/cover").header("Authorization","Bearer "+student)).andReturn().getResponse().getContentType());
  body.put("coverFileId",original);request("PUT","/content/"+id,teacher,body,403);
  body.put("officialUrl","");request("PUT","/content/"+id,admin,body,400);
  body.put("officialUrl","https://example.org/contest");body.put("coverFileId",null);request("PUT","/content/"+id,admin,body,400);
  assertEquals(replacement,jdbc.queryForObject("SELECT cover_file_id FROM content WHERE id=?",Long.class,id));
 }
 @Test void editingActiveNewsRevalidatesPublicationWindows()throws Exception{
  String admin=token("ADMIN"),student=token("STUDENT");
  var body=new HashMap<String,Object>(Map.of("title","Scheduled update","category","NEWS","officialUrl","https://example.org/contest"));
  long id=request("POST","/content",admin,body,200).asLong();request("PUT","/content/"+id+"/status?status=PUBLISHED",admin,null,200);
  var format=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  body.put("publishStartTime",java.time.LocalDateTime.now().plusDays(1).format(format));
  request("PUT","/content/"+id,admin,body,200);assertEquals("SCHEDULED",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
  request("GET","/content/"+id,student,null,403);
  body.put("title","Rescheduled news");request("PUT","/content/"+id,admin,body,200);
  body.put("publishStartTime",null);body.put("publishEndTime",java.time.LocalDateTime.now().minusMinutes(1).format(format));
  request("PUT","/content/"+id,admin,body,400);assertEquals("SCHEDULED",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
  body.put("publishEndTime",null);request("PUT","/content/"+id,admin,body,200);assertEquals("PUBLISHED",jdbc.queryForObject("SELECT status FROM content WHERE id=?",String.class,id));
 }
}
