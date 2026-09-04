# PostgreSQL 容器因 Windows 保留端口无法启动

所属阶段：M7 前置环境配置
创建日期：2026-09-03
更新日期：2026-09-03
Status: done
Resolution: fixed
验收结果：容器健康检查、宿主机 JDBC 连接及已有数据读取通过

关联文档：[需求确认书与开发计划](../../需求确认书与开发计划.md)

## 现象与影响

用户在 Docker Desktop 中启动 Compose 应用，`techstack-postgres` 启动失败，提示：

```text
ports are not available: exposing port TCP 0.0.0.0:5432 -> 127.0.0.1:0:
listen tcp 0.0.0.0:5432: bind:
An attempt was made to access a socket in a way forbidden by its access permissions.
```

预期：PostgreSQL 启动后可供本地后端访问。
实际：容器的宿主机端口绑定失败，数据库无法启动。
影响范围：本项目本地 PostgreSQL 启动与后端数据库连接。

用户反馈代理软件开启了全局 TUN；是否由 TUN 触发端口保留尚未确认。

## 复现与证据

1. 修复前执行 `docker compose start postgres`，复现用户报告的相同错误。
2. `netsh interface ipv4 show excludedportrange protocol=tcp` 与 IPv6 对应命令均显示保留区间 `5413–5512`，包含 `5432`。
3. TCP 监听检查未发现 `5432` 或 `15432` 的监听进程。
4. 用 Windows TCP socket 直接绑定 `127.0.0.1:5432`，失败并返回 `SocketError=AccessDenied; NativeError=10013`。
5. 在同一环境直接绑定 `127.0.0.1:15432` 成功。

## 根因

已确认的直接原因：原 Compose 将宿主机端口固定为 `5432`，与 Windows 当前保留端口范围冲突。即使没有监听进程，系统仍会拒绝该端口的 socket 绑定。

保留范围具体由哪个系统组件或代理软件创建，未进一步归因。本次修复采用已经实测可用的替代端口，无需调整 TUN 或系统保留范围。

## 修复

- [docker-compose.yml](../../../docker-compose.yml)：宿主机端口改为 `${POSTGRES_PORT:-15432}`，容器内 PostgreSQL 端口保持 `5432`。
- [application.yml](../../../backend/src/main/resources/application.yml)：数据库地址改为 `jdbc:postgresql://localhost:${POSTGRES_PORT:15432}/${POSTGRES_DB:techstack}`。
- [.env.example](../../../.env.example)：增加 `POSTGRES_PORT=15432`。
- 本地 `.env` 和当前启动 shell 未设置 `POSTGRES_PORT`，因此 Compose 与后端均使用默认值 `15432`。以后需要换端口时，在 `.env` 中统一设置该变量，再重新创建容器并重新启动后端。
- 使用原有命名数据卷 `skilllearningagents_pgdata`，挂载目标仍为 `/var/lib/postgresql/data`。

应用端口配置：

```powershell
docker compose up -d --no-deps --pull never --wait --wait-timeout 30 postgres
```

端口映射属于容器创建配置，修改后使用 `up` 应用。Docker 官方说明 `up` 会在配置变化时重建容器并保留挂载卷；`start` 仅启动已创建的容器。参见 [Docker Compose up](https://docs.docker.com/reference/cli/docker/compose/up/) 与 [Compose FAQ](https://docs.docker.com/compose/support-and-feedback/faq/)。

## 验收标准与实际结果

| 验收项 | 实际结果 |
|---|---|
| PostgreSQL 可以启动 | `up --wait` 返回退出码 0，容器为 `healthy` |
| 宿主机端口映射正确 | `0.0.0.0:15432->5432/tcp` 与 IPv6 对应映射 |
| 数据卷保持一致 | 修复前后均为 `skilllearningagents_pgdata`，挂载路径一致 |
| 本地应用可通过新端口认证连接 | 使用项目 PostgreSQL JDBC 42.7.8 驱动和本地配置，连接 `localhost:15432` 成功 |
| PGvector 可用 | 查询到扩展版本 `0.8.6` |
| 既有数据与迁移记录可读取 | `tech_stacks` 24 行、`vector_store` 31 行、`flyway_schema_history` 7 行 |

此次问题由宿主机网络状态引起，采用真实容器启动与宿主机 JDBC 只读查询进行回归验证；未添加无法复现系统端口保留问题的业务单元测试。验证脚本仅临时使用，验证后清理。

未验证项：Windows 重启后的保留端口分配、完整后端启动和业务端到端流程。本次未触发业务代码或数据库数据迁移。

## 后续使用

- 本地数据库连接地址：`localhost:15432`。
- 新端口配置应用后，Docker Desktop 可继续启停该容器。
- 后端经 `scripts/run-backend.ps1` 启动时，会读取 `.env`，未显式设置端口则使用 `15432`。
- 以后如再次遇到端口权限错误，先检查监听进程、系统保留范围并实际测试候选端口，再统一修改 `POSTGRES_PORT`。

## Comments

- 2026-09-03：用户授权修复 PostgreSQL 启动失败。完成端口调整与验证；Git 仓库及 GitHub 提交仍等待用户后续安排。
- 2026-09-03：后续已验证 Spring Boot 经 `15432` 连接数据库并正常启动，前端查询和学习引导通过；详见 [后端端口修复与运行验证](ISSUE-002-backend-port-binding.md)。
