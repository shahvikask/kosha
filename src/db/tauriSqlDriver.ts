import { SqlDriver, SqlValue } from './driver';

type Invoke = <T>(cmd: string, args?: Record<string, unknown>) => Promise<T>;

async function invoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke: inv } = await import('@tauri-apps/api/core');
  return (inv as Invoke)(cmd, args);
}

function toParam(v: SqlValue): string | number | null {
  if (v === null || v === undefined) return null;
  if (typeof v === 'bigint') return v.toString();
  if (v instanceof Uint8Array) return Array.from(v).join(',');
  return v;
}

/** Native rusqlite in the .exe. File: %LOCALAPPDATA%\GrowUrDhan\vault.db */
export class TauriSqlDriver implements SqlDriver {
  async open(): Promise<string> {
    return invoke<string>('sql_open');
  }

  async exec(sql: string, params?: SqlValue[]): Promise<void> {
    await invoke('sql_exec', { sql, params: (params ?? []).map(toParam) });
  }

  async query<T = Record<string, SqlValue>>(sql: string, params?: SqlValue[]): Promise<T[]> {
    return invoke<T[]>('sql_query', { sql, params: (params ?? []).map(toParam) });
  }

  async transaction<T>(fn: () => Promise<T>): Promise<T> {
    await this.exec('BEGIN');
    try {
      const r = await fn();
      await this.exec('COMMIT');
      return r;
    } catch (e) {
      try { await this.exec('ROLLBACK'); } catch { /* ignore */ }
      throw e;
    }
  }

  async persist(): Promise<void> {}
  async close(): Promise<void> {
    try { await invoke('sql_close'); } catch { /* ignore */ }
  }
}
