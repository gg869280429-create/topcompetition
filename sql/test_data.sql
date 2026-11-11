-- 测试数据脚本（可重复执行前建议先清空业务数据）
-- 覆盖：专业、用户、竞赛、报名、指导、获奖、内容、消息
USE `competition`;
SET NAMES utf8mb4;

-- 密码统一 admin123（复用现有 BCrypt 哈希）
SET @pwd = '$2a$10$CUj0M/m9lWg0auzNsZS2nuz1AHcqB6J4lllxSSpUuGYCrmBgsRrz.';

-- ======================================================================
-- 一、专业
-- ======================================================================
INSERT INTO sys_major (name, code, description) VALUES
('计算机科学与技术', 'CS', '计算机方向'),
('软件工程', 'SE', '软件方向'),
('网络工程', 'NE', '网络方向'),
('电子信息工程', 'EI', '电子方向'),
('通信工程', 'CE', '通信方向'),
('人工智能', 'AI', '人工智能方向');

SET @m_cs = (SELECT id FROM sys_major WHERE code = 'CS');
SET @m_se = (SELECT id FROM sys_major WHERE code = 'SE');
SET @m_ne = (SELECT id FROM sys_major WHERE code = 'NE');
SET @m_ei = (SELECT id FROM sys_major WHERE code = 'EI');
SET @m_ce = (SELECT id FROM sys_major WHERE code = 'CE');
SET @m_ai = (SELECT id FROM sys_major WHERE code = 'AI');

-- ======================================================================
-- 二、用户（学生 8 名、教师 4 名，密码 admin123）
-- ======================================================================
INSERT INTO sys_user (username, password, real_name, role, major_id, status) VALUES
('student3', @pwd, '张三', 'STUDENT', @m_cs, 1),
('student4', @pwd, '李四', 'STUDENT', @m_se, 1),
('student5', @pwd, '王五', 'STUDENT', @m_ne, 1),
('student6', @pwd, '赵六', 'STUDENT', @m_ei, 1),
('student7', @pwd, '孙七', 'STUDENT', @m_ce, 1),
('student8', @pwd, '周八', 'STUDENT', @m_ai, 1),
('student9', @pwd, '吴九', 'STUDENT', @m_cs, 1),
('student10', @pwd, '郑十', 'STUDENT', @m_se, 1),
('teacher2', @pwd, '陈老师', 'TEACHER', @m_cs, 1),
('teacher3', @pwd, '刘老师', 'TEACHER', @m_se, 1),
('teacher4', @pwd, '黄老师', 'TEACHER', @m_ne, 1),
('teacher5', @pwd, '杨老师', 'TEACHER', @m_ai, 1);

SET @s3 = (SELECT id FROM sys_user WHERE username = 'student3');
SET @s4 = (SELECT id FROM sys_user WHERE username = 'student4');
SET @s5 = (SELECT id FROM sys_user WHERE username = 'student5');
SET @s6 = (SELECT id FROM sys_user WHERE username = 'student6');
SET @s7 = (SELECT id FROM sys_user WHERE username = 'student7');
SET @s8 = (SELECT id FROM sys_user WHERE username = 'student8');
SET @s9 = (SELECT id FROM sys_user WHERE username = 'student9');
SET @s10 = (SELECT id FROM sys_user WHERE username = 'student10');
SET @t2 = (SELECT id FROM sys_user WHERE username = 'teacher2');
SET @t3 = (SELECT id FROM sys_user WHERE username = 'teacher3');
SET @t4 = (SELECT id FROM sys_user WHERE username = 'teacher4');
SET @t5 = (SELECT id FROM sys_user WHERE username = 'teacher5');

