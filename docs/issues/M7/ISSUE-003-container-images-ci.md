# 生产容器与 GHCR 镜像流水线

所属阶段：M7 部署
创建日期：2026-09-04
更新日期：2026-09-05
Status: ready-for-human

## 目标

为 Caddy/Vue 前端和 Spring Boot 后端建立可复现的生产镜像，并通过公开仓库的 GitHub Actions 免费构建 AMD64/ARM64 镜像，供 OCI 或普通 Linux VPS 拉取。

## 当前行为

- 只有 PostgreSQL/PGvector Compose 服务，没有前后端 Dockerfile。
- 仓库没有 `.github/workflows`。
- 本地开发依赖 Vite 与 Maven 直接运行，不具备生产入口和自动镜像产物。

## 已确认方案

- `frontend` 使用多阶段构建：Node 构建 Vue，Caddy 运行静态文件、SPA 回退、HTTPS 和后端反向代理。
- `backend` 使用 Java 21 多阶段构建并以非 root 用户运行 Spring Boot 可执行 JAR。
- Buildx 构建 `linux/amd64`、`linux/arm64`，发布公开 GHCR 镜像 `ghcr.io/into7ou/devguide-backend` 与 `ghcr.io/into7ou/devguide-frontend`。
- Pull Request 执行测试和镜像构建验证但不推送；`main` 或版本发布才推送。
- OCI 初期保留人工发布门，不在 Actions 中保存生产 SSH 私钥或运行时密钥。
- CI 为同一次前后端发布生成一致的不可变版本标签与 Commit SHA 标签；生产不部署 `latest` 或 `main`。
- 基础 Compose 不发布后端和 PostgreSQL 端口；本地 dev override 继续映射 PostgreSQL `15432:5432`。
- 用户日常仍通过 Docker Desktop 启停已创建的 PostgreSQL 容器；备用脚本仅用于首次重建、配置变化和恢复。

## 验收标准

- 后端、前端 Dockerfile 均使用明确基础镜像版本、合理 `.dockerignore` 和非 root 运行用户。
- GitHub Actions 的后端测试、前端测试、生产构建和双架构镜像发布成功。
- AMD64 本地环境与 ARM64 OCI 能从同一镜像标签启动。
- 容器镜像不包含 `.env`、Git 凭据、API Key、OAuth Secret 或数据库数据。
- 失败构建不会覆盖稳定标签；生产 Compose 通过必填 `IMAGE_TAG` 固定版本，部署说明包含回滚到上一成功镜像版本的方法。
- dev override 重建后本地 Spring Boot 仍能连接 `localhost:15432`，原命名卷数据保持不变，Docker Desktop 启停流程可用。

## 验证结果

- 前后端 Dockerfile、dockerignore、Caddyfile、基础/dev/prod Compose 与 GitHub Actions 已实现。
- 本地 AMD64 前后端镜像构建通过；两个运行时均以 uid 100 的 devguide 非 root 用户运行。
- Caddy 配置解析、静态首页、后端生产 profile、Flyway、内部健康检查与同网络反向代理均通过。
- 本地 PostgreSQL 已用 dev override 重建，继续使用 skilllearningagents_pgdata，端口仍为 15432，四张表计数未变化。
- 生产 Compose 已验证缺少 IMAGE_TAG 时拒绝解析。
- 尚待 push/PR/tag 后验证 GitHub Actions 双架构构建并将首次 GHCR Packages 设为 Public，因此转为 ready-for-human。

## Comments

- 2026-09-04：用户确认 GitHub Actions + 公开 GHCR 双架构镜像，OCI 采用人工拉取部署。
- 2026-09-04：用户确认 `v0.7.0` 首发，生产固定不可变标签并保留 Commit SHA 追溯。
- 2026-09-05：用户审查用途与文档来源后，明确授权创建 GitHub Actions；如首次发布需要设置 GHCR Package 为 public，应提示用户人工完成。
