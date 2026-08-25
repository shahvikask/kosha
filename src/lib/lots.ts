/**
 * FIFO tax-lot engine. Implements SRS-TAX-001..004 and SRS-LED-003..004.
 * The ledger is the source of truth. Positions and gains are derived here.
 * Integer arithmetic only. No floating point.
 */
import { LedgerEvent, PurchaseParcel, AssetCategory } from '../types';
import { QTY_SCALE } from './money';

export interface RealisedDisposal {
  saleEventId: string;
  purchaseEventId: string;
  holdingId: string;
  holdingName: string;
  ownerId: string;
  purchaseDate: string;
  saleDate: string;
  holdingDays: number;
  qtyScaled: bigint;
  costMinor: bigint;
  proceedsMinor: bigint;
  gainMinor: bigint;
  isLongTerm: boolean;
}

export interface FoldResult {
  openParcels: PurchaseParcel[];
  totalQtyScaled: bigint;
  totalCostMinor: bigint;
  disposals: RealisedDisposal[];
  errors: string[];
}

/** Long-term threshold in days, by asset category. */
export function longTermDays(category: AssetCategory): number {
  switch (category) {
    case 'EQUITY':
    case 'MUTUAL_FUND':
      return 365;
    case 'REAL_ESTATE':
      return 730;
    default:
      return 1095;
  }
}

function daysBetween(fromIso: string, toIso: string): number {
  const a = Date.parse(fromIso);
  const b = Date.parse(toIso);
  if (isNaN(a) || isNaN(b)) return 0;
  return Math.floor((b - a) / 86400000);
}

/**
 * Deterministic fold over one holding's events, FIFO lot matching.
 * Sort: trade date, then event-type precedence, then event id.
 */
export function foldHolding(
  events: LedgerEvent[],
  category: AssetCategory
): FoldResult {
  const errors: string[] = [];

  const superseded = new Set(
    events.filter(e => e.supersedesEventId).map(e => e.supersedesEventId as string)
  );

  const precedence: Record<string, number> = {
    BUY: 1, BONUS: 2, SPLIT: 3, CORRECTION: 4,
    SELL: 5, DIVIDEND: 6, INTEREST: 7,
    VALUATION_UPDATE: 8, REPAYMENT: 9,
  };

  const ordered = events
    .filter(e => !superseded.has(e.id))
    .slice()
    .sort((x, y) => {
      if (x.eventDate !== y.eventDate) return x.eventDate < y.eventDate ? -1 : 1;
      const px = precedence[x.eventType] ?? 99;
      const py = precedence[y.eventType] ?? 99;
      if (px !== py) return px - py;
      return x.id < y.id ? -1 : x.id > y.id ? 1 : 0;
    });

  const parcels: PurchaseParcel[] = [];
  const disposals: RealisedDisposal[] = [];

  for (const ev of ordered) {
    const qty = BigInt(ev.quantityScaled || '0');
    const unit = BigInt(ev.unitPriceMinor || '0');
    const charges = BigInt(ev.chargesMinor || '0');

    if (ev.eventType === 'BUY') {
      if (qty <= 0n) { errors.push(`BUY ${ev.id} has no quantity`); continue; }
      parcels.push({
        eventId: ev.id,
        purchaseDate: ev.eventDate,
        originalQtyScaled: qty,
        remainingQtyScaled: qty,
        purchasePriceMinor: unit,
        totalCostMinor: (qty * unit) / QTY_SCALE + charges,
      });
    } else if (ev.eventType === 'BONUS') {
      // Bonus units carry nil cost and KEEP the original acquisition date
      // of the parcel they attach to (SRS-COR-003).
      if (qty <= 0n) continue;
      const earliest = parcels.find(p => p.remainingQtyScaled > 0n);
      parcels.push({
        eventId: ev.id,
        purchaseDate: earliest ? earliest.purchaseDate : ev.eventDate,
        originalQtyScaled: qty,
        remainingQtyScaled: qty,
        purchasePriceMinor: 0n,
        totalCostMinor: 0n,
      });
    } else if (ev.eventType === 'SPLIT') {
      // quantityScaled carries the ratio numerator, unitPriceMinor the denominator.
      const num = qty;
      const den = unit;
      if (num <= 0n || den <= 0n) { errors.push(`SPLIT ${ev.id} has a bad ratio`); continue; }
      for (const p of parcels) {
        p.originalQtyScaled = (p.originalQtyScaled * num) / den;
        p.remainingQtyScaled = (p.remainingQtyScaled * num) / den;
        if (p.remainingQtyScaled > 0n) {
          p.purchasePriceMinor = (p.totalCostMinor * QTY_SCALE) / p.remainingQtyScaled;
        }
      }
    } else if (ev.eventType === 'SELL') {
      let toSell = qty > 0n ? qty : -qty;
      if (toSell <= 0n) { errors.push(`SELL ${ev.id} has no quantity`); continue; }
      const proceedsPerUnit = unit;

      for (const p of parcels) {
        if (toSell <= 0n) break;
        if (p.remainingQtyScaled <= 0n) continue;

        const take = p.remainingQtyScaled < toSell ? p.remainingQtyScaled : toSell;
        const cost = (take * p.purchasePriceMinor) / QTY_SCALE;
        const proceeds = (take * proceedsPerUnit) / QTY_SCALE;
        const days = daysBetween(p.purchaseDate, ev.eventDate);

        disposals.push({
          saleEventId: ev.id,
          purchaseEventId: p.eventId,
          holdingId: ev.holdingId,
          holdingName: ev.holdingName,
          ownerId: ev.ownerId,
          purchaseDate: p.purchaseDate,
          saleDate: ev.eventDate,
          holdingDays: days,
          qtyScaled: take,
          costMinor: cost,
          proceedsMinor: proceeds,
          gainMinor: proceeds - cost,
          isLongTerm: days > longTermDays(category),
        });

        p.remainingQtyScaled -= take;
        p.totalCostMinor -= cost;
        toSell -= take;
      }

      if (toSell > 0n) {
        errors.push(
          `SELL ${ev.id} on ${ev.eventDate} exceeds units held. ` +
          `Sold more than was ever bought — check for a missing purchase record.`
        );
      }
    }
  }

  const open = parcels.filter(p => p.remainingQtyScaled > 0n);
  const totalQty = open.reduce((s, p) => s + p.remainingQtyScaled, 0n);
  const totalCost = open.reduce((s, p) => s + p.totalCostMinor, 0n);

  return { openParcels: open, totalQtyScaled: totalQty, totalCostMinor: totalCost, disposals, errors };
}

/** Indian financial year label for a date: '2025-26'. */
export function financialYearOf(iso: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return '';
  const y = d.getFullYear();
  const m = d.getMonth(); // 0 = Jan
  const start = m >= 3 ? y : y - 1;
  return `${start}-${String((start + 1) % 100).padStart(2, '0')}`;
}
