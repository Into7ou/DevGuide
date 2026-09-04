# 生产域名与 OAuth 外部配置

所属阶段：M7 部署
创建日期：2026-09-04
更新日期：2026-09-05
Status: ready-for-human

## 目标

取得 DevGuide 首发域名并创建与本地环境隔离的生产 GitHub OAuth App，为真实 HTTPS 登录验收提供外部配置。

## 已确认方案

- 申请 `into7ou.is-a.dev`，DevGuide 规范地址使用 `devguide.into7ou.is-a.dev`，根地址保留给未来个人主页。
- 现有本地 GitHub OAuth App 和 `localhost:18080` Callback 保持不变。
- 新建 `DevGuide Production` OAuth App：
  - Homepage：`https://devguide.into7ou.is-a.dev`
  - Callback：`https://devguide.into7ou.is-a.dev/login/oauth2/code/github`
  - Callback wildcard：关闭
  - Device Flow：关闭
- 生产 Client ID/Secret 只进入 OCI 受保护的环境文件，不提交 Git、不写进镜像或 Actions。

## 需要人工完成

1. 取得 OCI 公网实例和固定公网 IP。
2. 按 `is-a.dev` 社区流程提交域名申请与 DNS 记录并等待审核。
3. 在 GitHub Developer Settings 创建生产 OAuth App，保存 Client ID，并仅在 OCI 生成/保存 Client Secret。
4. 配置完成后提供“已完成”确认，不在聊天、Issue 或日志中粘贴 Secret 原值。

## 验收标准

- DNS 指向实际 OCI 地址，Caddy 为规范域名签发有效证书并能自动续期。
- GitHub 登录授权页显示生产 App，Callback 使用 HTTPS 精确域名。
- 生产登录成功创建全新用户与加密 Token，刷新后 Session 有效。
- 本地开发 App 的 localhost 登录继续正常。
- 仓库历史、镜像层、Actions 日志和部署文档均不包含 Client Secret。

## 验证结果

尚未完成外部配置。

## Comments

- 2026-09-04：用户确认开发、生产分别使用独立 GitHub OAuth App。
- 2026-09-05：生产环境模板与部署运行手册已准备完成；等待用户取得 OCI 公网地址、完成 is-a.dev 审核并创建生产 OAuth App。
