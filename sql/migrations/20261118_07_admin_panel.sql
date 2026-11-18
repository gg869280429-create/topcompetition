-- 先完成06迁移，停止后端写入并备份；旧数据保留，未设置的时间不限制展示。
ALTER TABLE sys_user ADD COLUMN class_name VARCHAR(64) DEFAULT NULL COMMENT '学生班级';
ALTER TABLE sys_major ADD COLUMN icon_code VARCHAR(32) DEFAULT NULL COMMENT '专业预设图标', ADD COLUMN logo_file_id BIGINT DEFAULT NULL COMMENT '专业Logo文件';
ALTER TABLE content ADD COLUMN publish_start_time DATETIME DEFAULT NULL COMMENT '计划发布开始',
 ADD COLUMN publish_end_time DATETIME DEFAULT NULL COMMENT '新闻下线时间',
 ADD COLUMN carousel_start_time DATETIME DEFAULT NULL COMMENT '轮播展示开始',
 ADD COLUMN carousel_end_time DATETIME DEFAULT NULL COMMENT '轮播展示结束';
CREATE INDEX idx_student_class ON sys_user(role,major_id,class_name);
CREATE INDEX idx_content_schedule ON content(status,publish_start_time,publish_end_time);
