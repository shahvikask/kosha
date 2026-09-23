/**
 * GrowUrDhan commercial licence.
 *
 * Perpetual access is a one-time Google Play product. Annual support is a
 * Play subscription. A 30-day trial is started on a genuine Play install
 * (native) or a device-bound clock (web preview / device-test APK).
 *
 * VITE_GUD_TEST_APK=1 is set only on the GUD-Android-APK branch so a
 * sideloaded debug APK can be tested on a phone. The Play AAB on
 * GUD-Android does not set this flag and still rejects extracted APKs.
 */
export const TRIAL_DAYS = 30;

export const SKU_LIFETIME = 'gud_lifetime';
export const SKU_SUPPORT = 'gud_support_annual';

export const PRICE_INR = {
  lifetime: 249,
  supportAnnual: 299,
} as const;

export type LicencePlan = 'trial' | 'lifetime' | 'lifetime_support' | 'expired';

export interface LicenceState {
  plan: LicencePlan;
  trialStartedAt: string | null;
  trialEndsAt: string | null;
  daysLeft: number;
  lifetime: boolean;
  supportActive: boolean;
  supportRenewsAt: string | null;
  playAccountBound: boolean;
  sideloadBlocked: boolean;
  source: 'play' | 'preview';
}

export const EMPTY_LICENCE: LicenceState = {
  plan: 'trial',
  trialStartedAt: null,
  trialEndsAt: null,
  daysLeft: TRIAL_DAYS,
  lifetime: false,
  supportActive: false,
  supportRenewsAt: null,
  playAccountBound: false,
  sideloadBlocked: false,
  source: 'preview',
};

const LS_TRIAL = 'gud.licence.trialStartedAt';
const LS_LIFETIME = 'gud.licence.lifetime';
const LS_SUPPORT_UNTIL = 'gud.licence.supportUntil';

function isoDaysFrom(startIso: string, days: number): string {
  const t = Date.parse(startIso);
  if (!Number.isFinite(t)) return startIso;
  return new Date(t + days * 86400000).toISOString();
}

export function isTestApk(): boolean {
  try {
    return import.meta.env.VITE_GUD_TEST_APK === '1';
  } catch {
    return false;
  }
}

export function daysRemaining(trialStartedAt: string, now = new Date()): number {
  const start = Date.parse(trialStartedAt);
  if (!Number.isFinite(start)) return 0;
  const end = start + TRIAL_DAYS * 86400000;
  return Math.max(0, Math.ceil((end - now.getTime()) / 86400000));
}

export function derivePlan(input: {
  lifetime: boolean;
  supportActive: boolean;
  trialStartedAt: string | null;
  now?: Date;
}): Pick<LicenceState, 'plan' | 'daysLeft' | 'trialEndsAt'> {
  const now = input.now || new Date();
  if (input.lifetime && input.supportActive) {
    return { plan: 'lifetime_support', daysLeft: 0, trialEndsAt: null };
  }
  if (input.lifetime) {
    return { plan: 'lifetime', daysLeft: 0, trialEndsAt: null };
  }
  if (input.trialStartedAt) {
    const left = daysRemaining(input.trialStartedAt, now);
    const trialEndsAt = isoDaysFrom(input.trialStartedAt, TRIAL_DAYS);
    if (left > 0) return { plan: 'trial', daysLeft: left, trialEndsAt };
  }
  return { plan: 'expired', daysLeft: 0, trialEndsAt: input.trialStartedAt ? isoDaysFrom(input.trialStartedAt, TRIAL_DAYS) : null };
}

export function formatInr(n: number): string {
  return `₹${n.toLocaleString('en-IN')}`;
}

export function isNativeShell(): boolean {
  return typeof window !== 'undefined' && '__TAURI_INTERNALS__' in window;
}

function invokeErrorMessage(e: unknown): string {
  if (e instanceof Error && e.message) return e.message;
  if (typeof e === 'string' && e) return e;
  if (e && typeof e === 'object') {
    const o = e as Record<string, unknown>;
    if (typeof o.message === 'string' && o.message) return o.message;
    if (typeof o.error === 'string' && o.error) return o.error;
  }
  return '';
}

