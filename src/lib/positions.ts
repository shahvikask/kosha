/**
 * Position derivation. Implements SRS-LED-004 and the architectural rule
 * that the ledger is the source of truth and holdings are reference data.
 *
 * Quantity, cost and realised gain are NEVER stored. They are folded from
 * events every time they are needed. A holding row carries identity and
 * price only: name, ISIN, category, current price, valuation attributes.
 */
import { LedgerEvent, Holding, AssetCategory } from '../types';
import { foldHolding, RealisedDisposal } from './lots';
import { QTY_SCALE } from './money';

export interface Position {
  holdingId: string;
  quantityScaled: bigint;
  costMinor: bigint;
  avgCostPerUnitMinor: bigint;
  realisedGainMinor: bigint;
  disposals: RealisedDisposal[];
  openParcelCount: number;
  errors: string[];
  /** True when this position came from a stored balance with no event history. */
  fromOpeningBalance: boolean;
}

export interface DerivedState {
  positions: Map<string, Position>;
  errors: string[];
}

/**
 * Synthesise an OPENING_BALANCE event for a holding that has stored
 * quantity but no ledger history. This is the migration path from the
 * old holdings-as-truth model, and it is also how a user records a
 * position whose purchase history they no longer have.
 *
 * Returns null when the holding already has events or has no quantity.
 */
export function openingBalanceFor(
  holding: Holding,
  hasEvents: boolean
): LedgerEvent | null {
  if (hasEvents) return null;
  const qty = BigInt(holding.quantityScaled || '0');
  if (qty <= 0n) return null;
  const avg = BigInt(holding.averageCostPerUnitMinor || '0');
  return {
    id: `ob_${holding.id}`,
    holdingId: holding.id,
    holdingName: holding.name,
    ownerId: holding.ownerId,
    eventType: 'OPENING_BALANCE',
    eventDate: (holding.lastPriceUpdated || new Date().toISOString()).split('T')[0],
    quantityScaled: qty.toString(),
    unitPriceMinor: avg.toString(),
    totalAmountMinor: ((qty * avg) / QTY_SCALE).toString(),
    notes: 'Opening balance — recorded without purchase history',
    createdAt: new Date().toISOString(),
  };
}

/**
 * Derive every position from the ledger. This is the single place in the
 * application where quantity and cost are computed.
 */
export function derivePositions(
  events: LedgerEvent[],
  holdings: Holding[]
): DerivedState {
  const byHolding = new Map<string, LedgerEvent[]>();
  for (const ev of events) {
    const list = byHolding.get(ev.holdingId) || [];
    list.push(ev);
    byHolding.set(ev.holdingId, list);
  }

  const positions = new Map<string, Position>();
  const allErrors: string[] = [];

  for (const h of holdings) {
    const own = byHolding.get(h.id) || [];
    const hasEvents = own.length > 0;

    let effective = own;
    let fromOpening = false;
    if (!hasEvents) {
      const ob = openingBalanceFor(h, false);
      if (ob) { effective = [ob]; fromOpening = true; }
    }

    const category: AssetCategory = h.category;
    const fold = foldHolding(effective, category);

    const realised = fold.disposals.reduce((s, d) => s + d.gainMinor, 0n);
    const avg = fold.totalQtyScaled > 0n
      ? (fold.totalCostMinor * QTY_SCALE) / fold.totalQtyScaled
      : 0n;

    const scoped = fold.errors.map(e => `${h.name}: ${e}`);
    allErrors.push(...scoped);

    positions.set(h.id, {
      holdingId: h.id,
      quantityScaled: fold.totalQtyScaled,
      costMinor: fold.totalCostMinor,
      avgCostPerUnitMinor: avg,
      realisedGainMinor: realised,
      disposals: fold.disposals,
      openParcelCount: fold.openParcels.length,
      errors: scoped,
      fromOpeningBalance: fromOpening,
    });
  }

  // Events referencing a holding that no longer exists are reported,
  // never silently dropped.
  byHolding.forEach((evs, hid) => {
    if (!positions.has(hid)) {
      allErrors.push(
        `${evs.length} record(s) reference a holding that is no longer listed ` +
        `(${evs[0].holdingName}). They are excluded from totals.`
      );
    }
  });

  return { positions, errors: allErrors };
}

/** Market value of a position at a given unit price. */
export function marketValueMinor(qtyScaled: bigint, unitPriceMinor: bigint): bigint {
  return (qtyScaled * unitPriceMinor) / QTY_SCALE;
}
