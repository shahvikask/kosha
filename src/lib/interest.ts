/**
 * Financial-year interest catch-up.
 *
 * Research (AY 2026-27):
 * - SGB: 2.5% p.a. on the *issue price* (not CMP), paid semi-annually to
 *   the bank. Taxable as Other Income. Posted here as one FY total on
 *   31 March — cash, not added to the gold holding.
 * - PPF / SSY: interest calculated monthly on the 5th-to-month-end lowest
 *   balance, credited 31 March, compounded. We approximate with the
 *   stated rate on the recorded balance; we do not invent monthly ledgers.
 * - EPF: monthly running-balance calculation, credited after the FY once
 *   EPFO notifies the rate (often Jun–Sep). Same 31 March credit date on
 *   the passbook ("Int. Updated up to 31/03/…").
 * - Bank FD: tax on accrual each FY even for cumulative deposits. Cumulative
 *   adds to the deposit; payout FDs are income only.
 *
 * Never back-fill compounding balances — the passbook/CAS figure already
 * includes interest through the last valuation. SGB coupons are paid out,
 * so they *are* filled from the purchase date once the user records it.
 */
import { Holding, LedgerEvent } from '../types';
import { QTY_SCALE } from './money';

export type InterestMode = 'COMPOUND' | 'PAYOUT';
export type InterestKind = 'SGB' | 'PPF' | 'EPF' | 'SSY' | 'FD';

export interface InterestSpec {
  kind: InterestKind;
  mode: InterestMode;
  ratePercent: number;
  principalMinor: bigint;
}

export interface InterestCatchUpPlan {
  events: Omit<LedgerEvent, 'id' | 'createdAt'>[];
  currentPriceMinor?: string;
}

const DEFAULT_RATE: Record<InterestKind, number | null> = {
  SGB: 2.5,
  PPF: 7.1,
  EPF: 8.25,
  SSY: 8.2,
  FD: null,
};

function nameLooksLikeSgb(name?: string | null): boolean {
  const n = (name || '').toUpperCase();
  return /\bSGB\b/.test(n) || n.includes('SOVEREIGN GOLD');
}

export function interestKindOf(h: Holding): InterestKind | null {
  if (h.category === 'LIABILITY') return null;
  const sub = `${h.subCategory || ''} ${h.name}`.toLowerCase();
  if (h.category === 'GOLD_SGB' || nameLooksLikeSgb(h.name)) {
    if (h.category === 'GOLD_SGB' && /physical|digital/.test((h.subCategory || '').toLowerCase())) return null;
    return 'SGB';
  }
  if (h.category !== 'FIXED_INCOME') return null;
  if (/\bnps\b/.test(sub)) return null;
  if (h.subCategory === 'PPF' || /\bppf\b|public provident/.test(sub)) return 'PPF';
  if (h.subCategory === 'EPF' || /\bepf\b|\bvpf\b|employees. provident|provident fund/.test(sub)) return 'EPF';
  if (/sukanya|\bssy\b/.test(sub)) return 'SSY';
  if (h.subCategory === 'Bank FD' || /\bfd\b|term deposit|fixed deposit/.test(sub)) return 'FD';
  return null;
}

export function defaultInterestRate(kind: InterestKind): number | null {
  return DEFAULT_RATE[kind];
}

export function wholeMonths(fromIso: string, toIso: string): number {
  const a = parseIso(fromIso);
  const b = parseIso(toIso);
  if (!a || !b || toIso < fromIso) return 0;
  let months = (b.y - a.y) * 12 + (b.m - a.m);
  if (b.d < a.d) months -= 1;
  return months < 0 ? 0 : months;
}

export function fyEndDatesAfter(afterIso: string, asOf: string): string[] {
  const start = parseIso(afterIso);
  const end = parseIso(asOf);
  if (!end) return [];
  let y = start ? start.y : end.y - 40;
  if (start && (start.m > 3 || (start.m === 3 && start.d >= 31))) y += 1;
  const out: string[] = [];
  while (y <= end.y) {
    const fyEnd = `${y}-03-31`;
    if (fyEnd > afterIso && fyEnd <= asOf) out.push(fyEnd);
    y += 1;
    if (out.length > 80) break;
  }
  return out;
}

export function monthsHeldInFy(acquiredOn: string | undefined, fyEnd: string): number {
  const y = Number(fyEnd.slice(0, 4));
  const fyStart = `${y - 1}-04-01`;
  const from = acquiredOn && acquiredOn > fyStart ? acquiredOn : fyStart;
  if (from > fyEnd) return 0;
  if (from <= fyStart) return 12;
  return Math.min(12, wholeMonths(from, fyEnd));
}

export function interestMinor(principalMinor: bigint, ratePercent: number, months: number): bigint {
  if (principalMinor <= 0n || !(ratePercent > 0) || months <= 0) return 0n;
  const bps = BigInt(Math.round(ratePercent * 100));
  return (principalMinor * bps * BigInt(months)) / (10000n * 12n);
}

