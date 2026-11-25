import { WorkflowEntrypoint } from 'cloudflare:workers';

const queueDelayLimit = 24 * 60 * 60;
const maxArtifact = 20 * 1024 * 1024;
const encode = new TextEncoder();

async function pb(env, path, init = {}) {
  const response = await fetch(new URL(path, env.POCKETBASE_URL), { ...init, redirect: 'manual' });
  if (response.status >= 300 && response.status < 400) throw new Error('Unexpected PocketBase redirect');
  return response;
}
function dispatch(env, operation, body) {
  return pb(env, '/api/rikka/internal/remote/' + operation, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-Rikka-Service': env.DISPATCH_SECRET },
    body: JSON.stringify(body),
  }).then(response => {
    if (!response.ok) throw new Error('Dispatch failed: ' + response.status);
  });
}
function apiError(status, message) {
  return Response.json({ message }, { status, headers: { 'Cache-Control': 'no-store' } });
}
function responseHeaders(type) {
  return { 'Content-Type': type, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' };
}
function forward(response) {
  return new Response(response.body, { status: response.status, headers: responseHeaders('application/json') });
}
function schedule(env, task) {
  const delaySeconds = Math.max(0, Math.ceil((task.runAt - Date.now()) / 1000));
  const threshold = Number(env.QUEUE_DELAY_THRESHOLD_SECONDS) || queueDelayLimit;
  if (delaySeconds <= Math.min(threshold, queueDelayLimit)) return env.TASK_QUEUE.send({ id: task.id }, { delaySeconds });
  return env.DELAYED_TASK.create({ id: task.id, params: { id: task.id, runAt: task.runAt } });
}

async function streamEvents(request, env, path, headers, prefix) {
  let after = request.headers.get('Last-Event-ID') || new URL(request.url).searchParams.get('after') || '0';
  if (!/^\d{1,16}$/.test(after)) return apiError(400, 'Invalid cursor');
  const load = () => pb(env, prefix + path + '?after=' + after, { headers, signal: request.signal });
  const first = await load();
  if (!first.ok) return forward(first);
  let batch = await first.json();
  const deadline = Date.now() + 25000;
  const stream = new ReadableStream({
    async pull(controller) {
      if (request.signal.aborted || Date.now() >= deadline) return controller.close();
      try {
        if (!batch) {
          await new Promise(resolve => setTimeout(resolve, 900));
          const response = await load();
          if (!response.ok) return controller.close();
          batch = await response.json();
        }
        for (const item of batch.items) {
          controller.enqueue(encode.encode('id: ' + item.sequence + '\nevent: ' + item.type +
            '\ndata: ' + JSON.stringify(item.data) + '\n\n'));
        }
        controller.enqueue(encode.encode('event: state\ndata: ' +
          JSON.stringify({ state: batch.state, done: batch.done }) + '\n\n'));
        after = batch.cursor;
        if (batch.done) return controller.close();
        batch = null;
      } catch {
        controller.close();
      }
    },
  });
  return new Response(stream, { headers: responseHeaders('text/event-stream; charset=utf-8') });
}

async function artifact(request, env, headers, id) {
  const identity = await pb(env, '/api/rikka/remote/v1/identity', { headers });
  if (!identity.ok) return forward(identity);
  const { id: owner, enrolled } = await identity.json();
  if (!enrolled) return apiError(404, 'Artifact not found');
  return artifactStorage(request, env, owner, id);
}

async function artifactStorage(request, env, owner, id) {
  const key = 'remote-agent/' + owner + '/' + id;
  if (request.method === 'PUT') {
    const size = Number(request.headers.get('Content-Length'));
    if (!request.body || !Number.isSafeInteger(size) || size <= 0 || size > maxArtifact) {
      return apiError(413, 'Content-Length must be between 1 byte and 20 MiB');
    }
    // Measure bytes before publishing. R2 never sees an oversized or truncated object.
    const reader = request.body.getReader();
    const bytes = new Uint8Array(size);
    let offset = 0;
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        if (offset + value.length > size) return apiError(413, 'Artifact exceeds Content-Length');
        bytes.set(value, offset);
        offset += value.length;
      }
    } finally {
      await reader.cancel().catch(() => {});
    }
    if (offset !== size) return apiError(400, 'Incomplete artifact');
    const object = await env.ARTIFACTS.put(key, bytes, { onlyIf: { etagDoesNotMatch: '*' } });
    if (!object) return apiError(409, 'Artifact already exists');
    return Response.json({ id, size: object.size, etag: object.etag }, { status: 201 });
  }
  if (request.method === 'GET') {
    const object = await env.ARTIFACTS.get(key);
    if (!object) return apiError(404, 'Artifact not found');
    return new Response(object.body, { headers: {
      ...responseHeaders('application/octet-stream'), 'Content-Disposition': 'attachment',
      'Content-Length': String(object.size),
    } });
  }
  return apiError(405, 'Method not allowed');
}

