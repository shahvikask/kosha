/**
 * Locked GrowUrDhan backup.
 *
 * Binary AES-GCM (not pretty JSON). Keyed from the vault email + password,
 * so there is no second backup passphrase to remember. The same bytes work
 * in the browser, the Windows app, and a future Android WebView.
 */
import { FamilyMember, Holding, LedgerEvent, FamilyGoal, DematOrFolioAccount } from '../types';
import { VaultAccount, verifySecret, unpackHash, normaliseAnswer } from './auth';
import { CasLatestState } from './casPython';
import { maskPan } from './vault';

export const BACKUP_KIND = 'GUDVAULT';
export const BACKUP_VERSION = 2;
const MAGIC = 'GUDVLT01';
const PBKDF2_ITERATIONS = 600_000;
const SALT_BYTES = 16;
const IV_BYTES = 12;

export interface VaultBackup {
  kind: typeof BACKUP_KIND;
  version: number;
  exportedAt: string;
  account: VaultAccount;
  familyMembers: FamilyMember[];
  holdings: Holding[];
  ledgerEvents: LedgerEvent[];
  accounts: DematOrFolioAccount[];
  goals: FamilyGoal[];
  casLatest: CasLatestState | null;
}

export class BackupError extends Error {}

function keyMaterial(email: string, password: string): string {
  return `GrowUrDhan\n${email.trim().toLowerCase()}\n${password}`;
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

async function deriveKey(passphrase: string, salt: Uint8Array, iterations: number): Promise<CryptoKey> {
  const base = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey'],
  );
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations, hash: 'SHA-256' },
    base,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt'],
  );
}

export function isBinaryBackup(bytes: Uint8Array): boolean {
  if (bytes.length < 40) return false;
  return new TextDecoder().decode(bytes.slice(0, 8)) === MAGIC;
}

export function looksLikePlainJsonBackup(text: string): boolean {
  const t = text.trim();
  return t.startsWith('{') && (t.includes('"familyMembers"') || t.includes('"holdings"'));
}

/** Encrypt a vault snapshot. Bytes look like noise in a text editor after the magic. */
export async function packBackup(payload: VaultBackup, email: string, password: string): Promise<Uint8Array> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
  const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
  const key = await deriveKey(keyMaterial(email, password), salt, PBKDF2_ITERATIONS);
  const body = new TextEncoder().encode(JSON.stringify(payload));
  const cipher = new Uint8Array(
    await crypto.subtle.encrypt({ name: 'AES-GCM', iv: iv as unknown as BufferSource }, key, body),
  );
  const out = new Uint8Array(8 + 4 + SALT_BYTES + IV_BYTES + cipher.length);
  out.set(new TextEncoder().encode(MAGIC), 0);
  new DataView(out.buffer).setUint32(8, PBKDF2_ITERATIONS, false);
  out.set(salt, 12);
  out.set(iv, 28);
  out.set(cipher, 40);
  return out;
}

export async function unpackBackup(bytes: Uint8Array, email: string, password: string): Promise<VaultBackup> {
  if (isBinaryBackup(bytes)) return unpackBinary(bytes, email, password);
  const text = new TextDecoder().decode(bytes);
  if (looksLikePlainJsonBackup(text)) {
    throw new BackupError('This file is an old unlocked backup. Export a new locked copy from Family.');
  }
  return unpackLegacyJson(text, password);
}

async function unpackBinary(bytes: Uint8Array, email: string, password: string): Promise<VaultBackup> {
  const iterations = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength).getUint32(8, false);
  const salt = bytes.slice(12, 28);
  const iv = bytes.slice(28, 40);
  const cipher = bytes.slice(40);
  const key = await deriveKey(keyMaterial(email, password), salt, iterations || PBKDF2_ITERATIONS);
  try {
    const plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      key,
      cipher as unknown as BufferSource,
    );
    const data = JSON.parse(new TextDecoder().decode(plain)) as VaultBackup;
    if (!data || data.kind !== BACKUP_KIND || !data.account) {
      throw new BackupError('That file is not a GrowUrDhan backup.');
    }
    return data;
  } catch (e) {
    if (e instanceof BackupError) throw e;
    throw new BackupError('Email or password does not match this backup.');
  }
}

