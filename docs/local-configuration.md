# 本地配置与凭据更换

人工保存的启动、构建和测试日志统一放在项目根目录 `logs/`。例如在 `backend` 目录执行 PowerShell 命令：`mvn spring-boot:run *> ../logs/backend-local.log`；在 `frontend` 目录执行：`npm run dev *> ../logs/frontend-local.log`。执行前确保根目录的 `logs/` 已存在。该目录被 Git 忽略。IDE 控制台输出无需另存；需要导出时同样保存到 `logs/`。

主配置 application.yml 的 DB_URL、DB_USERNAME、DB_PASSWORD、JWT_SECRET 没有密码或密钥默认值。默认启用 local，读取本机忽略文件；显式选择 test 或 prod 时，分别由对应环境变量提供配置。

本地启动：在 backend 目录执行 `mvn spring-boot:run`，也可显式执行 `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`。
该 profile 从 application-local.yml 加载本机配置；该文件已加入忽略规则，不得提交。新机器可复制 application-local.example.yml 后填写本机值。

打包时排除真实的 application-local.yml / application-local.yaml，避免忽略的本机凭据被带入 JAR。IDE 和 `mvn spring-boot:run` 仍从本地类路径读取该配置。如需在 backend 目录用 JAR 本地启动，应显式加载外部本机配置：`java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=local --spring.config.additional-location=file:./src/main/resources/`。生产 JAR 必须使用 prod 环境及其环境变量。

环境切换：修改 application.yml 中的 `spring.profiles.default` 为 `local`、`test` 或 `prod`。IDE 的 Active profiles、环境变量 `SPRING_PROFILES_ACTIVE` 或启动参数指定的环境优先于此默认值。每次只选择一个环境，不要同时启用 local 和 prod。

| 环境 | 配置文件 | 所需环境变量 |
| --- | --- | --- |
| 本机 local | application-local.yml（不提交） | 文件内填写本机值，示例见 application-local.example.yml |
| 测试 test | application-test.yml | TEST_DB_URL、TEST_DB_USERNAME、TEST_DB_PASSWORD、TEST_JWT_SECRET |
| 生产 prod | application-prod.yml | PROD_DB_URL、PROD_DB_USERNAME、PROD_DB_PASSWORD、PROD_JWT_SECRET |

测试启动：`mvn spring-boot:run "-Dspring-boot.run.profiles=test"`。

生产启动：`java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=prod`（先运行 `mvn package`）。

测试和生产启动前，在 IDE 的环境变量设置或部署环境的秘密管理功能中提供对应变量。数据库 URL 格式示例：`jdbc:mysql://localhost:3306/YOUR_TEST_DATABASE?useSSL=false&serverTimezone=Asia/Shanghai`。密码和 JWT 密钥不提供默认值；两套环境应使用不同的数据库和随机密钥。不要把真实值写进受 Git 跟踪的 YAML、启动脚本、命令历史或提交记录。缺少变量时启动失败是预期行为。

配置切换不会创建数据库、复制数据或执行迁移。已有业务库的结构升级仍需另行确认。

本次已更换本机数据库当前连接账户密码与 JWT 签名密钥；新值仅保存在忽略的本地配置中。旧签名不能被新后端接受，用户需要重新登录。使用同一数据库账户的其他本机工具也须从本地配置同步密码。

业务流程升级的数据库迁移须另行确认后执行；不能用新建库脚本覆盖已有库。凭据变更不会代替业务结构迁移。

Git 历史若包含旧凭据，应视为已泄露。本次先更换凭据；清理历史、强制推送或修改远端均需另行确认。
