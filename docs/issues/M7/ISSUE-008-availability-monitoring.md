# 公网可用性与 OCI 资源监控

所属阶段：M7 部署
创建日期：2026-09-04
更新日期：2026-09-05
Status: ready-for-human

## 目标

用免费监控及时发现简历展示站无法访问，并区分公网入口故障与 OCI 主机资源异常，不引入需要长期付费的监控组件。

## 已确认方案

- UptimeRobot 每 5 分钟从 OCI 外部检查规范首页与 `/api/health`。
- 首页检查验证 HTTPS 2xx；健康检查验证 HTTPS 2xx 和响应中的 `UP` 关键字。
- OCI Monitoring 覆盖实例状态、CPU 高水位和磁盘容量风险，通过 OCI Notifications 发送邮件。
- 所有监控都不得触发模型、联网搜索、GitHub API 或资料写回。

## 实施范围

- 保持 `/api/health` 为公开、廉价且无副作用的监控端点，并补充必要测试。
- 在部署指南中提供 UptimeRobot Monitor 和 OCI Alarm/Notification 的人工配置清单。
- 告警阈值以避免短暂尖峰造成告警风暴为目标，并记录调整位置。
- 监控账户、邮箱和 OCI 资源标识只在外部服务中配置，不提交仓库。

## 验收标准

- 两个 UptimeRobot Monitor 连续成功，并能分别识别入口页面与后端健康故障。
- OCI 控制台可看到实例指标，CPU、磁盘和实例状态告警均关联到已确认的邮件订阅。
- 执行一次无破坏故障/恢复演练，实际收到故障与恢复通知。
- 健康探测不会产生 Session、调用计费服务或写入数据库。

## 验证结果

- /api/health 保持公开且无业务副作用；Caddy 到后端的真实容器代理检查已返回 {"status":"UP"}。
- 部署运行手册已记录 UptimeRobot 两个 Monitor、OCI Notifications 与 CPU/磁盘/实例状态告警阈值。
- 外部 Monitor、邮件订阅和故障/恢复演练必须在真实实例、DNS 与邮箱可用后由用户完成，因此转为 ready-for-human。

## Comments

- 2026-09-04：用户确认采用 UptimeRobot 外部 5 分钟探测与 OCI Monitoring/Notifications 主机告警的零成本组合。
- 参考：UptimeRobot 免费计划当前提供 5 分钟检查间隔；OCI Always Free 当前包含 Monitoring 数据点与 Notifications 邮件额度，最终以配置时官方页面和账户限额为准。
