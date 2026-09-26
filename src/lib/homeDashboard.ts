/**
 * Home helpers: Pass A–C (health, steps, since-last) and
 * Pass D (holdings whose CAS cost is still the market price).
 */
import { FamilyMember, Holding, LedgerEvent, StatementSnapRow, StatementHoldingSnap } from '../types';
import { CasFileType, CasLatestState, CAS_FILE_TYPE_LABEL } from './casPython';
import { DerivedState } from './positions';
import { financialYearOf } from './lots';
import { interestKindOf } from './interest';
import { isOpenPosition, metricsOf, displayHoldingName } from './holdingView';
import { isUnknownInvested } from './costBasis';

const STALE_AMBER_DAYS = 45;
const STALE_RED_DAYS = 90;
const PRICE_LAG_AMBER_DAYS = 7;
const TOP_N = 5;

export type HealthTone = 'ok' | 'amber' | 'red';
export type HealthGo = 'DOCUMENTS' | 'HOLDINGS' | 'FAMILY';

export interface HealthFinding {
  kind: 'COST_EQUALS_VALUE' | 'STALE_STATEMENT' | 'MISSING_FY_INTEREST' | 'MEMBER_PAN_MISSING';
  tone: HealthTone;
  title: string;
  detail: string;
  count?: number;
  go: HealthGo;
}

export interface BooksHealth {
  tone: HealthTone;
  headline: string;
  findings: HealthFinding[];
}

export interface ConcentrationRow {
  id: string;
  name: string;
  valueMinor: bigint;
  pct: number;
}

export function daysBetweenIso(fromIso: string, toIso: string): number | null {
  const a = ymd(fromIso);
  const b = ymd(toIso);
  if (!a || !b) return null;
  return Math.round((Date.UTC(b.y, b.m - 1, b.d) - Date.UTC(a.y, a.m - 1, a.d)) / 86_400_000);
}

export function previousFinancialYear(fy: string): string {
  const start = Number((fy || '').split('-')[0]);
  if (!Number.isFinite(start) || start < 1990) return '';
  return `${start - 1}-${String(start % 100).padStart(2, '0')}`;
}

export function fyEndIso(fy: string): string {
  const start = Number((fy || '').split('-')[0]);
  if (!Number.isFinite(start)) return '';
  return `${start + 1}-03-31`;
}

export function freshnessTone(opts: {
  statementAsOf: string | null;
  pricesAsOf: string | null;
  todayIso: string;
  staleAfterDays?: number;
  priceLagDays?: number;
}): HealthTone {
  const staleAfter = opts.staleAfterDays ?? STALE_AMBER_DAYS;
  const lagAfter = opts.priceLagDays ?? PRICE_LAG_AMBER_DAYS;
  if (opts.statementAsOf) {
    const age = daysBetweenIso(opts.statementAsOf, opts.todayIso);
    if (age != null && age >= STALE_RED_DAYS) return 'red';
    if (age != null && age >= staleAfter) return 'amber';
  }
  if (opts.statementAsOf && opts.pricesAsOf) {
    const lag = Math.abs(daysBetweenIso(opts.statementAsOf, opts.pricesAsOf) ?? 0);
    if (lag >= lagAfter) return 'amber';
  }
  return 'ok';
}

export function concentrationOf(
  holdings: { id: string; name: string; valueMinor: bigint }[],
  assetsMinor: bigint,
  topN = TOP_N,
): { rows: ConcentrationRow[]; topSharePct: number } {
  const open = holdings.filter(h => h.valueMinor > 0n).sort((a, b) => (a.valueMinor < b.valueMinor ? 1 : -1));
  const top = open.slice(0, topN);
  const denom = assetsMinor > 0n ? assetsMinor : 1n;
  const rows: ConcentrationRow[] = top.map(h => ({
    id: h.id,
    name: h.name,
    valueMinor: h.valueMinor,
    pct: Number((h.valueMinor * 10000n) / denom) / 100,
  }));
  const topSum = top.reduce((s, h) => s + h.valueMinor, 0n);
  const topSharePct = assetsMinor > 0n ? Number((topSum * 10000n) / assetsMinor) / 100 : 0;
  return { rows, topSharePct };
}

