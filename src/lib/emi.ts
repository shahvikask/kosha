/**
 * Loan EMI catch-up. Loans live on `holding` + append-only REPAYMENT events.
 * There is no server clock: on vault open (and on save) we post every EMI
 * whose date is on or before today, while status is PAYING.
 */
import { Holding, LedgerEvent, EmiStatus } from '../types';

export function addCalendarMonths(iso: string, months: number): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const y = Number(m[1]);
  const mo = Number(m[2]) - 1;
  const day = Number(m[3]);
  const dt = new Date(Date.UTC(y, mo + months, 1));
  const last = new Date(Date.UTC(dt.getUTCFullYear(), dt.getUTCMonth() + 1, 0)).getUTCDate();
  const d = Math.min(day, last);
  return `${dt.getUTCFullYear()}-${String(dt.getUTCMonth() + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

export function dueEmiDates(nextIso: string, remaining: number, asOf: string): string[] {
  if (!nextIso || remaining <= 0) return [];
  const dates: string[] = [];
  let d = nextIso;
  while (dates.length < remaining && d <= asOf) {
    dates.push(d);
    d = addCalendarMonths(d, 1);
  }
  return dates;
}

export function effectiveEmiStatus(h: Pick<Holding, 'emiStatus' | 'monthlyEmiMinor' | 'nextCouponDate'>): EmiStatus | undefined {
  if (h.emiStatus) return h.emiStatus;
  if (h.monthlyEmiMinor && h.nextCouponDate) return 'PAYING';
  return undefined;
}

export function resumeEmiDate(nextIso: string | undefined, asOf: string): string {
  if (!nextIso) return asOf;
  if (nextIso >= asOf) return nextIso;
  let d = nextIso;
  let guard = 0;
  while (d < asOf && guard++ < 360) d = addCalendarMonths(d, 1);
  return d;
}

export interface EmiCatchUpPlan {
  events: Omit<LedgerEvent, 'id' | 'createdAt'>[];
  nextCouponDate?: string;
  remainingEmiCount: number;
  outstandingMinor: string;
  emiStatus: EmiStatus;
}

export function planEmiCatchUp(
  h: Holding,
  asOf: string,
  alreadyPostedDates?: Iterable<string>
): EmiCatchUpPlan | null {
  if (h.category !== 'LIABILITY') return null;
  if (effectiveEmiStatus(h) !== 'PAYING') return null;
  const emi = BigInt(h.monthlyEmiMinor || '0');
  if (emi <= 0n) return null;
  if (!h.nextCouponDate) return null;

  const remaining = h.remainingEmiCount && h.remainingEmiCount > 0 ? h.remainingEmiCount : 0;
  if (remaining <= 0) return null;

  const posted = new Set(alreadyPostedDates || []);
  const dates = dueEmiDates(h.nextCouponDate, remaining, asOf).filter(d => !posted.has(d));
  if (dates.length === 0) return null;

  let outstanding = BigInt(h.currentPriceMinor || '0');
  const events: EmiCatchUpPlan['events'] = [];
  for (const date of dates) {
    if (outstanding <= 0n) break;
    const pay = outstanding < emi ? outstanding : emi;
    outstanding -= pay;
    events.push({
      holdingId: h.id,
      holdingName: h.name,
      ownerId: h.ownerId,
      eventType: 'REPAYMENT',
      eventDate: date,
      quantityScaled: '0',
      unitPriceMinor: pay.toString(),
      totalAmountMinor: pay.toString(),
      notes: 'Monthly EMI (automatic)',
    });
  }
  if (events.length === 0) return null;

  const postedCount = events.length;
  const newRemaining = Math.max(0, remaining - postedCount);
  const lastDate = events[postedCount - 1].eventDate;
  const done = outstanding <= 0n || newRemaining === 0;
  return {
    events,
    nextCouponDate: done ? undefined : addCalendarMonths(lastDate, 1),
    remainingEmiCount: newRemaining,
    outstandingMinor: outstanding.toString(),
    emiStatus: done ? 'STOPPED' : 'PAYING',
  };
}

export function applyEmiPlan(h: Holding, plan: EmiCatchUpPlan): Holding {
  return {
    ...h,
    currentPriceMinor: plan.outstandingMinor,
    remainingEmiCount: plan.remainingEmiCount,
    nextCouponDate: plan.nextCouponDate,
    emiStatus: plan.emiStatus,
    lastPriceUpdated: new Date().toISOString(),
    priceSource: 'EMI catch-up',
  };
}
