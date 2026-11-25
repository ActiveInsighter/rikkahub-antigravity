import { randomUUID } from 'node:crypto';
import { readFile, rm } from 'node:fs/promises';
import { userInfo } from 'node:os';
import { isAbsolute } from 'node:path';
import { setTimeout as sleep } from 'node:timers/promises';
import { executionEnvironment, runAcp, RuntimeError, worktree } from './runtime.mjs';

import { prepareFiles, publishOutputs } from './artifacts.mjs';

const config = JSON.parse(await readFile(process.argv[2], 'utf8'));
const secret = process.env.RIKKA_GATEWAY_SECRET;
if (!secret || !/^[a-z0-9]{15}$/.test(config.owner) || config.user !== userInfo().username ||
  ![config.home, config.repositoryRoot, config.worktreeRoot].every(path => typeof path === 'string' && isAbsolute(path)) ||
  typeof config.path !== 'string' || !Number.isSafeInteger(config.concurrency) ||
  config.concurrency < 1 || config.concurrency > 8) {
  throw new Error('Invalid Gateway configuration or runtime user');
}
const shutdown = new AbortController();
process.once('SIGTERM', () => shutdown.abort());
process.once('SIGINT', () => shutdown.abort());

async function store(operation, body) {
  const response = await fetch(new URL('/api/rikka/internal/remote/' + operation, config.pocketbaseUrl), {
    method: 'POST', redirect: 'error', signal: AbortSignal.timeout(5000),
    headers: { 'Content-Type': 'application/json', 'X-Rikka-Service': secret }, body: JSON.stringify(body),
  });
  if (!response.ok) throw new RuntimeError('persistence_' + response.status);
  return response.json();
}
function redact(value, env) {
  let encoded = JSON.stringify(value);
  if (!encoded) return null;
  for (const [key, text] of Object.entries(env)) {
    if (/(KEY|TOKEN|SECRET|PASSWORD|CREDENTIAL)/i.test(key) && text.length > 7) {
      encoded = encoded.replaceAll(JSON.stringify(text).slice(1, -1), '[redacted]');
    }
  }
  return encoded.length <= 20000 ? JSON.parse(encoded) : { truncated: true };
}

