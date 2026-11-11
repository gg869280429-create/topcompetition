# 业务完整性增量迁移

本说明供人工审阅和执行。交付文件本身不表示批准写入现有数据库；执行现有库前，须另行确认目标实例、库名、备份和维护窗口，不得覆盖用户现有数据。本次编写未执行数据库、未运行 Maven。主代理将在独立测试库验证第一批结构升级，结果另行记录。

## 适用范围与影响

- 新建空库使用 `sql/init.sql`；已有库不得重跑初始化脚本。
- 第一批结构先应用 `sql/migrations/20261110_01_competition.sql`；第二批使用 `sql/migrations/20261110_02_workflows.sql`。第二批脚本假定旧成员唯一索引为 `(competition_id,user_id)`、旧指导索引为 `(registration_id,teacher_id)`，且第二批字段和新表尚不存在。已有同名字段/表或定制结构须先逐项比对，不能强行执行。
- 第二批新增报名/成员快照、成员有效资格与当前成员标识、指导结束信息、获奖登记/审核/快照/撤销信息，以及邀请、退赛申请、业务历史三张表；与当前初始化脚本对应部分保持一致。
- 原表、原记录和原业务列保留；只回填本次新增列、追加迁移基线历史。显式令原表 `update_time=update_time`，避免新增快照触发原业务更新时间变化。
- 成员唯一索引替换为 `(competition_id,user_id,active_key)`；指导唯一索引替换为 `(registration_id,teacher_id,active_key)`。MySQL 唯一索引允许多条含 NULL 的记录，释放资格后可保留历史再申请。旧索引在回填后由单条 ALTER 原子替换，不会先裸删约束。
- ALTER、索引重建与批量 UPDATE 可能持有元数据锁/行锁、重建表并消耗磁盘、undo、binlog 和复制带宽，耗时需以副本演练估算。没有通过强制 `ALGORITHM` 或 `LOCK` 假设可在线完成。

## 回填含义与局限

`registration_member.active_key` 仅当所属报名当前状态为 `PENDING` / `APPROVED` 时为 1，其他状态及失联报名为 NULL；旧 `REJECTED` 释放占用。原成员行默认 `current_member=1`，表示迁移前仍存储在该报名成员表中的行，并不代表报名当前有效，也不推断迁移前已经删除的成员。`guidance.active_key` 仅 `ACTIVE` 为 1，其他为 NULL；不根据报名或竞赛状态擅自结束历史指导。结束时间和原因无可靠来源，保持 NULL。

报名队长姓名取当前用户姓名，报名专业名称按原 `registration.major_id` 关联当前专业表；成员姓名、专业 ID/名称取当前用户和专业表。获奖专业 ID 归原报名专业，团队名取原报名当前名称，人员快照取现存成员表关联当前用户的迁移基线。人员快照为 JSON 数组，包含 `userId`、`name`、`majorId`、`majorName`；无存储成员时为 `[]`，表示没有可回填的成员行，不证明当年没有成员。JSON 数组不承诺成员排列顺序。

以上均为**迁移时当前资料的基线**，无法恢复过去改名、转专业、成员变更和被物理删除的记录，也不能声称是事件当时的真实快照。缺失关联导致的快照保留 NULL，不猜测、不创建替代用户/专业。已有业务列中的无效关联 ID 原样保留。

旧获奖的登记人未知，`registrant_id` 保持 NULL，不能假定为队长、证书上传人或审核人。旧获奖无独立审核时间，`review_time` 保持 NULL，不能拿 `update_time` 代替。撤销原因保持 NULL，不虚构撤销事件。

每条旧报名和获奖追加一条 `MIGRATED_BASELINE` 历史：操作时间为迁移时刻，操作者 ID 为 NULL，名称为“迁移基线”，`before_json` 为 NULL，`after_json` 标注 `snapshotSource=MIGRATION_CURRENT_STATE` 并保留原状态、原审核人、原审核意见及可用时间。报名原 `review_time` 原样保存；获奖该新字段仍为 NULL。原审核意见不拼接迁移文字、不覆盖为说明，不补造早期审核事件。邀请和退赛申请表初始为空，不伪造邀请接受或退赛流程。