export function buildBooksHealth(opts: {
  holdings: Holding[];
  events: LedgerEvent[];
  derived: DerivedState;
  members: FamilyMember[];
  casLatest: CasLatestState;
  selectedMemberId: string;
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK';
  ibjaPerGramMinor?: bigint | null;
  todayIso: string;
}): BooksHealth {
  const findings: HealthFinding[] = [];
  const inScope = (ownerId: string) => opts.selectedMemberId === 'ALL' || ownerId === opts.selectedMemberId;
  const holdings = opts.holdings.filter(h => inScope(h.ownerId));
  const events = opts.events.filter(e => inScope(e.ownerId));

  const costEq: Holding[] = [];
  for (const h of holdings) {
    if (h.category === 'LIABILITY') continue;
    if (!isOpenPosition(h, opts.derived)) continue;
    const m = metricsOf(h, opts.derived, opts.sgbMode, opts.ibjaPerGramMinor);
    if (isUnknownInvested(h, events, m.cost, m.value)) costEq.push(h);
  }
  if (costEq.length > 0) {
    const names = costEq.slice(0, 2).map(h => displayHoldingName(h.name)).join(', ');
    findings.push({
      kind: 'COST_EQUALS_VALUE',
      tone: 'amber',
      title: costEq.length === 1
        ? 'Invested still matches current value'
        : `${costEq.length} holdings still use statement value as invested`,
      detail: costEq.length === 1
        ? `${names} has no separate purchase cost. CAS does not print that.`
        : `${names}${costEq.length > 2 ? ' and others' : ''}. Edit invested on the holding if you know what you paid.`,
      count: costEq.length,
      go: 'HOLDINGS',
    });
  }

  const owners = opts.selectedMemberId === 'ALL'
    ? Object.keys(opts.casLatest.byOwner || {})
    : [opts.selectedMemberId];
  let worstStale: { type: string; days: number; asOf: string } | null = null;
  for (const ownerId of owners) {
    const pack = opts.casLatest.byOwner[ownerId] || {};
    for (const [type, meta] of Object.entries(pack)) {
      const asOf = meta?.periodTo;
      if (!asOf) continue;
      const days = daysBetweenIso(asOf, opts.todayIso);
      if (days == null || days < STALE_AMBER_DAYS) continue;
      if (!worstStale || days > worstStale.days) {
        worstStale = { type, days, asOf };
      }
    }
  }
  if (worstStale) {
    const label = CAS_FILE_TYPE_LABEL[worstStale.type as CasFileType] || worstStale.type;
    findings.push({
      kind: 'STALE_STATEMENT',
      tone: worstStale.days >= STALE_RED_DAYS ? 'red' : 'amber',
      title: `${shortIssuer(label)} statement is ${worstStale.days} days old`,
      detail: `Last confirmed through ${worstStale.asOf}. Read a newer CAS or NPS file to refresh.`,
      go: 'DOCUMENTS',
    });
  }

  const currentFy = financialYearOf(opts.todayIso);
  const prevFy = previousFinancialYear(currentFy);
  const prevEnd = fyEndIso(prevFy);
  if (prevFy && prevEnd) {
    const missing: string[] = [];
    for (const h of holdings) {
      const kind = interestKindOf(h);
      if (!kind) continue;
      if (!isOpenPosition(h, opts.derived) && h.category !== 'GOLD_SGB') continue;
      if (h.category === 'GOLD_SGB' && !isOpenPosition(h, opts.derived)) continue;
      const existed = holdingExistedBy(h, events, prevEnd);
      if (!existed) continue;
      const posted = events.some(
        e => e.holdingId === h.id && e.eventType === 'INTEREST' && financialYearOf(e.eventDate) === prevFy,
      );
      if (!posted) missing.push(displayHoldingName(h.name));
    }
    if (missing.length > 0) {
      findings.push({
        kind: 'MISSING_FY_INTEREST',
        tone: 'amber',
        title: missing.length === 1
          ? `No FY ${prevFy} interest on ${missing[0]}`
          : `${missing.length} deposits have no FY ${prevFy} interest`,
        detail: 'Open the holding and confirm the interest. Payout schemes post on their own dates; PPF, SSY and EPF post on 31 March.',
        count: missing.length,
        go: 'HOLDINGS',
      });
    }
  }

  const panGaps = opts.members.filter(m => {
    if (!inScope(m.id)) return false;
    const hasCas = !!opts.casLatest.byOwner[m.id] && Object.keys(opts.casLatest.byOwner[m.id] || {}).length > 0;
    if (!hasCas) return false;
    return !m.panForStatements;
  });
  if (panGaps.length > 0) {
    findings.push({
      kind: 'MEMBER_PAN_MISSING',
      tone: 'amber',
      title: panGaps.length === 1
        ? `${panGaps[0].name} has no PAN on file`
        : `${panGaps.length} family members are missing a PAN`,
      detail: 'CAS passwords and matching use PAN. Save it on the Family screen.',
      count: panGaps.length,
      go: 'FAMILY',
    });
  }

  const tone: HealthTone = findings.some(f => f.tone === 'red')
    ? 'red'
    : findings.some(f => f.tone === 'amber')
      ? 'amber'
      : 'ok';
  const headline = tone === 'ok'
    ? 'Books look consistent'
    : findings.length === 1
      ? '1 thing to check'
      : `${findings.length} things to check`;

  return { tone, headline, findings };
}

