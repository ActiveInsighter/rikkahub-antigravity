# RikkaHub Antigravity

本仓库基于 [RikkaHub 3.3.0](https://github.com/rikkahub/rikkahub/tree/3.3.0)，上游提交 `eb33917e483aae1427db9cb10232642a640d2986`（`versionCode = 197`），保留上游 AGPL-3.0 许可证。

工作流集成架构、远端接口分工与自动化验证说明见 [docs/WORKFLOW_ARCHITECTURE.md](docs/WORKFLOW_ARCHITECTURE.md)。

## 下载与配置

打开本仓库的 [Releases](https://github.com/ActiveInsighter/rikkahub-antigravity/releases)，在最新的 Antigravity 构建下载 APK。Android 8.0 及以上，普通手机选 `arm64-v8a`，不确定选 `universal`；`x86_64` 适用于对应模拟器/设备。

这是独立的 Debug 应用，包名 `me.rerere.rikkahub.debug`，可与正式版并存。可通过官方版的数据导出与本版的数据导入迁移配置。从 Build 6 起，固定签名显式绑定到本仓库 Actions Secret，并核对 APK 证书与密钥证书一致。Build 6 及后续构建之间可以覆盖升级。Build 3/4 实际使用了各自的临时 Debug 签名，迁移到 Build 6 需要先导出数据、卸载旧修改版、再安装和导入；官方正式版不受影响。

1. 提供商：Google Gemini，Base URL：`https://generativelanguage.googleapis.com/v1beta`。
2. 开启 Interactions API，关闭 Vertex AI。
3. 手动添加模型 `antigravity-preview-09-2026`（以 Google 当前可用模型及你的项目权限为准）。
4. 清空此前自定义的 `model`、`agent`、`generation_config`、`tools`、`response_format` 字段。
5. 填自己的 AI Studio API Key，在新聊天中先发送「你好」。不要把 Key 提交到仓库或 Actions。

## 补丁行为

- 模型 ID 以 `antigravity-preview-` 开头时，使用 Interactions API 的 `agent` 参数。新对话使用远程沙盒，并可通过自定义 `environment` 配置 GitHub 出站认证。
- 不自动发送普通模型的 `generation_config`、图片 `response_format` 或 RikkaHub 工具声明，保留 Agent 默认的服务端工具。
- 普通 Gemini 请求保持原有路径。继续使用现有 Interactions 流式/非流式解析器，回归测试包含服务端代码执行事件和最终文本。
- 高级 Agent 参数仍可通过自定义请求体添加。例如 `agent_config` 的 JSON 值：

```json
{"type":"antigravity","max_total_tokens":"32768"}
```

`max_total_tokens` 是整个 Agent 运行的总 token 预算，与普通输出长度不同；按需设置。本补丁不把普通「最大输出」设置直接映射到总预算。

普通聊天直连 Antigravity 时使用 `store: true`，并把 Google 返回的 interaction 和沙盒 ID 保存在助手消息中。续聊用上一条助手消息的两个 ID，只发送本轮新增输入。历史消息缺少这两个 ID 时会用本地历史构建请求。文件转换智能体走云端任务链路，由执行器按 PocketBase 上文消息读取沙盒状态。沙盒文件的保留周期由 Google 管理。

参考：[Google Antigravity 文档](https://ai.google.dev/gemini-api/docs/antigravity-agent)、[托管 Agent 快速开始](https://ai.google.dev/gemini-api/docs/managed-agents-quickstart)、[创建 Interaction 的 API 参数](https://ai.google.dev/api/interactions-api)。

## Actions 构建

`Antigravity Debug APK` 工作流在推送 `main` 或从任一包含该工作流的分支手动运行时执行。构建成功后，在该次运行的 **Summary → Artifacts** 直接下载对应的 `arm64-v8a`、`x86_64` 或 `universal` APK。运行摘要提供 SHA-256 校验值，预发布 Release 也提供 APK 和校验清单。

环境：JDK 21、上游 Gradle Wrapper、Android SDK 37.2、CMake 3.22.1、Node 22、pnpm 11。库模块的 compileSdk 显式与 app 对齐到 37.2，避免 SDK 仓库不存在 android-37 包名的问题。Gradle 按上游配置安装所需 NDK 等依赖。

`tools/antigravity/prepare-debug.py` 生成非真实项目的 Firebase 占位配置，只用于满足上游的初始化要求；Debug Manifest 关闭 Analytics/Crashlytics 收集。无需提供 Firebase 项目或 Google API Key 来构建。

唯一必须的仓库 Secret 为 `ANDROID_DEBUG_KEYSTORE_BASE64`（固定 Debug keystore 的 Base64）。本仓库已配置。若你另行 Fork，应创建自己的签名并设置此 Secret；新签名不能覆盖安装本仓库签名的 APK。

上游每日构建保留手动入口，已去除定时触发；它依赖上游私有发布配置，请使用本仓库的 `Antigravity Debug APK` 工作流。

## 本地构建

使用 Android 开发环境并准备 JDK 21、Node/pnpm 后：

```bash
python3 tools/antigravity/prepare-debug.py
pnpm --dir web-ui install --frozen-lockfile
./gradlew :ai:testDebugUnitTest --tests me.rerere.ai.provider.providers.google.InteractionsApiTest
./gradlew :app:assembleGithubDebug
```

APK 在 `app/build/outputs/apk/github/debug/`。本地默认 Debug 签名可能不同于 Actions；若需要覆盖升级，使用同一份签名。

`patches/rikkahub-antigravity-interactions.patch` 只包含协议补丁，可用于干净的上游 3.2.0 源码：

```bash
git apply --check rikkahub-antigravity-interactions.patch
git apply rikkahub-antigravity-interactions.patch
```

## 私人 GitHub 认证配置

参考 `ActiveInsighter/file-converter-ai` 的线上 `gemini_agent.py`：通过远程环境的 network allowlist 向 `github.com` 注入 HTTPS Git Basic 认证，向 `api.github.com` 注入 REST Bearer 认证，并保留普通出站网络。Token 不放入环境变量或提示词；GitHub MCP 默认关闭。支持范围由该 Token 的实际 GitHub 权限决定。

使用 `tools/antigravity/create-github-provider.py` 生成私人二维码及 JSON（需要 Python 的 `qrcode[pil]`）：它读取 `RIKKA_GITHUB_TOKEN` 或本机 `gh` 登录，不把 Token 输出到终端。结果位于 Git 忽略的 `.private-config/`，目录和文件分别限制为 700/600；不得提交到仓库或上传公开 Release。

在手机「设置 → 提供商 → 导入 → 扫描二维码/从相册选择」导入，得到 `Antigravity GitHub` 提供商及已配置的模型。Google API Key 留空，请在手机填入你现有的 Key，然后在新聊天选择 `Antigravity + GitHub`。

如果想沿用原提供商，可将私人 `github-environment.json` 的对象填入原模型自定义请求体的 `environment` 字段，并在助手提示词说明 Git/REST 已由远程代理配置认证。生成的完整提供商还附有独立的 GitHub 使用说明；它不会更改你已有提供商。

不要在沙盒运行 `gh auth login` 或索要 Token。可先让 Agent 用 GitHub REST `/user` 确认登录账号，再读取目标仓库。修改源码后推送功能分支并核对远端提交。当前本机登录缺少 `workflow` scope，因此编辑 Actions 工作流可能被 GitHub 拒绝；普通仓库读写不因此失效。

本版会省略 Interactions 的原始 logcat 请求体/事件内容，并在应用请求日志中隐藏远程网络 transform、MCP headers 和 env；HTTP 日志同时隐藏常见鉴权头。公开 APK 及构建附件不含你的私人配置。Build 6 起固定签名已显式指定并核对；旧 Build 3/4 须按前述方式迁移。已有版本支持 GitHub 配置，导入凭据本身无需重编译。

## 应用内下载与增量更新

GitHub 渠道的 Antigravity Debug APK 在首页右上角提供「下载与更新」。它会列出更新服务器上最近 10 次发布，按构建号降序展示。可直接选择任意比当前安装更新的构建。Android 会阻止降级安装。需要撤回某次构建时，可以用旧代码重新构建并以更高的版本号发布。旧代码对数据格式的兼容性需要按对应版本验证。

客户端会按已安装 APK 的版本号和完整 SHA-256 匹配补丁，兼容通用包及单 ABI 包。合成结果经大小、SHA-256、应用包名、版本号和签名检查后，由 Android 的安装界面确认。补丁不可用时自动改用完整 APK。下载页也可以直接选择完整包。

### 配置 R2 发布

在 GitHub 仓库的 **Settings → Secrets and variables → Actions** 设置以下内容。

| 类型 | 名称 | 内容 |
| --- | --- | --- |
| Variable | `R2_UPDATE_ACCOUNT_ID` | Cloudflare Account ID |
| Variable | `R2_UPDATE_BUCKET` | R2 桶名称 |
| Variable | `R2_UPDATE_PUBLIC_URL` | 桶的公网 HTTPS 自定义域名，末尾包含 `/antigravity-updates`，例如 `https://downloads.example.com/antigravity-updates` |
| Secret | `R2_UPDATE_ACCESS_KEY_ID` | 桶范围的 R2 Object Read & Write 凭据的 Access Key ID |
| Secret | `R2_UPDATE_SECRET_ACCESS_KEY` | 同一凭据的 Secret Access Key |

[R2 S3 凭据配置](https://developers.cloudflare.com/r2/api/tokens/) · [R2 自定义域名](https://developers.cloudflare.com/r2/buckets/public-buckets/)

`Antigravity Debug APK` 工作流在 `main` 成功构建后写入桶的 `antigravity-updates/` 前缀。它先上传三种 ABI 的完整安装包和可节省至少 10% 的补丁，再写入签名后的 `catalog.json`，最后清理超出保留范围的对象。如果自定义域配置了缓存规则，请让 `/antigravity-updates/catalog.json` 遵循对象的 `Cache-Control: no-store`。APK 和补丁可按其内容哈希缓存。R2 凭据仅用于 Actions，客户端只读取公网索引和文件。

配置完成后，从 `main` 新启动一次工作流。第一次发布会建立全量基线，随后每次发布会为留存版本生成可用的直达补丁。公网地址随 APK 构建写入。此前未配置地址的 APK，需要从 GitHub 手动覆盖安装一次支持该地址的新版。构建以 `100000 + GITHUB_RUN_NUMBER` 作为 Android 版本号。同一次运行的重试沿用原版本号，R2 保留已经发布的内容。要重发请启动新的运行。

功能分支手动运行工作流时，可设置 `publish_release=false`、`updates_smoke=true`，由 Android x86_64 模拟器验证签名 APK 的差分合成。分支验证不会写入 R2。上游 `Daily Build` 工作流使用不同包名与签名，继续维护独立的分发流程。
