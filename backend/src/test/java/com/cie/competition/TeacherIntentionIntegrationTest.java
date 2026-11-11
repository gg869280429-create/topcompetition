package com.cie.competition;

import com.cie.competition.common.exception.BusinessException;
import com.cie.competition.common.security.*;
import com.cie.competition.module.registration.dto.*;
import com.cie.competition.module.registration.service.*;
import com.cie.competition.module.guidance.service.GuidanceService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.banner-mode=off", "logging.level.org.springframework.boot.autoconfigure.security=ERROR", "logging.level.com.cie.competition.common.config.DataInitializer=ERROR", "app.upload-directory=${java.io.tmpdir}/competition-teacher-tests"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class TeacherIntentionIntegrationTest extends IsolatedDatabaseTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired RegistrationService registrations;
    @Autowired RegistrationReviewService reviews;
    @Autowired RegistrationWorkflowService workflow;
    @Autowired com.cie.competition.module.file.service.FileService fileService;
    @Autowired com.cie.competition.module.statistics.service.StatisticsService statistics;
    @Autowired GuidanceService guidance;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    @Autowired com.cie.competition.module.user.service.TeacherService teacherService;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.cie.competition.module.competition.service.CompetitionService competitions;
    @Autowired com.cie.competition.module.award.service.AwardService awards;
    private Long competitionId, majorId, admin, teacher, otherTeacher, student, otherStudent;
    private final List<Long> userIds = new ArrayList<>();
    private String prefix;

    @BeforeEach
    void fixtures() {
        prefix = "teacher_test_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_major(name) VALUES (?)", prefix);
        majorId = jdbc.queryForObject("SELECT id FROM sys_major WHERE name=?", Long.class, prefix);
        admin = user("admin", "ADMIN"); teacher = user("teacher", "TEACHER"); otherTeacher = user("other_teacher", "TEACHER");
        student = user("student", "STUDENT"); otherStudent = user("other_student", "STUDENT");
        jdbc.update("INSERT INTO competition(title,status,max_members,max_teachers,registration_start_time,registration_end_time,publisher_id) VALUES (?,'REGISTERING',3,1,DATE_SUB(NOW(), INTERVAL 1 DAY),DATE_ADD(NOW(), INTERVAL 1 DAY),?)", prefix, admin);
        competitionId = jdbc.queryForObject("SELECT id FROM competition WHERE title=?", Long.class, prefix);
        jdbc.update("INSERT INTO guidance_quota(competition_id,teacher_id,quota) VALUES (?,?,1),(?,?,2)", competitionId, teacher, competitionId, otherTeacher);
    }

    private Long user(String suffix, String role) {
        String name = prefix + "_" + suffix;
        jdbc.update("INSERT INTO sys_user(username,password,real_name,role,major_id,status) VALUES (?,? ,?,?,?,1)", name, "unusable_test_password", suffix, role, majorId);
        Long id = jdbc.queryForObject("SELECT id FROM sys_user WHERE username=?", Long.class, name); userIds.add(id); return id;
    }

    private Long apply(Long leader, Long preferred) {
        UserContext.set(new LoginUser(leader, "test", "STUDENT"));
        RegistrationDTO dto = new RegistrationDTO(); dto.setCompetitionId(competitionId); dto.setPreferredTeacherId(preferred);
        dto.setTeamName(prefix + leader); dto.setMemberIds(List.of());
        return registrations.create(dto);
    }

    private void review(Long id, Long confirmed, String result, String comment) {
        UserContext.set(new LoginUser(admin, "test", "ADMIN"));
        ReviewDTO dto = new ReviewDTO(); dto.setResult(result); dto.setTeacherId(confirmed); dto.setComment(comment);
        reviews.review(id, dto);
    }

    @Test
    void cannotChooseTeacherOutsideCompetition() {
        assertThrows(BusinessException.class, () -> apply(student, student));
        assertEquals(0, count("SELECT COUNT(*) FROM registration WHERE competition_id=?", competitionId));
    }

    @Test
    void intentionsDoNotReserveQuota() {
        apply(student, teacher); apply(otherStudent, teacher);
        assertEquals(0, count("SELECT COUNT(*) FROM guidance WHERE competition_id=?", competitionId));
        assertEquals(1, guidance.competitionTeachers(competitionId).stream().filter(t -> t.getTeacherId().equals(teacher)).findFirst().orElseThrow().getRemaining());
    }

    @Test
    void approvalConfirmsTeacherAndNotifiesBothSides() {
        Long id = apply(student, teacher);
        review(id, teacher, "APPROVED", null);
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM registration WHERE id=?", String.class, id));
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND teacher_id=?", id, teacher));
        assertEquals(1, count("SELECT COUNT(*) FROM message WHERE receiver_id=?", teacher));
        assertEquals(1, count("SELECT COUNT(*) FROM message WHERE receiver_id=? AND type='AUDIT'", student));
    }

    @Test
    void reassignmentRequiresReasonAndRollsBackApproval() {
        Long id = apply(student, teacher);
        assertThrows(BusinessException.class, () -> review(id, otherTeacher, "APPROVED", " "));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM registration WHERE id=?", String.class, id));
        assertEquals(0, count("SELECT COUNT(*) FROM guidance WHERE registration_id=?", id));
        review(id, otherTeacher, "APPROVED", "根据研究方向调剂");
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND teacher_id=?", id, otherTeacher));
    }

    @Test
    void exhaustedQuotaRollsBackSecondApproval() {
        Long first = apply(student, teacher), second = apply(otherStudent, teacher);
        review(first, teacher, "APPROVED", null);
        assertThrows(BusinessException.class, () -> review(second, teacher, "APPROVED", null));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM registration WHERE id=?", String.class, second));
        assertNull(jdbc.queryForObject("SELECT reviewer_id FROM registration WHERE id=?", Long.class, second));
        assertEquals(0, count("SELECT COUNT(*) FROM message WHERE receiver_id=? AND type='AUDIT'", otherStudent));
    }

    @Test
    void rejectedRegistrationCanChangeIntentionAndResubmit() {
        Long id = apply(student, teacher); review(id, null, "REJECTED", "请修改信息");
        UserContext.set(new LoginUser(student, "test", "STUDENT"));
        RegistrationDTO dto = new RegistrationDTO(); dto.setCompetitionId(competitionId); dto.setPreferredTeacherId(otherTeacher);
        dto.setTeamName(prefix); dto.setMemberIds(List.of()); registrations.update(id, dto);
        workflow.submit(id);
        var result = registrations.getById(id);
        assertEquals("PENDING", result.getStatus()); assertEquals(otherTeacher, result.getPreferredTeacherId());
        assertNull(result.getReviewerId()); assertNull(result.getReviewComment()); assertNull(result.getReviewTime());
        registrations.cancel(id);
    }

    @Test
    void historicalRegistrationNeedsExplicitTeacher() {
        Long id = apply(student, teacher);
        jdbc.update("UPDATE registration SET preferred_teacher_id=NULL WHERE id=?", id);
        assertThrows(BusinessException.class, () -> review(id, null, "APPROVED", null));
        review(id, teacher, "APPROVED", null);
    }

    @Test
    void teacherLibraryIsRestrictedToAdministrators() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/teacher/page")
                .header("Authorization", "Bearer " + jwt.generateToken(student, "test", "STUDENT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/teacher/page")
                .header("Authorization", "Bearer " + jwt.generateToken(admin, "test", "ADMIN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200));
    }

    @Test
    void majorAdministratorCannotEditTeacherFromAnotherMajor() throws Exception {
        jdbc.update("UPDATE sys_user SET role='MAJOR_ADMIN' WHERE id=?", admin);
        jdbc.update("UPDATE sys_user SET major_id=NULL WHERE id=?", otherTeacher);
        var dto = Map.of("username", prefix + "_other_teacher", "realName", "不可越权修改", "majorId", majorId, "status", 1);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/teacher/" + otherTeacher)
                .header("Authorization", "Bearer " + jwt.generateToken(admin, "test", "MAJOR_ADMIN"))
                .contentType("application/json").content(json.writeValueAsBytes(dto)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        assertEquals("other_teacher", jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id=?", String.class, otherTeacher));
    }

    @Test
    void administratorCanMaintainTeacherAndDisableExistingToken() throws Exception {
        var dto = Map.of("username", prefix + "_new", "password", "test_password_123", "realName", "测试教师", "majorId", majorId,
                "expertise", "软件工程", "biography", "测试简介", "status", 1);
        String adminToken = "Bearer " + jwt.generateToken(admin, "test", "ADMIN");
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/teacher")
                .header("Authorization", adminToken).contentType("application/json").content(json.writeValueAsBytes(dto)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        Long id = json.readTree(response).get("data").asLong(); userIds.add(id);
        var edited = new HashMap<String, Object>(dto); edited.remove("password"); edited.put("status", 0);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/teacher/" + id)
                .header("Authorization", adminToken).contentType("application/json").content(json.writeValueAsBytes(edited)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/info")
                .header("Authorization", "Bearer " + jwt.generateToken(id, "test", "TEACHER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(401));
    }

    @Test
    void documentUploadAndProtectedDownload() throws Exception {
        byte[] content = "%PDF-1.4\nfixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "竞赛说明.pdf", "application/pdf", content);
        String adminToken = "Bearer " + jwt.generateToken(admin, "test", "ADMIN");
        String studentToken = "Bearer " + jwt.generateToken(student, "test", "STUDENT");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/file").file(file).header("Authorization", studentToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        String result = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/file").file(file).header("Authorization", adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        Long fileId = json.readTree(result).get("data").get("id").asLong();
        jdbc.update("UPDATE competition SET document_file_id=? WHERE id=?", fileId, competitionId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/competition/" + competitionId + "/document").header("Authorization", studentToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(content));
        jdbc.update("UPDATE competition SET status='DRAFT' WHERE id=?", competitionId);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/competition/" + competitionId + "/document").header("Authorization", studentToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(404));
        var invalid = new MockMultipartFile("file", "invalid.pdf", "application/pdf", new byte[] {1,2,3});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/file").file(invalid).header("Authorization", adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(400));
    }

    @Test
    void configuredTeacherIsVisibleBeforeAnyTeamIsAssigned() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/guidance/teachers").param("competitionId", competitionId.toString())
                .header("Authorization", "Bearer " + jwt.generateToken(student, "test", "STUDENT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.length()").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].remaining").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data[0].password").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/guidance/quota/mine")
                .header("Authorization", "Bearer " + jwt.generateToken(teacher, "test", "TEACHER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.records[0].competitionId").value(competitionId));
    }

    @Test
    void usedQuotaCannotBeReducedOrDeleted() {
        Long id = apply(student, teacher); review(id, teacher, "APPROVED", null);
        var dto = new com.cie.competition.module.guidance.dto.GuidanceQuotaDTO();
        dto.setCompetitionId(competitionId); dto.setTeacherId(teacher); dto.setQuota(0);
        assertThrows(BusinessException.class, () -> guidance.saveQuota(dto));
        Long quotaId = jdbc.queryForObject("SELECT id FROM guidance_quota WHERE competition_id=? AND teacher_id=?", Long.class, competitionId, teacher);
        assertThrows(BusinessException.class, () -> guidance.deleteQuota(quotaId));
        assertEquals(1, count("SELECT quota FROM guidance_quota WHERE id=?", quotaId));
    }

    @Test
    void teacherReplacementIsAtomicAndRequiresReason() {
        Long id = apply(student, teacher); review(id, teacher, "APPROVED", null);
        Long otherId = apply(otherStudent, otherTeacher); review(otherId, otherTeacher, "APPROVED", null);
        jdbc.update("UPDATE guidance_quota SET quota=1 WHERE competition_id=? AND teacher_id=?", competitionId, otherTeacher);
        Long oldGuidance = jdbc.queryForObject("SELECT id FROM guidance WHERE registration_id=?", Long.class, id);
        var dto = new com.cie.competition.module.guidance.dto.GuidanceAssignDTO(); dto.setRegistrationId(id); dto.setTeacherId(otherTeacher);
        assertThrows(BusinessException.class, () -> guidance.replace(oldGuidance, dto));
        dto.setComment("按研究方向调整");
        assertThrows(BusinessException.class, () -> guidance.replace(oldGuidance, dto));
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE id=? AND teacher_id=?", oldGuidance, teacher));
        jdbc.update("UPDATE guidance_quota SET quota=2 WHERE competition_id=? AND teacher_id=?", competitionId, otherTeacher);
        guidance.replace(oldGuidance, dto);
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE id=? AND status='ENDED' AND active_key IS NULL", oldGuidance));
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND teacher_id=?", id, otherTeacher));
        assertEquals(2, count("SELECT COUNT(*) FROM message WHERE receiver_id=? AND title IN ('报名审核结果','指导安排')", student));
        assertEquals(2, count("SELECT COUNT(*) FROM message WHERE receiver_id=?", teacher));
    }

    @Test
    void concurrentQuotaCreationPreventsDisablingTeacher() throws Exception {
        Long spare = user("spare", "TEACHER");
        var written = new CountDownLatch(1); var release = new CountDownLatch(1); var disabling = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var writer = pool.submit(() -> {
                UserContext.set(new LoginUser(admin, "test", "ADMIN"));
                try {
                    new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        var quota = new com.cie.competition.module.guidance.dto.GuidanceQuotaDTO(); quota.setCompetitionId(competitionId); quota.setTeacherId(spare); quota.setQuota(1);
                        guidance.saveQuota(quota); written.countDown();
                        try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("测试同步超时"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                    });
                } finally { UserContext.clear(); }
            });
            assertTrue(written.await(10, TimeUnit.SECONDS));
            var reader = pool.submit(() -> {
                UserContext.set(new LoginUser(admin, "test", "ADMIN"));
                try {
                    var dto = new com.cie.competition.module.user.dto.TeacherDTO(); dto.setUsername(prefix + "_spare"); dto.setRealName("spare"); dto.setMajorId(majorId); dto.setStatus(0);
                    disabling.countDown();
                    try { teacherService.save(spare, dto); return true; } catch (BusinessException e) { return false; }
                } finally { UserContext.clear(); }
            });
            assertTrue(disabling.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> reader.get(300, TimeUnit.MILLISECONDS));
            release.countDown(); writer.get(10, TimeUnit.SECONDS);
            assertFalse(reader.get(10, TimeUnit.SECONDS));
            assertEquals(1, count("SELECT status FROM sys_user WHERE id=?", spare));
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void replacementAndQuotaChangesUseConsistentLockOrder() throws Exception {
        Long id = apply(student, teacher); review(id, teacher, "APPROVED", null);
        Long oldGuidance = jdbc.queryForObject("SELECT id FROM guidance WHERE registration_id=?", Long.class, id);
        var lockedTeacher = new CountDownLatch(1); var readGuidance = new CountDownLatch(1); var started = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var quotaWriter = pool.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                teacherService.lockActive(teacher); lockedTeacher.countDown();
                try { if (!readGuidance.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("测试同步超时"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                jdbc.queryForList("SELECT id FROM guidance WHERE teacher_id=? FOR UPDATE", Long.class, teacher);
            }));
            assertTrue(lockedTeacher.await(5, TimeUnit.SECONDS));
            var replacement = pool.submit(() -> {
                UserContext.set(new LoginUser(admin, "test", "ADMIN"));
                try {
                    var dto = new com.cie.competition.module.guidance.dto.GuidanceAssignDTO();
                    dto.setRegistrationId(id); dto.setTeacherId(otherTeacher); dto.setComment("调整指导安排"); started.countDown();
                    return guidance.replace(oldGuidance, dto);
                } finally { UserContext.clear(); }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> replacement.get(300, TimeUnit.MILLISECONDS));
            readGuidance.countDown(); quotaWriter.get(10, TimeUnit.SECONDS); replacement.get(10, TimeUnit.SECONDS);
            assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND teacher_id=?", id, otherTeacher));
        } finally { readGuidance.countDown(); pool.shutdownNow(); }
    }

    @Test
    void concurrentApprovalsCannotOverbookLastPlace() throws Exception {
        Long first = apply(student, teacher), second = apply(otherStudent, teacher);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Long id : List.of(first, second)) results.add(pool.submit(() -> {
                start.await();
                try { review(id, teacher, "APPROVED", null); return true; }
                catch (BusinessException e) { return false; }
                finally { UserContext.clear(); }
            }));
            start.countDown(); int success = 0;
            for (var result : results) if (result.get(15, TimeUnit.SECONDS)) success++;
            assertEquals(1, success);
            assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE competition_id=?", competitionId));
            assertEquals(1, count("SELECT COUNT(*) FROM registration WHERE competition_id=? AND status='PENDING'", competitionId));
        } finally { pool.shutdownNow(); }
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    @Test
    void loginWorksWithRotatedKeyAndRejectsPreviousSignatures() throws Exception {
        String password = "temporary_test_password";
        jdbc.update("UPDATE sys_user SET password=? WHERE id=?", new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password), student);
        String username = jdbc.queryForObject("SELECT username FROM sys_user WHERE id=?", String.class, student);
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content(json.writeValueAsBytes(Map.of("username", username, "password", password))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(response).path("data").path("token").asText();
        assertTrue(jwt.validateToken(token));
        var otherKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(new byte[64]);
        String previousSignature = io.jsonwebtoken.Jwts.builder().subject(student.toString()).signWith(otherKey).compact();
        assertFalse(jwt.validateToken(previousSignature));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/info").header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200));
    }

    @Test
    void registrationTimeWindowRejectsEarlyLateAndResubmission() {
        jdbc.update("UPDATE competition SET registration_start_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?", competitionId);
        assertThrows(BusinessException.class, () -> apply(student, teacher));
        jdbc.update("UPDATE competition SET registration_start_time=DATE_SUB(NOW(),INTERVAL 2 DAY),registration_end_time=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?", competitionId);
        assertThrows(BusinessException.class, () -> apply(student, teacher));
        jdbc.update("UPDATE competition SET registration_end_time=DATE_ADD(NOW(),INTERVAL 1 DAY) WHERE id=?", competitionId);
        Long id = apply(student, teacher);
        review(id, null, "REJECTED", "补充材料");
        jdbc.update("UPDATE competition SET registration_end_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?", competitionId);
        UserContext.set(new LoginUser(student, "test", "STUDENT"));
        var dto = registrationDto(List.of());
        assertThrows(BusinessException.class, () -> registrations.update(id, dto));
        assertEquals("REJECTED", jdbc.queryForObject("SELECT status FROM registration WHERE id=?", String.class, id));
    }

    private RegistrationDTO registrationDto(List<Long> members) {
        var dto = new RegistrationDTO(); dto.setCompetitionId(competitionId); dto.setPreferredTeacherId(teacher);
        dto.setTeamName(prefix); dto.setMemberIds(members); return dto;
    }

    @Test
    void membershipValidationRejectsNonStudentsDisabledMissingMajorAndLeaderDuplicate() {
        UserContext.set(new LoginUser(student, "test", "STUDENT"));
        assertThrows(BusinessException.class, () -> registrations.create(registrationDto(List.of(student))));
        assertThrows(BusinessException.class, () -> registrations.create(registrationDto(List.of(teacher))));
        assertThrows(BusinessException.class, () -> registrations.create(registrationDto(List.of(Long.MAX_VALUE))));
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", otherStudent);
        assertThrows(BusinessException.class, () -> registrations.create(registrationDto(List.of(otherStudent))));
        jdbc.update("UPDATE sys_user SET status=1,major_id=NULL WHERE id=?", otherStudent);
        assertThrows(BusinessException.class, () -> registrations.create(registrationDto(List.of(otherStudent))));
        assertEquals(0, count("SELECT COUNT(*) FROM registration WHERE competition_id=?", competitionId));
    }

    @Test
    void crossMajorIsCalculatedAndOwnedByLeaderMajor() {
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?", majorId + 100000, otherStudent);
        UserContext.set(new LoginUser(student, "test", "STUDENT"));
        assertThrows(BusinessException.class, () -> registrations.create(registrationDto(List.of(otherStudent))));
        jdbc.update("UPDATE competition SET allow_cross_major=1 WHERE id=?", competitionId);
        Long id = registrations.create(registrationDto(List.of(otherStudent)));
        assertEquals(1, count("SELECT cross_major FROM registration WHERE id=?", id));
        assertEquals(majorId, jdbc.queryForObject("SELECT major_id FROM registration WHERE id=?", Long.class, id));
    }

    @Test
    void teacherCannotReadUnrelatedRegistrationOrGuidance() throws Exception {
        Long id = apply(student, teacher);
        String token = jwt.generateToken(otherTeacher, "test", "TEACHER");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/registration/" + id).header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/guidance/list").param("registrationId", id.toString()).header("Authorization", "Bearer " + token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        review(id, teacher, "APPROVED", null);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/registration/" + id).header("Authorization", "Bearer " + jwt.generateToken(teacher, "test", "TEACHER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200));
    }

    @Test
    void enrolledCompetitionCannotBeDeletedOrRulesChangedAndStateCannotGoBack() {
        apply(student, teacher);
        UserContext.set(new LoginUser(admin, "test", "ADMIN"));
        assertThrows(BusinessException.class, () -> competitions.delete(competitionId));
        var dto = new com.cie.competition.module.competition.dto.CompetitionDTO();
        org.springframework.beans.BeanUtils.copyProperties(competitions.getById(competitionId), dto);
        dto.setAllowCrossMajor(1);
        assertThrows(BusinessException.class, () -> competitions.update(competitionId, dto));
        dto.setAllowCrossMajor(0); dto.setMaxMembers(2);
        assertThrows(BusinessException.class, () -> competitions.update(competitionId, dto));
        assertThrows(BusinessException.class, () -> competitions.updateStatus(competitionId, "DRAFT"));
        assertThrows(BusinessException.class, () -> competitions.updateStatus(competitionId, "ENDED"));
        competitions.updateStatus(competitionId, "IN_PROGRESS");
        competitions.updateStatus(competitionId, "ENDED");
        assertThrows(BusinessException.class, () -> competitions.updateStatus(competitionId, "REGISTERING"));
    }

    @Test
    void cancellationRequiresReasonAndPreservesEnrollmentAndNotifies() {
        Long id = apply(student, teacher);
        UserContext.set(new LoginUser(admin, "test", "ADMIN"));
        assertThrows(BusinessException.class, () -> competitions.updateStatus(competitionId, "CANCELLED"));
        competitions.updateStatus(competitionId, "CANCELLED", "主办方取消");
        assertEquals(1, count("SELECT COUNT(*) FROM registration WHERE id=?", id));
        assertEquals(1, count("SELECT COUNT(*) FROM message WHERE receiver_id=? AND title='竞赛取消通知'", student));
        assertThrows(BusinessException.class, () -> apply(otherStudent, teacher));
    }

    @Test
    void administratorCanReviewAfterDeadlineButCannotAssignAfterCompetitionEnded() {
        Long id = apply(student, teacher);
        jdbc.update("UPDATE competition SET registration_end_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?", competitionId);
        review(id, teacher, "APPROVED", null);
        jdbc.update("UPDATE competition SET status='ENDED' WHERE id=?", competitionId);
        var assign = new com.cie.competition.module.guidance.dto.GuidanceAssignDTO();
        assign.setRegistrationId(id); assign.setTeacherId(otherTeacher); assign.setComment("调整");
        assertThrows(BusinessException.class, () -> guidance.assign(assign));
    }

    @Test
    void concurrentMembershipProducesOnlyOneEffectiveRegistration() throws Exception {
        Long third = user("third", "STUDENT");
        UserContext.set(new LoginUser(student, "test", "STUDENT"));
        Long first = registrations.create(registrationDto(List.of(third)));
        UserContext.set(new LoginUser(otherStudent, "test", "STUDENT"));
        Long second = registrations.create(registrationDto(List.of(third)));
        UserContext.set(new LoginUser(third, "test", "STUDENT"));
        for (Long invitation : jdbc.queryForList("SELECT id FROM team_invitation WHERE invitee_id=? AND status='PENDING'", Long.class, third)) workflow.respond(invitation, true);
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Long registration : List.of(first, second)) results.add(pool.submit(() -> {
                Long leader = registration.equals(first) ? student : otherStudent;
                start.await(); UserContext.set(new LoginUser(leader, "test", "STUDENT"));
                try { workflow.submit(registration); return true; }
                catch (BusinessException e) { return false; } finally { UserContext.clear(); }
            }));
            start.countDown(); int successes=0;
            for (var result : results) if (result.get(15, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, count("SELECT COUNT(*) FROM registration_member WHERE competition_id=? AND user_id=? AND active_key=1", competitionId, third));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentAssignmentToDifferentTeachersCannotExceedTeamLimit() throws Exception {
        Long id = apply(student, teacher);
        jdbc.update("UPDATE registration SET status='APPROVED',preferred_teacher_id=NULL WHERE id=?", id);
        UserContext.set(new LoginUser(admin, "test", "ADMIN"));
        var started = new CountDownLatch(1); var pool = Executors.newSingleThreadExecutor();
        try {
            var second = new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                competitions.lockById(competitionId);
                second.set(pool.submit(() -> {
                    UserContext.set(new LoginUser(admin, "test", "ADMIN"));
                    var dto = new com.cie.competition.module.guidance.dto.GuidanceAssignDTO(); dto.setRegistrationId(id); dto.setTeacherId(otherTeacher);
                    started.countDown();
                    try { guidance.assign(dto); return true; }
                    catch (BusinessException e) { return false; } finally { UserContext.clear(); }
                }));
                try { assertTrue(started.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { throw new IllegalStateException(e); }
                assertThrows(TimeoutException.class, () -> second.get().get(300, TimeUnit.MILLISECONDS));
                var first = new com.cie.competition.module.guidance.dto.GuidanceAssignDTO(); first.setRegistrationId(id); first.setTeacherId(teacher);
                guidance.assign(first);
            });
            assertFalse(second.get().get(15, TimeUnit.SECONDS));
            assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ACTIVE'", id));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void majorAdministratorCannotReadChangeDeleteOrReviewForeignAwardAndStatisticsAreScoped() throws Exception {
        Long id = apply(student, teacher); review(id, teacher, "APPROVED", null);
        jdbc.update("INSERT INTO award(competition_id,registration_id,award_level,status,major_id) VALUES (?,?,'FIRST','PENDING',?)", competitionId, id, majorId);
        Long awardId = jdbc.queryForObject("SELECT id FROM award WHERE registration_id=?", Long.class, id);
        Long localManager = user("local_manager", "MAJOR_ADMIN");
        Long foreignManager = user("foreign_manager", "MAJOR_ADMIN");
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?", majorId + 100000, foreignManager);
        String foreignToken = jwt.generateToken(foreignManager, "test", "MAJOR_ADMIN");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/award/"+awardId).header("Authorization", "Bearer "+foreignToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/award/"+awardId).header("Authorization", "Bearer "+foreignToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/award/"+awardId+"/review").header("Authorization", "Bearer "+foreignToken)
                        .contentType("application/json").content("{\"result\":\"APPROVED\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/award/page").header("Authorization", "Bearer "+foreignToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.total").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/statistics/overview").header("Authorization", "Bearer "+foreignToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.registrationCount").value(0));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/award/"+awardId).header("Authorization", "Bearer "+jwt.generateToken(localManager,"test","MAJOR_ADMIN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(200));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/award/"+awardId).header("Authorization", "Bearer "+jwt.generateToken(otherStudent,"test","STUDENT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM award WHERE id=?", String.class, awardId));
    }


    private void asStudent(Long id) { UserContext.set(new LoginUser(id, "test", "STUDENT")); }
    private void asAdmin() { UserContext.set(new LoginUser(admin, "test", "ADMIN")); }
    private Long draft(Long... members) { asStudent(student); return registrations.create(registrationDto(List.of(members))); }
    private Long invitation(Long registration, Long member) {
        return jdbc.queryForObject("SELECT MAX(id) FROM team_invitation WHERE registration_id=? AND invitee_id=?", Long.class, registration, member);
    }
    private void accept(Long registration, Long member) { asStudent(member); workflow.respond(invitation(registration, member), true); }
    private void submit(Long registration) { asStudent(student); workflow.submit(registration); }
    private String state(Long registration) { return jdbc.queryForObject("SELECT status FROM registration WHERE id=?", String.class, registration); }
    private Long award(Long registration) throws Exception {
        asAdmin();
        var certificate = fileService.uploadAwardCertificate(new MockMultipartFile("file", "proof.pdf", "application/pdf", "%PDF-1.4\nproof".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var dto = new com.cie.competition.module.award.dto.AwardDTO();
        dto.setRegistrationId(registration); dto.setAwardLevel("FIRST"); dto.setAwardTime(java.time.LocalDateTime.now()); dto.setCertificateFileId(certificate.getId());
        return awards.create(dto);
    }
    private void awardReview(Long id, String result, String comment) {
        asAdmin(); var dto = new com.cie.competition.module.award.dto.AwardReviewDTO(); dto.setResult(result); dto.setComment(comment); awards.review(id, dto);
    }

    @Test
    void unconfirmedInvitationCannotSubmitReadPrivateDetailsOrReserveEligibility() throws Exception {
        Long id = draft(otherStudent);
        assertEquals("DRAFT", state(id));
        assertEquals(0, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1", id));
        assertThrows(BusinessException.class, () -> workflow.submit(id));
        asStudent(otherStudent); assertThrows(BusinessException.class, () -> registrations.getById(id));
        Long own = apply(otherStudent, otherTeacher);
        assertEquals("PENDING", state(own));
        accept(id, otherStudent);
        assertThrows(BusinessException.class, () -> submit(id));
        assertEquals("DRAFT", state(id));
    }

    @Test
    void invitationsSupportRejectReinviteAndRevokeWithHistory() {
        Long id = draft(otherStudent); Long first = invitation(id, otherStudent);
        asStudent(otherStudent); workflow.respond(first, false);
        assertThrows(BusinessException.class, () -> submit(id));
        asStudent(student); registrations.update(id, registrationDto(List.of(otherStudent)));
        Long second = invitation(id, otherStudent); assertNotEquals(first, second);
        accept(id, otherStudent);
        asStudent(student); workflow.revoke(second); workflow.submit(id);
        assertEquals("PENDING", state(id));
        assertEquals(1, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1", id));
        assertEquals(1, count("SELECT COUNT(*) FROM business_history WHERE biz_type='REGISTRATION' AND biz_id=? AND operation='INVITATION_REJECT'", id));
        assertEquals(1, count("SELECT COUNT(*) FROM business_history WHERE biz_type='REGISTRATION' AND biz_id=? AND operation='INVITATION_REVOKE'", id));
    }

    @Test
    void acceptedMembersAreRevalidatedAtFormalSubmission() {
        Long id = draft(otherStudent); accept(id, otherStudent);
        jdbc.update("UPDATE sys_user SET status=0 WHERE id=?", otherStudent);
        assertThrows(BusinessException.class, () -> submit(id));
        jdbc.update("UPDATE sys_user SET status=1,major_id=NULL WHERE id=?", otherStudent);
        assertThrows(BusinessException.class, () -> submit(id));
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?", majorId+100000, otherStudent);
        assertThrows(BusinessException.class, () -> submit(id));
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?", majorId, otherStudent);
        jdbc.update("UPDATE competition SET max_members=1 WHERE id=?", competitionId);
        assertThrows(BusinessException.class, () -> submit(id));
        assertEquals(0, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1", id));
    }

    @Test
    void rejectedResubmissionKeepsOriginalOpinionsAndParticipantSnapshots() {
        Long id = draft(otherStudent); accept(id, otherStudent); submit(id);
        review(id, null, "REJECTED", "第一轮请补充材料");
        assertEquals(0, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1", id));
        jdbc.update("UPDATE sys_user SET real_name='改名后的学生' WHERE id=?", otherStudent);
        submit(id); review(id, teacher, "APPROVED", null);
        asStudent(student);
        var history = (java.util.List<?>)workflow.details(id).get("history");
        String contents = history.toString();
        assertTrue(contents.contains("第一轮请补充材料"));
        assertTrue(contents.contains("other_student"));
        assertTrue(contents.contains("改名后的学生"));
        assertNull(registrations.getById(id).getReviewComment());
        assertEquals(2, count("SELECT COUNT(*) FROM business_history WHERE biz_type='REGISTRATION' AND biz_id=? AND operation='REVIEW'", id));
    }

    @Test
    void cancellingSubmittedRegistrationPreservesHistoryAndReleasesMemberEligibility() {
        Long id = draft(otherStudent); accept(id, otherStudent); submit(id);
        asStudent(student); registrations.cancel(id);
        assertEquals("CANCELLED", state(id));
        assertEquals(2, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=?", id));
        assertEquals(0, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1", id));
        assertTrue(count("SELECT COUNT(*) FROM business_history WHERE biz_id=? AND biz_type='REGISTRATION'", id)>0);
        assertEquals("PENDING", state(apply(otherStudent, otherTeacher)));
    }

    @Test
    void withdrawalApprovalIsAtomicIdempotentAndFreesTeacherQuota() {
        Long id = draft(otherStudent); accept(id, otherStudent); submit(id); review(id, teacher, "APPROVED", null);
        asStudent(student); Long request = workflow.requestWithdrawal(id, "安排冲突");
        asAdmin(); workflow.reviewWithdrawal(request, "APPROVED", "同意退赛");
        assertEquals("WITHDRAWN", state(id));
        assertEquals(0, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ACTIVE'", id));
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ENDED' AND active_key IS NULL", id));
        assertEquals(0, count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1", id));
        int notifications=count("SELECT COUNT(*) FROM message WHERE receiver_id=?", teacher);
        workflow.reviewWithdrawal(request, "APPROVED", "再次操作");
        assertEquals(notifications,count("SELECT COUNT(*) FROM message WHERE receiver_id=?", teacher));
        Long other=apply(otherStudent, teacher); review(other, teacher, "APPROVED", null);
        assertEquals(1, count("SELECT COUNT(*) FROM guidance WHERE competition_id=? AND teacher_id=? AND status='ACTIVE'", competitionId, teacher));
    }

    @Test
    void approvedAwardBlocksWithdrawalUntilRevokedAndDeletionRemainsForbidden() throws Exception {
        Long id=apply(student,teacher); review(id,teacher,"APPROVED",null); Long prize=award(id); awardReview(prize,"APPROVED",null);
        asStudent(student); Long request=workflow.requestWithdrawal(id,"计划调整");
        asAdmin(); assertThrows(BusinessException.class,()->workflow.reviewWithdrawal(request,"APPROVED",null));
        assertEquals("APPROVED",state(id));
        assertThrows(BusinessException.class,()->awards.delete(prize));
        awards.revoke(prize,"证明无效，撤销获奖"); workflow.reviewWithdrawal(request,"APPROVED",null);
        assertEquals("WITHDRAWN",state(id));
        assertEquals("REVOKED",jdbc.queryForObject("SELECT status FROM award WHERE id=?",String.class,prize));
        assertEquals(1,count("SELECT COUNT(*) FROM award WHERE id=?",prize));
    }

    @Test
    void withdrawnRegistrationRequiresNewConfirmationAndOpenWindowForReapplication() {
        Long id=draft(otherStudent);accept(id,otherStudent);submit(id);review(id,teacher,"APPROVED",null);
        asStudent(student);Long request=workflow.requestWithdrawal(id,"时间冲突");asAdmin();workflow.reviewWithdrawal(request,"APPROVED",null);
        asStudent(student);workflow.reapply(id,"重新安排后参赛");
        assertThrows(BusinessException.class,()->workflow.submit(id));
        accept(id,otherStudent);submit(id);review(id,teacher,"APPROVED",null);
        assertEquals(1,count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ACTIVE'",id));
        assertEquals(1,count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ENDED'",id));
        asStudent(student);Long second=workflow.requestWithdrawal(id,"再次退出");asAdmin();workflow.reviewWithdrawal(second,"APPROVED",null);
        jdbc.update("UPDATE competition SET registration_end_time=DATE_SUB(NOW(),INTERVAL 1 SECOND) WHERE id=?",competitionId);
        asStudent(student);assertThrows(BusinessException.class,()->workflow.reapply(id,"截止后重申"));
    }

    @Test
    void approvedTeamRequiresAdjustmentAndNewMemberConfirmationBeforeReReview() {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);
        asStudent(student);assertThrows(BusinessException.class,()->registrations.update(id,registrationDto(List.of(otherStudent))));
        workflow.adjust(id,"补充一名成员");registrations.update(id,registrationDto(List.of(otherStudent)));
        assertThrows(BusinessException.class,()->workflow.submit(id));
        accept(id,otherStudent);submit(id);
        assertEquals("PENDING",state(id));
        assertEquals(0,count("SELECT COUNT(*) FROM guidance WHERE registration_id=? AND status='ACTIVE'",id));
        review(id,teacher,"APPROVED",null);
        assertEquals(2,count("SELECT COUNT(*) FROM registration_member WHERE registration_id=? AND active_key=1",id));
    }

    @Test
    void awardRejectResubmitCorrectAndRevokeKeepsHistoryAndEvidence() throws Exception {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);Long prize=award(id);
        awardReview(prize,"REJECTED","材料日期不清楚");
        var original=awards.getById(prize);var dto=new com.cie.competition.module.award.dto.AwardDTO();
        org.springframework.beans.BeanUtils.copyProperties(original,dto);dto.setAwardLevel("SECOND");
        awards.update(prize,dto);assertNull(awards.getById(prize).getReviewComment());awardReview(prize,"APPROVED",null);
        assertThrows(BusinessException.class,()->awards.update(prize,dto));assertThrows(BusinessException.class,()->awards.delete(prize));
        dto.setAwardLevel("FIRST");dto.setReason("核对后奖项等级应为一等奖");awards.correct(prize,dto);
        assertEquals("PENDING",awards.getById(prize).getStatus());awardReview(prize,"APPROVED",null);awards.revoke(prize,"证书撤销");
        String history=awards.history(prize).toString();assertTrue(history.contains("材料日期不清楚"));assertTrue(history.contains("SECOND"));assertTrue(history.contains("FIRST"));assertTrue(history.contains("证书撤销"));
        assertEquals(1,count("SELECT COUNT(*) FROM sys_file WHERE id=?",original.getCertificateFileId()));
    }

    @Test
    void awardCertificateDownloadEnforcesSnapshotParticipantAndTeacherScope() throws Exception {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);Long prize=award(id);
        for(Long allowed:List.of(student,teacher)) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/award/"+prize+"/certificate").header("Authorization","Bearer "+jwt.generateToken(allowed,"test",allowed.equals(teacher)?"TEACHER":"STUDENT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Content-Disposition"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/award/"+prize+"/certificate").header("Authorization","Bearer "+jwt.generateToken(otherStudent,"test","STUDENT")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value(403));
    }

    @Test
    void failedNotificationRollsBackAwardStatusAndHistory() throws Exception {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);Long prize=award(id);
        int events=count("SELECT COUNT(*) FROM business_history WHERE biz_type='AWARD' AND biz_id=?",prize);
        String trigger="fail_notice_"+prize;
        jdbc.execute("CREATE TRIGGER "+trigger+" BEFORE INSERT ON message FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='acceptance notification failure'");
        try{assertThrows(RuntimeException.class,()->awardReview(prize,"APPROVED",null));}
        finally{jdbc.execute("DROP TRIGGER "+trigger);}
        assertEquals("PENDING",awards.getById(prize).getStatus());
        assertEquals(events,count("SELECT COUNT(*) FROM business_history WHERE biz_type='AWARD' AND biz_id=?",prize));
    }

    @Test
    void awardApprovalAndWithdrawalApprovalCannotLeaveConflictingEffectiveStates() throws Exception {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);Long prize=award(id);
        asStudent(student);Long request=workflow.requestWithdrawal(id,"申请退赛");
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try{
            var awardFuture=pool.submit(()->{start.await();try{awardReview(prize,"APPROVED",null);return true;}catch(BusinessException e){return false;}finally{UserContext.clear();}});
            var withdrawalFuture=pool.submit(()->{start.await();asAdmin();try{workflow.reviewWithdrawal(request,"APPROVED",null);return true;}catch(BusinessException e){return false;}finally{UserContext.clear();}});
            start.countDown();int success=(awardFuture.get(15,TimeUnit.SECONDS)?1:0)+(withdrawalFuture.get(15,TimeUnit.SECONDS)?1:0);assertEquals(1,success);
            assertEquals(0,count("SELECT COUNT(*) FROM award a JOIN registration r ON r.id=a.registration_id WHERE a.id=? AND a.status='APPROVED' AND r.status='WITHDRAWN'",prize));
        }finally{pool.shutdownNow();}
    }

    @Test
    void reviewNotificationsLinkToAuthorizedBusinessDetail() {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);
        assertEquals(1,count("SELECT COUNT(*) FROM message WHERE receiver_id=? AND title='报名审核结果' AND biz_type='REGISTRATION' AND biz_id=?",student,id));
        assertEquals(1,count("SELECT COUNT(*) FROM message WHERE receiver_id=? AND title='新增指导团队' AND biz_type='REGISTRATION' AND biz_id=?",teacher,id));
    }


    @Test
    void statisticsDistinguishDraftSubmittedPeopleEffectiveAndRevokedAwards() throws Exception {
        Long id=draft(otherStudent);
        asAdmin();var before=statistics.overview();
        assertEquals(0L,before.getRegistrationCount());
        accept(id,otherStudent);submit(id);
        asAdmin();assertEquals(1L,statistics.overview().getRegistrationCount());
        var pending=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/statistics/overview").header("Authorization","Bearer "+jwt.generateToken(admin,"test","ADMIN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.registrationPeopleCount").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.effectiveRegistrationCount").value(0));
        review(id,teacher,"APPROVED",null);Long prize=award(id);awardReview(prize,"APPROVED",null);
        assertEquals(1L,statistics.overview().getAwardCount());
        awards.revoke(prize,"奖项撤销");
        assertEquals(0L,statistics.overview().getAwardCount());
        asStudent(student);Long withdrawal=workflow.requestWithdrawal(id,"退出竞赛");asAdmin();workflow.reviewWithdrawal(withdrawal,"APPROVED",null);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/statistics/overview").header("Authorization","Bearer "+jwt.generateToken(admin,"test","ADMIN")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.registrationCount").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.registrationPeopleCount").value(2))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.effectiveRegistrationCount").value(0))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.guidanceCount").value(0));
    }

    @Test
    void historicalAwardScopeUsesSavedMajorAndParticipantsAfterTeamAdjustment() throws Exception {
        Long id=draft(otherStudent);accept(id,otherStudent);submit(id);review(id,teacher,"APPROVED",null);Long prize=award(id);
        Long replacement=user("replacement","STUDENT");
        asStudent(student);workflow.adjust(id,"调整队员");registrations.update(id,registrationDto(List.of(replacement)));
        asStudent(replacement);
        assertEquals(0L,awards.page(new com.cie.competition.module.award.dto.AwardQueryDTO()).getTotal());
        assertThrows(BusinessException.class,()->awards.getById(prize));
        accept(id,replacement);submit(id);
        asStudent(otherStudent);assertEquals(prize,awards.getById(prize).getId());
        assertEquals(1L,awards.page(new com.cie.competition.module.award.dto.AwardQueryDTO()).getTotal());
        asAdmin();jdbc.update("UPDATE sys_user SET major_id=?,real_name='新名字' WHERE id IN (?,?)",majorId+100000,student,otherStudent);
        var saved=awards.getById(prize);assertEquals(majorId,saved.getMajorId());assertTrue(saved.getParticipantsSnapshot().contains("other_student"));
        assertFalse(saved.getParticipantsSnapshot().contains("新名字"));
    }

    @Test
    void pendingAwardMayBeRevokedAfterTeamHasWithdrawn() throws Exception {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);Long prize=award(id);
        asStudent(student);Long request=workflow.requestWithdrawal(id,"取消参赛");asAdmin();workflow.reviewWithdrawal(request,"APPROVED",null);
        awards.revoke(prize,"报名已退赛，关闭待审获奖");assertEquals("REVOKED",awards.getById(prize).getStatus());
    }


    @Test
    void endedCompetitionRemainsReadableWhenFormerTeachersAreDisabled() {
        jdbc.update("UPDATE competition SET status='ENDED' WHERE id=?",competitionId);
        jdbc.update("UPDATE sys_user SET status=0 WHERE id IN (?,?)",teacher,otherTeacher);
        asStudent(student);assertEquals(competitionId,competitions.getById(competitionId).getId());
    }

    @Test
    void competitionStateChangesHaveActorReasonAndBeforeAfterHistory() {
        asAdmin();competitions.updateStatus(competitionId,"IN_PROGRESS");competitions.updateStatus(competitionId,"CANCELLED","主办方调整");
        assertEquals(2,count("SELECT COUNT(*) FROM business_history WHERE biz_type='COMPETITION' AND biz_id=? AND operation='STATUS_CHANGED' AND actor_id=? AND operation_time IS NOT NULL AND before_json IS NOT NULL AND after_json IS NOT NULL",competitionId,admin));
        assertEquals(1,count("SELECT COUNT(*) FROM business_history WHERE biz_type='COMPETITION' AND biz_id=? AND reason='主办方调整'",competitionId));
    }


    @Test
    void maximumWithdrawalReasonDoesNotBreakNotificationTransaction() {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);
        jdbc.update("UPDATE registration SET team_name=? WHERE id=?","队".repeat(128),id);
        asStudent(student);Long request=workflow.requestWithdrawal(id,"因".repeat(1000));
        assertNotNull(request);assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM withdrawal_request WHERE id=?",String.class,request));
        assertEquals(1000,jdbc.queryForObject("SELECT CHAR_LENGTH(reason) FROM withdrawal_request WHERE id=?",Integer.class,request));
    }


    @Test void acceptedButUnsubmittedReplacementDoesNotEnterSubmittedPeopleStatistics() {
        Long id=draft(otherStudent);accept(id,otherStudent);submit(id);review(id,null,"REJECTED","修改信息");
        Long replacement=user("replacement","STUDENT");asStudent(student);registrations.update(id,registrationDto(List.of(replacement)));accept(id,replacement);
        asStudent(student);registrations.cancel(id);asAdmin();
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?",majorId+100000,replacement);
        assertEquals(2L,statistics.overview().getRegistrationPeopleCount());
        // A second submission by the never-submitted replacement must increase the unique people count.
        jdbc.update("UPDATE sys_user SET major_id=? WHERE id=?",majorId,replacement);apply(replacement,otherTeacher);asAdmin();assertEquals(3L,statistics.overview().getRegistrationPeopleCount());
    }
    @Test void originalMajorAdministratorCanRevokeHistoricalAwardAfterRegistrationMovesMajor() throws Exception {
        Long id=apply(student,teacher);review(id,teacher,"APPROVED",null);Long prize=award(id);Long manager=user("history_manager","MAJOR_ADMIN");
        jdbc.update("UPDATE registration SET major_id=? WHERE id=?",majorId+100000,id);
        UserContext.set(new LoginUser(manager,"test","MAJOR_ADMIN"));awards.revoke(prize,"原专业撤销旧获奖");assertEquals("REVOKED",awards.getById(prize).getStatus());
    }

    @AfterEach
    void cleanup() throws java.io.IOException {
        UserContext.clear();
        if (admin != null) {
            for (String name : jdbc.queryForList("SELECT storage_name FROM sys_file WHERE uploader_id=?", String.class, admin)) {
                var root = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "competition-teacher-tests").toAbsolutePath().normalize();
                var target = root.resolve(name).normalize();
                if (!target.startsWith(root)) throw new IllegalStateException("测试文件超出临时目录");
                java.nio.file.Files.deleteIfExists(target);
            }
            jdbc.update("DELETE FROM sys_file WHERE uploader_id=?", admin);
        }
        if (competitionId != null) {
            jdbc.update("DELETE FROM team_invitation WHERE registration_id IN (SELECT id FROM registration WHERE competition_id=?)", competitionId);
            jdbc.update("DELETE FROM withdrawal_request WHERE registration_id IN (SELECT id FROM registration WHERE competition_id=?)", competitionId);
            jdbc.update("DELETE FROM business_history WHERE biz_type='REGISTRATION' AND biz_id IN (SELECT id FROM registration WHERE competition_id=?)", competitionId);
            jdbc.update("DELETE FROM business_history WHERE biz_type='GUIDANCE' AND biz_id IN (SELECT id FROM guidance WHERE competition_id=?)", competitionId);
            jdbc.update("DELETE FROM business_history WHERE biz_type='AWARD' AND biz_id IN (SELECT id FROM award WHERE competition_id=?)", competitionId);
            jdbc.update("DELETE FROM business_history WHERE biz_type='COMPETITION' AND biz_id=?", competitionId);
            jdbc.update("DELETE FROM award WHERE competition_id=?", competitionId);
            jdbc.update("DELETE FROM guidance WHERE competition_id=?", competitionId);
            jdbc.update("DELETE FROM guidance_quota WHERE competition_id=?", competitionId);
            jdbc.update("DELETE FROM registration_member WHERE competition_id=?", competitionId);
            jdbc.update("DELETE FROM registration WHERE competition_id=?", competitionId);
            jdbc.update("DELETE FROM competition WHERE id=?", competitionId);
        }
        for (Long id : userIds) { jdbc.update("DELETE FROM message WHERE receiver_id=?", id); jdbc.update("DELETE FROM sys_user WHERE id=?", id); }
        if (majorId != null) jdbc.update("DELETE FROM sys_major WHERE id=?", majorId);
    }
}