-- ======================================================================
-- 三、竞赛（覆盖 REGISTERING / IN_PROGRESS / ENDED / DRAFT）
-- ======================================================================
INSERT INTO competition (title, competition_type, level, category, description, organizer, registration_start_time, registration_end_time, start_time, end_time, max_members, max_teachers, allow_cross_major, status, publisher_id) VALUES
('全国大学生数学建模竞赛', 'TEAM', 'NATIONAL', '数学建模', '全国大学生数学建模竞赛，三人一组', '中国工业与应用数学学会', '2026-11-01 00:00:00', '2026-11-20 23:59:59', '2026-12-01 08:00:00', '2026-12-05 18:00:00', 3, 1, 0, 'REGISTERING', 1),
('ACM 程序设计竞赛', 'TEAM', 'PROVINCE', '程序设计', '省赛 ACM 程序设计竞赛', '省计算机学会', '2026-11-05 00:00:00', '2026-11-25 23:59:59', '2026-12-10 09:00:00', '2026-12-10 17:00:00', 3, 1, 0, 'REGISTERING', 1),
('互联网+创新创业大赛', 'TEAM', 'NATIONAL', '创新创业', '互联网+ 创新创业大赛', '教育部', '2026-11-01 00:00:00', '2026-11-30 23:59:59', '2027-01-05 09:00:00', '2027-01-06 17:00:00', 5, 2, 1, 'REGISTERING', 5),
('蓝桥杯软件赛', 'PERSONAL', 'NATIONAL', '程序设计', '蓝桥杯全国软件和信息技术专业人才大赛', '工业和信息化部人才交流中心', '2026-10-01 00:00:00', '2026-10-20 23:59:59', '2026-11-01 09:00:00', '2026-11-30 18:00:00', 1, 1, 0, 'IN_PROGRESS', 1),
('电子设计大赛', 'TEAM', 'PROVINCE', '电子设计', '省电子设计大赛', '省教育厅', '2026-09-01 00:00:00', '2026-09-20 23:59:59', '2026-10-10 09:00:00', '2026-10-12 17:00:00', 3, 1, 0, 'ENDED', 1),
('机器人创新竞赛', 'TEAM', 'SCHOOL', '机器人', '校级机器人创新竞赛（草稿）', '信息工程学院', '2026-11-10 00:00:00', '2026-11-30 23:59:59', '2026-12-15 09:00:00', '2026-12-15 17:00:00', 4, 2, 0, 'DRAFT', 5);

SET @c_shumo = (SELECT id FROM competition WHERE title = '全国大学生数学建模竞赛');
SET @c_acm = (SELECT id FROM competition WHERE title = 'ACM 程序设计竞赛');
SET @c_hulianwang = (SELECT id FROM competition WHERE title = '互联网+创新创业大赛');
SET @c_lanqiao = (SELECT id FROM competition WHERE title = '蓝桥杯软件赛');
SET @c_dianzi = (SELECT id FROM competition WHERE title = '电子设计大赛');

-- ======================================================================
-- 四、指导教师报名（guidance_quota）
-- ======================================================================
INSERT INTO guidance_quota (competition_id, teacher_id, quota) VALUES
(@c_shumo, @t2, 3),
(@c_acm, @t2, 2),
(@c_acm, @t3, 2),
(@c_hulianwang, @t3, 3),
(@c_hulianwang, @t5, 2),
(@c_lanqiao, @t4, 5);

-- ======================================================================
-- 五、学生报名（registration + registration_member）
-- ======================================================================
-- 数学建模：张三(队长)+李四，已通过
INSERT INTO registration (competition_id, team_name, leader_id, major_id, cross_major, status, apply_time, review_time, reviewer_id) VALUES
(@c_shumo, '建模先锋队', @s3, @m_cs, 0, 'APPROVED', '2026-11-02 10:00:00', '2026-11-03 09:00:00', 1);
SET @r_shumo = LAST_INSERT_ID();
INSERT INTO registration_member (registration_id, competition_id, user_id, member_role) VALUES
(@r_shumo, @c_shumo, @s3, 'LEADER'),
(@r_shumo, @c_shumo, @s4, 'MEMBER');

-- ACM：王五(队长)+赵六+孙七，待审核
INSERT INTO registration (competition_id, team_name, leader_id, major_id, cross_major, status, apply_time) VALUES
(@c_acm, 'AC 战队', @s5, @m_ne, 1, 'PENDING', '2026-11-04 14:00:00');
SET @r_acm = LAST_INSERT_ID();
INSERT INTO registration_member (registration_id, competition_id, user_id, member_role) VALUES
(@r_acm, @c_acm, @s5, 'LEADER'),
(@r_acm, @c_acm, @s6, 'MEMBER'),
(@r_acm, @c_acm, @s7, 'MEMBER');

-- 互联网+：周八(队长)+吴九，已通过
INSERT INTO registration (competition_id, team_name, leader_id, major_id, cross_major, status, apply_time, review_time, reviewer_id) VALUES
(@c_hulianwang, '智创未来', @s8, @m_ai, 1, 'APPROVED', '2026-11-03 16:00:00', '2026-11-04 10:00:00', 5);
SET @r_hulianwang = LAST_INSERT_ID();
INSERT INTO registration_member (registration_id, competition_id, user_id, member_role) VALUES
(@r_hulianwang, @c_hulianwang, @s8, 'LEADER'),
(@r_hulianwang, @c_hulianwang, @s9, 'MEMBER');

-- 蓝桥杯（个人赛）：郑十，已通过
INSERT INTO registration (competition_id, team_name, leader_id, major_id, cross_major, status, apply_time, review_time, reviewer_id) VALUES
(@c_lanqiao, '郑十', @s10, @m_se, 0, 'APPROVED', '2026-10-15 09:00:00', '2026-10-16 09:00:00', 1);
SET @r_lanqiao = LAST_INSERT_ID();
INSERT INTO registration_member (registration_id, competition_id, user_id, member_role) VALUES
(@r_lanqiao, @c_lanqiao, @s10, 'LEADER');

