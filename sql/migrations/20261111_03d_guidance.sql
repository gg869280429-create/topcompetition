-- 适用于已完成20261110两份迁移的业务库；先备份并在副本演练，再单独确认执行。
ALTER TABLE guidance ADD COLUMN members_snapshot TEXT DEFAULT NULL;

UPDATE guidance g LEFT JOIN (
    SELECT rm.registration_id, JSON_ARRAYAGG(JSON_OBJECT('userId',rm.user_id,
        'name',COALESCE(rm.name_snapshot,NULLIF(u.real_name,''),u.username),
        'majorId',COALESCE(rm.major_id_snapshot,u.major_id),
        'majorName',COALESCE(rm.major_name_snapshot,m.name))) AS members
    FROM registration_member rm LEFT JOIN sys_user u ON u.id=rm.user_id
    LEFT JOIN sys_major m ON m.id=u.major_id WHERE rm.current_member=1
    GROUP BY rm.registration_id
) roster ON roster.registration_id=g.registration_id
SET g.members_snapshot=COALESCE(roster.members,JSON_ARRAY()),g.update_time=g.update_time;

CREATE TABLE participation_result (
    id BIGINT NOT NULL AUTO_INCREMENT,
    guidance_id BIGINT NOT NULL,
    registration_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    student_name_snapshot VARCHAR(64) DEFAULT NULL,
    participated TINYINT NOT NULL,
    won TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY(id),
    UNIQUE KEY uk_guidance_student(guidance_id,student_id),
    KEY idx_participation_student(student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
