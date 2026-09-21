/**
 * Play Store review prompt.
 *
 * Clock starts on first app open (seeded from the trial start when present).
 * After 7 days, opening an unlocked vault may show a pre-prompt. Submit hands
 * off to Google Play In-App Review so the rating lands on the Play listing.
 * Dismiss snoozes 2 days. Family → Rate on Google Play is always available.
 *
 * GrowUrDhan never collects the rating. Play does.
 */
import { isNativeShell, nativeCommand } from './license';

export const FEEDBACK_FIRST_DELAY_DAYS = 7;
export const FEEDBACK_SNOOZE_DAYS = 2;
export const FEEDBACK_MAX_PROMPTS = 6;
export const PLAY_PACKAGE_ID = 'in.growurdhan.app';
export const PLAY_LISTING_URL = `https://play.google.com/store/apps/details?id=${PLAY_PACKAGE_ID}`;

const LS_STATE = 'gud.feedback.state';
const memoryStore = new Map<string, string>();
let sessionClaimed = false;

export interface FeedbackState {
  firstSeenAt: string | null;
  snoozeUntil: string | null;
  completedAt: string | null;
  promptCount: number;
}

export const EMPTY_FEEDBACK: FeedbackState = {
  firstSeenAt: null,
  snoozeUntil: null,
  completedAt: null,
  promptCount: 0,
};

export interface PlayReviewResult {
  launched: boolean;
  fallback: boolean;
}

function lsGet(key: string): string | null {
  try {
    if (typeof localStorage !== 'undefined') {
      const v = localStorage.getItem(key);
      if (v != null) return v;
    }
  } catch {
    /* private mode */
  }
  return memoryStore.get(key) ?? null;
}

function lsSet(key: string, value: string): void {
  memoryStore.set(key, value);
  try {
    if (typeof localStorage !== 'undefined') localStorage.setItem(key, value);
  } catch {
    /* private mode */
  }
}

function isoDaysFrom(start: Date, days: number): string {
  return new Date(start.getTime() + days * 86400000).toISOString();
}

function daysBetween(fromIso: string, now: Date): number {
  const start = Date.parse(fromIso);
  if (!Number.isFinite(start)) return 0;
  return (now.getTime() - start) / 86400000;
}

export function loadFeedbackState(): FeedbackState {
  const raw = lsGet(LS_STATE);
  if (!raw) return { ...EMPTY_FEEDBACK };
  try {
    const parsed = JSON.parse(raw) as Partial<FeedbackState>;
    return {
      firstSeenAt: typeof parsed.firstSeenAt === 'string' ? parsed.firstSeenAt : null,
      snoozeUntil: typeof parsed.snoozeUntil === 'string' ? parsed.snoozeUntil : null,
      completedAt: typeof parsed.completedAt === 'string' ? parsed.completedAt : null,
      promptCount: Number.isFinite(parsed.promptCount) ? Math.max(0, Number(parsed.promptCount)) : 0,
    };
  } catch {
    return { ...EMPTY_FEEDBACK };
  }
}

export function persistFeedbackState(state: FeedbackState): FeedbackState {
  lsSet(LS_STATE, JSON.stringify(state));
  return state;
}

export function seedFirstSeen(
  state: FeedbackState,
  trialStartedAt: string | null,
  now = new Date(),
): FeedbackState {
  if (state.firstSeenAt) return state;
  return {
    ...state,
    firstSeenAt: trialStartedAt || now.toISOString(),
  };
}

/** Start the 7-day clock on first app open, even before a vault exists. */
export function seedFeedbackClock(trialStartedAt: string | null, now = new Date()): FeedbackState {
  return persistFeedbackState(seedFirstSeen(loadFeedbackState(), trialStartedAt, now));
}

export function shouldShowFeedbackPrompt(state: FeedbackState, now = new Date()): boolean {
  if (state.completedAt) return false;
  if (state.promptCount >= FEEDBACK_MAX_PROMPTS) return false;
  if (!state.firstSeenAt) return false;
  if (daysBetween(state.firstSeenAt, now) < FEEDBACK_FIRST_DELAY_DAYS) return false;
  if (state.snoozeUntil && Date.parse(state.snoozeUntil) > now.getTime()) return false;
  return true;
}

export function snoozeFeedback(state: FeedbackState, now = new Date()): FeedbackState {
  return {
    ...state,
    promptCount: state.promptCount + 1,
    snoozeUntil: isoDaysFrom(now, FEEDBACK_SNOOZE_DAYS),
  };
}

export function completeFeedback(state: FeedbackState, now = new Date()): FeedbackState {
  return {
    ...state,
    completedAt: now.toISOString(),
    snoozeUntil: null,
  };
}

export function dismissFeedbackPrompt(now = new Date()): FeedbackState {
  return persistFeedbackState(snoozeFeedback(loadFeedbackState(), now));
}

export function markFeedbackCompleted(now = new Date()): FeedbackState {
  return persistFeedbackState(completeFeedback(loadFeedbackState(), now));
}

/** One auto-prompt per process so a remount cannot stack sheets. */
export function claimFeedbackPrompt(state: FeedbackState, now = new Date()): boolean {
  if (sessionClaimed) return false;
  if (!shouldShowFeedbackPrompt(state, now)) return false;
  sessionClaimed = true;
  return true;
}

export function resetFeedbackSession(): void {
  sessionClaimed = false;
}

function openListingInBrowser(): void {
  try {
    const opened = window.open(PLAY_LISTING_URL, '_blank', 'noopener,noreferrer');
    if (!opened) window.location.assign(PLAY_LISTING_URL);
  } catch {
    try {
      window.location.assign(PLAY_LISTING_URL);
    } catch {
      /* ignore */
    }
  }
}

export async function launchPlayReview(): Promise<PlayReviewResult> {
  if (isNativeShell()) {
    try {
      const remote = await nativeCommand<Partial<PlayReviewResult>>('play_review');
      return {
        launched: Boolean(remote.launched),
        fallback: Boolean(remote.fallback) || !remote.launched,
      };
    } catch {
      try {
        await nativeCommand('play_open_listing');
      } catch {
        openListingInBrowser();
      }
      return { launched: false, fallback: true };
    }
  }
  openListingInBrowser();
  return { launched: false, fallback: true };
}

/** User-initiated path (Family). Stops the auto-prompt. */
export async function rateOnGooglePlay(): Promise<PlayReviewResult> {
  markFeedbackCompleted();
  sessionClaimed = true;
  return launchPlayReview();
}
