/**
 * Snapshot reconciliation. The fix for CAS re-import.
 *
 * THE PROBLEM
 * A CAS holdings statement is a SNAPSHOT — "as of 31 Jul 2026 you hold 110
 * units" — not a transaction. Recording it as a BUY means importing the
 * September and January statements adds both, so a portfolio that grew from
 * 1.1 crore to 1.5 crore reads as 2.6 crore.
 *
 * THE RULE
 * Fold the ledger AS OF the statement date, compare with the statement, and
 * record ONLY the difference. Importing the same statement twice changes
 * nothing. Importing a later statement adds only what actually changed.
 *
 * Implements SRS-IMP-009, SRS-IMP-010, SRS-IMP-011.
 */
import { LedgerEvent, Holding, AssetCategory } from '../types';
import { foldHolding } from './lots';
import { QTY_SCALE } from './money';

export type SnapshotOutcome =
  | 'MATCH'          // ledger already agrees; nothing to record
  | 'NEW_POSITION'   // no history at all; record an opening balance
  | 'ADJUST_UP'      // statement holds more than the ledger knows about
  | 'ADJUST_DOWN'    // statement holds less
  | 'CLOSED'         // statement shows nil; position was exited
  | 'CONFLICT';      // cannot be resolved automatically; ask the user

export interface SnapshotLine {
  holdingId: string;
  holdingName: string;
  category: AssetCategory;
  statementDate: string;
  /** Units held as printed on the statement, scaled. */
  quantityScaled: bigint;
  /** NAV or market price on the statement date, minor units. */
  unitPriceMinor: bigint;
  /** Cumulative amount invested, where the statement prints it. */
  cumulativeInvestedMinor?: bigint;
  isin?: string;
  folio?: string;
}

export interface ReconciledLine {
  line: SnapshotLine;
  outcome: SnapshotOutcome;
  expectedQtyScaled: bigint;
  statementQtyScaled: bigint;
  deltaQtyScaled: bigint;
  /** Plain-language explanation for the review screen (SRS-UX-033). */
  message: string;
  /** The single event to append, or null when nothing should be recorded. */
  suggestedEvent: LedgerEvent | null;
  priceMinor: bigint;
}

export interface ReconciliationResult {
  statementDate: string;
  lines: ReconciledLine[];
  counts: Record<SnapshotOutcome, number>;
  netValueChangeMinor: bigint;
  warnings: string[];
}

/** Quantity tolerance: 1e-6 units, absorbing registrar rounding. */
const QTY_TOLERANCE = 100n;

function newId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Fold a holding's events restricted to those on or before asOf.
 * A statement dated in the past must not be reconciled against events
 * that had not happened yet.
 */
export function expectedQuantityAsOf(
  events: LedgerEvent[],
  category: AssetCategory,
  asOf: string
): { qtyScaled: bigint; hasHistory: boolean; errors: string[] } {
  const upTo = events.filter(e => e.eventDate <= asOf);
  if (upTo.length === 0) return { qtyScaled: 0n, hasHistory: false, errors: [] };
  const fold = foldHolding(upTo, category);
  return { qtyScaled: fold.totalQtyScaled, hasHistory: true, errors: fold.errors };
}

/**
 * Reconcile one statement line against the ledger.
 * Returns at most ONE event. Never re-adds the full statement quantity.
 */
export function reconcileLine(
  line: SnapshotLine,
  events: LedgerEvent[],
  ownerId: string
): ReconciledLine {
  const { qtyScaled: expected, hasHistory, errors } =
    expectedQuantityAsOf(events, line.category, line.statementDate);

  const stated = line.quantityScaled;
  const delta = stated - expected;
  const absDelta = delta < 0n ? -delta : delta;

  const base = {
    line,
    expectedQtyScaled: expected,
    statementQtyScaled: stated,
    deltaQtyScaled: delta,
    priceMinor: line.unitPriceMinor,
  };

  const units = (q: bigint) => (Number(q) / Number(QTY_SCALE)).toLocaleString('en-IN',
    { maximumFractionDigits: 3 });

  if (absDelta <= QTY_TOLERANCE) {
    return { ...base, outcome: 'MATCH',
      message: `Already up to date — ${units(stated)} units.`,
      suggestedEvent: null };
  }

  if (!hasHistory) {
    let unitCost = line.unitPriceMinor;
    if (line.cumulativeInvestedMinor && stated > 0n) {
      const derived = (line.cumulativeInvestedMinor * QTY_SCALE) / stated;
      if (derived > 0n) unitCost = derived;
    }
    return { ...base, outcome: 'NEW_POSITION',
      message: `New to your records — ${units(stated)} units. ` +
        `We have no purchase history, so this is recorded as an opening balance.`,
      suggestedEvent: {
        id: newId('ob'), holdingId: line.holdingId, holdingName: line.holdingName,
        ownerId, eventType: 'OPENING_BALANCE', eventDate: line.statementDate,
        quantityScaled: stated.toString(), unitPriceMinor: unitCost.toString(),
        totalAmountMinor: ((stated * unitCost) / QTY_SCALE).toString(),
        notes: 'Opening balance from statement — no purchase history available',
        createdAt: new Date().toISOString(),
      } as LedgerEvent };
  }

  if (stated === 0n) {
    return { ...base, outcome: 'CLOSED',
      message: `The statement shows nothing left, but your records expect ` +
        `${units(expected)} units. Recording the exit.`,
      suggestedEvent: {
        id: newId('adj'), holdingId: line.holdingId, holdingName: line.holdingName,
        ownerId, eventType: 'SNAPSHOT_ADJUSTMENT', eventDate: line.statementDate,
        quantityScaled: (-expected).toString(),
        unitPriceMinor: line.unitPriceMinor.toString(),
        totalAmountMinor: ((expected * line.unitPriceMinor) / QTY_SCALE).toString(),
        notes: 'Position closed per statement',
        createdAt: new Date().toISOString(),
      } as LedgerEvent };
  }

  const direction: SnapshotOutcome = delta > 0n ? 'ADJUST_UP' : 'ADJUST_DOWN';
  const verb = delta > 0n ? 'more' : 'fewer';
  return { ...base, outcome: direction,
    message: `Statement shows ${units(stated)} units; your records have ` +
      `${units(expected)}. Recording the ${units(absDelta)} ${verb} — not the whole holding.`,
    suggestedEvent: {
      id: newId('adj'), holdingId: line.holdingId, holdingName: line.holdingName,
      ownerId, eventType: 'SNAPSHOT_ADJUSTMENT', eventDate: line.statementDate,
      quantityScaled: delta.toString(),
      unitPriceMinor: line.unitPriceMinor.toString(),
      totalAmountMinor: ((absDelta * line.unitPriceMinor) / QTY_SCALE).toString(),
      notes: errors.length
        ? `Adjusted to statement balance. Note: ${errors[0]}`
        : 'Adjusted to statement balance',
      createdAt: new Date().toISOString(),
    } as LedgerEvent };
}

