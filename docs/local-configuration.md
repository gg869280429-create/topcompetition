# 分环境 YAML 配置

后端恢复使用 `application.yml` 加 `application-local.yml`、`application-test.yml`、`application-prod.yml`。不再读取 `config.properties`；旧 properties 文件已备份到忽略的 `output/backups/yaml-restoration-*` 下。

## 文件职责

| 文件 | 内容 |
| --- | --- |
| `application.yml` | 公共参数及默认 `local` 环境；数据库/JWT 占位符；上传、JSON、MyBatis、OSS 通用配置。 |
| `application-local.yml` | 本机实际数据库、JWT、OSS 参数，Git 忽略且不打进 JAR。 |
| `application-local.example.yml` | 不含真实凭据的本机模板。 |
| `application-test.yml` | 从 `TEST_*` 环境变量读取测试数据库和 JWT。 |
| `application-prod.yml` | 从 `PROD_*` 环境变量读取生产数据库和 JWT，并关闭默认账号自动初始化。 |

恢复时数据库连接也回到迁移前 YAML 的业务库，保留 JWT 密钥和已填写的 OSS 参数，不更换数据库密码、不重置账号、不改数据库结构。当前 OSS 地域与 Endpoint 不匹配且密钥未齐，先保持 `enabled: false`；核对 Bucket 的实际地域、补齐参数后再启用。

## 本机启动

本机文件不存在时复制模板；已有文件不要覆盖：

```powershell
if (-not (Test-Path backend/src/main/resources/application-local.yml)) {
  Copy-Item backend/src/main/resources/application-local.example.yml backend/src/main/resources/application-local.yml
}
```

填写数据库、用户名、密码和至少32字节的独立随机 JWT 密钥。IDE 设置 Active profiles 为 `local`，或在 `backend/` 执行：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

`local` 是默认环境，不指定时也读取本机 YAML。JAR 不包含实际本机配置，使用 JAR 在 `backend/` 启动时：

```powershell
java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=local --spring.config.additional-location=file:./src/main/resources/
```

## 测试与生产

| profile | 数据库 URL | 数据库用户 | 数据库密码 | JWT 密钥 |
| --- | --- | --- | --- | --- |
| `test` | `TEST_DB_URL` | `TEST_DB_USERNAME` | `TEST_DB_PASSWORD` | `TEST_JWT_SECRET` |
| `prod` | `PROD_DB_URL` | `PROD_DB_USERNAME` | `PROD_DB_PASSWORD` | `PROD_JWT_SECRET` |

在 IDE 环境变量或服务器运行环境中填写，不把真实凭据写到受跟踪的 YAML 或启动脚本。缺少必需配置时启动失败，不提供真实密码默认值。

```powershell
# backend/，先设置对应环境变量
mvn spring-boot:run "-Dspring-boot.run.profiles=test"
java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=prod
```

每次只启用一个环境；可用 IDE、`SPRING_PROFILES_ACTIVE` 或 `--spring.profiles.active` 设置。生产保持 `app.bootstrap-default-users.enabled=false`，已有账号不受影响。配置切换不会创建数据库或迁移数据，更换 JWT 密钥后旧登录凭证失效。

## OSS

在本机 YAML 的 `app.avatar-oss` 填写 Bucket、地域、Endpoint。AccessKey 可填在本机忽略文件，或保留环境变量占位符并提供 `OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`。测试和生产通过公共配置中的占位符读取 OSS 环境变量。默认关闭，开启前确保参数完整且地域与公网 Endpoint 匹配。详见 [OSS 头像](oss-avatar.md)。

## 测试与日志

集成测试使用本机 YAML 中的 MySQL 连接账号，创建随机隔离库；`RUN_DB_TESTS=true` 控制是否执行。不要把数据库测试连接指向生产实例。

日志统一放在根目录 `logs/`，不提交 Git。从 `backend/` 保存日志时使用 `../logs/文件名.log`。测试完成后释放8080端口。
