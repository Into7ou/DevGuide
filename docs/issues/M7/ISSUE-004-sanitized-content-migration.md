# 脱敏内容迁移与恢复

所属阶段：M7 部署
创建日期：2026-09-04
更新日期：2026-09-05
Status: ready-for-human

## 目标

把本地已经积累的技术栈和 RAG 展示内容迁移到 OCI PostgreSQL/PGvector，同时确保本地用户身份、GitHub Token 和加密密钥不会进入公网环境。

## 当前数据基线

2026-09-04 只读统计：`tech_stacks` 26 条、`vector_store` 44 条、`users` 1 条、`user_tokens` 1 条。数据库卷为 `skilllearningagents_pgdata`。

## 已确认方案

- 采用 PostgreSQL 逻辑备份，不复制 Docker 数据卷。
- 迁移数据库结构、Flyway 历史、`tech_stacks` 和 `vector_store` 数据。
- `users`、`user_tokens` 只保留表结构，不导出数据。
- 脱敏备份保存在仓库外并限制访问，不进入 Git、Actions Artifact 或容器镜像。
- OCI 先启动 PostgreSQL 并恢复备份，再启动后端执行 Flyway 校验；生产使用全新 Token 加密密钥。

## 验收标准

- 导出前后对备份内容执行表级检查，确认不存在用户和 Token 行。
- 在隔离的临时数据库完成一次恢复演练，不以“命令成功”代替数据验证。
- 恢复后 `tech_stacks`、`vector_store` 计数及代表性来源正确，`users`、`user_tokens` 计数为 0。
- 后端能在恢复库上启动，Flyway 无校验错误，PGvector 查询可用。
- 首次生产 GitHub OAuth 登录创建新用户和采用新密钥的 Token，不依赖本地账号数据。

## 验证结果

- 已提供仓库外导出脚本与自动清理的隔离恢复验证脚本。
- 2026-09-05 实际演练结果：tech_stacks=26、vector_store=44、users=0、user_tokens=0、Flyway 历史=8、vector 扩展=1、非空向量=44。
- 临时迁移包与隔离容器已在验证后删除；仓库中无数据库转储。
- 尚待 OCI 实例上执行正式导入、生产后端启动及首次生产 OAuth 登录，因此转为 ready-for-human。

## Comments

- 2026-09-04：用户确认只迁移技术栈与 RAG 内容，不迁移用户或 Token。
