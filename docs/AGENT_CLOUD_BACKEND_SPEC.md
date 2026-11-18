# Agent 云端数据库与后端接口设计规范

本文档汇总了 RikkaHub Antigravity 中 **Agent 模块** 所需的全部云端数据库表结构与后端接口规范，供后续服务端实现使用。

客户端已实现本地隔离数据库 `agent.db`（`AgentDatabase` v3）以及对应的客户端同步逻辑（`PocketBaseAgentCloudClient` + `AgentRepository`）：
- **助手配置 (`agent_assistant`)**：仅本地存储，不同步云端。
- **对话文件夹 (`agent_folder`)**：本地 + 云端双向同步。
- **用量统计 (`agent_usage_record`)**：本地 + 云端双向同步。
- **对话线程 (`agent_conversation` / `aw_gemini_jobs`)**：新增 `folder_id` 字段关联文件夹。

---

## 1. 云端数据库设计（PocketBase Collections & SQL DDL）

### 1.1 `aw_agent_folders`（对话文件夹表）

**PocketBase Collection 配置：**
- Type: `base`
- List/View/Create/Update/Delete Rule: `@request.auth.id != "" && owner = @request.auth.id`

| 字段名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `text` (PK) | PocketBase 自动生成 15 位记录 ID |
| `owner` | `relation(users)` | 必填，所属用户 |
| `client_id` | `text` | 必填，客户端生成的 UUID，用于幂等去重 |
| `name` | `text` | 必填，文件夹名称（1~64 字符） |
| `sort_order` | `number` | 默认 `0`，排序权重 |
| `created` | `autodate` | 创建时间 |
| `updated` | `autodate` | 更新时间 |

**索引建议：**
- `UNIQUE (owner, client_id)`

**参考 SQL DDL（SQLite / PostgreSQL）：**
```sql
CREATE TABLE aw_agent_folders (
    id          TEXT PRIMARY KEY,
    owner       TEXT NOT NULL,
    client_id   TEXT NOT NULL,
    name        TEXT NOT NULL,
    sort_order  INTEGER NOT NULL DEFAULT 0,
    created     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner, client_id)
);
CREATE INDEX idx_aw_agent_folders_owner ON aw_agent_folders (owner, sort_order);
```

---

### 1.2 `aw_agent_usage_records`（用量统计表）

**PocketBase Collection 配置：**
- Type: `base`
- List/View/Create Rule: `@request.auth.id != "" && owner = @request.auth.id`
- Update/Delete Rule: `null`（仅追加，不可篡改）

| 字段名 | 类型 | 约束 / 说明 |
| --- | --- | --- |
| `id` | `text` (PK) | PocketBase 自动生成记录 ID |
| `owner` | `relation(users)` | 必填，所属用户 |
| `client_id` | `text` | 必填，客户端记录 UUID，用于幂等上传 |
| `conversation_id` | `text` | 选填，本地会话 UUID |
| `assistant_id` | `text` | 选填，本地助手 UUID |
| `model_id` | `text` | 选填，模型 ID（如 `Antigravity`） |
| `model_name` | `text` | 选填，模型展示名称 |
| `provider_id` | `text` | 选填，渠道标识（如 `anyworkflow`） |
| `prompt_tokens` | `number` | 默认 `0` |
| `completion_tokens` | `number` | 默认 `0` |
| `cached_tokens` | `number` | 默认 `0` |
| `reasoning_tokens` | `number` | 默认 `0` |
| `total_tokens` | `number` | 默认 `0` |
| `duration_ms` | `number` | 默认 `0`，耗时（毫秒） |
| `source` | `text` | 默认 `agent` |
| `created_at_ms` | `number` | 必填，客户端记录时间戳（Unix ms） |

**索引建议：**
- `UNIQUE (owner, client_id)`
- `INDEX (owner, created_at_ms DESC)`

**参考 SQL DDL：**
```sql
CREATE TABLE aw_agent_usage_records (
    id                TEXT PRIMARY KEY,
    owner             TEXT NOT NULL,
    client_id         TEXT NOT NULL,
    conversation_id   TEXT,
    assistant_id      TEXT,
    model_id          TEXT,
    model_name        TEXT,
    provider_id       TEXT,
    prompt_tokens     BIGINT NOT NULL DEFAULT 0,
    completion_tokens BIGINT NOT NULL DEFAULT 0,
    cached_tokens     BIGINT NOT NULL DEFAULT 0,
    reasoning_tokens  BIGINT NOT NULL DEFAULT 0,
    total_tokens      BIGINT NOT NULL DEFAULT 0,
    duration_ms       BIGINT NOT NULL DEFAULT 0,
    source            TEXT NOT NULL DEFAULT 'agent',
    created_at_ms     BIGINT NOT NULL,
    created           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner, client_id)
);
CREATE INDEX idx_aw_agent_usage_owner_time ON aw_agent_usage_records (owner, created_at_ms DESC);
```

---

### 1.3 `aw_gemini_jobs`（已有表扩展字段）

