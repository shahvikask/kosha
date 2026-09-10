/**
 * Growth engine. Pure view over holdings + ledger already in SQLite.
 * Indian FY (Apr–Mar). XIRR is money-weighted; CAGR is point-to-point
 * from the first record. Prices are last close / NAV, not live.
 */
import { FamilyMember, Holding, LedgerEvent } from '../types';
import { DerivedState } from './positions';
import { MONEY_SCALE, QTY_SCALE } from './money';
import { calendarDays, financialYearOf } from './lots';
import { buildCashFlows, xirr, CashFlow } from './returns';
import {
  FamilyBucketId,
  FAMILY_BUCKETS,
  bucketOf,
  metricsOf,
  displayHoldingName,
} from './holdingView';
import { ExposureNode, EXPOSURES, classifyHolding } from './taxonomy';
import { apportion, resolveSplit } from './lookthrough';

export const BUCKET_COLORS: Record<FamilyBucketId, string> = {
  SHARES: '#f59e0b',
  FUNDS: '#3b82f6',
  GOLD: '#eab308',
  GOVT: '#10b981',
  PROPERTY: '#8b5cf6',
  BANK: '#06b6d4',
  SPECIAL: '#6366f1',
  LOAN: '#f43f5e',
};

export const EXPOSURE_COLORS: Record<ExposureNode, string> = {
  EQUITY: '#f59e0b',
  DEBT: '#10b981',
  GOLD: '#eab308',
  PROPERTY: '#a78bfa',
  CASH: '#22d3ee',
  PRIVATE: '#64748b',
  PROTECTION: '#ec4899',
  LIABILITY: '#f43f5e',
};

const MEMBER_PALETTE = ['#f59e0b', '#3b82f6', '#10b981', '#8b5cf6', '#06b6d4', '#f43f5e', '#84cc16'];

export interface GrowthSlice {
  id: string;
  label: string;
  valueMinor: bigint;
  color: string;
  count: number;
}

export interface GrowthHoldingRow {
  id: string;
  name: string;
  bucket: FamilyBucketId;
  exposure: ExposureNode;
  ownerId: string;
  ownerName: string;
  valueMinor: bigint;
  costMinor: bigint;
  gainMinor: bigint;
  realisedMinor: bigint;
}

export interface IncomeYear {
  fy: string;
  dividendMinor: bigint;
  interestMinor: bigint;
  rentalMinor: bigint;
  totalMinor: bigint;
}

export interface MonthPoint {
  month: string;
  investedMinor: bigint;
  incomeMinor: bigint;
}

export interface GrowthKpis {
  investedMinor: bigint;
  assetsMinor: bigint;
  liabilitiesMinor: bigint;
  netWorthMinor: bigint;
  absoluteGainMinor: bigint;
  absoluteReturnPct: number | null;
  xirrPct: number | null;
  cagrPct: number | null;
  firstDate: string | null;
  yearsHeld: number | null;
  incomeThisFyMinor: bigint;
  realisedMinor: bigint;
  unrealisedMinor: bigint;
}

export interface GrowthModel {
  kpis: GrowthKpis;
  buckets: GrowthSlice[];
  exposures: GrowthSlice[];
  members: GrowthSlice[];
  holdings: GrowthHoldingRow[];
  incomeYears: IncomeYear[];
  months: MonthPoint[];
  currentFy: string;
}

function rupees(minor: bigint): number {
  return Number(minor) / Number(MONEY_SCALE);
}

function eventCashMinor(ev: LedgerEvent): bigint {
  const total = BigInt(ev.totalAmountMinor || '0');
  if (total !== 0n) return total < 0n ? -total : total;
  const qty = BigInt(ev.quantityScaled || '0');
  const unit = BigInt(ev.unitPriceMinor || '0');
  const v = (qty * unit) / QTY_SCALE;
  return v < 0n ? -v : v;
}

export function firstActivityDate(events: LedgerEvent[]): string | null {
  let min: string | null = null;
  for (const ev of events) {
    if (!ev.eventDate) continue;
    if (!min || ev.eventDate < min) min = ev.eventDate;
  }
  return min;
}

export function absoluteReturnPct(invested: bigint, current: bigint): number | null {
  if (invested <= 0n) return null;
  return (rupees(current - invested) / rupees(invested)) * 100;
}

