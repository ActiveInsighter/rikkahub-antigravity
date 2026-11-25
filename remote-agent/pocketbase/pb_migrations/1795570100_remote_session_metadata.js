/* global migrate, BoolField, TextField */

migrate(app => {
  const sessions = app.findCollectionByNameOrId('aw_remote_sessions');
  sessions.fields.add(new BoolField({ name: 'pinned' }));
  sessions.fields.add(new BoolField({ name: 'deleted' }));
  sessions.fields.add(new TextField({ name: 'originalTitle', max: 120 }));
  app.save(sessions);
}, app => {
  const sessions = app.findCollectionByNameOrId('aw_remote_sessions');
  sessions.fields.removeByName('pinned');
  sessions.fields.removeByName('deleted');
  sessions.fields.removeByName('originalTitle');
  app.save(sessions);
});
