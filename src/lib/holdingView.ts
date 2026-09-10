import { AssetCategory, EventType, Holding } from '../types';
import { DerivedState, marketValueMinor } from './positions';
import { QTY_SCALE } from './money';
import { formatStatementDate } from './casPython';

export type ThemeName = 'dark' | 'light';
export type FamilyBucketId =
  | 'SHARES'
  | 'FUNDS'
  | 'GOLD'
  | 'GOVT'
  | 'PROPERTY'
  | 'BANK'
  | 'SPECIAL'
  | 'LOAN';

export const FAMILY_BUCKETS: {
  id: FamilyBucketId;
  title: string;
  hint: string;
}[] = [
  { id: 'SHARES', title: 'Shares', hint: 'Stocks in your demat account' },
  { id: 'FUNDS', title: 'Mutual funds', hint: 'Folios with CAMS or KFintech' },
  { id: 'GOLD', title: 'Gold', hint: 'Sovereign gold bonds and physical gold' },
  { id: 'GOVT', title: 'Govt schemes', hint: 'PPF, EPF, NPS, Sukanya' },
  { id: 'PROPERTY', title: 'Property', hint: 'Home, land, REITs' },
  { id: 'BANK', title: 'Bank & deposits', hint: 'Savings and fixed deposits' },
  { id: 'SPECIAL', title: 'AIF & others', hint: 'PMS, AIF, unlisted' },
  { id: 'LOAN', title: 'Loans', hint: 'What the family owes' },
];

export function looksLikeSgb(name?: string | null): boolean {
  const n = (name || '').toUpperCase();
  return /\bSGB\b/.test(n) || n.includes('SOVEREIGN GOLD');
}

export function bucketOf(h: Holding): FamilyBucketId {
  if (h.category === 'GOLD_SGB' || looksLikeSgb(h.name)) return 'GOLD';
  if (h.category === 'EQUITY') return 'SHARES';
  if (h.category === 'MUTUAL_FUND') return 'FUNDS';
  if (h.category === 'REAL_ESTATE') return 'PROPERTY';
  if (h.category === 'CASH_BANK') return 'BANK';
  if (h.category === 'LIABILITY') return 'LOAN';
  if (h.category === 'SPECIALISED') return 'SPECIAL';
  if (h.category === 'FIXED_INCOME') {
    const s = `${h.subCategory || ''} ${h.name}`.toLowerCase();
    if (/\bfd\b|deposit|\brd\b/.test(s)) return 'BANK';
    return 'GOVT';
  }
  return 'FUNDS';
}

export function plainCategory(category: AssetCategory): string {
  switch (category) {
    case 'EQUITY':
      return 'Share';
    case 'MUTUAL_FUND':
      return 'Mutual fund';
    case 'GOLD_SGB':
      return 'Gold';
    case 'FIXED_INCOME':
      return 'Govt / deposit';
    case 'REAL_ESTATE':
      return 'Property';
    case 'SPECIALISED':
      return 'AIF / PMS';
    case 'CASH_BANK':
      return 'Bank';
    case 'LIABILITY':
      return 'Loan';
    default:
      return category;
  }
}

export function eventLabel(type: EventType | string, notes?: string): string {
  switch (type) {
    case 'OPENING_BALANCE':
      return 'Opening from statement';
    case 'SNAPSHOT_ADJUSTMENT':
      return 'Updated to match statement';
    case 'BUY':
      return 'Bought';
    case 'SELL':
      return 'Sold';
    case 'BONUS':
      return 'Bonus shares';
    case 'SPLIT':
      return 'Share split';
    case 'DIVIDEND':
      return 'Dividend';
    case 'INTEREST':
      return 'Interest';
    case 'VALUATION_UPDATE':
      return notes && /loan/i.test(notes) ? 'Loan recorded' : 'Value updated';
    case 'REPAYMENT':
      return 'Loan repayment';
    case 'CORRECTION':
      return 'Correction';
    default:
      return type;
  }
}

export function unitWord(h: Holding): string {
  if (h.category === 'GOLD_SGB') return 'grams';
  if (h.category === 'REAL_ESTATE') return '';
  if (h.category === 'CASH_BANK' || h.category === 'LIABILITY') return '';
  return 'units';
}

export function priceMinorOf(
  h: Holding,
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK'
): bigint {
  if (h.category === 'GOLD_SGB' && sgbMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
    return BigInt(h.benchmarkPriceMinor);
  }
  return BigInt(h.currentPriceMinor || '0');
}

export function qtyOf(h: Holding, derived: DerivedState): bigint {
  const pos = derived.positions.get(h.id);
  if (pos) return pos.quantityScaled;
  return BigInt(h.quantityScaled || '0');
}

export function costOf(h: Holding, derived: DerivedState): bigint {
  const pos = derived.positions.get(h.id);
  if (pos) return pos.costMinor;
  const qty = qtyOf(h, derived);
  return (qty * BigInt(h.averageCostPerUnitMinor || '0')) / QTY_SCALE;
}

export function valueOf(
  h: Holding,
  derived: DerivedState,
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK'
): bigint {
  return metricsOf(h, derived, sgbMode).value;
}

export interface HoldingMetrics {
  qty: bigint;
  price: bigint;
  value: bigint;
  cost: bigint;
  gain: bigint;
}

export function metricsOf(
  h: Holding,
  derived: DerivedState,
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK'
): HoldingMetrics {
  if (h.category === 'LIABILITY') {
    const outstanding = BigInt(h.currentPriceMinor || '0');
    const sanctioned = BigInt(h.averageCostPerUnitMinor || '0');
    return {
      qty: 0n,
      price: outstanding,
      value: outstanding,
      cost: sanctioned > 0n ? sanctioned : outstanding,
      gain: 0n,
    };
  }
  const qty = qtyOf(h, derived);
  const price = priceMinorOf(h, sgbMode);
  const value = marketValueMinor(qty, price);
  const cost = costOf(h, derived);
  return { qty, price, value, cost, gain: value - cost };
}