async function equalSecrets(expected, supplied) {
  if (!supplied || supplied.length > 256) return false;
  const [a, b] = await Promise.all([expected, supplied].map(value =>
    crypto.subtle.digest('SHA-256', encode.encode(value))));
  return crypto.subtle.timingSafeEqual(a, b);
}

export class DelayedTask extends WorkflowEntrypoint {
  async run(event, step) {
    const { id, runAt } = event.payload;
    await step.sleepUntil('wait until scheduled time', new Date(runAt));
    await step.do('enqueue task', () => this.env.TASK_QUEUE.send({ id }));
  }
}

async function handleRequest(request, env, ctx) {
  if (!env.DISPATCH_SECRET) return apiError(503, 'Remote Agent deployment is awaiting activation');
  const url = new URL(request.url);
  const agentFile = /^\/internal\/artifacts\/([a-f0-9-]{36})$/.exec(url.pathname);
  if (agentFile) {
    const supplied = request.headers.get('X-Rikka-Service') || '';
    if (!env.ARTIFACT_GATEWAY_SECRET || !/^[a-z0-9]{15}$/.test(env.ARTIFACT_GATEWAY_OWNER || '') ||
      !await equalSecrets(env.ARTIFACT_GATEWAY_SECRET, supplied)) return apiError(403, 'Forbidden');
    return artifactStorage(request, env, env.ARTIFACT_GATEWAY_OWNER, agentFile[1]);
  }
  const auth = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '');
  if (!auth) return apiError(401, 'Sign in with PocketBase');
  const file = /^\/v1\/artifacts\/([a-f0-9-]{36})$/.exec(url.pathname);
  const headers = { Authorization: auth };
  try {
    if (file) return await artifact(request, env, headers, file[1]);
    const path = url.pathname;
    if (!/^\/v1\/(projects|sessions|tasks)(\/[a-zA-Z0-9_-]+)*$/.test(path)) {
      return apiError(404, 'Unknown endpoint');
    }
    const prefix = '/api/rikka/remote';
    if (request.method === 'GET' && path.endsWith('/events') &&
      request.headers.get('Accept')?.includes('text/event-stream')) {
      return await streamEvents(request, env, path, headers, prefix);
    }
    if (request.method !== 'GET' && request.method !== 'POST') return apiError(405, 'Method not allowed');
    let body;
    if (request.method === 'POST') {
      const reader = request.body?.getReader();
      const decoder = new TextDecoder();
      let size = 0;
      body = '';
      if (reader) {
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          size += value.length;
          if (size > 65536) {
            await reader.cancel();
            return apiError(413, 'Request exceeds 64 KiB');
          }
          body += decoder.decode(value, { stream: true });
        }
        body += decoder.decode();
      }
      body ||= '{}';
      headers['Content-Type'] = 'application/json';
      if (request.headers.has('Idempotency-Key')) {
        let payload;
        try { payload = JSON.parse(body); } catch { return apiError(400, 'Invalid JSON body'); }
        if (!payload || typeof payload !== 'object' || Array.isArray(payload)) return apiError(400, 'Invalid JSON body');
        body = JSON.stringify({ ...payload, requestKey: request.headers.get('Idempotency-Key') });
      }
    }
    const response = await pb(env, prefix + path + url.search, { method: request.method, headers, body });
    if (response.ok && request.method === 'POST' &&
      (/^\/v1\/sessions\/[a-z0-9]{15}\/tasks$/.test(path) || path.endsWith('/retry'))) {
      const task = await response.clone().json();
      if (task.state === 'scheduled') ctx.waitUntil(schedule(env, task).catch(() => {
        console.warn('Task dispatch signal failed', task.id);
      }));
    }
    return forward(response);
  } catch (error) {
    console.warn('Remote Agent upstream request failed:', error.message);
    return apiError(502, 'Remote Agent service unavailable');
  }
}

export default {
  async fetch(request, env, ctx) {
    const origin = request.headers.get('Origin');
    const allowed = origin && (env.CLIENT_ORIGINS || '').split(',').map(value => value.trim()).includes(origin)
      && new URL(request.url).pathname.startsWith('/v1/');
    const cors = allowed ? {
      'Access-Control-Allow-Origin': origin,
      'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type, Idempotency-Key, Last-Event-ID',
      'Access-Control-Max-Age': '600',
      Vary: 'Origin',
    } : {};
    if (request.method === 'OPTIONS') {
      return allowed ? new Response(null, { status: 204, headers: cors }) : apiError(403, 'Origin not allowed');
    }
    const response = await handleRequest(request, env, ctx);
    if (!allowed) return response;
    const headers = new Headers(response.headers);
    for (const [name, value] of Object.entries(cors)) headers.set(name, value);
    return new Response(response.body, { headers, status: response.status });
  },
  async queue(batch, env) {
    for (const message of batch.messages) {
      try {
        await dispatch(env, 'dispatch', { id: message.body.id });
        message.ack();
      } catch {
        message.retry();
      }
    }
  },
  async scheduled(_controller, env) {
    if (env.DISPATCH_SECRET) await dispatch(env, 'reconcile', {});
  },
};
