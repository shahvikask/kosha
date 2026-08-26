/** node:sqlite driver. Used by the test suite only, never shipped. */
import { DatabaseSync } from 'node:sqlite';
import { SqlDriver, SqlValue } from './driver';

export class NodeSqlDriver implements SqlDriver {
  private db: DatabaseSync;
  constructor(path = ':memory:') { this.db = new DatabaseSync(path); }

  async exec(sql: string, params?: SqlValue[]): Promise<void> {
    if (params && params.length) {
      this.db.prepare(sql).run(...(params as never[]));
    } else {
      this.db.exec(sql);
    }
  }
  async query<T = Record<string, SqlValue>>(sql: string, params?: SqlValue[]): Promise<T[]> {
    return this.db.prepare(sql).all(...((params ?? []) as never[])) as T[];
  }
  async transaction<T>(fn: () => Promise<T>): Promise<T> {
    this.db.exec('BEGIN');
    try { const r = await fn(); this.db.exec('COMMIT'); return r; }
    catch (e) { this.db.exec('ROLLBACK'); throw e; }
  }
  async persist(): Promise<void> {}
  async close(): Promise<void> { this.db.close(); }
}
