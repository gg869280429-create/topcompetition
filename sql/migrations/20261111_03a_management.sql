-- 新增分类专业，历史竞赛保持NULL，下一次发布/编辑时补录。
ALTER TABLE competition ADD COLUMN major_id BIGINT DEFAULT NULL COMMENT '分类展示专业，不改变报名审核归属', ADD KEY idx_classification_major (major_id);
