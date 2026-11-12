-- 在已完成03a至03d迁移的数据库执行；保留原有账号、密码及业务记录。
ALTER TABLE sys_user ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0 COMMENT '密码变更后的登录凭证版本' AFTER password;
