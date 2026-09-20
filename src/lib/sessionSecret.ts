/**
 * Vault password kept in process memory for this unlock only.
 * Never written to localStorage, IndexedDB, or disk. Cleared on lock.
 * Scheduled backups need it to encrypt; they cannot run after the app is killed.
 */

let email: string | null = null;
let password: string | null = null;

export function rememberSessionSecret(nextEmail: string, nextPassword: string): void {
  email = nextEmail.trim().toLowerCase();
  password = nextPassword;
}

export function peekSessionSecret(): { email: string; password: string } | null {
  if (!email || !password) return null;
  return { email, password };
}

export function clearSessionSecret(): void {
  email = null;
  password = null;
}
