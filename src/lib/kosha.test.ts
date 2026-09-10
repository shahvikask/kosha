import { foldHolding, financialYearOf, assessmentYearOf, calendarDays } from './lots';
import { derivePositions, openingBalanceFor, marketValueMinor } from './positions';
import { ruleSetFor, classifyGain, isEquityOriented, grandfatheredCostMinor, applyRateMinor } from './taxRules';
import { encryptJson, decryptJson, isEncryptedBlob, generateRecoveryKey, maskPan, WrongPassphraseError } from './vault';
import { xirr } from './returns';
import { classifyHolding, EXPOSURES, WRAPPERS, exposureLabel, l2Label } from './taxonomy';
import { resolveSplit, apportion, buildTree, isHybrid, activeWrappers } from './lookthrough';
import { LedgerEvent, Holding } from '../types';
import { eventLabel, bucketOf, upcomingFromHoldings, displayHoldingName, accountTitle, inferAmcFromSchemeName, looksLikeSgb, metricsOf } from './holdingView';
import { formatStatementDate, latestAsOfForOwner, statementAsOfByMember, parseCasLatest } from './casPython';
import { addCalendarMonths, dueEmiDates, planEmiCatchUp, resumeEmiDate } from './emi';
import { absoluteReturnPct, cagrPct, incomeByFinancialYear, buildGrowthModel, firstActivityDate, formatPct } from './growth';
import { profileOf, holdingReturn } from './returnMetrics';
import { titleCaseName, passwordIssues, isValidPan, isValidEmail, normaliseAnswer, needsPasswordRotation, packHash, unpackHash, hashSecret, verifySecret } from './auth';
import { checkImportPan, pansMatch, panBlocksCommit, findDuplicatePanMember, panFingerprint } from './pan';
import { applyCatalogPrices, catalogToListed, searchListed, isEodPriceSource } from './eodPrices';
import { findMatchingHolding, holdingIdentityKey } from './matchHolding';
import { StatementImportRecord } from '../types';

let pass=0, fail=0;
const eq=(n:string,a:any,b:any)=>{const ok=String(a)===String(b);ok?pass++:fail++;console.log(`${ok?'ok  ':'FAIL'} ${n}: ${a}${ok?'':' | want '+b}`)};

const ev=(id:string,t:any,d:string,q:number,p:number,hid='h1'):LedgerEvent=>({
  id,holdingId:hid,holdingName:'Reliance',ownerId:'f1',eventType:t,eventDate:d,
  quantityScaled:String(BigInt(Math.round(q*1e8))),unitPriceMinor:String(BigInt(Math.round(p*1e4))),
  totalAmountMinor:String(BigInt(Math.round(q*p*1e4))),createdAt:'2024-01-01'});

const hold=(id:string,cat:any,qty:number,avg:number,price:number,sub=''):Holding=>({
  id,name:'H'+id,symbolOrFolio:'X',category:cat,subCategory:sub,ownerId:'f1',
  quantityScaled:String(BigInt(Math.round(qty*1e8))),
  averageCostPerUnitMinor:String(BigInt(Math.round(avg*1e4))),
  currentPriceMinor:String(BigInt(Math.round(price*1e4))),
  lastPriceUpdated:'2026-08-01T00:00:00Z',isPriceStale:false,priceSource:'test'});

console.log('--- A. Timezone safety (was a real bug) ---');
eq('FY 31 Mar 2025 = 2024-25', financialYearOf('2025-03-31'), '2024-25');
eq('FY 01 Apr 2025 = 2025-26', financialYearOf('2025-04-01'), '2025-26');
eq('AY for FY2025-26', assessmentYearOf('2025-06-10'), 'AY26-27');
eq('calendarDays exact', calendarDays('2024-03-14','2025-04-09'), 391);

console.log('\n--- B. Ledger as source of truth ---');
const holdings=[hold('h1','EQUITY',999,1,2950)];  // stored qty is WRONG on purpose
const events=[ev('e1','BUY','2024-03-14',100,2450),ev('e2','BUY','2024-09-01',50,2600),ev('e3','SELL','2025-04-09',40,2750)];
const d1=derivePositions(events,holdings);
const p1=d1.positions.get('h1')!;
eq('qty from LEDGER not stored 999', Number(p1.quantityScaled)/1e8, 110);
eq('cost derived', Number(p1.costMinor)/1e4, 277000);
eq('avg cost derived', Math.round(Number(p1.avgCostPerUnitMinor)/1e4), 2518);
eq('realised gain', Number(p1.realisedGainMinor)/1e4, 12000);
eq('market value', Number(marketValueMinor(p1.quantityScaled,BigInt(2950*1e4)))/1e4, 324500);

console.log('\n--- C. Opening balance migration ---');
const d2=derivePositions([],[hold('h9','EQUITY',75,1200,1500)]);
const p2=d2.positions.get('h9')!;
eq('legacy holding preserved', Number(p2.quantityScaled)/1e8, 75);
eq('flagged as opening balance', p2.fromOpeningBalance, true);
eq('cost from stored avg', Number(p2.costMinor)/1e4, 90000);

