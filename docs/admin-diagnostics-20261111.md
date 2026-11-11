# 管理员端报错定位与数据库修复（2026-11-11）

## 原因

当前 IDE 启动的新版后端使用本地 `competition` 库，但该库此前只完成 01、02 迁移，未执行新增功能的 03 系列迁移。实体查询及业务 SQL 已引用新结构，实际数据库缺少 11 个字段、4 张表，导致 MySQL 1054（字段不存在）和 1146（表不存在）。Spring Boot 能启动并不表示所有业务 SQL 能执行；本项目没有配置 Flyway/Liquibase 自动迁移，构建项目或启动应用不会自动执行这些增量 SQL，`CREATE TABLE IF NOT EXISTS` 也不会补已有表的字段。

修复前实际打开管理员端 10 个页面：

| 页面 | 修复前 | 修复后 |
| --- | --- | --- |
| 首页 | 竞赛列表接口 500 | 正常加载 |
| 竞赛管理 | 列表接口 500 | 8 条历史竞赛正常显示 |
| 报名管理 | 列表接口 500，查询会读取关联竞赛 | 6 条历史报名正常显示 |
| 指导管理 | 初始化竞赛选项接口 500 | 竞赛选项正常加载 |
| 获奖记录 | 列表接口 500 | 2 条历史成果正常显示 |
| 新闻管理 | 列表接口 500 | 4 条历史内容正常显示 |
| 统计中心 | 聚合查询新关联表时 500 | 正常加载 |
| 专业管理 | 正常 | 正常 |
| 学生管理 | 正常 | 正常 |
| 教师库 | 列表正常 | 列表及竞赛名额查询正常 |

此前的独立测试库已经执行新迁移，因此测试库能运行、当前业务库仍然报错。这是本次差异的直接原因。

## 已修改的业务库结构

根据用户本轮“如果表里面少了东西，你需要修改我的数据库表结构”的明确授权，已在当前本地 `competition` 库执行以下四份迁移：

| 脚本 | 已补齐内容 |
| --- | --- |
| `20261111_03a_management.sql` | `competition.major_id` 及分类索引 |
| `20261111_03b_content.sql` | `content.official_url`、`carousel_enabled`、`sort_order` |
| `20261111_03c_awards.sql` | `award.achievement_name`、`achievement_key`、`legacy_record`、`official_list_file_id`、`registration_screenshot_file_id`、`process_photo_file_ids`；新增 `award_student`、`award_teacher`、`award_celebration`；成果去重唯一索引替换 |
| `20261111_03d_guidance.sql` | `guidance.members_snapshot`；新增 `participation_result`；建立旧指导的成员基线快照 |

执行前暂停后端，完成全量逻辑备份、恢复到独立库、逐表校验恢复结果并演练四份脚本，再迁移当前业务库。15 张原表的原有记录数及所有原字段的 SHA-256 校验在迁移前后一致。

- 备份：`output/backups/competition-before-feature-upgrade-1f175f3b79b84b3da66dfe4b424cf9e2.sql`，44,345 字节。
- 已验证可恢复的副本：`competition_restore_1f175f3b79b84b3da66dfe4b424cf9e2`。
- 执行记录：`output/feature-approved-migration-result.json`，阶段为 `complete`。
- 未重新初始化业务库；现有账号与密钥保持原值。

## 修复后的实际验证

1. 对照 `sql/init.sql` 检查 **19 张表、206 个字段的名称/类型及字符串长度、37 个业务索引的字段和唯一性**；对照 **12 个 MyBatis 实体类**检查字段映射，差异为 0。
2. 后端使用本地配置重新启动，真实数据库连接正常；真实管理员登录返回 200，签发 Token 后查询登录信息返回 200。
3. **10 个系统管理员页面**逐页加载，均没有接口 500 或未捕获的 JavaScript 错误；专业、学生、教师、竞赛、新闻及成果新增表单可以打开，新闻官网字段与成果材料字段正常展示。
4. 系统管理员、专业管理员分别直连后端和通过前端代理完成 **168 次接口请求**，无 500、无网络连接失败。该检查包含教师/学生专用接口、越权消息和不存在的材料，返回的 400/403 是对应的输入或权限拒绝，不应改为全部成功。
5. 使用系统管理员逐条查询全部 **8 个竞赛、6 个报名、2 项成果、4 条内容和5 位教师**，包括详情、历史、指导名单、各竞赛教师名额及状态筛选，额外 **81 次请求全部返回 200**。内容阅读按原业务逻辑增加阅读数。
6. 重新执行 `$env:RUN_DB_TESTS='true'; mvn -o test`，**183 项测试通过，失败 0、错误 0、跳过 0**。写入、审核、取消等回归使用独立测试库，不操作现有真实报名和成果。

本轮检查期间，原前端开发进程曾因 `ECONNRESET` 退出，已重新启动并验证前端代理正常。未对该单次连接重置作额外的根因结论。

当前后端运行于 8080，前端开发服务运行于 5173。可直接刷新管理员页面；后台进程日志在 `output/admin-backend-after-migration*.log`、`output/admin-frontend-after-migration*.log`，这些本地产物被 Git 忽略。

## 仍需核实的资料

- **专业管理员 ID 5 没有所属专业**：专业业务接口明确返回 403。需要确定真实专业后配置，不能自动猜测或放开跨专业权限；系统管理员本轮 10 个页面正常。
- **报名 ID 71 及其队长缺少专业**：需要人工核实原归属；本次结构迁移未覆盖这条业务资料。
- **8 个旧竞赛的分类待补录**：迁移保留空值，可通过竞赛管理的分类补录入口填写。
- **2 条旧成果**：实际获奖学生、对应教师及新增必传材料需要核实补录；其中晚于当前日期的获奖日期需核实。未补关联不会计入新增学生、教师成果统计。
- **旧新闻**：原发布状态保留，缺官网、封面等资料的新闻需要按新发布规则补录；默认不轮播。
- 现有管理员仍可用代码中的初始口令登录，生产部署应更换初始口令策略及该账号口令；本次没有重置真实用户密码。
- 之前已记录的 Tomcat Native 版本提示、Vite 包体积警告、favicon 404 仍作为环境/资源问题保留，详见[功能验收记录](feature-acceptance-20261111.md)。

证据：`output/admin-schema-code-alignment.json`、`output/admin-diagnostics.json`、`output/admin-browser-pages-before.json`、`output/admin-browser-pages-after.json`、`output/admin-records-check.json`、`output/admin-login-check.json`、`output/admin-final-tests.log`。不将含本地账号辅助信息、备份和日志的整个 `output/` 目录提交 Git。
