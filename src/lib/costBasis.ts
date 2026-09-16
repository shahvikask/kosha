/**
 * Manual cost basis. CDSL/NSDL holdings CAS prints quantity and CMP,
 * not the purchase price or trade date. Contract notes and the CAMS/KFin
 * MF CAS do. Until those exist, the user enters what they paid.
 *
 * Append-only: a CORRECTION supersedes the opening parcel. Quantity stays.
 */
import { Holding, LedgerEvent } from '../types';
import { QTY_SCALE } from './money';

const PARCEL_TYPES = new Set(['BUY', 'OPENING_BALANCE', 'SNAPSHOT_ADJUSTMENT', 'CORRECTION']);

export function earliestActiveParcel(
  events: LedgerEvent[],
  holdingId: string
): LedgerEvent | null {
  const mine = events.filter(e => e.holdingId === holdingId);
  const superseded = new Set(
    mine.filter(e => e.supersedesEventId).map(e => e.supersedesEventId as string)
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
  const { holding, events, unitPriceMinor, acquiredOn } = opts;
  if (unitPriceMinor <= 0n) return null;
  if (!/^\d{4}-\d{2}-\d{2}$/.test(acquiredOn)) return null;
  const parcel = earliestActiveParcel(events, holding.id);
  if (!parcel) return null;
  const qty = BigInt(parcel.quantityScaled || '0');
  if (qty <= 0n) return null;
  const total = (qty * unitPriceMinor) / QTY_SCALE;
  const samePrice = BigInt(parcel.unitPriceMinor || '0') === unitPriceMinor;
  const sameDate = parcel.eventDate === acquiredOn;
  if (samePrice && sameDate) return null;
  return {
    holdingId: holding.id,
    holdingName: holding.name,
    ownerId: holding.ownerId,
    eventType: 'CORRECTION',
    eventDate: acquiredOn,
    quantityScaled: qty.toString(),
    unitPriceMinor: unitPriceMinor.toString(),
    totalAmountMinor: total.toString(),
    notes: 'Invested updated from your records (statement has no buy price)',
    supersedesEventId: parcel.id,
  };
}