## 执行前只读核查

先在已明确选中的目标连接中执行以下只读语句并保存结果。不要在应用运行时把这里的结果当成稳定迁移快照；正式回填期间需要停止相关写入。查询仅列出问题，不自动更改历史业务值。出现阻断项时先停止迁移，另行审阅数据处置方案，不要删除违规历史来满足约束。

```sql
-- 核对实例/目标库及 SQL 模式；必须使用 MySQL 8.x 和严格模式，避免静默截断。
SELECT @@hostname AS server_host, @@port AS server_port, DATABASE() AS target_schema,
       VERSION() AS mysql_version, @@session.sql_mode AS sql_mode;
SHOW CREATE TABLE registration;
SHOW CREATE TABLE registration_member;
SHOW CREATE TABLE guidance;
SHOW CREATE TABLE award;
SHOW INDEX FROM registration_member;
SHOW INDEX FROM guidance;
SELECT table_name, engine FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('registration','registration_member','guidance','award');
-- 四张原表必须是 InnoDB；确认第二批尚未执行，无同名新增表/新增字段。
SELECT table_name, column_name FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND ((table_name='registration' AND column_name='leader_name_snapshot')
    OR (table_name='registration_member' AND column_name='active_key')
    OR (table_name='guidance' AND column_name='active_key')
    OR (table_name='award' AND column_name='registrant_id'));
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('team_invitation','withdrawal_request','business_history');

-- 关联用户已不存在；不读取密码或凭证列。
SELECT 'REGISTRATION_LEADER' AS source, r.id AS biz_id, r.leader_id AS user_id
FROM registration r LEFT JOIN sys_user u ON u.id=r.leader_id WHERE u.id IS NULL
UNION ALL
SELECT 'MEMBER', rm.id, rm.user_id FROM registration_member rm
LEFT JOIN sys_user u ON u.id=rm.user_id WHERE u.id IS NULL
UNION ALL
SELECT 'GUIDANCE_TEACHER', g.id, g.teacher_id FROM guidance g
LEFT JOIN sys_user u ON u.id=g.teacher_id WHERE u.id IS NULL
UNION ALL
SELECT 'QUOTA_TEACHER', q.id, q.teacher_id FROM guidance_quota q
LEFT JOIN sys_user u ON u.id=q.teacher_id WHERE u.id IS NULL;

-- 专业缺失/失联，以及成员身份/启用状态不合规。
SELECT r.id, r.major_id FROM registration r
LEFT JOIN sys_major m ON m.id=r.major_id WHERE m.id IS NULL;
SELECT rm.id, rm.registration_id, rm.user_id, u.role, u.status, u.major_id,
       m.name AS major_name, m.status AS major_status
FROM registration_member rm LEFT JOIN sys_user u ON u.id=rm.user_id
LEFT JOIN sys_major m ON m.id=u.major_id
WHERE u.id IS NULL OR u.role<>'STUDENT' OR u.status<>1
   OR m.id IS NULL OR m.status<>1;

-- 孤儿报名、孤儿成员、成员冗余竞赛 ID 与报名不一致。
SELECT r.id, r.competition_id FROM registration r
LEFT JOIN competition c ON c.id=r.competition_id WHERE c.id IS NULL;
SELECT rm.id, rm.registration_id, rm.competition_id, r.competition_id AS registration_competition
FROM registration_member rm LEFT JOIN registration r ON r.id=rm.registration_id
WHERE r.id IS NULL OR rm.competition_id<>r.competition_id;

-- 以当前专业资料核查禁跨专业/跨专业标志不符；无法证明历史发生时违规。
SELECT r.id, c.allow_cross_major, r.cross_major,
       COUNT(DISTINCT u.major_id) AS current_major_count
FROM registration r JOIN competition c ON c.id=r.competition_id
JOIN registration_member rm ON rm.registration_id=r.id
LEFT JOIN sys_user u ON u.id=rm.user_id
GROUP BY r.id, c.allow_cross_major, r.cross_major
HAVING (c.allow_cross_major=0 AND COUNT(DISTINCT u.major_id)>1)
    OR r.cross_major<>IF(COUNT(DISTINCT u.major_id)>1,1,0);

-- 报名期缺失、相等或反向；脚本不会自动补报名日期。
SELECT id, status, registration_start_time, registration_end_time FROM competition
WHERE registration_start_time IS NULL OR registration_end_time IS NULL
   OR registration_start_time>=registration_end_time;

-- 有效成员重复：阻断新唯一约束，必须查明，不能直接删历史。
SELECT rm.competition_id, rm.user_id, COUNT(*) AS occupied_rows
FROM registration_member rm JOIN registration r ON r.id=rm.registration_id
WHERE r.status IN ('PENDING','APPROVED')
GROUP BY rm.competition_id, rm.user_id HAVING COUNT(*)>1;
SELECT registration_id, teacher_id, COUNT(*) AS active_rows FROM guidance
WHERE status='ACTIVE' GROUP BY registration_id, teacher_id HAVING COUNT(*)>1;

-- 孤儿指导、冗余竞赛不一致；非有效报名/终态竞赛却仍 ACTIVE 的历史疑点。
SELECT g.id, g.registration_id, g.competition_id, g.teacher_id, g.status,
       r.status AS registration_status, c.status AS competition_status
FROM guidance g LEFT JOIN registration r ON r.id=g.registration_id
LEFT JOIN competition c ON c.id=g.competition_id
LEFT JOIN sys_user u ON u.id=g.teacher_id
WHERE r.id IS NULL OR c.id IS NULL OR u.id IS NULL OR u.role<>'TEACHER'
   OR NOT (g.competition_id <=> r.competition_id)
   OR (g.status='ACTIVE' AND (r.status<>'APPROVED' OR c.status IN ('ENDED','CANCELLED')));

-- 无材料/材料元数据失联、缺日期或未来日期的老获奖；数据库无法证明磁盘文件仍存在。
SELECT a.id, a.status, a.certificate_file_id, a.award_time,
       r.status AS registration_status, r.competition_id AS registration_competition
FROM award a LEFT JOIN sys_file f ON f.id=a.certificate_file_id
LEFT JOIN registration r ON r.id=a.registration_id
WHERE f.id IS NULL OR r.id IS NULL OR r.status<>'APPROVED'
   OR NOT (a.competition_id <=> r.competition_id)
   OR a.award_time IS NULL OR a.award_time>NOW();

-- 获奖人员快照 TEXT 最大65535字节；超限需另行审阅，不能截断丢人。
SELECT roster.registration_id, OCTET_LENGTH(roster.members) AS snapshot_bytes
FROM (
 SELECT rm.registration_id,
        JSON_ARRAYAGG(JSON_OBJECT('userId',rm.user_id,'name',u.real_name,
          'majorId',u.major_id,'majorName',m.name)) AS members
 FROM registration_member rm LEFT JOIN sys_user u ON u.id=rm.user_id
 LEFT JOIN sys_major m ON m.id=u.major_id GROUP BY rm.registration_id
) roster
WHERE OCTET_LENGTH(roster.members)>65535;

-- 保存原记录数量，与迁移后结果比对；另行导出主键/原字段用于精确比对。
SELECT 'registration' AS source, COUNT(*) AS rows_count FROM registration
UNION ALL SELECT 'registration_member', COUNT(*) FROM registration_member
UNION ALL SELECT 'guidance', COUNT(*) FROM guidance
UNION ALL SELECT 'award', COUNT(*) FROM award;
```

