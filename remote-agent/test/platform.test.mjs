import assert from 'node:assert/strict';
import { execFile, spawn } from 'node:child_process';
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import net from 'node:net';
import { homedir, userInfo } from 'node:os';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';
import test from 'node:test';
import { setTimeout as sleep } from 'node:timers/promises';
import { promisify } from 'node:util';

const here = fileURLToPath(new URL('../', import.meta.url));
const exec = promisify(execFile);
const pocketbase = process.env.RIKKA_TEST_POCKETBASE;
const wrangler = process.env.RIKKA_TEST_WRANGLER;
const id = () => randomUUID();

async function port() {
  const server = net.createServer().listen(0, '127.0.0.1');
  await once(server, 'listening');
  const { port } = server.address();
  await new Promise(resolve => server.close(resolve));
  return port;
}
async function wait(fn, milliseconds = 20000) {
  const end = Date.now() + milliseconds;
  let error;
  while (Date.now() < end) {
    try { const result = await fn(); if (result) return result; } catch (problem) { error = problem; }
    await sleep(250);
  }
  throw new Error('Timed out waiting for test condition', { cause: error });
}
async function call(base, path, { token, body, method = body === undefined ? 'GET' : 'POST', headers = {} } = {}) {
  const response = await fetch(base + path, {
    method, signal: AbortSignal.timeout(10000),
    headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}),
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const data = await response.json();
  return { ...data, status: response.status };
}
async function checked(base, path, options) {
  const result = await call(base, path, options);
  assert.ok(result.status < 300, JSON.stringify(result));
  return result;
}
test('Worker → PocketBase → Linux Gateway → ACP', {
  skip: !pocketbase || !wrangler ? 'Set RIKKA_TEST_POCKETBASE and RIKKA_TEST_WRANGLER' : false,
  timeout: 600000,
  concurrency: 5,
}, async t => {
  const root = await mkdtemp(join(here, '.local-test-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const processes = new Set();
  function start(name, args, { env = process.env } = {}) {
    const child = spawn(name, args, { cwd: here, env, stdio: ['ignore', 'pipe', 'pipe'], detached: true });
    processes.add(child);
    child.on('exit', () => processes.delete(child));
    let logs = '';
    for (const stream of [child.stdout, child.stderr]) stream.on('data', data => { logs = (logs + data).slice(-10000); });
    child.logs = () => logs;
    return child;
  }
  function stop(child) {
    if (child.exitCode !== null || child.signalCode) return Promise.resolve();
    process.kill(-child.pid, 'SIGTERM');
    return once(child, 'exit');
  }
  try {
    const [pbPort, workerPort] = await Promise.all([port(), port()]);
    const pbUrl = 'http://127.0.0.1:' + pbPort;
    const workerUrl = 'http://127.0.0.1:' + workerPort;
    const repoRoot = join(root, 'repos');
    const repo = join(repoRoot, 'authorized');
    await mkdir(repo, { recursive: true });
    await exec('git', ['init', repo]);
    const marker = 'RIKKA-WORKTREE-' + id().slice(0, 10);
    await writeFile(join(repo, 'AUTHORIZED.txt'), marker + '\n');
    await exec('git', ['-C', repo, 'add', 'AUTHORIZED.txt']);
    await exec('git', ['-C', repo, '-c', 'user.name=Smoke', '-c', 'user.email=smoke@example.test',
      'commit', '-m', 'Authorized read-only fixture']);
    const migrations = join(root, 'migrations');
    await cp(join(here, 'pocketbase/pb_migrations'), migrations, { recursive: true });
    const metadataMigration = '1795570100_remote_session_metadata.js';
    await rm(join(migrations, metadataMigration));
    await writeFile(join(migrations, '1795569900_fixture.js'), `
      migrate(app => app.save(new Collection({ name: 'aw_clients', type: 'auth' })), app => {});
    `);
    const args = ['--dir=' + join(root, 'pb'), '--migrationsDir=' + migrations,
      '--hooksDir=' + join(here, 'pocketbase/pb_hooks')];
    const password = id() + 'Aa!';
    const email = 'admin@example.test';
    const dispatchSecret = id();
    const gatewaySecret = id();
    const artifactSecret = id();
    await exec(pocketbase, [...args, 'superuser', 'upsert', email, password]);
    const pbEnv = { ...process.env, RIKKA_DISPATCH_SECRET: dispatchSecret, RIKKA_GATEWAY_SECRET: gatewaySecret };
    const startPB = () => start(pocketbase, [...args, 'serve', '--http=127.0.0.1:' + pbPort], { env: pbEnv });
    let pb = startPB();
    await wait(() => fetch(pbUrl + '/api/health').then(r => r.ok)).catch(error => { throw new Error(pb.logs(), { cause: error }); });
    const admin = (await checked(pbUrl, '/api/collections/_superusers/auth-with-password',
      { body: { identity: email, password } })).token;
    async function user(name) {
      const identity = name + '@example.test';
      const record = await checked(pbUrl, '/api/collections/aw_clients/records',
        { token: admin, body: { email: identity, password, passwordConfirm: password } });
      const login = await checked(pbUrl, '/api/collections/aw_clients/auth-with-password',
        { body: { identity, password } });
      return { owner: record.id, token: login.token };
    }
    const { owner, token } = await user('owner');
    const other = await user('other');
    const real = process.env.RIKKA_TEST_REAL_CONFIG
      ? JSON.parse(await readFile(process.env.RIKKA_TEST_REAL_CONFIG, 'utf8')) : {};
    const project = await checked(pbUrl, '/api/collections/aw_remote_projects/records', {
      token: admin, body: { owner, name: 'Authorized test repository', workspaceKey: 'authorized',
        agents: ['fixture', ...Object.keys(real)], enabled: true },
    });
    const creation = { projectId: project.id, agent: 'fixture', title: 'Original session', requestKey: id() };
    const s = await checked(pbUrl, '/api/collections/aw_remote_sessions/records', {
      token: admin,
      body: { owner, project: project.id, agent: creation.agent, title: creation.title, requestKey: creation.requestKey },
    });
    await stop(pb);
    await cp(join(here, 'pocketbase/pb_migrations', metadataMigration), join(migrations, metadataMigration));
    await exec(pocketbase, [...args, 'migrate', 'up']);
    pbEnv.RIKKA_GATEWAY_OWNER = owner;
    pb = startPB();
    await wait(() => fetch(pbUrl + '/api/health').then(r => r.ok));
    assert.equal((await checked(pbUrl, '/api/rikka/remote/v1/projects', { token })).items[0].id, project.id);
    const configPath = join(root, 'gateway.json');
    await writeFile(configPath, JSON.stringify({
      owner, user: userInfo().username, home: process.env.RIKKA_TEST_AGENT_HOME || homedir(), path: process.env.PATH,
      repositoryRoot: repoRoot, worktreeRoot: join(root, 'worktrees'), pocketbaseUrl: pbUrl, artifactUrl: workerUrl,
      concurrency: 4, turnTimeoutSeconds: 120,
      agents: { fixture: { command: process.execPath, args: [join(here, 'test/fixture-agent.mjs')] }, ...real },
      projects: { authorized: { repository: repo } },
    }));
    const startGateway = () => start(process.execPath, [join(here, 'gateway/index.mjs'), configPath],
      { env: { ...process.env, RIKKA_GATEWAY_SECRET: gatewaySecret, RIKKA_ARTIFACT_SECRET: artifactSecret } });
    let gateway = startGateway();
    const worker = start(wrangler, ['dev', '--local', '--test-scheduled', '--port', String(workerPort),
      '--ip', '127.0.0.1', '--persist-to', join(root, 'workerd'),
      '--var', 'POCKETBASE_URL:' + pbUrl, '--var', 'DISPATCH_SECRET:' + dispatchSecret,
      '--var', 'ARTIFACT_GATEWAY_SECRET:' + artifactSecret, '--var', 'ARTIFACT_GATEWAY_OWNER:' + owner,
      '--var', 'QUEUE_DELAY_THRESHOLD_SECONDS:5', '--var', 'CLIENT_ORIGINS:https://client.example.test'],
    { env: { ...process.env, WRANGLER_SEND_METRICS: 'false' } });
    const request = (path, body, options) => checked(workerUrl, '/v1' + path, { token, body, ...options });
    await wait(() => request('/projects')).catch(error => { throw new Error(worker.logs() + '\n' + pb.logs(), { cause: error }); });
    assert.equal((await request('/projects')).items[0].id, project.id);
    const preflight = await fetch(workerUrl + '/v1/sessions', { method: 'OPTIONS', headers: {
      Origin: 'https://client.example.test', 'Access-Control-Request-Method': 'POST',
    } });
    assert.equal(preflight.status, 204);
    assert.equal(preflight.headers.get('Access-Control-Allow-Origin'), 'https://client.example.test');
    assert.deepEqual((await request('/projects', undefined, { token: other.token })).items, []);
    assert.equal((await call(workerUrl, '/v1/sessions', { token: other.token,
      body: { projectId: project.id, agent: 'fixture', title: 'unauthorized', requestKey: id() },
    })).status, 404);
    assert.equal((await call(workerUrl, '/v1/projects', { token: 'invalid' })).status, 401);
    const session = async agent => request('/sessions', { projectId: project.id, agent, title: agent, requestKey: id() });
    const upgraded = (await request('/sessions')).items.find(item => item.id === s.id);
    assert.equal(upgraded.title, creation.title);
    assert.equal(upgraded.pinned, false);
    assert.equal(upgraded.created, s.created);
    assert.equal((await call(workerUrl, '/v1/sessions/' + s.id, { token: other.token })).status, 404);

    const submit = (s, prompt, extra = {}) => request('/sessions/' + s.id + '/tasks', { prompt, requestKey: id(), ...extra });
    const firstKey = id();
    const duplicated = () => submit(s, 'approve', { requestKey: firstKey, config: { thought_level: 'high' } });
    const [first, duplicate] = await Promise.all([duplicated(), duplicated()]);
    assert.equal(duplicate.id, first.id);
    assert.equal((await call(workerUrl, '/v1/sessions/' + s.id + '/delete', { token, body: {} })).status, 409);
    assert.equal((await call(workerUrl, '/v1/sessions/' + s.id + '/tasks',
      { token, body: { requestKey: firstKey, prompt: 'different' } })).status, 409);
    const task = id => request('/tasks/' + id);
    const events = async (id, after = 0) => {
      const all = [];
      let page;
      do {
        page = await request('/tasks/' + id + '/events?after=' + after);
        all.push(...page.items);
        after = page.cursor;
      } while (!page.done && page.items.length === 50);
      return { ...page, items: all };
    };
    const finish = async (id, timeout) => wait(async () => {
      const value = await events(id);
      return value.done && value;
    }, timeout).catch(error => { throw new Error(gateway.logs() + '\n' + worker.logs() + '\n' + pb.logs(), { cause: error }); });
    const approval = await wait(async () => (await task(first.id)).pendingApproval).catch(async error => {
      throw new Error(JSON.stringify(await events(first.id)) + gateway.logs() + '\n' + pb.logs(), { cause: error });
    });
    await request('/tasks/' + first.id + '/approvals/' + approval.id, { optionId: 'yes' });
    const complete = await finish(first.id);
    assert.equal(complete.state, 'succeeded');
    assert.match(JSON.stringify(complete.items), new RegExp(marker + '; git version .*effort=high'));
    const resumed = await fetch(workerUrl + '/v1/tasks/' + first.id + '/events', {
      headers: { Authorization: 'Bearer ' + token, Accept: 'text/event-stream',
        'Last-Event-ID': String(complete.items[0].sequence) },
    });
    assert.match(resumed.headers.get('Content-Type'), /text\/event-stream/);
    assert.doesNotMatch(await resumed.text(), /event: approval/);

    const memory = id();
    const remembered = await submit(s, 'remember ' + memory);
    assert.equal((await finish(remembered.id)).state, 'succeeded');
    const delayed = await submit(s, 'recall', { runAt: Date.now() + 3500 });
    assert.equal((await task(delayed.id)).state, 'scheduled');
    await fetch(workerUrl + '/__scheduled');
    assert.equal((await task(delayed.id)).state, 'scheduled');
    const continued = await finish(delayed.id);
    assert.equal(continued.state, 'succeeded');
    assert.match(JSON.stringify(continued.items), new RegExp('remembered=' + memory + '.*effort=high'));
    assert.doesNotMatch(JSON.stringify(continued.items), /REPLAYED HISTORY/);
    assert.equal((await request('/sessions')).items.find(item => item.id === s.id).latestTask.id, delayed.id);
    const workflow = await submit(s, 'workflow turn', { runAt: Date.now() + 8000 });
    assert.equal((await task(workflow.id)).state, 'scheduled');
    assert.equal((await finish(workflow.id, 30000)).state, 'succeeded');
    const missed = await checked(pbUrl, '/api/rikka/remote/v1/sessions/' + s.id + '/tasks',
      { token, body: { prompt: 'missed dispatch', requestKey: id() } });
    assert.equal((await task(missed.id)).state, 'scheduled');
    const cron = await fetch(workerUrl + '/__scheduled');
    assert.equal(cron.status, 200);
    assert.equal((await finish(missed.id)).state, 'succeeded');
    const cancel = await submit(s, 'cancel');
    await wait(async () => (await events(cancel.id)).items.some(item => item.data?.content?.text === 'waiting'));
    await request('/tasks/' + cancel.id + '/cancel', {});
    assert.equal((await finish(cancel.id)).state, 'canceled');
    const breakTask = await submit(s, 'restart');
    await wait(async () => (await events(breakTask.id)).items.some(item => item.data?.content?.text === 'waiting'));
    await stop(gateway);
    assert.equal((await finish(breakTask.id)).state, 'interrupted');
    const orphan = await submit(s, 'orphaned process');
    await wait(async () => (await task(orphan.id)).state === 'queued');
    const lostClaim = await checked(pbUrl, '/api/rikka/internal/remote/claim', {
      headers: { 'X-Rikka-Service': gatewaySecret }, body: { owner, execution: id() },
    });
    assert.equal(lostClaim.task.id, orphan.id);
    await checked(pbUrl, '/api/collections/aw_remote_tasks/records/' + orphan.id, {
      token: admin, method: 'PATCH', body: { leaseUntil: 1 },
    });
    gateway = startGateway();
    const recovered = await finish(orphan.id);
    assert.equal(recovered.state, 'interrupted');
    assert.equal(recovered.items.at(-1).data.code, 'execution_interrupted');
    const retried = await request('/tasks/' + breakTask.id + '/retry', { requestKey: id(), runAt: Date.now() + 60000 });
    assert.equal(retried.retryOf, breakTask.id);
    await request('/tasks/' + retried.id + '/cancel', {});

    const artifact = workerUrl + '/v1/artifacts/' + id();
    const upload = await fetch(artifact, { method: 'PUT', headers: { Authorization: 'Bearer ' + token }, body: marker });
    assert.equal(upload.status, 201, await upload.text());
    const retrieved = await fetch(artifact, { headers: { Authorization: 'Bearer ' + token } });
    assert.equal(await retrieved.text(), marker);
    assert.equal((await fetch(artifact, { headers: { Authorization: 'Bearer ' + other.token } })).status, 404);
    const withFiles = await submit(s, 'artifact', { files: {
      inputs: [{ id: artifact.split('/').at(-1), name: 'input.txt' }], outputs: ['output.txt'],
    } });
    const fileResult = await finish(withFiles.id);
    assert.equal(fileResult.state, 'succeeded', JSON.stringify(fileResult.items));
    const generated = fileResult.items.find(item => item.type === 'artifact').data;
    assert.equal(await (await fetch(workerUrl + '/v1/artifacts/' + generated.id, {
      headers: { Authorization: 'Bearer ' + token },
    })).text(), marker);

    const metadata = await request('/sessions/' + s.id + '/metadata', { title: 'Renamed session', pinned: true });
    assert.equal(metadata.title, 'Renamed session');
    assert.equal(metadata.pinned, true);
    const index = (await request('/sessions')).items.find(item => item.id === s.id);
    assert.equal(index.title, metadata.title);
    assert.equal(index.pinned, true);
    const replay = await request('/sessions', creation);
    assert.equal(replay.id, s.id);
    assert.equal(replay.title, metadata.title);
    assert.equal((await call(workerUrl, '/v1/sessions',
      { token, body: { ...creation, title: metadata.title } })).status, 409);
    assert.equal((await call(workerUrl, '/v1/sessions/' + s.id + '/delete',
      { token: other.token, body: {} })).status, 404);
    await request('/sessions/' + s.id + '/delete', {});
    assert.equal((await request('/sessions')).items.some(item => item.id === s.id), false);
    assert.equal((await call(workerUrl, '/v1/sessions/' + s.id, { token })).status, 404);
    assert.equal((await call(workerUrl, '/v1/sessions/' + s.id + '/tasks',
      { token, body: { prompt: 'after deletion', requestKey: id() } })).status, 404);

    await Promise.all(Object.keys(real).map(agent => t.test(agent, async () => {
      const agentSession = await session(agent);
      const smoke = await submit(agentSession,
        'This is an authorized read-only integration test. Use your available tools to read the local file AUTHORIZED.txt and run git --version. ' +
        'Reply with the exact marker and Git version. Do not edit files, access the network with tools, or search outside the current working directory.');
      const observe = async () => wait(async () => {
        const status = await task(smoke.id);
        if (status.pendingApproval) {
          const all = await events(smoke.id);
          const pending = all.items.find(item => item.type === 'approval' && item.data.id === status.pendingApproval.id);
          const title = pending?.data.toolCall?.title || '';
          const safe = /^(read (file )?[^;|&]*AUTHORIZED\.txt|(?:Shell: )?git --version)$/i.test(title);
          const option = status.pendingApproval.options.find(item => item.kind === (safe ? 'allow_once' : 'reject_once'));
          if (option) await request('/tasks/' + smoke.id + '/approvals/' + status.pendingApproval.id, { optionId: option.optionId });
        }
        const value = await events(smoke.id);
        return value.done && value;
      }, 150000);
      const result = await observe();
      const output = result.items.filter(item => item.data?.sessionUpdate === 'agent_message_chunk')
        .map(item => item.data?.content?.text || '').join('');
      assert.equal(result.state, 'succeeded', JSON.stringify(result.items.filter(item => ['stop', 'error'].includes(item.type))));
      assert.ok(result.items.some(item => ['tool_call', 'tool_call_update'].includes(item.data?.sessionUpdate)),
        'Agent did not publish ACP tool events');
      assert.ok(output.includes(marker) && /git version|[0-9]+\.[0-9]+\.[0-9]+/i.test(output),
        'Agent did not report the authorized marker and Git version');
      const next = await submit(agentSession, 'Repeat the marker from your previous answer without using any tools.');
      const continued = await finish(next.id, 130000);
      assert.equal(continued.state, 'succeeded', JSON.stringify(continued.items.filter(item => item.type === 'error')));
      const nextOutput = continued.items.filter(item => item.data?.sessionUpdate === 'agent_message_chunk')
        .map(item => item.data?.content?.text || '').join('');
      assert.ok(nextOutput.includes(marker), 'ACP session did not retain the previous turn');
    })));
  } finally {
    await Promise.allSettled([...processes].map(stop));
  }
});
