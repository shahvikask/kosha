/** Local vault login. Password is hashed (PBKDF2); never stored in the clear. */

export const PASSWORD_MAX_AGE_DAYS = 180;
export const REMEMBER_EMAIL_KEY = 'kosha.remember_email';

export const SECURITY_QUESTIONS: { id: string; text: string }[] = [
  { id: 'maiden', text: "What is your mother's maiden name?" },
  { id: 'school', text: 'What was the name of your first school?' },
  { id: 'pet', text: 'What was the name of your first pet?' },
  { id: 'city', text: 'In which city were you born?' },
  { id: 'teacher', text: 'What was the name of your favourite teacher?' },
  { id: 'vehicle', text: 'What was your first car or bike?' },
];

const ITERATIONS = 210_000;
const SALT_BYTES = 16;

export interface VaultAccount {
  email: string;
  firstName: string;
  lastName: string;
  passwordSalt: string;
  passwordHash: string;
  passwordIterations: number;
  passwordChangedAt: string;
  panMasked?: string;
  panForStatements?: string;
  dateOfBirth?: string;
  q1Id: string;
  q1Hash: string;
  q2Id: string;
  q2Hash: string;
  createdAt: string;
}

export interface VaultAccountPublic {
  email: string;
  firstName: string;
  lastName: string;
  panMasked?: string;
  dateOfBirth?: string;
  passwordChangedAt: string;
  q1Id: string;
  q2Id: string;
}

export function toPublicAccount(a: VaultAccount): VaultAccountPublic {
  return {
    email: a.email,
    firstName: a.firstName,
    lastName: a.lastName,
    panMasked: a.panMasked,
    dateOfBirth: a.dateOfBirth,
    passwordChangedAt: a.passwordChangedAt,
    q1Id: a.q1Id,
    q2Id: a.q2Id,
  };
}

export function titleCaseName(raw: string): string {
  const t = (raw || '').trim().replace(/\s+/g, ' ');
  if (!t) return '';
  return t
    .split(' ')
    .map(w => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ');
}

export function passwordIssues(pw: string): string[] {
  const issues: string[] = [];
  if (pw.length < 8) issues.push('At least 8 characters');
  if (!/[A-Z]/.test(pw)) issues.push('One capital letter');
  if (!/[a-z]/.test(pw)) issues.push('One small letter');
  if (!/[0-9]/.test(pw)) issues.push('One number');
  if (!/[^A-Za-z0-9]/.test(pw)) issues.push('One special character');
  return issues;
}

export function isValidPan(pan: string): boolean {
  return /^[A-Z]{5}[0-9]{4}[A-Z]$/.test((pan || '').trim().toUpperCase());
}

export function isValidEmail(email: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test((email || '').trim());
}

export function normaliseAnswer(raw: string): string {
  return (raw || '').trim().toLowerCase().replace(/\s+/g, ' ');
}

export function needsPasswordRotation(changedAt: string, now = new Date()): boolean {
  const t = Date.parse(changedAt);
  if (!Number.isFinite(t)) return true;
  const days = (now.getTime() - t) / 86400000;
  return days >= PASSWORD_MAX_AGE_DAYS;
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

function same(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let x = 0;
  for (let i = 0; i < a.length; i++) x |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return x === 0;
}

async function pbkdf2(secret: string, salt: Uint8Array, iterations: number): Promise<string> {
  const base = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    'PBKDF2',
    false,
    ['deriveBits'],
  );
  const bits = await crypto.subtle.deriveBits(
    { name: 'PBKDF2', salt: salt as unknown as BufferSource, iterations, hash: 'SHA-256' },
    base,
    256,
  );
  return toB64(bits);
}

export async function hashSecret(secret: string): Promise<{ salt: string; hash: string; iterations: number }> {
  const salt = crypto.getRandomValues(new Uint8Array(SALT_BYTES));
  const hash = await pbkdf2(secret, salt, ITERATIONS);
  return { salt: toB64(salt), hash, iterations: ITERATIONS };
}

export function packHash(h: { salt: string; hash: string; iterations: number }): string {
  return `${h.salt}:${h.hash}:${h.iterations}`;
}

export function unpackHash(packed: string): { salt: string; hash: string; iterations: number } | null {
  const parts = (packed || '').split(':');
  if (parts.length !== 3) return null;
  return { salt: parts[0], hash: parts[1], iterations: Number(parts[2]) || ITERATIONS };
}

export async function verifySecret(
  secret: string,
  saltB64: string,
  hashB64: string,
  iterations: number,
): Promise<boolean> {
  const got = await pbkdf2(secret, fromB64(saltB64), iterations || ITERATIONS);
  return same(got, hashB64);
}

export function readRememberedEmail(): string {
  try {
    return localStorage.getItem(REMEMBER_EMAIL_KEY) || '';
  } catch {
    return '';
  }
}

export function writeRememberedEmail(email: string): void {
  try {
    localStorage.setItem(REMEMBER_EMAIL_KEY, email.trim().toLowerCase());
  } catch {
    /* ignore */
  }
}

export function clearRememberedEmail(): void {
  try {
    localStorage.removeItem(REMEMBER_EMAIL_KEY);
  } catch {
    /* ignore */
  }
}
