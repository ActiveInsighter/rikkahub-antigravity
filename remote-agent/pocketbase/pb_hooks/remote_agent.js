/* global Record, BadRequestError, NotFoundError, ApiError */

const projects = 'aw_remote_projects';
const sessions = 'aw_remote_sessions';
const tasks = 'aw_remote_tasks';
const events = 'aw_remote_events';
const active = ['running', 'canceling'];
const terminal = ['succeeded', 'failed', 'canceled', 'interrupted'];

function reject(message) { throw new BadRequestError(message); }
function conflict(message) { throw new ApiError(409, message); }
function transaction(app, fn) {
  let result;
  app.runInTransaction(tx => { result = fn(tx); });
  return result;
}
function find(app, collection, filter, params, sort = '', limit = 1, offset = 0) {
  return app.findRecordsByFilter(collection, filter, sort, limit, offset, params);
}
function owned(app, collection, id, owner) {
  const record = find(app, collection, 'id = {:id} && owner = {:owner}', { id, owner })[0];
  if (!record) throw new NotFoundError();
  return record;
}
function save(app, record, values) {
  for (const [key, value] of Object.entries(values)) record.set(key, value);
  app.save(record);
  return record;
}
function create(app, collection, values) {
  return save(app, new Record(app.findCollectionByNameOrId(collection)), values);
}
function json(record, key, fallback) {
  return JSON.parse(record.getString(key) || 'null') ?? fallback;
}
function projectView(record) {
  return { id: record.id, name: record.getString('name'), agents: json(record, 'agents', []) };
}
function sessionView(record) {
  return {
    id: record.id, projectId: record.getString('project'), agent: record.getString('agent'),
    title: record.getString('title'), options: json(record, 'options', []), updated: record.getString('updated'),
  };
}
function taskView(record) {
  return {
    id: record.id, sessionId: record.getString('session'), prompt: record.getString('prompt'),
    state: record.getString('state'), runAt: record.getInt('runAt'), retryOf: record.getString('retryOf') || null,
    config: json(record, 'config', {}), files: json(record, 'files', { inputs: [], outputs: [] }), cursor: record.getInt('cursor'), created: record.getString('created'),
    pendingApproval: record.getString('approvalId') && !record.getString('decision') ?
      { id: record.getString('approvalId'), options: json(record, 'approvalOptions', []) } : null,
  };
}
function checkedText(value, max, name) {
  if (typeof value !== 'string' || !value.trim() || value.length > max) reject('Invalid ' + name);
  return value;
}
function keys(body) {
  if (!/^[A-Za-z0-9_-]{16,80}$/.test(body.requestKey || '')) reject('Invalid idempotency key');
  return body.requestKey;
}
function taskConfig(config) {
  if (config == null) return {};
  if (typeof config !== 'object' || Array.isArray(config)) reject('Invalid configuration');
  const result = {};
  for (const key of ['model', 'thought_level']) {
    if (config[key] !== undefined) result[key] = checkedText(config[key], 256, key);
  }
  return result;
}
function taskFiles(files) {
  if (files == null) return { inputs: [], outputs: [] };
  if (typeof files !== 'object' || Array.isArray(files)) reject('Invalid files');
  const inputs = files.inputs ?? [];
  const outputs = files.outputs ?? [];
  const safeSegment = /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,79}$/;
  if (!Array.isArray(inputs) || inputs.length > 5 || !Array.isArray(outputs) || outputs.length > 5) {
    reject('At most five input files or output paths are supported');
  }
  const normalizedInputs = inputs.map(input => {
    if (!input || typeof input.id !== 'string' || !/^[a-f0-9-]{36}$/.test(input.id) ||
      typeof input.name !== 'string' || !safeSegment.test(input.name)) reject('Invalid input file');
    return { id: input.id, name: input.name };
  });
  if (new Set(normalizedInputs.map(input => input.name)).size !== inputs.length) reject('Duplicate input name');
  for (const path of outputs) {
    if (typeof path !== 'string' || path.length > 240 ||
      !path.split('/').every(part => safeSegment.test(part))) reject('Invalid output path');
  }
  if (new Set(outputs).size !== outputs.length) reject('Duplicate output path');
  return { inputs: normalizedInputs, outputs };
}
function requireProject(app, id, owner, agent) {
  const project = owned(app, projects, id, owner);
  if (!project.getBool('enabled') || !json(project, 'agents', []).includes(agent)) {
    throw new NotFoundError();
  }
  return project;
}
function newTask(tx, owner, session, body, source) {
  const requestKey = keys(body);
  const prompt = source ? source.getString('prompt') : checkedText(body.prompt, 48000, 'prompt');
  const config = source ? json(source, 'config', {}) : taskConfig(body.config);
  const files = taskFiles(source ? json(source, 'files', null) : body.files);
  const requestedTime = body.runAt ?? 0;
  const retryOf = source?.id || '';
  const existing = find(tx, tasks, 'owner = {:owner} && requestKey = {:requestKey}', { owner, requestKey })[0];
  if (existing) {
    if (existing.getString('session') !== session.id || existing.getString('prompt') !== prompt ||
      JSON.stringify(json(existing, 'config', {})) !== JSON.stringify(config) ||
      JSON.stringify(taskView(existing).files) !== JSON.stringify(files) || existing.getInt('runAt') !== requestedTime || existing.getString('retryOf') !== retryOf) {
      conflict('Idempotency key already used with different arguments');
    }
    return taskView(existing);
  }
  if (!Number.isSafeInteger(requestedTime) || requestedTime < 0 ||
    requestedTime > Date.now() + 365 * 86400000) reject('Invalid scheduled time');
  requireProject(tx, session.getString('project'), owner, session.getString('agent'));
  const pending = find(tx, tasks, 'owner = {:owner} && (state = "scheduled" || state = "queued")', { owner }, '', 100);
  if (pending.length >= 100) throw new ApiError(429, 'Too many pending tasks');
  return taskView(create(tx, tasks, { owner, session: session.id, requestKey, prompt, config, files,
    retryOf, runAt: requestedTime, state: 'scheduled' }));
}

