# RikkaHub 远程开发迁移

## 现状

Android 的 Agent 模块通过 AnyWorkflow 的 PocketBase hooks 提交任务，并通过 PocketBase Realtime 同步。线上使用 `aw_clients` 登录，`aw_gemini_jobs` 保存线程和任务，`aw_gemini_messages` 保存轮次和供应商状态，`aw_agent_parts` 保存流式分段。文件夹、用量和计划已有独立集合。Linux 上有独立的 Codex、Claude Code、Antigravity CLI、Command Code 和 Gemini systemd 服务，其工作目录、协议和环境文件各异。Android 图片资料库和版本发布已经使用 R2。

## 迁移顺序

1. 并行部署 `/v1`。沿用现有 PocketBase 实例及 `aw_clients`，新增远程开发专用集合。旧任务、计划、实时同步与文件转换继续通过老接口运行。Git 项目由管理员登记，并绑定到已授权的用户。
2. Gateway 使用 ACP v1 TypeScript SDK。Codex 与 Claude Code 使用 ACP 项目维护的适配器，Antigravity、OpenCode 和 Command Code 使用厂商的原生 ACP 命令。ACP 会话与服务端 Git Worktree 绑定，在 Linux 用户下运行。适配器的模型、思考程度和跨进程恢复能力通过 ACP 握手和会话配置实测。
3. Worker 暴露统一 API，使用 PocketBase 验证用户、按任务 ID 发布队列信号。短期延时用 Queue，长等待用 Workflow，Cron 补偿漏发。PocketBase 事务处理重复派发、每会话串行领取和执行围栏。未知结果标记为中断，由用户检查工作区后显式重试。
4. 按 Agent 开放新会话。现有 providerState 由原协议写入，旧会话继续使用其原执行器，直到有证据表明相应 ACP 适配器能可靠导入。Android 在新链路达到功能对等后切换新增对话入口。回退时停止新链路的入口和派发，保留已写入的记录与 Worktree。

参考资料：[ACP v1 会话](https://agentclientprotocol.com/protocol/v1/session-setup) · [ACP TypeScript SDK](https://agentclientprotocol.com/libraries/typescript) · [Claude 适配器](https://github.com/agentclientprotocol/claude-agent-acp) · [Codex 适配器](https://github.com/agentclientprotocol/codex-acp) · [Antigravity](https://antigravity.google/docs/cli/acp/) · [OpenCode](https://opencode.ai/docs/cli/acp/) · [Command Code](https://commandcode.ai/docs/acp) · [Queues](https://developers.cloudflare.com/queues/reference/delivery-guarantees/) · [Workflows](https://developers.cloudflare.com/workflows/) · [PocketBase 规则](https://pocketbase.io/docs/api-rules-and-filters/) · [R2 Workers API](https://developers.cloudflare.com/r2/api/workers/workers-api-reference/)。

## 生产部署记录

2026-11-25，将远程开发 Worker 发布到独立的 workers.dev 地址，并绑定 `rikka-agent-tasks`、`rikka-agent-delay` 和私有 R2 桶 `rikka-agent-private`。已有 PocketBase 实例完成在线备份后加载新增 hooks，Gateway 以 `rikka-agent` 专用用户运行。主 Agent 账号已登记公开的 RikkaHub Antigravity 项目。

五个 Agent 均通过生产链路读取该项目、调用 Git 并恢复 ACP 会话。生产 R2 的授权上传和下载已通过，原 Android 线程接口的授权读取返回 HTTP 200。GitHub 私有仓库和写入凭据需要按项目单独配置。


## Android Agent 页切换

主 Agent 页首条消息通过 ACP 创建会话和任务。客户端将会话 ID 写入 Room，后续发送把新任务提交到同一个会话。Gateway 从该会话的持久记录加载适配器的原生会话 ID 和 Worktree。历史同步按任务和事件游标恢复消息，继续轮询直到终态事件读完。

Android 复用 AnyWorkflow 登录。未迁移的旧 Agent 会话保留本地历史，需要从新会话启动 ACP。周期任务仍需服务端提供相应接口。

2026-11-25 生产部署：PocketBase 0.40.4 在停止服务、记录备份后应用了 `1795570100_remote_session_metadata.js`，并安装了更新后的 `remote_agent.js` hooks。已确认 `aw_remote_sessions` 新增 `pinned`、`deleted`、`originalTitle` 三列并写入迁移账本，原有 7 个会话保持可读。会话重命名、置顶、软删除及重命名后按原请求键重发创建请求均已在生产链路实测通过（重命名/置顶返回更新后的会话，删除后不再出现在列表且详情返回 404）。
