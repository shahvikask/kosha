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
export const SKU_GMAIL = 'gud_gmail';
export const SKU_GMAIL_UPGRADE = 'gud_gmail_upgrade';
export const SKU_SUPPORT = 'gud_support_annual';

export const PRICE_INR = {
  lifetime: 249,
  gmail: 499,
  gmailUpgrade: 250,
  supportAnnual: 199,
} as const;

export type LicencePlan = 'trial' | 'lifetime' | 'lifetime_support' | 'expired';

export interface LicenceState {
  plan: LicencePlan;
  trialStartedAt: string | null;
  trialEndsAt: string | null;
  daysLeft: number;
  lifetime: boolean;
  gmail: boolean;
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
  gmail: false,
  supportActive: false,
  supportRenewsAt: null,
  playAccountBound: false,
  sideloadBlocked: false,
  source: 'preview',
};

const LS_TRIAL = 'gud.licence.trialStartedAt';
const LS_LIFETIME = 'gud.licence.lifetime';
const LS_GMAIL = 'gud.licence.gmail';
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

const APP_FIRST = new Set([
  'cas_health',
  'cas_parse',
  'diag_log',
  'diag_status',
  'diag_set',
  'prices_refresh',
  'gmail_connect',
  'gmail_disconnect',
  'gmail_status',
  'gmail_search',
  'gmail_fetch',
  'gmail_advance',
]);

function redactArgs(args?: Record<string, unknown>): string {
  if (!args) return '';
  const parts: string[] = [];
  for (const [k, v] of Object.entries(args)) {
    if (v == null) continue;
    if (/password|bytesB64|pdfBase64|pan/i.test(k)) {
      parts.push(`${k}Chars=${typeof v === 'string' ? v.length : 0}`);
    } else if (typeof v === 'string') {
      parts.push(`${k}=${v.slice(0, 80)}`);
    } else if (typeof v === 'number' || typeof v === 'boolean') {
      parts.push(`${k}=${String(v)}`);
    } else {
      parts.push(`${k}=${typeof v}`);
    }
  }
  return parts.join(' ');
}

function summarize(value: unknown): string {
  if (value == null) return 'null';
  if (typeof value !== 'object') return String(value).slice(0, 240);
  const o = value as Record<string, unknown>;
  const bits: string[] = [];
  for (const k of ['ok', 'error', 'engine', 'path', 'privatePath', 'writeError', 'code', 'lifetime', 'integrity']) {
    if (o[k] != null) bits.push(`${k}=${String(o[k]).slice(0, 240)}`);
  }
  if (typeof o.payload === 'string') {
    try {
      const parsed = JSON.parse(o.payload) as Record<string, unknown>;
      bits.push(
        `payload.ok=${String(parsed.ok)} code=${String(parsed.code ?? '')} fileType=${String(parsed.fileType ?? '')} error=${String(parsed.error ?? '').slice(0, 240)}`
      );
    } catch {
      bits.push(`payloadChars=${o.payload.length}`);
    }
  }
  return bits.join(' ').slice(0, 700) || 'object';
}

const diagQueue: string[] = [];
let diagPump: Promise<void> | null = null;

function pumpDiag() {
  if (diagPump || !isNativeShell()) return;
  diagPump = (async () => {
    const { invoke } = await import('@tauri-apps/api/core');
    while (diagQueue.length) {
      const next = diagQueue.shift();
      if (!next) continue;
      try {
        await invoke('diag_log', { line: next });
      } catch {
        diagQueue.length = 0;
        break;
      }
    }
  })().finally(() => {
    diagPump = null;
    if (diagQueue.length) pumpDiag();
  });
}

function diagNote(cmd: string, line: string) {
  if (cmd === 'diag_log' || !line) return;
  diagQueue.push(line.slice(0, 1800));
  if (diagQueue.length > 40) diagQueue.splice(0, diagQueue.length - 40);
  pumpDiag();
}

/**
 * Vault and Play stay plugin-first.
 * cas_health, cas_parse, diag_*, and prices_refresh try the app command first
 * (Rust forwards to the Android plugin). Fall back only when that channel is
 * not registered.
 */
export async function nativeCommand<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke } = await import('@tauri-apps/api/core');
  const hint = redactArgs(args);
  const appFirst = APP_FIRST.has(cmd);
  const channels = appFirst ? [cmd, `plugin:growurdhan|${cmd}`] : [`plugin:growurdhan|${cmd}`, cmd];
  let last: unknown;
  for (let i = 0; i < channels.length; i++) {
    const channel = channels[i];
    try {
      const result = await invoke<T>(channel, args);
      diagNote(cmd, `js ${cmd} via ${channel} ok ${summarize(result)} ${hint}`.trim());
      return result;
    } catch (e) {
      last = e;
      const msg = invokeErrorMessage(e);
      diagNote(cmd, `js ${cmd} via ${channel} FAIL ${msg.slice(0, 500)} ${hint}`.trim());
      // Only switch channels when this channel is missing. A real Python error
      // (FileNotFoundError, bad password, parse failure) must be shown as-is.
      // "Plugin not found" is Tauri's missing-channel error, not a Python error.
      const channelMissing =
        !msg ||
        /unknown command|does not exist|forbidden/i.test(msg) ||
        /not allowed/i.test(msg) ||
        /plugin not found/i.test(msg);
      const tryNext = i === 0 && channelMissing;
      if (!tryNext) throw e instanceof Error ? e : new Error(msg || 'command failed');
    }
  }
  const msg = invokeErrorMessage(last);
  throw last instanceof Error ? last : new Error(msg || 'command failed');
}