无效关联、旧材料缺失、现有专业变更等问题可能不妨碍新增列，但会限制快照完整性及新流程使用，需列入数据问题清单并由负责人决定后续处理。重复有效成员、非预期表结构、非 InnoDB、空间不足、JSON 超长和备份未验证为迁移阻断项。旧报名状态 `REJECTED` 释放资格属于新增有效标识回填规则，不改变旧报名状态。

## 备份与分步执行

1. 盘点实例、库、版本、表/索引定义和记录数，保存只读核查结果及原业务字段导出。确认目标不含未经评估的同名字段/表，评估复制延迟和空闲磁盘。
2. 预先完成可恢复备份，包含建表、数据、索引及实例已有触发器等对象；在隔离实例/唯一测试库验证恢复，不仅检查备份文件存在。连接凭据使用安全的凭据配置，不放入文档、日志或命令输出。
3. 在隔离测试库复制第一批结构和脱敏测试数据，按脚本 A/B/C 顺序演练，覆盖通过/驳回报名、已结束指导、缺失关联、旧获奖材料缺失及审核意见保留。测试库验证不构成批准修改现有业务库。
4. 对**本次目标库和本脚本执行**单独取得人工确认，安排维护窗口、停止应用及相关定时任务写入；重复只读检查并制作一致性备份/记录可恢复位置。不要启动新版本应用抢先写入旧结构。
5. 客户端在连接参数明确指定经确认的数据库，不靠脚本切换默认库。使用遇错停止的批处理执行一次，禁止 `--force`，不要放入应用启动钩子或循环自动重试。脚本不是幂等脚本。
6. 阶段 A 仅执行新增列/新表 DDL，核对定义；阶段 B 在同一连接的 InnoDB 事务内回填新增列并追加历史，成功才 COMMIT；阶段 C 原子替换两条唯一索引。可按脚本标注分段人工执行，必须记录最后成功语句；不要重复执行 B，否则将重复追加迁移基线。
7. 执行后比对原表记录数量、主键和原业务字段导出（尤其原审核意见、审核人、审核时间、update_time），确认原值没有变化；核对新增列、有效标识与基线历史。完成独立库验证与既有库迁移验收后，才部署相应应用并恢复写入。

