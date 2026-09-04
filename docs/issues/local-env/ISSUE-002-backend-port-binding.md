# 后端 8080 保留端口冲突与前后端配置同步

所属阶段：M7 前置环境配置
创建日期：2026-09-03
更新日期：2026-09-03
Status: done
Resolution: fixed
验收结果：后端启动、数据库连接、前端代理、GitHub Top10 和学习引导 SSE 验证通过；2026-09-03 用户确认登录无问题

关联文档：[需求确认书与开发计划](../../需求确认书与开发计划.md)、[PostgreSQL 端口修复](ISSUE-001-postgres-port-binding.md)

## 现象与影响

用户报告 Spring Boot 的 `8080` 端口被占用，要求同步调整配置并验证项目运行。

预期：后端正常监听，前端可查询技术栈、获取 GitHub 项目、生成学习引导和发起登录。
实际：Windows 拒绝绑定 `8080`；后端和 Vite 代理还使用该端口。
影响范围：本地后端启动、前端 API 和登录代理、OAuth 回调地址与操作文档。

## 复现与根因

- 监听检查未发现占用 `8080` 的进程。
- Windows TCP 保留区间包含 `7996–8095`，覆盖 `8080`。
- 直接绑定 `0.0.0.0:8080` 失败：`SocketError=AccessDenied; NativeError=10013`。
- 同一环境绑定 `0.0.0.0:18080` 成功。

直接原因是固定端口与系统保留范围冲突。是否由代理 TUN 触发该保留范围尚未确认。

## 修复

1. [后端配置](../../../backend/src/main/resources/application.yml) 使用 `server.port: ${SERVER_PORT:18080}`。
2. [Vite 配置](../../../frontend/vite.config.js) 使用 `loadEnv` 从项目根目录读取 `SERVER_` 前缀的配置，`/api` 和 `/oauth2` 共用 `http://localhost:<SERVER_PORT>`，默认 `18080`。不将后端密钥注入浏览器代码。
3. 前端启用 `strictPort`，固定使用 `5173`；占用时明确报错，避免自动切换端口后与登录成功返回地址不一致。
4. [.env.example](../../../.env.example) 增加 `SERVER_PORT=18080` 与 `FRONTEND_BASE_URL=http://localhost:5173`，注明新的本地 OAuth 回调地址。
5. [M3 手动测试步骤](../../M3-Agent业务/手动测试步骤.md) 同步当前后端地址；[M5 问题记录](../../M5-用户体系/问题记录.md) 补充当前回调地址，保留当时的历史端口记录。

本地 `.env` 和启动环境没有设置 `SERVER_PORT`，因此当前前后端均使用默认值 `18080`。以后需要换端口时，在根目录 `.env` 中配置该变量并重启前后端。

## 当前地址

| 服务或入口 | 地址 |
|---|---|
| 前端 | `http://localhost:5173` |
| 后端 | `http://localhost:18080` |
| 后端健康检查 | `http://localhost:18080/api/health` |
| 前端代理健康检查 | `http://localhost:5173/api/health` |
| PostgreSQL | `localhost:15432` |
| OAuth 回调 | `http://localhost:18080/login/oauth2/code/github` |

## 启动方式

先在 Docker Desktop 启动 PostgreSQL，再分别运行：

```powershell
.\scripts\run-backend.ps1
```

```powershell
.\scripts\run-frontend.ps1
```

本次后台启动遇到 Windows PowerShell 执行策略限制；经用户明确授权，仅为两个启动子进程使用 `-ExecutionPolicy Bypass`，未修改系统永久策略。服务已保持运行，使用页面时无需重复启动。

启动输出保存在 `backend/target/local-run.stdout.log` 和 `frontend/local-run.stdout.log`，错误输出为同目录对应的 `local-run.stderr.log`。

## 验收标准与结果

| 验收项 | 实际结果 |
|---|---|
| 后端监听新端口 | Tomcat 在 `18080` 启动，`Started TechStackAgentApplication` |
| 数据库连接 | Hikari 连接成功，Flyway 验证 7 条迁移记录，schema 版本 6，无需新迁移 |
| 后端健康检查 | HTTP 200，`{"status":"UP"}` |
| 前端首页资源 | `http://localhost:5173/` 返回 HTTP 200，包含应用挂载节点 |
| 前端代理健康检查 | `/api/health` 返回 HTTP 200、`UP` |
| 技术栈清单 | 经前端代理返回 24 条记录 |
| React 详情 | 官方文档为 `https://react.dev`，GitHub 仓库 10 个 |
| 登录状态查询 | 未登录请求 `/api/auth/me` 返回 HTTP 200、`authenticated=false` |
| OAuth 登录入口 | 经前端 `/oauth2/authorization/github` 返回 302，目标为 GitHub，`redirect_uri` 为新回调地址 |
| 多智能体学习引导 | 经前端 `/api/v1/agent/multi/stream` 返回 HTTP 200、`text/event-stream`；1,130 个事件、2,578 字符、包含来源链接，耗时 27.8 秒 |
| 前端生产构建 | `npm run build` 成功，46 个模块完成构建 |

SSE 验证中有一次 Agent 请求不存在的 `react/react` README，工具降级后继续完成回答；此记录不等同于所有外部工具每次调用均成功。

## OAuth 与验证边界

Spring Security 根据当前请求地址生成回调，本次已验证本地登录入口产生的新端口。未修改 GitHub 账户中的 OAuth App 设置。

GitHub 官方文档说明 loopback 回调支持请求端口与登记端口不同；实际应用仍应以完整授权流程为准。如 GitHub 报回调不匹配，应将该 OAuth App 的回调配置同步为上表地址。参见 [GitHub loopback redirect URLs](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#loopback-redirect-urls)。

浏览器自动化工具因 `failed to write kernel assets` 初始化失败，本次通过真实 HTTP 请求验证服务和代理。页面视觉交互、GitHub 用户实际授权、登录成功返回与用户 Token 绑定尚未重新验证。旧阶段文档里的通过记录不作为本次验证结果。

## Comments

- 2026-09-03：用户授权同步修改端口并验证运行。完成本地配置和接口冒烟，保持前后端服务运行，未初始化 Git 或提交 GitHub。
- 2026-09-03：用户明确反馈“登录无问题”，补充实际登录验收；此前自动化验证边界保留为历史记录。后续 M6.5 修复重启后端的日志位置见 [M6.5 测试与验收](../../M6.5-动态转正与界面优化/测试与验收.md)。