在现有的 `aw_gemini_jobs` 表中增加 1 个字段，用于保存会话所属文件夹：

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `folder_id` | `text` | 选填，对应 `aw_agent_folders.client_id` 或 `aw_agent_folders.id`；空字符串表示未归类 |

```sql
ALTER TABLE aw_gemini_jobs ADD COLUMN folder_id TEXT DEFAULT '';
```

---

## 2. 后端 REST API 接口规范

所有接口均需在请求头携带用户鉴权 Token：
```http
Authorization: <pocketbase_auth_token>
Content-Type: application/json
```

### 2.1 文件夹同步接口（PocketBase 标准 Collections API）

客户端 `PocketBaseAgentCloudClient` 已直接对接以下路径：

1. **获取文件夹列表**
   - `GET /api/collections/aw_agent_folders/records?perPage=200&sort=sort_order,created`
   - 响应 `200 OK`:
     ```json
     {
       "page": 1,
       "perPage": 200,
       "totalItems": 2,
       "items": [
         {
           "id": "pb_folder_001",
           "owner": "user_001",
           "client_id": "0196f...",
           "name": "论文翻译",
           "sort_order": 0,
           "created": "2026-04-01 10:00:00.000Z",
           "updated": "2026-04-01 10:00:00.000Z"
         }
       ]
     }
     ```

2. **创建文件夹**
   - `POST /api/collections/aw_agent_folders/records`
   - 请求体:
     ```json
     {
       "owner": "user_001",
       "client_id": "0196f...",
       "name": "论文翻译",
       "sort_order": 0
     }
     ```
   - 响应 `200 OK`: 返回创建后的记录对象（含 `id`）。

3. **更新/重命名文件夹**
   - `PATCH /api/collections/aw_agent_folders/records/{remoteId}`
   - 请求体:
     ```json
     {
       "owner": "user_001",
       "client_id": "0196f...",
       "name": "新名称",
       "sort_order": 1
     }
     ```

4. **删除文件夹**
   - `DELETE /api/collections/aw_agent_folders/records/{remoteId}`
   - 服务端建议：删除文件夹时同步将 `aw_gemini_jobs` 中引用该文件夹的 `folder_id` 置空。

---

### 2.2 用量统计同步接口

1. **拉取历史用量记录**
   - `GET /api/collections/aw_agent_usage_records/records?perPage=200&sort=-created_at_ms`
   - 响应 `200 OK`:
     ```json
     {
       "items": [
         {
           "id": "pb_usage_001",
           "owner": "user_001",
           "client_id": "0196f...",
           "conversation_id": "0196f...",
           "assistant_id": "00000000-0000-0000-0000-000000000000",
           "model_id": "Antigravity",
           "model_name": "Antigravity",
           "provider_id": "anyworkflow",
           "prompt_tokens": 1200,
           "completion_tokens": 450,
           "cached_tokens": 300,
           "reasoning_tokens": 180,
           "total_tokens": 1650,
           "duration_ms": 4200,
           "source": "agent",
           "created_at_ms": 1775000000000
         }
       ]
     }
     ```

2. **上传单条用量记录**
   - `POST /api/collections/aw_agent_usage_records/records`
   - 请求体同上（不含 `id`）。
   - 幂等性要求：若 `(owner, client_id)` 已存在，建议返回已存在的记录（或 `200`）避免重复计费/统计。

3. **（可选）聚合汇总接口**
   - `GET /api/anyworkflow/agent/usage/summary?since={unix_ms}`
   - 响应 `200 OK`:
     ```json
     {
       "promptTokens": 120000,
       "completionTokens": 45000,
       "cachedTokens": 30000,
       "reasoningTokens": 15000,
       "totalTokens": 165000,
       "requestCount": 42,
       "byModel": [
         {
           "modelId": "Antigravity",
           "promptTokens": 120000,
           "completionTokens": 45000,
           "totalTokens": 165000
         }
       ]
     }
     ```

---

### 2.3 现有线程接口扩展（`folderId` 支持）

1. **`PATCH /api/anyworkflow/gemini/jobs/{jobId}/metadata`**
   - 新增可选请求字段：`folderId?: string`（传 `""` 表示移出文件夹）。
   - 服务端应将 `folderId` 更新到 `thread_root` 对应的最新记录或整条线程的所有 job 记录上。

2. **`GET /api/anyworkflow/gemini/threads`**
   - 返回的每个线程对象建议补充字段 `folderId: string | null`，以便客户端换机或刷新列表时自动恢复文件夹归属。

---

## 3. 同步策略与幂等约定

1. **离线优先（Offline-First）**：
   - 文件夹与用量记录均先写入本地 `agent.db`（`sync_status = 'PENDING'`）。
   - 有有效云端 Session 时立即异步推送；推送成功则更新 `remote_id` 与 `sync_status = 'SYNCED'`。
   - 下次进入侧边栏或刷新时，先推送本地 `PENDING` 记录，再拉取远端全量合并。
2. **向后兼容**：
   - 在服务端尚未部署 `aw_agent_folders` 与 `aw_agent_usage_records` 集合前，客户端已做 `runCatching` 容错，不会影响本地文件夹分类、用量记录与 Agent 对话的正常使用。