console.log('\n--- D. Orphan events reported not dropped ---');
const d3=derivePositions([ev('x1','BUY','2024-01-01',10,100,'GONE')],[hold('h1','EQUITY',0,0,100)]);
eq('orphan flagged', d3.errors.some(e=>e.includes('no longer listed')), true);

console.log('\n--- E. Debt MF taxed at slab, not 20% ---');
eq('debt fund NOT equity', isEquityOriented('MUTUAL_FUND','Gilt Fund - Long Duration'), false);
eq('liquid fund NOT equity', isEquityOriented('MUTUAL_FUND','Liquid Fund'), false);
eq('flexi cap IS equity', isEquityOriented('MUTUAL_FUND','Flexi Cap Fund'), true);
eq('debt classified DEBT_SLAB', classifyGain('MUTUAL_FUND','Corporate Bond Fund',false), 'DEBT_SLAB');
eq('equity short = STCG', classifyGain('MUTUAL_FUND','Flexi Cap Fund',false), 'STCG_EQUITY');
eq('SGB exempt', classifyGain('GOLD_SGB','',true), 'SGB_EXEMPT');

console.log('\n--- F. Versioned rules + grandfathering ---');
const r=ruleSetFor('AY26-27')!;
eq('rule set found', r.assessmentYear, 'AY26-27');
eq('unpublished year refused', ruleSetFor('AY40-41'), null);
eq('LTCG 12.5% of 100000', Number(applyRateMinor(BigInt(100000*1e4), r.ltcgEquityRateBasisPoints))/1e4, 12500);
// bought 2015 @100, FMV 2018 = 400, sold @900 -> cost becomes 400
eq('grandfathered cost uses FMV',
  Number(grandfatheredCostMinor(r,'2015-06-01',BigInt(100*1e4),BigInt(900*1e4),BigInt(400*1e4)))/1e4, 400);
// FMV above proceeds is capped at proceeds
eq('FMV capped at proceeds',
  Number(grandfatheredCostMinor(r,'2015-06-01',BigInt(100*1e4),BigInt(300*1e4),BigInt(900*1e4)))/1e4, 300);
// post-2018 purchase unaffected
eq('post-2018 unchanged',
  Number(grandfatheredCostMinor(r,'2020-06-01',BigInt(100*1e4),BigInt(900*1e4),BigInt(400*1e4)))/1e4, 100);

console.log('\n--- G. Regression: original audit scenarios still pass ---');
const rr=foldHolding(events,'EQUITY');
eq('gain 12000', Number(rr.disposals[0].gainMinor)/1e4, 12000);
eq('LTCG 391d', rr.disposals[0].isLongTerm, true);
const rl=foldHolding([ev('l1','BUY','2024-01-01',10,1000),ev('l2','SELL','2024-06-01',10,800)],'EQUITY');
eq('loss included', Number(rl.disposals[0].gainMinor)/1e4, -2000);
const ro=foldHolding([ev('o1','BUY','2024-01-01',10,100),ev('o2','SELL','2024-06-01',50,120)],'EQUITY');
eq('oversell flagged', ro.errors.length>0, true);
eq('xirr null on no outflow', xirr([{date:'2024-01-01',amount:100},{date:'2025-01-01',amount:100}]), null);


const H=(name:string,cat:any,sub:string,val=100000,extra:Partial<Holding>={}):Holding=>({
  id:'h'+name.slice(0,6),name,symbolOrFolio:'X',category:cat,subCategory:sub,ownerId:'f1',
  quantityScaled:'100000000',averageCostPerUnitMinor:String(val*10000),
  currentPriceMinor:String(val*10000),lastPriceUpdated:'2026-08-01T00:00:00Z',
  isPriceStale:false,priceSource:'t',...extra});

const P=(h:Holding)=>classifyHolding(h);
const place=(h:Holding)=>`${P(h).exposure}/${P(h).l2}/${P(h).wrapper}`;

console.log('--- A. Two axes are orthogonal (the whole point) ---');
eq('equity direct',    place(H('Reliance Industries','EQUITY','Large Cap Conglomerate')), 'EQUITY/LARGE_CAP/DIRECT');
eq('equity via MF',    place(H('HDFC Flexi Cap Fund','MUTUAL_FUND','Flexi Cap Fund')),    'EQUITY/MULTI_FLEXI/MF');
eq('equity via index', place(H('Nifty 50 Index Fund','MUTUAL_FUND','Index Fund')),        'EQUITY/LARGE_CAP/INDEX_ETF');
eq('equity via SIF',   place(H('Quant SIF Equity Long-Short','SPECIALISED','SIF Strategy')), 'EQUITY/LARGE_CAP/SIF');
eq('DEBT via MF',      place(H('SBI Gilt Fund','MUTUAL_FUND','Gilt Fund')),               'DEBT/GOVERNMENT/MF');
eq('DEBT via SIF',     place(H('Edelweiss SIF Debt Long-Short','SPECIALISED','SIF')),     'DEBT/DURATION/SIF');
eq('DEBT via deposit', place(H('HDFC Bank FD','FIXED_INCOME','Bank Term Deposit')),       'DEBT/DEPOSITS/DEPOSIT');
console.log('   ^ gilt fund and debt SIF now HAVE a home. This was impossible before.');

