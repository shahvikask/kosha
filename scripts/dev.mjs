#!/usr/bin/env node
/**
 * Start the local Python CAS sidecar, then Vite.
 * CAS PDFs are parsed on this machine and never leave the device.
 *
 * Always (re)starts the sidecar so a `git pull` of casparser is picked
 * up. Reusing whatever is already on 8765 left people on a stale reader
 * (no NPS) after the UI had already updated.
 */
import { spawn, execSync } from 'node:child_process';
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

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function killSidecarPort(port) {
  if (process.platform === 'win32') {
    let out = '';
    try {
      out = execSync('netstat -ano', { encoding: 'utf8', windowsHide: true });
    } catch {
      return;
    }
    const pids = new Set();
    const re = new RegExp(`:${port}(?:\\s|$)`);
    for (const line of out.split(/\r?\n/)) {
      if (!/LISTENING/i.test(line) || !re.test(line)) continue;
      const pid = line.trim().split(/\s+/).pop();
      if (pid && /^\d+$/.test(pid) && pid !== '0') pids.add(pid);
    }
    for (const pid of pids) {
      try {
        execSync(`taskkill /PID ${pid} /F`, { stdio: 'ignore', windowsHide: true });
        console.log(`[cas-parser] stopped stale reader (pid ${pid})`);
      } catch {
        /* ignore */
      }
    }
    return;
  }
  try {
    const out = execSync(`lsof -ti tcp:${port} -sTCP:LISTEN`, { encoding: 'utf8' });
    for (const pid of out.split(/\s+/).filter(Boolean)) {
      try {
        process.kill(Number(pid), 'SIGTERM');
        console.log(`[cas-parser] stopped stale reader (pid ${pid})`);
      } catch {
        /* ignore */
      }
    }
  } catch {
    /* lsof missing or nothing listening */
  }
}

async function waitPortFree(port, ms = 2500) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    if (!(await portInUse(port))) return true;
    await sleep(150);
  }
  return !(await portInUse(port));
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
    console.log(`[cas-parser] restarting reader on 127.0.0.1:${SIDECAR_PORT}`);
    killSidecarPort(SIDECAR_PORT);
    await waitPortFree(SIDECAR_PORT);
  }
  if (await portInUse(SIDECAR_PORT)) {
    console.warn(
      '[cas-parser] could not replace the process on 8765; it may be a stale reader. Close other python/GrowUrDhan windows and run npm run dev again.'
    );
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
