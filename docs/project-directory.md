# 项目目录与文件用途

按当前目录整理于 2026-11-12。业务代码、依赖、构建产物和本地测试资料分别说明；同类 Java 文件按分层职责归类。

## 根目录各项

| 文件或目录 | 用途 |
| --- | --- |
| `README.md` | 项目简介及常用说明入口。 |
| `项目开发指南.md` | 前后端分层、模块复用、权限、事务、页面等开发规范。 |
| `AGENTS.md` | 自动化开发工具执行任务时读取的项目约定，目前记录日志存放规则。 |
| `.gitignore` | 指定不提交 Git 的本地配置、日志、依赖、编译产物和测试资料。 |
| `backend/` | Java 后端：接口、权限、业务规则、数据库访问及测试。 |
| `frontend/` | Vue 前端：页面、菜单、组件和接口调用。 |
| `sql/` | 新库建表、测试数据和已有数据库的增量迁移脚本。 |
| `docs/` | 配置说明、功能设计、迁移步骤、检查与验收报告。 |
| `logs/` | 运行、构建、测试和排查日志，已被 Git 忽略。 |
| `output/` | 本地排查脚本、JSON 检查结果、截图、测试附件和数据库备份；已被 Git 忽略。 |
| `.playwright-cli/` | 浏览器自动化工具生成的页面快照等本地资料；已被 Git 忽略，独立日志归档至 `logs/`。 |
| `.claude/` | Claude 工具本地配置，目前为被忽略的 `settings.local.json`，不参与业务运行。 |
| `.git/` | Git 的提交历史、分支、索引和仓库配置，不是业务代码。 |
| `川托院发〔2026〕37号文件《技能大师工作室建设与管理办法》.pdf` | 项目目录中存放的 PDF 资料，不参与编译和启动；本次未审阅其正文内容。 |

## 后端

| 路径 | 用途 |
| --- | --- |
| `backend/pom.xml` | Maven 项目配置，声明 Java 版本、Spring Boot、数据库驱动等依赖，以及测试、打包插件。 |
| `backend/src/main/java/com/cie/competition/Application.java` | Spring Boot 启动入口。 |
| `backend/src/main/java/com/cie/competition/common/config/` | MyBatis 分页、JSON 序列化及初始数据等公共配置。 |
| `backend/src/main/java/com/cie/competition/common/security/` | JWT 签发与校验、登录身份、接口权限和认证失败处理。 |
| `backend/src/main/java/com/cie/competition/common/result/` | 统一返回结果、状态码及分页结果结构。 |
| `backend/src/main/java/com/cie/competition/common/exception/` | 业务异常和统一异常处理，将异常转换成接口响应。 |
| `backend/src/main/java/com/cie/competition/module/` | 按功能拆分的业务模块，见下表。 |
| `backend/src/main/resources/application.yml` | 公共配置：端口、默认环境、连接参数占位符、上传大小和 JSON 等。 |
| `backend/src/main/resources/application-local.yml` | 本机真实配置，不提交 Git，也不打入发布 JAR。 |
| `backend/src/main/resources/application-local.example.yml` | 不含真实凭据的本机配置模板。 |
| `backend/src/main/resources/application-test.yml` | 测试环境配置，从测试环境变量读取连接及密钥。 |
| `backend/src/main/resources/application-prod.yml` | 生产环境配置，从生产环境变量读取连接及密钥。 |
| `backend/src/test/java/` | 权限、报名、指导、获奖、新闻、文件、迁移及并发等自动化测试。 |
| `backend/src/test/java/com/cie/competition/IsolatedDatabaseTest.java` | 为需要数据库的集成测试准备独立测试库。 |
| `backend/target/` | Maven 生成的编译文件、测试报告、JAR 等，可通过构建重新生成，不提交 Git。 |
| `backend/.idea/` | 本机 IDE 工程设置，不提交 Git。 |

业务模块：

| 模块目录 | 职责 |
| --- | --- |
| `user` | 登录、用户资料、学生和教师账号管理。 |
| `major` | 专业管理，系统管理员维护专业。 |
| `competition` | 竞赛发布、分类、报名时间、状态、规则和取消关联处理。 |
| `registration` | 报名、团队成员、邀请确认、审核、重提、成员调整和退赛。 |
| `guidance` | 意向教师、分配和调剂、名额、完成指导及逐学生结果登记。 |
| `award` | 成果草稿、申报、材料、审核、更正、撤销、共享成果及庆祝去重。 |
| `content` | 新闻、公告等内容及官网、封面、发布状态和轮播配置。 |
| `message` | 站内消息、未读数量、已读状态及消息详情。 |
| `file` | 统一上传、存储记录、文件类型和用途校验、受权限控制的下载。 |
| `history` | 关键业务操作及变更前后信息的历史记录。 |
| `statistics` | 团队、人数、成果、获奖学生和教师成果等统计。 |

