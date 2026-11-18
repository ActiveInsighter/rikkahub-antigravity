# 服务端后端迁移指南（PocketBase + Worker + Converter Worker）

本文档汇总了本次支持 **自定义 Gemini 模型 / 思考程度透传** 与 **Agent 定时工作流** 所需的全部服务端后端改动，便于后续一次性迁移到线上 PocketBase、AnyWorkflow-workers 与 Anyworkflow-remote。

---

## 1. PocketBase Hook：`pb_hooks/gemini_validation.js`

允许 `thinkingLevel` 传入 `'off'`（用于显式关闭思考）：

```js
// 原代码：
// if (!['', 'minimal', 'low', 'medium', 'high'].includes(thinkingLevel)) throw Error('思考程度无效');

// 修改为：
if (!['', 'off', 'minimal', 'low', 'medium', 'high'].includes(thinkingLevel)) throw Error('思考程度无效');
```

---

## 2. PocketBase Hook：`pb_hooks/gemini_agent.js`

放开 `agentModel` 的硬编码白名单，改为使用 `model_id.js` 校验任意合法模型 ID：

```js
const DEFAULT_AGENT = 'antigravity-preview-09-2026';
const DEFAULT_AGENT_MODEL = 'gemini-4-hydrogen';

function isAntigravity(model) {
  return /^antigravity(?:$|-)/i.test(String(model || ''));
}

function normalizeAgent(body) {
  const raw = body.agentModel === undefined || body.agentModel === ''
    ? DEFAULT_AGENT_MODEL
    : body.agentModel;
  const model = require('./model_id.js').normalizeModelId(raw);
  return {
    id: /^antigravity$/i.test(body.model) ? DEFAULT_AGENT : String(body.model).toLowerCase(),
    model,
  };
}

module.exports = { DEFAULT_AGENT, DEFAULT_AGENT_MODEL, isAntigravity, normalizeAgent };
```

---

## 3. PocketBase 数据库迁移：`pb_migrations/1795000000_agent_schedules.js`

新增 `aw_agent_schedules` 集合，用于存储 Agent 定时工作流任务：

```js
migrate((app) => {
  const clients = app.findCollectionByNameOrId('aw_clients');
  const owned = '@request.auth.id != "" && @request.auth.collectionName = "aw_clients" && owner = @request.auth.id';
  const createOwned = '@request.auth.id != "" && @request.auth.collectionName = "aw_clients" && @request.body.owner = @request.auth.id';

  const collection = new Collection({
    name: 'aw_agent_schedules',
    type: 'base',
    listRule: owned,
    viewRule: owned,
    createRule: createOwned,
    updateRule: owned,
    deleteRule: owned,
    fields: [
      { name: 'owner', type: 'relation', collectionId: clients.id, required: true, maxSelect: 1, cascadeDelete: true },
      { name: 'name', type: 'text', required: true, max: 200 },
      { name: 'prompt', type: 'text', required: true, max: 12000 },
      { name: 'systemPrompt', type: 'text', max: 12000 },
      { name: 'model', type: 'text', required: true, max: 120 },
      { name: 'thinkingLevel', type: 'text', max: 20 },
      { name: 'intervalMinutes', type: 'number', required: true, min: 5, max: 43200 },
      { name: 'enabled', type: 'bool' },
      { name: 'nextRunAt', type: 'date' },
      { name: 'lastRunAt', type: 'date' },
      { name: 'lastJobId', type: 'text', max: 32 },
      { name: 'lastStatus', type: 'text', max: 32 },
      { name: 'lastError', type: 'text', max: 1000 },
    ],
    indexes: [
      'CREATE INDEX idx_aw_agent_schedules_owner ON aw_agent_schedules (owner)',
      'CREATE INDEX idx_aw_agent_schedules_due ON aw_agent_schedules (enabled, nextRunAt)',
    ],
  });

  app.save(collection);
}, (app) => {
  app.delete(app.findCollectionByNameOrId('aw_agent_schedules'));
});
```

---

## 4. PocketBase Hook：`pb_hooks/aw_agent_schedules.pb.js`

处理客户端自动填充 `owner` 与 `nextRunAt`，以及手动触发 `/run` 和定时扫描 `/sweep`：