export function sgbPrincipalMinor(h: Holding, qtyScaled?: bigint): bigint {
  const qty = qtyScaled != null ? qtyScaled : BigInt(h.quantityScaled || '0');
  const issue = BigInt(h.issuePriceMinor || '0');
  const paid = issue > 0n ? issue : BigInt(h.averageCostPerUnitMinor || '0');
  if (qty <= 0n || paid <= 0n) return 0n;
  return (qty * paid) / QTY_SCALE;
}

export function interestSpecOf(h: Holding, qtyScaled?: bigint): InterestSpec | null {
  const kind = interestKindOf(h);
  if (!kind) return null;
  const rate =
    kind === 'SGB'
      ? (h.couponRatePercent != null && h.couponRatePercent > 0 ? h.couponRatePercent : DEFAULT_RATE.SGB)
      : (h.interestRatePercent != null && h.interestRatePercent > 0
          ? h.interestRatePercent
          : DEFAULT_RATE[kind]);
  if (rate == null || rate <= 0) return null;
  if (kind === 'SGB') {
    const principal = sgbPrincipalMinor(h, qtyScaled);
    if (principal <= 0n) return null;
    return { kind, mode: 'PAYOUT', ratePercent: rate, principalMinor: principal };
  }
  const principal = BigInt(h.currentPriceMinor || '0');
  if (principal <= 0n) return null;
  const payout = kind === 'FD' && isFdPayout(h);
  return { kind, mode: payout ? 'PAYOUT' : 'COMPOUND', ratePercent: rate, principalMinor: principal };
}

function isFdPayout(h: Holding): boolean {
  const c = h.details?.compounding;
  return c === 'MONTHLY' || c === 'QUARTERLY' || c === 'YEARLY';
}

export function planInterestCatchUp(
  h: Holding,
  asOf: string,
  alreadyPostedDates?: Iterable<string>,
  qtyScaled?: bigint
): InterestCatchUpPlan | null {
  const spec = interestSpecOf(h, qtyScaled);
  if (!spec) return null;
  const posted = new Set(alreadyPostedDates || []);
  const acquired = h.acquiredOn;
  const valuedOn = (h.lastPriceUpdated || '').slice(0, 10);
  const after =
    spec.mode === 'COMPOUND'
      ? maxIso(valuedOn, acquired)
      : acquired || valuedOn || '';
  if (!after && spec.mode === 'COMPOUND') return null;
  const dates = fyEndDatesAfter(after || '0000-01-01', asOf).filter(d => !posted.has(d));
  if (dates.length === 0) return null;

  let principal = spec.principalMinor;
  const events: InterestCatchUpPlan['events'] = [];
  for (const fyEnd of dates) {
    const months = monthsHeldInFy(acquired, fyEnd);
    const amt = interestMinor(principal, spec.ratePercent, months);
    if (amt <= 0n) continue;
    const fy = fyLabel(fyEnd);
    events.push({
      holdingId: h.id,
      holdingName: h.name,
      ownerId: h.ownerId,
      eventType: 'INTEREST',
      eventDate: fyEnd,
      quantityScaled: '0',
      unitPriceMinor: '0',
      totalAmountMinor: amt.toString(),
      notes: interestNote(spec, fy),
    });
    if (spec.mode === 'COMPOUND') principal += amt;
  }
  if (events.length === 0) return null;
  return {
    events,
    currentPriceMinor: spec.mode === 'COMPOUND' ? principal.toString() : undefined,
  };
}

export function applyInterestPlan(h: Holding, plan: InterestCatchUpPlan): Holding {
  if (!plan.currentPriceMinor) return h;
  return {
    ...h,
    currentPriceMinor: plan.currentPriceMinor,
    lastPriceUpdated: new Date().toISOString(),
    priceSource: h.priceSource || 'FY interest',
  };
}

function interestNote(spec: InterestSpec, fy: string): string {
  const rate = spec.ratePercent % 1 === 0
    ? String(spec.ratePercent)
    : spec.ratePercent.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
  switch (spec.kind) {
    case 'SGB':
      return `SGB coupon for FY ${fy} (${rate}% on issue price)`;
    case 'PPF':
      return `PPF interest for FY ${fy} (${rate}%)`;
    case 'EPF':
      return `EPF interest for FY ${fy} (${rate}%)`;
    case 'SSY':
      return `SSY interest for FY ${fy} (${rate}%)`;
    case 'FD':
      return spec.mode === 'COMPOUND'
        ? `FD interest for FY ${fy} (${rate}%, cumulative)`
        : `FD interest for FY ${fy} (${rate}%, paid out)`;
  }
}

function fyLabel(fyEnd: string): string {
  const y = Number(fyEnd.slice(0, 4));
  return `${y - 1}-${String(y % 100).padStart(2, '0')}`;
}

function parseIso(iso: string): { y: number; m: number; d: number } | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return null;
  return { y: Number(m[1]), m: Number(m[2]), d: Number(m[3]) };
}

function maxIso(a?: string, b?: string): string {
  const x = a && /^\d{4}-\d{2}-\d{2}/.test(a) ? a.slice(0, 10) : '';
  const y = b && /^\d{4}-\d{2}-\d{2}/.test(b) ? b.slice(0, 10) : '';
  if (!x) return y;
  if (!y) return x;
  return x >= y ? x : y;
}
