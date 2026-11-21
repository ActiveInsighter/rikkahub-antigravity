基于官方 RikkaHub **3.3.0** 的 Antigravity Interactions API Debug 版。

### 本次更新

- 修复 Gemini 聊天无法发送图片库和历史消息中的图片。
- 修复 Gemini 流式回复只显示半截的问题。
- 聊天存盘移到后台，关闭思考时减少 Gemini 返回的思考摘要。

### Updates

- Fix sending library and history images in Gemini chat.
- Recover Gemini replies after interrupted streams.
- Save chat progress in the background and omit Gemini thought summaries when thinking is off.

- Android 8.0 及以上；普通手机优先下载 **arm64-v8a-debug.apk**，不确定架构时下载 **universal-debug.apk**。
- 独立包名 `me.rerere.rikkahub.debug`，可与官方正式版并存；**Build 6 起**显式使用固定签名，并核对 APK 证书。Build 3/4 的临时签名不同，升级前请导出数据，再卸载旧修改版、安装新版并导入；从 Build 6 升级后续构建可直接覆盖。
- 提供商选 Google Gemini，Base URL 为 `https://generativelanguage.googleapis.com/v1beta`，开启 **Interactions API**，关闭 Vertex AI。
- 手动添加模型 ID：`antigravity-preview-09-2026`。清空旧的自定义 `model`、`agent`、`generation_config`；在 APK 中填写自己的 AI Studio API Key。
- 先新建聊天，发送「你好」。温度、top-p、普通输出 token 上限不适用于 Agent，本补丁不发送这些参数。
- 普通聊天直连 Antigravity 时可在同一对话续用 Google 沙盒。文件转换智能体由云端执行器按上文消息续接沙盒。
- 支持通过私人提供商二维码配置 GitHub HTTPS Git/REST 出站认证；公开 APK 不含任何个人 GitHub Token。新版隐藏请求日志中的远程环境/MCP 认证信息。
- Agent 页面使用 PocketBase Realtime 同步所有活跃会话。切换会话时，其他任务继续更新 Room，重连后会补齐遗漏分段。
- Agent 新增本地输入草稿、侧边栏运行和未读状态，以及完成与失败通知。成功回复会显示最终保存的全文，历史消息也会按自身状态显示。
- Agent 支持手动添加 Gemini 模型。普通 Gemini 可设置思考程度，Antigravity 使用托管推理。
- Agent 侧栏的「工作流」可定时执行提示词。每个任务可设置模型、系统提示词、Cron 和时区，也可暂停或立即执行。
- Antigravity 的新对话由 Gemini 3.5 Flash-Lite 生成标题。点击运行耗时或消息末尾的执行详情，可查看执行总结及派发、首响和步骤时间线。
- 文档阅读器复用 Chat 的三点菜单，修复「设置」「站点设置」「UI 设置」的跳转崩溃。
- Agent 遇到 HTTP 400 后发送下一条消息时，原轮次已同步的工具步骤、推理和错误仍会显示。
- 首页的「下载与更新」支持签名构建列表、增量更新和完整 APK 下载。更新源启用前可通过本页下载完整 APK。
- Actions 运行请求及同步回归测试，构建 APK 并验证签名。
- Debug 版关闭 Firebase Analytics 和 Crashlytics 数据收集，使用本地占位配置，无需 Firebase 密钥。

源代码及详细说明见仓库的 [ANTIGRAVITY.md](https://github.com/ActiveInsighter/rikkahub-antigravity/blob/main/ANTIGRAVITY.md)。
