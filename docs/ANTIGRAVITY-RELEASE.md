基于官方 RikkaHub **3.2.0** 的 Antigravity Interactions API Debug 版。

- Android 8.0 及以上；普通手机优先下载 **arm64-v8a-debug.apk**，不确定架构时下载 **universal-debug.apk**。
- 独立包名 `me.rerere.rikkahub.debug`，可与官方正式版并存；**Build 5 起**显式使用固定签名，并核对 APK 证书。Build 3/4 的临时签名不同，升级前请导出数据，再卸载旧修改版、安装新版并导入；从 Build 5 升级后续构建可直接覆盖。
- 提供商选 Google Gemini，Base URL 为 `https://generativelanguage.googleapis.com/v1beta`，开启 **Interactions API**，关闭 Vertex AI。
- 手动添加模型 ID：`antigravity-preview-09-2026`。清空旧的自定义 `model`、`agent`、`generation_config`；在 APK 中填写自己的 AI Studio API Key。
- 先新建聊天，发送「你好」。温度、top-p、普通输出 token 上限不适用于 Agent，本补丁不发送这些参数。
- 这是无状态基础适配：支持请求构造及现有文本/思考/服务端工具事件解析；每次调用创建新远程环境，尚未实现沙盒持久化、文件下载、后台任务或客户端 MCP/函数工具的续接。
- 支持通过私人提供商二维码配置 GitHub HTTPS Git/REST 出站认证；公开 APK 不含任何个人 GitHub Token。新版隐藏请求日志中的远程环境/MCP 认证信息。
- Actions 运行请求回归测试、构建 APK 并验证签名；未使用用户 API Key 调用 Google 实测，仍需安装后验证账号权限和真实响应。
- Debug 版关闭 Firebase Analytics 和 Crashlytics 数据收集，使用本地占位配置，无需 Firebase 密钥。

源代码及详细说明见仓库的 [ANTIGRAVITY.md](https://github.com/ActiveInsighter/rikkahub-antigravity/blob/main/ANTIGRAVITY.md)。
