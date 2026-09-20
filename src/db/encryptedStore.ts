/**
 * AES-GCM envelope around the SQLite WASM snapshot.
 *
 * Web: ciphertext in IndexedDB (other sites cannot read it).
 * Android: NativeKeystoreStore writes an EncryptedFile under the app
 * sandbox, keyed by Android Keystore — other apps, backup agents and
 * simple file stealers see noise. If the native plugin is not wired yet,
 * we still wrap with AES-GCM in IndexedDB inside the app sandbox.
 *
 * Envelope: GUDENC01 | salt(16) | iv(12) | ciphertext
 * Other sites cannot read IndexedDB. On Android, Keystore wraps the file.
 */
import { BlobStore, IdbBlobStore } from './sqliteWasmDriver';
import { isNativeShell, nativeCommand } from '../lib/license';

const MAGIC = 'GUDENC01';
const MAGIC_LEN = 8;
const SALT_LEN = 16;
const IV_LEN = 12;
const KEY_IDB = 'gud-vault-wrap';
const PBKDF2_ITERATIONS = 310_000;

let wrapOnce: Promise<{ salt: Uint8Array; key: CryptoKey }> | null = null;

function wrapMaterial(): { salt: Uint8Array; key: CryptoKey } | Promise<{ salt: Uint8Array; key: CryptoKey }> {
  if (!wrapOnce) {
    wrapOnce = (async () => {
      const store = new IdbBlobStore('kosha-store', 'db');
      let salt = await store.load(KEY_IDB);
      if (!salt || salt.length < SALT_LEN) {
        salt = crypto.getRandomValues(new Uint8Array(SALT_LEN));
        await store.save(KEY_IDB, salt);
      } else if (salt.length > SALT_LEN) {
        salt = salt.slice(0, SALT_LEN);
      }
      const key = await deriveDeviceKey(salt, PEPPERS[0]);
      return { salt, key };
    })();
  }
  return wrapOnce;
}

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
  if (bytes.length < MAGIC_LEN) return false;
  return new TextDecoder().decode(bytes.slice(0, MAGIC_LEN)) === MAGIC;
}

const PEPPERS = [
  'GrowUrDhan-vault-wrap/v1',
  typeof location !== 'undefined' && location.origin ? `GrowUrDhan-device/${location.origin}` : '',
  'GrowUrDhan-device/app',
].filter(Boolean);

async function deriveDeviceKey(salt: Uint8Array, material = PEPPERS[0]): Promise<CryptoKey> {
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(material), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations: PBKDF2_ITERATIONS, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

async function encryptBytes(plain: Uint8Array, key: CryptoKey, salt: Uint8Array): Promise<Uint8Array> {
  const iv = crypto.getRandomValues(new Uint8Array(IV_LEN));
  const cipher = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv as unknown as BufferSource }, key, plain as unknown as BufferSource),
  );
  const out = new Uint8Array(MAGIC_LEN + SALT_LEN + IV_LEN + cipher.length);
  out.set(new TextEncoder().encode(MAGIC), 0);
  out.set(salt, MAGIC_LEN);
  out.set(iv, MAGIC_LEN + SALT_LEN);
  out.set(cipher, MAGIC_LEN + SALT_LEN + IV_LEN);
  return out;
}

async function decryptBytes(packed: Uint8Array, key: CryptoKey, saltInFile: boolean): Promise<Uint8Array> {
  const saltOff = saltInFile ? SALT_LEN : 0;
  const iv = packed.slice(MAGIC_LEN + saltOff, MAGIC_LEN + saltOff + IV_LEN);
  const cipher = packed.slice(MAGIC_LEN + saltOff + IV_LEN);
  const plain = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource },
    key,
    cipher as unknown as BufferSource,
  );
  return new Uint8Array(plain);
}

/** Web / preview: origin-bound AES wrap over IndexedDB. */
export class EncryptedIdbStore implements BlobStore {
  private salt: Uint8Array | null = null;
  private key: CryptoKey | null = null;
  private inner = new IdbBlobStore('kosha-store', 'db');

  async prepare(): Promise<void> {
    const wrap = await wrapMaterial();
    this.salt = wrap.salt;
    this.key = wrap.key;
  }

  private async aes(): Promise<{ key: CryptoKey; salt: Uint8Array }> {
    if (!this.key || !this.salt) await this.prepare();
    return { key: this.key!, salt: this.salt! };
  }

  async load(key: string): Promise<Uint8Array | null> {
    const raw = await this.inner.load(key);
    if (!raw || raw.length === 0) return null;
    if (!hasMagic(raw)) {
      const { key: aesKey, salt } = await this.aes();
      const locked = await encryptBytes(raw, aesKey, salt);
      await this.inner.save(key, locked);
      return raw;
    }
    const fileSalt = raw.slice(MAGIC_LEN, MAGIC_LEN + SALT_LEN);
    const stored = this.salt || (await this.aes()).salt;
    const salts = [fileSalt, stored];
    for (const material of PEPPERS) {
      for (const salt of salts) {
        try {
          const k = await deriveDeviceKey(salt, material);
          return await decryptBytes(raw, k, true);
        } catch {
          try {
            const k = await deriveDeviceKey(salt, material);
            return await decryptBytes(raw, k, false);
          } catch {
            /* try next pepper/salt */
          }
        }
      }
    }
    throw new Error('The encrypted vault on this device could not be opened.');
  }

  async save(key: string, bytes: Uint8Array): Promise<void> {
    const { key: aesKey, salt } = await this.aes();
    const locked = await encryptBytes(bytes, aesKey, salt);
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
