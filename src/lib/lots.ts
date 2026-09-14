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

/**
 * Days between two ISO dates, parsed as calendar dates.
 * Deliberately avoids Date.parse on bare YYYY-MM-DD, which is UTC-anchored
 * and shifts by a day in negative-offset timezones.
 */
export function calendarDays(fromIso: string, toIso: string): number {
  const a = toUTCDays(fromIso);
  const b = toUTCDays(toIso);
  if (a === null || b === null) return 0;
  return b - a;
}

function toUTCDays(iso: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return null;
  const ms = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return Math.floor(ms / 86400000);
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

  const byId = new Map(events.map(e => [e.id, e]));
  const effectiveType = (ev: LedgerEvent): string => {
    if (ev.eventType !== 'CORRECTION') return ev.eventType;
    const seen = new Set<string>([ev.id]);
    let cur: LedgerEvent | undefined = byId.get(ev.supersedesEventId || '');
    while (cur && cur.eventType === 'CORRECTION' && cur.supersedesEventId && !seen.has(cur.id)) {
      seen.add(cur.id);
      cur = byId.get(cur.supersedesEventId);
    }
    return cur?.eventType ?? 'OPENING_BALANCE';
  };

  const precedence: Record<string, number> = {
    OPENING_BALANCE: 0, BUY: 1, BONUS: 2, SPLIT: 3, CORRECTION: 4,
    SNAPSHOT_ADJUSTMENT: 4.5,
    SELL: 5, DIVIDEND: 6, INTEREST: 7,
    VALUATION_UPDATE: 8, REPAYMENT: 9,
  };

  const ordered = events
    .filter(e => !superseded.has(e.id))
    .slice()
    .sort((x, y) => {
      if (x.eventDate !== y.eventDate) return x.eventDate < y.eventDate ? -1 : 1;
      const px = precedence[effectiveType(x)] ?? 99;
      const py = precedence[effectiveType(y)] ?? 99;
      if (px !== py) return px - py;
      return x.id < y.id ? -1 : x.id > y.id ? 1 : 0;
    });

  const parcels: PurchaseParcel[] = [];
  const disposals: RealisedDisposal[] = [];

  for (const ev of ordered) {
    const qty = BigInt(ev.quantityScaled || '0');
    const unit = BigInt(ev.unitPriceMinor || '0');
    const charges = BigInt(ev.chargesMinor || '0');
    const kind = effectiveType(ev);

    if (kind === 'BUY' || kind === 'OPENING_BALANCE') {
      if (qty <= 0n) {
        if (ev.eventType !== 'CORRECTION') errors.push(`${kind} ${ev.id} has no quantity`);
        continue;
      }
      parcels.push({
        eventId: ev.id,
        purchaseDate: ev.eventDate,
        originalQtyScaled: qty,
        remainingQtyScaled: qty,
        purchasePriceMinor: unit,
        totalCostMinor: (qty * unit) / QTY_SCALE + charges,
      });
    } else if (kind === 'BONUS') {
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
    } else if (kind === 'SPLIT') {
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
    } else if (kind === 'SNAPSHOT_ADJUSTMENT') {
      // A statement said we hold more or less than the ledger knew.
      // Upward: open a parcel at the statement price, dated to the statement.
      // Downward: reduce parcels FIFO WITHOUT recording a taxable disposal —
      // we know units left, not what they sold for. The gains report is told.
      if (qty > 0n) {
        parcels.push({
          eventId: ev.id,
          purchaseDate: ev.eventDate,
          originalQtyScaled: qty,
          remainingQtyScaled: qty,
          purchasePriceMinor: unit,
          totalCostMinor: (qty * unit) / QTY_SCALE,
        });
      } else if (qty < 0n) {
        let toRemove = -qty;
        for (const p of parcels) {
          if (toRemove <= 0n) break;
          if (p.remainingQtyScaled <= 0n) continue;
          const take = p.remainingQtyScaled < toRemove ? p.remainingQtyScaled : toRemove;
          const cost = (take * p.purchasePriceMinor) / QTY_SCALE;
          p.remainingQtyScaled -= take;
          p.totalCostMinor -= cost;
          toRemove -= take;
        }
        // Downward snapshot is a holdings-statement close, not a sale.
        // Do not warn on every fold — that flooded Documents after JAN–JUL
        // CDSL re-imports. Only flag when the statement exceeds units held.
        if (toRemove > 0n) {
          errors.push(`Statement adjustment on ${ev.eventDate} exceeds units held.`);
        }
      }
    } else if (kind === 'SELL') {
      let toSell = qty > 0n ? qty : -qty;
      if (toSell <= 0n) { errors.push(`SELL ${ev.id} has no quantity`); continue; }
      const proceedsPerUnit = unit;

      for (const p of parcels) {
        if (toSell <= 0n) break;
        if (p.remainingQtyScaled <= 0n) continue;

        const take = p.remainingQtyScaled < toSell ? p.remainingQtyScaled : toSell;
        const cost = (take * p.purchasePriceMinor) / QTY_SCALE;
        const proceeds = (take * proceedsPerUnit) / QTY_SCALE;
        const days = calendarDays(p.purchaseDate, ev.eventDate);

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

/**
 * Indian financial year label for an ISO date: '2025-26'.
 * Parsed as a calendar date, not via Date.parse, so it is timezone-safe.
 */
export function financialYearOf(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return '';
  const y = Number(m[1]);
  const mo = Number(m[2]); // 1 = Jan
  const start = mo >= 4 ? y : y - 1;
  return `${start}-${String((start + 1) % 100).padStart(2, '0')}`;
}

/** Assessment year for a financial year: FY 2025-26 -> AY26-27. */
export function assessmentYearOf(iso: string): string {
  const fy = financialYearOf(iso);
  if (!fy) return '';
  const start = Number(fy.split('-')[0]);
  return `AY${String((start + 1) % 100).padStart(2, '0')}-${String((start + 2) % 100).padStart(2, '0')}`;
}
