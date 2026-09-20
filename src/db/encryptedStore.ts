/**
 * AES-GCM envelope around the SQLite WASM snapshot.
 *
 * Web: ciphertext in IndexedDB (other sites cannot read it).
 * Android: NativeKeystoreStore writes an EncryptedFile under the app
 * sandbox, keyed by Android Keystore — other apps, backup agents and
 * simple file stealers see noise. If the native plugin is not wired yet,
 * we still wrap with AES-GCM in IndexedDB inside the app sandbox.
 */
import { BlobStore, IdbBlobStore } from './sqliteWasmDriver';
import { isNativeShell, nativeCommand } from '../lib/license';

const MAGIC = 'GUDENC01';
const KEY_IDB = 'gud-vault-wrap';
const PBKDF2_ITERATIONS = 310_000;

function toB64(bytes: Uint8Array): string {
  const chunk = 0x8000;
  let s = '';
  for (let i = 0; i < bytes.length; i += chunk) {
    s += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(s);
}

function fromB64(b64: string): Uint8Array {
  const s = atob(b64);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

function hasMagic(bytes: Uint8Array): boolean {
  if (bytes.length < 8) return false;
  return new TextDecoder().decode(bytes.slice(0, 8)) === MAGIC;
}

async function deriveDeviceKey(salt: Uint8Array): Promise<CryptoKey> {
  const origin = typeof location !== 'undefined' && location.origin ? location.origin : 'app';
  const material = `GrowUrDhan-device/${origin}`;
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(material), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

async function encryptBytes(plain: Uint8Array, key: CryptoKey): Promise<Uint8Array> {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const cipher = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv as unknown as BufferSource }, key, plain as unknown as BufferSource),
  );
  const out = new Uint8Array(8 + 12 + cipher.length);
  out.set(new TextEncoder().encode(MAGIC), 0);
  out.set(iv, 8);
  out.set(cipher, 20);
  return out;
}

async function decryptBytes(packed: Uint8Array, key: CryptoKey): Promise<Uint8Array> {
  const iv = packed.slice(8, 20);
  const cipher = packed.slice(20);
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource },
    key,
    cipher as unknown as BufferSource,
  );
  return new Uint8Array(plain);
}

/** Web / preview: origin-bound AES wrap over IndexedDB. */
export class EncryptedIdbStore implements BlobStore {
  private inner = new IdbBlobStore('kosha-store', 'db');
  private saltStore = new IdbBlobStore('kosha-store', 'db');
  private key: CryptoKey | null = null;

  async prepare(): Promise<void> {
    let salt = await this.saltStore.load(KEY_IDB);
    if (!salt || salt.length < 16) {
      salt = crypto.getRandomValues(new Uint8Array(16));
      await this.saltStore.save(KEY_IDB, salt);
    }
    this.key = await deriveDeviceKey(salt);
  }

  private async aes(): Promise<CryptoKey> {
    if (!this.key) await this.prepare();
    return this.key!;
  }

  async load(key: string): Promise<Uint8Array | null> {
    const raw = await this.inner.load(key);
    if (!raw || raw.length === 0) return null;
    if (!hasMagic(raw)) {
      const locked = await encryptBytes(raw, await this.aes());
      await this.inner.save(key, locked);
      return raw;
    }
    try {
      return await decryptBytes(raw, await this.aes());
    } catch {
      throw new Error('The encrypted vault on this device could not be opened.');
    }
  }

  async save(key: string, bytes: Uint8Array): Promise<void> {
    const locked = await encryptBytes(bytes, await this.aes());
    await this.inner.save(key, locked);
  }
}

/** Android: ciphertext file + Android Keystore, never a JS-held raw key. */
export class NativeKeystoreStore implements BlobStore {
  async probe(): Promise<boolean> {
    try {
      await nativeCommand<unknown>('vault_blob_load');
      return true;
    } catch {
      return false;
    }
  }

  async load(_key: string): Promise<Uint8Array | null> {
    const raw = await nativeCommand<unknown>('vault_blob_load');
    if (raw == null) return null;
    const b64 = typeof raw === 'string' ? raw : (raw as { value?: string }).value;
    if (!b64) return null;
    return fromB64(b64);
  }

  async save(_key: string, bytes: Uint8Array): Promise<void> {
    await nativeCommand('vault_blob_save', { bytesB64: toB64(bytes) });
  }
}

export async function createVaultStore(): Promise<BlobStore> {
  if (isNativeShell()) {
    const native = new NativeKeystoreStore();
    if (await native.probe()) return native;
  }
  const s = new EncryptedIdbStore();
  await s.prepare();
  return s;
}

export const __test = { hasMagic, encryptBytes, decryptBytes, deriveDeviceKey, MAGIC };
