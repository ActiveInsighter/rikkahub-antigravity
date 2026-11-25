import { readFile, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { Readable, Writable } from 'node:stream';
import { setTimeout } from 'node:timers/promises';
import { agent, ndJsonStream } from '@agentclientprotocol/sdk';

const options = [{
  id: 'effort', name: 'Effort', category: 'thought_level', type: 'select', currentValue: 'low',
  options: [{ value: 'low', name: 'Low' }, { value: 'high', name: 'High' }],
}];
const sessionFile = '.fixture-session.json';
let session;
const persist = () => writeFile(sessionFile, JSON.stringify(session));
const app = agent()
  .onRequest('initialize', () => ({ protocolVersion: 1, agentCapabilities: { loadSession: true } }))
  .onRequest('session/new', async () => {
    session = { id: randomUUID(), memory: '', effort: options[0].currentValue };
    await persist();
    return { sessionId: session.id, configOptions: options };
  })
  .onRequest('session/load', async ({ client, params }) => {
    session = JSON.parse(await readFile(sessionFile, 'utf8'));
    if (params.sessionId !== session.id) throw new Error('Wrong native session ID');
    options[0].currentValue = session.effort;
    await client.notify('session/update', { sessionId: session.id, update: {
      sessionUpdate: 'agent_message_chunk', content: { type: 'text', text: 'REPLAYED HISTORY' },
    } });
    return { configOptions: options };
  })
  .onRequest('session/set_config_option', async ({ params }) => {
    options[0].currentValue = session.effort = params.value;
    await persist();
    return { configOptions: options };
  })
  .onRequest('session/prompt', async ({ client, params, signal }) => {
    const { sessionId, prompt } = params;
    const send = text => client.notify('session/update', { sessionId,
      update: { sessionUpdate: 'agent_message_chunk', content: { type: 'text', text } } });
    if (prompt[0].text.startsWith('remember ')) {
      session.memory = prompt[0].text.slice('remember '.length);
      await persist();
    }
    if (prompt[0].text === 'recall') await send('remembered=' + session.memory + '; ');
    if (prompt[0].text === 'cancel' || prompt[0].text === 'restart') {
      await send('waiting');
      await setTimeout(40000, undefined, { signal });
    }
    if (prompt[0].text === 'approve') {
      const response = await client.request('session/request_permission', { sessionId,
        toolCall: { toolCallId: 'git', title: 'Read Git version', kind: 'execute' },
        options: [{ optionId: 'yes', kind: 'allow_once', name: 'Allow once' },
          { optionId: 'no', kind: 'reject_once', name: 'Reject once' }] });
      if (response.outcome.optionId !== 'yes') return { stopReason: 'refusal' };
    }
    if (prompt[0].text.startsWith('artifact')) {
      const match = /Uploaded input files: (.+)\nWrite requested output artifacts to: (.+)/.exec(prompt[0].text);
      await writeFile(match[2], await readFile(match[1]));
    }
    const marker = await readFile('AUTHORIZED.txt', 'utf8');
    const git = execFileSync('/usr/bin/git', ['--version'], { encoding: 'utf8' }).trim();
    await send(marker.trim() + '; ' + git + '; effort=' + options[0].currentValue);
    return { stopReason: 'end_turn' };
  });
app.connect(ndJsonStream(Writable.toWeb(process.stdout), Readable.toWeb(process.stdin)));
