/**
 * Scheduled locked backups. Last 5 .kosha files in a folder the user picks
 * (Drive / OneDrive / Downloads via Android SAF, or a browser directory).
 * Password is never persisted — runs only while the vault is unlocked.
 */
import { suggestedScheduledBackupName } from './backup';
import { isNativeShell, nativeCommand } from './license';
import { peekSessionSecret } from './sessionSecret';

export const BACKUP_KEEP_LAST = 5;
const LS_SETTINGS = 'gud.backup.schedule';
const IDB_NAME = 'gud-backup-schedule';
const IDB_STORE = 'kv';
const IDB_DIR = 'dir';

export type BackupTrigger = 'open' | 'cas';

export interface BackupScheduleSettings {
  enabled: boolean;
  onOpen: boolean;
  onCasImport: boolean;
  folderLabel: string | null;
  lastRunAt: string | null;
  lastError: string | null;
  lastKept: number;
}

export const DEFAULT_BACKUP_SCHEDULE: BackupScheduleSettings = {
  enabled: false,
  onOpen: true,
  onCasImport: true,
  folderLabel: null,
  lastRunAt: null,
  lastError: null,
  lastKept: 0,
};

export function pruneToLastN<T>(items: T[], keep: number, timeOf: (item: T) => number): T[] {
  if (keep < 0) return [...items];
  return [...items].sort((a, b) => timeOf(b) - timeOf(a)).slice(keep);
}

export function readBackupSchedule(): BackupScheduleSettings {
  try {
    const raw = localStorage.getItem(LS_SETTINGS);
    if (!raw) return { ...DEFAULT_BACKUP_SCHEDULE };
    const parsed = JSON.parse(raw) as Partial<BackupScheduleSettings>;
    return { ...DEFAULT_BACKUP_SCHEDULE, ...parsed };
  } catch {
    return { ...DEFAULT_BACKUP_SCHEDULE };
  }
}

export function writeBackupSchedule(next: BackupScheduleSettings): void {
  try {
    localStorage.setItem(LS_SETTINGS, JSON.stringify(next));
  } catch {
    /* ignore quota */
  }
  notifyScheduleListeners();
}

const listeners = new Set<() => void>();

