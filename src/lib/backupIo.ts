/**
 * Save / pick a backup file. Same API on web, Windows (Tauri) and Android WebView.
 * Tries native dialogs first, then the File System Access API, then a download / file input.
 */
export async function saveBackupFile(filename: string, bytes: Uint8Array): Promise<void> {
  const native = await tauriSave(filename, bytes);
  if (native) return;
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
      return;
    } catch (e) {
      if ((e as { name?: string }).name === 'AbortError') return;
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
}

export async function pickBackupFile(): Promise<Uint8Array | null> {
  const fromTauri = await tauriOpen();
  if (fromTauri !== undefined) return fromTauri;
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
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

function b64ToBytes(b64: string): Uint8Array {
  const s = atob(b64);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

async function invokeTauri<T>(cmd: string, args: Record<string, unknown>): Promise<T | null> {
  const g = window as unknown as {
    __TAURI_INTERNALS__?: { invoke?: (c: string, a: unknown) => Promise<T> };
    __TAURI__?: { invoke?: (c: string, a: unknown) => Promise<T> };
  };
  const inv = g.__TAURI_INTERNALS__?.invoke || g.__TAURI__?.invoke;
  if (typeof inv !== 'function') return null;
  try {
    return await inv(cmd, args);
  } catch {
    return null;
  }
}

async function tauriSave(filename: string, bytes: Uint8Array): Promise<boolean> {
  const ok = await invokeTauri<boolean | void>('backup_save', {
    filename,
    bytesB64: bytesToB64(bytes),
  });
  return ok !== null;
}

async function tauriOpen(): Promise<Uint8Array | null | undefined> {
  const g = window as unknown as { __TAURI_INTERNALS__?: unknown; __TAURI__?: unknown };
  if (!g.__TAURI_INTERNALS__ && !g.__TAURI__) return undefined;
  const b64 = await invokeTauri<string | null>('backup_open', {});
  if (b64 === null) return undefined;
  if (!b64) return null;
  return b64ToBytes(b64);
}
