# 项目 Agent 配置

## Agent skills

### Issue tracker

问题和需求使用本地 Markdown，分别归档到 `docs/issues/` 和 `docs/requirements/`。参见 `docs/agents/issue-tracker.md`。

### Triage labels

采用五个默认状态名称，在问题文件的 `Status:` 字段中记录。参见 `docs/agents/triage-labels.md`。

### Domain docs

前后端共用单一上下文：根目录 `CONTEXT.md` 与 `docs/adr/`，同时参考现有需求和阶段文档。参见 `docs/agents/domain.md`。

### Documentation conventions

- 根目录的 `AGENTS.md` 是规则入口，`CONTEXT.md` 是按需生成的项目背景入口；需求、问题、计划、验证记录和阶段总结统一归档到 `docs/`。
- 创建或更新文档前，遵循 `docs/agents/domain.md` 中的目录、命名、内容与更新规范。
- 同一主题优先更新已有文档，保留现有阶段目录及文件名，不创建内容重复的副本。
