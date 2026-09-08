import { ThemeName } from './holdingView';

const CLASS = 'theme-light';
const STORAGE = 'kosha.theme';

export function applyTheme(theme: ThemeName): void {
  if (typeof document === 'undefined') return;
  const on = theme === 'light';
  document.documentElement.classList.toggle(CLASS, on);
  document.body.classList.toggle(CLASS, on);
}

export function readStoredTheme(): ThemeName | null {
  try {
    const v = localStorage.getItem(STORAGE);
    if (v === 'light' || v === 'dark') return v;
  } catch {
    /* ignore */
  }
  return null;
}

export function writeStoredTheme(theme: ThemeName): void {
  try {
    localStorage.setItem(STORAGE, theme);
  } catch {
    /* ignore */
  }
}
