# 信息工程学院竞赛管理平台

面向学生、教师、专业管理员和系统管理员的竞赛管理系统，覆盖竞赛发布、学生组队、报名审核、教师指导、参赛结果登记、获奖申报和统计。项目采用 Vue 3 与 Spring Boot 前后端分离架构，使用 MySQL 保存业务数据。

## 角色与功能

| 角色 | 主要功能与数据范围 |
| --- | --- |
| 学生 `STUDENT` | 浏览竞赛及新闻轮播、邀请组队、确认队长、报名和退赛申请、查看本人获奖记录、维护个人信息及密码 |
| 教师 `TEACHER` | 在个人中心查看本人指导团队及名额、确认参赛、完成指导、逐人或整队登记结果、申报获奖、查看指导成果 |
| 专业管理员 `MAJOR_ADMIN` | 在权限范围内维护竞赛和新闻，管理本专业学生、教师、报名及获奖业务，查看本专业统计 |
| 系统管理员 `ADMIN` | 专业管理、全局账号和业务管理、竞赛发布、报名与成果审核、教师名额维护、全局统计 |

前端按角色提供不同菜单，后端同时校验操作权限及数据范围。竞赛规则的维护还受发布人、负责人等业务权限限制。

## 主要业务

### 竞赛与报名

- 竞赛可选择具体专业或“全部专业”，该字段用于分类展示，不限制其他专业学生查看、报名，也不改变报名归属及审核范围。
- 新增竞赛可同时选择团队赛、个人赛，事务内生成两条独立竞赛记录。两条记录分别发布，报名、审核、教师名额和状态独立；同一学生可分别报名，师生端显示两张赛制卡片。
- 新建竞赛默认允许跨专业组队，历史竞赛保留原设置。团队专业根据实际成员计算，报名归属队长专业。
- 学生可先组队，再报名多个团队赛；各次报名保留独立名单。成员需接受邀请，创建人指定队长后须由队长本人确认。
- 报名提交、驳回重提均校验竞赛状态、报名时间、成员身份、专业、人数和重复报名；审核及操作保留历史。
- 管理员在报名管理中选择指导老师；学生选择老师时可查看剩余名额。
- 教师确认参赛前，队长可按邀请规则调整名单；确认后锁定名单。退赛走申请、审批流程。
- 竞赛按“草稿 → 报名中 → 比赛中 → 已结束”推进；取消需填写原因，并处理关联报名、指导、流程和通知。有效获奖等关联记录会限制取消、删除等操作。

### 教师与成果

- 管理员在教师库编辑中按竞赛维护指导名额；教师在个人中心“我的指导”查看团队、完成指导和登记结果。
- 完成指导释放对应指导关系占用的名额，保留指导历史；整队登记保留每位学生的个人明细。
- 未参赛、参赛未获奖的结果由教师保存，不自动执行退赛，也不生成获奖记录。
- 获奖申报支持草稿、待审核、驳回重提、通过、更正和撤销；保留审核及变更历史。
- 一项成果关联实际获奖学生及多名指导教师，在个人页面分别展示，统计时避免重复计算成果数量。
- 审核通过后通知对应教师，庆祝弹窗按“成果＋教师”在后端去重。

获奖申报提交时需要以下材料，统一通过文件服务上传和授权访问：

| 材料 | 要求 |
| --- | --- |
| 获奖证书 | 必传 |
| 官方获奖名单 | 选传 |
| 大赛报名官网截图 | 必传 |
| 指导过程照片 | 必须为2张 |

### 基础管理与新闻

- 专业管理仅系统管理员可操作，支持预设专业图标和自定义 Logo；学生管理支持班级维护及筛选。
- 新闻支持正文、封面、官网链接、发布及下线；发布时校验 HTTP/HTTPS 官网地址。
- 新闻展示时间与轮播时间分别配置。轮播需封面和官网链接，新闻下线后同步停止轮播展示。
- 定时任务默认每15秒检查发布及到期状态，读取接口同时校验展示时段。
- 站内通知支持相关业务跳转，报名、指导、退赛和成果等关键操作保留历史。

**尚未开发的入口：**大师工作室、个人中心的竞赛费用申报审批，目前展示“敬请期待”。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | Vue 3、Vite 5、Element Plus、Pinia、Vue Router、Axios、ECharts |
| 后端 | Java 17、Spring Boot 3.2.5、Spring Security、MyBatis-Plus 3.5.7、JWT |
| 数据库 | MySQL 8.x，当前初始化结构包含21张业务表 |
| 测试 | JUnit 5、Spring Boot Test、MockMvc、MySQL 隔离集成测试；浏览器交互可使用 Playwright 检查 |

## 项目目录