console.log('\n--- B. Annex C placement rules ---');
eq('SGB -> GOLD not DEBT', P(H('SGB 2019-20 Series V','GOLD_SGB','RBI Sovereign Gold Bond')).exposure, 'GOLD');
eq('SGB wrapper DIRECT',   P(H('SGB 2019-20 Series V','GOLD_SGB','RBI Sovereign Gold Bond')).wrapper, 'DIRECT');
eq('REIT -> PROPERTY not EQUITY', P(H('Embassy Office Parks REIT','EQUITY','REIT')).exposure, 'PROPERTY');
eq('InvIT -> PROPERTY',    P(H('IndiGrid InvIT','EQUITY','InvIT')).exposure, 'PROPERTY');
eq('bullion physical',     place(H('Physical Gold Bars','GOLD_SGB','Physical 24 Karat Bullion')), 'GOLD/GOLD/PHYSICAL');
eq('PPF -> DEBT/small savings', place(H('PPF Account','FIXED_INCOME','Govt Small Savings (EEE)')), 'DEBT/SMALL_SAVINGS/SMALL_SAVINGS');
eq('EPF wrapper RETIREMENT', P(H('EPF Balance','FIXED_INCOME','Retirement Provident Fund')).wrapper, 'RETIREMENT');
eq('liquid fund -> CASH',  P(H('Liquid Contingency','CASH_BANK','Liquid Contingency Fund')).exposure, 'CASH');
eq('home loan',            place(H('SBI Home Loan','LIABILITY','Home Loan Overdraft')), 'LIABILITY/HOME_LOAN/DIRECT');
eq('term plan -> PURE',    place(H('HDFC Click 2 Protect Term Plan','SPECIALISED','Term Insurance')), 'PROTECTION/PURE/INSURANCE');
eq('ESOP',                 place(H('Employer ESOP Grant','SPECIALISED','ESOP')), 'PRIVATE/ESOP/DIRECT');

console.log('\n--- C. Look-through: listed once, counted proportionally ---');
const bal = H('HDFC Balanced Advantage Fund','MUTUAL_FUND','Balanced Advantage',142000);
const s = resolveSplit(bal, P(bal));
eq('is hybrid', isHybrid(s), true);
eq('estimated flag', s.estimated, true);
const ap = apportion(1420000000n, s);   // Rs 1,42,000
eq('equity slice Rs', Number(ap.get('EQUITY'))/1e4, 92300);
eq('debt slice Rs',   Number(ap.get('DEBT'))/1e4, 49700);
eq('sums exactly',    Number((ap.get('EQUITY')!)+(ap.get('DEBT')!))/1e4, 142000);

console.log('\n--- D. No paisa lost on awkward splits ---');
const ma = H('Multi Asset Fund','MUTUAL_FUND','Multi Asset Allocation');
const ms = resolveSplit(ma, P(ma));
const odd = 1000000000000n + 7n;  // deliberately indivisible
const mp = apportion(odd, ms);
let tot=0n; mp.forEach(v=>tot+=v);
eq('3-way split exact', tot === odd, true);
eq('3 slices', mp.size, 3);

console.log('\n--- E. User split overrides the estimate ---');
const custom = H('My Hybrid','MUTUAL_FUND','Balanced Advantage',100000,
  {allocationSplit:[{exposure:'EQUITY',percent:70},{exposure:'DEBT',percent:30}]});
const cs = resolveSplit(custom, P(custom));
eq('not estimated', cs.estimated, false);
const cap = apportion(1000000000n, cs);
eq('user 70%', Number(cap.get('EQUITY'))/1e4, 70000);

console.log('\n--- F. Explicit placement beats inference ---');
const forced = H('Weird Name','EQUITY','',100000,{exposureNode:'GOLD',l2Node:'SILVER',wrapper:'PHYSICAL'});
eq('explicit wins', place(forced), 'GOLD/SILVER/PHYSICAL');
eq('flagged not inferred', P(forced).inferred, false);
eq('inference flagged', P(H('Reliance','EQUITY','Large Cap')).inferred, true);

console.log('\n--- G. Tree building ---');
const portfolio = [
  H('Reliance Industries','EQUITY','Large Cap Conglomerate',389000),
  H('HDFC Balanced Advantage Fund','MUTUAL_FUND','Balanced Advantage',142000),
  H('SGB 2019-20 Series V','GOLD_SGB','RBI Sovereign Gold Bond',412000),
  H('SBI Gilt Fund','MUTUAL_FUND','Gilt Fund',200000),
  H('Nifty 50 Index Fund','MUTUAL_FUND','Index Fund',112000),
];
const { tree, exposureTotals } = buildTree(portfolio, h => BigInt(h.currentPriceMinor));
const eqNode = tree.find(t=>t.id==='EQUITY')!;
eq('EQUITY total incl 65% of hybrid', Number(exposureTotals.get('EQUITY'))/1e4, 389000+112000+92300);
eq('DEBT total incl 35% of hybrid',   Number(exposureTotals.get('DEBT'))/1e4, 200000+49700);
eq('GOLD total', Number(exposureTotals.get('GOLD'))/1e4, 412000);
eq('hybrid listed ONCE under EQUITY',
   tree.flatMap(t=>t.l2).flatMap(l=>l.rows).filter(r=>r.holding.name.includes('Balanced')).length, 1);
