-- 信息工程学院竞赛管理平台 数据库初始化脚本
-- MySQL 8.x

CREATE DATABASE IF NOT EXISTS `competition` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE `competition`;

-- ======================================================================
-- 一、系统基础表
-- ======================================================================

-- 专业表
CREATE TABLE IF NOT EXISTS `sys_major` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  NOT NULL COMMENT '专业名称',
    `code`        VARCHAR(32)  DEFAULT NULL COMMENT '专业代码',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '专业简介',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 停用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '专业表';

-- 系统用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    `password`    VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
    `real_name`   VARCHAR(64)  DEFAULT NULL COMMENT '真实姓名',
    `role`        VARCHAR(32)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：ADMIN/MAJOR_ADMIN/TEACHER/STUDENT',
    `major_id`    BIGINT       DEFAULT NULL COMMENT '所属专业 ID',
    `email`       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `expertise`   VARCHAR(255) DEFAULT NULL,
    `biography`   TEXT DEFAULT NULL,
    `phone`       VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 正常，0 禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_major_id` (`major_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表';

-- ======================================================================
-- 二、竞赛与报名
-- ======================================================================

-- 竞赛表
CREATE TABLE IF NOT EXISTS `competition` (
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`                   VARCHAR(128) NOT NULL COMMENT '竞赛名称',
    `competition_type`        VARCHAR(16)  NOT NULL DEFAULT 'TEAM' COMMENT '赛制：PERSONAL 个人赛 / TEAM 团队赛',
    `level`                   VARCHAR(16)  NOT NULL DEFAULT 'SCHOOL' COMMENT '级别：SCHOOL 校级 / CITY 市级 / PROVINCE 省级 / NATIONAL 国家级 / INTERNATIONAL 国际级',
    `category`                VARCHAR(64)  DEFAULT NULL COMMENT '学科类别',
    `description`             TEXT         COMMENT '竞赛简介',
    `organizer`               VARCHAR(128) DEFAULT NULL COMMENT '主办单位',
    `document_file_id`        BIGINT DEFAULT NULL,
    `cover_file_id`           BIGINT       DEFAULT NULL COMMENT '封面图片文件 ID',
    `registration_start_time` DATETIME     DEFAULT NULL COMMENT '报名开始时间',
    `registration_end_time`   DATETIME     DEFAULT NULL COMMENT '报名截止时间',
    `start_time`              DATETIME     DEFAULT NULL COMMENT '竞赛开始时间',
    `end_time`                DATETIME     DEFAULT NULL COMMENT '竞赛结束时间',
    `max_members`             INT          NOT NULL DEFAULT 1 COMMENT '团队人数上限（个人赛为 1）',
    `max_teachers`            INT          NOT NULL DEFAULT 1 COMMENT '每团队指导教师数量上限',
    `allow_cross_major`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许跨专业组队：1 是，0 否',
    `status`                  VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / REGISTERING 报名中 / IN_PROGRESS 进行中 / ENDED 已结束',
    `publisher_id`            BIGINT       DEFAULT NULL COMMENT '发布人用户 ID',
    `manager_id`              BIGINT       DEFAULT NULL COMMENT '指定负责人用户 ID',
    `cancellation_reason`     VARCHAR(1000) DEFAULT NULL,
    `cancelled_at`            DATETIME DEFAULT NULL,
    `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_publisher` (`publisher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '竞赛表';

-- 报名表
CREATE TABLE IF NOT EXISTS `registration` (
    `preferred_teacher_id` BIGINT DEFAULT NULL,
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `competition_id` BIGINT       NOT NULL COMMENT '竞赛 ID',
    `team_name`      VARCHAR(128) NOT NULL COMMENT '团队名称（个人赛可为姓名）',
    `leader_id`      BIGINT       NOT NULL COMMENT '队长 / 负责人用户 ID',
    `major_id`       BIGINT       DEFAULT NULL COMMENT '所属专业 ID',
    `cross_major`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否跨专业：1 是，0 否',
    `leader_name_snapshot` VARCHAR(64) DEFAULT NULL,
    `major_name_snapshot` VARCHAR(64) DEFAULT NULL,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
    `apply_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
    `review_time`    DATETIME     DEFAULT NULL COMMENT '审核时间',
    `reviewer_id`    BIGINT       DEFAULT NULL COMMENT '审核人用户 ID',
    `review_comment` VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_competition` (`competition_id`),
    KEY `idx_leader` (`leader_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报名表';

-- 团队成员表
CREATE TABLE IF NOT EXISTS `registration_member` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `registration_id` BIGINT      NOT NULL COMMENT '报名 ID',
    `competition_id`  BIGINT      NOT NULL COMMENT '竞赛 ID（冗余，用于唯一约束防止重复报名）',
    `user_id`         BIGINT      NOT NULL COMMENT '学生用户 ID',
    `member_role`     VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT '角色：LEADER 队长 / MEMBER 成员',
    `active_key`      TINYINT DEFAULT NULL COMMENT '有效资格为1，释放后为NULL',
    `current_member`  TINYINT NOT NULL DEFAULT 1,
    `name_snapshot` VARCHAR(64) DEFAULT NULL,
    `major_id_snapshot` BIGINT DEFAULT NULL,
    `major_name_snapshot` VARCHAR(64) DEFAULT NULL,
    `join_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_competition_user` (`competition_id`, `user_id`, `active_key`),
    KEY `idx_registration` (`registration_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '团队成员表';

-- ======================================================================
-- 三、指导与获奖
-- ======================================================================

-- 指导教师名额表
CREATE TABLE IF NOT EXISTS `guidance_quota` (
    `id`             BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
    `competition_id` BIGINT   NOT NULL COMMENT '竞赛 ID',
    `teacher_id`     BIGINT   NOT NULL COMMENT '教师用户 ID',
    `quota`          INT      NOT NULL DEFAULT 0 COMMENT '可指导团队名额数',
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_competition_teacher` (`competition_id`, `teacher_id`),
    KEY `idx_teacher` (`teacher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '指导教师名额表';

-- 指导关系表（师生匹配）
CREATE TABLE IF NOT EXISTS `guidance` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `registration_id` BIGINT      NOT NULL COMMENT '报名 ID',
    `competition_id`  BIGINT      DEFAULT NULL COMMENT '竞赛 ID（冗余，用于名额统计）',
    `teacher_id`      BIGINT      NOT NULL COMMENT '教师用户 ID',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 指导中 / ENDED 已结束',
    `assigner_id`     BIGINT      DEFAULT NULL COMMENT '分配人用户 ID',
    `assign_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '分配时间',
    `active_key`      TINYINT DEFAULT 1,
    `ended_time`      DATETIME DEFAULT NULL,
    `end_reason`      VARCHAR(1000) DEFAULT NULL,
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_registration_teacher` (`registration_id`, `teacher_id`, `active_key`),
    KEY `idx_teacher` (`teacher_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '指导关系表';

-- 获奖表
CREATE TABLE IF NOT EXISTS `award` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `competition_id`      BIGINT       NOT NULL COMMENT '竞赛 ID',
    `registration_id`     BIGINT       NOT NULL COMMENT '报名 ID',
    `award_level`         VARCHAR(32)  NOT NULL COMMENT '奖项等级：SPECIAL 特等奖 / FIRST 一等奖 / SECOND 二等奖 / THIRD 三等奖 / EXCELLENT 优秀奖',
    `certificate_file_id` BIGINT       DEFAULT NULL COMMENT '获奖证书文件 ID',
    `status`              VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
    `award_time`          DATETIME     DEFAULT NULL COMMENT '获奖时间',
    `reviewer_id`         BIGINT       DEFAULT NULL COMMENT '审核人用户 ID',
    `review_comment`      VARCHAR(255) DEFAULT NULL COMMENT '审核意见',
    `registrant_id` BIGINT DEFAULT NULL,
    `review_time` DATETIME DEFAULT NULL,
    `major_id` BIGINT DEFAULT NULL,
    `major_name_snapshot` VARCHAR(64) DEFAULT NULL,
    `team_name_snapshot` VARCHAR(128) DEFAULT NULL,
    `participants_snapshot` TEXT,
    `revocation_reason` VARCHAR(1000) DEFAULT NULL,
    `create_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_competition_registration` (`competition_id`, `registration_id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '获奖表';

CREATE TABLE IF NOT EXISTS `team_invitation` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `registration_id` BIGINT NOT NULL,
    `inviter_id` BIGINT NOT NULL,
    `invitee_id` BIGINT NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `responded_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_invitee_status` (`invitee_id`,`status`),
    KEY `idx_invitation_registration` (`registration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `withdrawal_request` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `registration_id` BIGINT NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `reason` VARCHAR(1000) NOT NULL,
    `reviewer_id` BIGINT DEFAULT NULL,
    `review_comment` VARCHAR(1000) DEFAULT NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `review_time` DATETIME DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_withdrawal_registration` (`registration_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `business_history` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `biz_type` VARCHAR(32) NOT NULL,
    `biz_id` BIGINT NOT NULL,
    `operation` VARCHAR(64) NOT NULL,
    `actor_id` BIGINT DEFAULT NULL,
    `actor_name` VARCHAR(64) DEFAULT NULL,
    `operation_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `before_json` MEDIUMTEXT,
    `after_json` MEDIUMTEXT,
    `reason` VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_history_business` (`biz_type`,`biz_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ======================================================================
-- 四、内容、消息、文件与日志
-- ======================================================================

-- 内容表（新闻 / 通知 / 公告）
CREATE TABLE IF NOT EXISTS `content` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`          VARCHAR(128) NOT NULL COMMENT '标题',
    `content`        MEDIUMTEXT   COMMENT '正文（富文本）',
    `cover_file_id`  BIGINT       DEFAULT NULL COMMENT '封面图片文件 ID',
    `category`       VARCHAR(32)  NOT NULL DEFAULT 'NOTICE' COMMENT '分类：NOTICE 通知 / NEWS 新闻 / ANNOUNCEMENT 公告',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布',
    `author_id`      BIGINT       DEFAULT NULL COMMENT '作者用户 ID',
    `publish_time`   DATETIME     DEFAULT NULL COMMENT '发布时间',
    `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '浏览次数',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_category` (`category`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '内容表';

-- 站内消息表
CREATE TABLE IF NOT EXISTS `message` (
    `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sender_id`   BIGINT        NOT NULL DEFAULT 0 COMMENT '发送人用户 ID（0 表示系统）',
    `receiver_id` BIGINT        NOT NULL COMMENT '接收人用户 ID',
    `title`       VARCHAR(128)  NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(1024) DEFAULT NULL COMMENT '消息内容',
    `type`        VARCHAR(32)   NOT NULL DEFAULT 'SYSTEM' COMMENT '类型：SYSTEM 系统 / NOTICE 通知 / AUDIT 审核',
    `biz_type`    VARCHAR(32)   DEFAULT NULL COMMENT '关联业务类型',
    `biz_id`      BIGINT        DEFAULT NULL COMMENT '关联业务 ID',
    `is_read`     TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已读：1 是，0 否',
    `send_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_receiver` (`receiver_id`, `is_read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内消息表';

-- 文件表
CREATE TABLE IF NOT EXISTS `sys_file` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `storage_name`  VARCHAR(255) NOT NULL COMMENT '存储文件名（唯一）',
    `url`           VARCHAR(512) NOT NULL COMMENT '访问 URL',
    `size`          BIGINT       NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
    `content_type`  VARCHAR(128) DEFAULT NULL COMMENT 'MIME 类型',
    `biz_type`      VARCHAR(32)  DEFAULT NULL COMMENT '业务类型：COMPETITION_COVER / CONTENT_COVER / CERTIFICATE 等',
    `uploader_id`   BIGINT       DEFAULT NULL COMMENT '上传人用户 ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_name` (`storage_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文件表';

-- 操作日志表
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '操作用户 ID',
    `username`    VARCHAR(64)  DEFAULT NULL COMMENT '操作用户名',
    `operation`   VARCHAR(64)  DEFAULT NULL COMMENT '操作描述',
    `method`      VARCHAR(255) DEFAULT NULL COMMENT '请求方法',
    `params`      TEXT         COMMENT '请求参数',
    `ip`          VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：1 成功，0 失败',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- ======================================================================
-- 说明：
-- 1. 默认管理员账号由后端 DataInitializer 首次启动时自动创建（admin / admin123）。
-- 2. 表间关系通过逻辑外键（ID 引用）维护，物理外键约束不建立，
--    数据一致性由后端事务 + 数据库唯一约束（uk_*）共同保证。
-- 3. 关键并发约束：
--    - uk_competition_user：同一竞赛同一学生仅能报名一次（防重复报名 / 团队成员唯一性）。
--    - uk_competition_teacher：同一竞赛同一教师的指导名额仅一份。
--    - uk_registration_teacher：同一团队对同一教师仅一条指导关系。
--    - uk_competition_registration：同一团队在同一竞赛下仅一条获奖记录。
-- ======================================================================