```text
topproject/
├── backend/
│   ├── pom.xml                  # 后端依赖、测试与打包配置
│   └── src/
│       ├── main/java/           # 接口、业务、权限与数据库访问
│       ├── main/resources/      # 公共配置及各环境配置
│       └── test/java/           # 单元测试与集成测试
├── frontend/
│   ├── package.json             # 前端依赖与运行命令
│   ├── vite.config.js           # 开发服务及 /api 代理
│   └── src/                     # 页面、组件、路由、状态及接口封装
├── sql/
│   ├── init.sql                 # 新库完整建表脚本
│   ├── test_data.sql            # 历史示例数据，非启动必需
│   └── migrations/              # 已有数据库增量迁移及说明
├── docs/                        # 配置、设计、迁移与验收文档
├── logs/                        # 本地日志，不提交 Git
├── output/                      # 本地报告、截图及数据库备份，不提交 Git
├── AGENTS.md                    # 自动化开发工具约定
└── 项目开发指南.md               # 项目工程规范
```

详细说明见[项目目录与文件用途](docs/project-directory.md)。历史文档中的菜单、教师称呼等以当前代码和本 README 为准。

## 本地启动

以下命令以 Windows PowerShell 为例，从项目根目录开始。准备 Java 17、Maven 3.8+、MySQL 8.x 和 Node.js 20+（推荐），确保相关命令已加入 PATH。

### 1. 初始化新数据库

仅对新环境执行。先进入 MySQL 客户端，密码通过交互输入，不写入命令：

```powershell
mysql --default-character-set=utf8mb4 -u YOUR_DATABASE_USER -p
```

在 MySQL 客户端内执行：

```sql
SOURCE sql/init.sql;
```

脚本创建 `competition` 数据库及当前完整表结构。已有业务数据库请按下文执行增量迁移，重复执行初始化脚本不会补齐旧表字段。

`sql/test_data.sql` 是历史示例数据，包含固定关联及旧业务状态，不作为当前完整业务验收数据，不要导入生产库。

### 2. 配置本机后端

仅在本机配置文件尚不存在时复制模板：

```powershell
Copy-Item backend/src/main/resources/application-local.example.yml backend/src/main/resources/application-local.yml
New-Item -ItemType Directory -Force logs | Out-Null
```

编辑 `application-local.yml`，填写本机数据库地址、用户名、密码及独立随机 JWT 密钥。JWT 密钥至少32字节，不要直接使用模板占位值。

`application-local.yml` 已被 Git 忽略，也不会打入发布 JAR。不要覆盖已有本机配置，不要提交真实密码、密钥或 Token。

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

后端默认端口为 `8080`。也可在 IDE 中运行 `com.cie.competition.Application`，Active profiles 设置为 `local`；公共配置默认环境同样是 `local`。

首次启动会按需创建 `admin`、`majoradmin` 账号，初始化逻辑见 [DataInitializer.java](backend/src/main/java/com/cie/competition/common/config/DataInitializer.java)。首次部署应修改初始口令，并给专业管理员配置真实所属专业；没有专业的专业管理员无法正常办理本专业业务。

### 4. 启动前端

另开一个终端，从项目根目录执行：

```powershell
cd frontend
npm ci
npm run dev
```

