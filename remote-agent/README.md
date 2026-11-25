# RikkaHub 远程开发 API

Worker 提供客户端 API 与任务派发。PocketBase 保存项目、会话、任务、审批和 ACP 事件。Linux Gateway 在会话专属的 Git Worktree 中启动适配器，私有 R2 桶保存附件与产物。现有 Android 和 AnyWorkflow 入口见[迁移方案](../docs/REMOTE_AGENT_MIGRATION.md)。

## API v1

使用 `Authorization: Bearer <aw_clients token>`。列表接口的 `page` 从 1 开始，返回 `items` 和 `nextPage`。除文件使用 UUID 外，资源 ID 均为 PocketBase 记录 ID。

| 方法和路径 | 请求与结果 |
| --- | --- |
| `GET /v1/projects` | 返回当前账号可访问的项目和启用的 Agent |
| `GET /v1/sessions` | 返回持久化会话列表和每个会话的最新任务摘要 |
| `POST /v1/sessions` | 提交 `projectId`、`agent`、`title`，返回会话 |
| `GET /v1/sessions/{id}` | 返回会话及最近一次 ACP 声明的配置项 |
| `POST /v1/sessions/{id}/metadata` | 更新 `title` 或 `pinned`，返回会话 |
| `POST /v1/sessions/{id}/delete` | 将已结束的会话移出列表并阻止新任务，保留服务端记录 |
| `GET /v1/sessions/{id}/tasks` | 按最新优先返回会话轮次 |
| `POST /v1/sessions/{id}/tasks` | 提交 `prompt`，可选 `runAt`（Unix 毫秒）、`config` 和 `files`，返回任务 |
| `GET /v1/tasks/{id}` | 返回任务状态、事件游标和待处理审批 |
| `GET /v1/tasks/{id}/events?after=0` | 返回顺序事件、`cursor`、`state` 和 `done`。请求 `Accept: text/event-stream` 时提供 SSE |
| `POST /v1/tasks/{id}/approvals/{approvalId}` | 提交审批事件中提供的 `optionId` |
| `POST /v1/tasks/{id}/cancel` | 请求取消，返回任务状态 |
| `POST /v1/tasks/{id}/retry` | 可选 `runAt`，返回含 `retryOf` 的新任务 |
| `PUT /v1/artifacts/{uuid}` | 上传文件字节，需要 `Content-Length`，单文件最大 20 MiB |
| `GET /v1/artifacts/{uuid}` | 下载当前账号的文件 |

创建会话、创建任务与重试时提供 `Idempotency-Key` 或 JSON 的 `requestKey`。每次新操作生成一个 UUID，重发同一次操作时复用。同一个键对应不同参数时返回 HTTP 409。

任务 `config` 支持 `model` 和 `thought_level`，取值来自该适配器声明的会话配置。首轮初始化后可从会话读取配置项。后续轮次使用相同 ACP 会话及 Worktree。

```json
{
  "prompt": "读取附件，并将结果写到指定路径。",
  "files": {
    "inputs": [{"id": "8e67cd33-4a0a-4682-ad86-bb47037d54f3", "name": "input.txt"}],
    "outputs": ["reports/result.txt"]
  }
}
```

先上传文件，再在任务中引用。Gateway 将文件放入该轮独立目录，并在成功结束时上传指定产物或日志。每个文件通过 `artifact` 事件返回 `id`、`path` 和 `size`。每轮最多五个输入和五个输出，单文件 20 MiB。

SSE 持久事件的 `id` 为序号。重连使用 `Last-Event-ID` 或 `after`。Worker 每约 25 秒结束一次连接，客户端使用退避策略重连，收到 `state` 事件中的 `done: true` 后停止。 `update` 事件携带 ACP 文本、思考和工具更新，其他持久事件包括 `approval`、`artifact`、`stop` 和 `error`。审批只接受本次选择，等待时间两分钟。

任务状态包括 `scheduled`、`queued`、`running`、`canceling`、`succeeded`、`failed`、`canceled`、`interrupted`。租约失效产生 `interrupted`，由用户确认工作区状态后显式重试。

## 并行部署