-- 电子设计（已结束）：张三(队长)+赵六，已通过
INSERT INTO registration (competition_id, team_name, leader_id, major_id, cross_major, status, apply_time, review_time, reviewer_id) VALUES
(@c_dianzi, '电子之光', @s3, @m_cs, 0, 'APPROVED', '2026-09-05 10:00:00', '2026-09-06 09:00:00', 1);
SET @r_dianzi = LAST_INSERT_ID();
INSERT INTO registration_member (registration_id, competition_id, user_id, member_role) VALUES
(@r_dianzi, @c_dianzi, @s3, 'LEADER'),
(@r_dianzi, @c_dianzi, @s6, 'MEMBER');

-- ======================================================================
-- 六、指导关系（guidance）
-- ======================================================================
INSERT INTO guidance (registration_id, competition_id, teacher_id, status, assigner_id, assign_time) VALUES
(@r_shumo, @c_shumo, @t2, 'ACTIVE', 1, '2026-11-03 10:00:00'),
(@r_hulianwang, @c_hulianwang, @t3, 'ACTIVE', 5, '2026-11-04 11:00:00'),
(@r_dianzi, @c_dianzi, @t4, 'ENDED', 1, '2026-09-06 10:00:00');

-- ======================================================================
-- 七、获奖（award）
-- ======================================================================
INSERT INTO award (competition_id, registration_id, award_level, certificate_file_id, status, award_time, reviewer_id) VALUES
(@c_dianzi, @r_dianzi, 'FIRST', NULL, 'APPROVED', '2026-10-20 00:00:00', 1),
(@c_lanqiao, @r_lanqiao, 'SECOND', NULL, 'APPROVED', '2026-11-30 00:00:00', 1);

-- ======================================================================
-- 八、内容（content：通知 / 新闻 / 公告）
-- ======================================================================
INSERT INTO content (title, content, category, status, author_id, publish_time, view_count) VALUES
('关于全国大学生数学建模竞赛报名的通知', '请各参赛队伍于 11 月 20 日前完成报名。', 'NOTICE', 'PUBLISHED', 1, '2026-11-01 09:00:00', 120),
('学院举办 ACM 程序设计竞赛赛前培训', '本周六晚在实验楼 301 举办 ACM 赛前培训。', 'NEWS', 'PUBLISHED', 1, '2026-11-02 10:00:00', 86),
('电子设计大赛获奖名单公示', '恭喜以下队伍在电子设计大赛中获奖。', 'ANNOUNCEMENT', 'PUBLISHED', 1, '2026-10-21 09:00:00', 230),
('机器人创新竞赛方案（草稿）', '机器人创新竞赛方案待完善。', 'NEWS', 'DRAFT', 5, NULL, 0);

-- ======================================================================
-- 九、消息（message）
-- ======================================================================
INSERT INTO message (sender_id, receiver_id, title, content, type, biz_type, biz_id, is_read, send_time) VALUES
(0, @s3, '报名审核通过', '你报名的「全国大学生数学建模竞赛」已审核通过。', 'AUDIT', 'REGISTRATION', @r_shumo, 0, '2026-11-03 09:00:00'),
(0, @s5, '报名待审核', '你报名的「ACM 程序设计竞赛」正在审核中。', 'AUDIT', 'REGISTRATION', @r_acm, 0, '2026-11-04 14:00:00'),
(0, @s3, '竞赛通知', '数学建模竞赛将于 12 月 1 日开赛。', 'NOTICE', NULL, NULL, 1, '2026-11-01 09:00:00'),
(0, @s8, '报名审核通过', '你报名的「互联网+创新创业大赛」已审核通过。', 'AUDIT', 'REGISTRATION', @r_hulianwang, 0, '2026-11-04 10:00:00');

-- ======================================================================
-- 完成
-- ======================================================================
SELECT '测试数据插入完成' AS result;
SELECT (SELECT COUNT(*) FROM sys_user) AS 用户数,
       (SELECT COUNT(*) FROM sys_major) AS 专业数,
       (SELECT COUNT(*) FROM competition) AS 竞赛数,
       (SELECT COUNT(*) FROM registration) AS 报名数,
       (SELECT COUNT(*) FROM guidance_quota) AS 教师报名数,
       (SELECT COUNT(*) FROM guidance) AS 指导数,
       (SELECT COUNT(*) FROM award) AS 获奖数,
       (SELECT COUNT(*) FROM content) AS 内容数,
       (SELECT COUNT(*) FROM message) AS 消息数;
