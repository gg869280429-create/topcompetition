-- Production initialization; MySQL 8.x; empty target database only.
-- Structure exported from local MySQL; no local business data is included.
-- The sole application administrator is root; password is stored as BCrypt.
-- Existing table errors must stop import; do not use --force.
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS `competition` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `competition-test`;

CREATE TABLE `award` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `competition_id` bigint NOT NULL COMMENT '竞赛 ID',
  `registration_id` bigint NOT NULL COMMENT '报名 ID',
  `award_level` varchar(32) NOT NULL COMMENT '奖项等级：SPECIAL 特等奖 / FIRST 一等奖 / SECOND 二等奖 / THIRD 三等奖 / EXCELLENT 优秀奖',
  `certificate_file_id` bigint DEFAULT NULL COMMENT '获奖证书文件 ID',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
  `award_time` datetime DEFAULT NULL COMMENT '获奖时间',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人用户 ID',
  `review_comment` varchar(255) DEFAULT NULL COMMENT '审核意见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `registrant_id` bigint DEFAULT NULL,
  `review_time` datetime DEFAULT NULL,
  `major_id` bigint DEFAULT NULL,
  `major_name_snapshot` varchar(64) DEFAULT NULL,
  `team_name_snapshot` varchar(128) DEFAULT NULL,
  `participants_snapshot` text,
  `revocation_reason` varchar(1000) DEFAULT NULL,
  `achievement_name` varchar(160) DEFAULT NULL,
  `achievement_key` char(64) DEFAULT NULL,
  `legacy_record` tinyint NOT NULL DEFAULT '0',
  `official_list_file_id` bigint DEFAULT NULL,
  `registration_screenshot_file_id` bigint DEFAULT NULL,
  `process_photo_file_ids` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_achievement` (`competition_id`,`registration_id`,`achievement_key`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='获奖表';

CREATE TABLE `award_celebration` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `award_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `displayed_time` datetime DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_award_teacher` (`award_id`,`teacher_id`),
  KEY `idx_pending` (`teacher_id`,`displayed_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `award_student` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `award_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `name_snapshot` varchar(64) DEFAULT NULL,
  `major_id_snapshot` bigint DEFAULT NULL,
  `major_name_snapshot` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_award_student` (`award_id`,`student_id`),
  KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `award_teacher` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `award_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `name_snapshot` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_award_teacher` (`award_id`,`teacher_id`),
  KEY `idx_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `business_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_type` varchar(32) NOT NULL,
  `biz_id` bigint NOT NULL,
  `operation` varchar(64) NOT NULL,
  `actor_id` bigint DEFAULT NULL,
  `actor_name` varchar(64) DEFAULT NULL,
  `operation_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `before_json` mediumtext,
  `after_json` mediumtext,
  `reason` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_history_business` (`biz_type`,`biz_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `competition` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(128) NOT NULL COMMENT '竞赛名称',
  `competition_type` varchar(16) NOT NULL DEFAULT 'TEAM' COMMENT '赛制：PERSONAL 个人赛 / TEAM 团队赛',
  `level` varchar(16) NOT NULL DEFAULT 'SCHOOL' COMMENT '级别：SCHOOL 校级 / CITY 市级 / PROVINCE 省级 / NATIONAL 国家级 / INTERNATIONAL 国际级',
  `category` varchar(64) DEFAULT NULL COMMENT '学科类别',
  `description` text COMMENT '竞赛简介',
  `organizer` varchar(128) DEFAULT NULL COMMENT '主办单位',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面图片文件 ID',
  `registration_start_time` datetime DEFAULT NULL COMMENT '报名开始时间',
  `registration_end_time` datetime DEFAULT NULL COMMENT '报名截止时间',
  `start_time` datetime DEFAULT NULL COMMENT '竞赛开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '竞赛结束时间',
  `max_members` int NOT NULL DEFAULT '1' COMMENT '团队人数上限（个人赛为 1）',
  `max_teachers` int NOT NULL DEFAULT '1' COMMENT '每团队指导教师数量上限',
  `allow_cross_major` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许跨专业组队：1 是，0 否',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / REGISTERING 报名中 / IN_PROGRESS 进行中 / ENDED 已结束',
  `publisher_id` bigint DEFAULT NULL COMMENT '发布人用户 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `document_file_id` bigint DEFAULT NULL,
  `manager_id` bigint DEFAULT NULL COMMENT '指定竞赛负责人',
  `cancellation_reason` varchar(1000) DEFAULT NULL COMMENT '取消原因',
  `cancelled_at` datetime DEFAULT NULL COMMENT '取消时间',
  `major_id` bigint DEFAULT NULL COMMENT '分类展示专业，不改变报名审核归属',
  `all_majors` tinyint NOT NULL DEFAULT '0' COMMENT '明确选择全部专业，区别历史未分类',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_publisher` (`publisher_id`),
  KEY `idx_classification_major` (`major_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='竞赛表';