function readPreviewState(now = new Date()): LicenceState {
  let trialStartedAt = localStorage.getItem(LS_TRIAL);
  if (!trialStartedAt) {
    trialStartedAt = now.toISOString();
    localStorage.setItem(LS_TRIAL, trialStartedAt);
  }
  const lifetime = localStorage.getItem(LS_LIFETIME) === '1';
  const gmail = localStorage.getItem(LS_GMAIL) === '1';
  const supportUntil = localStorage.getItem(LS_SUPPORT_UNTIL);
  const supportActive = Boolean(supportUntil && Date.parse(supportUntil) > now.getTime());
  const derived = derivePlan({ lifetime, supportActive, trialStartedAt, now });
  return {
    ...EMPTY_LICENCE,
    ...derived,
    trialStartedAt,
    lifetime,
    gmail,
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
      const ownsManual = Boolean(remote.lifetime);
      const ownsGmail = Boolean(remote.gmail);
      const lifetime = ownsManual || ownsGmail;
      const gmail = ownsGmail;
      const supportActive = Boolean(remote.supportActive);
      const trialStartedAt = remote.trialStartedAt || now.toISOString();
      const derived = derivePlan({ lifetime, supportActive, trialStartedAt, now });
      const sideloadBlocked = remote.integrity === false && !lifetime;
      return {
        ...EMPTY_LICENCE,
        ...derived,
        trialStartedAt,
        lifetime,
        gmail,
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
  const current = await loadLicence();
  if (!canPurchaseLifetime(current)) {
    throw new Error('The ₹249 licence is not available. The Gmail licence already includes perpetual access.');
  }
  if (isTestApk() || !isNativeShell()) {
    localStorage.setItem(LS_LIFETIME, '1');
    localStorage.removeItem(LS_GMAIL);
    return loadLicence();
  }
  await nativeCommand('play_purchase', { sku: SKU_LIFETIME });
  return loadLicence();
}

export async function purchaseGmailPlan(): Promise<LicenceState> {
  const current = await loadLicence();
  if (current.gmail) return current;
  if (!current.lifetime && !canPurchaseGmailDirect(current)) {
    throw new Error('The ₹499 Gmail licence is not available after the ₹249 licence. Use the ₹250 upgrade.');
  }
  const upgrade = current.lifetime;
  if (!upgrade && !canPurchaseGmailDirect(current)) {
    throw new Error('The ₹499 Gmail licence is not available on this account.');
  }
  if (upgrade && !canPurchaseGmailUpgrade(current)) {
    throw new Error('The ₹250 Gmail upgrade is only for an account that already holds the ₹249 licence.');
  }
  if (isTestApk() || !isNativeShell()) {
    localStorage.setItem(LS_LIFETIME, '1');
    localStorage.setItem(LS_GMAIL, '1');
    return loadLicence();
  }
  await nativeCommand('play_purchase', { sku: upgrade ? SKU_GMAIL_UPGRADE : SKU_GMAIL });
  return loadLicence();
}

export async function purchaseSupport(): Promise<LicenceState> {
  const current = await loadLicence();
  if (!canPurchaseSupport(current)) {
    throw new Error('Buy a perpetual licence first. The annual service cannot be purchased on its own.');
  }
  if (isTestApk() || !isNativeShell()) {
    const until = new Date();
    until.setFullYear(until.getFullYear() + 1);
    localStorage.setItem(LS_SUPPORT_UNTIL, until.toISOString());
    return loadLicence();
  }
  await nativeCommand('play_purchase', { sku: SKU_SUPPORT, offer: 'subscription' });
  return loadLicence();
}

export async function restorePurchases(): Promise<LicenceState> {
  if (isNativeShell() && !isTestApk()) {
    await nativeCommand('play_restore', {});
  }
  return loadLicence();
}

export function inTrial(state: LicenceState): boolean {
  return state.plan === 'trial' && state.daysLeft > 0;
}

/** New features stay on during the trial and while the annual subscription is paid. */
export function enhancementsEnabled(state: LicenceState): boolean {
  if (inTrial(state)) return true;
  return state.supportActive && state.lifetime;
}

export function appUnlocked(state: LicenceState): boolean {
  if (state.sideloadBlocked && !state.lifetime) return false;
  return state.lifetime || state.plan === 'trial' || state.plan === 'lifetime_support';
}

/** Annual service is sold only after a perpetual licence is on this Play account. */
export function canPurchaseSupport(state: LicenceState): boolean {
  return state.lifetime && !state.sideloadBlocked && !state.supportActive;
}

/** ₹249 is hidden once any perpetual product, including the ₹499 Gmail licence, is owned. */
export function canPurchaseLifetime(state: LicenceState): boolean {
  return !state.lifetime && !state.gmail && !state.sideloadBlocked;
}

/** ₹499 is only for a first purchase. After ₹249, the ₹250 upgrade is the only Gmail product. */
export function canPurchaseGmailDirect(state: LicenceState): boolean {
  return !state.lifetime && !state.gmail && !state.sideloadBlocked;
}

export function canPurchaseGmailUpgrade(state: LicenceState): boolean {
  return state.lifetime && !state.gmail && !state.sideloadBlocked;
}

/**
 * Trial includes Gmail. After the trial, Gmail stays off on the ₹249 licence
 * until gud_gmail_upgrade is owned, and on when gud_gmail or the upgrade is owned.
 */
export function gmailSyncAllowed(state: LicenceState): boolean {
  if (state.sideloadBlocked && !state.lifetime) return false;
  if (inTrial(state)) return true;
  return state.lifetime && state.gmail;
}
