-- 已有数据库执行一次；不删除或覆盖原有业务数据。
ALTER TABLE sys_user ADD COLUMN expertise VARCHAR(255) DEFAULT NULL;
ALTER TABLE sys_user ADD COLUMN biography TEXT DEFAULT NULL;
ALTER TABLE registration ADD COLUMN preferred_teacher_id BIGINT DEFAULT NULL;
ALTER TABLE competition ADD COLUMN document_file_id BIGINT DEFAULT NULL;
