# 本地配置与凭据更换

主配置 application.yml 的 DB_URL、DB_USERNAME、DB_PASSWORD、JWT_SECRET 必须由环境提供，没有密码或密钥默认值。

本地启动：在 backend 目录执行 `mvn spring-boot:run "-Dspring-boot.run.profiles=local"`。
该 profile 从 application-local.yml 加载本机配置；该文件已加入忽略规则，不得提交。新机器可复制 application-local.example.yml 后填写本机值。

不使用 local profile 时，在部署环境的秘密管理功能配置上述四个环境变量，再启动后端。不要把真实值写进启动脚本、命令历史或提交记录。

本次已更换本机数据库当前连接账户密码与 JWT 签名密钥；新值仅保存在忽略的本地配置中。旧签名不能被新后端接受，用户需要重新登录。使用同一数据库账户的其他本机工具也须从本地配置同步密码。

业务流程升级的数据库迁移须另行确认后执行；不能用新建库脚本覆盖已有库。凭据变更不会代替业务结构迁移。

Git 历史若包含旧凭据，应视为已泄露。本次先更换凭据；清理历史、强制推送或修改远端均需另行确认。
