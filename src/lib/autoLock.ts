/** Skip vault lock while a system file/folder picker is on top of the WebView. */
let pickerDepth = 0;

export function beginNativePicker(): void {
  pickerDepth += 1;
}

export function endNativePicker(): void {
  pickerDepth = Math.max(0, pickerDepth - 1);
}

export function isNativePickerOpen(): boolean {
  return pickerDepth > 0;
}

/** How long the vault stays unlocked after the app leaves the foreground. */
export const AUTO_LOCK_MS = 30_000;

function fileInputFrom(target: EventTarget | null): HTMLInputElement | null {
  const node = target instanceof Element ? target : null;
  if (!node) return null;
  if (node instanceof HTMLInputElement && node.type === 'file') return node;
  const nested = node.closest?.('input[type="file"]');
  if (nested instanceof HTMLInputElement) return nested;
  const label = node.closest?.('label');
  const owned = label?.querySelector('input[type="file"]');
  return owned instanceof HTMLInputElement ? owned : null;
}

/** Document/CAS file inputs open the system browser. Treat that as still in the app. */
export function installFileInputPickerGuard(): () => void {
  const onClick = (event: Event) => {
    const input = fileInputFrom(event.target);
    if (!input || input.dataset.gudPicker === '1') return;
    input.dataset.gudPicker = '1';
    beginNativePicker();
    let ended = false;
    const finish = () => {
      if (ended) return;
      ended = true;
      delete input.dataset.gudPicker;
      endNativePicker();
      input.removeEventListener('change', finish);
      input.removeEventListener('cancel', finish);
      document.removeEventListener('visibilitychange', onVisible);
    };
    const onVisible = () => {
      if (document.visibilityState === 'visible') window.setTimeout(finish, 400);
    };
    input.addEventListener('change', finish);
    input.addEventListener('cancel', finish);
    document.addEventListener('visibilitychange', onVisible);
  };
  document.addEventListener('click', onClick, true);
  return () => document.removeEventListener('click', onClick, true);
}
