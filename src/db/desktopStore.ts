import { BlobStore, IdbBlobStore } from './sqliteWasmDriver';

export function isDesktop(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
}

/** `%LOCALAPPDATA%\GrowUrDhan\vault.db` on Windows (localDataDir + GrowUrDhan). */
export class TauriFileBlobStore implements BlobStore {
  private pathPromise: Promise<string> | null = null;

  private async vaultPath(): Promise<string> {
    if (!this.pathPromise) {
      this.pathPromise = (async () => {
        const { localDataDir, join } = await import('@tauri-apps/api/path');
        const { mkdir, exists } = await import('@tauri-apps/plugin-fs');
        const dir = await join(await localDataDir(), 'GrowUrDhan');
        if (!(await exists(dir))) {
          await mkdir(dir, { recursive: true });
        }
        return await join(dir, 'vault.db');
      })();
    }
    return this.pathPromise;
  }

  async load(_key: string): Promise<Uint8Array | null> {
    const { readFile, exists } = await import('@tauri-apps/plugin-fs');
    const path = await this.vaultPath();
    if (!(await exists(path))) return null;
    const bytes = await readFile(path);
    return bytes && bytes.length ? new Uint8Array(bytes) : null;
  }

  async save(_key: string, bytes: Uint8Array): Promise<void> {
    const { writeFile } = await import('@tauri-apps/plugin-fs');
    const path = await this.vaultPath();
    await writeFile(path, bytes);
  }
}

export async function createVaultStore(): Promise<BlobStore> {
  if (isDesktop()) return new TauriFileBlobStore();
  return new IdbBlobStore();
}
