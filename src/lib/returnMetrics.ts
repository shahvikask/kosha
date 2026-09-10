/**
 * Return metric by Indian instrument. Numbers come from the vault
 * (ledger + last close). Index / category / G-sec benchmarks are labelled
 * only — we do not invent a Nifty or CRISIL figure.
 */
import { Holding, LedgerEvent } from '../types';
import { DerivedState } from './positions';
import { calendarDays } from './lots';
import { xirr } from './returns';
import { classifyHolding } from './taxonomy';
import { metricsOf } from './holdingView';
import {
  absoluteReturnPct,
  cagrPct,
  firstActivityDate,
  formatPct,
  investmentCashFlows,
} from './growth';
import { MONEY_SCALE } from './money';

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
  MF_SIP: 'Cash goes in on many dates. XIRR is the money-weighted rate for each instalment.',
  MF_LUMP: 'Point-to-point annualised growth, for a lump sum held more than a year.',
  FD: 'Stated interest is pre-tax. Interest is taxed at your slab (TDS may apply), so post-tax yield is the true earning. We do not store your slab.',
  EQUITY: 'Buys, sells and dividends happen on different dates. XIRR includes all of that.',
  SGB: '2.5% p.a. simple interest (half-yearly) plus gold price. Interest is in the record book when posted.',
  PPF: 'EEE — the compounded rate is already post-tax. Compare with 10-year G-sec when you look it up.',
  AIF: 'Capital calls and distributions. TVPI is total value plus cash back, divided by money paid in.',
  SIF: 'Absolute, risk-aware return for long-short / hedge-style books. XIRR from your cash flows.',
  REIT: 'Rental or dividend yield plus price change. IRR needs the buy date and today’s value.',
  OTHER: 'Absolute change versus what you put in, until we have a fuller cash-flow history.',
} as const;

function blob(h: Holding): string {
  return `${h.name} ${h.subCategory || ''} ${h.schemeType || ''} ${h.tag || ''}`.toLowerCase();
}

function buyCount(events: LedgerEvent[]): number {
  return events.filter(e => e.eventType === 'BUY' || e.eventType === 'OPENING_BALANCE').length;
}

export function profileOf(h: Holding, events: LedgerEvent[], asOf: string): InstrumentProfile {
  const t = blob(h);
  const place = classifyHolding(h);
  const buys = buyCount(events);
  const first = firstActivityDate(events);
  const years = first ? calendarDays(first, asOf) / 365.25 : 0;

  if (h.category === 'GOLD_SGB' || /\bsgb\b|sovereign gold/.test(t)) {
    return { key: 'SGB', primaryLabel: 'XIRR (price + coupon)', secondaryLabel: 'CAGR', why: WHY.SGB };
  }
  if (h.category === 'REAL_ESTATE' || place.l2 === 'REIT' || /\breit\b|\binvit\b/.test(t)) {
    return { key: 'REIT', primaryLabel: 'XIRR', secondaryLabel: 'Income yield', why: WHY.REIT };
  }
  if (place.wrapper === 'SIF' || /\bsif\b|specialised investment/.test(t)) {
    return { key: 'SIF', primaryLabel: 'XIRR', secondaryLabel: 'Absolute return', why: WHY.SIF };
  }
  if (place.wrapper === 'AIF' || h.category === 'SPECIALISED' || /\baif\b|pms\b|category ii|cat ii/.test(t)) {
    return { key: 'AIF', primaryLabel: 'TVPI', secondaryLabel: 'XIRR', why: WHY.AIF };
  }
  if (h.category === 'FIXED_INCOME' && /\bppf|epf|vpf|provident|sukanya|\bssy\b/.test(t)) {
    return { key: 'PPF', primaryLabel: 'Stated compounded yield', secondaryLabel: 'CAGR on the books', why: WHY.PPF };
  }
  if (
    h.category === 'FIXED_INCOME' ||
    h.category === 'CASH_BANK' ||
    /\bfd\b|fixed deposit|term deposit|post office|\btd\b|recurring/.test(t)
  ) {
    return { key: 'FD', primaryLabel: 'Stated yield (pre-tax)', secondaryLabel: 'Absolute return', why: WHY.FD };
  }
  if (h.category === 'MUTUAL_FUND') {
    if (buys >= 3 || /\bsip\b|\bstp\b|\bswp\b/.test(t)) {
      return { key: 'MF_SIP', primaryLabel: 'XIRR', secondaryLabel: 'Absolute return', why: WHY.MF_SIP };
    }
    if (years >= 1) {
      return { key: 'MF_LUMP', primaryLabel: 'CAGR', secondaryLabel: 'Absolute return', why: WHY.MF_LUMP };
    }
    return { key: 'MF_SIP', primaryLabel: 'XIRR', secondaryLabel: 'Absolute return', why: WHY.MF_SIP };
  }
  if (h.category === 'EQUITY') {
    return { key: 'EQUITY', primaryLabel: 'XIRR', secondaryLabel: 'Absolute return', why: WHY.EQUITY };
  }
  return { key: 'OTHER', primaryLabel: 'Absolute return', secondaryLabel: 'XIRR', why: WHY.OTHER };
}

