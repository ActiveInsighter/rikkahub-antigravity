# Agent 工作流后端

PocketBase 迁移、部署顺序和回退步骤见 AnyWorkflow-workers 的 [Agent 工作流生产部署](https://github.com/ActiveInsighter/AnyWorkflow-workers/blob/main/deploy/agent-workflows.md)。

Android 的输入框草稿按会话 ID 保存在已有 Room 数据库。执行总结和时间节点由 PocketBase 同步，随消息存放在已有的 JSON 列中。
