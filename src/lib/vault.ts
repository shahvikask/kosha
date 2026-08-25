/**
 * Encryption at rest. Implements the intent of SRS-SEC-001..005 within the
 * limits of a browser.
 *
 * Uses only the Web Crypto API — no dependency, no bundled crypto library.
 * PBKDF2-SHA256 is used for key derivation because Argon2id is not available
 * natively in the browser. This is weaker than the SRS requires and is
 * recorded as a known deviation; a desktop build must use Argon2id.
 *
 * The passphrase is never stored. The derived key never leaves memory.
 * There is no recovery path: lose the passphrase and the recovery key and
 * the data is gone. That must be disclosed before a vault is created.
 */

const PBKDF2_ITERATIONS = 600_000;
const SALT_BYTES = 16;
const IV_BYTES = 12;
const MAGIC = 'KOSHA1';

export interface EncryptedBlob {
  magic: string;
  kdf: 'PBKDF2-SHA256';
  iterations: number;
  saltB64: string;
  ivB64: string;
  cipherB64: string;
}

function toB64(buf: ArrayBuffer | Uint8Array): string {
  const bytes = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
  let s = '';
  for (let i = 0; i < bytes.length; i++) s += String.fromCharCode(bytes[i]);
  return btoa(s);
}

function fromB64(b64: string): Uint8Array {
  const s = atob(b64);
  const out = new Uint8Array(s.length);
  for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
  return out;
}

async function deriveKey(
  passphrase: string,
  salt: Uint8Array,
  iterations: number
): Promise<CryptoKey> {
  const enc = new TextEncoder();
  const base = await crypto.subtle.importKey(
    'raw', enc.encode(passphrase), 'PBKDF2', false, ['deriveKey']
  );
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

/** Encrypt a JSON-serialisable value with a passphrase. */
export async function encryptJson(value: unknown, passphrase: string): Promise<EncryptedBlob> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const key = await deriveKey(passphrase, salt, PBKDF2_ITERATIONS);
  const plaintext = new TextEncoder().encode(JSON.stringify(value));
  const cipher = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: iv as unknown as BufferSource }, key, plaintext
  );
  return {
    magic: MAGIC,
    kdf: 'PBKDF2-SHA256',
    iterations: PBKDF2_ITERATIONS,
    saltB64: toB64(salt),
    ivB64: toB64(iv),
    cipherB64: toB64(cipher),
  };
}

export class WrongPassphraseError extends Error {
  constructor() { super('Wrong password, or this file has been altered.'); }
}

/** Decrypt. Throws WrongPassphraseError on a bad passphrase or tampering. */
export async function decryptJson<T = unknown>(blob: EncryptedBlob, passphrase: string): Promise<T> {
  if (!blob || blob.magic !== MAGIC) {
    throw new Error('This is not a Kosha backup file.');
  }
  const salt = fromB64(blob.saltB64);
  const iv = fromB64(blob.ivB64);
  const key = await deriveKey(passphrase, salt, blob.iterations || PBKDF2_ITERATIONS);
  try {
    const plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      key,
      fromB64(blob.cipherB64) as unknown as BufferSource
    );
    return JSON.parse(new TextDecoder().decode(plain)) as T;
  } catch {
    throw new WrongPassphraseError();
  }
}

export function isEncryptedBlob(v: unknown): v is EncryptedBlob {
  return !!v && typeof v === 'object' && (v as EncryptedBlob).magic === MAGIC;
}

/** A 256-bit recovery key, shown once, never stored in recoverable form. */
export function generateRecoveryKey(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  const hex = Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join('').toUpperCase();
  return (hex.match(/.{1,5}/g) || []).join('-');
}

/** Mask a PAN for storage and display: ABCDE1234F -> ABCDE****F */
export function maskPan(pan: string): string {
  const p = (pan || '').trim().toUpperCase();
  if (p.length !== 10) return p ? '**********' : '';
  return `${p.slice(0, 5)}****${p.slice(9)}`;
}