eq('empty nodes flagged', tree.find(t=>t.id==='PROTECTION')!.hasHoldings, false);

console.log('\n--- H. Empty wrappers hidden ---');
const lc = eqNode.l2.find(n=>n.id==='LARGE_CAP')!;
const order = WRAPPERS.map(w=>w.id);
const active = activeWrappers(lc.rows, order);
eq('only wrappers in use', active.join(','), 'DIRECT,INDEX_ETF');
eq('not all 11 shown', active.length < 11, true);

console.log('\n--- I. Structure sanity ---');
eq('8 exposure nodes', EXPOSURES.length, 8);
eq('11 wrappers', WRAPPERS.length, 11);
eq('labels resolve', `${exposureLabel('DEBT')} / ${l2Label('DEBT','GOVERNMENT')}`, 'Debt & fixed income / Government');

console.log('\n--- J. Plain-language events and family buckets ---');
eq('snapshot label', eventLabel('SNAPSHOT_ADJUSTMENT'), 'Updated to match statement');
eq('opening label', eventLabel('OPENING_BALANCE'), 'Opening from statement');
eq('buy label', eventLabel('BUY'), 'Bought');
eq('equity bucket', bucketOf(H('Reliance','EQUITY','Large Cap')), 'SHARES');
eq('MF bucket', bucketOf(H('HDFC Flexi Cap','MUTUAL_FUND','Flexi Cap')), 'FUNDS');
eq('SGB bucket', bucketOf(H('SGB 2020','GOLD_SGB','SGB')), 'GOLD');
eq('PPF bucket', bucketOf(H('PPF','FIXED_INCOME','Govt Small Savings (EEE)')), 'GOVT');
eq('statement date IST-safe', formatStatementDate('2026-03-31'), '31 March 2026');
const up = upcomingFromHoldings(
  [H('SGB 2020','GOLD_SGB','SGB',1,{nextCouponDate:'2099-11-15',maturityDate:'2099-11-15'})],
  [{id:'g1',name:'College',targetDate:'2099-06-01'}],
  '2026-01-01'
);
eq('upcoming includes coupon', up.some(i=>i.kind==='SGB_COUPON'), true);
eq('upcoming includes goal', up.some(i=>i.kind==='GOAL'), true);

console.log('\n--- K. Same AMC folio must not merge different schemes ---');
const mfRec = (name: string, isin: string, folio: string): StatementImportRecord => ({
  id: isin, date: '2026-03-31', holdingName: name, category: 'MUTUAL_FUND',
  action: 'BUY', quantity: 1, pricePerUnit: 10, totalAmount: 10,
  status: 'NEW', statusMessage: '', selected: true, isin, folio, recordType: 'HOLDING',
});
const mfHold = (id: string, rec: StatementImportRecord): Holding => ({
  id, name: rec.holdingName, symbolOrFolio: rec.folio || '', isin: rec.isin,
  category: 'MUTUAL_FUND', subCategory: 'MF', ownerId: 'f1',
  quantityScaled: '0', averageCostPerUnitMinor: '0', currentPriceMinor: '0',
  lastPriceUpdated: '2026-03-31', isPriceStale: false, priceSource: 't',
});
const iciciFolio = '17001385/94';
const momentum = mfRec('3572 - ICICI Prudential Active Momentum Fund - Growth', 'INF109K1A377', iciciFolio);
const multiAsset = mfRec('15 - ICICI Prudential Multi-Asset Fund - Growth', 'INF109K01761', iciciFolio);
const avap1 = mfRec('AVAP - ICICI Prudential Aggressive Hybrid Active FOF - Growth', 'INF109K01852', iciciFolio);
const avap2 = mfRec('AVAP - ICICI Prudential Aggressive Hybrid Active FOF - Growth', 'INF109K01852', iciciFolio);
const edel1 = mfRec('CYRG - Edelweiss Business Cycle Fund', 'INF754K01SW8', '9109231221/0');
const edel2 = mfRec('MURG - Edelweiss Multi Cap Fund', 'INF754K01RW0', '9109231221/0');
const first = mfHold('h_mom', momentum);
eq('different ISIN same folio: no match', findMatchingHolding([first], multiAsset, 'f1') ? 'hit' : 'none', 'none');
eq('same ISIN second lot: match', findMatchingHolding([mfHold('h_av', avap1)], avap2, 'f1')?.id, 'h_av');
eq('edelweiss different schemes: no match', findMatchingHolding([mfHold('h_cyrg', edel1)], edel2, 'f1') ? 'hit' : 'none', 'none');

const seq = [momentum, multiAsset, avap1, avap2, edel1, edel2];
const working: Holding[] = [];
for (const r of seq) {
  const m = findMatchingHolding(working, r, 'f1');
  if (!m) working.push(mfHold('h' + working.length, r));
}
eq('unique holdings from 6 rows (1 duplicate ISIN)', working.length, 5);

const woFolio = '1000129793';
const woMid = mfRec('101G - WhiteOak Capital - Mid Cap Fund - Regular Plan Growth', 'INF03CN01103', woFolio);
const woLarge = mfRec('111G - WhiteOak Capital - Large Cap Fund - Regular Plan Growth', 'INF03CN01111', woFolio);
eq('whiteoak stripped names collide but ISINs differ', findMatchingHolding([mfHold('h_wo', woMid)], woLarge, 'f1') ? 'hit' : 'none', 'none');