/** Merge lots of the same holding (same ISIN printed twice on a CAS). */
export function coalesceSnapshotLines(lines: SnapshotLine[]): SnapshotLine[] {
  const byId = new Map<string, SnapshotLine>();
  const order: string[] = [];
  for (const line of lines) {
    const prev = byId.get(line.holdingId);
    if (!prev) {
      byId.set(line.holdingId, { ...line });
      order.push(line.holdingId);
      continue;
    }
    const invested =
      prev.cumulativeInvestedMinor != null || line.cumulativeInvestedMinor != null
        ? (prev.cumulativeInvestedMinor ?? 0n) + (line.cumulativeInvestedMinor ?? 0n)
        : undefined;
    byId.set(line.holdingId, {
      ...prev,
      quantityScaled: prev.quantityScaled + line.quantityScaled,
      unitPriceMinor: line.unitPriceMinor || prev.unitPriceMinor,
      cumulativeInvestedMinor: invested,
      isin: prev.isin || line.isin,
      folio: prev.folio || line.folio,
    });
  }
  return order.map(id => byId.get(id)!);
}

/** Reconcile a whole statement. */
export function reconcileStatement(
  rawLines: SnapshotLine[],
  allEvents: LedgerEvent[],
  holdings: Holding[],
  ownerId: string
): ReconciliationResult {
  const lines = coalesceSnapshotLines(rawLines);
  const byHolding = new Map<string, LedgerEvent[]>();
  for (const e of allEvents) {
    const l = byHolding.get(e.holdingId) || [];
    l.push(e);
    byHolding.set(e.holdingId, l);
  }

  const counts: Record<SnapshotOutcome, number> = {
    MATCH: 0, NEW_POSITION: 0, ADJUST_UP: 0, ADJUST_DOWN: 0, CLOSED: 0, CONFLICT: 0,
  };
  const warnings: string[] = [];
  const out: ReconciledLine[] = [];
  let netChange = 0n;

  for (const line of lines) {
    const r = reconcileLine(line, byHolding.get(line.holdingId) ?? [], ownerId);
    counts[r.outcome]++;
    if (r.suggestedEvent) {
      netChange += (r.deltaQtyScaled * line.unitPriceMinor) / QTY_SCALE;
    }
    out.push(r);
  }

  // A holding the app knows about that the statement does not mention is NOT
  // assumed closed — the statement may simply not cover that custodian.
  const mentioned = new Set(lines.map(l => l.holdingId));
  const silent = holdings.filter(h => !mentioned.has(h.id));
  if (silent.length > 0) {
    warnings.push(
      `${silent.length} holding(s) are not on this statement and have been left alone. ` +
      `A statement from one custodian does not cover the others.`);
  }

  return {
    statementDate: lines[0]?.statementDate ?? '',
    lines: out,
    counts,
    netValueChangeMinor: netChange,
    warnings,
  };
}

/** Events to append for the lines the user accepted. */
export function eventsToCommit(
  result: ReconciliationResult,
  acceptedHoldingIds: Set<string>
): LedgerEvent[] {
  return result.lines
    .filter(l => l.suggestedEvent && acceptedHoldingIds.has(l.line.holdingId))
    .map(l => l.suggestedEvent as LedgerEvent);
}

/**
 * Zero-quantity snapshot lines for holdings that were on the previous
 * statement of the same issuer but are missing from this one.
 * Used so a later CDSL/NSDL/CAMS/KFin file replaces — not appends — the last one.
 */
export function snapshotLinesToClose(
  previousHoldingIds: string[],
  currentLines: SnapshotLine[],
  holdings: Holding[],
  statementDate: string,
  events: LedgerEvent[] = []
): SnapshotLine[] {
  const mentioned = new Set(currentLines.map(l => l.holdingId));
  const out: SnapshotLine[] = [];
  for (const id of previousHoldingIds) {
    if (mentioned.has(id)) continue;
    const h = holdings.find(x => x.id === id);
    if (!h) continue;
    if (events.length > 0) {
      const { qtyScaled } = expectedQuantityAsOf(
        events.filter(e => e.holdingId === id),
        h.category,
        statementDate
      );
      if (qtyScaled === 0n) continue;
    }
    out.push({
      holdingId: h.id,
      holdingName: h.name,
      category: h.category,
      statementDate,
      quantityScaled: 0n,
      unitPriceMinor: BigInt(h.currentPriceMinor || '0'),
      isin: h.isin,
      folio: h.symbolOrFolio,
    });
  }
  return out;
}