浏览器访问 [http://localhost:5173](http://localhost:5173)。开发服务将 `/api` 代理到 `http://localhost:8080`，接口基础路径为 `/api`，登录接口为 `POST /api/auth/login`。

上传目录默认相对于后端工作目录为 `./uploads`，从 `backend/` 启动时对应 `backend/uploads/`。附件文件和数据库记录需要配套保留。

## 环境切换与部署

| 环境 | 配置 | 使用方式 |
| --- | --- | --- |
| 本机 | `application-local.yml` | 本机私有配置，启用 `local` |
| 测试 | `application-test.yml` | 启用 `test`；使用前核对并配置独立测试库及密钥 |
| 生产 | `application-prod.yml` | 启用 `prod`，由部署环境提供生产环境变量 |

可通过 IDE Active profiles、`SPRING_PROFILES_ACTIVE` 或 `--spring.profiles.active` 切换环境；每次只选择一个环境。环境切换不会创建数据库或自动执行迁移。

公共配置使用 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET` 占位符；生产配置使用 `PROD_DB_URL`、`PROD_DB_USERNAME`、`PROD_DB_PASSWORD`、`PROD_JWT_SECRET`。生产变量须在部署环境中配置，不提供真实密码或密钥的默认值。

**当前配置注意：**`application-test.yml` 仍存在固定数据库连接及敏感配置，尚未与生产配置一样全部改为环境变量占位。部署或提交前需单独处理，不能按历史文档假定它已读取 `TEST_*` 变量。本 README 不包含这些真实值。

打包命令：

```powershell
# 在 backend/ 中
mvn package

# 在 frontend/ 中
npm run build
```

生产后端启动（在 `backend/` 中，先配置上述生产变量）：

```powershell
java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=prod
```

本机使用 JAR 时，由于本机配置没有打入包，需显式加载外部配置（同样在 `backend/` 中）：

```powershell
java -jar target/competition-backend-1.0.0.jar --spring.profiles.active=local --spring.config.additional-location=file:./src/main/resources/
```

前端部署产物为 `frontend/dist/`。部署服务需将 `/api` 转发到后端，并为 Vue Router 的 History 路由配置回退到 `index.html`；Vite 的开发代理不会进入生产静态文件。

上传目录通过 `app.upload-directory` 配置，生产部署应使用持久化目录。更换 JWT 密钥后，原登录凭证失效，用户需要重新登录。

## 已有数据库升级

数据库没有启动时自动迁移机制。先核对当前结构和已执行记录，只执行缺失迁移；不要批量重复运行目录内所有 SQL。

| 阶段 | 迁移内容 |
| --- | --- |
| 01、02 | 竞赛约束、成员邀请、退赛、历史及资格约束 |
| 03a～03d | 专业分类、新闻轮播、共享成果与材料、逐学生参赛结果 |
| 04、05、06 | 登录版本、教师确认名单锁定、独立学生组队 |
| 07 | 学生班级、专业图标与 Logo、新闻及轮播独立时间配置 |
| 08 | 全部专业标记、新建竞赛默认跨专业及双赛制兼容 |

完整流程：停止写入 → 备份数据库和附件 → 在独立恢复库演练 → 执行目标库缺失迁移 → 比对原字段、行数及关联数据 → 部署代码并验证业务。不要用清空数据库替代升级。

- [基础业务迁移说明](docs/business-integrity-migration.md)
- [功能扩展迁移说明](docs/feature-migration-20261111.md)
- [07 管理功能整合迁移](sql/migrations/20261118_07_admin_panel.md)
- [08 竞赛选项迁移](sql/migrations/20261118_08_competition_options.md)

历史兼容：旧学生班级可后续补录，旧专业可补图标；旧新闻无时间配置时沿用原规则；历史未分类竞赛不自动视为“全部专业”，历史跨专业开关也不会被新默认值覆盖。旧成果材料及人员归属按迁移说明核对补录。

## 测试与验证

在 `backend/` 中执行普通测试：

```powershell
mvn test
```

需要真实 MySQL 的集成测试默认受开关控制。完整运行前准备本机配置，并使用专用测试 MySQL 实例及具备创建、测试和清理临时数据库所需权限的账号：

```powershell
$env:RUN_DB_TESTS = 'true'
try {
    mvn test
} finally {
    Remove-Item Env:RUN_DB_TESTS -ErrorAction SilentlyContinue
}
```

集成测试基类读取公共及本机配置，在对应 MySQL 实例创建随机隔离库，从 `sql/init.sql` 建表。迁移测试还会验证旧结构升级。这里的集成测试开关与 Spring 的 `test` profile 是两回事，不要将其连接到生产实例。

覆盖重点包括报名时间、成员邀请、跨专业和重复资格校验、权限隔离、审核重提、退赛和名额释放、获奖材料、更正撤销、多教师成果、轮播时间及双赛制独立流程。测试结果以当次报告为准，不将接口返回成功或构建通过等同于完整业务验收。

前端通过 `npm run build` 检查生产构建；页面排布、按钮、角色入口及完整交互需另外进行浏览器验证。

## 日志与常见问题

人工保存的运行、构建、测试及排查日志统一写入根目录 `logs/`，不要放在根目录或 `output/`。例如在 `backend/` 中：

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local" *> ../logs/backend-local.log
```

Maven 内部测试报告仍保留在 `backend/target/surefire-reports/`。`output/` 可能包含数据库备份及业务资料，不能当作普通缓存整体清理。`logs/`、`*.log`、`output/`、本机配置及构建产物均由 `.gitignore` 忽略。

| 现象 | 检查方向 |
| --- | --- |
| `Could not resolve placeholder 'JWT_SECRET'` | 环境未选对或缺少对应密钥；检查 profile 和本机配置，不要添加明文默认值 |
| 数据库连接失败 | 核对数据库是否启动、账号权限、连接地址及当前环境配置；切换环境不会同步数据库密码 |
| `Unknown column` / 表不存在 | 代码与库结构版本不一致，按迁移说明检查缺失字段和表 |
| 专业管理员无法处理业务 | 检查所属专业、目标数据归属及具体操作权限 |
| 双赛制仅看到一张可报名卡片 | 两条记录需分别发布，并分别满足展示、状态和报名时间条件 |
| 轮播没有展示 | 核对新闻发布状态、封面、官网、轮播开关及新闻和轮播各自时间范围 |
| `8080` 端口占用 | 先确认占用进程，停止对应旧后端或调整端口；本地后端验收结束后释放测试占用的端口 |

## 更多文档

- [本地配置与环境说明](docs/local-configuration.md)（测试环境配置差异见上文）
- [后端接口清单](docs/backend-endpoint-inventory.md)（新增接口以 Controller 为准）
- [学生组队流程](docs/student-team-20261112.md)
- [教师个人中心与名单规则](docs/teacher-center-20261112.md)
- [开发规范](项目开发指南.md)
