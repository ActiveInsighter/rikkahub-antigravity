import { spawn, execFile } from 'node:child_process';
import { mkdir, realpath, stat } from 'node:fs/promises';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { Writable, Readable } from 'node:stream';
import { setTimeout as sleep } from 'node:timers/promises';
import { promisify } from 'node:util';
import { client, ndJsonStream, PROTOCOL_VERSION } from '@agentclientprotocol/sdk';

const exec = promisify(execFile);

export class RuntimeError extends Error {
  constructor(code) { super(code); this.code = code; }
}

export function executionEnvironment(config, profile) {
  const env = {
    HOME: config.home, USER: config.user, LOGNAME: config.user, PATH: config.path,
    LANG: 'C.UTF-8', GIT_TERMINAL_PROMPT: '0', TMPDIR: config.tmpDir || '/tmp',
  };
  for (const key of [...(config.passEnvironment || []), ...(profile.passEnvironment || [])]) {
    // Service authentication belongs to the Gateway process.
    if (!/^RIKKA_|^NOTIFY_SOCKET$|^CREDENTIALS_DIRECTORY$/.test(key) && process.env[key] !== undefined) {
      env[key] = process.env[key];
    }
  }
  return env;
}

async function git(cwd, env, args) {
  const { stdout } = await exec('/usr/bin/git', ['-C', cwd, ...args], {
    env, timeout: 15000, maxBuffer: 65536,
  });
  return stdout.trim();
}
export async function worktree(config, key, sessionId, env, resumed) {
  const project = Object.hasOwn(config.projects, key) && config.projects[key];
  if (!project || !/^[a-z0-9]{15}$/.test(sessionId)) throw new RuntimeError('workspace_not_authorized');
  const repo = await realpath(project.repository);
  const root = await realpath(config.repositoryRoot);
  const distance = relative(root, repo);
  if (!distance || distance === '..' || distance.startsWith('../') || isAbsolute(distance)) {
    throw new RuntimeError('workspace_not_authorized');
  }
  if (await git(repo, env, ['rev-parse', '--show-toplevel']) !== repo) {
    throw new RuntimeError('workspace_invalid');
  }
  await mkdir(config.worktreeRoot, { recursive: true, mode: 0o700 });
  const worktreeRoot = await realpath(config.worktreeRoot);
  const cwd = join(worktreeRoot, sessionId);
  let found;
  try { found = await realpath(cwd); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (found) {
    if (found !== cwd || await git(cwd, env, ['rev-parse', '--show-toplevel']) !== cwd ||
      await realpath(resolve(cwd, await git(cwd, env, ['rev-parse', '--git-common-dir']))) !==
      await realpath(resolve(repo, await git(repo, env, ['rev-parse', '--git-common-dir'])))) {
      throw new RuntimeError('workspace_invalid');
    }
    return cwd;
  }
  if (resumed) throw new RuntimeError('workspace_missing');
  if (await stat(cwd).then(() => true, () => false)) throw new RuntimeError('workspace_invalid');
  const commit = await git(repo, env, ['rev-parse', '--verify', '--end-of-options', (project.base || 'HEAD') + '^{commit}']);
  await git(repo, env, ['-c', 'core.hooksPath=/dev/null', 'worktree', 'add', '--detach', cwd, commit]);
  return cwd;
}

function terminate(child, signal) {
  if (!child.pid) return;
  try { process.kill(-child.pid, signal); }
  catch (error) { if (error.code !== 'ESRCH') throw error; }
}

function optionValues(option) {
  return (option.options || []).flatMap(value => value.options || [value]);
}
function setting(options, category, value) {
  const option = options.find(item => item.category === category || item.id === category);
  if (!option || !optionValues(option).some(item => item.value === value)) {
    throw new RuntimeError('unsupported_' + category);
  }
  return option;
}

export async function runAcp({
  profile, env, cwd, nativeId, prompt, config, signal,
  onSession, onUpdate, onPermission,
}) {
  const child = spawn(profile.command, profile.args, { cwd, env, detached: true, stdio: ['pipe', 'pipe', 'ignore'] });
  const close = new Promise(resolve => {
    child.once('close', resolve);
    child.once('error', resolve);
  });
  let phase = 'initialize';
  let sessionId = nativeId;
  let connection;
  let loading = false;
  let advertisedOptions;
  const onAbort = () => {
    if (sessionId) void connection?.agent.notify('session/cancel', { sessionId }).catch(() => {});
  };
  try {
    const app = client()
      .onNotification('session/update', ({ params }) => {
        if (params.sessionId !== sessionId) return;
        if (params.update.sessionUpdate === 'config_option_update') advertisedOptions = params.update.configOptions;
        if (!loading) onUpdate(params.update);
      })
      .onRequest('session/request_permission', async ({ params }) => {
        if (params.sessionId !== sessionId || signal.aborted) return { outcome: { outcome: 'cancelled' } };
        const optionId = await onPermission(params);
        return { outcome: optionId ? { outcome: 'selected', optionId } : { outcome: 'cancelled' } };
      });
    const stream = ndJsonStream(Writable.toWeb(child.stdin), Readable.toWeb(child.stdout));
    connection = app.connect(stream);
    const request = (method, params, timeout) => connection.agent.request(method, params, {
      cancellationSignal: timeout ? AbortSignal.any([signal, AbortSignal.timeout(timeout)]) : signal,
      stopWaitingOnCancel: true,
    });
    signal.addEventListener('abort', onAbort, { once: true });
    const init = await request('initialize', {
      protocolVersion: PROTOCOL_VERSION, clientCapabilities: {},
      clientInfo: { name: 'rikkahub-gateway', version: '1.0.0' },
    }, 15000);
    if (init.protocolVersion !== PROTOCOL_VERSION) throw new RuntimeError('unsupported_protocol');

    phase = nativeId ? 'load_session' : 'new_session';
    if (nativeId && !init.agentCapabilities?.loadSession) throw new RuntimeError('resume_unsupported');
    const sessionArgs = { cwd, mcpServers: profile.mcpServers || [] };
    // The load replay belongs to earlier tasks. Only updates from this turn are published.
    loading = Boolean(nativeId);
    const session = nativeId
      ? await request('session/load', { ...sessionArgs, sessionId: nativeId }, 30000)
      : await request('session/new', sessionArgs, 30000);
    sessionId = nativeId || session.sessionId;
    loading = false;
    let options = session?.configOptions || advertisedOptions || [];
    await onSession(sessionId, options);
    phase = 'configure';
    for (const [category, value] of Object.entries(config)) {
      const option = setting(options, category, value);
      if (option.currentValue !== value) {
        const result = await request('session/set_config_option', { sessionId, configId: option.id, value }, 15000);
        options = result.configOptions;
        await onSession(sessionId, options);
      }
    }
    phase = 'prompt';
    return await request('session/prompt', { sessionId, prompt: [{ type: 'text', text: prompt }] });
  } catch (error) {
    if (error instanceof RuntimeError) throw error;
    if (error?.code === -32000 && phase !== 'prompt') throw new RuntimeError('agent_authentication_required');
    throw new RuntimeError('agent_' + phase + '_failed');
  } finally {
    signal.removeEventListener('abort', onAbort);
    if (signal.aborted && sessionId) await Promise.race([close, sleep(400)]);
    connection?.close();
    terminate(child, 'SIGTERM');
    await Promise.race([close, sleep(700)]);
    terminate(child, 'SIGKILL');
  }
}
