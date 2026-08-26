/**
 * Browser driver: the official SQLite WASM build.
 *
 * Why this design rather than OPFS:
 *   OPFS requires COOP/COEP headers, which cannot be set inside an
 *   embedded preview iframe (Google AI Studio, CodeSandbox and similar).
 *   wa-sqlite's example VFS modules are also outside its export map and
 *   their APIs change between releases.
 *
 * So: the database lives in memory and is SERIALISED to IndexedDB after
 * every write, debounced. IndexedDB has no header requirements, gigabytes
 * of quota, and is available everywhere.
 *
 * On desktop and Android this whole file is replaced by TauriSqlDriver,
 * which uses a real SQLCipher file. Nothing above the SqlDriver interface
 * changes.
 */
import { SqlDriver, SqlValue } from './driver';

/** Storage for the serialised database. Injectable so it can be tested. */
export interface BlobStore {
  load(key: string): Promise<Uint8Array | null>;
  save(key: string, bytes: Uint8Array): Promise<void>;
}

/** IndexedDB-backed blob store. One object store, one key. */
export class IdbBlobStore implements BlobStore {
  constructor(private dbName = 'kosha-store', private storeName = 'db') {}

  private open(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open(this.dbName, 1);
      req.onupgradeneeded = () => {
        if (!req.result.objectStoreNames.contains(this.storeName)) {
          req.result.createObjectStore(this.storeName);
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  }

  async load(key: string): Promise<Uint8Array | null> {
    const idb = await this.open();
    return new Promise((resolve, reject) => {
      const tx = idb.transaction(this.storeName, 'readonly');
      const req = tx.objectStore(this.storeName).get(key);
      req.onsuccess = () => {
        const v = req.result;
        resolve(v ? new Uint8Array(v as ArrayBuffer) : null);
      };
      req.onerror = () => reject(req.error);
    });
  }

  async save(key: string, bytes: Uint8Array): Promise<void> {
    const idb = await this.open();
    return new Promise((resolve, reject) => {
      const tx = idb.transaction(this.storeName, 'readwrite');
      const copy = new Uint8Array(bytes);
      tx.objectStore(this.storeName).put(copy.buffer, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }
}

const WRITE_RE = /^\s*(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|REPLACE|COMMIT|PRAGMA)/i;

export class SqliteWasmDriver implements SqlDriver {
  private sqlite3: any = null;
  private db: any = null;
  private dirty = false;
  private saveTimer: ReturnType<typeof setTimeout> | null = null;
  private inTransaction = false;

  constructor(
    private key = 'kosha.db',
    private store: BlobStore = new IdbBlobStore(),
    private debounceMs = 400
  ) {}

  async open(): Promise<void> {
    const init: any = (await import('@sqlite.org/sqlite-wasm')).default;
    this.sqlite3 = await init({ print: () => {}, printErr: () => {} });
    this.db = new this.sqlite3.oo1.DB(':memory:', 'c');

    const saved = await this.store.load(this.key);
    if (saved && saved.length > 0) {
      const p = this.sqlite3.wasm.allocFromTypedArray(saved);
      const rc = this.sqlite3.capi.sqlite3_deserialize(
        this.db.pointer, 'main', p, saved.length, saved.length,
        this.sqlite3.capi.SQLITE_DESERIALIZE_FREEONCLOSE |
        this.sqlite3.capi.SQLITE_DESERIALIZE_RESIZEABLE
      );
      if (rc !== 0) {
        throw new Error(
          'Your saved records could not be opened. Nothing has been changed. ' +
          'A backup copy is still in browser storage.'
        );
      }
    }
    this.db.exec('PRAGMA foreign_keys = ON;');
  }

  private ensure(): void {
    if (!this.db) throw new Error('Database is not open yet.');
  }

  private markDirty(): void {
    this.dirty = true;
    if (this.inTransaction) return;
    if (this.saveTimer) clearTimeout(this.saveTimer);
    this.saveTimer = setTimeout(() => { void this.persist(); }, this.debounceMs);
  }

  async exec(sql: string, params?: SqlValue[]): Promise<void> {
    this.ensure();
    if (params && params.length) {
      this.db.exec({ sql, bind: params.map(normalise) });
    } else {
      this.db.exec(sql);
    }
    if (WRITE_RE.test(sql)) this.markDirty();
  }

  async query<T = Record<string, SqlValue>>(sql: string, params?: SqlValue[]): Promise<T[]> {
    this.ensure();
    const rows: T[] = [];
    this.db.exec({
      sql,
      bind: params && params.length ? params.map(normalise) : undefined,
      rowMode: 'object',
      callback: (r: T) => { rows.push(r); },
    });
    return rows;
  }

  async transaction<T>(fn: () => Promise<T>): Promise<T> {
    this.ensure();
    this.db.exec('BEGIN');
    this.inTransaction = true;
    try {
      const r = await fn();
      this.db.exec('COMMIT');
      this.inTransaction = false;
      this.markDirty();
      return r;
    } catch (e) {
      try { this.db.exec('ROLLBACK'); } catch { /* already rolled back */ }
      this.inTransaction = false;
      throw e;
    }
  }

  /** Serialise and write to IndexedDB. Safe to call at any time. */
  async persist(): Promise<void> {
    if (!this.db || !this.dirty) return;
    const bytes: Uint8Array = this.sqlite3.capi.sqlite3_js_db_export(this.db.pointer);
    await this.store.save(this.key, bytes);
    this.dirty = false;
  }

  async close(): Promise<void> {
    if (this.saveTimer) clearTimeout(this.saveTimer);
    await this.persist();
    if (this.db) { this.db.close(); this.db = null; }
  }

  /** Bytes of the current database, for a manual backup file. */
  async exportBytes(): Promise<Uint8Array> {
    this.ensure();
    return this.sqlite3.capi.sqlite3_js_db_export(this.db.pointer);
  }
}

function normalise(v: SqlValue): string | number | null | Uint8Array {
  if (v === null || v === undefined) return null;
  if (typeof v === 'bigint') return v.toString();
  return v;
}