const noIsinMid = { ...woMid, isin: undefined };
const noIsinHold = mfHold('h_noisin', { ...woMid, isin: undefined });
eq('no-ISIN backfill via folio+name', findMatchingHolding([noIsinHold], woMid, 'f1')?.id, 'h_noisin');

// Mar 2026 smoking gun: summing units then × last NAV inflated Home to ₹5.44 cr.
const iciciRows = [
  { name: '3572 - ICICI Prudential Active Momentum Fund - Growth', isin: 'INF109K1A377', qty: 29998.5, nav: 12.81 },
  { name: '15 - ICICI Prudential Multi-Asset Fund - Growth', isin: 'INF109K01761', qty: 8160.55, nav: 756.6303 },
  { name: 'AVAP - ICICI Prudential Aggressive Hybrid Active FOF - Growth', isin: 'INF109K01852', qty: 38.415, nav: 221.04 },
  { name: 'AVAP - ICICI Prudential Aggressive Hybrid Active FOF - Growth', isin: 'INF109K01852', qty: 11703.407, nav: 221.04 },
  { name: 'ICICI Prudential Value Fund', isin: 'INF109K01AF8', qty: 1012.787, nav: 180.0 },
];
const iciciHoldings: Holding[] = [];
let correctValue = 0;
let lumpedQty = 0;
for (const row of iciciRows) {
  const rec = mfRec(row.name, row.isin, iciciFolio);
  rec.quantity = row.qty;
  rec.pricePerUnit = row.nav;
  lumpedQty += row.qty;
  const m = findMatchingHolding(iciciHoldings, rec, 'f1');
  if (!m) {
    iciciHoldings.push(mfHold('h' + iciciHoldings.length, rec));
    correctValue += row.qty * row.nav;
  } else {
    correctValue += row.qty * row.nav;
  }
}
const lastNav = iciciRows[iciciRows.length - 1].nav;
const inflated = lumpedQty * lastNav;
eq('icici folio yields 4 schemes not 1', iciciHoldings.length, 4);
eq('correct value not last-NAV lump', Math.round(correctValue) !== Math.round(inflated), true);
eq('dedup key not folio-only',
  holdingIdentityKey(mfHold('a', momentum)) === holdingIdentityKey(mfHold('b', multiAsset)),
  false);

console.log('\n--- L. Display name strips CDSL scheme-code prefix ---');
eq('MURG prefix', displayHoldingName('MURG - Edelweiss Multi Cap Fund - Regular Plan Growth'), 'Edelweiss Multi Cap Fund - Regular Plan Growth');
eq('3572 prefix', displayHoldingName('3572 - ICICI Prudential Active Momentum Fund'), 'ICICI Prudential Active Momentum Fund');
eq('15 numeric prefix', displayHoldingName('15 - ICICI Prudential Multi-Asset Fund'), 'ICICI Prudential Multi-Asset Fund');
eq('001ZG prefix', displayHoldingName('001ZG - Parag Parikh Flexi Cap Fund'), 'Parag Parikh Flexi Cap Fund');
eq('no prefix share', displayHoldingName('HDFC Bank Ltd'), 'HDFC Bank Ltd');
eq('SGB year-dash kept', displayHoldingName('Sovereign Gold Bond 2018-19 Series VI'), 'Sovereign Gold Bond 2018-19 Series VI');
eq('code only unchanged', displayHoldingName('AVAP'), 'AVAP');
eq('empty', displayHoldingName(''), '');

console.log('\n--- M. Account card title from AMC / broker, not CDSL bucket ---');
eq('infer Edelweiss', inferAmcFromSchemeName('MURG - Edelweiss Multi Cap Fund - Regular Plan Growth'), 'Edelweiss');
eq('infer ICICI', inferAmcFromSchemeName('3572 - ICICI Prudential Active Momentum Fund'), 'ICICI Prudential');
eq('infer Nippon', inferAmcFromSchemeName('MCAG - NIPPON INDIA MNC FUND'), 'Nippon India');
eq('infer WhiteOak Capital', inferAmcFromSchemeName('101G - WhiteOak Capital Fund'), 'WhiteOak Capital');
eq('HDFC fund prefix', inferAmcFromSchemeName('HDFC Flexi Cap Fund'), 'HDFC');
eq('generic custodian uses AMC field', accountTitle('Mutual Fund Folios', [{amc:'ICICI Prudential', name:'X'}], 'MF_FOLIO'), 'ICICI Prudential');
eq('generic custodian infers from scheme', accountTitle('Mutual Fund Folios', [{name:'MURG - Edelweiss Multi Cap Fund'}], 'MF_FOLIO'), 'Edelweiss');
eq('demat keeps broker name', accountTitle('RAISE SECURITIES PRIVATE LIMITED', [{name:'HDFC Bank Ltd'}], 'DEMAT'), 'RAISE SECURITIES PRIVATE LIMITED');
eq('empty mf fallback', accountTitle('Mutual Fund Folios', [], 'MF_FOLIO'), 'Mutual fund folio');

