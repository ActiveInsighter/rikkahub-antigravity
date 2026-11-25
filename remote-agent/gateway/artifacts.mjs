import { createHash } from 'node:crypto';
import { constants } from 'node:fs';
import { mkdir, mkdtemp, open, realpath, rm, writeFile } from 'node:fs/promises';
import { basename, join, relative } from 'node:path';
import { RuntimeError } from './runtime.mjs';

const maxSize = 20 * 1024 * 1024;
const secret = process.env.RIKKA_ARTIFACT_SECRET;
const artifactId = (taskId, path) => {
  const hex = createHash('sha256').update(taskId + '\0' + path).digest('hex');
  return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20, 32)].join('-');
};

async function storage(config, id, signal, init = {}) {
  if (!secret || !config.artifactUrl) throw new RuntimeError('artifact_storage_unavailable');
  const url = new URL('/internal/artifacts/' + id, config.artifactUrl);
  if (url.protocol !== 'https:' && url.hostname !== '127.0.0.1') throw new RuntimeError('artifact_url_invalid');
  return fetch(url, {
    ...init, redirect: 'error', signal: AbortSignal.any([signal, AbortSignal.timeout(60000)]),
    headers: { 'X-Rikka-Service': secret, ...init.headers },
  });
}

export async function prepareFiles(config, task, cwd, signal) {
  const { inputs, outputs } = task.files;
  if (!inputs.length && !outputs.length) return { prompt: task.prompt };
  // A fresh directory keeps outputs from previous turns out of the artifact manifest.
  const directory = await mkdtemp(join(cwd, '.rikka-files-'));
  try {
    const inputDir = join(directory, 'input');
    const outputDir = join(directory, 'output');
    await mkdir(inputDir, { mode: 0o700 });
    await mkdir(outputDir, { mode: 0o700 });
    for (const input of inputs) {
      const response = await storage(config, input.id, signal);
      const size = Number(response.headers.get('Content-Length'));
      if (!response.ok || !size || size > maxSize) throw new RuntimeError('input_artifact_unavailable');
      const contents = new Uint8Array(await response.arrayBuffer());
      if (contents.length !== size) throw new RuntimeError('input_artifact_invalid');
      await writeFile(join(inputDir, input.name), contents, { flag: 'wx', mode: 0o600 });
    }
    const folder = basename(directory);
    const notes = [];
    if (inputs.length) notes.push('Uploaded input files: ' + inputs.map(item => folder + '/input/' + item.name).join(', '));
    if (outputs.length) notes.push('Write requested output artifacts to: ' +
      outputs.map(path => folder + '/output/' + path).join(', '));
    return { prompt: task.prompt + '\n\n' + notes.join('\n'), directory };
  } catch (error) {
    await rm(directory, { recursive: true, force: true }).catch(() => {});
    throw error;
  }
}

export async function publishOutputs(config, task, directory, signal, onArtifact) {
  const outputDir = join(directory, 'output');
  if (await realpath(outputDir) !== outputDir) throw new RuntimeError('output_directory_invalid');
  for (const path of task.files.outputs) {
    const requested = join(outputDir, path);
    const resolved = await realpath(requested);
    if (relative(outputDir, resolved) !== path) throw new RuntimeError('output_path_invalid');
    const file = await open(requested, constants.O_RDONLY | constants.O_NOFOLLOW | constants.O_NONBLOCK);
    try {
      const info = await file.stat();
      if (!info.isFile() || info.size < 1 || info.size > maxSize) throw new RuntimeError('output_artifact_invalid');
      const id = artifactId(task.id, path);
      const bytes = await file.readFile();
      if (bytes.length !== info.size) throw new RuntimeError('output_artifact_changed');
      const response = await storage(config, id, signal, { method: 'PUT', body: bytes });
      if (!response.ok) throw new RuntimeError('output_artifact_publish_failed');
      await onArtifact({ id, path, size: info.size });
    } finally {
      await file.close();
    }
  }
}
