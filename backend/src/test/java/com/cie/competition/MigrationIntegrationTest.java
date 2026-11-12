package com.cie.competition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.main.banner-mode=off", "logging.level.org.springframework.boot.autoconfigure.security=ERROR", "logging.level.com.cie.competition.common.config.DataInitializer=ERROR"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class MigrationIntegrationTest extends IsolatedDatabaseTest {
    @Autowired DataSource dataSource;

    @Test
    void incrementalMigrationsPreserveLegacyDataAndBuildUsableSnapshotsAndIndexes() throws Exception {
        Path root = Path.of("../sql");
        String schema = Files.readString(root.resolve("init.sql"));
        Map<String, Set<String>> additions = Map.of(
                "sys_user", Set.of("token_version"),
                "competition", Set.of("manager_id", "cancellation_reason", "cancelled_at", "major_id"),
                "registration", Set.of("leader_name_snapshot", "major_name_snapshot"),
                "registration_member", Set.of("active_key", "current_member", "name_snapshot", "major_id_snapshot", "major_name_snapshot"),
                "guidance", Set.of("active_key", "ended_time", "end_reason", "members_snapshot"),
                "content", Set.of("official_url", "carousel_enabled", "sort_order"),
                "award", Set.of("registrant_id", "review_time", "major_id", "major_name_snapshot", "team_name_snapshot", "participants_snapshot", "revocation_reason", "achievement_name", "achievement_key", "legacy_record", "official_list_file_id", "registration_screenshot_file_id", "process_photo_file_ids"));
        for (var entry : additions.entrySet()) {
            var pattern = Pattern.compile("(?s)(CREATE TABLE IF NOT EXISTS `" + entry.getKey() + "` \\(.*?;)" );
            var match = pattern.matcher(schema);
            assertTrue(match.find());
            String original = match.group(1), legacy = original;
            for (String column : entry.getValue()) legacy = legacy.replaceAll("(?m)^\\s*`" + column + "`[^\\r\\n]*\\R", "");
            schema = schema.replace(original, legacy);
        }
        for (String table : Set.of("team_invitation", "withdrawal_request", "business_history", "award_student", "award_teacher", "award_celebration", "participation_result"))
            schema = schema.replaceAll("(?s)CREATE TABLE IF NOT EXISTS `" + table + "`.*?;", "");
        schema = schema.replace("(`competition_id`, `user_id`, `active_key`)", "(`competition_id`, `user_id`)")
                .replace("`uk_achievement` (`competition_id`, `registration_id`, `achievement_key`)", "`uk_competition_registration` (`competition_id`, `registration_id`)")
                .replace("(`registration_id`, `teacher_id`, `active_key`)", "(`registration_id`, `teacher_id`)");
        schema = schema.replaceAll("(?m)^\\s*KEY `idx_classification_major`[^\\r\\n]*\\R", "");
        schema = schema.replaceAll("(?im)^CREATE DATABASE[^;]*;", "").replaceAll("(?im)^USE\\s+[^;]*;", "");
        String name = "competition_test_migration_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = dataSource.getConnection()) {
            String originalCatalog = connection.getCatalog();
            try (var creation = connection.createStatement()) {
                creation.execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4");
            }
            connection.setCatalog(name);
            try (var statement = connection.createStatement()) {
                execute(connection, schema);
                statement.executeUpdate("INSERT INTO sys_major(id,name) VALUES(1,'原专业')");
                statement.executeUpdate("INSERT INTO sys_user(id,username,password,real_name,role,major_id) VALUES(1,'legacy','test-only','原学生','STUDENT',1),(2,'mentor','test-only','原教师','TEACHER',1)");
                statement.executeUpdate("INSERT INTO competition(id,title,publisher_id) VALUES(1,'历史竞赛',2)");
                statement.executeUpdate("INSERT INTO registration(id,competition_id,leader_id,major_id,team_name,status,review_comment,update_time) VALUES(1,1,1,1,'历史团队','APPROVED','原审核意见','2020-01-02 03:04:05')");
                statement.executeUpdate("INSERT INTO registration_member(registration_id,competition_id,user_id,member_role) VALUES(1,1,1,'LEADER')");
                statement.executeUpdate("INSERT INTO guidance(registration_id,competition_id,teacher_id,status) VALUES(1,1,2,'ACTIVE')");
                statement.executeUpdate("INSERT INTO award(competition_id,registration_id,award_level,status,review_comment) VALUES(1,1,'FIRST','APPROVED','历史获奖意见')");
                execute(connection, Files.readString(root.resolve("migrations/20261110_01_competition.sql")));
                execute(connection, Files.readString(root.resolve("migrations/20261110_02_workflows.sql")));
                try (var result = statement.executeQuery("SELECT team_name,review_comment,update_time,leader_name_snapshot,major_name_snapshot FROM registration WHERE id=1")) {
                    assertTrue(result.next());
                    assertEquals("历史团队", result.getString(1)); assertEquals("原审核意见", result.getString(2));
                    assertEquals("2020-01-02 03:04:05", result.getTimestamp(3).toString().substring(0, 19));
                    assertEquals("原学生", result.getString(4)); assertEquals("原专业", result.getString(5));
                }
                try (var result = statement.executeQuery("SELECT major_id,participants_snapshot,registrant_id,review_time FROM award WHERE id=1")) {
                    assertTrue(result.next()); assertEquals(1, result.getLong(1));
                    assertTrue(result.getString(2).contains("原学生")); assertNull(result.getObject(3)); assertNull(result.getObject(4));
                }
                try (var result = statement.executeQuery("SELECT COUNT(*) FROM business_history WHERE operation='MIGRATED_BASELINE'")) {
                    assertTrue(result.next()); assertEquals(2, result.getInt(1));
                }
                statement.executeUpdate("INSERT INTO registration_member(registration_id,competition_id,user_id,active_key) VALUES(2,1,1,NULL),(3,1,1,NULL)");
                assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("INSERT INTO registration_member(registration_id,competition_id,user_id,active_key) VALUES(4,1,1,1)"));
                statement.executeUpdate("INSERT INTO guidance(registration_id,competition_id,teacher_id,status,active_key) VALUES(1,1,2,'ENDED',NULL)");
                assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("INSERT INTO guidance(registration_id,competition_id,teacher_id,status,active_key) VALUES(1,1,2,'ACTIVE',1)"));
                for (String migration : java.util.List.of("03a_management", "03b_content", "03c_awards", "03d_guidance"))
                    execute(connection, Files.readString(root.resolve("migrations/20261111_" + migration + ".sql")));
                execute(connection, Files.readString(root.resolve("migrations/20261112_04_student_profile.sql")));
                try (var result = statement.executeQuery("SELECT password,token_version FROM sys_user WHERE id=1")) {
                    assertTrue(result.next()); assertEquals("test-only",result.getString(1)); assertEquals(0,result.getLong(2));
                }
                try (var result=statement.executeQuery("SELECT legacy_record,review_comment,participants_snapshot FROM award WHERE id=1")) {
                    assertTrue(result.next());assertEquals(1,result.getInt(1));assertEquals("历史获奖意见",result.getString(2));assertTrue(result.getString(3).contains("原学生"));
                }
                try (var result=statement.executeQuery("SELECT members_snapshot FROM guidance WHERE status='ACTIVE'")) {
                    assertTrue(result.next());assertTrue(result.getString(1).contains("原学生"));
                }
                try (var result=statement.executeQuery("SELECT major_id FROM competition WHERE id=1")) {
                    assertTrue(result.next());assertNull(result.getObject(1));
                }
                statement.executeUpdate("INSERT INTO award(competition_id,registration_id,award_level,achievement_key) VALUES(1,1,'FIRST','first'),(1,1,'FIRST','second')");
                assertThrows(java.sql.SQLException.class,()->statement.executeUpdate("INSERT INTO award(competition_id,registration_id,award_level,achievement_key) VALUES(1,1,'FIRST','first')"));
            } finally {
                connection.setCatalog(originalCatalog);
            }
        }
    }

    private static void execute(Connection connection, String script) {
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }
}
