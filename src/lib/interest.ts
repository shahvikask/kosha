/**
 * Interest catch-up for Indian schemes.
 *
 * Rates below are the July–September 2026 small-savings rates and the
 * FY 2025-26 EPF rate. When the stored balance is still the amount deposited,
 * PPF, SSY, EPF, NSC, KVP, RD and a cumulative FD are projected from that
 * deposit, including earlier financial years. A balance the user typed
 * (Manual Verified) is left as they saved it. Payout products (SGB, SCSS, POMIS, the
 * RBI bond, GOI bonds, payout FDs) are cash from the purchase date and are
 * not added to the holding. REIT monthly income is a separate cash record.
 *
 * PPF / SSY month-end "lowest balance from the 5th" needs every deposit.
 * Without those dates the annual credit uses the deposited amount.
 */
import { Holding, LedgerEvent } from '../types';
import { QTY_SCALE } from './money';

export type InterestMode = 'COMPOUND' | 'PAYOUT';
export type InterestKind =
  | 'SGB'
  | 'PPF'
  | 'EPF'
  | 'SSY'
  | 'FD'
  | 'SCSS'
  | 'POMIS'
  | 'NSC'
  | 'KVP'
  | 'RD'
  | 'FRSB'
  | 'BOND'
  | 'OTHER';

export interface InterestSpec {
  kind: InterestKind;
  mode: InterestMode;
  ratePercent: number;
  principalMinor: bigint;
  /** Payouts per year. Cumulative FD / RD compound quarterly instead. */
  periodsPerYear: number;
  /** Cumulative FD and RD: (1 + r/4)^(months/3) − 1, not simple interest. */
  quarterlyCompound: boolean;
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
  SCSS: 8.2,
  POMIS: 7.4,
  NSC: 7.7,
  KVP: 7.5,
  RD: 6.7,
  FRSB: 8.05,
  BOND: null,
  OTHER: null,
};

const PAYOUT_KIND = new Set<InterestKind>(['SGB', 'SCSS', 'POMIS', 'FRSB', 'BOND']);

function nameLooksLikeSgb(name?: string | null): boolean {
  const n = (name || '').toUpperCase();
  return /\bSGB\b/.test(n) || n.includes('SOVEREIGN GOLD');
}

export function kindForSubcategory(subCategory?: string | null): InterestKind | null {
  const sub = (subCategory || '').toLowerCase();
  if (/sovereign gold|\bsgb\b/.test(sub)) return 'SGB';
  if (sub === 'ppf' || /\bppf\b|public provident/.test(sub)) return 'PPF';
  if (sub === 'epf' || /\bepf\b|\bvpf\b|provident fund/.test(sub)) return 'EPF';
  if (/sukanya|\bssy\b/.test(sub)) return 'SSY';
  if (sub === 'bank fd' || /\bfd\b|term deposit|fixed deposit/.test(sub)) return 'FD';
  if (/\bscss\b|senior citizen savings/.test(sub)) return 'SCSS';
  if (/pomis|monthly income/.test(sub)) return 'POMIS';
  if (sub === 'nsc' || /national savings certificate/.test(sub)) return 'NSC';
  if (sub === 'kvp' || /kisan vikas/.test(sub)) return 'KVP';
  if (/\brd\b|recurring deposit/.test(sub)) return 'RD';
  if (/floating rate|frsb/.test(sub)) return 'FRSB';
  if (/bond|goi/.test(sub)) return 'BOND';
  return null;
}