function ymd(iso: string): { y: number; m: number; d: number } | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso || '');
  if (!m) return null;
  return { y: Number(m[1]), m: Number(m[2]), d: Number(m[3]) };
}

function shortIssuer(label: string): string {
  return label.split(' ')[0] || label;
}

function holdingExistedBy(h: Holding, events: LedgerEvent[], iso: string): boolean {
  if (h.acquiredOn && h.acquiredOn.slice(0, 10) <= iso) return true;
  return events.some(e => e.holdingId === h.id && e.eventDate && e.eventDate.slice(0, 10) <= iso);
}

export interface StatementStep {
  date: string;
  valueMinor: bigint;
  investedMinor: bigint;
  issuers: string[];
  /** Horizontal pad to today — not a new statement. */
  isLivePad?: boolean;
}

export function issuerLabel(issuer: string): string {
  return CAS_FILE_TYPE_LABEL[issuer as CasFileType] || issuer;
}

/**
 * Last-known statement value after each file.
 * A later CDSL replaces the previous CDSL for that member; CAMS stacks beside it.
 * The line stays flat until the next file — we do not invent NAV in between.
 */
export function buildStatementSteps(opts: {
  rows: StatementSnapRow[];
  selectedMemberId: string;
  todayIso: string;
}): StatementStep[] {
  const inScope = (ownerId: string) => opts.selectedMemberId === 'ALL' || ownerId === opts.selectedMemberId;
  const rows = opts.rows
    .filter(r => r.date && inScope(r.ownerId))
    .slice()
    .sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : a.issuer.localeCompare(b.issuer)));
  if (rows.length === 0) return [];

  const dates: string[] = [];
  for (const r of rows) {
    if (dates[dates.length - 1] !== r.date) dates.push(r.date);
  }

  const latest = new Map<string, { value: bigint; invested: bigint; issuer: string }>();
  const steps: StatementStep[] = [];
  let i = 0;
  for (const date of dates) {
    while (i < rows.length && rows[i].date === date) {
      const r = rows[i];
      latest.set(`${r.ownerId}\0${r.issuer}`, { value: r.valueMinor, invested: r.investedMinor, issuer: r.issuer });
      i += 1;
    }
    let value = 0n;
    let invested = 0n;
    const issuers: string[] = [];
    for (const slot of latest.values()) {
      value += slot.value;
      invested += slot.invested;
      if (!issuers.includes(slot.issuer)) issuers.push(slot.issuer);
    }
    steps.push({ date, valueMinor: value, investedMinor: invested, issuers });
  }

  const last = steps[steps.length - 1];
  if (last && opts.todayIso && last.date < opts.todayIso) {
    steps.push({
      date: opts.todayIso,
      valueMinor: last.valueMinor,
      investedMinor: last.investedMinor,
      issuers: last.issuers,
      isLivePad: true,
    });
  }
  return steps;
}

/** Carry last printed invested forward; live pad uses today's known invested. */
export function investedSeriesFrom(steps: StatementStep[], liveInvestedMinor: bigint): StatementStep[] {
  let last = 0n;
  return steps.map(s => {
    const raw = s.isLivePad ? (liveInvestedMinor > 0n ? liveInvestedMinor : s.investedMinor) : s.investedMinor;
    const v = raw > 0n ? raw : last;
    if (v > 0n) last = v;
    return { ...s, investedMinor: v };
  });
}

export function firstToLastPct(values: bigint[]): number | null {
  const nz = values.filter(v => v > 0n);
  if (nz.length < 2) return null;
  const first = nz[0];
  const last = nz[nz.length - 1];
  return Number(last - first) / Number(first) * 100;
}

