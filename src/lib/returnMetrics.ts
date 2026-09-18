/**
 * Holding return presentation.
 *
 * Absolute return is always shown when invested is known.
 * Annual return (CAGR) is only shown when one unchanged amount sat ≥ 1 year.
 * If invested changed (SIP, extra buy, sale), annual is N/A.
 */
import { Holding, LedgerEvent } from '../types';
import { DerivedState } from './positions';
import { classifyHolding } from './taxonomy';
import { metricsOf } from './holdingView';
import { absoluteReturnPct, annualReturnPct, formatPct } from './growth';
import { isUnknownInvested, UNKNOWN_INVESTED_LABEL, earliestActiveParcel } from './costBasis';
import { foldHolding, calendarDays } from './lots';

export interface InstrumentProfile {
  key: string;
  primaryLabel: string;
  secondaryLabel: string;
  why: string;
}

export type AnnualReason = 'ok' | 'changed' | 'under_year' | 'unknown' | 'closed' | 'none';

export interface HoldingReturn {
  profile: InstrumentProfile;
  primaryPct: number | null;
  primaryText: string;
  secondaryPct: number | null;
  secondaryText: string;
  annualReason: AnnualReason;
  annualWhy: string;
  unknownInvested?: boolean;
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
  UNKNOWN: 'The statement printed today’s market value, not what you paid. Return cannot be computed until you add invested.',
  CLOSED: 'This holding is no longer on the latest statement. Quantity is zero.',
} as const;

export const ANNUAL_WHY: Record<AnnualReason, string> = {
  ok: 'Annual return is the yearly rate if this same amount sat from purchase until today.',
  changed: 'N/A — invested changed after purchase (SIP, extra buy or sale). Annual return only works on one amount left untouched.',
  under_year: 'N/A — held for under a year.',
  unknown: 'N/A — add what you paid first.',
  closed: 'N/A — this holding is closed.',
  none: 'N/A — not enough of a purchase date to annualise.',
};

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

/** Extra lots, SIPs or a sale — simple annual return would lie. */
export function investedHasChanged(events: LedgerEvent[], holdingId: string, category: Holding['category']): boolean {
  const mine = events.filter(e => e.holdingId === holdingId);
  if (mine.some(e => e.eventType === 'SELL')) return true;
  const fold = foldHolding(mine, category);
  return fold.openParcels.filter(p => p.remainingQtyScaled > 0n).length > 1;
}

export function firstPurchaseIso(holding: Holding, events: LedgerEvent[]): string | null {
  const acquired = (holding.acquiredOn || '').slice(0, 10);
  if (/^\d{4}-\d{2}-\d{2}$/.test(acquired)) return acquired;
  const parcel = earliestActiveParcel(events, holding.id);
  const d = (parcel?.eventDate || '').slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(d) ? d : null;
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
    secondaryLabel: 'Annual return',
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

  const annual = (reason: AnnualReason, pct: number | null = null): Pick<HoldingReturn, 'secondaryPct' | 'secondaryText' | 'annualReason' | 'annualWhy'> => ({
    secondaryPct: pct,
    secondaryText: reason === 'ok' ? formatPct(pct) : 'N/A',
    annualReason: reason,
    annualWhy: ANNUAL_WHY[reason],
  });

  if (h.category !== 'LIABILITY' && m.qty <= 0n) {
    return {
      profile: { ...profile, primaryLabel: 'Closed', why: WHY.CLOSED },
      primaryPct: null,
      primaryText: 'Closed',
      unknownInvested: false,
      ...annual('closed'),
    };
  }

  if (isUnknownInvested(h, own, m.cost, m.value)) {
    return {
      profile: { ...profile, why: WHY.UNKNOWN },
      primaryPct: null,
      primaryText: UNKNOWN_INVESTED_LABEL,
      unknownInvested: true,
      ...annual('unknown'),
    };
  }

  const abs = absoluteReturnPct(m.cost, m.value);
  const labelled = isStatementOnlyHistory(own)
    ? { ...profile, why: WHY.STATEMENT }
    : profile;

  let ann = annual('none');
  if (investedHasChanged(own, h.id, h.category)) {
    ann = annual('changed');
  } else {
    const start = firstPurchaseIso(h, own);
    const days = start ? calendarDays(start, asOf) : 0;
    if (!start) ann = annual('none');
    else if (days < 365) ann = annual('under_year');
    else {
      const pct = annualReturnPct(m.cost, m.value, days);
      ann = pct == null ? annual('none') : annual('ok', pct);
    }
  }

  return {
    profile: labelled,
    primaryPct: abs,
    primaryText: formatPct(abs),
    unknownInvested: false,
    ...ann,
  };
}

export function bucketMetricHint(_bucket: string): string | null {
  return 'Abs. return = (current − invested) ÷ invested. Annual return is N/A if invested changed.';
}