1. 备份现有 PocketBase，记录版本。将新增迁移和 hooks 安装到服务配置的目录。为 PocketBase 设置独立的随机 `RIKKA_GATEWAY_SECRET`、`RIKKA_DISPATCH_SECRET` 和授权账号 ID `RIKKA_GATEWAY_OWNER`。以 PocketBase 服务账号执行迁移并重载服务，检查原有 Client 与 Agent 路由。
2. 准备 Node 24，确认 systemd 示例中的 Node 路径与服务器一致。随后在 Gateway 安装目录运行 `npm ci --omit=dev`。将适配器安装在配置的路径。已测试的 npm 版本为 `@agentclientprotocol/codex-acp@2.12.0`、`@agentclientprotocol/claude-agent-acp@0.112.0` 和 `opencode-ai@1.19.6`。原生入口为 `agy acp --sandbox`、`cmd acp` 和 `opencode acp`。
3. 按[部署示例](deploy/)配置专用 Linux 用户、持久目录和 systemd。Gateway 环境文件设置相同的 `RIKKA_GATEWAY_SECRET` 和独立的 `RIKKA_ARTIFACT_SECRET`。以该用户完成各 CLI 的认证。公共工具由 `PATH` 提供，缓存和 Skills 放在该账号持久目录，公共 MCP 通过顶层 `mcpServers` 声明，适配器可按名称覆盖。GitHub 凭据使用仓库范围的 Git 凭证助手或部署密钥。
4. 管理员在 `aw_remote_projects` 添加项目，填写 `owner`、`name`、`workspaceKey`、`enabled` 和允许使用的 `agents`。在 Gateway 配置中把同一 `workspaceKey` 映射到已经授权的本地检出。首次执行按配置的基准引用创建 detached Worktree。
5. 按 [Wrangler 配置](wrangler.jsonc)绑定现有 PocketBase 的 HTTPS 地址、Queue 和私有 R2 桶。跨域 Web 客户端可通过 `CLIENT_ORIGINS` 配置以逗号分隔的可信 HTTPS Origin。设置 Worker Secret `DISPATCH_SECRET`、`ARTIFACT_GATEWAY_SECRET`，并将 `ARTIFACT_GATEWAY_OWNER` 指向同一授权账号。短期延迟用 Queue，长延时用 Workflow，Cron 补偿漏派发。启动 Gateway，验收后开放 Worker 地址。
6. 回退时关闭新 Worker 路由和 Gateway，保留会话记录和 Worktree 供恢复，原 AnyWorkflow 路由继续服务 Android。迁移的 down 步骤会删除新数据，仅适用于空的测试实例。

在 Ubuntu 启用了 AppArmor 用户命名空间限制、且 Codex 报 `bwrap: loopback: Failed RTM_NEWADDR` 时，可使用 [Gateway 专用的 AppArmor 配置](deploy/rikka-codex-bwrap.apparmor)。把发行版的 `/usr/bin/bwrap` 安装为 root 所有、权限 0755 的 `/opt/rikka-remote-agent/bin/bwrap`，将该目录放在 Gateway 的 `PATH` 前部。把配置安装到 `/etc/apparmor.d/rikka-codex-bwrap` 后，运行 `apparmor_parser -r /etc/apparmor.d/rikka-codex-bwrap`。发行版更新 bubblewrap 后同步更新该副本。[Ubuntu 用户命名空间限制](https://documentation.ubuntu.com/release-notes/24.04/) · [Codex Linux 沙盒](https://github.com/openai/codex/blob/main/codex-rs/linux-sandbox/README.md)

当前 Gateway 面向一个受信任的 PocketBase 账号和专用 Linux 用户。Worktree 隔离工作文件，系统权限和各适配器的沙盒决定工具权限。多租户部署需要独立的操作系统安全边界。

## 验证

在 `remote-agent/` 中使用 Node 24 和与服务端版本匹配的 PocketBase。

```bash
npm ci
RIKKA_TEST_POCKETBASE=/path/to/pocketbase \
RIKKA_TEST_WRANGLER=/path/to/wrangler \
npm test
```

设置 `RIKKA_TEST_REAL_CONFIG` 指向以 Agent ID 为键的启动配置 JSON，并通过 `RIKKA_TEST_AGENT_HOME` 指定已经授权的测试账号目录，即可测试真实适配器。测试使用临时 PocketBase、R2 模拟绑定和临时 Git 仓库，要求 Agent 读取已提交文件、调用 `git --version`，再通过新的 ACP 进程续聊。
