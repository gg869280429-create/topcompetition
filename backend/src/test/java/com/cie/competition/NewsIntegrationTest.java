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
}
