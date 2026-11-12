-- 在05迁移之后执行；新增报名之前的组队记录，不修改原报名名单。
CREATE TABLE `student_team` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `creator_id` BIGINT NOT NULL,
    `captain_id` BIGINT DEFAULT NULL,
    `nominated_captain_id` BIGINT DEFAULT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'FORMING',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_team_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生报名之前的组队';
CREATE TABLE `student_team_member` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `team_id` BIGINT NOT NULL,
    `user_id` BIGINT NOT NULL,
    `invited_by` BIGINT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `responded_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_member` (`team_id`,`user_id`),
    KEY `idx_team_member_user` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生组队成员及邀请状态，历次变更保存在业务历史';
ALTER TABLE registration ADD COLUMN source_team_id BIGINT DEFAULT NULL COMMENT '来源学生组队ID，报名名单独立保存';
