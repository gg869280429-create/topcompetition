# 生产数据库初始化

`competition-test.sql` 适用于 MySQL 8.x 的新数据库，或已经创建但尚无业务表的 `competition-test` 数据库。它包含从本地实际数据库导出的21张表结构，只初始化一个启用的 `ADMIN` 系统账号 `root`，不导入本地用户、专业、竞赛、消息、附件记录等业务数据。

`root` 是网页系统登录账号，不是要求使用的 MySQL 连接账号。初始口令按本次要求设置，SQL 只保存 BCrypt 哈希，不包含明文口令，也不创建或修改 MySQL 用户。

## 导入

1. 确认目标是正确的生产实例；如果目标库已有表或数据，先备份并另行制定迁移方案，不使用本初始化脚本覆盖。
2. 将 SQL 文件放到数据库服务器可访问的位置。使用有建库、建表及插入权限的 MySQL 账号导入；密码由客户端交互输入。
3. 使用 MySQL 客户端批处理方式执行，不启用 `--force`，发生任何错误立即停止处理。以下为 Linux 或 Windows CMD 命令，在项目根目录执行：

```sh
mysql --default-character-set=utf8mb4 --host=YOUR_DB_HOST --user=YOUR_DB_USER -p < sql/production/competition-test.sql
```

PowerShell 不支持上述输入重定向，可在 CMD 中运行，或使用数据库客户端的 SQL 文件导入功能，并启用“出错停止”。脚本没有删除表、清空数据、覆盖用户或修改 MySQL 密码的语句；遇到已有同名表会报错停止，不是可反复执行的迁移脚本。

如果 MySQL 账号没有建库权限，应先由数据库管理员创建同名空库，并去掉脚本的 `CREATE DATABASE IF NOT EXISTS` 语句后再导入；保留 `USE`，不要修改业务建表及初始化账号语句。

## 后端配置

使用包含本轮改动的后端，启用 `prod`。`application-prod.yml` 已配置：

```yaml
app:
  bootstrap-default-users:
    enabled: false
```

此设置避免生产启动额外创建默认账号。启动环境需要提供：

| 环境变量 | 内容 |
| --- | --- |
| `PROD_DB_URL` | 指向 `competition-test` 的 JDBC URL |
| `PROD_DB_USERNAME` | MySQL 连接账号，与网页系统 root 无关 |
| `PROD_DB_PASSWORD` | MySQL 连接密码 |
| `PROD_JWT_SECRET` | 独立随机 JWT 密钥，至少32字节 |

在 `backend/` 执行：

```sh
mvn package
java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=prod
```

真实本机 YAML 不打入 JAR；测试、生产 YAML 只包含环境变量占位符。完整配置方式见 [分环境配置说明](../../docs/local-configuration.md)。

## 验证与交付边界

导入后可检查：

```sql
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'competition-test' AND table_type = 'BASE TABLE';

SELECT id, username, role, status FROM `competition-test`.sys_user;
```

应有21张表、1个启用的 `root / ADMIN` 系统账号，其他表均为空。空生产库需要管理员先新增专业、教师、学生及竞赛，再开展完整业务。

本次在本机独立库完成 MySQL 客户端导入、全部建表定义比对、重复导入拒绝验证，以及 `prod` 配置真实启动、管理员登录和5个已认证查询接口检查；确认启动后仍只有1个用户。临时验证库已清理，本地业务库未修改。

2026-11-20 初始化结构已补充 `sys_user.avatar_url` 可空字段，头像 OSS 默认关闭，配置说明见 [私有 OSS 头像](../../docs/oss-avatar.md)。已有生产库应先备份并执行 [09 增量迁移](../migrations/20261120_09_user_avatar.md)，不要重新导入初始化 SQL。

尚未连接、导入或验证实际生产服务器。生产 MySQL 账号权限、网络、版本、TLS、环境变量和部署 JAR 均需在目标环境核对。该 SQL 是初始化快照，不替代后续增量迁移。