export type UpcomingKind = 'SGB_COUPON' | 'MATURITY' | 'GOAL' | 'EMI';

export interface UpcomingItem {
  id: string;
  date: string;
  title: string;
  kind: UpcomingKind;
  holdingId?: string;
}

export function upcomingFromHoldings(
  holdings: Holding[],
  goals: { id: string; name: string; targetDate?: string }[],
  asOf = new Date().toISOString().slice(0, 10)
): UpcomingItem[] {
  const out: UpcomingItem[] = [];
  for (const h of holdings) {
    if (h.category === 'LIABILITY' && h.nextCouponDate && h.nextCouponDate >= asOf && h.emiStatus !== 'STOPPED') {
      out.push({
        id: `e_${h.id}`,
        date: h.nextCouponDate,
        title: `EMI due · ${displayHoldingName(h.name)}`,
        kind: 'EMI',
        holdingId: h.id,
      });
    } else if (h.category !== 'LIABILITY' && h.nextCouponDate && h.nextCouponDate >= asOf) {
      out.push({
        id: `c_${h.id}`,
        date: h.nextCouponDate,
        title:
          h.category === 'GOLD_SGB'
            ? `Gold bond interest · ${displayHoldingName(h.name)}`
            : `Interest · ${displayHoldingName(h.name)}`,
        kind: 'SGB_COUPON',
        holdingId: h.id,
      });
    }
    if (h.maturityDate && h.maturityDate >= asOf) {
      const kindLabel =
        h.category === 'GOLD_SGB'
          ? 'Gold bond matures'
          : h.category === 'FIXED_INCOME'
            ? 'Matures'
            : h.category === 'LIABILITY'
              ? 'Loan ends'
              : 'Lock-in ends';
      out.push({
        id: `m_${h.id}`,
        date: h.maturityDate,
        title: `${kindLabel} · ${displayHoldingName(h.name)}`,
        kind: 'MATURITY',
        holdingId: h.id,
      });
    }
  }
  for (const g of goals) {
    if (g.targetDate && g.targetDate >= asOf) {
      out.push({
        id: `g_${g.id}`,
        date: g.targetDate,
        title: `Goal · ${g.name}`,
        kind: 'GOAL',
      });
    }
  }
  return out.sort((a, b) => a.date.localeCompare(b.date)).slice(0, 8);
}

export function formatCalendarDate(iso?: string | null): string {
  return formatStatementDate(iso);
}

/**
 * CDSL prints mutual-fund names as "SCHEMECODE - Fund name"
 * (MURG, 3572, 001ZG, 15, …). Strip that prefix for display only.
 * Stored names and matching are unchanged.
 */
export function displayHoldingName(name: string | null | undefined): string {
  const n = (name || '').trim();
  if (!n) return '';
  const m = n.match(/^([A-Z0-9]{1,8})\s*-\s+(.+)$/);
  if (!m) return n;
  const rest = m[2].trim();
  return rest || n;
}

const GENERIC_CUSTODIAN = /^(mutual fund folios?|mf folios?|cas)$/i;

/** Longest first so "ICICI Prudential" wins over "ICICI". */
const AMC_PREFIXES = [
  'Aditya Birla Sun Life',
  'Baroda BNP Paribas',
  'Mahindra Manulife',
  'WhiteOak Capital',
  'Motilal Oswal',
  'Canara Robeco',
  'ICICI Prudential',
  'Nippon India',
  'Parag Parikh',
  'Mirae Asset',
  'JM Financial',
  'Bank of India',
  'Edelweiss',
  'WhiteOak',
  'Bandhan',
  'Sundaram',
  'Franklin',
  'Invesco',
  'Kotak',
  'HDFC',
  'Axis',
  'Quant',
  'PPFAS',
  'Groww',
  'Tata',
  'HSBC',
  'Navi',
  'DSP',
  'UTI',
  'SBI',
  'LIC',
  'ITI',
];

export function inferAmcFromSchemeName(name: string | null | undefined): string | undefined {
  const n = displayHoldingName(name).trim();
  if (!n) return undefined;
  const lower = n.toLowerCase();
  const prefixes = [...AMC_PREFIXES].sort((a, b) => b.length - a.length);
  for (const prefix of prefixes) {
    if (lower.startsWith(prefix.toLowerCase())) return prefix;
  }
  return undefined;
}

/** Title for an account card: AMC or broker, never the CDSL bucket "Mutual Fund Folios". */
export function accountTitle(
  custodian: string | undefined,
  holdings: { amc?: string; name: string }[],
  accountType?: string
): string {
  const amc = holdings.map(h => (h.amc || '').trim()).find(Boolean);
  if (amc) return amc;
  const c = (custodian || '').trim();
  if (c && !GENERIC_CUSTODIAN.test(c)) return c;
  for (const h of holdings) {
    const inferred = inferAmcFromSchemeName(h.name);
    if (inferred) return inferred;
  }
  if (accountType === 'MF_FOLIO') return 'Mutual fund folio';
  return c || 'Account';
}

export function whereHeld(h: Holding): string {
  const bits = [
    h.institutionName || h.dpName || h.amc,
    h.accountNumberMasked || h.symbolOrFolio,
  ].filter(Boolean);
  return bits.join(' · ') || 'Location not recorded';
}

export const EMPTY_SELF = {
  id: 'fam_1',
  name: 'You',
  relation: 'Self' as const,
  color: '#0284c7',
  isPrimary: true,
};