```js
/* global routerAdd, onRecordCreateRequest, onRecordUpdateRequest, $apis, Record, BadRequestError, NotFoundError */

function computeNextRunIso(intervalMinutes, fromMs) {
  const mins = Math.max(5, Math.min(43200, Number(intervalMinutes) || 60));
  return new Date(fromMs + mins * 60 * 1000).toISOString();
}

function enqueueJobFromSchedule(tx, schedule, nowMs) {
  const owner = schedule.getString('owner');
  const model = require(__hooks + '/model_id.js').normalizeModelId(schedule.getString('model') || 'Antigravity');
  const prompt = schedule.getString('prompt').trim();
  if (!prompt) throw new BadRequestError('定时任务提示词不能为空');

  const input = require(__hooks + '/gemini_validation.js').normalizeInput({
    responseVersion: 1,
    title: '[定时] ' + schedule.getString('name'),
    prompt,
    systemPrompt: schedule.getString('systemPrompt'),
    model,
    thinkingLevel: schedule.getString('thinkingLevel'),
  });

  const job = new Record(tx.findCollectionByNameOrId('aw_gemini_jobs'));
  job.set('owner', owner);
  job.set('title', input.title);
  job.set('prompt', input.prompt);
  job.set('systemPrompt', input.systemPrompt);
  job.set('model', input.model);
  job.set('options', input.options);
  job.set('status', 'queued');
  tx.save(job);
  job.set('threadRoot', job.id);
  tx.save(job);

  const msg = new Record(tx.findCollectionByNameOrId('aw_gemini_messages'));
  msg.set('owner', owner);
  msg.set('job', job.id);
  msg.set('index', 1);
  msg.set('part', 1);
  msg.set('sourceName', '定时任务');
  msg.set('inputText', '');
  msg.set('status', 'queued');
  tx.save(msg);

  schedule.set('lastRunAt', new Date(nowMs).toISOString());
  schedule.set('nextRunAt', schedule.getBool('enabled') ? computeNextRunIso(schedule.getInt('intervalMinutes'), nowMs) : '');
  schedule.set('lastJobId', job.id);
  schedule.set('lastStatus', 'queued');
  schedule.set('lastError', '');
  tx.save(schedule);

  return { id: job.id, threadRoot: job.id, status: 'queued' };
}

onRecordCreateRequest((e) => {
  const auth = e.auth;
  if (auth && auth.collection().name === 'aw_clients') {
    e.record.set('owner', auth.id);
  }
  if (e.record.getBool('enabled') && !e.record.getString('nextRunAt')) {
    e.record.set('nextRunAt', computeNextRunIso(e.record.getInt('intervalMinutes'), Date.now()));
  }
  return e.next();
}, 'aw_agent_schedules');

onRecordUpdateRequest((e) => {
  if (e.record.getBool('enabled') && !e.record.getString('nextRunAt')) {
    e.record.set('nextRunAt', computeNextRunIso(e.record.getInt('intervalMinutes'), Date.now()));
  } else if (!e.record.getBool('enabled')) {
    e.record.set('nextRunAt', '');
  }
  return e.next();
}, 'aw_agent_schedules');

routerAdd('POST', '/api/anyworkflow/agent-schedules/{id}/run', (e) => {
  const id = e.request.pathValue('id');
  let result;
  e.app.runInTransaction((tx) => {
    let schedule;
    try {
      schedule = tx.findRecordById('aw_agent_schedules', id);
    } catch {
      throw new NotFoundError();
    }
    if (schedule.getString('owner') !== e.auth.id) throw new NotFoundError();
    result = enqueueJobFromSchedule(tx, schedule, Date.now());
  });
  return e.json(200, result);
}, $apis.requireAuth('aw_clients'));

routerAdd('POST', '/api/anyworkflow/agent-schedules/sweep', (e) => {
  const nowMs = Date.now();
  const nowIso = new Date(nowMs).toISOString();
  const due = e.app.findRecordsByFilter(
    'aw_agent_schedules',
    'enabled = true && (nextRunAt = "" || nextRunAt <= {:now})',
    'nextRunAt',
    50,
    0,
    { now: nowIso }
  );
  const triggered = [];
  for (const item of due) {
    try {
      e.app.runInTransaction((tx) => {
        const fresh = tx.findRecordById('aw_agent_schedules', item.id);
        if (!fresh.getBool('enabled')) return;
        triggered.push(enqueueJobFromSchedule(tx, fresh, nowMs));
      });
    } catch (err) {
      item.set('lastStatus', 'failed');
      item.set('lastError', String(err && err.message ? err.message : err).slice(0, 500));
      item.set('nextRunAt', computeNextRunIso(item.getInt('intervalMinutes'), nowMs));
      e.app.save(item);
    }
  }
  return e.json(200, { triggered: triggered.length, jobs: triggered });
}, $apis.requireSuperuserAuth());
```

---

## 5. Worker 定时调度：`AnyWorkflow-workers`

在 `AnyWorkflow-workers/src/domains/schedules/service.mjs` 的定时轮询 pass 中追加对 `/api/anyworkflow/agent-schedules/sweep` 的调用；当 `triggered > 0` 时，唤醒 `converter-worker`（`POST https://file.any1.tech/api/file-conversion/wake` 或对应内部触发接口），使其立即派发 `gemini-text.yml`。

---

## 6. Cloudflare Worker：`Anyworkflow-remote/converter-worker/gemini.ts`

1. 将 `GITHUB_GEMINI_RESPONSE_REF` 指向包含 `Agent-gemeni` 新分支 `feat/dynamic-gemini-models` 的分支（或合并到 `main`）。
2. 在 `geminiEngine` 的 `dispatch(inputs)` 中，可将 PocketBase `aw_gemini_jobs` 的 `model` 与 `thinkingLevel` 一并透传给 `gemini-text.yml`：

```ts
const options = 'options' in job && job.options && typeof job.options === 'object' ? job.options : {}
const dispatchInputs = {
  ...inputs,
  model: typeof job.model === 'string' ? job.model : '',
  thinking_level: typeof options.thinkingLevel === 'string' ? options.thinkingLevel : '',
}
```
