# RikkaHub Antigravity

本仓库基于 [RikkaHub 3.2.0](https://github.com/rikkahub/rikkahub/tree/3.2.0)，上游提交 `e033a47d395fe57d16db2144069c3abdb7117547`，保留上游 AGPL-3.0 许可证。

## 下载与配置

打开本仓库的 [Releases](https://github.com/ActiveInsighter/rikkahub-antigravity/releases)，在最新的 Antigravity 构建下载 APK。Android 8.0 及以上，普通手机选 `arm64-v8a`，不确定选 `universal`；`x86_64` 适用于对应模拟器/设备。

这是独立的 Debug 应用，包名 `me.rerere.rikkahub.debug`，可与正式版并存。可通过官方版的数据导出与本版的数据导入迁移配置。固定签名保存在本仓库 Actions Secret 中，后续构建可以覆盖升级。

1. 提供商：Google Gemini，Base URL：`https://generativelanguage.googleapis.com/v1beta`。
2. 开启 Interactions API，关闭 Vertex AI。
3. 手动添加模型 `antigravity-preview-09-2026`（以 Google 当前可用模型及你的项目权限为准）。
4. 清空此前自定义的 `model`、`agent`、`generation_config`、`tools`、`response_format` 字段。
5. 填自己的 AI Studio API Key，在新聊天中先发送「你好」。不要把 Key 提交到仓库或 Actions。

## 补丁行为

- 模型 ID 以 `antigravity-preview-` 开头时，发送 `agent` 和 `environment: "remote"`，不发送 `model`；即使旧的自定义请求体含 `model`，最终请求也会移除它。
- 不自动发送普通模型的 `generation_config`、图片 `response_format` 或 RikkaHub 工具声明，保留 Agent 默认的服务端工具。
- 普通 Gemini 请求保持原有路径。继续使用现有 Interactions 流式/非流式解析器，回归测试包含服务端代码执行事件和最终文本。
- 高级 Agent 参数仍可通过自定义请求体添加。例如 `agent_config` 的 JSON 值：

```json
{"type":"antigravity","max_total_tokens":"32768"}
```

`max_total_tokens` 是整个 Agent 运行的总 token 预算，与普通输出长度不同；按需设置。本补丁不把普通「最大输出」设置直接映射到总预算。

这是**无状态基础适配**：沿用 `store: false` 和客户端历史回放，每次请求创建新远程环境。尚未实现 `previous_interaction_id` / `environment_id` 持久化、远程文件下载、后台任务、客户端 MCP/函数工具续接。不要依赖上一轮沙盒文件继续存在。签名验证和自动化测试不能替代使用真实 API Key 的安装验收。

参考：[Google Antigravity 文档](https://ai.google.dev/gemini-api/docs/antigravity-agent)、[托管 Agent 快速开始](https://ai.google.dev/gemini-api/docs/managed-agents-quickstart)、[创建 Interaction 的 API 参数](https://ai.google.dev/api/interactions-api)。

## Actions 构建

`Antigravity Debug APK` 工作流在推送 `main` 或手动运行时执行，测试 `InteractionsApiTest`、构建 `:app:assembleGithubDebug`、验证 APK 签名、上传构建附件并创建含 APK 和 SHA-256 清单的预发布 Release。

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