function publicRequest(e) {
  const app = e.app;
  const owner = e.auth.id;
  const method = e.request.method;
  const path = e.request.pathValue('path').split('/');
  const [group, id, action, approvalId] = path;
  const body = method === 'POST' ? (e.requestInfo().body || {}) : {};
  const query = e.request.url.query();
  const page = Number(query.get('page') || 1);
  if (!Number.isSafeInteger(page) || page < 1 || page > 10000) reject('Invalid page');
  const pageOf = (collection, filter, params, sort, view) => {
    const rows = find(app, collection, filter, params, sort, 51, (page - 1) * 50);
    return { items: rows.slice(0, 50).map(view), nextPage: rows.length > 50 ? page + 1 : null };
  };
  if (method === 'GET') {
    if (group === 'identity' && path.length === 1) return {
      id: owner, enrolled: Boolean(find(app, projects, 'owner = {:owner} && enabled = true', { owner }).length),
    };
    if (group === 'projects' && path.length === 1) {
      return pageOf(projects, 'owner = {:owner} && enabled = true', { owner }, '+name,+id', projectView);
    }
    if (group === 'sessions' && path.length === 1) {
      return pageOf(sessions, 'owner = {:owner}', { owner }, '-created,-id', sessionView);
    }
    if (group === 'sessions' && path.length === 2) return sessionView(owned(app, sessions, id, owner));
    if (group === 'sessions' && path.length === 3 && action === 'tasks') {
      owned(app, sessions, id, owner);
      return pageOf(tasks, 'owner = {:owner} && session = {:id}', { owner, id }, '-created,-id', taskView);
    }
    if (group === 'tasks' && path.length === 2) return taskView(owned(app, tasks, id, owner));
    if (group === 'tasks' && path.length === 3 && action === 'events') {
      const task = owned(app, tasks, id, owner);
      const after = Number(query.get('after') || 0);
      if (!Number.isSafeInteger(after) || after < 0) reject('Invalid cursor');
      const rows = find(app, events, 'task = {:id} && sequence > {:after} && sequence <= {:cursor}',
        { id, after, cursor: task.getInt('cursor') }, '+sequence', 50);
      const items = rows.map(row => ({
        sequence: row.getInt('sequence'), type: row.getString('type'), data: json(row, 'data', null),
      }));
      const cursor = items.length ? items[items.length - 1].sequence : after;
      return { items, cursor, state: task.getString('state'),
        done: terminal.includes(task.getString('state')) && cursor >= task.getInt('cursor') };
    }
    throw new NotFoundError();
  }
  return transaction(app, tx => {
    if (group === 'sessions' && path.length === 1) {
      const agent = checkedText(body.agent, 40, 'agent');
      const title = checkedText(body.title, 120, 'title');
      const project = requireProject(tx, body.projectId || '', owner, agent);
      const requestKey = keys(body);
      const existing = find(tx, sessions, 'owner = {:owner} && requestKey = {:requestKey}', { owner, requestKey })[0];
      if (existing) {
        if (existing.getString('project') !== project.id || existing.getString('agent') !== agent ||
          existing.getString('title') !== title) conflict('Idempotency key already used with different arguments');
        return sessionView(existing);
      }
      return sessionView(create(tx, sessions, { owner, project: project.id, agent, title, requestKey }));
    }
    if (group === 'sessions' && path.length === 3 && action === 'tasks') {
      return newTask(tx, owner, owned(tx, sessions, id, owner), body);
    }
    if (group !== 'tasks') throw new NotFoundError();
    const task = owned(tx, tasks, id, owner);
    const state = task.getString('state');
    if (path.length === 3 && action === 'cancel') {
      if (state === 'scheduled' || state === 'queued') save(tx, task, { state: 'canceled' });
      if (state === 'running') save(tx, task, { state: 'canceling' });
      return taskView(task);
    }
    if (path.length === 3 && action === 'retry') {
      if (!['failed', 'canceled', 'interrupted'].includes(state)) conflict('Task cannot be retried');
      return newTask(tx, owner, owned(tx, sessions, task.getString('session'), owner), body, task);
    }
    if (path.length === 4 && action === 'approvals') {
      if (state !== 'running' || task.getString('approvalId') !== approvalId) conflict('Approval is closed');
      const choice = json(task, 'approvalOptions', []).find(option => option.optionId === body.optionId);
      if (!choice) reject('Invalid approval option');
      const decision = task.getString('decision');
      if (decision && decision !== choice.optionId) conflict('Approval already decided');
      if (!decision) save(tx, task, { decision: choice.optionId });
      return taskView(task);
    }
    throw new NotFoundError();
  });
}

