// FILE: src/lib/snapshot.test.ts
import { reconcileStatement, SnapshotLine, ReconciliationResult, snapshotLinesToClose, coalesceSnapshotLines } from './snapshot';
import { getSampleCdslSep2024, getSampleCdslJune2026, getSampleCdslJuly2026 } from './casFixtures';
import { derivePositions } from './positions';
import { QTY_SCALE } from './money';
import { LedgerEvent, Holding, StatementImportRecord } from '../types';
import { formatStatementDate, formatInrAmount } from './casPython';

let pass=0, fail=0;
const eq=(n:string,a:any,b:any)=>{const ok=String(a)===String(b);ok?pass++:fail++;console.log(`${ok?'ok  ':'FAIL'} ${n}: ${a}${ok?'':'  | want '+b}`)};
const R=(m:bigint)=>'Rs '+Math.round(Number(m)/1e4).toLocaleString('en-IN');

const keyOf=(r:StatementImportRecord)=> 'h_'+(r.isin || r.folio || r.holdingName).replace(/\s+/g,'_');

/** Convert parsed HOLDING records into snapshot lines. */
function toLines(res: ReturnType<typeof getSampleCdslJuly2026>): SnapshotLine[] {
  return res.importRecords.filter(r=>r.recordType==='HOLDING').map(r=>({
    holdingId: keyOf(r),
    holdingName: r.holdingName,
    category: r.category,
    statementDate: r.date,
    quantityScaled: BigInt(Math.round(r.quantity*1e8)),
    unitPriceMinor: BigInt(Math.round(r.pricePerUnit*1e4)),
    cumulativeInvestedMinor: r.cumulativeInvested ? BigInt(Math.round(r.cumulativeInvested*1e4)) : undefined,
    isin: r.isin, folio: r.folio,
  }));
}
function toHoldings(res: ReturnType<typeof getSampleCdslJuly2026>): Holding[] {
  return res.importRecords.filter(r=>r.recordType==='HOLDING').map(r=>({
    id: keyOf(r), name: r.holdingName, symbolOrFolio: r.folio ?? r.isin ?? '',
    isin: r.isin, category: r.category, subCategory:'', ownerId:'f1',
    quantityScaled:'0', averageCostPerUnitMinor:'0',
    currentPriceMinor: String(Math.round(r.pricePerUnit*1e4)),
    lastPriceUpdated:r.date, isPriceStale:false, priceSource:'CAS',
  } as Holding));
}

// Portfolio state
let events: LedgerEvent[] = [];
let holdings: Holding[] = [];

function importStatement(res: ReturnType<typeof getSampleCdslJuly2026>, label: string): ReconciliationResult {
  const lines = toLines(res);
  // merge holdings (reference data)
  for (const h of toHoldings(res)) if (!holdings.find(x=>x.id===h.id)) holdings.push(h);
  else holdings = holdings.map(x=>x.id===h.id ? {...x, currentPriceMinor:h.currentPriceMinor} : x);
  const rec = reconcileStatement(lines, events, holdings, 'f1');
  const commit = rec.lines.filter(l=>l.suggestedEvent).map(l=>l.suggestedEvent!);
  events = [...events, ...commit];
  const c = rec.counts;
  console.log(`  ${label}: match=${c.MATCH} new=${c.NEW_POSITION} up=${c.ADJUST_UP} down=${c.ADJUST_DOWN} closed=${c.CLOSED} -> ${commit.length} events appended`);
  return rec;
}
function portfolioValue(): bigint {
  const d = derivePositions(events, holdings);
  let t=0n;
  for (const h of holdings) t += ((d.positions.get(h.id)?.quantityScaled ?? 0n) * BigInt(h.currentPriceMinor))/QTY_SCALE;
  return t;
}

console.log('=== A. THE BUG: three statements in sequence ===');
importStatement(getSampleCdslSep2024(), 'Sep 2024');
const afterSep = portfolioValue();
console.log(`     portfolio = ${R(afterSep)}`);
importStatement(getSampleCdslJune2026(), 'Jun 2026');
const afterJun = portfolioValue();
console.log(`     portfolio = ${R(afterJun)}`);
importStatement(getSampleCdslJuly2026(), 'Jul 2026');
const afterJul = portfolioValue();
console.log(`     portfolio = ${R(afterJul)}`);
eq('final equals Jul statement, NOT the sum', Math.abs(Number(afterJul)/1e4 - 3894069) < 2, true);
eq('not the naive sum', Math.round(Number(afterJul)/1e4) === 10491379, false);
eq('event count is sane (not 35)', events.length <= 24, true);

console.log('\n=== B. Idempotence: re-import the SAME statement ===');
const before = portfolioValue(); const evBefore = events.length;
const again = importStatement(getSampleCdslJuly2026(), 'Jul 2026 again');
eq('every line MATCH', again.counts.MATCH, again.lines.length);
eq('zero events appended', events.length, evBefore);
eq('portfolio unchanged', portfolioValue(), before);