async function execute(claim, execution) {
  const { task, session, workspaceKey, nativeId } = claim;
  const auth = { id: task.id, execution };
  const canceled = new AbortController();
  const timeout = AbortSignal.timeout((config.turnTimeoutSeconds || 1800) * 1000);
  const signal = AbortSignal.any([shutdown.signal, canceled.signal, timeout]);
  const profile = Object.hasOwn(config.agents, session.agent) && config.agents[session.agent];
  const env = profile ? executionEnvironment(config, profile) : {};
  let cursor = task.cursor;
  let heartbeat;
  let writes = Promise.resolve();
  let error;
  let pending = [];
  let pendingChars = 0;
  let eventCount = 0;
  let queuedWrites = 0;
  const serialized = work => {
    ++queuedWrites;
    const result = writes.then(work).finally(() => --queuedWrites);
    writes = result.catch(problem => {
      error ||= problem;
      canceled.abort();
    });
    return result;
  };
  const flush = () => {
    if (!pending.length) return writes;
    const items = pending;
    pending = [];
    pendingChars = 0;
    return serialized(async () => {
      if (error) throw error;
      const result = await store('events', { ...auth, after: cursor, items }).catch(problem => {
        if (problem instanceof RuntimeError && problem.code !== 'persistence_500') throw problem;
        return store('events', { ...auth, after: cursor, items });
      });
      cursor = result.cursor;
    });
  };
  const update = value => {
    if (signal.aborted) return;
    const data = redact(value, env);
    const last = pending.at(-1)?.data;
    const isChunk = ['agent_message_chunk', 'agent_thought_chunk', 'user_message_chunk'].includes(value.sessionUpdate);
    if (isChunk && last?.sessionUpdate === value.sessionUpdate && last.content?.type === 'text' &&
      data?.content?.type === 'text' && last.content.text.length + data.content.text.length <= 12000) {
      last.content.text += data.content.text;
      pendingChars += data.content.text.length;
    } else {
      if (eventCount >= 5000 || queuedWrites >= 32) {
        error = new RuntimeError('event_stream_limit');
        canceled.abort();
        return;
      }
      ++eventCount;
      pending.push({ type: 'update', data });
      pendingChars += JSON.stringify(data).length;
    }
    if (pending.length >= 25 || pendingChars >= 50000) void flush().catch(() => {});
  };
  const flushTimer = setInterval(() => { void flush().catch(() => {}); }, 300);
  const check = async () => {
    const value = await store('heartbeat', auth);
    if (value.state === 'canceling') canceled.abort();
  };
  const permission = async ({ toolCall, options }) => {
    const allowed = options.filter(option => ['allow_once', 'reject_once'].includes(option.kind) &&
      option.optionId.length <= 256).slice(0, 12);
    if (!allowed.length) return null;
    await flush();
    const id = randomUUID();
    await serialized(async () => {
      const result = await store('approval', { ...auth, approval: {
        id, toolCall: redact(toolCall, env), options: allowed,
      } });
      cursor = result.cursor;
    });
    const deadline = Date.now() + 120000;
    try {
      while (!signal.aborted && Date.now() < deadline) {
        // The gateway may open a new approval while a heartbeat is in flight.
        const value = await store('heartbeat', auth);
        if (value.state === 'canceling') canceled.abort();
        const decision = value.decision;
        if (!signal.aborted && decision) return allowed.some(option => option.optionId === decision) ? decision : null;
        await sleep(750, undefined, { signal }).catch(() => {});
      }
      return null;
    } finally {
      await store('clearApproval', { ...auth, approvalId: id });
    }
  };
  // Approval operations may arrive in parallel over ACP. PocketBase stores one active decision per turn.
  let approvals = Promise.resolve();
  const onPermission = params => {
    const result = approvals.then(() => signal.aborted ? null : permission(params));
    approvals = result.catch(problem => {
      error ||= problem;
      canceled.abort();
    });
    return result;
  };
  let state = 'failed';
  let result;
  let directory;
  try {
    await check();
    heartbeat = (async () => {
      while (!signal.aborted) {
        await sleep(3000, undefined, { signal }).catch(() => {});
        if (!signal.aborted) await check();
      }
    })().catch(problem => {
      error ||= problem;
      canceled.abort();
    });
    if (!profile || !isAbsolute(profile.command) || !Array.isArray(profile.args)) throw new RuntimeError('agent_disabled');
    signal.throwIfAborted();
    const cwd = await worktree(config, workspaceKey, session.id, env, Boolean(nativeId));
    signal.throwIfAborted();
    const files = await prepareFiles(config, task, cwd, signal);
    directory = files.directory;
    const response = await runAcp({
      profile: { ...profile, mcpServers: [...new Map(
        [...(config.mcpServers || []), ...(profile.mcpServers || [])].map(server => [server.name, server]),
      ).values()] },
      env, cwd, nativeId, signal, prompt: files.prompt, config: task.config,
      onSession: (id, options) => {
        const safeOptions = redact(options, env);
        return store('session', { ...auth, nativeId: id, options: Array.isArray(safeOptions) ? safeOptions : [] });
      },
      onUpdate: update, onPermission,
    });
    if (response.stopReason === 'cancelled') state = 'canceled';
    else if (response.stopReason === 'refusal') state = 'failed';
    else {
      if (task.files.outputs.length) {
        await flush();
        await publishOutputs(config, task, directory, signal, data => serialized(async () => {
          const persisted = await store('events', { ...auth, after: cursor, items: [{ type: 'artifact', data }] });
          cursor = persisted.cursor;
        }));
      }
      state = 'succeeded';
    }
    result = { type: 'stop', data: { reason: response.stopReason } };
  } catch (problem) {
    if (shutdown.signal.aborted || error) state = 'interrupted';
    else if (canceled.signal.aborted) state = 'canceled';
    result = { type: 'error', data: { code: shutdown.signal.aborted ? 'gateway_stopped' :
      timeout.aborted ? 'turn_timeout' : state === 'canceled' ? 'canceled' :
      (problem instanceof RuntimeError ? problem.code : 'execution_failed') } };
  } finally {
    clearInterval(flushTimer);
    if (directory) await rm(directory, { recursive: true, force: true }).catch(() => {});
    canceled.abort();
    await heartbeat;
    await approvals;
    await flush().catch(() => {});
    if (error) {
      state = 'interrupted';
      result = { type: 'error', data: { code: error.code === 'event_stream_limit' ? error.code : 'persistence_interrupted' } };
    }
    const body = { ...auth, state, result };
    await store('finish', body).catch(() => store('finish', body)).catch(() => {
      console.warn('Task finalization deferred to lease recovery', task.id);
    });
  }
}

const running = new Set();
while (!shutdown.signal.aborted) {
  try {
    if (running.size < config.concurrency) {
      const execution = randomUUID();
      const claim = await store('claim', { owner: config.owner, execution });
      if (claim.task) {
        const work = execute(claim, execution).finally(() => running.delete(work));
        running.add(work);
        continue;
      }
    }
  } catch (error) {
    console.warn('Gateway claim failed:', error instanceof RuntimeError ? error.code : 'connection_error');
  }
  await sleep(1000, undefined, { signal: shutdown.signal }).catch(() => {});
}
await Promise.allSettled(running);