export function interestKindOf(h: Holding): InterestKind | null {
  if (h.category === 'LIABILITY') return null;
  const sub = (h.subCategory || '').toLowerCase();
  if (h.category === 'GOLD_SGB') {
    if (/physical|digital/.test(sub)) return null;
    if (/\bother\b/.test(sub) && !nameLooksLikeSgb(h.name)) return null;
    if (/sovereign|\bsgb\b/.test(sub) || nameLooksLikeSgb(h.name) || sub === '') return 'SGB';
    return null;
  }
  if (nameLooksLikeSgb(h.name) && !/physical|digital/.test(sub)) return 'SGB';
  if (h.category !== 'FIXED_INCOME') return null;
  if (/\bnps\b/.test(`${sub} ${h.name}`.toLowerCase())) return null;
  const kind = kindForSubcategory(`${h.subCategory || ''} ${h.name}`);
  if (kind) return kind;
  if (sub === 'other' && (h.interestRatePercent ?? 0) > 0) return 'OTHER';
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

export function addMonthsIso(iso: string, months: number): string | null {
  const p = parseIso(iso);
  if (!p || !Number.isFinite(months)) return null;
  const dt = new Date(Date.UTC(p.y, p.m - 1 + months, p.d));
  const y = dt.getUTCFullYear();
  const m = String(dt.getUTCMonth() + 1).padStart(2, '0');
  const d = String(dt.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
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

function periodInterest(principalMinor: bigint, ratePercent: number, periodsPerYear: number): bigint {
  if (principalMinor <= 0n || !(ratePercent > 0) || periodsPerYear <= 0) return 0n;
  const bps = BigInt(Math.round(ratePercent * 100));
  return (principalMinor * bps) / (10000n * BigInt(periodsPerYear));
}

function compoundQuarterlyMinor(principalMinor: bigint, ratePercent: number, months: number): bigint {
  if (principalMinor <= 0n || !(ratePercent > 0) || months <= 0) return 0n;
  const r = ratePercent / 100 / 4;
  const factor = Math.pow(1 + r, months / 3);
  const next = Math.round(Number(principalMinor) * factor);
  const interest = BigInt(next) - principalMinor;
  return interest > 0n ? interest : 0n;
}

function investedMinor(h: Holding, qtyScaled?: bigint): bigint {
  const qty = qtyScaled != null ? qtyScaled : BigInt(h.quantityScaled || '0');
  const avg = BigInt(h.averageCostPerUnitMinor || '0');
  if (qty <= 0n || avg <= 0n) return 0n;
  return (qty * avg) / QTY_SCALE;
}

export function valueIsUserLocked(h: Holding): boolean {
  return /Manual Verified/.test(h.priceSource || '');
}

export function sgbPrincipalMinor(h: Holding, qtyScaled?: bigint): bigint {
  const qty = qtyScaled != null ? qtyScaled : BigInt(h.quantityScaled || '0');
  const issue = BigInt(h.issuePriceMinor || '0');
  if (qty <= 0n || issue <= 0n) return 0n;
  return (qty * issue) / QTY_SCALE;
}

function bondPrincipalMinor(h: Holding, qtyScaled?: bigint): bigint {
  const qty = qtyScaled != null ? qtyScaled : BigInt(h.quantityScaled || '0');
  const face = BigInt(h.details?.faceValueMinor || '0');
  const px = face > 0n ? face : BigInt(h.currentPriceMinor || '0');
  if (qty <= 0n || px <= 0n) return 0n;
  return (qty * px) / QTY_SCALE;
}

function periodsFor(kind: InterestKind, h: Holding): { periodsPerYear: number; stepMonths: number } {
  if (kind === 'FD') {
    switch (h.details?.compounding) {
      case 'MONTHLY': return { periodsPerYear: 12, stepMonths: 1 };
      case 'QUARTERLY': return { periodsPerYear: 4, stepMonths: 3 };
      case 'HALF_YEARLY': return { periodsPerYear: 2, stepMonths: 6 };
      case 'YEARLY': return { periodsPerYear: 1, stepMonths: 12 };
      default: return { periodsPerYear: 4, stepMonths: 3 };
    }
  }
  if (kind === 'POMIS') return { periodsPerYear: 12, stepMonths: 1 };
  if (kind === 'SCSS') return { periodsPerYear: 4, stepMonths: 3 };
  if (kind === 'RD') return { periodsPerYear: 4, stepMonths: 3 };
  return { periodsPerYear: 2, stepMonths: 6 };
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

  if (kind === 'FD' && !h.details?.compounding) return null;

  const { periodsPerYear } = periodsFor(kind, h);
  if (kind === 'SGB') {
    const principal = sgbPrincipalMinor(h, qtyScaled);
    if (principal <= 0n) return null;
    return { kind, mode: 'PAYOUT', ratePercent: rate, principalMinor: principal, periodsPerYear: 2, quarterlyCompound: false };
  }
  if (kind === 'BOND') {
    const principal = bondPrincipalMinor(h, qtyScaled);
    if (principal <= 0n) return null;
    return { kind, mode: 'PAYOUT', ratePercent: rate, principalMinor: principal, periodsPerYear, quarterlyCompound: false };
  }
  const principal = BigInt(h.currentPriceMinor || '0');
  if (principal <= 0n) return null;
  if (PAYOUT_KIND.has(kind)) {
    return { kind, mode: 'PAYOUT', ratePercent: rate, principalMinor: principal, periodsPerYear, quarterlyCompound: false };
  }
  if (kind === 'FD') {
    const cumulative = h.details?.compounding === 'CUMULATIVE';
    const deposited = investedMinor(h, qtyScaled);
    const balance = BigInt(h.currentPriceMinor || '0');
    const principal = !cumulative && deposited > 0n ? deposited : balance;
    if (principal <= 0n) return null;
    return {
      kind,
      mode: cumulative ? 'COMPOUND' : 'PAYOUT',
      ratePercent: rate,
      principalMinor: principal,
      periodsPerYear,
      quarterlyCompound: cumulative,
    };
  }
  return {
    kind,
    mode: 'COMPOUND',
    ratePercent: rate,
    principalMinor: principal,
    periodsPerYear: kind === 'RD' ? 4 : 1,
    quarterlyCompound: kind === 'RD',
  };
}

function steppedDates(anchor: string, stepMonths: number, asOf: string, maturity?: string): string[] {
  const out: string[] = [];
  let cursor = addMonthsIso(anchor, stepMonths);
  let guard = 0;
  while (cursor && cursor <= asOf && guard < 480) {
    if (maturity && cursor > maturity) break;
    out.push(cursor);
    if (maturity && cursor === maturity) break;
    cursor = addMonthsIso(cursor, stepMonths);
    guard += 1;
  }
  return out;
}

function frsbDates(acquired: string, asOf: string, maturity?: string): string[] {
  const start = parseIso(acquired);
  const end = parseIso(asOf);
  if (!start || !end) return [];
  const out: string[] = [];
  for (let y = start.y; y <= end.y + 1; y += 1) {
    for (const md of ['01-01', '07-01']) {
      const d = `${y}-${md}`;
      if (d <= acquired || d > asOf) continue;
      if (maturity && d > maturity) continue;
      out.push(d);
    }
  }
  return out;
}

function payoutDates(h: Holding, spec: InterestSpec, asOf: string): string[] {
  const acquired = (h.acquiredOn || '').slice(0, 10);
  const maturity = (h.maturityDate || '').slice(0, 10) || undefined;
  if (!acquired) return [];
  if (spec.kind === 'FRSB') return frsbDates(acquired, asOf, maturity);
  const { stepMonths } = periodsFor(spec.kind, h);
  return steppedDates(acquired, stepMonths, asOf, maturity);
}

/** Next payout or 31 March credit on or after asOf. Null when the scheme has ended. */
export function nextScheduledInterestDate(h: Holding, asOf: string): string | null {
  const spec = interestSpecOf(h);
  if (!spec) return null;
  const acquired = (h.acquiredOn || '').slice(0, 10);
  const maturity = (h.maturityDate || '').slice(0, 10) || undefined;
  const today = (asOf || '').slice(0, 10);
  if (!acquired || !today) return null;
  if (maturity && today > maturity) return null;
  if (spec.mode === 'COMPOUND') {
    const horizon = maturity && maturity < '2099-03-31' ? maturity : '2099-03-31';
    return fyEndDatesAfter(acquired, horizon).find(d => d >= today && (!maturity || d <= maturity)) || null;
  }
  if (spec.kind === 'FRSB') {
    const start = parseIso(today);
    if (!start) return null;
    for (let y = start.y; y <= start.y + 3; y += 1) {
      for (const md of ['01-01', '07-01']) {
        const d = `${y}-${md}`;
        if (d < today || d <= acquired) continue;
        if (maturity && d > maturity) return null;
        return d;
      }
    }
    return null;
  }
  const { stepMonths } = periodsFor(spec.kind, h);
  let cursor = addMonthsIso(acquired, stepMonths);
  let guard = 0;
  while (cursor && guard < 600) {
    if (maturity && cursor > maturity) return null;
    if (cursor >= today) return cursor;
    cursor = addMonthsIso(cursor, stepMonths);
    guard += 1;
  }
  return null;
}

function contributionThisFy(h: Holding, fyEnd: string): { amount: bigint; earnsThisYear: boolean } | null {
  if (h.details?.compounding) return null;
  const kind = interestKindOf(h);
  if (kind !== 'PPF' && kind !== 'SSY') return null;
  const amount = BigInt(h.details?.annualContributionMinor || '0');
  const when = (h.details?.contributionOn || '').slice(0, 10);
  if (amount <= 0n || !/^\d{4}-\d{2}-\d{2}/.test(when)) return null;
  const y = Number(fyEnd.slice(0, 4));
  const fyStart = `${y - 1}-04-01`;
  if (when < fyStart || when > fyEnd) return null;
  const day = Number(when.slice(8, 10));
  return { amount, earnsThisYear: day <= 5 };
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
  const acquired = (h.acquiredOn || '').slice(0, 10);
  const valuedOn = (h.lastPriceUpdated || '').slice(0, 10);
  const maturity = (h.maturityDate || '').slice(0, 10);

  let dates: string[];
  let opening = spec.principalMinor;
  const deposited = investedMinor(h, qtyScaled);
  const projectFromDeposit =
    spec.mode === 'COMPOUND' &&
    !valueIsUserLocked(h) &&
    !!acquired &&
    deposited > 0n &&
    opening === deposited;
  if (spec.mode === 'PAYOUT') {
    dates = payoutDates(h, spec, asOf).filter(d => !posted.has(d));
  } else if (valueIsUserLocked(h)) {
    return null;
  } else if (projectFromDeposit) {
    dates = fyEndDatesAfter(acquired, asOf).filter(d => (!maturity || d <= maturity));
    opening = deposited;
  } else {
    const after = maxIso(valuedOn, acquired);
    if (!after) return null;
    dates = fyEndDatesAfter(after, asOf).filter(d => !posted.has(d) && (!maturity || d <= maturity));
  }
  if (dates.length === 0) return null;

  let principal = opening;
  const events: InterestCatchUpPlan['events'] = [];
  for (const date of dates) {
    if (!projectFromDeposit && posted.has(date)) continue;
    const contrib = spec.mode === 'COMPOUND' ? contributionThisFy(h, date) : null;
    let base = principal;
    if (contrib?.earnsThisYear) base += contrib.amount;
    const amt = spec.mode === 'PAYOUT'
      ? periodInterest(principal, spec.ratePercent, spec.periodsPerYear)
      : spec.quarterlyCompound
        ? compoundQuarterlyMinor(base, spec.ratePercent, monthsHeldInFy(acquired, date))
        : interestMinor(base, spec.ratePercent, monthsHeldInFy(acquired, date));
    if (amt <= 0n) {
      if (spec.mode === 'COMPOUND' && contrib && !contrib.earnsThisYear) principal += contrib.amount;
      continue;
    }
    if (!posted.has(date)) {
      events.push({
        holdingId: h.id,
        holdingName: h.name,
        ownerId: h.ownerId,
        eventType: 'INTEREST',
        eventDate: date,
        quantityScaled: '0',
        unitPriceMinor: '0',
        totalAmountMinor: amt.toString(),
        notes: interestNote(spec, date),
      });
    }
    if (spec.mode === 'COMPOUND') {
      principal = base + amt;
      if (contrib && !contrib.earnsThisYear) principal += contrib.amount;
    }
  }
  if (events.length === 0) return null;
  const grown = spec.mode === 'COMPOUND' ? principal.toString() : undefined;
  if (grown && grown === (h.currentPriceMinor || '')) return null;
  return {
    events,
    currentPriceMinor: grown,
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

function interestNote(spec: InterestSpec, date: string): string {
  const rate = spec.ratePercent % 1 === 0
    ? String(spec.ratePercent)
    : spec.ratePercent.toFixed(2).replace(/0+$/, '').replace(/\.$/, '');
  const fy = fyLabel(date);
  switch (spec.kind) {
    case 'SGB':
      return `SGB half-year coupon on ${date} (${rate}% on the issue price)`;
    case 'PPF':
      return `PPF interest for FY ${fy} (${rate}%)`;
    case 'EPF':
      return `EPF interest for FY ${fy} (${rate}%, passbook date 31 March)`;
    case 'SSY':
      return `SSY interest for FY ${fy} (${rate}%)`;
    case 'FD':
      return spec.mode === 'COMPOUND'
        ? `FD interest for FY ${fy} (${rate}%, compounded quarterly)`
        : `FD interest paid on ${date} (${rate}%)`;
    case 'SCSS':
      return `SCSS quarterly interest on ${date} (${rate}%)`;
    case 'POMIS':
      return `POMIS monthly interest on ${date} (${rate}%)`;
    case 'NSC':
      return `NSC accrual for FY ${fy} (${rate}%, paid only at maturity)`;
    case 'KVP':
      return `KVP accrual for FY ${fy} (${rate}%, paid only at maturity)`;
    case 'RD':
      return `RD interest for FY ${fy} (${rate}%, quarterly compound on the balance entered)`;
    case 'FRSB':
      return `RBI floating-rate bond interest on ${date} (${rate}%)`;
    case 'BOND':
      return `Bond coupon on ${date} (${rate}% on face value)`;
    case 'OTHER':
      return `Interest for FY ${fy} (${rate}%)`;
  }
}

function fyLabel(fyEndOrDate: string): string {
  const p = parseIso(fyEndOrDate);
  if (!p) return fyEndOrDate;
  const endYear = p.m > 3 ? p.y + 1 : p.y;
  return `${endYear - 1}-${String(endYear % 100).padStart(2, '0')}`;
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

export function clampIncomeDay(day: number): number {
  if (!Number.isFinite(day)) return 1;
  const n = Math.round(day);
  if (n < 1) return 1;
  if (n > 28) return 28;
  return n;
}

function isoYmd(y: number, m: number, d: number): string {
  return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** Paydays from start through asOf, clamped to day 1–28, stopping after incomeEnd. */
export function monthlyIncomeDates(startIso: string, day: number, asOf: string, endIso?: string): string[] {
  const start = (startIso || '').slice(0, 10);
  const today = (asOf || '').slice(0, 10);
  const end = (endIso || '').slice(0, 10);
  const s = parseIso(start);
  if (!s || !/^\d{4}-\d{2}-\d{2}$/.test(today)) return [];
  const d = clampIncomeDay(day);
  let y = s.y;
  let m = s.m;
  let cursor = isoYmd(y, m, d);
  if (cursor < start) {
    m += 1;
    if (m > 12) { m = 1; y += 1; }
    cursor = isoYmd(y, m, d);
  }
  const out: string[] = [];
  let guard = 0;
  while (cursor <= today && guard < 480) {
    if (end && /^\d{4}-\d{2}-\d{2}$/.test(end) && cursor > end) break;
    out.push(cursor);
    m += 1;
    if (m > 12) { m = 1; y += 1; }
    cursor = isoYmd(y, m, d);
    guard += 1;
  }
  return out;
}

export function planMonthlyIncome(
  h: Holding,
  asOf: string,
  alreadyPostedDates?: Iterable<string>
): InterestCatchUpPlan['events'] | null {
  const amount = BigInt(h.details?.monthlyIncomeMinor || '0');
  if (amount <= 0n) return null;
  const start = (h.details?.incomeStart || h.acquiredOn || '').slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start)) return null;
  const posted = new Set(alreadyPostedDates || []);
  const dates = monthlyIncomeDates(start, h.details?.incomeDay ?? 1, asOf, h.details?.incomeEnd)
    .filter(d => !posted.has(d));
  if (dates.length === 0) return null;
  return dates.map(date => ({
    holdingId: h.id,
    holdingName: h.name,
    ownerId: h.ownerId,
    eventType: 'DIVIDEND',
    eventDate: date,
    quantityScaled: '0',
    unitPriceMinor: '0',
    totalAmountMinor: amount.toString(),
    notes: `Monthly income on ${date}`,
  }));
}

export function nextMonthlyIncomeDate(h: Holding, asOf: string): string | null {
  const amount = BigInt(h.details?.monthlyIncomeMinor || '0');
  if (amount <= 0n) return null;
  const start = (h.details?.incomeStart || h.acquiredOn || '').slice(0, 10);
  const today = (asOf || '').slice(0, 10);
  const horizon = addMonthsIso(today, 36) || today;
  const dates = monthlyIncomeDates(start, h.details?.incomeDay ?? 1, horizon, h.details?.incomeEnd);
  return dates.find(d => d >= today) || null;
}
