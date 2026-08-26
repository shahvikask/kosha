/**
 * Storage driver interface. The one abstraction that makes the same
 * repository code run on browser, desktop and Android.
 *
 * Implementations:
 *   WaSqliteDriver   browser, wa-sqlite + OPFS
 *   TauriSqlDriver   desktop and Android, rusqlite/SQLCipher via invoke
 *   NodeSqlDriver    tests, node:sqlite
 *
 * Nothing above this interface knows which platform it is on.
 */

export type SqlValue = string | number | bigint | null | Uint8Array;

export interface SqlDriver {
  /** Run statements with no result set. Used for DDL and writes. */
  exec(sql: string, params?: SqlValue[]): Promise<void>;
  /** Run a query and return rows as plain objects. */
  query<T = Record<string, SqlValue>>(sql: string, params?: SqlValue[]): Promise<T[]>;
  /** Run fn inside a transaction. Rolls back on any throw. */
  transaction<T>(fn: () => Promise<T>): Promise<T>;
  /** Flush to durable storage. No-op where writes are already durable. */
  persist(): Promise<void>;
  close(): Promise<void>;
}

/** Thrown when the append-only trigger fires. Not an error to swallow. */
export class AppendOnlyViolation extends Error {
  constructor(detail: string) {
    super(`The records log cannot be changed. ${detail}`);
  }
}

export function isAppendOnlyViolation(e: unknown): boolean {
  const m = e instanceof Error ? e.message : String(e);
  return m.includes('append-only') || m.includes('immutable once installed');
}