MySQL DDL 会隐式提交，整份脚本**不能用一个事务整体回滚**。阶段 B 仅在未 COMMIT、且没有混入 DDL 时可 ROLLBACK。若中途失败立即停止，不要为了继续而清表、覆盖或强制重跑；保存错误及已完成步骤，先核查实际结构和事务状态。恢复演练库可从备份重建；现有库的恢复或后续修复同样需要另行确认。业务恢复写入后不能简单恢复旧备份丢弃新记录，也不能盲目降级旧应用依赖的唯一约束语义。

## 迁移后只读验收

```sql
SHOW CREATE TABLE registration_member;
SHOW CREATE TABLE guidance;
SELECT COUNT(*) AS member_key_mismatches
FROM registration_member rm LEFT JOIN registration r ON r.id=rm.registration_id
WHERE NOT (rm.active_key <=> CASE WHEN r.status IN ('PENDING','APPROVED') THEN 1 ELSE NULL END)
   OR rm.current_member<>1;
SELECT COUNT(*) AS guidance_key_mismatches FROM guidance
WHERE NOT (active_key <=> CASE WHEN status='ACTIVE' THEN 1 ELSE NULL END);
SELECT biz_type, operation, COUNT(*) AS rows_count FROM business_history
WHERE operation='MIGRATED_BASELINE' GROUP BY biz_type,operation;
-- 分别与旧报名、旧获奖数量一致；没有伪造旧邀请或退赛申请。
SELECT COUNT(*) AS invitation_rows FROM team_invitation;
SELECT COUNT(*) AS withdrawal_rows FROM withdrawal_request;
SELECT id FROM award WHERE participants_snapshot IS NULL OR NOT JSON_VALID(participants_snapshot);
SELECT COUNT(*) AS guessed_registrants FROM award WHERE registrant_id IS NOT NULL;
SELECT COUNT(*) AS guessed_award_review_times FROM award WHERE review_time IS NOT NULL;
```

本页最后两项只适用于迁移后、应用尚未恢复写入的验收窗口。回填后迁移基线可用于后续比对，仍不能作为当年身份、专业或完整审批轨迹的证明。
