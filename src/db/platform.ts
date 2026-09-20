import { SqlDriver } from './driver';
import { SqliteWasmDriver } from './sqliteWasmDriver';
import { createVaultStore } from './encryptedStore';
import { isNativeShell } from '../lib/license';

export { isNativeShell };

export function errorMessage(err: unknown): string {
  if (err instanceof Error && err.message) return err.message;
  if (typeof err === 'string' && err.trim()) return err;
  return 'Could not open the vault.';
}

/** Same repository on web preview and on the Android WebView. */
export async function openVaultDriver(): Promise<SqlDriver> {
  const store = await createVaultStore();
  const d = new SqliteWasmDriver('kosha.db', store);
  await d.open();
  return d;
}

export function isAndroidApp(): boolean {
  if (!isNativeShell()) return false;
  return /android/i.test(navigator.userAgent) || !!(window as unknown as { __TAURI_INTERNALS__?: { metadata?: { target?: string } } }).__TAURI_INTERNALS__;
}