export interface SinceLastMover {
  id: string;
  name: string;
  thenMinor: bigint;
  nowMinor: bigint;
  deltaMinor: bigint;
}

export interface SinceLastStatement {
  asOf: string | null;
  thenMinor: bigint;
  nowMinor: bigint;
  deltaMinor: bigint;
  movers: SinceLastMover[];
  newCount: number;
}

/**
 * Current value of holdings vs their last statement CMV.
 * Coverage is what the files reported — PPF/FD never on CAS are omitted.
 */
export function buildSinceLastStatement(opts: {
  snaps: StatementHoldingSnap[];
  holdings: Holding[];
  derived: DerivedState;
  events: LedgerEvent[];
  selectedMemberId: string;
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK';
  ibjaPerGramMinor?: bigint | null;
  todayIso: string;
}): SinceLastStatement {
  const inScope = (ownerId: string) => opts.selectedMemberId === 'ALL' || ownerId === opts.selectedMemberId;
  const latest = new Map<string, StatementHoldingSnap>();
  let asOf: string | null = null;
  for (const s of opts.snaps) {
    if (!inScope(s.ownerId) || !s.date) continue;
    const prev = latest.get(s.holdingId);
    if (!prev || s.date >= prev.date) latest.set(s.holdingId, s);
    if (!asOf || s.date > asOf) asOf = s.date;
  }
  if (!asOf) {
    return { asOf: null, thenMinor: 0n, nowMinor: 0n, deltaMinor: 0n, movers: [], newCount: 0 };
  }

  let thenMinor = 0n;
  let nowMinor = 0n;
  const movers: SinceLastMover[] = [];
  for (const snap of latest.values()) {
    const h = opts.holdings.find(x => x.id === snap.holdingId);
    if (!h) continue;
    const now = metricsOf(h, opts.derived, opts.sgbMode, opts.ibjaPerGramMinor).value;
    thenMinor += snap.valueMinor;
    nowMinor += now;
    const delta = now - snap.valueMinor;
    if (delta !== 0n) {
      movers.push({
        id: h.id,
        name: displayHoldingName(h.name),
        thenMinor: snap.valueMinor,
        nowMinor: now,
        deltaMinor: delta,
      });
    }
  }
  movers.sort((a, b) => {
    const aa = a.deltaMinor < 0n ? -a.deltaMinor : a.deltaMinor;
    const bb = b.deltaMinor < 0n ? -b.deltaMinor : b.deltaMinor;
    return aa < bb ? 1 : aa > bb ? -1 : 0;
  });

  let newCount = 0;
  for (const h of opts.holdings) {
    if (!inScope(h.ownerId) || h.category === 'LIABILITY') continue;
    if (latest.has(h.id)) continue;
    if (!isOpenPosition(h, opts.derived)) continue;
    const first = opts.events
      .filter(e => e.holdingId === h.id && e.eventDate)
      .sort((a, b) => a.eventDate.localeCompare(b.eventDate))[0];
    const start = (h.acquiredOn || first?.eventDate || '').slice(0, 10);
    if (start && start > asOf) newCount += 1;
  }

  return {
    asOf,
    thenMinor,
    nowMinor,
    deltaMinor: nowMinor - thenMinor,
    movers: movers.slice(0, 5),
    newCount,
  };
}

export interface UnknownCostRow {
  id: string;
  name: string;
  valueMinor: bigint;
}

/** Pass D: CAS printed CMV as invested — user still needs to add what they paid. */
export function unknownCostRows(opts: {
  holdings: Holding[];
  events: LedgerEvent[];
  derived: DerivedState;
  sgbMode: 'MARKET_TRADED' | 'RBI_BENCHMARK';
  ibjaPerGramMinor?: bigint | null;
}): UnknownCostRow[] {
  const out: UnknownCostRow[] = [];
  for (const h of opts.holdings) {
    if (h.category === 'LIABILITY') continue;
    if (!isOpenPosition(h, opts.derived)) continue;
    const m = metricsOf(h, opts.derived, opts.sgbMode, opts.ibjaPerGramMinor);
    if (!isUnknownInvested(h, opts.events, m.cost, m.value)) continue;
    out.push({ id: h.id, name: displayHoldingName(h.name), valueMinor: m.value });
  }
  out.sort((a, b) => (a.valueMinor < b.valueMinor ? 1 : -1));
  return out;
}