console.log('\n--- N. Statement as-of is per family member ---');
const casState = parseCasLatest(JSON.stringify({
  asOf: '2026-02-28',
  byOwner: {
    fam_1: { CDSL: { fileType: 'CDSL', filename: 'a.pdf', pdfGrandTotal: 1, parsedGrandTotal: 1, complete: true, periodTo: '2026-01-31' } },
    fam_2: { CDSL: { fileType: 'CDSL', filename: 'b.pdf', pdfGrandTotal: 1, parsedGrandTotal: 1, complete: true, periodTo: '2026-02-28' } },
    fam_3: { CAMS: { fileType: 'CAMS', filename: 'c.pdf', pdfGrandTotal: 1, parsedGrandTotal: 1, complete: true, periodTo: '2026-01-31' } },
  },
}));
eq('global max still Feb', casState.asOf, '2026-02-28');
eq('self is Jan', latestAsOfForOwner(casState, 'fam_1'), '2026-01-31');
eq('spouse is Feb', latestAsOfForOwner(casState, 'fam_2'), '2026-02-28');
eq('unknown owner null', latestAsOfForOwner(casState, 'fam_x') == null, true);
const listed = statementAsOfByMember(casState, [
  { id: 'fam_1', name: 'Vikas' },
  { id: 'fam_2', name: 'Nancy' },
  { id: 'fam_3', name: 'HUF' },
]);
eq('three profiles listed', listed.map(x => x.name + ':' + x.asOf).join('|'), 'Vikas:2026-01-31|Nancy:2026-02-28|HUF:2026-01-31');

console.log('\n--- O. Loan EMI catch-up ---');
eq('Jan 31 plus 1 month', addCalendarMonths('2026-01-31', 1), '2026-02-28');
eq('due dates three months', dueEmiDates('2026-03-05', 3, '2026-05-10').join(','), '2026-03-05,2026-04-05,2026-05-05');
eq('future EMI not due', dueEmiDates('2026-10-05', 12, '2026-09-09').join(','), '');
const loan: Holding = {
  ...hold('loan1', 'LIABILITY', 1, 850000, 612000, 'Car loan'),
  name: 'HDFC Car Loan',
  monthlyEmiMinor: String(18500 * 10000),
  remainingEmiCount: 6,
  nextCouponDate: '2026-07-05',
  emiStatus: 'PAYING',
};
const julyPlan = planEmiCatchUp(loan, '2026-09-09');
eq('posts Jul Aug Sep', julyPlan && julyPlan.events.map(e => e.eventDate).join(','), '2026-07-05,2026-08-05,2026-09-05');
eq('remaining after 3', julyPlan && julyPlan.remainingEmiCount, 3);
eq('outstanding minus 3 EMIs', julyPlan && Number(julyPlan.outstandingMinor) / 10000, 612000 - 18500 * 3);
eq('next EMI Oct', julyPlan && julyPlan.nextCouponDate, '2026-10-05');
const paused = planEmiCatchUp({ ...loan, emiStatus: 'PAUSED' }, '2026-09-09');
eq('paused posts nothing', paused, null);
const skipped = planEmiCatchUp(loan, '2026-09-09', ['2026-07-05']);
eq('skips already posted Jul', skipped && skipped.events.map(e => e.eventDate).join(','), '2026-08-05,2026-09-05');
eq('resume jumps past gap', resumeEmiDate('2026-07-05', '2026-09-09'), '2026-10-05');
const emiUp = upcomingFromHoldings([{ ...loan, nextCouponDate: '2026-10-05' }], []);
eq('upcoming EMI not gold-bond', emiUp.some(i => i.kind === 'EMI' && i.title.includes('HDFC Car Loan')), true);

console.log('\n--- P. Import PAN must match the selected member ---');
eq('full vs masked', pansMatch('ABCDE1234F', 'ABCDE****F'), true);
eq('different PAN', pansMatch('ABCDE1234F', 'ABTPS9999M'), false);
eq('fingerprint', panFingerprint('abcde1234f'), 'ABCDE****F');
const selfM = { id: 'fam_1', name: 'Vikas', relation: 'Self' as const, color: '#000', panMasked: 'ABCPS****K', panForStatements: 'ABCPS1234K', isPrimary: true };
const spouseM = { id: 'fam_2', name: 'Nancy', relation: 'Spouse' as const, color: '#111', panMasked: 'ABTPS****M' };
const hufM = { id: 'fam_3', name: 'Vikas HUF', relation: 'HUF' as const, color: '#222', panMasked: 'AAAPS****R' };
const fam = [selfM, spouseM, hufM];
eq('match selected', checkImportPan(selfM, fam, 'ABCPS1234K').kind, 'MATCH');
eq('wrong member suggests spouse', checkImportPan(selfM, fam, 'ABTPS8888M').kind, 'SWITCH');
const sw = checkImportPan(selfM, fam, 'ABTPS8888M');
eq('switch target Nancy', sw.kind === 'SWITCH' ? sw.matched.name : '', 'Nancy');
eq('unknown PAN', checkImportPan(selfM, fam, 'ZZZZZ9999Z').kind, 'UNKNOWN_PAN');
eq('no pan on member', checkImportPan({ ...hufM, panMasked: undefined }, fam, 'ZZZZZ9999Z').kind, 'MEMBER_HAS_NO_PAN');
eq('pdf missing PAN allowed', checkImportPan(selfM, fam, undefined).kind, 'NO_PDF_PAN');
eq('block switch', panBlocksCommit({ kind: 'SWITCH', matched: spouseM }), true);
eq('allow match', panBlocksCommit({ kind: 'MATCH' }), false);
eq('allow missing pdf pan', panBlocksCommit({ kind: 'NO_PDF_PAN' }), false);
eq('dup PAN finds Nancy', findDuplicatePanMember(fam, 'ABTPS0000M')?.name, 'Nancy');
eq('own PAN not a dup', findDuplicatePanMember(fam, 'ABCPS1234K', 'fam_1') == null, true);

