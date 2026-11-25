/* global routerAdd, __hooks, $apis, $os, $security, ForbiddenError */

routerAdd('GET', '/api/rikka/remote/v1/{path...}', e =>
  e.json(200, require(__hooks + '/remote_agent.js').publicRequest(e)), $apis.requireAuth('aw_clients'));
routerAdd('POST', '/api/rikka/remote/v1/{path...}', e =>
  e.json(200, require(__hooks + '/remote_agent.js').publicRequest(e)), $apis.requireAuth('aw_clients'));
routerAdd('POST', '/api/rikka/internal/remote/{operation}', e => {
  const operation = e.request.pathValue('operation');
  const dispatch = ['dispatch', 'reconcile'].includes(operation);
  const secret = $os.getenv(dispatch ? 'RIKKA_DISPATCH_SECRET' : 'RIKKA_GATEWAY_SECRET');
  if (!secret || !$security.equal(secret, e.request.header.get('X-Rikka-Service'))) throw new ForbiddenError();
  return e.json(200, require(__hooks + '/remote_agent.js').internalRequest(e, operation, $os.getenv('RIKKA_GATEWAY_OWNER')));
});
