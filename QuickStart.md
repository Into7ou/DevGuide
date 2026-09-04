# DevGuide Quick Start

本文用于在本地启动 DevGuide 开发环境。项目能力与架构介绍见 [README](README.md)。

## 环境要求

- Java 21
- Maven 3.9+
- Node.js 20+ 与 npm
- Docker Desktop / Docker Compose
- DeepSeek API Key
- DashScope API Key
- Tavily API Key
- GitHub Token，以及用于登录联调的 GitHub OAuth App

## 1. 配置环境变量

在项目根目录复制配置模板：

```powershell
Copy-Item .env.example .env
```

编辑 `.env` 并填写本地配置。至少需要确认以下项目：

| 配置 | 用途 |
|---|---|
| `DEEPSEEK_API_KEY` | 对话、资料判断与回答生成 |
| `DASHSCOPE_API_KEY` | 文本 embedding |
| `TAVILY_SEARCH_API_KEY` | 联网资料搜索 |
| `GITHUB_TOKEN` | 查询公开仓库时提高 API 限额 |
| `GITHUB_OAUTH_CLIENT_ID` | GitHub OAuth 登录 |
| `GITHUB_OAUTH_CLIENT_SECRET` | GitHub OAuth 登录 |
| `TOKEN_CIPHER_KEY` | 用户 GitHub Token 加密 |
| `POSTGRES_PASSWORD` | 本地 PostgreSQL 密码 |

> `.env` 只用于本机或受控部署环境，已经被 Git 忽略。不要把真实 API Key、Token、OAuth Secret 或密码写入 Markdown、源码、日志、截图和问题记录。

GitHub OAuth App 的本地回调地址：

```text
http://localhost:18080/login/oauth2/code/github
```

## 2. 启动 PostgreSQL + PGvector

```powershell
docker compose up -d postgres
```

默认连接信息：

| 服务 | 地址 |
|---|---|
| PostgreSQL | `localhost:15432` |
| 数据库 | `techstack` |

数据库密码必须由 `.env` 的 `POSTGRES_PASSWORD` 提供。Flyway 会在后端启动时自动执行迁移。

## 3. 启动后端

```powershell
.\scripts\run-backend.ps1
```

脚本会读取根目录 `.env` 并将配置注入当前进程。若根目录存在本机专用的 `maven-settings.xml`，脚本会使用该文件；否则使用 Maven 默认配置。

后端默认地址：`http://localhost:18080`。

健康检查：

```powershell
Invoke-RestMethod http://localhost:18080/api/health
```

## 4. 启动前端

另开一个终端：

```powershell
.\scripts\run-frontend.ps1
```

首次运行时脚本会通过 `npm ci` 安装锁定版本的依赖。前端默认地址：`http://localhost:5173`。

## 5. 运行测试

后端：

```powershell
Set-Location backend
mvn test
```

如果本机需要项目根目录的 Maven 配置：

```powershell
mvn -s ..\maven-settings.xml test
```

前端：

```powershell
Set-Location frontend
npm ci
npm run test:unit
npm run build
```

上传前最近一次验证结果：后端 48 项测试通过，前端 34 项测试通过，Vite 生产构建通过。

## 6. 停止本地服务

前后端开发进程可在对应终端按 `Ctrl+C` 停止。停止数据库容器：

```powershell
docker compose down
```

该命令不会删除 `pgdata` 数据卷。如需处理本地数据，请先确认目标和备份，不要直接删除数据卷。

## 常见问题

### 后端无法连接 PostgreSQL

确认 Docker Desktop 已启动，并检查：

```powershell
docker compose ps
```

项目默认使用主机端口 `15432`，用于避开部分 Windows 环境的保留端口范围。

### GitHub OAuth 回调失败

确认 OAuth App 回调地址与 `SERVER_PORT` 一致，并检查 `.env` 中的 Client ID、Client Secret 和 `FRONTEND_BASE_URL`。

### Maven 下载或缓存受限

`maven-settings.xml` 是可选的本机配置，不会提交到仓库。没有该文件时启动脚本会使用 Maven 默认配置。

### 页面有回答但外部资料不完整

检查 DeepSeek、DashScope 和 Tavily 配置。DevGuide 在资料不足或外部页面无法取得正文时会保留已有依据，并明确说明证据缺口，而不会把搜索摘要当作来源原文。
