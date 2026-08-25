/**
 * Money-weighted return (XIRR). Implements SRS-ANA-001.
 * Returns null when it cannot converge. It never returns a guess.
 */
import { LedgerEvent } from '../types';
import { QTY_SCALE } from './money';

export interface CashFlow {
  date: string;   // ISO YYYY-MM-DD
  amount: number; // rupees; negative = money you put in
}

/** Build dated cash flows from the ledger, plus today's value as the closing flow. */
export function buildCashFlows(
  events: LedgerEvent[],
  currentValueRupees: number,
  asOf: string
): CashFlow[] {
  const flows: CashFlow[] = [];

  for (const ev of events) {
    const total = Number(BigInt(ev.totalAmountMinor || '0')) / 10000;
    const charges = Number(BigInt(ev.chargesMinor || '0')) / 10000;
    const qty = BigInt(ev.quantityScaled || '0');
    const unit = BigInt(ev.unitPriceMinor || '0');
    const computed = Number((qty * unit) / QTY_SCALE) / 10000;
    const amt = total !== 0 ? Math.abs(total) : Math.abs(computed);
    if (amt === 0) continue;

    switch (ev.eventType) {
      case 'BUY':
        flows.push({ date: ev.eventDate, amount: -(amt + charges) });
        break;
      case 'SELL':
        flows.push({ date: ev.eventDate, amount: amt - charges });
        break;
      case 'DIVIDEND':
      case 'INTEREST':
        flows.push({ date: ev.eventDate, amount: amt });
        break;
      default:
        break; // BONUS, SPLIT, VALUATION_UPDATE move no cash
    }
  }

  if (flows.length === 0) return [];
  flows.push({ date: asOf, amount: currentValueRupees });
  flows.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
  return flows;
}

function yearsFrom(base: number, iso: string): number {
  return (Date.parse(iso) - base) / (365.25 * 86400000);
}

function npv(rate: number, flows: CashFlow[], base: number): number {
  let sum = 0;
  for (const f of flows) {
    sum += f.amount / Math.pow(1 + rate, yearsFrom(base, f.date));
  }
  return sum;
}

/**
 * XIRR as an annualised percentage, or null if it will not converge.
 * Newton-Raphson, then bisection. Never returns a fabricated figure.
 */
export function xirr(flows: CashFlow[]): number | null {
  if (flows.length < 2) return null;
  const hasNeg = flows.some(f => f.amount < 0);
  const hasPos = flows.some(f => f.amount > 0);
  if (!hasNeg || !hasPos) return null;

  const base = Date.parse(flows[0].date);
  if (isNaN(base)) return null;

  // Newton-Raphson
  let rate = 0.1;
  for (let i = 0; i < 60; i++) {
    const f = npv(rate, flows, base);
    if (!isFinite(f)) break;
    if (Math.abs(f) < 0.0001) return Math.round(rate * 10000) / 100;
    const h = 1e-6;
    const d = (npv(rate + h, flows, base) - f) / h;
    if (!isFinite(d) || Math.abs(d) < 1e-12) break;
    const next = rate - f / d;
    if (!isFinite(next) || next <= -0.999999) break;
    if (Math.abs(next - rate) < 1e-9) return Math.round(next * 10000) / 100;
    rate = next;
  }

  // Bisection fallback
  let lo = -0.9999;
  let hi = 10;
  let flo = npv(lo, flows, base);
  let fhi = npv(hi, flows, base);
  if (!isFinite(flo) || !isFinite(fhi) || flo * fhi > 0) return null;

  for (let i = 0; i < 300; i++) {
    const mid = (lo + hi) / 2;
    const fm = npv(mid, flows, base);
    if (Math.abs(fm) < 0.0001) return Math.round(mid * 10000) / 100;
    if (flo * fm < 0) { hi = mid; fhi = fm; } else { lo = mid; flo = fm; }
  }
  return null;
}

/** Simple total gain percentage. NOT annualised. Label it honestly. */
export function totalGainPercent(investedRupees: number, gainRupees: number): number {
  if (investedRupees <= 0) return 0;
  return Math.round((gainRupees / investedRupees) * 10000) / 100;
}
