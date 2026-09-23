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