console.log('\n--- Q. Close-only CMP from catalog ---');
eq('eod source NSE', isEodPriceSource('NSE close'), true);
eq('cas source not eod', isEodPriceSource('CAS Statement Snapshot'), false);
const tcs = catalogToListed({ isin: 'INE467B01029', name: 'TCS', symbol: 'TCS', nse: 'TCS', bse: 'TCS', amfi: '', cat: 'EQUITY', px: 2208, dt: '2026-09-09', src: 'NSE close' });
eq('TCS close minor', tcs && tcs.closeMinor, String(2208 * 10000));
const before = hold('h_tcs', 'EQUITY', 1000, 2000, 2000);
before.isin = 'INE467B01029';
const priced = applyCatalogPrices([before], tcs ? [tcs] : []);
eq('updated count', priced.updated, 1);
eq('CMP 2208', Number(priced.holdings[0].currentPriceMinor) / 10000, 2208);
eq('qty unchanged', priced.holdings[0].quantityScaled, before.quantityScaled);
const catalogRows = [tcs!];
eq('search TCS', searchListed(catalogRows, 'tcs', 'EQUITY')[0]?.isin, 'INE467B01029');
eq('search misses funds', searchListed(catalogRows, 'tcs', 'MUTUAL_FUND').length, 0);
const mfCat = [
  { isin: 'INF879O01118', name: 'Parag Parikh Liquid Fund — Direct Plan · Daily Reinvestment of IDCW', category: 'MUTUAL_FUND' as const, closeMinor: '10007489', priceDate: '2026-09-10', source: 'AMFI NAV' },
  { isin: 'INF879O01027', name: 'Parag Parikh Flexi Cap Fund — Direct Plan · Growth', category: 'MUTUAL_FUND' as const, closeMinor: '893946', priceDate: '2026-09-09', source: 'AMFI NAV' },
  { isin: 'INF879O01068', name: 'Parag Parikh Liquid Fund — Direct Plan · Growth', category: 'MUTUAL_FUND' as const, closeMinor: '15705718', priceDate: '2026-09-10', source: 'AMFI NAV' },
];
eq('flexi growth ranks first for parag', searchListed(mfCat, 'parag pari', 'MUTUAL_FUND')[0]?.isin, 'INF879O01027');

console.log('\n--- R. Loan outstanding, SGB gold tab, loan labels ---');
eq('SGB name detected', looksLikeSgb('2.5% SGB 2021-22 SERIES II 2029-LOAN CODE 30433'), true);
eq('SGB bucket even if tagged equity', bucketOf({ ...hold('h_sgb','EQUITY',10,5000,5000), name: '2.5% SGB 2021-22 SERIES II' }), 'GOLD');
const loanH: Holding = {
  ...hold('h_loan','LIABILITY',1,850000,612000),
  name: 'HDFC Car Loan',
  monthlyEmiMinor: String(18500*10000),
  remainingEmiCount: 36,
};
const emptyDerived = derivePositions([], [loanH]);
const lm = metricsOf(loanH, emptyDerived, 'MARKET_TRADED');
eq('loan list is outstanding not zero', Number(lm.value) / 10000, 612000);
eq('loan original amount', Number(lm.cost) / 10000, 850000);
eq('loan has no share-style gain', lm.gain, 0n);
eq('loan recorded label', eventLabel('VALUATION_UPDATE', 'Loan recorded — outstanding at start'), 'Loan recorded');
eq('repayment label', eventLabel('REPAYMENT'), 'Loan repayment');

console.log('\n--- S. Growth engine ---');
eq('absolute 50%', Math.round(absoluteReturnPct(1_000_000_000n, 1_500_000_000n)!), 50);
eq('cagr ~10% over 2y', Math.round(cagrPct(1_000_000_000n, 1_210_000_000n, '2024-09-10', '2026-09-10')!), 10);
eq('cagr refuses short window', cagrPct(1_000_000_000n, 1_100_000_000n, '2026-09-01', '2026-09-10'), null);
eq('format pct', formatPct(12.54), '+12.5%');
eq('FY of May is next label', financialYearOf('2026-05-01'), '2026-27');
const divEv = ev('dv1','DIVIDEND','2026-05-10',1,5000,'h_g');
const inc = incomeByFinancialYear([divEv], new Map());
eq('dividend in FY 2026-27', inc[0]?.fy, '2026-27');
eq('dividend amount', Number(inc[0]?.dividendMinor)/10000, 5000);
eq('first date', firstActivityDate([ev('a','BUY','2025-01-02',1,1), ev('b','BUY','2024-06-01',1,1)]), '2024-06-01');
const gh = hold('h_g','EQUITY',10,100,150);
const openEv: LedgerEvent = {
  ...ev('op1','OPENING_BALANCE','2024-09-10',10,100,'h_g'),
};
const gDer = derivePositions([openEv], [gh]);
const gModel = buildGrowthModel(
  [gh],
  [openEv],
  gDer,
  [{ id:'f1', name:'Vikas', relation:'Self', color:'#f59e0b', isPrimary:true }],
  'MARKET_TRADED',
  '2026-09-10',
);
eq('growth assets', Number(gModel.kpis.assetsMinor)/10000, 1500);
eq('growth invested', Number(gModel.kpis.investedMinor)/10000, 1000);
eq('growth abs +50%', Math.round(gModel.kpis.absoluteReturnPct || 0), 50);
eq('shares slice present', gModel.buckets.some(s => s.id === 'SHARES'), true);
eq('xirr not null with opening', gModel.kpis.xirrPct == null, false);

