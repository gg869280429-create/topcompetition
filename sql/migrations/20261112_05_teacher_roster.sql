-- 在04迁移后执行。先备份；只增加名单锁定字段，保留原报名、指导、成员及审核记录。
ALTER TABLE registration ADD COLUMN roster_locked_at DATETIME DEFAULT NULL COMMENT '教师确认参赛或完成指导后的名单锁定时间',
                         ADD COLUMN roster_locked_by BIGINT DEFAULT NULL COMMENT '锁定名单的教师';
-- 旧数据已确认参赛、完成指导或通过获奖的名单保持冻结；升级时间不冒充原确认时间。
UPDATE registration r SET r.roster_locked_at=NOW(), r.update_time=r.update_time
WHERE r.roster_locked_at IS NULL AND (
    EXISTS (SELECT 1 FROM participation_result p WHERE p.registration_id=r.id AND p.participated=1)
 OR EXISTS (SELECT 1 FROM guidance g WHERE g.registration_id=r.id AND g.status='COMPLETED')
 OR EXISTS (SELECT 1 FROM award a WHERE a.registration_id=r.id AND a.status='APPROVED')
);
