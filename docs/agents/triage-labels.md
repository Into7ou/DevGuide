# 问题状态

技能中的五个标准角色对应以下状态名称：

| 技能角色 | 本项目状态 | 含义 |
|---|---|---|
| `needs-triage` | `needs-triage` | 待分析 |
| `needs-info` | `needs-info` | 待补充信息 |
| `ready-for-agent` | `ready-for-agent` | 信息充分，可交给 Agent 实现 |
| `ready-for-human` | `ready-for-human` | 需要人工处理 |
| `wontfix` | `wontfix` | 暂不处理 |
| — | `done` | 已实现并完成与风险相称的验收；项目本地问题跟踪器的终态 |

在本地问题文件顶部写入 `Status: <状态名称>`。技能要求应用或更换状态标签时，更新此字段。`done` 是本项目为保留已解决问题记录而增加的终态，不对应新的 triage 角色。