模块内部文件按以下规则阅读，并非每个模块都有全部层级：

| 文件位置或名称 | 用途 |
| --- | --- |
| `controller/*Controller.java` | HTTP 接口入口，接收参数并调用业务服务。 |
| `service/*Service.java` | 定义或实现业务规则；部分复杂流程使用独立的 Workflow、Lifecycle、Completion 服务。 |
| `service/impl/*ServiceImpl.java` | 相应服务接口的实现，处理业务、事务和模块协调。 |
| `mapper/*Mapper.java` | 通过 MyBatis 操作数据库，包括查询、插入、修改和加锁。 |
| `entity/*.java` | 数据库记录对应的 Java 对象。 |
| `dto/*DTO.java` | 接口接收的输入参数及校验规则。 |
| `vo/*VO.java` | 返回给前端的展示数据。 |
| `src/test/**/*Test.java` | 对应功能的测试；IntegrationTest 验证多个模块或真实数据库协作。 |

## 前端

| 路径 | 用途 |
| --- | --- |
| `frontend/package.json` | 前端依赖及 `dev`、`build`、`preview` 命令。 |
| `frontend/package-lock.json` | npm 的依赖版本锁定文件。 |
| `frontend/yarn.lock` | Yarn 的依赖版本锁定文件。当前项目同时保留两份锁文件，分别供对应工具使用。 |
| `frontend/vite.config.js` | Vite 开发及构建配置，包括路径别名、5173 端口和 `/api` 到后端的代理。 |
| `frontend/index.html` | 浏览器加载的 HTML 入口。 |
| `frontend/src/main.js` | 初始化 Vue、路由、状态管理、组件库及全局样式。 |
| `frontend/src/App.vue` | 顶层 Vue 组件。 |
| `frontend/src/router/index.js` | 页面路由、登录检查和角色访问限制。 |
| `frontend/src/store/user.js` | 登录状态、当前用户及退出登录操作。 |
| `frontend/src/layout/index.vue` | 登录后的公共外壳：菜单、顶部栏、用户入口、消息及庆祝组件。 |
| `frontend/src/api/request.js` | Axios 公共配置，附加 Token、处理统一响应和错误。 |
| `frontend/src/api/auth.js` | 登录和当前登录用户接口。 |
| `frontend/src/api/{competition,registration,guidance,award,content,message,major,student,user,teacher,statistics,file}.js` | 对应业务模块的接口调用封装；花括号表示这一组实际文件。 |
| `frontend/src/constants/dict.js` | 状态、奖项和竞赛类别等展示字典。 |
| `frontend/src/styles/index.css` | 全局主题、间距、字号及公共样式。 |
| `frontend/src/views/` | 具体页面，见下表。 |
| `frontend/src/components/` | 可复用界面组件，见下表。 |
| `frontend/node_modules/` | 安装的前端依赖，由包管理器生成，不提交 Git。 |
| `frontend/dist/` | `npm run build` 生成的部署静态文件，不提交 Git。 |

每个页面目录中的 `index.vue` 对应该页面的模板、交互和样式：

| 页面目录 | 页面作用 |
| --- | --- |
| `views/login/` | 登录。 |
| `views/dashboard/` | 学生、教师首页展示管理员新闻轮播；管理员首页保留竞赛卡片及操作入口。 |
| `views/competition/` | 竞赛查询、发布、编辑、分类及状态操作。 |
| `views/registration/` | 报名、邀请、审核、团队及退赛管理。 |
| `views/guidance/` | 指导、名额、师生匹配、完成指导及结果登记。 |
| `views/award/` | 成果申报审核、个人获奖记录、材料和历史。 |
| `views/content/` | 管理员新闻管理；学生和教师的“竞赛预览”，展示原首页竞赛卡片及详情、报名入口。 |
| `views/major/` | 专业管理。 |
| `views/student/` | 学生账号管理。 |
| `views/teacher/` | 教师库、教师详情及指导名额维护。 |
| `views/statistics/` | 统计卡片和图表。 |
| `views/error/403.vue` | 无权访问页面。 |

| 组件文件 | 作用 |
| --- | --- |
| `PageHeader.vue` | 公共页面标题和操作区域。 |
| `PageContainer.vue` | 公共页面内容容器。 |
| `CompetitionResources.vue` | 竞赛资料展示和访问。 |
| `CompetitionPreview.vue` | 复用竞赛卡片、详情、教师资料及学生报名入口，供管理员首页和师生竞赛预览使用。 |
| `CompetitionTeacherConfig.vue` | 竞赛教师及指导名额配置界面。 |
| `MessageDrawer.vue` | 站内消息抽屉和业务详情跳转。 |
| `NewsCarousel.vue` | 已发布且开启轮播的新闻封面、标题、摘要、详情弹窗及官网链接。 |
| `AwardCelebration.vue` | 教师获奖庆祝、依次展示及后端去重交互。 |

