# 本机业务库迁移执行记录（2026-11-11）

用户明确授权“在数据库中加上”后，对本机 MySQL 的 `competition` 数据库执行以下两份增量脚本：

1. `sql/migrations/20261110_01_competition.sql`
2. `sql/migrations/20261110_02_workflows.sql`

执行前暂停 8080 后端写入，完成有效成员/指导重复、结构及存储引擎等前置核查。生成完整逻辑备份，包含表结构、数据、索引及已有存储对象；随后在独立库恢复备份，校验原有所有列的数据摘要一致，并在恢复库再次演练两份脚本，验证原值不变后才执行原业务库迁移。

备份位置：`output/backups/competition-before-workflows-43ad02ed59c74b7f879a3bfee993231f.sql`，29370 字节。恢复验证库：`competition_restore_43ad02ed59c74b7f879a3bfee993231f`。备份包含业务数据，保留在 Git 忽略目录，不提交。

结果：新增 3 张表、20 个字段，调整有效资格唯一索引，回填人员和专业基线快照及迁移历史。未清库、未删除历史记录、未更改原业务列。逐表比较原有 12 张表全部原列的 SHA-256 摘要及记录数，迁移前后完全一致。

| 原有表 | 保留记录数 |
| --- | ---: |
| award | 2 |
| competition | 8 |
| content | 4 |
| guidance | 4 |
| guidance_quota | 7 |
| message | 6 |
| operation_log | 0 |
| registration | 6 |
| registration_member | 11 |
| sys_file | 0 |
| sys_major | 6 |
| sys_user | 17 |

迁移后校验有效成员/指导标识、获奖 JSON 快照、报名及获奖迁移基线历史通过；按新版初始化结构比对缺失表及字段为空。后端已恢复在 8080 运行，连接仍为原 `competition` 库；四种角色及未登录状态下相同的 145 次 GET 网络请求复测，0 次 500。权限拒绝和材料缺失等业务响应仍按规则返回，不将其冒充正常业务成功。

报名 ID 71 原本缺少归属专业，其队长当前专业也为空，本次保留原值。该历史数据问题需明确真实归属后单独修正。

执行阶段、脚本及备份摘要、迁移前后逐表比对证据保存在 `output/approved-migration-result.json`；接口复测证据保存在 `output/current-readonly-api-audit.json`，均不含连接密码或 Token。迁移脚本非幂等，不能重复执行。
