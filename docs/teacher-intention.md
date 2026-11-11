# 教师库与意向教师使用说明

## 操作流程

1. 管理员进入“教师库”，新增或编辑教师姓名、账号、专业、方向和简介；新增账号需要设置至少8个字符的初始密码。
2. 在“竞赛管理”新增或编辑竞赛，配置教师及可指导团队数，可上传一份不超过10MB的 PDF/DOC/DOCX 竞赛文件。保存后发布。教师无需报名。
3. 学生从首页查看竞赛详情、文件和教师名单；报名时必须选择一位意向教师。意向不占名额，即使多个团队选择同一教师，也由管理员最终确认。
4. 管理员在“报名审核”确认最终教师。默认选择有剩余名额的意向教师；调剂需填写原因。审核、分配、消息通知在同一事务中完成，名额不足则全部回滚。
5. 学生在报名详情查看意向和最终教师；教师在“指导管理”查看自己的指导竞赛、名额和团队。双方收到站内消息。
6. 驳回后队长可修改意向和报名信息重提或取消。审核通过后的指导变更由管理员在“师生匹配”中选择原教师、目标教师并填写原因，原子完成调剂并通知相关人员。

专业管理员仅能维护本专业教师、配置本专业教师名额、审核本专业报名；账号必须先配置所属专业。停用仍有未结束竞赛或指导任务的教师会被阻止。

## 数据与部署

- 新数据库使用 `sql/init.sql`。
- 已有数据库在更新后端前执行一次 `sql/migrate_teacher_intention.sql`。本次本地数据库已完成迁移，不需要再次执行。
- 历史报名没有意向教师时保留原记录，管理员审核通过时手动选择最终教师。
- 文件默认保存在后端工作目录的 `uploads` 下，通过 `app.upload-directory` 可指定持久化目录。文件目录与数据库需要一并保留和备份，下载须通过有权访问的竞赛接口。
- 上传后未保存竞赛或移除关联的文件不会自动删除。

## 接口变更

- `POST /api/teacher`、`PUT /api/teacher/{id}`：教师档案维护。
- `GET /api/teacher/page`、`GET /api/teacher/majors`：管理员教师库查询。
- 新增和编辑竞赛请求须携带 `teachers: [{teacherId, quota}]`，可携带 `documentFileId`。
- 新增和编辑报名请求须携带 `preferredTeacherId`。
- 审核通过报名须携带 `teacherId`；调剂原因写入 `comment`，驳回须填写 `comment`。
- `GET /api/guidance/teachers?competitionId=...`：该竞赛启用教师的公开档案及名额。
- `GET /api/guidance/quota/mine`：教师本人的竞赛安排。
- `POST /api/file`：管理员上传竞赛文件；`GET /api/competition/{id}/document`：鉴权下载。
- 删除教师自行报名接口 `POST /api/guidance/signup`。
- 原 `DELETE /api/guidance/{id}` 改为 `PUT /api/guidance/{id}`，提交 `{registrationId, teacherId, comment}` 原子调剂；不再通过先取消后新增进行换教师。

## 验证

后端基础校验：在 `backend` 下运行 `mvn test`。

数据库与接口回归需要本地 MySQL 配置可用，在 PowerShell 中运行：

```powershell
$env:RUN_DB_TESTS = 'true'
mvn test '-Dlogging.level.org.springframework.boot.autoconfigure.security=ERROR'
```

集成测试使用独立随机标识的新建记录，结束后清理自己的教师、专业、竞赛、报名、指导、通知及临时文件。

前端构建：在 `frontend` 下运行 `npm run build`。

本次18项自动化用例覆盖意向必填与关联校验、意向不占名额、审核确认、调剂原因、名额不足回滚、驳回重提、历史兼容、教师库权限、专业范围、停用鉴权、文件权限、名额保护、并发名额、停用并发及锁顺序。浏览器已验证管理员配置竞赛、学生选择教师报名、管理员审核、教师查看团队和学生教师库403。

本次聚焦教师库与意向指导流程；此前审查发现的报名时间、跨专业成员等其他问题不属于本次已修复范围。