console.log('\n--- T. Instrument return metrics ---');
const mfSip = hold('mf1','MUTUAL_FUND',100,10,12);
eq('MF 3 buys is XIRR', profileOf(mfSip, [ev('s1','BUY','2025-01-01',10,10,'mf1'), ev('s2','BUY','2025-02-01',10,10,'mf1'), ev('s3','BUY','2025-03-01',10,10,'mf1')], '2026-09-10').primaryLabel, 'XIRR');
const mfLump = hold('mf2','MUTUAL_FUND',100,10,15);
eq('MF lump >1y is CAGR', profileOf(mfLump, [ev('l1','OPENING_BALANCE','2024-01-01',100,10,'mf2')], '2026-09-10').primaryLabel, 'CAGR');
eq('equity is XIRR', profileOf(hold('eq1','EQUITY',10,100,120), [ev('e','BUY','2024-01-01',10,100,'eq1')], '2026-09-10').key, 'EQUITY');
eq('SGB key', profileOf({...hold('sg1','GOLD_SGB',10,5000,5500), name:'SGB 2020'}, [], '2026-09-10').key, 'SGB');
const ppfH = {...hold('pp1','FIXED_INCOME',1,100000,110000), name:'PPF', couponRatePercent: 7.1};
eq('PPF uses stated yield', profileOf(ppfH, [], '2026-09-10').key, 'PPF');
const ppfRet = holdingReturn(ppfH, [], derivePositions([], [ppfH]), 'MARKET_TRADED', '2026-09-10');
eq('PPF primary 7.1', ppfRet.primaryText, '+7.1%');
eq('AIF is TVPI', profileOf({...hold('a1','SPECIALISED',1,100,150), name:'Cat II AIF'}, [], '2026-09-10').primaryLabel, 'TVPI');

console.log('\n--- U. Local vault login ---');
eq('title Vikas', titleCaseName('vIKAS'), 'Vikas');
eq('title last Shah', titleCaseName('SHAH'), 'Shah');
eq('pw short', passwordIssues('Ab1!').length>0, true);
eq('pw ok', passwordIssues('Abcd1234!').length, 0);
eq('pan ok', isValidPan('byaps1234q'), true);
eq('pan bad', isValidPan('123'), false);
eq('email ok', isValidEmail('a@b.com'), true);
eq('answer case', normaliseAnswer('  Blue  Dog '), 'blue dog');
eq('rotate 181d', needsPasswordRotation(new Date(Date.now()-181*86400000).toISOString()), true);
eq('rotate fresh', needsPasswordRotation(new Date().toISOString()), false);
eq('unpack hash', unpackHash(packHash({salt:'s',hash:'h',iterations:3}))?.hash, 'h');

console.log('\n--- H. Encryption ---');
(async()=>{
  const secret={holdings:[{name:'Reliance',value:389000}],pan:'ABCDE1234F'};
  const blob=await encryptJson(secret,'correct horse battery');
  eq('is encrypted blob', isEncryptedBlob(blob), true);
  eq('ciphertext not readable', blob.cipherB64.includes('Reliance'), false);
  const back=await decryptJson<typeof secret>(blob,'correct horse battery');
  eq('round trip', back.holdings[0].value, 389000);
  let threw=false;
  try{ await decryptJson(blob,'wrong password'); }catch(e){ threw = e instanceof WrongPassphraseError; }
  eq('wrong password rejected', threw, true);
  // tamper detection
  const t={...blob, cipherB64: blob.cipherB64.slice(0,-4)+'AAAA'};
  let tampered=false;
  try{ await decryptJson(t,'correct horse battery'); }catch{ tampered=true; }
  eq('tampering detected', tampered, true);
  eq('recovery key format', /^[0-9A-F]{5}(-[0-9A-F]{1,5})+$/.test(generateRecoveryKey()), true);
  eq('PAN masked', maskPan('ABCDE1234F'), 'ABCDE****F');
  const hashed = await hashSecret('Abcd1234!');
  eq('password verifies', await verifySecret('Abcd1234!', hashed.salt, hashed.hash, hashed.iterations), true);
  eq('wrong password fails', await verifySecret('nope', hashed.salt, hashed.hash, hashed.iterations), false);

  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail?1:0);
})();
