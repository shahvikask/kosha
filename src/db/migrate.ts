/**
 * One-time migration from localStorage to SQLite.
 *
 * Safety rules:
 *  - The localStorage data is NEVER deleted. It is renamed to a backup key.
 *    If the migration is wrong, the user has not lost anything.
 *  - The migration is idempotent: running it twice does nothing the second time.
 *  - It verifies before declaring success: event count and holding count must
 *    match, and the folded portfolio value must match to the paisa.
 */
import { Repository } from './repository';
import { LedgerEvent, Holding, FamilyMember } from '../types';
import { derivePositions, openingBalanceFor } from '../lib/positions';
import { QTY_SCALE } from '../lib/money';

export const LS_KEYS = {
  holdings: 'kosha_holdings',
  events: 'kosha_ledger_events',
  family: 'kosha_family_members',
  settings: 'kosha_settings',
};

export interface MigrationReport {
  ran: boolean;
  entities: number;
  holdings: number;
  events: number;
  /** Holdings that had a balance but no purchase history. */
  openingBalances: number;
  verified: boolean;
  valueBeforeMinor: string;
  valueAfterMinor: string;
  errors: string[];
}

function portfolioValueMinor(events: LedgerEvent[], holdings: Holding[]): bigint {
  const d = derivePositions(events, holdings);
  let total = 0n;
  for (const h of holdings) {
    const qty = d.positions.get(h.id)?.quantityScaled ?? 0n;
    total += (qty * BigInt(h.currentPriceMinor || '0')) / QTY_SCALE;
  }
  return total;
}

export async function migrateFromLocalStorage(
  repo: Repository,
  storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>
): Promise<MigrationReport> {
  const report: MigrationReport = {
    ran: false, entities: 0, holdings: 0, events: 0, openingBalances: 0,
    verified: false, valueBeforeMinor: '0', valueAfterMinor: '0', errors: [],
  };

  const done = await repo.getMeta('migrated_from_localstorage');
  if (done === 'true') return report;

  const read = <T>(k: string): T[] => {
    const raw = storage.getItem(k);
    if (!raw) return [];
    try {
      const v = JSON.parse(raw);
      return Array.isArray(v) ? v : [];
    } catch {
      report.errors.push(`Could not read stored ${k}. It was left untouched.`);
      return [];
    }
  };

  const holdings = read<Holding>(LS_KEYS.holdings);
  const events = read<LedgerEvent>(LS_KEYS.events);
  const family = read<FamilyMember>(LS_KEYS.family);

  if (holdings.length === 0 && events.length === 0 && family.length === 0) {
    await repo.setMeta('migrated_from_localstorage', 'true');
    return report;
  }

  report.valueBeforeMinor = portfolioValueMinor(events, holdings).toString();

  for (const m of family) { await repo.upsertEntity(m); report.entities++; }
  for (const h of holdings) { await repo.upsertHolding(h); report.holdings++; }

  // A holding may carry a stored quantity with no purchase history — that was
  // valid under the old model, where quantity lived on the holding row.
  // Under the ledger-as-truth model the quantity must exist as an EVENT, or the
  // fold returns zero and the position silently disappears. So we persist a real
  // OPENING_BALANCE event for each such holding rather than synthesising one at
  // read time. After this the ledger is complete and self-describing.
  const withEvents = new Set(events.map(e => e.holdingId));
  const opening: LedgerEvent[] = [];
  for (const h of holdings) {
    if (withEvents.has(h.id)) continue;
    const ob = openingBalanceFor(h, false);
    if (ob) opening.push(ob);
  }
  report.openingBalances = opening.length;

  await repo.appendEvents([...events, ...opening]);
  report.events = events.length + opening.length;
  report.ran = true;

  // Verify by reading back and re-folding.
  const backEvents = await repo.allEvents();
  const backHoldings = await repo.allHoldings();
  const expectedEvents = events.length + opening.length;
  const after = portfolioValueMinor(backEvents, backHoldings);
  report.valueAfterMinor = after.toString();

  report.verified =
    backEvents.length === expectedEvents &&
    backHoldings.length === holdings.length &&
    after.toString() === report.valueBeforeMinor;

  if (!report.verified) {
    report.errors.push(
      `Migration check failed. Events ${backEvents.length}/${expectedEvents}, ` +
      `holdings ${backHoldings.length}/${holdings.length}, ` +
      `value ${after} vs ${report.valueBeforeMinor}. ` +
      `Your original data has been kept and nothing was deleted.`);
    return report;
  }

  // Keep the old data under a backup key. Never delete it.
  for (const k of Object.values(LS_KEYS)) {
    const raw = storage.getItem(k);
    if (raw !== null) {
      storage.setItem(`${k}__pre_sqlite_backup`, raw);
      storage.removeItem(k);
    }
  }
  await repo.setMeta('migrated_from_localstorage', 'true');
  await repo.setMeta('migrated_at', new Date().toISOString());
  return report;
}
