ALTER TABLE award
 ADD COLUMN achievement_name VARCHAR(160) DEFAULT NULL,
 ADD COLUMN achievement_key CHAR(64) DEFAULT NULL,
 ADD COLUMN legacy_record TINYINT NOT NULL DEFAULT 0,
 ADD COLUMN official_list_file_id BIGINT DEFAULT NULL,
 ADD COLUMN registration_screenshot_file_id BIGINT DEFAULT NULL,
 ADD COLUMN process_photo_file_ids TEXT;
UPDATE award SET legacy_record=1,update_time=update_time;
ALTER TABLE award DROP INDEX uk_competition_registration,
 ADD UNIQUE KEY uk_achievement (competition_id,registration_id,achievement_key);
CREATE TABLE award_student (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, award_id BIGINT NOT NULL, student_id BIGINT NOT NULL,
 name_snapshot VARCHAR(64), major_id_snapshot BIGINT, major_name_snapshot VARCHAR(64),
 UNIQUE KEY uk_award_student(award_id,student_id),KEY idx_student(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE award_teacher (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, award_id BIGINT NOT NULL, teacher_id BIGINT NOT NULL,
 name_snapshot VARCHAR(64), UNIQUE KEY uk_award_teacher(award_id,teacher_id),KEY idx_teacher(teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE award_celebration (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, award_id BIGINT NOT NULL,teacher_id BIGINT NOT NULL,
 displayed_time DATETIME DEFAULT NULL,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE KEY uk_award_teacher(award_id,teacher_id),KEY idx_pending(teacher_id,displayed_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