function rupees(minor: bigint): number {
  return Number(minor) / Number(MONEY_SCALE);
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
  const first = firstActivityDate(own);
  const abs = absoluteReturnPct(m.cost, m.value);
  const cagr = first ? cagrPct(m.cost, m.value, first, asOf) : null;
  const flows = investmentCashFlows(own, rupees(m.value), asOf);
  const x = xirr(flows);
  const income = own
    .filter(e => e.eventType === 'DIVIDEND' || e.eventType === 'INTEREST')
    .reduce((s, e) => s + BigInt(e.totalAmountMinor || '0'), 0n);
  const tvpi = m.cost > 0n ? Number(m.value + income) / Number(m.cost) : null;
  const years = first ? calendarDays(first, asOf) / 365.25 : 0;
  const yieldPct =
    m.value > 0n && years >= 0.25 ? (rupees(income) / Math.max(years, 1 / 12) / rupees(m.value)) * 100 : null;
  const coupon = h.couponRatePercent ?? h.interestRatePercent ?? null;

  const pick = (kind: 'xirr' | 'cagr' | 'abs' | 'tvpi' | 'yield' | 'coupon'): { pct: number | null; text: string } => {
    if (kind === 'xirr') return { pct: x, text: x == null ? 'Need a buy date and today’s value' : formatPct(x) };
    if (kind === 'cagr') return { pct: cagr, text: cagr == null ? 'Need ≥ 30 days of history' : formatPct(cagr) };
    if (kind === 'abs') return { pct: abs, text: abs == null ? '—' : formatPct(abs) };
    if (kind === 'tvpi') return { pct: tvpi, text: tvpi == null ? '—' : `${tvpi.toFixed(2)}×` };
    if (kind === 'yield') return { pct: yieldPct, text: yieldPct == null ? 'No income rows yet' : formatPct(yieldPct) };
    return { pct: coupon, text: coupon == null ? 'Add the rate on the holding' : formatPct(coupon) };
  };

  const primaryKind =
    profile.key === 'MF_LUMP' ? 'cagr'
    : profile.key === 'AIF' ? 'tvpi'
    : profile.key === 'FD' || profile.key === 'PPF' ? 'coupon'
    : profile.key === 'OTHER' ? 'abs'
    : 'xirr';
  const secondaryKind =
    profile.key === 'REIT' ? 'yield'
    : profile.key === 'AIF' ? 'xirr'
    : profile.key === 'SGB' ? 'cagr'
    : profile.key === 'PPF' ? 'cagr'
    : profile.key === 'OTHER' ? 'xirr'
    : 'abs';

  const p = pick(primaryKind);
  const s = pick(secondaryKind);
  return {
    profile,
    primaryPct: p.pct,
    primaryText: p.text,
    secondaryPct: s.pct,
    secondaryText: s.text,
  };
}

export function bucketMetricHint(bucket: string): string | null {
  switch (bucket) {
    case 'FUNDS': return 'SIPs: XIRR · lump sum > 1 year: CAGR';
    case 'SHARES': return 'XIRR from buys, sells and dividends';
    case 'GOLD': return 'SGB: XIRR of price plus 2.5% coupon';
    case 'GOVT': return 'PPF/EPF: compounded EEE yield · FD: pre-tax stated rate';
    case 'PROPERTY': return 'XIRR + rental / REIT dividend yield';
    case 'SPECIAL': return 'AIF: TVPI · SIF: XIRR';
    case 'BANK': return 'FD / deposit: stated yield (pre-tax)';
    default: return null;
  }
}
