-- 第一批竞赛增量迁移：仅新增列；由管理员备份后执行，本任务未执行。
ALTER TABLE competition
    ADD COLUMN manager_id BIGINT DEFAULT NULL COMMENT '指定竞赛负责人',
    ADD COLUMN cancellation_reason VARCHAR(1000) DEFAULT NULL COMMENT '取消原因',
    ADD COLUMN cancelled_at DATETIME DEFAULT NULL COMMENT '取消时间';