/** Plugin channel first (Android Keystore + Play Billing), then app commands. */
export async function nativeCommand<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke } = await import('@tauri-apps/api/core');
  try {
    return await invoke<T>(`plugin:growurdhan|${cmd}`, args);
  } catch (e) {
    const msg = invokeErrorMessage(e);
    if (msg && !/not found|unknown command|does not exist|forbidden/i.test(msg)) {
      throw e instanceof Error ? e : new Error(msg);
    }
    return invoke<T>(cmd, args);
  }
}

function readPreviewState(now = new Date()): LicenceState {
  let trialStartedAt = localStorage.getItem(LS_TRIAL);
  if (!trialStartedAt) {
    trialStartedAt = now.toISOString();
    localStorage.setItem(LS_TRIAL, trialStartedAt);
  }
  const lifetime = localStorage.getItem(LS_LIFETIME) === '1';
  const supportUntil = localStorage.getItem(LS_SUPPORT_UNTIL);
  const supportActive = Boolean(supportUntil && Date.parse(supportUntil) > now.getTime());
  const derived = derivePlan({ lifetime, supportActive, trialStartedAt, now });
  return {
    ...EMPTY_LICENCE,
    ...derived,
    trialStartedAt,
    lifetime,
    supportActive,
    supportRenewsAt: supportUntil,
    playAccountBound: lifetime,
    source: 'preview',
  };
}

export async function loadLicence(now = new Date()): Promise<LicenceState> {
  if (isTestApk()) {
    return { ...readPreviewState(now), sideloadBlocked: false, source: 'preview' };
  }
  if (isNativeShell()) {
    try {
      const remote = await nativeCommand<Partial<LicenceState> & { ok?: boolean; integrity?: boolean }>('play_licence_status');
      const lifetime = Boolean(remote.lifetime);
      const supportActive = Boolean(remote.supportActive);
      const trialStartedAt = remote.trialStartedAt || now.toISOString();
      const derived = derivePlan({ lifetime, supportActive, trialStartedAt, now });
      const sideloadBlocked = remote.integrity === false && !lifetime;
      return {
        ...EMPTY_LICENCE,
        ...derived,
        trialStartedAt,
        lifetime,
        supportActive,
        supportRenewsAt: remote.supportRenewsAt || null,
        playAccountBound: lifetime || supportActive,
        sideloadBlocked,
        source: 'play',
      };
    } catch {
      return { ...EMPTY_LICENCE, plan: 'expired', daysLeft: 0, source: 'play', sideloadBlocked: true };
    }
  }
  return readPreviewState(now);
}

export async function purchaseLifetime(): Promise<LicenceState> {
  if (isTestApk() || !isNativeShell()) {
    localStorage.setItem(LS_LIFETIME, '1');
    return loadLicence();
  }
  await nativeCommand('play_purchase', { sku: SKU_LIFETIME });
  return loadLicence();
}

export async function purchaseSupport(): Promise<LicenceState> {
  const current = await loadLicence();
  if (!canPurchaseSupport(current)) {
    throw new Error('Buy the ₹249 lifetime licence first. Support cannot be purchased on its own.');
  }
  if (isTestApk() || !isNativeShell()) {
    const until = new Date();
    until.setFullYear(until.getFullYear() + 1);
    localStorage.setItem(LS_SUPPORT_UNTIL, until.toISOString());
    return loadLicence();
  }
  await nativeCommand('play_purchase', { sku: SKU_SUPPORT, offer: 'standalone' });
  return loadLicence();
}

export async function restorePurchases(): Promise<LicenceState> {
  if (isNativeShell() && !isTestApk()) {
    await nativeCommand('play_restore', {});
  }
  return loadLicence();
}

export function enhancementsEnabled(state: LicenceState): boolean {
  return state.supportActive && (state.lifetime || state.plan === 'lifetime_support');
}

export function appUnlocked(state: LicenceState): boolean {
  if (state.sideloadBlocked && !state.lifetime) return false;
  return state.lifetime || state.plan === 'trial' || state.plan === 'lifetime_support';
}

/** Support is sold only after the perpetual licence is on this Play account. */
export function canPurchaseSupport(state: LicenceState): boolean {
  return state.lifetime && !state.sideloadBlocked;
}
