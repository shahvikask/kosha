/**
 * Manual cost basis. CDSL/NSDL holdings CAS prints quantity and CMP,
 * not the purchase price or trade date. Contract notes and the CAMS/KFin
 * MF CAS do. Until those exist, the user enters what they paid.
 *
 * Append-only: a CORRECTION supersedes the opening parcel. Quantity stays.
 */
import { Holding, LedgerEvent } from '../types';
import { QTY_SCALE } from './money';
import { interestKindOf } from './interest';

const PARCEL_TYPES = new Set(['BUY', 'OPENING_BALANCE', 'SNAPSHOT_ADJUSTMENT', 'CORRECTION']);

export function earliestActiveParcel(
  events: LedgerEvent[],
  holdingId: string,
): LedgerEvent | null {
  const mine = events.filter(e => e.holdingId === holdingId);
  const superseded = new Set(
    mine.filter(e => e.supersedesEventId).map(e => e.supersedesEventId as string),
  );
  const parcels = mine
    .filter(e => {
      if (superseded.has(e.id)) return false;
      if (!PARCEL_TYPES.has(e.eventType)) return false;
      return BigInt(e.quantityScaled || '0') > 0n;
    })
    .sort((a, b) => {
      if (a.eventDate !== b.eventDate) return a.eventDate < b.eventDate ? -1 : 1;
      return a.id < b.id ? -1 : a.id > b.id ? 1 : 0;
    });
  return parcels[0] || null;
}

export function planInvestedCorrection(opts: {
  holding: Holding;
  events: LedgerEvent[];
  unitPriceMinor: bigint;
  acquiredOn: string;
}): Omit<LedgerEvent, 'id' | 'createdAt'> | null {
  const { holding, events, unitPriceMinor } = opts;
  if (unitPriceMinor <= 0n) return null;
  const parcel = earliestActiveParcel(events, holding.id);
  const qty = parcel
    ? BigInt(parcel.quantityScaled || '0')
    : BigInt(holding.quantityScaled || '0');
  if (qty <= 0n) return null;
  const acquiredOn = /^\d{4}-\d{2}-\d{2}$/.test(opts.acquiredOn)
    ? opts.acquiredOn
    : parcel?.eventDate || holding.acquiredOn || '';
  if (!/^\d{4}-\d{2}-\d{2}$/.test(acquiredOn)) return null;
  const total = (qty * unitPriceMinor) / QTY_SCALE;
  const samePrice = parcel != null && BigInt(parcel.unitPriceMinor || '0') === unitPriceMinor;
  const sameDate = parcel != null && parcel.eventDate === acquiredOn;
  if (parcel && samePrice && sameDate) return null;
  return {
    holdingId: holding.id,
    holdingName: holding.name,
    ownerId: holding.ownerId,
    eventType: 'CORRECTION',
    eventDate: acquiredOn,
    quantityScaled: qty.toString(),
    unitPriceMinor: unitPriceMinor.toString(),
    totalAmountMinor: total.toString(),
    notes: 'Purchase date you typed — not the day you edited this. The statement had no buy price.',
    supersedesEventId: parcel?.id,
  };
}

const RUPEE = 10_000n;
const BALANCE_IS_COST = new Set(['PPF', 'EPF', 'SSY', 'FD']);

export const UNKNOWN_INVESTED_LABEL = 'Need what you paid';

export function isUnknownInvested(
  holding: Holding,
  events: LedgerEvent[],
  costMinor: bigint,
  valueMinor: bigint,
): boolean {
  if (holding.category === 'LIABILITY') return false;
  const kind = interestKindOf(holding);
  if (kind && BALANCE_IS_COST.has(kind)) return false;
  if (costMinor <= 0n || valueMinor <= 0n) return false;
  const diff = costMinor > valueMinor ? costMinor - valueMinor : valueMinor - costMinor;
  if (diff > RUPEE) return false;
  return !events.some(
    e => e.holdingId === holding.id && (e.eventType === 'BUY' || e.eventType === 'CORRECTION'),
  );
}
