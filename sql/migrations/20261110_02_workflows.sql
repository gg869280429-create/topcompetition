-- 第二批工作流增量迁移，适用 MySQL 8.x，输入结构为已完成第一批迁移的旧库。
-- 禁止直接向现有业务库运行：先读 docs/business-integrity-migration.md，完成只读核查、备份、
-- 独立库演练，并取得针对目标库与本脚本的单次人工执行确认。客户端连接时明确指定目标库。
-- 本脚本不选择数据库、不清库、不覆盖原业务列；非幂等，禁止使用客户端 --force 或失败后整段重跑。
-- 阶段 A/C 的 DDL 会隐式提交，ROLLBACK 无法撤销；阶段 B 仅回填新增列并追加基线历史。

-- 阶段 A：新增字段及历史表，暂时保留旧唯一索引。
ALTER TABLE registration
    ADD COLUMN leader_name_snapshot VARCHAR(64) DEFAULT NULL,
    ADD COLUMN major_name_snapshot VARCHAR(64) DEFAULT NULL;

ALTER TABLE registration_member
    ADD COLUMN active_key TINYINT DEFAULT NULL COMMENT '有效资格为1，释放后为NULL',
    ADD COLUMN current_member TINYINT NOT NULL DEFAULT 1,
    ADD COLUMN name_snapshot VARCHAR(64) DEFAULT NULL,
    ADD COLUMN major_id_snapshot BIGINT DEFAULT NULL,
    ADD COLUMN major_name_snapshot VARCHAR(64) DEFAULT NULL;

ALTER TABLE guidance
    ADD COLUMN active_key TINYINT DEFAULT 1,
    ADD COLUMN ended_time DATETIME DEFAULT NULL,
    ADD COLUMN end_reason VARCHAR(1000) DEFAULT NULL;

ALTER TABLE award
    ADD COLUMN registrant_id BIGINT DEFAULT NULL,
    ADD COLUMN review_time DATETIME DEFAULT NULL,
    ADD COLUMN major_id BIGINT DEFAULT NULL,
    ADD COLUMN major_name_snapshot VARCHAR(64) DEFAULT NULL,
    ADD COLUMN team_name_snapshot VARCHAR(128) DEFAULT NULL,
    ADD COLUMN participants_snapshot TEXT,
    ADD COLUMN revocation_reason VARCHAR(1000) DEFAULT NULL;