function append(tx, task, list) {
  let cursor = task.getInt('cursor');
  for (const item of list) {
    create(tx, events, { task: task.id, sequence: ++cursor, type: item.type, data: item.data });
  }
  return cursor;
}
function clearApproval() { return { approvalId: '', approvalOptions: [], decision: '' }; }

function internalRequest(e, operation, gatewayOwner) {
  const body = e.requestInfo().body;
  const now = Date.now();
  return transaction(e.app, tx => {
    if (operation === 'dispatch' || operation === 'reconcile') {
      const recordFilter = operation === 'dispatch' ? ' && id = {:id}' : '';
      const due = find(tx, tasks, 'state = "scheduled" && runAt <= {:now}' + recordFilter,
        operation === 'dispatch' ? { now, id: body.id || '' } : { now }, '+runAt,+created,+id', 100);
      for (const task of due) save(tx, task, { state: 'queued' });
      return { dispatched: due.length };
    }
    if (!gatewayOwner) throw new NotFoundError();
    if (operation === 'claim') {
      if (body.owner !== gatewayOwner || !/^[a-z0-9]{15}$/.test(body.owner || '') || !/^[a-z0-9-]{36}$/.test(body.execution || '')) {
        reject('Invalid execution identity');
      }
      const scope = { owner: body.owner, now };
      const stale = find(tx, tasks, 'owner = {:owner} && (state = "running" || state = "canceling") && leaseUntil <= {:now}',
        scope, '+leaseUntil', 100);
      for (const task of stale) {
        const cursor = append(tx, task, [{ type: 'error', data: { code: 'execution_interrupted' } }]);
        save(tx, task, { ...clearApproval(), cursor, state: 'interrupted', leaseUntil: 0 });
      }
      const queue = find(tx, tasks, 'owner = {:owner} && state = "queued"', { owner: body.owner }, '+created,+id', 100);
      for (const task of queue) {
        const sessionId = task.getString('session');
        if (find(tx, tasks, 'session = {:sessionId} && (state = "running" || state = "canceling")', { sessionId }).length) continue;
        const session = owned(tx, sessions, sessionId, body.owner);
        const project = owned(tx, projects, session.getString('project'), body.owner);
        if (!project.getBool('enabled') || !json(project, 'agents', []).includes(session.getString('agent'))) {
          const cursor = append(tx, task, [{ type: 'error', data: { code: 'project_access_revoked' } }]);
          save(tx, task, { state: 'failed', cursor });
          continue;
        }
        save(tx, task, { state: 'running', execution: body.execution, leaseUntil: now + 30000 });
        return { task: taskView(task), session: sessionView(session),
          workspaceKey: project.getString('workspaceKey'), nativeId: session.getString('nativeId') };
      }
      return {};
    }
    const task = owned(tx, tasks, body.id || '', gatewayOwner);
    const state = task.getString('state');
    if (operation === 'finish' && terminal.includes(state) && task.getString('execution') === body.execution) {
      return taskView(task);
    }
    if (!active.includes(state) || task.getString('execution') !== body.execution || task.getInt('leaseUntil') <= now) {
      conflict('Execution lease lost');
    }
    if (operation === 'heartbeat') {
      save(tx, task, { leaseUntil: now + 30000 });
      return { state, decision: task.getString('decision') };
    }
    if (operation === 'events') {
      // Validate contiguous batches and allow a response-lost write to be acknowledged again.
      const cursor = task.getInt('cursor');
      const count = Array.isArray(body.items) ? body.items.length : 0;
      if (!count || count > 50 || !Number.isSafeInteger(body.after) || body.after < 0) reject('Invalid events');
      if (cursor === body.after + count) return { cursor, state };
      if (cursor !== body.after) conflict('Event cursor changed');
      return { cursor: save(tx, task, { cursor: append(tx, task, body.items) }).getInt('cursor'), state };
    }
    if (operation === 'session') {
      const session = tx.findRecordById(sessions, task.getString('session'));
      save(tx, session, { nativeId: checkedText(body.nativeId, 2048, 'native session ID'), options: body.options || [] });
      return { state };
    }
    if (operation === 'approval') {
      if (state !== 'running' || task.getString('approvalId')) conflict('Cannot open approval');
      const cursor = append(tx, task, [{ type: 'approval', data: body.approval }]);
      save(tx, task, { cursor, approvalId: body.approval.id, approvalOptions: body.approval.options, decision: '' });
      return { cursor };
    }
    if (operation === 'clearApproval') {
      if (task.getString('approvalId') === body.approvalId) save(tx, task, clearApproval());
      return { state };
    }
    if (operation === 'finish') {
      if (!terminal.includes(body.state)) reject('Invalid completion state');
      const cursor = body.result ? append(tx, task, [body.result]) : task.getInt('cursor');
      return taskView(save(tx, task, { ...clearApproval(), cursor, leaseUntil: 0,
        state: state === 'canceling' ? 'canceled' : body.state }));
    }
    throw new NotFoundError();
  });
}

module.exports = { publicRequest, internalRequest };
