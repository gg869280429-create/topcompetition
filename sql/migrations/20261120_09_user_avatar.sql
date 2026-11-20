-- 已有数据库执行一次；不修改旧用户数据，不保存临时签名 URL。
ALTER TABLE sys_user ADD COLUMN avatar_url VARCHAR(1024) DEFAULT NULL COMMENT '头像OSS永久地址（不含签名）';