export function cagrPct(
  startMinor: bigint,
  endMinor: bigint,
  fromIso: string,
  toIso: string,
): number | null {
  if (startMinor <= 0n || endMinor <= 0n) return null;
  const days = calendarDays(fromIso, toIso);
  if (days < 30) return null;
  const years = days / 365.25;
  const start = rupees(startMinor);
  const end = rupees(endMinor);
  if (start <= 0 || end <= 0) return null;
  return (Math.pow(end / start, 1 / years) - 1) * 100;
}

export function formatPct(n: number | null, digits = 1): string {
  if (n == null || !Number.isFinite(n)) return '—';
  const sign = n > 0 ? '+' : '';
  return `${sign}${n.toFixed(digits)}%`;
}

/** OPENING_BALANCE is treated as money put in (CAS has no buy history). */
export function investmentCashFlows(
  events: LedgerEvent[],
  currentValueRupees: number,
  asOf: string,
): CashFlow[] {
  const extra: LedgerEvent[] = events.map(ev =>
    ev.eventType === 'OPENING_BALANCE' ? { ...ev, eventType: 'BUY' as const } : ev,
  );
  return buildCashFlows(extra, currentValueRupees, asOf);
}

export function incomeByFinancialYear(
  events: LedgerEvent[],
  holdingsById: Map<string, Holding>,
): IncomeYear[] {
  const map = new Map<string, IncomeYear>();
  for (const ev of events) {
    if (ev.eventType !== 'DIVIDEND' && ev.eventType !== 'INTEREST') continue;
    const fy = financialYearOf(ev.eventDate);
    if (!fy) continue;
    let row = map.get(fy);
    if (!row) {
      row = { fy, dividendMinor: 0n, interestMinor: 0n, rentalMinor: 0n, totalMinor: 0n };
      map.set(fy, row);
    }
    const amt = eventCashMinor(ev);
    const h = holdingsById.get(ev.holdingId);
    if (ev.eventType === 'DIVIDEND') row.dividendMinor += amt;
    else if (h?.category === 'REAL_ESTATE') row.rentalMinor += amt;
    else row.interestMinor += amt;
    row.totalMinor += amt;
  }
  return [...map.values()].sort((a, b) => a.fy.localeCompare(b.fy));
}

export function cumulativeByMonth(events: LedgerEvent[], asOf: string): MonthPoint[] {
  const start = firstActivityDate(events);
  if (!start) return [];
  const from = start.slice(0, 7);
  const to = asOf.slice(0, 7);
  const months: string[] = [];
  let y = Number(from.slice(0, 4));
  let m = Number(from.slice(5, 7));
  const endY = Number(to.slice(0, 4));
  const endM = Number(to.slice(5, 7));
  let guard = 0;
  while ((y < endY || (y === endY && m <= endM)) && guard++ < 240) {
    months.push(`${y}-${String(m).padStart(2, '0')}`);
    m += 1;
    if (m > 12) {
      m = 1;
      y += 1;
    }
  }

  const investedAt = new Map<string, bigint>();
  const incomeAt = new Map<string, bigint>();
  for (const ev of events) {
    const key = (ev.eventDate || '').slice(0, 7);
    if (!key) continue;
    const amt = eventCashMinor(ev);
    if (amt === 0n) continue;
    if (ev.eventType === 'BUY' || ev.eventType === 'OPENING_BALANCE') {
      investedAt.set(key, (investedAt.get(key) || 0n) + amt);
    } else if (ev.eventType === 'SELL') {
      investedAt.set(key, (investedAt.get(key) || 0n) - amt);
    } else if (ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST') {
      incomeAt.set(key, (incomeAt.get(key) || 0n) + amt);
    }
  }

  let inv = 0n;
  let inc = 0n;
  return months.map(month => {
    inv += investedAt.get(month) || 0n;
    if (inv < 0n) inv = 0n;
    inc += incomeAt.get(month) || 0n;
    return { month, investedMinor: inv, incomeMinor: inc };
  });
}