console.log('\n=== C. Out-of-order: importing an OLDER statement afterwards ===');
const qtyBefore = (()=>{const d=derivePositions(events,holdings);let t=0n;holdings.forEach(h=>{t+=d.positions.get(h.id)?.quantityScaled ?? 0n});return t})();
const old = importStatement(getSampleCdslSep2024(), 'Sep 2024 again (stale)');
eq('older statement matches as-of its own date', old.counts.ADJUST_UP + old.counts.ADJUST_DOWN + old.counts.NEW_POSITION, 0);
const qtyAfter = (()=>{const d=derivePositions(events,holdings);let t=0n;holdings.forEach(h=>{t+=d.positions.get(h.id)?.quantityScaled ?? 0n});return t})();
eq('no quantity corrupted by stale statement', qtyAfter, qtyBefore);

console.log('\n=== D. Genuine change is captured ===');
const jul = getSampleCdslJuly2026();
const bumped = { ...jul, importRecords: jul.importRecords.map(r =>
  r.recordType==='HOLDING' && r.holdingName.includes('Flexi Cap')
    ? { ...r, date:'2026-08-31', quantity: r.quantity + 500, currentValuation: (r.quantity+500)*r.pricePerUnit }
    : r.recordType==='HOLDING' ? { ...r, date:'2026-08-31' } : r ) };
const aug = importStatement(bumped as any, 'Aug 2026 (+500 units)');
eq('exactly one adjustment', aug.counts.ADJUST_UP, 1);
const adj = aug.lines.find(l=>l.outcome==='ADJUST_UP')!;
eq('delta is 500 not 4710', Number(adj.deltaQtyScaled)/1e8, 500);
eq('message names the difference', adj.message.includes('not the whole holding'), true);

console.log('\n=== E. Position closed ===');
const closed = { ...jul, importRecords: jul.importRecords.map(r =>
  r.recordType==='HOLDING' && r.holdingName.includes('Gilt')
    ? { ...r, date:'2026-09-30', quantity: 0, currentValuation: 0 }
    : r.recordType==='HOLDING' ? { ...r, date:'2026-09-30' } : r ) };
const sep = importStatement(closed as any, 'Sep 2026 (gilt exited)');
eq('one CLOSED', sep.counts.CLOSED, 1);
const d2 = derivePositions(events, holdings);
const gilt = holdings.find(h=>h.name.includes('Gilt'))!;
eq('gilt quantity now zero', Number(d2.positions.get(gilt.id)!.quantityScaled), 0);

console.log('\n=== F. Statement from one custodian leaves others alone ===');
const partial = { ...jul, importRecords: jul.importRecords.filter(r =>
  r.recordType==='HOLDING' && r.folio) };
const p = reconcileStatement(toLines(partial as any), events, holdings, 'f1');
eq('warns about untouched holdings', p.warnings.length > 0, true);
eq('warning mentions custodian', p.warnings[0].includes('custodian'), true);

console.log('\n=== G. Same-issuer overwrite closes vanished holdings ===');
{
  const current = toLines(jul);
  const prevIds = holdings.map(h => h.id);
  const gone = snapshotLinesToClose(prevIds, current.slice(0, 1), holdings, '2026-02-28', events);
  eq('closes holdings missing from later file', gone.length > 0, true);
  eq('closed lines have zero quantity', gone.every(l => l.quantityScaled === 0n), true);
}

eq('calendar date no UTC shift', formatStatementDate('2026-01-31'), '31 January 2026');
eq('inr grouping', formatInrAmount(26210612.93), '₹ 2,62,10,612.93');

console.log('\n=== H. Same-ISIN lots on one statement coalesce, re-import is MATCH ===');
{
  const lot = (qty: number): SnapshotLine => ({
    holdingId: 'h_avap',
    holdingName: 'AVAP',
    category: 'MUTUAL_FUND',
    statementDate: '2026-03-31',
    quantityScaled: BigInt(Math.round(qty * 1e8)),
    unitPriceMinor: BigInt(Math.round(221.04 * 1e4)),
    isin: 'INF109K01852',
    folio: '17001385/94',
  });
  const merged = coalesceSnapshotLines([lot(38.415), lot(11703.407)]);
  eq('coalesce to one line', merged.length, 1);
  eq('qty is the sum', merged[0].quantityScaled.toString(),
    (BigInt(Math.round(38.415 * 1e8)) + BigInt(Math.round(11703.407 * 1e8))).toString());

  const avapHold: Holding = {
    id: 'h_avap', name: 'AVAP', symbolOrFolio: '17001385/94', isin: 'INF109K01852',
    category: 'MUTUAL_FUND', subCategory: '', ownerId: 'f1',
    quantityScaled: '0', averageCostPerUnitMinor: '0',
    currentPriceMinor: String(Math.round(221.04 * 1e4)),
    lastPriceUpdated: '2026-03-31', isPriceStale: false, priceSource: 'CAS',
  };
  let evs: LedgerEvent[] = [];
  const first = reconcileStatement([lot(38.415), lot(11703.407)], evs, [avapHold], 'f1');
  eq('first import one NEW not two', first.counts.NEW_POSITION, 1);
  evs = [...evs, ...first.lines.filter(l => l.suggestedEvent).map(l => l.suggestedEvent!)];
  const again = reconcileStatement([lot(38.415), lot(11703.407)], evs, [avapHold], 'f1');
  eq('re-import MATCH not zeroed', again.counts.MATCH, 1);
  eq('re-import no extra events', again.counts.ADJUST_DOWN + again.counts.ADJUST_UP, 0);
}

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail?1:0);
