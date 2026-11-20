package com.cie.competition;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.cie.competition.common.security.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.*;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(properties={"app.avatar-oss.enabled=true","app.avatar-oss.endpoint=https://oss-cn-hangzhou.aliyuncs.com","app.avatar-oss.region=cn-hangzhou","app.avatar-oss.bucket=avatar-tests"})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
class AvatarIntegrationTest extends IsolatedDatabaseTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired JwtUtil jwt; @Autowired ObjectMapper json; @Autowired PasswordEncoder encoder;
    @MockBean(name="avatarOssClient") OSS oss;
    long student, teacher, admin;
    @BeforeEach void fixture()throws Exception {
        student=user("STUDENT");teacher=user("TEACHER");admin=user("ADMIN");
        when(oss.generatePresignedUrl(anyString(),anyString(),any(Date.class))).thenAnswer(call->new URL("https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/"+call.getArgument(1)+"?test-signature=temporary"));
    }
    long user(String role){String name="avatar_"+UUID.randomUUID();jdbc.update("INSERT INTO sys_user(username,password,role,status) VALUES(?,?,?,1)",name,encoder.encode("test-avatar-password"),role);return jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?",Long.class,name);}
    String token(long actor){return jwt.generateToken(actor,"fixture",jdbc.queryForObject("SELECT role FROM sys_user WHERE id=?",String.class,actor));}
    byte[] image()throws Exception {var output=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(16,16,BufferedImage.TYPE_INT_RGB),"png",output);return output.toByteArray();}
    JsonNode upload(long actor,byte[] bytes,String filename,int expected)throws Exception {
        var result=json.readTree(mvc.perform(multipart("/api/avatar").file(new MockMultipartFile("file",filename,"image/png",bytes)).param("userId",Long.toString(teacher)).header("Authorization","Bearer "+token(actor))).andReturn().getResponse().getContentAsByteArray());
        assertEquals(expected,result.path("code").asInt(),result.path("message").asText());return result.path("data");
    }
    @Test void authenticatedUsersUploadOnlyTheirOwnAvatarAndStoreUnsignedOssUrl()throws Exception {
        for(long actor:List.of(student,teacher,admin)) {
            var result=upload(actor,image(),"avatar.png",200);
            String stored=jdbc.queryForObject("SELECT avatar_url FROM sys_user WHERE id=?",String.class,actor);
            assertTrue(stored.startsWith("https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/avatars/"+actor+"/"));assertFalse(stored.contains("?"));
            assertTrue(result.path("avatarUrl").asText().contains("test-signature=temporary"));
        }
        verify(oss,times(3)).putObject(any(PutObjectRequest.class));verify(oss,never()).deleteObject(anyString(),anyString());
    }
    @Test void invalidAndOversizedFilesNeverReachOss()throws Exception {
        upload(student,"<svg onload='alert(1)'/>".getBytes(),"avatar.png",400);
        upload(student,new byte[2*1024*1024+1],"large.png",400);
        upload(student,new byte[0],"empty.png",400);
        verify(oss,never()).putObject(any(PutObjectRequest.class));
    }
    @Test void ossFailureKeepsThePreviousAvatar()throws Exception {
        String old="https://avatar-tests.oss-cn-hangzhou.aliyuncs.com/avatars/"+student+"/old.png";
        jdbc.update("UPDATE sys_user SET avatar_url=? WHERE id=?",old,student);
        when(oss.putObject(any(PutObjectRequest.class))).thenThrow(new com.aliyun.oss.ClientException("simulated transport failure"));
        upload(student,image(),"avatar.png",503);assertEquals(old,jdbc.queryForObject("SELECT avatar_url FROM sys_user WHERE id=?",String.class,student));
    }
    @Test void loginAndProfileReturnFreshSignedUrlsWithoutChangingStoredAddress()throws Exception {
        upload(student,image(),"avatar.png",200);String stored=jdbc.queryForObject("SELECT avatar_url FROM sys_user WHERE id=?",String.class,student);
        for(String path:List.of("/api/auth/info","/api/profile")){
            var result=json.readTree(mvc.perform(get(path).header("Authorization","Bearer "+token(student))).andReturn().getResponse().getContentAsByteArray());
            assertEquals(200,result.path("code").asInt());assertTrue(result.path("data").path("avatarUrl").asText().contains("test-signature=temporary"));
        }
        String username=jdbc.queryForObject("SELECT username FROM sys_user WHERE id=?",String.class,student);
        var login=json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsBytes(Map.of("username",username,"password","test-avatar-password")))).andReturn().getResponse().getContentAsByteArray());
        assertEquals(200,login.path("code").asInt());assertTrue(login.path("data").path("user").path("avatarUrl").asText().contains("test-signature=temporary"));
        assertEquals(stored,jdbc.queryForObject("SELECT avatar_url FROM sys_user WHERE id=?",String.class,student));
    }
    @Test void anonymousUploadIsRejected()throws Exception {
        var response=mvc.perform(multipart("/api/avatar").file(new MockMultipartFile("file","avatar.png","image/png",image()))).andReturn().getResponse();
        assertEquals(401,json.readTree(response.getContentAsByteArray()).path("code").asInt());verify(oss,never()).putObject(any(PutObjectRequest.class));
    }
    @Test void nonMultipartRequestReturnsBadRequestInsteadOfServerError()throws Exception {
        var response=mvc.perform(post("/api/avatar").contentType("application/json").content("{}")
                .header("Authorization","Bearer "+token(student))).andReturn().getResponse();
        assertEquals(400,json.readTree(response.getContentAsByteArray()).path("code").asInt());verify(oss,never()).putObject(any(PutObjectRequest.class));
    }
}
