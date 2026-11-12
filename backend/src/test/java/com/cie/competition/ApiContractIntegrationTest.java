package com.cie.competition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.banner-mode=off", "logging.level.org.springframework.boot.autoconfigure.security=ERROR", "app.upload-directory=${java.io.tmpdir}/competition-api-contract-tests"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@Transactional
class ApiContractIntegrationTest extends IsolatedDatabaseTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired com.cie.competition.module.user.mapper.UserMapper userMapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;

    private String login(String role) throws Exception {
        String name = "api_contract_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_major(name) VALUES (?)", name);
        Long major = jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?", Long.class, name);
        jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES (?,?,?,?,?,1)",
                name, encoder.encode("contract-test-only"), name, role, major);
        var result = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("username", name, "password", "contract-test-only"))))
                .andReturn().getResponse();
        var body = json.readTree(result.getContentAsString());
        assertEquals(200, body.path("code").asInt(), "Real login must succeed");
        return body.path("data").path("token").asText();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "MAJOR_ADMIN", "STUDENT", "TEACHER", "ANONYMOUS"})
    void everyMappedEndpointHandlesRoleAndInvalidInputWithoutServerErrors(String role) throws Exception {
        String token = role.equals("ANONYMOUS") ? null : login(role);
        var failures = new ArrayList<String>();
        var results = new ArrayList<Map<String,Object>>();
        int checked = 0;
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.cie.competition.module")) continue;
            for (String pattern : entry.getKey().getPatternValues()) {
                if (pattern.equals("/api/auth/login")) continue;
                for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                    String path = pattern.replaceAll("\\{[^}]+}", "999999999");
                    var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method.name()), URI.create(path))
                            .contentType(MediaType.APPLICATION_JSON).content("{}");
                    if (token != null) request.header("Authorization", "Bearer " + token);
                    var response = mvc.perform(request).andReturn().getResponse();
                    int code = json.readTree(response.getContentAsString()).path("code").asInt(response.getStatus());
                    results.add(Map.of("role",role,"method",method.name(),"path",pattern,"httpStatus",response.getStatus(),"businessCode",code));
                    if (code >= 500 || response.getStatus() >= 500 || (token == null && code != 401))
                        failures.add(method + " " + pattern + " -> " + code);
                    checked++;
                }
            }
        }
        assertTrue(checked >= 60, "All controller mappings must be included");
        assertTrue(failures.isEmpty(), role + " endpoint errors: " + failures);
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("../output"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("../output/feature-api-"+role+".json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(results));
        System.out.println("API contract: " + role + " checked " + checked + " mappings");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/competition/not-a-number", "/api/competition/page?current=abc", "/api/guidance/list", "/api/teacher/page?current=abc"})
    void malformedParametersReturnBadRequest(String path) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + login("ADMIN"))).andReturn().getResponse();
        assertEquals(400, json.readTree(response.getContentAsString()).path("code").asInt());
    }

    private com.fasterxml.jackson.databind.JsonNode call(String method, String path, String token, Object body, int expected) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), URI.create("/api" + path))
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
        if (body != null) request.content(json.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        var result = json.readTree(response.getContentAsByteArray());
        assertEquals(expected, result.path("code").asInt(), method + " " + path);
        return result.path("data");
    }

    @Test
    void contentPublishReadUnpublishEditAndDeleteThroughHttp() throws Exception {
        String admin = login("ADMIN"), student = login("STUDENT");
        var body = Map.of("title", "API lifecycle notice", "category", "NOTICE", "content", "Initial text");
        long id = call("POST", "/content", admin, body, 200).asLong();
        call("GET", "/content/" + id, student, null, 403);
        call("PUT", "/content/" + id, admin, body, 200);
        call("PUT", "/content/" + id + "/status?status=PUBLISHED", admin, null, 200);
        assertEquals("Initial text", call("GET", "/content/" + id, student, null, 200).path("content").asText());
        call("PUT", "/content/" + id, admin, body, 400);
        call("PUT", "/content/" + id + "/status?status=DRAFT", admin, null, 200);
        call("GET", "/content/" + id, student, null, 403);
        call("DELETE", "/content/" + id, admin, null, 200);
        call("GET", "/content/" + id, admin, null, 404);
    }

    @Test
    void messageDeliveryReadAndDeleteAreRestrictedToReceiver() throws Exception {
        String admin = login("ADMIN"), student = login("STUDENT"), other = login("STUDENT");
        long receiver = call("GET", "/auth/info", student, null, 200).path("id").asLong();
        long id = call("POST", "/message", admin, Map.of("receiverId", receiver, "title", "Test message", "content", "Test body"), 200).asLong();
        assertEquals(1, call("GET", "/message/unread-count", student, null, 200).asInt());
        call("GET", "/message/" + id, other, null, 403);
        call("DELETE", "/message/" + id, other, null, 403);
        call("PUT", "/message/" + id + "/read", student, null, 200);
        assertEquals(0, call("GET", "/message/unread-count", student, null, 200).asInt());
        call("GET", "/message/" + id, student, null, 200);
        call("PUT", "/message/read-all", student, null, 200);
        call("DELETE", "/message/" + id, student, null, 200);
        call("GET", "/message/" + id, student, null, 404);
    }

    @Test
    void teacherCreationEditAndDisabledLoginThroughHttp() throws Exception {
        String admin = login("ADMIN");
        long major = call("GET", "/auth/info", admin, null, 200).path("majorId").asLong();
        String name = "teacher_api_" + UUID.randomUUID().toString().replace("-", "");
        var body = new java.util.HashMap<String, Object>(Map.of("username", name, "password", "contract-test-only", "realName", "Test teacher", "majorId", major, "status", 1));
        long id = call("POST", "/teacher", admin, body, 200).asLong();
        body.put("status", 0);
        call("PUT", "/teacher/" + id, admin, body, 200);
        call("POST", "/auth/login", admin, Map.of("username", name, "password", "contract-test-only"), 403);
    }

    private record Actor(String token, long id, long major) {}

    private Actor actor(String role) throws Exception {
        String token = login(role);
        var info = call("GET", "/auth/info", token, null, 200);
        return new Actor(token, info.path("id").asLong(), info.path("majorId").asLong());
    }

    private Map<String, Object> competitionBody(long teacher, Long file) {
        var body = new java.util.HashMap<String, Object>();
        body.put("title", "HTTP business flow " + UUID.randomUUID());
        body.put("majorId", jdbc.queryForObject("SELECT major_id FROM sys_user WHERE id=?",Long.class,teacher));
        body.put("competitionType", "TEAM"); body.put("level", "SCHOOL");
        body.put("maxMembers", 3); body.put("maxTeachers", 1); body.put("allowCrossMajor", 1);
        body.put("registrationStartTime", java.time.LocalDateTime.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("registrationEndTime", java.time.LocalDateTime.now().plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("teachers", java.util.List.of(Map.of("teacherId", teacher, "quota", 3)));
        if (file != null) body.put("documentFileId", file);
        return body;
    }

    private long upload(String path, String token, byte[] bytes) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.multipart("/api" + path)
                .file(new org.springframework.mock.web.MockMultipartFile("file", "proof.pdf", "application/pdf", bytes))
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        var result = json.readTree(response.getContentAsString());
        assertEquals(200, result.path("code").asInt(), "Upload must persist a file reference");
        return result.path("data").path("id").asLong();
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competitionPublicationCanPublishOneNewsAndRejectDuplicatePublication() throws Exception {
        var admin = actor("ADMIN"); var teacher = actor("TEACHER"); var student = actor("STUDENT");
        long cid = call("POST", "/competition", admin.token(), competitionBody(teacher.id(), null), 200).asLong();
        var news = Map.of("title", "同步发布新闻", "content", "竞赛详细说明", "category", "NEWS", "officialUrl", "https://example.org/contest");
        var payload = Map.of("syncNews", true, "news", news);
        long newsId = call("POST", "/competition/" + cid + "/publish", admin.token(), payload, 200).asLong();
        assertEquals("REGISTERING", call("GET", "/competition/" + cid, student.token(), null, 200).path("status").asText());
        var published = call("GET", "/content/" + newsId, student.token(), null, 200);
        assertEquals("PUBLISHED", published.path("status").asText());
        assertEquals("竞赛详细说明", published.path("content").asText());
        assertEquals("https://example.org/contest", published.path("officialUrl").asText());
        assertEquals(admin.id(), jdbc.queryForObject("SELECT author_id FROM content WHERE id=?", Long.class, newsId));
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM content", Long.class);
        call("POST", "/competition/" + cid + "/publish", admin.token(), payload, 400);
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM content", Long.class));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competitionPublicationWithoutNewsKeepsContentUnchanged() throws Exception {
        var admin = actor("ADMIN"); var teacher = actor("TEACHER");
        long cid = call("POST", "/competition", admin.token(), competitionBody(teacher.id(), null), 200).asLong();
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM content", Long.class);
        call("POST", "/competition/" + cid + "/publish", admin.token(), Map.of("syncNews", false), 200);
        assertEquals("REGISTERING", jdbc.queryForObject("SELECT status FROM competition WHERE id=?", String.class, cid));
        assertEquals(count, jdbc.queryForObject("SELECT COUNT(*) FROM content", Long.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "javascript:alert(1)", "https://", "https://user:password@example.org"})
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void invalidNewsRollsBackCompetitionAndNewsPublication(String url) throws Exception {
        var admin = actor("ADMIN"); var teacher = actor("TEACHER");
        long cid = call("POST", "/competition", admin.token(), competitionBody(teacher.id(), null), 200).asLong();
        long contentCount = jdbc.queryForObject("SELECT COUNT(*) FROM content", Long.class);
        long historyCount = jdbc.queryForObject("SELECT COUNT(*) FROM business_history", Long.class);
        var news = Map.of("title", "无效官网", "category", "NEWS", "officialUrl", url);
        call("POST", "/competition/" + cid + "/publish", admin.token(), Map.of("syncNews", true, "news", news), 400);
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM competition WHERE id=?", String.class, cid));
        assertEquals(contentCount, jdbc.queryForObject("SELECT COUNT(*) FROM content", Long.class));
        assertEquals(historyCount, jdbc.queryForObject("SELECT COUNT(*) FROM business_history", Long.class));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void competitionPublicationRequiresOwnerAndNewsInformation() throws Exception {
        var admin = actor("ADMIN"); var teacher = actor("TEACHER"); var student = actor("STUDENT"); var otherAdmin = actor("MAJOR_ADMIN");
        long cid = call("POST", "/competition", admin.token(), competitionBody(teacher.id(), null), 200).asLong();
        String path = "/competition/" + cid + "/publish";
        call("POST", path, student.token(), Map.of("syncNews", false), 403);
        call("POST", path, teacher.token(), Map.of("syncNews", false), 403);
        call("POST", path, otherAdmin.token(), Map.of("syncNews", false), 403);
        call("POST", path, admin.token(), Map.of("syncNews", true), 400);
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM competition WHERE id=?", String.class, cid));
    }

    private void download(String path, String token, byte[] expected) throws Exception {
        var response = mvc.perform(MockMvcRequestBuilders.get("/api" + path)
                .header("Authorization", "Bearer " + token)).andReturn().getResponse();
        assertEquals(200, response.getStatus(), path);
        assertArrayEquals(expected, response.getContentAsByteArray(), path);
        assertNotNull(response.getHeader("Content-Disposition"));
    }

    private long uploadImage(String token, String type) throws Exception {
        byte[] bytes = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a5WQAAAAASUVORK5CYII=");
        var response=mvc.perform(MockMvcRequestBuilders.multipart("/api/file/award-material")
                .file(new org.springframework.mock.web.MockMultipartFile("file","photo.png","image/png",bytes))
                .param("bizType",type)
                .header("Authorization","Bearer "+token)).andReturn().getResponse();
        var result=json.readTree(response.getContentAsString());assertEquals(200,result.path("code").asInt());
        return result.path("data").path("id").asLong();
    }

    private long invitation(String member, long registration) throws Exception {
        for (var invite : call("GET", "/registration/invitations", member, null, 200))
            if (invite.path("registrationId").asLong() == registration && invite.path("status").asText().equals("PENDING"))
                return invite.path("id").asLong();
        fail("Pending member invitation must be delivered"); return 0;
    }

    @Test
    void competitionTeamReviewGuidanceAwardWithdrawalAndReapplicationThroughHttp() throws Exception {
        var admin = actor("ADMIN"); var teacher = actor("TEACHER"); var replacement = actor("TEACHER");
        var captain = actor("STUDENT"); var member = actor("STUDENT"); var outsider = actor("STUDENT");
        var majorAdmin = actor("MAJOR_ADMIN");
        byte[] document = "%PDF-1.4\nbackend business acceptance\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        long documentId = upload("/file", admin.token(), document);
        var competition = competitionBody(teacher.id(), documentId);
        long cid = call("POST", "/competition", admin.token(), competition, 200).asLong();
        String cp = "/competition/" + cid;
        call("GET", cp, captain.token(), null, 404);
        call("PUT", cp, admin.token(), competition, 200);
        call("PUT", cp + "/status?status=REGISTERING", admin.token(), null, 200);
        call("GET", cp, captain.token(), null, 200);
        download(cp + "/document", captain.token(), document);
        assertEquals(1, call("GET", "/guidance/teachers?competitionId=" + cid, captain.token(), null, 200).size());
        long extraQuota = call("POST", "/guidance/quota", admin.token(), Map.of("competitionId", cid, "teacherId", replacement.id(), "quota", 2), 200).asLong();
        call("GET", "/guidance/quota/page?competitionId=" + cid, admin.token(), null, 200);
        call("GET", "/guidance/quota/mine", teacher.token(), null, 200);

        var enrollment = Map.of("competitionId", cid, "teamName", "HTTP team", "preferredTeacherId", teacher.id(), "memberIds", java.util.List.of(member.id()));
        long rid = call("POST", "/registration", captain.token(), enrollment, 200).asLong();
        String rp = "/registration/" + rid;
        call("POST", rp + "/submit", captain.token(), null, 400);
        call("GET", rp, member.token(), null, 403);
        call("GET", rp, outsider.token(), null, 403);
        long invite = invitation(member.token(), rid);
        call("PUT", "/registration/invitations/" + invite + "/response", member.token(), Map.of("accepted", false), 200);
        call("PUT", rp, captain.token(), enrollment, 200);
        invite = invitation(member.token(), rid);
        call("DELETE", "/registration/invitations/" + invite, captain.token(), null, 200);
        call("PUT", rp, captain.token(), enrollment, 200);
        invite = invitation(member.token(), rid);
        call("PUT", "/registration/invitations/" + invite + "/response", member.token(), Map.of("accepted", true), 200);
        call("POST", rp + "/submit", captain.token(), null, 200);
        call("GET", "/registration/mine", member.token(), null, 200);
        call("PUT", rp + "/review", majorAdmin.token(), Map.of("result", "REJECTED", "comment", "Foreign major"), 403);
        call("PUT", rp + "/review", admin.token(), Map.of("result", "REJECTED", "comment", "Please correct the application"), 200);
        call("PUT", rp, captain.token(), enrollment, 200);
        call("POST", rp + "/submit", captain.token(), null, 200);
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?", captain.major(), majorAdmin.id());
        call("PUT", rp + "/review", majorAdmin.token(), Map.of("result", "APPROVED", "teacherId", teacher.id()), 200);
        assertEquals("APPROVED", call("GET", rp, captain.token(), null, 200).path("status").asText());
        call("GET", rp, teacher.token(), null, 200);
        call("GET", "/guidance/mine", teacher.token(), null, 200);
        var guide = call("GET", "/guidance/list?registrationId=" + rid, captain.token(), null, 200);
        assertEquals(1, guide.size());
        long gid = guide.get(0).path("id").asLong();
        call("POST", "/guidance", admin.token(), Map.of("registrationId", rid, "teacherId", replacement.id(), "comment", "Additional teacher"), 400);
        call("PUT", "/guidance/" + gid, admin.token(), Map.of("registrationId", rid, "teacherId", replacement.id(), "comment", "Specialty match"), 200);
        call("GET", rp, teacher.token(), null, 403);
        call("GET", rp, replacement.token(), null, 200);
        call("DELETE", "/guidance/quota/" + extraQuota, admin.token(), null, 400);
        call("DELETE", cp, admin.token(), null, 400);
        call("PUT", rp, captain.token(), enrollment, 400);

        long proof = upload("/file/award-certificate", admin.token(), document);
        var awardBody = new java.util.HashMap<String, Object>(Map.of("registrationId", rid, "awardLevel", "FIRST", "awardTime", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), "certificateFileId", proof));
        awardBody.put("achievementName", "HTTP competition result");
        awardBody.put("studentIds", java.util.List.of(captain.id(), member.id()));
        awardBody.put("teacherIds", java.util.List.of(replacement.id()));
        awardBody.put("registrationScreenshotFileId", uploadImage(admin.token(), "AWARD_REGISTRATION_SCREENSHOT"));
        awardBody.put("processPhotoFileIds", java.util.List.of(uploadImage(admin.token(), "AWARD_PROCESS_PHOTO"), uploadImage(admin.token(), "AWARD_PROCESS_PHOTO")));
        long aid = call("POST", "/award", admin.token(), awardBody, 200).asLong();
        String ap = "/award/" + aid;
        call("POST", "/award", admin.token(), awardBody, 400);
        call("GET", ap, outsider.token(), null, 403);
        call("PUT", ap + "/review", admin.token(), Map.of("result", "REJECTED", "comment", "Revise certificate"), 200);
        call("PUT", ap, admin.token(), awardBody, 200);
        call("PUT", ap + "/review", admin.token(), Map.of("result", "APPROVED"), 200);
        download(ap + "/certificate", member.token(), document);
        call("GET", ap + "/certificate", outsider.token(), null, 403);
        call("DELETE", ap, admin.token(), null, 400);
        call("PUT", ap, admin.token(), awardBody, 400);
        awardBody.put("awardLevel", "SECOND"); awardBody.put("reason", "Correct award level");
        call("PUT", ap + "/correction", admin.token(), awardBody, 200);
        call("PUT", ap + "/review", admin.token(), Map.of("result", "APPROVED"), 200);
        long withdrawal = call("POST", rp + "/withdrawals", captain.token(), Map.of("reason", "Scheduling conflict"), 200).asLong();
        call("PUT", "/registration/withdrawals/" + withdrawal + "/review", admin.token(), Map.of("result", "APPROVED"), 400);
        assertEquals(1, call("GET", "/statistics/overview", majorAdmin.token(), null, 200).path("awardCount").asInt());
        call("PUT", ap + "/revoke", admin.token(), Map.of("reason", "Evidence withdrawn"), 200);
        assertEquals(0, call("GET", "/statistics/overview", majorAdmin.token(), null, 200).path("awardCount").asInt());
        call("PUT", "/registration/withdrawals/" + withdrawal + "/review", majorAdmin.token(), Map.of("result", "APPROVED"), 200);
        call("PUT", "/registration/withdrawals/" + withdrawal + "/review", majorAdmin.token(), Map.of("result", "APPROVED"), 200);
        assertEquals("WITHDRAWN", call("GET", rp, captain.token(), null, 200).path("status").asText());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ACTIVE'", Integer.class, rid));
        assertTrue(call("GET", ap + "/history", captain.token(), null, 200).toString().contains("Correct award level"));
        assertTrue(call("GET", ap + "/history", admin.token(), null, 200).toString().contains("Correct award level"));
        assertTrue(call("GET", rp + "/history", captain.token(), null, 200).toString().contains("Please correct the application"));
        call("POST", rp + "/reapply", captain.token(), Map.of("reason", "New availability"), 200);
        call("POST", rp + "/submit", captain.token(), null, 400);
        invite = invitation(member.token(), rid);
        call("PUT", "/registration/invitations/" + invite + "/response", member.token(), Map.of("accepted", true), 200);
        call("POST", rp + "/submit", captain.token(), null, 200);
        call("PUT", rp + "/review", admin.token(), Map.of("result", "APPROVED", "teacherId", teacher.id()), 200);
        call("POST", rp + "/adjustment", captain.token(), Map.of("reason", "Team restructuring"), 200);
        call("POST", rp + "/submit", captain.token(), null, 400);
        call("PUT", rp + "/members", captain.token(), Map.of("memberIds", java.util.List.of(), "reason", "Captain adjusts roster"), 200);
        assertEquals("APPROVED", call("GET", rp, captain.token(), null, 200).path("status").asText());
        var retainedGuidance=call("GET", "/guidance/list?registrationId="+rid,captain.token(),null,200);
        assertEquals(1,java.util.stream.StreamSupport.stream(retainedGuidance.spliterator(),false).filter(g -> "ACTIVE".equals(g.path("status").asText())).count());
        long finalWithdrawal=call("POST",rp+"/withdrawals",captain.token(),Map.of("reason","Leave after roster adjustment"),200).asLong();
        call("PUT","/registration/withdrawals/"+finalWithdrawal+"/review",admin.token(),Map.of("result","APPROVED"),200);
        assertEquals("WITHDRAWN", call("GET", rp, captain.token(), null, 200).path("status").asText());
        call("DELETE", "/guidance/quota/" + extraQuota, admin.token(), null, 200);
        assertTrue(call("GET", "/message/mine", member.token(), null, 200).path("total").asLong() > 0);
    }

    @Test
    void personalCompetitionTimeWindowCancellationAndTerminalStatesThroughHttp() throws Exception {
        var admin = actor("ADMIN"); var teacher = actor("TEACHER"); var captain = actor("STUDENT"); var member = actor("STUDENT");
        var body = competitionBody(teacher.id(), null);
        long disposable = call("POST", "/competition", admin.token(), body, 200).asLong();
        call("DELETE", "/competition/" + disposable, admin.token(), null, 200);
        body.put("competitionType", "PERSONAL"); body.put("maxMembers", 1); body.put("allowCrossMajor", 0);
        body.put("registrationStartTime", java.time.LocalDateTime.now().plusHours(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        long cid = call("POST", "/competition", admin.token(), body, 200).asLong();
        String cp = "/competition/" + cid;
        call("PUT", cp + "/status?status=REGISTERING", admin.token(), null, 200);
        var enrollment = new java.util.HashMap<String, Object>(Map.of("competitionId", cid, "teamName", "Personal entrant", "preferredTeacherId", teacher.id(), "memberIds", java.util.List.of()));
        call("POST", "/registration", captain.token(), enrollment, 400);
        body.put("registrationStartTime", java.time.LocalDateTime.now().minusHours(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("registrationEndTime", java.time.LocalDateTime.now().minusHours(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        call("PUT", cp, admin.token(), body, 200);
        call("POST", "/registration", captain.token(), enrollment, 400);
        body.put("registrationEndTime", java.time.LocalDateTime.now().plusHours(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        call("PUT", cp, admin.token(), body, 200);
        enrollment.put("memberIds", java.util.List.of(member.id()));
        call("POST", "/registration", captain.token(), enrollment, 400);
        enrollment.put("memberIds", java.util.List.of());
        long rid = call("POST", "/registration", captain.token(), enrollment, 200).asLong();
        call("PUT", cp + "/status?status=CANCELLED", admin.token(), null, 400);
        call("PUT", cp + "/status?status=CANCELLED&reason=Cancelled_by_organizer", admin.token(), null, 200);
        call("POST", "/registration/" + rid + "/submit", captain.token(), null, 400);
        call("POST", "/guidance", admin.token(), Map.of("registrationId", rid, "teacherId", teacher.id()), 400);
        assertTrue(call("GET", "/message/mine", captain.token(), null, 200).toString().contains("Cancelled_by_organizer"));

        var ended = competitionBody(teacher.id(), null);
        long eid = call("POST", "/competition", admin.token(), ended, 200).asLong();
        String ep = "/competition/" + eid;
        call("PUT", ep + "/status?status=REGISTERING", admin.token(), null, 200);
        call("PUT", ep + "/status?status=IN_PROGRESS", admin.token(), null, 200);
        call("PUT", ep + "/status?status=ENDED", admin.token(), null, 200);
        call("PUT", ep + "/status?status=REGISTERING", admin.token(), null, 400);
        enrollment.put("competitionId", eid);
        call("POST", "/registration", captain.token(), enrollment, 400);
    }

    @Test
    void manualMessagesRejectMissingDisabledRecipientsAndOversizedContentWithoutPersisting() throws Exception {
        var admin = actor("ADMIN"); var recipient = actor("STUDENT");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE sender_id=?", Integer.class, admin.id());
        assertAll(
                () -> call("POST", "/message", admin.token(), Map.of("receiverId", 999999999L, "title", "No recipient"), 400),
                () -> call("POST", "/message", admin.token(), Map.of("receiverId", recipient.id(), "title", "Too long", "content", "x".repeat(1025)), 400),
                () -> call("POST", "/message", admin.token(), Map.of("receiverId", recipient.id(), "title", "Invalid type", "type", "x".repeat(30)), 400),
                () -> {
                    var disabled = new com.cie.competition.module.user.entity.User();
                    disabled.setId(recipient.id()); disabled.setStatus(0);
                    userMapper.updateById(disabled);
                    call("POST", "/message", admin.token(), Map.of("receiverId", recipient.id(), "title", "Disabled recipient"), 400);
                });
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM message WHERE sender_id=?", Integer.class, admin.id()));
    }
}