export function buildGrowthModel(
  holdings: Holding[],
  events: LedgerEvent[],
  derived: DerivedState,
  members: FamilyMember[],
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK',
  asOf: string,
): GrowthModel {
  const byId = new Map(holdings.map(h => [h.id, h]));
  const nameOf = (id: string) => members.find(m => m.id === id)?.name || 'Family';

  let assets = 0n;
  let invested = 0n;
  let liabilities = 0n;
  let realised = 0n;
  const rows: GrowthHoldingRow[] = [];
  const bucketMap = new Map<FamilyBucketId, { value: bigint; count: number }>();
  const exposureMap = new Map<ExposureNode, { value: bigint; count: number }>();
  const memberMap = new Map<string, { value: bigint; count: number }>();

  for (const h of holdings) {
    const m = metricsOf(h, derived, sgbMode);
    const pos = derived.positions.get(h.id);
    const real = pos ? pos.realisedGainMinor : 0n;
    realised += real;
    const bucket = bucketOf(h);
    if (h.category === 'LIABILITY') {
      liabilities += m.value;
    } else {
      assets += m.value;
      invested += m.cost;
      const b = bucketMap.get(bucket) || { value: 0n, count: 0 };
      b.value += m.value;
      b.count += 1;
      bucketMap.set(bucket, b);

      const placement = classifyHolding(h);
      const split = resolveSplit(h, placement);
      const parts = apportion(m.value, split);
      parts.forEach((v, node) => {
        const e = exposureMap.get(node) || { value: 0n, count: 0 };
        e.value += v;
        e.count += 1;
        exposureMap.set(node, e);
      });

      const mem = memberMap.get(h.ownerId) || { value: 0n, count: 0 };
      mem.value += m.value;
      mem.count += 1;
      memberMap.set(h.ownerId, mem);
    }
    rows.push({
      id: h.id,
      name: displayHoldingName(h.name),
      bucket,
      exposure: classifyHolding(h).exposure,
      ownerId: h.ownerId,
      ownerName: nameOf(h.ownerId),
      valueMinor: m.value,
      costMinor: m.cost,
      gainMinor: m.gain,
      realisedMinor: real,
    });
  }

  rows.sort((a, b) => (a.gainMinor < b.gainMinor ? 1 : -1));

  const buckets: GrowthSlice[] = FAMILY_BUCKETS
    .filter(b => b.id !== 'LOAN')
    .map(b => ({
      id: b.id,
      label: b.title,
      valueMinor: bucketMap.get(b.id)?.value || 0n,
      color: BUCKET_COLORS[b.id],
      count: bucketMap.get(b.id)?.count || 0,
    }))
    .filter(s => s.valueMinor > 0n);

  const exposures: GrowthSlice[] = EXPOSURES
    .filter(e => e.id !== 'LIABILITY')
    .map(e => ({
      id: e.id,
      label: e.label,
      valueMinor: exposureMap.get(e.id)?.value || 0n,
      color: EXPOSURE_COLORS[e.id],
      count: exposureMap.get(e.id)?.count || 0,
    }))
    .filter(s => s.valueMinor > 0n);

  const membersSlices: GrowthSlice[] = members.map((mem, i) => ({
    id: mem.id,
    label: mem.name,
    valueMinor: memberMap.get(mem.id)?.value || 0n,
    color: MEMBER_PALETTE[i % MEMBER_PALETTE.length],
    count: memberMap.get(mem.id)?.count || 0,
  })).filter(s => s.valueMinor > 0n);

  const assetIds = new Set(holdings.filter(h => h.category !== 'LIABILITY').map(h => h.id));
  const assetEvents = events.filter(e => assetIds.has(e.holdingId));
  const first = firstActivityDate(assetEvents);
  const yearsHeld = first ? calendarDays(first, asOf) / 365.25 : null;
  const absPct = absoluteReturnPct(invested, assets);
  const cagr = first ? cagrPct(invested, assets, first, asOf) : null;
  const flows = investmentCashFlows(assetEvents, rupees(assets), asOf);
  const x = xirr(flows);
  const incomeYears = incomeByFinancialYear(events, byId);
  const currentFy = financialYearOf(asOf);
  const incomeThisFy = incomeYears.find(y => y.fy === currentFy)?.totalMinor || 0n;
  const months = cumulativeByMonth(assetEvents, asOf);
  const unrealised = assets - invested;

  return {
    kpis: {
      investedMinor: invested,
      assetsMinor: assets,
      liabilitiesMinor: liabilities,
      netWorthMinor: assets - liabilities,
      absoluteGainMinor: assets - invested,
      absoluteReturnPct: absPct,
      xirrPct: x,
      cagrPct: cagr,
      firstDate: first,
      yearsHeld,
      incomeThisFyMinor: incomeThisFy,
      realisedMinor: realised,
      unrealisedMinor: unrealised,
    },
    buckets,
    exposures,
    members: membersSlices,
    holdings: rows,
    incomeYears,
    months,
    currentFy,
  };
}
