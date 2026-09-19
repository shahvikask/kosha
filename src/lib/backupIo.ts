/**
 * Save / pick a locked .kosha file on every surface:
 *   web           — File System Access API, then <a download> / file input
 *   Windows exe   — Tauri commands backup_save / backup_open (native dialog)
 *   Android later — same file-input path as the browser (WebView)
 *
 * Encryption lives in backup.ts. This file only moves the bytes.
 * On the desktop app we never fall back to a fake <a download> — that
 * silently fails in WebView2, which is why backup looked broken on Windows.
 */

function isTauriShell(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
}

export async function saveBackupFile(filename: string, bytes: Uint8Array): Promise<'saved' | 'cancelled'> {
  if (isTauriShell()) {
    const saved = await desktopInvoke<boolean>('backup_save', {
      filename,
      bytesB64: bytesToB64(bytes),
    });
    return saved === false ? 'cancelled' : 'saved';
  }
  const picker = window as unknown as {
    showSaveFilePicker?: (opts: unknown) => Promise<{ createWritable: () => Promise<{ write: (b: BufferSource) => Promise<void>; close: () => Promise<void> }> }>;
  };
  if (typeof picker.showSaveFilePicker === 'function') {
    try {
      const handle = await picker.showSaveFilePicker({
        suggestedName: filename,
        types: [{ description: 'GrowUrDhan backup', accept: { 'application/octet-stream': ['.kosha'] } }],
      });
      const w = await handle.createWritable();
      await w.write(bytes as unknown as BufferSource);
      await w.close();
      return 'saved';
    } catch (e) {
      if ((e as { name?: string }).name === 'AbortError') return 'cancelled';
    }
  }
  const blob = new Blob([bytes as unknown as BlobPart], { type: 'application/octet-stream' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 2000);
  return 'saved';
}

export async function pickBackupFile(): Promise<Uint8Array | null> {
  if (isTauriShell()) {
    const b64 = await desktopInvoke<string | null>('backup_open', {});
    if (!b64) return null;
    return b64ToBytes(b64);
  }
  return pickViaInput();
}

function pickViaInput(): Promise<Uint8Array | null> {
  return new Promise(resolve => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.kosha,.json,application/octet-stream';
    input.style.display = 'none';
    input.onchange = async () => {
      const file = input.files?.[0];
      input.remove();
      if (!file) {
        resolve(null);
        return;
      }
      const buf = await file.arrayBuffer();
      resolve(new Uint8Array(buf));
    };
    input.oncancel = () => {
      input.remove();
      resolve(null);
    };
    document.body.appendChild(input);
    input.click();
  });
}

function bytesToB64(bytes: Uint8Array): string {
  const chunk = 0x8000;
  let s = '';
  for (let i = 0; i < bytes.length; i += chunk) {
    s += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(s);
}

function b64ToBytes(b64: string): Uint8Array {
  const s = atob(b64);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

/** Same invoke path as CAS parse. Throws so Family can show the error. */
async function desktopInvoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke } = await import('@tauri-apps/api/core');
  return invoke<T>(cmd, args);
}
