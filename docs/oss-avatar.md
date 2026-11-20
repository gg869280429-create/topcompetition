# 私有 OSS 用户头像

学生和教师从个人中心“更换头像”上传，所有登录角色也可以从顶部头像菜单更换。竞赛文件、新闻封面、专业 Logo 和获奖材料继续使用原文件服务。

`POST /api/avatar` 接收 `multipart/form-data`，字段名 `file`，需要登录。用户身份取自登录上下文，不能指定其他用户。仅接受真实 PNG/JPEG 图片，最大2MB，宽高均不超过4096像素。数据在内存中上传，不写入本地 uploads；文件扩展名和 Content-Type 以实际解码格式为准。

返回结构为 `{"code":200,"data":{"avatarUrl":"临时签名地址"}}`。登录、`GET /api/auth/info` 和 `GET /api/profile` 同样返回 `avatarUrl`。数据库只保存不含签名的 OSS HTTPS 地址，后端签名有效期15分钟；前端每5分钟、进入系统及切回页面时刷新。没有头像时保留首字占位。

## 配置

默认关闭。本机在被 Git 忽略的 `backend/src/main/resources/application-local.yml` 中填写：

```yaml
app:
  avatar-oss:
    enabled: false # 填写完整后改为 true
    endpoint: "" # 例如 https://oss-cn-hangzhou.aliyuncs.com
    region: ""   # 例如 cn-hangzhou，必须与 Endpoint 匹配
    bucket: ""
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
```

可以把两项 AccessKey 直接填入本机忽略文件，或保留占位符并在后端运行环境设置变量。不要在受 Git 跟踪的 YAML、前端或启动命令中填写真实凭据。模板位于 `application-local.example.yml`，不要覆盖已有实际文件。

测试和生产通过公共 `application.yml` 读取以下环境变量：

| 变量 | 内容 |
| --- | --- |
| `AVATAR_OSS_ENABLED` | 参数齐全后设为 true，默认 false |
| `AVATAR_OSS_ENDPOINT` | 公网 HTTPS Endpoint |
| `AVATAR_OSS_REGION` | 与 Endpoint 匹配的地域 |
| `AVATAR_OSS_BUCKET` | 私有 Bucket 名称 |
| `OSS_ACCESS_KEY_ID` | RAM 用户 AccessKey ID |
| `OSS_ACCESS_KEY_SECRET` | RAM 用户 AccessKey Secret |

使用阿里云官方 Java SDK，后端将 YAML 绑定后的凭据交给 SDK，不向前端发送密钥。Endpoint 必须是该地域的公网地址，不使用内网 Endpoint。开启但配置不完整时启动会明确报错；保持关闭时其他业务正常，上传返回业务码503。修改后重启后端。环境选择及 JAR 本机配置加载方式见 [分环境说明](local-configuration.md)。

Bucket 保持私有，不配置公共读。本轮使用后端转传上传，浏览器只读取签名图片，无需给浏览器颁发 AccessKey。RAM 权限限定目标 Bucket 的 `avatars/*`，需要 `oss:PutObject`、`oss:GetObject`、`oss:DeleteObject`；删除权限用于数据库保存失败时清理本次新对象。不需要列举整个 Bucket 或修改 ACL 的权限。

每次上传生成新对象；OSS 上传失败不会更改原头像。数据库保存或提交失败时清理本次新对象，保留之前的头像。成功替换后旧头像暂时保留，不自动批量删除，后续清理需要明确的保留策略。

## 数据库与验证

已有库执行 [09 增量迁移](../sql/migrations/20261120_09_user_avatar.md)，不能用初始化 SQL 覆盖旧数据。

本轮自动测试覆盖三类角色上传、伪造用户 ID、未登录拒绝、图片和大小校验、OSS 失败保留旧头像、数据库提交失败清理新对象、登录/资料签名及过期时间，并使用真实 SDK 离线生成 V4 签名。隔离 MySQL 测试中的 OSS 网络调用使用模拟客户端，不代表已验证云端权限。

2026-11-20 验证结果：后端全量258项测试通过，头像接口和服务在最终修改后再次通过10项针对性测试；前端生产构建通过，头像页面17项检查、教师预览10项检查通过。更新后的生产初始化 SQL 与迁移后本地21张表的完整建表定义一致；隔离环境实际启动、管理员登录及5个已认证查询通过，关闭 OSS 时上传明确返回503。临时启动验证库及进程已清理。日志位于 `logs/`，本机恢复报告及截图位于 `output/`，均不提交 Git。

当前未填写真实 OSS 参数，因此尚未验证真实上传和浏览器读取。填写后需在私有 Bucket 确认对象路径为 `avatars/{用户ID}/...`、数据库地址没有签名、浏览器能显示、匿名访问对象地址被拒绝、临时签名过期后刷新可恢复，并检查本地没有新增头像文件。

官方参考：[Java SDK](https://www.alibabacloud.com/help/en/oss/developer-reference/oss-java-sdk)、[流式上传](https://www.alibabacloud.com/help/en/oss/developer-reference/simple-upload-11)。