async function unpackLegacyJson(text: string, password: string): Promise<VaultBackup> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new BackupError('That file is not a GrowUrDhan backup.');
  }
  const blob = parsed as { magic?: string; saltB64?: string; ivB64?: string; cipherB64?: string; iterations?: number };
  if (blob.magic !== 'KOSHA1' || !blob.cipherB64) {
    throw new BackupError('That file is not a GrowUrDhan backup.');
  }
  const salt = fromB64(blob.saltB64 || '');
  const iv = fromB64(blob.ivB64 || '');
  const key = await deriveKey(password, salt, blob.iterations || PBKDF2_ITERATIONS);
  try {
    const plain = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: iv as unknown as BufferSource },
      key,
      fromB64(blob.cipherB64) as unknown as BufferSource,
    );
    const inner = JSON.parse(new TextDecoder().decode(plain)) as Partial<VaultBackup> & {
      familyMembers?: FamilyMember[];
      holdings?: Holding[];
      ledgerEvents?: LedgerEvent[];
    };
    if (!inner.account) {
      throw new BackupError('This older backup has no login. Create the vault on this computer first, then export a new locked file.');
    }
    return {
      kind: BACKUP_KIND,
      version: inner.version || 1,
      exportedAt: inner.exportedAt || new Date().toISOString(),
      account: inner.account,
      familyMembers: inner.familyMembers || [],
      holdings: inner.holdings || [],
      ledgerEvents: inner.ledgerEvents || [],
      accounts: inner.accounts || [],
      goals: inner.goals || [],
      casLatest: inner.casLatest || null,
    };
  } catch (e) {
    if (e instanceof BackupError) throw e;
    throw new BackupError('Email or password does not match this backup.');
  }
}

export interface RestoreIdentity {
  email: string;
  password: string;
  pan?: string;
  dateOfBirth?: string;
  q1Id?: string;
  q1Answer?: string;
  q2Id?: string;
  q2Answer?: string;
  /** New device: PAN, DOB and both security answers must match. */
  strict: boolean;
}

export async function verifyRestoreIdentity(backup: VaultBackup, id: RestoreIdentity): Promise<string | null> {
  const a = backup.account;
  if (id.email.trim().toLowerCase() !== a.email.toLowerCase()) {
    return 'Email or password does not match this backup.';
  }
  const pwOk = await verifySecret(id.password, a.passwordSalt, a.passwordHash, a.passwordIterations);
  if (!pwOk) return 'Email or password does not match this backup.';
  if (!id.strict) return null;

  const pan = (id.pan || '').trim().toUpperCase();
  const storedPan = (a.panForStatements || '').trim().toUpperCase();
  if (storedPan) {
    if (pan !== storedPan) return 'PAN does not match this backup.';
  } else if (a.panMasked) {
    if (maskPan(pan) !== a.panMasked) return 'PAN does not match this backup.';
  } else {
    return 'This backup has no PAN. It cannot be opened on a new device.';
  }
  if (!id.dateOfBirth || id.dateOfBirth !== (a.dateOfBirth || '')) {
    return 'Date of birth does not match this backup.';
  }
  if (!id.q1Id || !id.q2Id || id.q1Id !== a.q1Id || id.q2Id !== a.q2Id) {
    return 'Security questions do not match this backup.';
  }
  const p1 = unpackHash(a.q1Hash);
  const p2 = unpackHash(a.q2Hash);
  if (!p1 || !p2) return 'Security questions are missing from this backup.';
  const ok1 = await verifySecret(normaliseAnswer(id.q1Answer || ''), p1.salt, p1.hash, p1.iterations);
  const ok2 = await verifySecret(normaliseAnswer(id.q2Answer || ''), p2.salt, p2.hash, p2.iterations);
  if (!ok1 || !ok2) return 'Security answers do not match this backup.';
  return null;
}

export function suggestedBackupName(now = new Date()): string {
  return `growurdhan_${now.toISOString().slice(0, 10)}.kosha`;
}

/** Exposed for tests that inspect the ciphertext. */
export function backupToBase64(bytes: Uint8Array): string {
  return toB64(bytes);
}
