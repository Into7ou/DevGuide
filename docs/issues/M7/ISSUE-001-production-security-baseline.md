# ISSUE-001：M7 上线安全基线

Status: done

> 阶段：M7 部署
> 创建日期：2026-09-04
> 更新日期：2026-09-04

## 目标

在容器编排前关闭已有阶段记录的上线阻断项：业务接口鉴权、SSE 登录上下文传播、阻塞调用线程隔离、HTTPS Session Cookie、Token 强加密/密钥轮换，以及首次部署的单实例边界。

## 修复前实际行为

- `SecurityConfig` 使用 `anyRequest().permitAll()`，匿名用户可调用业务 API。
- 整个 `/api/**` 被排除在 CSRF 防护之外，包含会写入知识库的 POST 接口。
- 多智能体链路切换到 `boundedElastic` 后，`SecurityContextHolder` 中的当前用户丢失，GitHub 工具可能回退到应用级 Token。
- 单 Agent 流式链路没有统一声明阻塞边界。
- 生产环境没有独立 Cookie/profile 配置；Token 使用无版本的 `Encryptors.standard`，无法安全轮换。
- 多实例 Session 与 OAuth authorized-client 尚无共享存储，但部署边界未明确。

## 根因

Servlet 鉴权使用 ThreadLocal，而 Reactor 调度是线程无关的；仅使用 `subscribeOn` 不会自动把登录上下文写入 Reactor Context。安全配置仍保留开发阶段的全放行和 API 级 CSRF 豁免。加密实现也缺少密文版本与历史密钥兼容层。

## 解决方案

1. 只公开健康检查、登录状态/CSRF 探针、OAuth 回调与静态资源；`/api/**`、MCP SSE 端点要求认证，匿名 API 返回 JSON 401。
2. 恢复 CSRF 防护，通过 `/api/auth/csrf` 下发令牌，Vue 在学习对话 POST 中携带服务端指定的请求头。
3. 注册 Micrometer `ThreadLocalAccessor<SecurityContext>`，启用 Reactor 自动上下文传播，并在两条流式链路订阅时显式 `contextCapture()`。
4. 两条流式 Agent 链路统一使用 `boundedElastic`，避免抓取、向量检索和工具调用占用事件线程。
5. 新密文使用 `v2:` 标识和 `Encryptors.stronger`；兼容旧无版本密文，并允许通过 `TOKEN_CIPHER_PREVIOUS_KEYS` 在轮换窗口读取历史密钥。
6. 新增 `prod` profile 和生产环境变量模板，强制安全 Session Cookie 与代理头处理。
7. 首次 M7 部署限定单后端实例；扩容前接入共享 Session 与 JDBC authorized-client。

## 验收标准

- 匿名业务请求返回 401，健康检查和登录探针保持公开。
- 已登录 POST 缺少 CSRF 时返回 403，携带令牌时成功。
- 登录用户在 `boundedElastic` 工具线程可见，下一匿名请求不继承其身份。
- 弱/占位密钥启动失败；新旧密文及轮换密钥均能正确读取。
- `prod` profile 强制 Secure/HttpOnly/SameSite Cookie 与转发头策略。
- 后端全量测试、前端全量测试与生产构建通过。

## 验证结果

- 后端：60 项测试通过。
- 前端：36 项测试通过。
- 前端：Vite 生产构建通过。
- 未执行：真实 HTTPS 反向代理、GitHub OAuth 回调及容器部署验收；这些属于后续 M7 编排任务。

## Comments

- 多智能体链路在本轮开始前已使用 `boundedElastic`，因此 P8 是“验证已有修复 + 补齐单 Agent”，不是重复修复。