export function subscribeBackupSchedule(fn: () => void): () => void {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

function notifyScheduleListeners(): void {
  listeners.forEach(fn => {
    try {
      fn();
    } catch {
      /* ignore */
    }
  });
}

function patchSchedule(partial: Partial<BackupScheduleSettings>): BackupScheduleSettings {
  const next = { ...readBackupSchedule(), ...partial };
  writeBackupSchedule(next);
  return next;
}

export type PackBackupFn = (email: string, password: string) => Promise<Uint8Array>;

let packBackupFn: PackBackupFn | null = null;
let running: Promise<void> | null = null;
let lastStartMs = 0;

export function bindScheduledBackupPacker(fn: PackBackupFn | null): void {
  packBackupFn = fn;
}

export async function pickBackupFolder(): Promise<BackupScheduleSettings> {
  if (isNativeShell()) {
    try {
      const result = await nativeCommand<{ label?: string | null; chosen?: boolean } | null>('backup_pick_folder');
      if (!result || result.chosen === false) return readBackupSchedule();
      return patchSchedule({
        folderLabel: result.label || 'Cloud folder',
        lastError: null,
      });
    } catch (e) {
      return patchSchedule({
        lastError: e instanceof Error ? e.message : 'Could not open the folder picker.',
      });
    }
  }
  const picker = window as unknown as {
    showDirectoryPicker?: (opts?: { mode?: string }) => Promise<FileSystemDirectoryHandle>;
  };
  if (typeof picker.showDirectoryPicker !== 'function') {
    return patchSchedule({
      lastError: 'This browser cannot keep a cloud folder. On the Android app, pick Google Drive or OneDrive.',
    });
  }
  try {
    const handle = await picker.showDirectoryPicker({ mode: 'readwrite' });
    await saveDirHandle(handle);
    return patchSchedule({ folderLabel: handle.name || 'Chosen folder', lastError: null });
  } catch (e) {
    if ((e as { name?: string }).name === 'AbortError') return readBackupSchedule();
    return patchSchedule({
      lastError: e instanceof Error ? e.message : 'Could not use that folder.',
    });
  }
}

export async function clearBackupFolder(): Promise<BackupScheduleSettings> {
  if (isNativeShell()) {
    try {
      await nativeCommand('backup_clear_folder');
    } catch {
      /* still clear local label */
    }
  }
  await deleteDirHandle();
  return patchSchedule({ folderLabel: null, enabled: false });
}

export async function maybeRunScheduledBackup(
  reason: BackupTrigger,
  pack: PackBackupFn = packBackupFn || rejectedPack,
): Promise<void> {
  const settings = readBackupSchedule();
  if (!settings.enabled) return;
  if (reason === 'open' && !settings.onOpen) return;
  if (reason === 'cas' && !settings.onCasImport) return;
  const secret = peekSessionSecret();
  if (!secret) {
    patchSchedule({ lastError: 'Unlock the vault to run scheduled backup. The password is not stored.' });
    return;
  }
  if (!settings.folderLabel && !isNativeShell()) {
    patchSchedule({ lastError: 'Choose a Google Drive or OneDrive folder first.' });
    return;
  }
  const now = Date.now();
  if (running || now - lastStartMs < 8_000) return;
  lastStartMs = now;
  running = (async () => {
    try {
      const bytes = await pack(secret.email, secret.password);
      const filename = suggestedScheduledBackupName();
      const kept = await writeScheduledBytes(filename, bytes);
      patchSchedule({
        lastRunAt: new Date().toISOString(),
        lastError: null,
        lastKept: kept,
      });
    } catch (e) {
      patchSchedule({
        lastError: e instanceof Error ? e.message : 'Scheduled backup did not finish.',
      });
    } finally {
      running = null;
    }
  })();
  await running;
}

async function rejectedPack(): Promise<Uint8Array> {
  throw new Error('Vault is not ready for backup.');
}

async function writeScheduledBytes(filename: string, bytes: Uint8Array): Promise<number> {
  if (isNativeShell()) {
    const result = await nativeCommand<{ kept?: number; label?: string }>('backup_write_scheduled', {
      filename,
      bytesB64: bytesToB64(bytes),
      keepLast: BACKUP_KEEP_LAST,
    });
    if (result?.label) patchSchedule({ folderLabel: result.label });
    return result?.kept ?? BACKUP_KEEP_LAST;
  }
  const dir = await loadDirHandle();
  if (!dir) throw new Error('Choose a folder for scheduled backups first.');
  const perm = await ensureDirPermission(dir);
  if (!perm) throw new Error('Allow GrowUrDhan to write in that folder, or pick it again.');
  const file = await dir.getFileHandle(filename, { create: true });
  const writable = await file.createWritable();
  await writable.write(bytes as unknown as BufferSource);
  await writable.close();
  return pruneWebFolder(dir);
}

async function pruneWebFolder(dir: FileSystemDirectoryHandle): Promise<number> {
  const found: { name: string; t: number }[] = [];
  const iterable = dir as unknown as { values(): AsyncIterable<FileSystemHandle> };
  for await (const handle of iterable.values()) {
    if (handle.kind !== 'file' || !handle.name.endsWith('.kosha')) continue;
    const f = await (handle as FileSystemFileHandle).getFile();
    found.push({ name: handle.name, t: f.lastModified });
  }
  const extra = pruneToLastN(found, BACKUP_KEEP_LAST, x => x.t);
  for (const item of extra) {
    try {
      await dir.removeEntry(item.name);
    } catch {
      /* ignore */
    }
  }
  return found.length - extra.length;
}

function bytesToB64(bytes: Uint8Array): string {
  const chunk = 0x8000;
  let s = '';
  for (let i = 0; i < bytes.length; i += chunk) {
    s += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(s);
}

function openHandleDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1);
    req.onupgradeneeded = () => {
      if (!req.result.objectStoreNames.contains(IDB_STORE)) req.result.createObjectStore(IDB_STORE);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function saveDirHandle(handle: FileSystemDirectoryHandle): Promise<void> {
  const db = await openHandleDb();
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(IDB_STORE, 'readwrite');
    tx.objectStore(IDB_STORE).put(handle, IDB_DIR);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
  db.close();
}

async function loadDirHandle(): Promise<FileSystemDirectoryHandle | null> {
  try {
    const db = await openHandleDb();
    const handle = await new Promise<FileSystemDirectoryHandle | null>((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readonly');
      const req = tx.objectStore(IDB_STORE).get(IDB_DIR);
      req.onsuccess = () => resolve((req.result as FileSystemDirectoryHandle) || null);
      req.onerror = () => reject(req.error);
    });
    db.close();
    return handle;
  } catch {
    return null;
  }
}

async function deleteDirHandle(): Promise<void> {
  try {
    const db = await openHandleDb();
    await new Promise<void>((resolve, reject) => {
      const tx = db.transaction(IDB_STORE, 'readwrite');
      tx.objectStore(IDB_STORE).delete(IDB_DIR);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
    db.close();
  } catch {
    /* ignore */
  }
}

async function ensureDirPermission(dir: FileSystemDirectoryHandle): Promise<boolean> {
  const h = dir as FileSystemDirectoryHandle & {
    queryPermission?: (o: { mode: string }) => Promise<PermissionState>;
    requestPermission?: (o: { mode: string }) => Promise<PermissionState>;
  };
  if (typeof h.queryPermission !== 'function') return true;
  let state = await h.queryPermission({ mode: 'readwrite' });
  if (state === 'granted') return true;
  if (typeof h.requestPermission === 'function') {
    state = await h.requestPermission({ mode: 'readwrite' });
  }
  return state === 'granted';
}