CREATE TABLE team_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    inviter_id BIGINT NOT NULL,
    invitee_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_invitee_status (invitee_id, status),
    KEY idx_invitation_registration (registration_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE withdrawal_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    registration_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(1000) NOT NULL,
    reviewer_id BIGINT DEFAULT NULL,
    review_comment VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_withdrawal_registration (registration_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE business_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    operation VARCHAR(64) NOT NULL,
    actor_id BIGINT DEFAULT NULL,
    actor_name VARCHAR(64) DEFAULT NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    before_json MEDIUMTEXT,
    after_json MEDIUMTEXT,
    reason VARCHAR(1000) DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_history_business (biz_type, biz_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 阶段 B：维护窗口内停止业务写入；本段仅含 InnoDB DML，可整体回滚。
-- 缺失关联保留 NULL；当前身份/专业仅作为迁移日基线，不是历史发生时的真实快照。
START TRANSACTION;

UPDATE registration r
LEFT JOIN sys_user u ON u.id = r.leader_id
LEFT JOIN sys_major m ON m.id = r.major_id
SET r.leader_name_snapshot = COALESCE(NULLIF(u.real_name,''),u.username),
    r.major_name_snapshot = m.name,
    r.update_time = r.update_time;

UPDATE registration_member rm
LEFT JOIN registration r ON r.id = rm.registration_id
LEFT JOIN sys_user u ON u.id = rm.user_id
LEFT JOIN sys_major m ON m.id = u.major_id
SET rm.active_key = CASE WHEN r.status IN ('PENDING', 'APPROVED') THEN 1 ELSE NULL END,
    rm.current_member = 1,
    rm.name_snapshot = COALESCE(NULLIF(u.real_name,''),u.username),
    rm.major_id_snapshot = u.major_id,
    rm.major_name_snapshot = m.name;

UPDATE guidance g
SET g.active_key = CASE WHEN g.status = 'ACTIVE' THEN 1 ELSE NULL END,
    g.update_time = g.update_time;

-- 旧登记人、获奖审核时间、指导终止时间及原因均无可靠来源，保持新增列 NULL。
UPDATE award a
LEFT JOIN registration r ON r.id = a.registration_id
LEFT JOIN sys_major m ON m.id = r.major_id
LEFT JOIN (
    SELECT registration_id,
           JSON_ARRAYAGG(JSON_OBJECT('userId', user_id, 'name', name_snapshot,
               'majorId', major_id_snapshot, 'majorName', major_name_snapshot)) AS members
    FROM registration_member
    GROUP BY registration_id
) roster ON roster.registration_id = a.registration_id
SET a.major_id = r.major_id,
    a.major_name_snapshot = m.name,
    a.team_name_snapshot = r.team_name,
    a.participants_snapshot = COALESCE(roster.members, JSON_ARRAY()),
    a.update_time = a.update_time;

-- MIGRATED_BASELINE 是迁移时刻对旧记录当前状态的观察，不补造早期审批事件。
-- actor_id 不猜测为原审核人；原审核人/时间/意见逐字段原样保存于 after_json。
INSERT INTO business_history
    (biz_type, biz_id, operation, actor_id, actor_name, before_json, after_json, reason)
SELECT 'REGISTRATION', r.id, 'MIGRATED_BASELINE', NULL, '迁移基线', NULL,
       JSON_OBJECT('snapshotSource', 'MIGRATION_CURRENT_STATE',
           'id', r.id, 'competitionId', r.competition_id, 'teamName', r.team_name,
           'leaderId', r.leader_id, 'majorId', r.major_id, 'crossMajor', r.cross_major,
           'preferredTeacherId', r.preferred_teacher_id, 'status', r.status,
           'applyTime', r.apply_time, 'reviewerId', r.reviewer_id,
           'reviewTime', r.review_time, 'reviewComment', r.review_comment,
           'leaderNameSnapshot', r.leader_name_snapshot, 'majorNameSnapshot', r.major_name_snapshot,
           'createTime', r.create_time, 'updateTime', r.update_time,
           'members', (SELECT JSON_ARRAYAGG(JSON_OBJECT('userId',rm.user_id,'name',rm.name_snapshot,'majorId',rm.major_id_snapshot,'majorName',rm.major_name_snapshot)) FROM registration_member rm WHERE rm.registration_id=r.id)),
       '原记录迁移基线；姓名与专业来自迁移时当前资料，无法还原此前变更。'
FROM registration r;

INSERT INTO business_history
    (biz_type, biz_id, operation, actor_id, actor_name, before_json, after_json, reason)
SELECT 'AWARD', a.id, 'MIGRATED_BASELINE', NULL, '迁移基线', NULL,
       JSON_OBJECT('snapshotSource', 'MIGRATION_CURRENT_STATE',
           'id', a.id, 'competitionId', a.competition_id, 'registrationId', a.registration_id,
           'awardLevel', a.award_level, 'certificateFileId', a.certificate_file_id,
           'status', a.status, 'awardTime', a.award_time, 'reviewerId', a.reviewer_id,
           'reviewTime', a.review_time, 'reviewComment', a.review_comment,
           'registrantId', a.registrant_id, 'majorId', a.major_id,
           'majorNameSnapshot', a.major_name_snapshot, 'teamNameSnapshot', a.team_name_snapshot,
           'participantsSnapshot', CAST(a.participants_snapshot AS JSON),
           'createTime', a.create_time, 'updateTime', a.update_time),
       '原记录迁移基线；历史登记人和审核时间未知，姓名与专业仅为迁移时当前资料。'
FROM award a;

COMMIT;

-- 阶段 C：同一条 ALTER 原子替换各自索引；NULL 允许保留多条已释放的历史成员/指导记录。
ALTER TABLE registration_member
    DROP INDEX uk_competition_user,
    ADD UNIQUE KEY uk_competition_user (competition_id, user_id, active_key);

ALTER TABLE guidance
    DROP INDEX uk_registration_teacher,
    ADD UNIQUE KEY uk_registration_teacher (registration_id, teacher_id, active_key);
