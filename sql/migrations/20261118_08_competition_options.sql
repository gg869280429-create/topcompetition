-- 先完成07迁移并停止后端写入，备份及恢复演练后执行一次；不改变已有跨专业设置。
ALTER TABLE competition ADD COLUMN all_majors TINYINT NOT NULL DEFAULT 0 COMMENT '明确选择全部专业，区别历史未分类';
ALTER TABLE competition ALTER COLUMN allow_cross_major SET DEFAULT 1;
