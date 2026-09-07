#!/usr/bin/env node
/**
 * Start the local Python CAS sidecar, then Vite.
 * CAS PDFs are parsed on this machine and never leave the device.
 */
import { spawn } from 'node:child_process';
import { createServer } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const SIDECAR_PORT = 8765;

function pythonBins() {
  return process.platform === 'win32' ? ['python', 'py', 'python3'] : ['python3', 'python'];
}

function portInUse(port) {
  return new Promise(resolve => {
    const s = createServer();
    s.once('error', () => resolve(true));
    s.once('listening', () => s.close(() => resolve(false)));
    s.listen(port, '127.0.0.1');
  });
}

function spawnPython(bin) {
  return new Promise((resolve, reject) => {
    const child = spawn(bin, ['scripts/cas_parse_server.py'], {
      cwd: root,
      stdio: ['ignore', 'pipe', 'pipe'],
      windowsHide: true,
    });
    let settled = false;
    const fail = err => {
      if (settled) return;
      settled = true;
      reject(err);
    };
    const ok = () => {
      if (settled) return;
      settled = true;
      resolve(child);
    };
    child.on('error', fail);
    child.stdout.on('data', chunk => {
      process.stdout.write(chunk);
      if (String(chunk).includes('listening')) ok();
    });
    child.stderr.on('data', chunk => {
      process.stderr.write(chunk);
      const text = String(chunk);
      if (text.includes('Address already in use') || text.includes('already in use')) ok();
    });
    child.on('exit', code => {
      fail(new Error(`${bin} exited with code ${code}`));
    });
    setTimeout(ok, 1200);
  });
}

async function startSidecar() {
  if (await portInUse(SIDECAR_PORT)) {
    console.log(`[cas-parser] already running on 127.0.0.1:${SIDECAR_PORT}`);
    return null;
  }
  let lastErr;
  for (const bin of pythonBins()) {
    try {
      const child = await spawnPython(bin);
      console.log(`[cas-parser] started with ${bin}`);
      return child;
    } catch (err) {
      lastErr = err;
    }
  }
  console.warn('[cas-parser] Python sidecar did not start. PDF import needs Python 3.');
  console.warn('[cas-parser] Install deps:  pip install -r scripts/requirements-cas.txt');
  if (lastErr) console.warn('[cas-parser]', lastErr.message || lastErr);
  return null;
}

const sidecar = await startSidecar();
const viteBin = path.join(root, 'node_modules', 'vite', 'bin', 'vite.js');
const vite = spawn(
  process.execPath,
  [viteBin, '--port=3000', '--host=0.0.0.0', ...process.argv.slice(2)],
  { cwd: root, stdio: 'inherit' }
);

function shutdown() {
  try {
    vite.kill('SIGTERM');
  } catch {
    /* ignore */
  }
  try {
    sidecar?.kill('SIGTERM');
  } catch {
    /* ignore */
  }
}

process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);

vite.on('exit', code => {
  try {
    sidecar?.kill('SIGTERM');
  } catch {
    /* ignore */
  }
  process.exit(code ?? 0);
});
