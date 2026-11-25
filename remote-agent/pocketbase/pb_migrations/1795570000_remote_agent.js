/* global migrate, Collection */

migrate(app => {
  const clients = app.findCollectionByNameOrId('aw_clients');
  const text = (name, max, required = false) => ({ name, type: 'text', max, required });
  const number = name => ({ name, type: 'number', onlyInt: true, min: 0 });
  const json = (name, maxSize) => ({ name, type: 'json', maxSize });
  const relation = (name, collection) => ({
    name, type: 'relation', collectionId: collection.id, required: true, maxSelect: 1, cascadeDelete: true,
  });
  const create = (name, fields, indexes = []) => {
    const collection = new Collection({ name, type: 'base', fields: [
      ...fields,
      { name: 'created', type: 'autodate', onCreate: true },
      { name: 'updated', type: 'autodate', onCreate: true, onUpdate: true },
    ], indexes });
    app.save(collection);
    return collection;
  };
  const projects = create('aw_remote_projects', [
    relation('owner', clients), text('name', 120, true), text('workspaceKey', 80, true),
    json('agents', 4096), { name: 'enabled', type: 'bool' },
  ], ['CREATE UNIQUE INDEX aw_remote_project_key ON aw_remote_projects(owner, workspaceKey)']);
  const sessions = create('aw_remote_sessions', [
    relation('owner', clients), relation('project', projects), text('agent', 40, true),
    text('title', 120, true), text('requestKey', 80, true), text('nativeId', 2048),
    json('options', 65536),
  ], ['CREATE UNIQUE INDEX aw_remote_session_request ON aw_remote_sessions(owner, requestKey)']);
  const tasks = create('aw_remote_tasks', [
    relation('owner', clients), relation('session', sessions), text('requestKey', 80, true),
    text('prompt', 48000, true), json('config', 4096), json('files', 16384), text('retryOf', 15),
    text('state', 24, true), number('runAt'), number('leaseUntil'), text('execution', 80),
    number('cursor'), text('approvalId', 80), json('approvalOptions', 16384), text('decision', 256),
  ], [
    'CREATE UNIQUE INDEX aw_remote_task_request ON aw_remote_tasks(owner, requestKey)',
    'CREATE INDEX aw_remote_task_due ON aw_remote_tasks(state, runAt)',
    'CREATE INDEX aw_remote_task_lease ON aw_remote_tasks(state, leaseUntil)',
    'CREATE INDEX aw_remote_task_session ON aw_remote_tasks(session, state)',
    "CREATE UNIQUE INDEX aw_remote_active_session ON aw_remote_tasks(session) WHERE state IN ('running', 'canceling')",
  ]);
  create('aw_remote_events', [
    relation('task', tasks), number('sequence'), text('type', 32, true), json('data', 131072),
  ], ['CREATE UNIQUE INDEX aw_remote_event_cursor ON aw_remote_events(task, sequence)']);
}, app => {
  for (const name of ['events', 'tasks', 'sessions', 'projects']) {
    app.delete(app.findCollectionByNameOrId('aw_remote_' + name));
  }
});
