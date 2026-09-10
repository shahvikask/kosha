import { BlobStore, IdbBlobStore } from './sqliteWasmDriver';
import { SqlDriver } from './driver';

export function isDesktop(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
}

export function errorMessage(err: unknown): string {
  if (err instanceof Error && err.message) return err.message;
  if (typeof err === 'string' && err.trim()) return err;
  if (err && typeof err === 'object') {
    const o = err as { message?: unknown; error?: unknown };
    if (typeof o.message === 'string' && o.message.trim()) return o.message;
    if (typeof o.error === 'string' && o.error.trim()) return o.error;
    try { return JSON.stringify(err); } catch { /* ignore */ }
  }
  return 'Database initialization error';
}

/** Browser only. Desktop uses rusqlite via TauriSqlDriver. */
export async function createVaultStore(): Promise<BlobStore> {
  return new IdbBlobStore();
}

export async function openVaultDriver(): Promise<SqlDriver> {
  if (isDesktop()) {
    const { TauriSqlDriver } = await import('./tauriSqlDriver');
    const d = new TauriSqlDriver();
    await d.open();
    return d;
  }
  const { SqliteWasmDriver } = await import('./sqliteWasmDriver');
  const d = new SqliteWasmDriver('kosha.db', await createVaultStore());
  await d.open();
  return d;
}