CREATE TABLE `content` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` mediumtext COMMENT '正文（富文本）',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面图片文件 ID',
  `category` varchar(32) NOT NULL DEFAULT 'NOTICE' COMMENT '分类：NOTICE 通知 / NEWS 新闻 / ANNOUNCEMENT 公告',
  `status` varchar(16) NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布',
  `author_id` bigint DEFAULT NULL COMMENT '作者用户 ID',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `official_url` varchar(2048) DEFAULT NULL,
  `carousel_enabled` tinyint NOT NULL DEFAULT '0',
  `sort_order` int NOT NULL DEFAULT '0',
  `publish_start_time` datetime DEFAULT NULL COMMENT '计划发布开始',
  `publish_end_time` datetime DEFAULT NULL COMMENT '新闻下线时间',
  `carousel_start_time` datetime DEFAULT NULL COMMENT '轮播展示开始',
  `carousel_end_time` datetime DEFAULT NULL COMMENT '轮播展示结束',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_category` (`category`),
  KEY `idx_content_schedule` (`status`,`publish_start_time`,`publish_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='内容表';

CREATE TABLE `guidance` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `registration_id` bigint NOT NULL COMMENT '报名 ID',
  `competition_id` bigint DEFAULT NULL COMMENT '竞赛 ID（冗余，用于名额统计）',
  `teacher_id` bigint NOT NULL COMMENT '教师用户 ID',
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 指导中 / ENDED 已结束',
  `assigner_id` bigint DEFAULT NULL COMMENT '分配人用户 ID',
  `assign_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `active_key` tinyint DEFAULT '1',
  `ended_time` datetime DEFAULT NULL,
  `end_reason` varchar(1000) DEFAULT NULL,
  `members_snapshot` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_registration_teacher` (`registration_id`,`teacher_id`,`active_key`),
  KEY `idx_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指导关系表';

CREATE TABLE `guidance_quota` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `competition_id` bigint NOT NULL COMMENT '竞赛 ID',
  `teacher_id` bigint NOT NULL COMMENT '教师用户 ID',
  `quota` int NOT NULL DEFAULT '0' COMMENT '可指导团队名额数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_teacher` (`competition_id`,`teacher_id`),
  KEY `idx_teacher` (`teacher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='指导教师名额表';

CREATE TABLE `message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sender_id` bigint NOT NULL DEFAULT '0' COMMENT '发送人用户 ID（0 表示系统）',
  `receiver_id` bigint NOT NULL COMMENT '接收人用户 ID',
  `title` varchar(128) NOT NULL COMMENT '消息标题',
  `content` varchar(1024) DEFAULT NULL COMMENT '消息内容',
  `type` varchar(32) NOT NULL DEFAULT 'SYSTEM' COMMENT '类型：SYSTEM 系统 / NOTICE 通知 / AUDIT 审核',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '关联业务类型',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务 ID',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '是否已读：1 是，0 否',
  `send_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_receiver` (`receiver_id`,`is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内消息表';

CREATE TABLE `operation_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '操作用户 ID',
  `username` varchar(64) DEFAULT NULL COMMENT '操作用户名',
  `operation` varchar(64) DEFAULT NULL COMMENT '操作描述',
  `method` varchar(255) DEFAULT NULL COMMENT '请求方法',
  `params` text COMMENT '请求参数',
  `ip` varchar(64) DEFAULT NULL COMMENT '客户端 IP',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功：1 成功，0 失败',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='操作日志表';

CREATE TABLE `participation_result` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `guidance_id` bigint NOT NULL,
  `registration_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  `teacher_id` bigint NOT NULL,
  `student_name_snapshot` varchar(64) DEFAULT NULL,
  `participated` tinyint NOT NULL,
  `won` tinyint NOT NULL DEFAULT '0',
  `remark` varchar(1000) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_guidance_student` (`guidance_id`,`student_id`),
  KEY `idx_participation_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `registration` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `competition_id` bigint NOT NULL COMMENT '竞赛 ID',
  `team_name` varchar(128) NOT NULL COMMENT '团队名称（个人赛可为姓名）',
  `leader_id` bigint NOT NULL COMMENT '队长 / 负责人用户 ID',
  `major_id` bigint DEFAULT NULL COMMENT '所属专业 ID',
  `cross_major` tinyint NOT NULL DEFAULT '0' COMMENT '是否跨专业：1 是，0 否',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
  `apply_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  `review_time` datetime DEFAULT NULL COMMENT '审核时间',
  `reviewer_id` bigint DEFAULT NULL COMMENT '审核人用户 ID',
  `review_comment` varchar(255) DEFAULT NULL COMMENT '审核意见',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `preferred_teacher_id` bigint DEFAULT NULL,
  `leader_name_snapshot` varchar(64) DEFAULT NULL,
  `major_name_snapshot` varchar(64) DEFAULT NULL,
  `roster_locked_at` datetime DEFAULT NULL COMMENT '教师确认参赛或完成指导后的名单锁定时间',
  `roster_locked_by` bigint DEFAULT NULL COMMENT '锁定名单的教师',
  `source_team_id` bigint DEFAULT NULL COMMENT '来源学生组队ID，报名名单独立保存',
  PRIMARY KEY (`id`),
  KEY `idx_competition` (`competition_id`),
  KEY `idx_leader` (`leader_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报名表';

CREATE TABLE `registration_member` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `registration_id` bigint NOT NULL COMMENT '报名 ID',
  `competition_id` bigint NOT NULL COMMENT '竞赛 ID（冗余，用于唯一约束防止重复报名）',
  `user_id` bigint NOT NULL COMMENT '学生用户 ID',
  `member_role` varchar(16) NOT NULL DEFAULT 'MEMBER' COMMENT '角色：LEADER 队长 / MEMBER 成员',
  `join_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `active_key` tinyint DEFAULT NULL COMMENT '有效资格为1，释放后为NULL',
  `current_member` tinyint NOT NULL DEFAULT '1',
  `name_snapshot` varchar(64) DEFAULT NULL,
  `major_id_snapshot` bigint DEFAULT NULL,
  `major_name_snapshot` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_user` (`competition_id`,`user_id`,`active_key`),
  KEY `idx_registration` (`registration_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='团队成员表';

CREATE TABLE `student_team` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(128) NOT NULL,
  `creator_id` bigint NOT NULL,
  `captain_id` bigint DEFAULT NULL,
  `nominated_captain_id` bigint DEFAULT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'FORMING',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_team_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生报名之前的组队';

CREATE TABLE `student_team_member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `team_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `invited_by` bigint NOT NULL,
  `status` varchar(20) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `responded_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`team_id`,`user_id`),
  KEY `idx_team_member_user` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学生组队成员及邀请状态，历次变更保存在业务历史';

CREATE TABLE `sys_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `storage_name` varchar(255) NOT NULL COMMENT '存储文件名（唯一）',
  `url` varchar(512) NOT NULL COMMENT '访问 URL',
  `size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `content_type` varchar(128) DEFAULT NULL COMMENT 'MIME 类型',
  `biz_type` varchar(32) DEFAULT NULL COMMENT '业务类型：COMPETITION_COVER / CONTENT_COVER / CERTIFICATE 等',
  `uploader_id` bigint DEFAULT NULL COMMENT '上传人用户 ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_storage_name` (`storage_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文件表';

CREATE TABLE `sys_major` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '专业名称',
  `code` varchar(32) DEFAULT NULL COMMENT '专业代码',
  `description` varchar(255) DEFAULT NULL COMMENT '专业简介',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用，0 停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `icon_code` varchar(32) DEFAULT NULL COMMENT '专业预设图标',
  `logo_file_id` bigint DEFAULT NULL COMMENT '专业Logo文件',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业表';

CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(64) NOT NULL COMMENT '登录用户名',
  `password` varchar(128) NOT NULL COMMENT '密码（BCrypt 加密）',
  `token_version` bigint NOT NULL DEFAULT '0' COMMENT '密码变更后的登录凭证版本',
  `real_name` varchar(64) DEFAULT NULL COMMENT '真实姓名',
  `role` varchar(32) NOT NULL DEFAULT 'STUDENT' COMMENT '角色：ADMIN/MAJOR_ADMIN/TEACHER/STUDENT',
  `major_id` bigint DEFAULT NULL COMMENT '所属专业 ID',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 正常，0 禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `expertise` varchar(255) DEFAULT NULL,
  `biography` text,
  `class_name` varchar(64) DEFAULT NULL COMMENT '学生班级',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_major_id` (`major_id`),
  KEY `idx_student_class` (`role`,`major_id`,`class_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

CREATE TABLE `team_invitation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `inviter_id` bigint NOT NULL,
  `invitee_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `responded_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_invitee_status` (`invitee_id`,`status`),
  KEY `idx_invitation_registration` (`registration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `withdrawal_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registration_id` bigint NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `reason` varchar(1000) NOT NULL,
  `reviewer_id` bigint DEFAULT NULL,
  `review_comment` varchar(1000) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `review_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_withdrawal_registration` (`registration_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

START TRANSACTION;
INSERT INTO `sys_user` (`id`,`username`,`password`,`real_name`,`role`,`status`,`token_version`) VALUES (1,'root','$2a$10$RoaZYI7PcWxlP9xpNl7/Me/bXYPUsEgInUotdXSIozB2KZzJV3nxi','系统管理员','ADMIN',1,0);
COMMIT;
