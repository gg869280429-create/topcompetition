# 登录页替换验收（2026-11-12）

参考 `frontend/更新的登录页面.html` 替换正式 `/login` 页面。保留左右分栏、灰色网格背景、四个彩色角色、眼睛跟随鼠标、输入时互动、显示密码时转开视线，以及圆角表单和按钮悬停效果。小屏隐藏插画，单独显示登录表单；短窗口可滚动。

- 按要求移除注册、微信登录、Logo、隐私政策和服务条款。原 HTML 保留为设计参考。
- 账号仍使用项目现有用户名，不套用模板中的手机号格式校验；保留密码显隐、回车提交、加载状态和登录失败提示。
- 演示登录替换为原有 `userStore.login` 和 `/api/auth/login`，成功后进入首页；防止加载期间重复提交。不修改后端认证、角色、数据库或真实账号。
- 不显示缺少对应后端支持的“30 天记住我”。忘记密码提供联系管理员重置的提示。
- 图形及样式由本地 Vue/CSS 实现，不加载 Tailwind CDN、Google Fonts 或外部图片。动画组件离开登录页时清理事件、定时器及动画帧，并支持减少动态效果设置。

改动文件：`frontend/src/views/login/index.vue`、`frontend/src/components/LoginCharacters.vue`。

验证结果：

1. `npm run build` 通过。
2. Edge 浏览器 32 项断言通过，覆盖四种角色、移除入口、表单校验、登录成功与失败、网络失败重试、回车提交、重复提交、角色互动、密码显隐、1024/768/390/320px 宽度、短窗口及减少动态效果。此组使用模拟接口响应，不修改业务库。
3. 真实前端代理与本地后端连接验证通过：不存在的测试账号返回业务码 400，页面显示“用户名或密码错误”，不产生登录状态，不修改数据库。
4. 独立 MySQL 测试库运行既有 `ApiContractIntegrationTest#everyMappedEndpointHandlesRoleAndInvalidInputWithoutServerErrors`，5 项通过，0 失败、0 错误。四角色使用真实登录流程；91 个受保护接口覆盖四角色及未登录，共 455 次参数/权限检查，无服务端错误。此次未重跑全部业务流程，因为未改动后端业务代码。

日志：`logs/login-redesign-*.log`、`logs/login-auth-contract-tests.log`、`logs/login-live-negative.log`。结果与截图：`output/login-redesign-ui-result.json`、`output/login-live-negative-result.json`、`output/playwright/login-redesign-desktop.png`、`output/playwright/login-redesign-mobile.png`。

测试结束后停止临时前端服务，并检查、释放 8080 端口。