## SQL 文件

| 文件 | 用途 |
| --- | --- |
| `sql/init.sql` | 新数据库的完整建表脚本。已有表不会因为重复执行此脚本自动补齐字段。 |
| `sql/test_data.sql` | 测试用示例数据，不能当作正式业务资料。 |
| `sql/migrate_teacher_intention.sql` | 早期教师库及报名意向教师相关结构迁移。 |
| `sql/migrations/20261110_01_competition.sql` | 竞赛负责人、取消及相关基础结构迁移。 |
| `sql/migrations/20261110_02_workflows.sql` | 邀请、退赛、历史、人员快照和有效资格约束等流程迁移。 |
| `sql/migrations/20261111_03a_management.sql` | 竞赛所属专业分类及索引。 |
| `sql/migrations/20261111_03b_content.sql` | 新闻官网、轮播开关及排序。 |
| `sql/migrations/20261111_03c_awards.sql` | 成果材料、实际获奖学生、指导教师、庆祝状态及成果唯一索引。 |
| `sql/migrations/20261111_03d_guidance.sql` | 指导成员快照及逐学生参赛结果表。 |

迁移脚本有执行前提和顺序，不能将整个目录的脚本反复执行。当前业务库的执行记录见迁移和排查文档。

## docs 中各份文档

| 文件 | 用途 |
| --- | --- |
| `project-directory.md` | 本文，目录及文件用途。 |
| `local-configuration.md` | 本机、测试、生产配置与启动方法。 |
| `teacher-intention.md` | 教师库、报名意向教师及管理员确认方案。 |
| `backend-endpoint-inventory.md` | 后端接口方法与路径清单。 |
| `api-audit-20261111.md` | 早期接口排查及回归结果。 |
| `database-migration-20261111.md` | 早期数据库缺失结构与升级说明。 |
| `business-integrity-migration.md` | 报名、邀请、退赛及历史等业务完整性迁移说明。 |
| `business-integrity-batch1.md` | 第一批业务规则修复记录。 |
| `business-integrity-batch2.md` | 第二批业务规则修复记录。 |
| `business-integrity-batch3.md` | 第三批业务规则修复记录。 |
| `business-integrity-acceptance.md` | 上述业务完整性调整的验收记录。 |
| `feature-expansion-plan.md` | 本轮功能扩展的实施计划及检查清单。 |
| `feature-migration-20261111.md` | 新功能四份增量迁移、旧资料兼容及补录说明。 |
| `feature-acceptance-20261111.md` | 18 项功能交付、测试范围和遗留问题。 |
| `admin-diagnostics-20261111.md` | 管理员端报错根因、实际业务库修复及复验记录。 |
| `superpowers/` | 本地开发工具保存的计划等辅助资料，已被 Git 忽略。 |

## logs 与 output 中的本地产物

日志按内容识别：`*-tests.log` 为测试，`*-build.log` 为构建，`*-compile.log` 为编译，`*-live.log` / `*-backend*.log` 为运行，`console-*.log` 为浏览器控制台。它们都存放在 `logs/`。

`output/` 不包含产品运行必需的源码，现有文件主要分为：

| 文件或目录 | 用途 |
| --- | --- |
| `backups/*.sql` | 数据库升级前的备份，包含原有业务资料，不应将整个 `output/` 当作普通缓存清理。 |
| `*Migration*.java`、`*Rehearsal*.java` | 经过特定授权执行过的迁移、备份和演练辅助程序，不是通用启动命令。 |
| `*Audit.java`、`*Diagnostics.java`、`*Preflight.java`、`SchemaCodeAlignment.java` | 接口检查、结构检查和代码映射核对辅助程序。 |
| `*Credentials.java`、`*MysqlPassword*.java`、`ResetMysqlPasswordAdmin.ps1` | 历史凭据调整辅助程序，仅对应当时授权的操作，不参与正常启动。 |
| `*Check.java`、`*Verification.java`、`*Setup.java` | 配置、登录、旧数据保持及测试准备等辅助检查。 |
| `*.json` | 接口检查、迁移结果、结构核对和测试统计；部分是本地测试账号资料。 |
| `*.js` | 浏览器验收、登录及业务检查脚本。 |
| `*.txt` | 本地执行所需的类路径、测试副本连接辅助信息。 |
| `*.pid` | 当时启动的本地服务进程编号；进程结束后不代表当前进程。 |
| `playwright/`、`*.png` | 页面截图和浏览器验收结果。 |
| `feature-uploads/` | 独立验收所用的上传材料。 |

构建工具自行生成的编译和测试内部报告仍留在 `backend/target/`，不与人工日志混放。
