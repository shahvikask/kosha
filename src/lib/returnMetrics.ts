/**
 * Holding return presentation.
 *
 * Kosha intentionally exposes one return metric in the UI: absolute return.
 * This module keeps the existing API shape used by the holding detail modal,
 * but no longer calculates or imports XIRR, CAGR, TVPI, or yield metrics.
 */
import { Holding, LedgerEvent } from '../types';
import { DerivedState } from './positions';
import { classifyHolding } from './taxonomy';
import { metricsOf } from './holdingView';
import { absoluteReturnPct, formatPct } from './growth';

export interface InstrumentProfile {
  key: string;
  primaryLabel: string;
  secondaryLabel: string;
  why: string;
}

export interface HoldingReturn {
  profile: InstrumentProfile;
  primaryPct: number | null;
  primaryText: string;
  secondaryPct: number | null;
  secondaryText: string;
}

const WHY = {
  SGB: 'Absolute return compares today’s value with the amount invested in this holding.',
  REIT: 'Absolute return compares today’s value with the amount invested in this holding.',
  SIF: 'Absolute return compares today’s value with the amount invested in this holding.',
  AIF: 'Absolute return compares today’s value with the amount invested in this holding.',
  PPF: 'Absolute return compares today’s value with the amount invested in this holding.',
  FD: 'Absolute return compares today’s value with the amount invested in this holding.',
  MF: 'Absolute return compares today’s value with the amount invested in this holding.',
  EQUITY: 'Absolute return compares today’s value with the amount invested in this holding.',
  OTHER: 'Absolute return compares today’s value with the amount invested in this holding.',
  STATEMENT: 'This is the change in value versus the invested value recorded in Kosha.',
  CLOSED: 'This holding is no longer on the latest statement. Quantity is zero.',
} as const;

function blob(h: Holding): string {
  return `${h.name} ${h.subCategory || ''} ${h.schemeType || ''} ${h.tag || ''}`.toLowerCase();
}

/** True when the ledger has a statement opening and no real buys/sells. */
export function isStatementOnlyHistory(events: LedgerEvent[]): boolean {
  let open = false;
  for (const e of events) {
    if (e.eventType === 'BUY' || e.eventType === 'SELL') return false;
    if (e.eventType === 'OPENING_BALANCE') open = true;
  }
  return open;
}

export function profileOf(h: Holding, _events: LedgerEvent[], _asOf: string): InstrumentProfile {
  const t = blob(h);
  const place = classifyHolding(h);

  let key = 'OTHER';
  if (h.category === 'GOLD_SGB' || /\bsgb\b|sovereign gold/.test(t)) key = 'SGB';
  else if (h.category === 'REAL_ESTATE' || place.l2 === 'REIT' || /\breit\b|\binvit\b/.test(t)) key = 'REIT';
  else if (place.wrapper === 'SIF' || /\bsif\b|specialised investment/.test(t)) key = 'SIF';
  else if (place.wrapper === 'AIF' || h.category === 'SPECIALISED' || /\baif\b|\bpms\b|category ii|cat ii/.test(t)) key = 'AIF';
  else if (h.category === 'FIXED_INCOME' && /\bppf\b|\bepf\b|\bvpf\b|provident|sukanya|\bssy\b/.test(t)) key = 'PPF';
  else if (h.category === 'FIXED_INCOME' || h.category === 'CASH_BANK' || /\bfd\b|fixed deposit|term deposit|post office|\btd\b|recurring/.test(t)) key = 'FD';
  else if (h.category === 'MUTUAL_FUND') key = 'MF';
  else if (h.category === 'EQUITY') key = 'EQUITY';

  return {
    key,
    primaryLabel: 'Absolute return',
    secondaryLabel: 'Gain / loss',
    why: WHY[key as keyof typeof WHY] || WHY.OTHER,
  };
}

export function holdingReturn(
  h: Holding,
  events: LedgerEvent[],
  derived: DerivedState,
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK',
  asOf: string,
): HoldingReturn {
  const own = events.filter(e => e.holdingId === h.id);
  const profile = profileOf(h, own, asOf);
  const m = metricsOf(h, derived, sgbMode);

  if (h.category !== 'LIABILITY' && m.qty <= 0n) {
    return {
      profile: { ...profile, primaryLabel: 'Closed', why: WHY.CLOSED },
      primaryPct: null,
      primaryText: 'Closed',
      secondaryPct: null,
      secondaryText: '—',
    };
  }

  const abs = absoluteReturnPct(m.cost, m.value);
  const labelled = isStatementOnlyHistory(own)
    ? { ...profile, why: WHY.STATEMENT }
    : profile;

  return {
    profile: labelled,
    primaryPct: abs,
    primaryText: formatPct(abs),
    secondaryPct: null,
    secondaryText: '—',
  };
}

export function bucketMetricHint(_bucket: string): string | null {
  return 'Absolute return = (current value − invested value) ÷ invested value.';
}
