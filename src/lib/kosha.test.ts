import { foldHolding, financialYearOf, assessmentYearOf, calendarDays } from './lots';
import { derivePositions, openingBalanceFor, marketValueMinor } from './positions';
import { ruleSetFor, classifyGain, isEquityOriented, grandfatheredCostMinor, applyRateMinor } from './taxRules';
import { encryptJson, decryptJson, isEncryptedBlob, generateRecoveryKey, maskPan, WrongPassphraseError } from './vault';
import { packBackup, unpackBackup, verifyRestoreIdentity, isBinaryBackup, looksLikePlainJsonBackup, BACKUP_KIND, VaultBackup, suggestedScheduledBackupName, suggestedBackupName } from './backup';
import { derivePlan, daysRemaining, TRIAL_DAYS, PRICE_INR, appUnlocked, enhancementsEnabled, canPurchaseSupport } from './license';
import {
  FEEDBACK_FIRST_DELAY_DAYS,
  FEEDBACK_SNOOZE_DAYS,
  FEEDBACK_MAX_PROMPTS,
  PLAY_PACKAGE_ID,
  EMPTY_FEEDBACK,
  seedFirstSeen,
  shouldShowFeedbackPrompt,
  snoozeFeedback,
  completeFeedback,
  claimFeedbackPrompt,
  resetFeedbackSession,
} from './feedback';
import { __test as encTest } from '../db/encryptedStore';
import { ONBOARDING_SLIDES } from './onboarding';
import { TERMS_SECTIONS, TERMS_VERSION, TERMS_TEXT_ID, termsPlainText, hasAcceptedCurrentTerms, acceptCurrentTerms } from './terms';
import { pruneToLastN, BACKUP_KEEP_LAST, DEFAULT_BACKUP_SCHEDULE } from './backupSchedule';
import { xirr } from './returns';
import { classifyHolding, EXPOSURES, WRAPPERS, exposureLabel, l2Label } from './taxonomy';
import { resolveSplit, apportion, buildTree, isHybrid, activeWrappers } from './lookthrough';
import { LedgerEvent, Holding } from '../types';
import { eventLabel, bucketOf, upcomingFromHoldings, displayHoldingName, accountTitle, inferAmcFromSchemeName, looksLikeSgb, metricsOf, isOpenPosition, orderBucketsByPresence } from './holdingView';
import { formatStatementDate, latestAsOfForOwner, statementAsOfByMember, parseCasLatest, npsStatementPassword, statementPasswordsToTry } from './casPython';
import { addCalendarMonths, dueEmiDates, planEmiCatchUp, resumeEmiDate } from './emi';
import { planInterestCatchUp, fyEndDatesAfter, monthsHeldInFy, interestMinor, interestKindOf, interestSpecOf, planMonthlyIncome, nextMonthlyIncomeDate } from './interest';
import { planInvestedCorrection, earliestActiveParcel, isUnknownInvested, UNKNOWN_INVESTED_LABEL } from './costBasis';
import { absoluteReturnPct, annualReturnPct, incomeByFinancialYear, buildGrowthModel, formatPct } from './growth';
import { profileOf, holdingReturn, isStatementOnlyHistory, investedHasChanged } from './returnMetrics';
import { titleCaseName, passwordIssues, isValidPan, isValidEmail, normaliseAnswer, needsPasswordRotation, packHash, unpackHash, hashSecret, verifySecret } from './auth';
import { checkImportPan, pansMatch, panBlocksCommit, findDuplicatePanMember, panFingerprint } from './pan';
import { applyCatalogPrices, catalogToListed, searchListed, isEodPriceSource } from './eodPrices';
import { findMatchingHolding, holdingIdentityKey, isPlaceholderName, preferSchemeName } from './matchHolding';
import { StatementImportRecord } from '../types';
import { buildBooksHealth, buildStatementSteps, buildSinceLastStatement, concentrationOf, daysBetweenIso, freshnessTone, previousFinancialYear, fyEndIso, unknownCostRows, firstToLastPct, investedSeriesFrom } from './homeDashboard';
import { classifyStatementFilename, decideStatementMail, issuerFromSender, newestNamedStatements, filesForMember } from './statementMail';
import {
  usesUnits,
  identifierLabel,
  looksLikeUan,
  looksLikePran,
  defaultSgbCoupon,
  suggestLockInDates,
  looksLikeNsdlDpId,
  looksLikeNsdlClientId,
  looksLikeBoid,
  inferMfPlan,
  inferMfOption,
  nameLooksLikeEtf,
  dematAccountMask,
  detailsLines,
  nameLooksLikeReit,
  nameLooksLikeAif,
  recategorizeCasAsset,
  casHoldingSubCategory,
  casImportDetails,
} from './holdingForm';

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
eq('SGB certificate redemption is exempt', classifyGain('GOLD_SGB', 'Sovereign Gold Bond', true, 'CERTIFICATE'), 'SGB_EXEMPT');
eq('SGB demat sale is not exempt', classifyGain('GOLD_SGB', 'Sovereign Gold Bond', true, 'DEMAT'), 'DEBT_SLAB');
eq('physical gold is not SGB exempt', classifyGain('GOLD_SGB', 'Physical gold (24K)', true), 'DEBT_SLAB');

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

const adjEv: LedgerEvent = {
  ...ev('adj1','SNAPSHOT_ADJUSTMENT','2026-02-28',-10,0,'h1'),
};
// quantityScaled for negative: ev() uses Math.round(q*1e8) so -10 works
const adjFold = foldHolding([ev('b1','BUY','2026-01-31',10,100,'h1'), adjEv], 'MUTUAL_FUND');
eq('snapshot close leaves zero qty', Number(adjFold.totalQtyScaled), 0);
eq('snapshot close is silent (no units-removed wall)', adjFold.errors.some(e => /Units were removed|capital gains/i.test(e)), false);
const adjOver = foldHolding([ev('b2','BUY','2026-01-31',5,100,'h1'), {
  ...ev('adj2','SNAPSHOT_ADJUSTMENT','2026-02-28',-10,0,'h1'),
}], 'MUTUAL_FUND');
eq('snapshot oversell still flagged', adjOver.errors.some(e => /exceeds units held/.test(e)), true);

const corOb = ev('obc','OPENING_BALANCE','2026-05-31',6418.7052,75.0840,'hnps');
const corQty = BigInt(Math.round(6418.7052 * 1e8));
const corWant = BigInt(Math.round(317909.45 * 1e4));
const corUnit = (corWant * 100_000_000n) / corQty;
const corFix: LedgerEvent = {
  ...ev('cor1','CORRECTION','2026-05-31',6418.7052,49.5288,'hnps'),
  eventType: 'CORRECTION',
  supersedesEventId: 'obc',
  quantityScaled: corQty.toString(),
  unitPriceMinor: corUnit.toString(),
  totalAmountMinor: corWant.toString(),
  notes: 'Invested updated from the printed statement contribution',
};
const corFold = foldHolding([corOb, corFix], 'FIXED_INCOME');
eq('correction replaces opening cost', Math.abs(Number(corFold.totalCostMinor)/1e4 - 317909.45) < 1, true);
eq('correction keeps units', Number(corFold.totalQtyScaled)/1e8, 6418.7052);
eq('superseded opening is not double-counted', corFold.openParcels.length, 1);


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
const chipOrder = orderBucketsByPresence(['SHARES', 'FUNDS', 'SPECIAL']);
eq('used classes sit together first', chipOrder.filter(b => !b.empty).map(b => b.id).join(','), 'SHARES,FUNDS,SPECIAL');
eq('empty classes trail', chipOrder.filter(b => b.empty).map(b => b.id).join(','), 'GOLD,GOVT,PROPERTY,BANK,LOAN');
eq('loans gray when unused', chipOrder.find(b => b.id === 'LOAN')?.empty, true);
eq('shares not gray when used', chipOrder.find(b => b.id === 'SHARES')?.empty, false);
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

eq('placeholder mutual fund units', isPlaceholderName('MUTUAL FUND UNITS'), true);
eq('placeholder scheme code', isPlaceholderName('MURG'), true);
eq('placeholder folio', isPlaceholderName('17001385/94'), true);
eq('real scheme not placeholder', isPlaceholderName('MURG - Edelweiss Multi Cap Fund - Regular Plan Growth'), false);
eq('prefer real over placeholder', preferSchemeName('MUTUAL FUND UNITS', 'MURG - Edelweiss Multi Cap Fund'), 'MURG - Edelweiss Multi Cap Fund');
eq('keep real over later placeholder', preferSchemeName('Edelweiss Multi Cap Fund', 'MUTUAL FUND UNITS'), 'Edelweiss Multi Cap Fund');

const stubHold = mfHold('h_stub', { ...edel1, isin: undefined, holdingName: 'MUTUAL FUND UNITS' });
eq('placeholder folio stub matches the only unnamed row',
  findMatchingHolding([stubHold], { ...edel1, isin: undefined, holdingName: 'MUTUAL FUND UNITS' }, 'f1')?.id,
  'h_stub');
eq('placeholder does not merge two named schemes on same folio',
  findMatchingHolding([mfHold('h_cyrg', edel1), mfHold('h_murg', edel2)], { ...edel1, isin: undefined, holdingName: 'MUTUAL FUND UNITS' }, 'f1') ? 'hit' : 'none',
  'none');

console.log('\n--- L. Display name strips CDSL scheme-code prefix ---');
eq('MURG prefix', displayHoldingName('MURG - Edelweiss Multi Cap Fund - Regular Plan Growth'), 'Edelweiss Multi Cap Fund - Regular Plan Growth');
eq('3572 prefix', displayHoldingName('3572 - ICICI Prudential Active Momentum Fund'), 'ICICI Prudential Active Momentum Fund');
eq('15 numeric prefix', displayHoldingName('15 - ICICI Prudential Multi-Asset Fund'), 'ICICI Prudential Multi-Asset Fund');
eq('001ZG prefix', displayHoldingName('001ZG - Parag Parikh Flexi Cap Fund'), 'Parag Parikh Flexi Cap Fund');
eq('no prefix share', displayHoldingName('HDFC Bank Ltd'), 'HDFC Bank Ltd');
eq('SGB year-dash kept', displayHoldingName('Sovereign Gold Bond 2018-19 Series VI'), 'Sovereign Gold Bond 2018-19 Series VI');
eq('code only unchanged', displayHoldingName('AVAP'), 'AVAP');
eq('empty', displayHoldingName(''), '');
eq('AMC#scheme uses scheme', displayHoldingName('NIPPON LIFE INDIA AM LTD#NIPPON INDIA MF-NIPPON INDIA ETF NIFTY 1D RATE'), 'NIPPON INDIA MF-NIPPON INDIA ETF NIFTY 1D RATE');
eq('hash then code prefix', displayHoldingName('AMC#MURG - Edelweiss Multi Cap Fund'), 'Edelweiss Multi Cap Fund');

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

console.log('\n--- N2. NPS CRA PDF password convention ---');
eq('vikas 22 Mar', npsStatementPassword('Vikas Kunjbihari Shah', '1986-03-22'), 'vika2203');
eq('kiah 4-letter first name', npsStatementPassword('Kiah Vikas Shah', '2018-02-07'), 'kiah0702');
eq('short first name', npsStatementPassword('Om Shah', '2020-01-05'), 'om0501');
eq('dd/mm/yyyy', npsStatementPassword('Amit', '15/08/1990'), 'amit1508');
eq('missing dob', npsStatementPassword('Vikas', undefined), null);
const tried = statementPasswordsToTry({
  typed: 'ABCPS1234K',
  pan: 'ABCPS1234K',
  name: 'Vikas Shah',
  dateOfBirth: '1986-03-22',
});
eq('tries pan then nps', tried.join(','), 'ABCPS1234K,vika2203');
eq('nps first when no pan', statementPasswordsToTry({
  name: 'Kiah Vikas Shah',
  dateOfBirth: '2018-02-07',
}).join(','), 'kiah0702');


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
eq('CAGR 21% over 365 days', Math.round(annualReturnPct(100n * 10000n, 121n * 10000n, 365)!), 21);
eq('CAGR refuses under a year', annualReturnPct(100n * 10000n, 150n * 10000n, 200), null);
eq('format pct', formatPct(12.54), '+12.5%');
eq('FY of May is next label', financialYearOf('2026-05-01'), '2026-27');
const divEv = ev('dv1','DIVIDEND','2026-05-10',1,5000,'h_g');
const inc = incomeByFinancialYear([divEv], new Map());
eq('dividend in FY 2026-27', inc[0]?.fy, '2026-27');
eq('dividend amount', Number(inc[0]?.dividendMinor)/10000, 5000);
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
eq('growth xirr removed from kpis', 'xirrPct' in gModel.kpis, false);

console.log('\n--- T. Instrument return metrics ---');
const mfSip = hold('mf1','MUTUAL_FUND',100,10,12);
eq('MF shows absolute return', profileOf(mfSip, [ev('s1','BUY','2025-01-01',10,10,'mf1'), ev('s2','BUY','2025-02-01',10,10,'mf1'), ev('s3','BUY','2025-03-01',10,10,'mf1')], '2026-09-10').primaryLabel, 'Absolute return');
const mfLump = hold('mf2','MUTUAL_FUND',100,10,15);
eq('MF lump is also absolute', profileOf(mfLump, [ev('l1','OPENING_BALANCE','2024-01-01',100,10,'mf2')], '2026-09-10').primaryLabel, 'Absolute return');
eq('equity key', profileOf(hold('eq1','EQUITY',10,100,120), [ev('e','BUY','2024-01-01',10,100,'eq1')], '2026-09-10').key, 'EQUITY');
eq('SGB key', profileOf({...hold('sg1','GOLD_SGB',10,5000,5500), name:'SGB 2020'}, [], '2026-09-10').key, 'SGB');
const ppfH = {...hold('pp1','FIXED_INCOME',1,100000,110000), name:'PPF', couponRatePercent: 7.1};
eq('PPF key', profileOf(ppfH, [], '2026-09-10').key, 'PPF');
const ppfRet = holdingReturn(ppfH, [], derivePositions([], [ppfH]), 'MARKET_TRADED', '2026-09-10');
eq('PPF primary is absolute not coupon', ppfRet.primaryText, '+10.0%');
const sipEv = [ev('s1','BUY','2025-01-01',10,10,'mf1'), ev('s2','BUY','2025-02-01',10,10,'mf1'), ev('s3','BUY','2025-03-01',10,10,'mf1')];
eq('SIP invested changed', investedHasChanged(sipEv, 'mf1', 'MUTUAL_FUND'), true);
const sipRet = holdingReturn(mfSip, sipEv, derivePositions(sipEv, [mfSip]), 'MARKET_TRADED', '2026-09-10');
eq('SIP annual is N/A', sipRet.secondaryText, 'N/A');
eq('SIP annual reason', sipRet.annualReason, 'changed');
const lumpEv = [ev('l1','OPENING_BALANCE','2024-01-01',100,10,'mf2')];
const lumpRet = holdingReturn(mfLump, lumpEv, derivePositions(lumpEv, [mfLump]), 'MARKET_TRADED', '2026-09-10');
eq('lump annual is computed', lumpRet.annualReason, 'ok');
eq('lump annual text is a pct', lumpRet.secondaryText.includes('%'), true);
eq('fresh lump under a year is N/A', holdingReturn(
  mfLump, [ev('l2','OPENING_BALANCE','2026-06-01',100,10,'mf2')],
  derivePositions([ev('l2','OPENING_BALANCE','2026-06-01',100,10,'mf2')], [mfLump]),
  'MARKET_TRADED', '2026-09-10',
).annualReason, 'under_year');
eq('AIF is absolute not TVPI', profileOf({...hold('a1','SPECIALISED',1,100,150), name:'Cat II AIF'}, [], '2026-09-10').primaryLabel, 'Absolute return');

const closedH = hold('cl1','MUTUAL_FUND',0,10,12);
const closedEv = ev('cl_adj','SNAPSHOT_ADJUSTMENT','2026-02-28',-10,10,'cl1');
const closedOpen = ev('cl_ob','OPENING_BALANCE','2026-01-31',10,10,'cl1');
const closedDer = derivePositions([closedOpen, closedEv], [closedH]);
eq('closed qty is zero', Number(closedDer.positions.get('cl1')!.quantityScaled), 0);
eq('isOpenPosition false for closed', isOpenPosition(closedH, closedDer), false);
const closedRet = holdingReturn(closedH, [closedOpen, closedEv], closedDer, 'MARKET_TRADED', '2026-09-10');
eq('closed return label', closedRet.primaryText, 'Closed');
eq('closed return primary', closedRet.profile.primaryLabel, 'Closed');

eq('opening only is statement history', isStatementOnlyHistory([ev('ob','OPENING_BALANCE','2026-01-31',100,10,'mf2')]), true);
eq('real buy is not statement-only', isStatementOnlyHistory([ev('b','BUY','2025-01-01',10,10,'mf1')]), false);
const stmtH = hold('st1','MUTUAL_FUND',100,10,12);
const stmtEv = ev('st_ob','OPENING_BALANCE','2026-01-31',100,10,'st1');
const stmtDer = derivePositions([stmtEv], [stmtH]);
const stmtRet = holdingReturn(stmtH, [stmtEv], stmtDer, 'MARKET_TRADED', '2026-09-10');
eq('statement-only still labelled absolute', stmtRet.profile.primaryLabel, 'Absolute return');
eq('statement-only why is honest', stmtRet.profile.why.includes('recorded in Kosha'), true);
eq('open position with qty', isOpenPosition(stmtH, stmtDer), true);

const ghostH = hold('gh0','MUTUAL_FUND',0,10,12);
const ghostEv = ev('gh_adj','SNAPSHOT_ADJUSTMENT','2026-02-28',-5,10,'gh0');
const ghostOpen = ev('gh_ob','OPENING_BALANCE','2026-01-31',5,10,'gh0');
const ghostDer = derivePositions([ghostOpen, ghostEv], [ghostH, stmtH]);
const ghostModel = buildGrowthModel(
  [ghostH, stmtH],
  [ghostOpen, ghostEv, stmtEv],
  ghostDer,
  [{ id:'f1', name:'Vikas', relation:'Self', color:'#f59e0b', isPrimary:true }],
  'MARKET_TRADED',
  '2026-09-10',
);
eq('growth skips qty-0 ghost rows', ghostModel.holdings.some(r => r.id === 'gh0'), false);
eq('growth keeps live row', ghostModel.holdings.some(r => r.id === 'st1'), true);

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

console.log('\n--- V. Add-holding fields follow Indian instrument type ---');
eq('equity still uses units', usesUnits('EQUITY'), true);
eq('gold uses grams', usesUnits('GOLD_SGB', 'Sovereign Gold Bond'), true);
eq('PPF is a balance not units', usesUnits('FIXED_INCOME', 'PPF'), false);
eq('G-sec uses units', usesUnits('FIXED_INCOME', 'Bond / GOI'), true);
eq('flat is not units', usesUnits('REAL_ESTATE', 'Residential'), false);
eq('REIT uses units', usesUnits('REAL_ESTATE', 'REIT'), true);
eq('direct REIT is lumpsum', usesUnits('REAL_ESTATE', 'REIT', { altHoldMode: 'LUMPSUM' }), false);
eq('InvIT uses units', usesUnits('REAL_ESTATE', 'InvIT'), true);
eq('PMS is a mandate not units', usesUnits('SPECIALISED', 'PMS'), false);
eq('AIF is commitment not units', usesUnits('SPECIALISED', 'AIF'), false);
eq('demat AIF uses units', usesUnits('SPECIALISED', 'AIF', { altHoldMode: 'DEMAT' }), true);
eq('unlisted uses shares', usesUnits('SPECIALISED', 'Unlisted equity'), true);
eq('ESOP uses shares', usesUnits('SPECIALISED', 'ESOP'), true);
eq('NPS id is PRAN', identifierLabel('FIXED_INCOME', 'NPS'), 'PRAN (12 digits)');
eq('EPF id is UAN', identifierLabel('FIXED_INCOME', 'EPF'), 'UAN (12 digits)');
eq('SGB id is series', identifierLabel('GOLD_SGB', 'Sovereign Gold Bond'), 'SGB series');
eq('physical gold id is invoice', identifierLabel('GOLD_SGB', 'Physical gold (24K)'), 'Invoice / hallmark no.');
eq('loan id is account', identifierLabel('LIABILITY', 'Home loan'), 'Loan account number');
eq('12-digit UAN', looksLikeUan('100012345678'), true);
eq('short UAN rejected', looksLikeUan('12345'), false);
eq('12-digit PRAN', looksLikePran('110123981321'), true);
eq('SGB coupon default', defaultSgbCoupon(), 2.5);
const sgbDates = suggestLockInDates('GOLD_SGB', 'Sovereign Gold Bond', '2020-09-08');
eq('SGB matures in 8 years', sgbDates.maturity, '2028-09-08');
eq('SGB first exit in 5 years', sgbDates.earlyExit, '2025-09-08');
eq('PPF 15-year block', suggestLockInDates('FIXED_INCOME', 'PPF', '2011-04-01').maturity, '2026-04-01');
eq('SSY 21 years', suggestLockInDates('FIXED_INCOME', 'Sukanya Samriddhi (SSY)', '2018-02-07').maturity, '2039-02-07');
eq('equity id is symbol', identifierLabel('EQUITY', 'Large cap'), 'NSE/BSE Symbol');
eq('MF id is folio', identifierLabel('MUTUAL_FUND', 'Equity fund'), 'Folio Number');
eq('index/ETF id is mixed until held-as', identifierLabel('MUTUAL_FUND', 'Index / ETF'), 'Symbol or folio');
eq('ETF demat uses symbol', identifierLabel('MUTUAL_FUND', 'Index / ETF', 'DEMAT'), 'NSE/BSE Symbol');
eq('index fund folio', identifierLabel('MUTUAL_FUND', 'Index / ETF', 'FOLIO'), 'Folio Number');
eq('demat open-end folio is optional', identifierLabel('MUTUAL_FUND', 'Equity fund', 'DEMAT'), 'Folio (optional)');
eq('ELSS 3-year lock-in', suggestLockInDates('MUTUAL_FUND', 'ELSS (tax saving)', '2023-03-14').maturity, '2026-03-14');
eq('NSDL DP ID', looksLikeNsdlDpId('IN300214'), true);
eq('NSDL DP ID needs IN', looksLikeNsdlDpId('300214'), false);
eq('NSDL client 8 digits', looksLikeNsdlClientId('12345678'), true);
eq('CDSL 16-digit BOID', looksLikeBoid('1208160012345678'), true);
eq('short BOID rejected', looksLikeBoid('12081600'), false);
eq('Direct from scheme name', inferMfPlan('Parag Parikh Flexi Cap Fund - Direct Growth'), 'DIRECT');
eq('Regular from scheme name', inferMfPlan('HDFC Flexi Cap Fund Regular Plan'), 'REGULAR');
eq('IDCW from scheme name', inferMfOption('SBI Bluechip Fund Direct IDCW'), 'IDCW');
eq('Growth from scheme name', inferMfOption('Axis ELSS Tax Saver Direct Growth'), 'GROWTH');
eq('Nippon BeES is an ETF', nameLooksLikeEtf('Nippon India ETF Nifty BeES'), true);
eq('flexi cap is not an ETF', nameLooksLikeEtf('Parag Parikh Flexi Cap Fund'), false);
eq('NSDL mask is DP + client', dematAccountMask({ dpId: 'in300214', clientId: '12345678' }), 'IN300214 12345678');
eq('CDSL mask is BOID', dematAccountMask({ boId: '1208160012345678' }), '1208160012345678');
const mfLines = detailsLines({
  id: 'h_mf', name: 'Parag Parikh Flexi Cap', symbolOrFolio: '12345678',
  category: 'MUTUAL_FUND', subCategory: 'Equity fund', ownerId: 'f1',
  quantityScaled: '0', averageCostPerUnitMinor: '0', currentPriceMinor: '0',
  lastPriceUpdated: '', isPriceStale: false, priceSource: 't',
  acquiredOn: '2024-01-15',
  details: { mfPlan: 'DIRECT', mfOption: 'GROWTH', mfHeldAs: 'FOLIO' },
} as Holding);
eq('MF plan on sheet', mfLines.some(r => r.label === 'Plan' && r.value === 'Direct'), true);
eq('MF option on sheet', mfLines.some(r => r.label === 'Option' && r.value === 'Growth'), true);
eq('MF held as SOA', mfLines.some(r => r.label === 'Held as' && r.value === 'SOA folio'), true);
const buyCh: LedgerEvent = { ...ev('ch1','BUY','2024-01-02',10,100), chargesMinor: String(25 * 10000) };
const chFold = foldHolding([buyCh], 'EQUITY');
eq('STT/brokerage added to cost', Number(chFold.totalCostMinor)/1e4, 1025);
eq('Embassy is a REIT', nameLooksLikeReit('Embassy Office Parks REIT'), true);
eq('TCS is not a REIT', nameLooksLikeReit('Tata Consultancy Services'), false);
eq('Discovery Fund is AIF', nameLooksLikeAif('IIFL India Discovery Fund'), true);
eq('CAS Embassy → Real Estate', recategorizeCasAsset('Embassy Office Parks REIT', 'EQUITY'), 'REAL_ESTATE');
eq('CAS IndiGrid → Real Estate', recategorizeCasAsset('IndiGrid InvIT Fund', 'EQUITY'), 'REAL_ESTATE');
eq('CAS TCS stays equity', recategorizeCasAsset('Tata Consultancy Services Ltd', 'EQUITY'), 'EQUITY');
eq('CAS AIF from MF type', recategorizeCasAsset('Blume Ventures Fund IV', 'MUTUAL_FUND', 'AIF'), 'SPECIALISED');
eq('CAS REIT sub', casHoldingSubCategory('REAL_ESTATE', 'Embassy Office Parks REIT'), 'REIT');
eq('CAS InvIT sub', casHoldingSubCategory('REAL_ESTATE', 'IndiGrid InvIT'), 'InvIT');
eq('CAS AIF sub not Category II', casHoldingSubCategory('SPECIALISED', 'Edelweiss Crossover', 'AIF'), 'AIF');
eq('CAS REIT details are demat', casImportDetails({ category: 'REAL_ESTATE', casFileType: 'CDSL' })?.altHoldMode, 'DEMAT');
eq('direct REIT id is agreement', identifierLabel('REAL_ESTATE', 'REIT', undefined, { altHoldMode: 'LUMPSUM' }), 'Agreement / folio (optional)');
eq('demat AIF id is ISIN', identifierLabel('SPECIALISED', 'AIF', undefined, { altHoldMode: 'DEMAT' }), 'ISIN');

console.log('\n--- W. Cost basis from your records, FY-end interest ---');
const casOpen = ev('ob_rel', 'OPENING_BALANCE', '2026-08-31', 139, 1285, 'h_rel');
eq('opening parcel is the CAS line', earliestActiveParcel([casOpen], 'h_rel')?.id, 'ob_rel');
const relH: Holding = {
  ...hold('h_rel', 'EQUITY', 139, 1285, 1285, 'Other'),
  name: 'RELIANCE INDUSTRIES LIMITED EQUITY SHARES',
};
const paid = planInvestedCorrection({
  holding: relH,
  events: [casOpen],
  unitPriceMinor: BigInt(Math.round(850 * 1e4)),
  acquiredOn: '2021-03-15',
});
eq('correction keeps 139 shares', Number(BigInt(paid!.quantityScaled)) / 1e8, 139);
eq('correction unit is 850', Number(BigInt(paid!.unitPriceMinor)) / 1e4, 850);
eq('correction dated to trade date', paid!.eventDate, '2021-03-15');
const paidFold = foldHolding([casOpen, { ...paid!, id: 'cor_rel', createdAt: 't' }], 'EQUITY');
eq('invested is qty × paid', Math.round(Number(paidFold.totalCostMinor) / 1e4), 139 * 850);
eq('same price and date is a no-op', planInvestedCorrection({
  holding: relH, events: [casOpen],
  unitPriceMinor: BigInt(Math.round(1285 * 1e4)), acquiredOn: '2026-08-31',
}), null);
eq('blank date uses the statement date', planInvestedCorrection({
  holding: relH, events: [casOpen],
  unitPriceMinor: BigInt(Math.round(850 * 1e4)), acquiredOn: '',
})!.eventDate, '2026-08-31');

const aifSnap = ev('s_aif', 'SNAPSHOT_ADJUSTMENT', '2026-08-31', 25000, 170, 'h_aif3');
const aifHold: Holding = { ...hold('h_aif3', 'SPECIALISED', 25000, 170, 170, 'AIF'), name: 'INDIA DISCOVERY FUND II - CLASS B1' };
const aifPaid = planInvestedCorrection({
  holding: aifHold,
  events: [aifSnap],
  unitPriceMinor: BigInt(Math.round(140 * 1e4)),
  acquiredOn: '',
});
eq('AIF save without purchase date', aifPaid!.eventDate, '2026-08-31');
eq('AIF unit becomes 140', Number(BigInt(aifPaid!.unitPriceMinor)) / 1e4, 140);
const aifFold = foldHolding([aifSnap, { ...aifPaid!, id: 'c_aif', createdAt: 't' }], 'SPECIALISED');
eq('AIF qty stays 25000', Number(aifFold.totalQtyScaled) / 1e8, 25000);
eq('AIF invested is 25000 × 140', Math.round(Number(aifFold.totalCostMinor) / 1e4), 25000 * 140);
eq('CAS AIF cost=CMV is unknown', isUnknownInvested(aifHold, [aifSnap], BigInt(25000 * 170 * 10000), BigInt(25000 * 170 * 10000)), true);
eq('after correction cost is known', isUnknownInvested(aifHold, [aifSnap, { ...aifPaid!, id: 'c_aif', createdAt: 't' }], BigInt(25000 * 140 * 10000), BigInt(25000 * 170 * 10000)), false);
const ppfEq = hold('h_ppf_eq', 'FIXED_INCOME', 1, 500000, 500000, 'PPF');
ppfEq.name = 'PPF SBI';
eq('PPF balance is not unknown cost', isUnknownInvested(ppfEq, [], BigInt(500000 * 10000), BigInt(500000 * 10000)), false);
const aifDer = derivePositions([aifSnap], [aifHold]);
const aifRet = holdingReturn(aifHold, [aifSnap], aifDer, 'MARKET_TRADED', '2026-09-16');
eq('AIF return asks for cost', aifRet.primaryText, UNKNOWN_INVESTED_LABEL);
eq('AIF return is flagged unknown', aifRet.unknownInvested, true);
const aifModel = buildGrowthModel([aifHold], [aifSnap], aifDer, [{ id: 'f1', name: 'You', relation: 'Self', color: '#f59e0b' }], 'MARKET_TRADED', '2026-09-16');
eq('unknown AIF not in invested KPI', Number(aifModel.kpis.investedMinor), 0);
eq('unknown AIF counted', aifModel.kpis.unknownInvestedCount, 1);
eq('unknown AIF return pct is null', aifModel.kpis.absoluteReturnPct, null);

eq('FY ends after CAS date skip 31 Mar 2026', fyEndDatesAfter('2026-08-31', '2026-09-15').join(','), '');
eq('next FY end after Aug is Mar 2027', fyEndDatesAfter('2026-08-31', '2027-03-31').join(','), '2027-03-31');
eq('SGB from purchase fills 31 Mar dates', fyEndDatesAfter('2018-09-08', '2020-03-31').join(','), '2019-03-31,2020-03-31');
eq('full FY is 12 months', monthsHeldInFy('2011-04-01', '2026-03-31'), 12);
eq('bought Sep is 6 months to Mar', monthsHeldInFy('2018-09-08', '2019-03-31'), 6);
eq('2.5% of 1 lakh for a year', Number(interestMinor(1_000_000_000n, 2.5, 12)) / 1e4, 2500);

const sgbH: Holding = {
  ...hold('h_sgb2', 'GOLD_SGB', 10, 7200, 7200, 'Sovereign Gold Bond'),
  name: 'SGB 2018-19 Series VI',
  couponRatePercent: 2.5,
  issuePriceMinor: String(3119 * 10000),
  acquiredOn: '2018-09-08',
  lastPriceUpdated: '2026-08-31T00:00:00Z',
};
eq('SGB is payout interest', interestKindOf(sgbH), 'SGB');
eq('SGB principal is grams × issue', Number(interestSpecOf(sgbH)!.principalMinor) / 1e4, 31190);
const sgbPlan = planInterestCatchUp(sgbH, '2020-03-31', []);
eq('SGB posts three half-year coupons', sgbPlan?.events.length, 3);
eq('SGB first coupon six months after issue', sgbPlan?.events[0].eventDate, '2019-03-08');
eq('SGB does not add to gold value', sgbPlan?.currentPriceMinor, undefined);
eq('SGB coupon is half of 2.5% on issue', Math.round(Number(BigInt(sgbPlan!.events[0].totalAmountMinor)) / 1e4), 390);
const sgbAgain = planInterestCatchUp(sgbH, '2020-03-31', sgbPlan!.events.map(e => e.eventDate));
eq('SGB catch-up is idempotent', sgbAgain, null);

const ppfW: Holding = {
  ...hold('h_ppf2', 'FIXED_INCOME', 1, 1450000, 1940000, 'PPF'),
  name: 'Public Provident Fund',
  interestRatePercent: 7.1,
  acquiredOn: '2011-04-01',
  lastPriceUpdated: '2026-08-31T00:00:00Z',
};
eq('PPF is compound interest', interestKindOf(ppfW), 'PPF');
eq('PPF does not backfill before last valuation', planInterestCatchUp(ppfW, '2026-09-15', []), null);
const ppfFwd = planInterestCatchUp(ppfW, '2027-03-31', []);
eq('PPF posts the next 31 March', ppfFwd?.events.length, 1);
eq('PPF credit date', ppfFwd?.events[0].eventDate, '2027-03-31');
eq('PPF balance grows by the interest', BigInt(ppfFwd!.currentPriceMinor!) > BigInt(ppfW.currentPriceMinor), true);

const epfW: Holding = { ...ppfW, id: 'h_epf2', name: 'EPFO', subCategory: 'EPF', interestRatePercent: 8.25 };
eq('EPF kind', interestKindOf(epfW), 'EPF');
const ssyW: Holding = { ...ppfW, id: 'h_ssy2', name: 'SSY daughter', subCategory: 'Sukanya Samriddhi (SSY)', interestRatePercent: 8.2 };
eq('SSY kind', interestKindOf(ssyW), 'SSY');
const fdW: Holding = {
  ...hold('h_fd2', 'FIXED_INCOME', 1, 1000000, 1000000, 'Bank FD'),
  name: 'HDFC 3-year FD',
  interestRatePercent: 7.5,
  lastPriceUpdated: '2026-08-31T00:00:00Z',
};
eq('FD kind', interestKindOf(fdW), 'FD');
eq('FD without rate uses none if 0', interestKindOf({ ...fdW, interestRatePercent: undefined }), 'FD');
eq('FD needs a rate to post', planInterestCatchUp({ ...fdW, interestRatePercent: undefined }, '2027-03-31', []), null);
eq('FD with no payout choice does not post', planInterestCatchUp(fdW, '2027-03-31', []), null);
const fdPay: Holding = { ...fdW, acquiredOn: '2026-04-01', details: { compounding: 'QUARTERLY' } };
const fdPayPlan = planInterestCatchUp(fdPay, '2027-04-01', []);
eq('payout FD does not raise the deposit', fdPayPlan?.currentPriceMinor, undefined);
eq('quarterly FD pays four times', fdPayPlan?.events.length, 4);
const fdCum: Holding = { ...fdW, acquiredOn: '2026-04-01', details: { compounding: 'CUMULATIVE' } };
const fdCumPlan = planInterestCatchUp(fdCum, '2027-03-31', []);
eq('cumulative FD compounds quarterly', Number(BigInt(fdCumPlan!.events[0].totalAmountMinor)) / 1e4 > 75000, true);
const scss: Holding = { ...fdW, id: 'h_scss', name: 'SCSS', subCategory: 'SCSS', interestRatePercent: undefined, acquiredOn: '2026-04-01' };
eq('SCSS uses the default rate', interestKindOf(scss), 'SCSS');
const scssPlan = planInterestCatchUp(scss, '2026-10-02', []);
eq('SCSS pays quarterly and does not grow', scssPlan?.currentPriceMinor, undefined);
eq('SCSS first coupon is one quarter later', scssPlan?.events[0].eventDate, '2026-07-01');
const aifH: Holding = { ...hold('h_aif2', 'SPECIALISED', 25000, 170, 170, 'AIF'), name: 'INDIA DISCOVERY FUND II - CLASS B1' };
eq('AIF is not an interest product', interestKindOf(aifH), null);

const fdOld: Holding = {
  ...hold('h_fd_old', 'FIXED_INCOME', 1, 100000, 100000, 'Bank FD'),
  name: 'HDFC FD 2023',
  interestRatePercent: 7,
  acquiredOn: '2023-04-01',
  details: { compounding: 'CUMULATIVE' },
  lastPriceUpdated: '2026-09-26T00:00:00Z',
  priceSource: 'Manual Initial Entry',
};
const fdOldPlan = planInterestCatchUp(fdOld, '2026-09-26', []);
eq('cumulative FD backfills three financial years', fdOldPlan?.events.map(e => e.eventDate).join(','), '2024-03-31,2025-03-31,2026-03-31');
eq('cumulative FD balance grows', BigInt(fdOldPlan!.currentPriceMinor!) > BigInt(fdOld.currentPriceMinor), true);
eq('second year interest is larger', BigInt(fdOldPlan!.events[1].totalAmountMinor) > BigInt(fdOldPlan!.events[0].totalAmountMinor), true);
const fdGrown: Holding = {
  ...fdOld,
  currentPriceMinor: fdOldPlan!.currentPriceMinor,
  lastPriceUpdated: '2026-09-26T00:00:00Z',
  priceSource: 'FY interest',
};
eq('second catch-up does not repeat', planInterestCatchUp(fdGrown, '2026-09-26', fdOldPlan!.events.map(e => e.eventDate)), null);

const fdMonth: Holding = {
  ...fdOld,
  id: 'h_fd_month',
  acquiredOn: '2024-01-15',
  details: { compounding: 'MONTHLY' },
};
const fdMonthPlan = planInterestCatchUp(fdMonth, '2024-04-20', []);
eq('monthly payout posts each 15th', fdMonthPlan?.events.map(e => e.eventDate).join(','), '2024-02-15,2024-03-15,2024-04-15');
eq('payout FD does not grow the deposit', fdMonthPlan?.currentPriceMinor, undefined);

const ppfOpen: Holding = {
  ...hold('h_ppf_open', 'FIXED_INCOME', 1, 100000, 100000, 'PPF'),
  name: 'PPF from deposit',
  interestRatePercent: 7.1,
  acquiredOn: '2022-04-01',
  priceSource: 'Manual Initial Entry',
};
const ppfOpenPlan = planInterestCatchUp(ppfOpen, '2026-09-26', []);
eq('PPF equal to deposit backfills four years', ppfOpenPlan?.events.length, 4);
eq('PPF first credit is 31 Mar 2023', ppfOpenPlan?.events[0].eventDate, '2023-03-31');
eq('locked PPF balance is not rebuilt', planInterestCatchUp({ ...ppfOpen, priceSource: 'Manual Verified Entry' }, '2026-09-26', []), null);
const ppfGrown: Holding = {
  ...ppfOpen,
  currentPriceMinor: ppfOpenPlan!.currentPriceMinor,
  lastPriceUpdated: '2026-09-26T00:00:00Z',
};
eq('PPF catch-up is idempotent', planInterestCatchUp(ppfGrown, '2026-09-26', ppfOpenPlan!.events.map(e => e.eventDate)), null);

const otherH: Holding = {
  ...hold('h_other', 'FIXED_INCOME', 1, 50000, 50000, 'Other'),
  name: 'Staff deposit',
  interestRatePercent: 6,
  acquiredOn: '2025-04-01',
};
eq('other with a rate compounds', interestKindOf(otherH), 'OTHER');
eq('other posts last 31 March', planInterestCatchUp(otherH, '2026-09-26', [])?.events[0].eventDate, '2026-03-31');

const reitInc: Holding = {
  ...hold('h_reit_inc', 'REAL_ESTATE', 100, 300, 320, 'REIT'),
  name: 'Embassy Office Parks REIT',
  acquiredOn: '2026-01-01',
  details: {
    monthlyIncomeMinor: String(15000 * 10000),
    incomeDay: 15,
    incomeStart: '2026-01-10',
    incomeEnd: '2026-03-20',
  },
};
const incomePlan = planMonthlyIncome(reitInc, '2026-09-26', []);
eq('REIT income stops at the end date', incomePlan?.map(e => e.eventDate).join(','), '2026-01-15,2026-02-15,2026-03-15');
eq('REIT income note', incomePlan?.[0].notes?.startsWith('Monthly income'), true);
eq('REIT income amount', Number(BigInt(incomePlan![0].totalAmountMinor)) / 1e4, 15000);
eq('REIT income does not repeat', planMonthlyIncome(reitInc, '2026-09-26', incomePlan!.map(e => e.eventDate)), null);
eq('next REIT payday', nextMonthlyIncomeDate(reitInc, '2026-02-01'), '2026-02-15');
eq('no payday after the end date', nextMonthlyIncomeDate(reitInc, '2026-03-16'), null);
const dayClamp: Holding = {
  ...reitInc,
  id: 'h_reit_day',
  details: { monthlyIncomeMinor: String(1000 * 10000), incomeDay: 31, incomeStart: '2026-01-01' },
};
eq('income day clamps to 28', planMonthlyIncome(dayClamp, '2026-01-31', [])?.[0].eventDate, '2026-01-28');
const reitUp = upcomingFromHoldings([reitInc], [], '2026-01-01');
eq('coming up includes monthly income', reitUp.some(i => i.kind === 'INCOME' && i.date === '2026-01-15'), true);

console.log('\n--- X. Home books health, concentration, freshness ---');
eq('days 12 Sep to 16 Sep', daysBetweenIso('2026-09-12', '2026-09-16'), 4);
eq('previous FY of 2026-27', previousFinancialYear('2026-27'), '2025-26');
eq('FY 2025-26 ends', fyEndIso('2025-26'), '2026-03-31');
eq('fresh dates are ok', freshnessTone({ statementAsOf: '2026-09-12', pricesAsOf: '2026-09-16', todayIso: '2026-09-16' }), 'ok');
eq('old statement is amber', freshnessTone({ statementAsOf: '2026-07-01', pricesAsOf: '2026-09-16', todayIso: '2026-09-16' }), 'amber');
eq('very old statement is red', freshnessTone({ statementAsOf: '2026-04-01', pricesAsOf: '2026-09-16', todayIso: '2026-09-16' }), 'red');
const dashDerived = { positions: new Map(), errors: [] as string[] };
const eqCost = hold('h_eq', 'EQUITY', 10, 100, 100, 'Large Cap');
eqCost.name = 'Reliance';
const healthEq = buildBooksHealth({
  holdings: [eqCost],
  events: [],
  derived: dashDerived,
  members: [{ id: 'f1', name: 'You', relation: 'Self', color: '#f59e0b' }],
  casLatest: { asOf: null, byOwner: {} },
  selectedMemberId: 'ALL',
  sgbMode: 'MARKET_TRADED',
  todayIso: '2026-09-16',
});
eq('invested = CMV is flagged', healthEq.findings.some(f => f.kind === 'COST_EQUALS_VALUE'), true);
eq('health is amber not red', healthEq.tone, 'amber');
const moved = hold('h_eq2', 'EQUITY', 10, 100, 140, 'Large Cap');
moved.name = 'TCS';
const healthOk = buildBooksHealth({
  holdings: [moved],
  events: [],
  derived: dashDerived,
  members: [{ id: 'f1', name: 'You', relation: 'Self', color: '#f59e0b' }],
  casLatest: { asOf: null, byOwner: {} },
  selectedMemberId: 'ALL',
  sgbMode: 'MARKET_TRADED',
  todayIso: '2026-09-16',
});
eq('true gain is not a cost flag', healthOk.findings.some(f => f.kind === 'COST_EQUALS_VALUE'), false);
const staleCas = parseCasLatest(JSON.stringify({
  asOf: '2026-06-01',
  byOwner: { f1: { CDSL: { fileType: 'CDSL', filename: 'cas.pdf', periodTo: '2026-06-01' } } },
}));
const healthStale = buildBooksHealth({
  holdings: [moved],
  events: [],
  derived: dashDerived,
  members: [{ id: 'f1', name: 'You', relation: 'Self', color: '#f59e0b' }],
  casLatest: staleCas,
  selectedMemberId: 'ALL',
  sgbMode: 'MARKET_TRADED',
  todayIso: '2026-09-16',
});
eq('stale CDSL is flagged', healthStale.findings.some(f => f.kind === 'STALE_STATEMENT'), true);
eq('missing PAN with CAS is flagged', healthStale.findings.some(f => f.kind === 'MEMBER_PAN_MISSING'), true);
const ppf = hold('h_ppf', 'FIXED_INCOME', 1, 500000, 500000, 'PPF');
ppf.name = 'PPF SBI';
ppf.acquiredOn = '2020-04-01';
const healthPpf = buildBooksHealth({
  holdings: [ppf],
  events: [{
    id: 'ob', holdingId: 'h_ppf', holdingName: 'PPF SBI', ownerId: 'f1', eventType: 'OPENING_BALANCE',
    eventDate: '2020-04-01', quantityScaled: ppf.quantityScaled, unitPriceMinor: ppf.averageCostPerUnitMinor,
    totalAmountMinor: String(500000 * 10000), createdAt: 't',
  }],
  derived: dashDerived,
  members: [{ id: 'f1', name: 'You', relation: 'Self', color: '#f59e0b', panForStatements: 'ABCDE1234F' }],
  casLatest: { asOf: null, byOwner: {} },
  selectedMemberId: 'ALL',
  sgbMode: 'MARKET_TRADED',
  todayIso: '2026-09-16',
});
eq('PPF without last-FY interest is flagged', healthPpf.findings.some(f => f.kind === 'MISSING_FY_INTEREST'), true);
const conc = concentrationOf([
  { id: 'a', name: 'A', valueMinor: 60n * 10000n * 100n },
  { id: 'b', name: 'B', valueMinor: 30n * 10000n * 100n },
  { id: 'c', name: 'C', valueMinor: 10n * 10000n * 100n },
], 100n * 10000n * 100n, 2);
eq('top 2 share', Math.round(conc.topSharePct), 90);
eq('largest is A', conc.rows[0].name, 'A');

const snap = (date: string, issuer: string, rupees: number, owner = 'f1') => ({
  date, issuer, ownerId: owner, valueMinor: BigInt(rupees) * 10000n, investedMinor: 0n,
});
const stepsMix = buildStatementSteps({
  rows: [
    snap('2026-01-31', 'CDSL', 100),
    snap('2026-02-28', 'CAMS', 40),
    snap('2026-03-31', 'CDSL', 110),
  ],
  selectedMemberId: 'ALL',
  todayIso: '2026-03-31',
});
eq('Jan is CDSL only', Number(stepsMix[0].valueMinor) / 10000, 100);
eq('Feb stacks CAMS on last CDSL', Number(stepsMix[1].valueMinor) / 10000, 140);
eq('Mar replaces CDSL, keeps CAMS', Number(stepsMix[2].valueMinor) / 10000, 150);
eq('no pad when last date is today', stepsMix.some(s => s.isLivePad), false);
const stepsPad = buildStatementSteps({
  rows: [snap('2026-01-31', 'CDSL', 100)],
  selectedMemberId: 'ALL',
  todayIso: '2026-09-16',
});
eq('pads flat to today', stepsPad[stepsPad.length - 1].isLivePad, true);
eq('pad keeps last value', Number(stepsPad[1].valueMinor) / 10000, 100);
eq('other member is empty', buildStatementSteps({
  rows: [snap('2026-01-31', 'CDSL', 100, 'f1')],
  selectedMemberId: 'f2',
  todayIso: '2026-09-16',
}).length, 0);
eq('pct from 100 to 150', Math.round(firstToLastPct([100n, 150n])!), 50);
eq('pct one point is empty', firstToLastPct([100n]), null);
const carried = investedSeriesFrom([
  { date: '2026-01-31', valueMinor: 100n, investedMinor: 80n, issuers: ['CDSL'] },
  { date: '2026-03-31', valueMinor: 120n, investedMinor: 0n, issuers: ['CDSL'] },
], 90n);
eq('invested carries forward', Number(carried[1].investedMinor), 80);
const since = buildSinceLastStatement({
  snaps: [{ date: '2026-08-31', holdingId: 'h_eq2', ownerId: 'f1', valueMinor: 10n * 100n * 10000n }],
  holdings: [moved],
  derived: dashDerived,
  events: [],
  selectedMemberId: 'ALL',
  sgbMode: 'MARKET_TRADED',
  todayIso: '2026-09-16',
});
eq('since last as-of', since.asOf, '2026-08-31');
eq('since last now is 10 × 140', Number(since.nowMinor) / 10000, 1400);
eq('since last then is 10 × 100', Number(since.thenMinor) / 10000, 1000);
eq('since last mover is TCS', since.movers[0].name, 'TCS');
const need = unknownCostRows({
  holdings: [eqCost, moved],
  events: [],
  derived: dashDerived,
  sgbMode: 'MARKET_TRADED',
});
eq('Pass D lists cost=CMV', need.some(r => r.name === 'Reliance'), true);
eq('Pass D skips true gain', need.some(r => r.name === 'TCS'), false);

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

  const a1 = await hashSecret(normaliseAnswer('Fluffy'));
  const a2 = await hashSecret(normaliseAnswer('Pune'));
  const acct = {
    email: 'vikas@example.com',
    firstName: 'Vikas',
    lastName: 'Shah',
    passwordSalt: hashed.salt,
    passwordHash: hashed.hash,
    passwordIterations: hashed.iterations,
    passwordChangedAt: '2026-01-01T00:00:00.000Z',
    panMasked: maskPan('ABCDE1234F'),
    panForStatements: 'ABCDE1234F',
    dateOfBirth: '1980-04-01',
    q1Id: 'pet',
    q1Hash: packHash(a1),
    q2Id: 'city',
    q2Hash: packHash(a2),
    createdAt: '2026-01-01T00:00:00.000Z',
  };
  const payload: VaultBackup = {
    kind: BACKUP_KIND,
    version: 2,
    exportedAt: '2026-09-19T00:00:00.000Z',
    account: acct,
    familyMembers: [],
    holdings: [{ id: 'h1', name: 'Secret Fund', ownerId: 'f1' } as any],
    ledgerEvents: [],
    accounts: [],
    goals: [],
    casLatest: null,
  };
  const packed = await packBackup(payload, 'vikas@example.com', 'Abcd1234!');
  eq('backup is binary not json', isBinaryBackup(packed), true);
  const dump = new TextDecoder().decode(packed);
  eq('notepad does not see holding name', dump.includes('Secret Fund'), false);
  eq('notepad does not see PAN', dump.includes('ABCDE1234F'), false);
  eq('notepad does not see email', dump.includes('vikas@example.com'), false);
  eq('file is not pretty json', dump.trimStart().startsWith('{'), false);
  eq('plain json detector on binary is false', looksLikePlainJsonBackup(dump), false);
  let plainRejected = false;
  try {
    await unpackBackup(new TextEncoder().encode(JSON.stringify(payload)), 'vikas@example.com', 'Abcd1234!');
  } catch (e) {
    plainRejected = e instanceof Error && /unlocked backup/i.test(e.message);
  }
  eq('refuses plaintext json backup', plainRejected, true);
  const opened = await unpackBackup(packed, 'vikas@example.com', 'Abcd1234!');
  eq('backup round trip holding', opened.holdings[0].name, 'Secret Fund');
  let badKey = false;
  try { await unpackBackup(packed, 'vikas@example.com', 'WrongPass1!'); } catch { badKey = true; }
  eq('wrong vault password cannot unpack', badKey, true);
  let badUser = false;
  try { await unpackBackup(packed, 'other@example.com', 'Abcd1234!'); } catch { badUser = true; }
  eq('wrong email cannot unpack', badUser, true);
  eq('logged-in restore only needs login', await verifyRestoreIdentity(opened, {
    email: 'vikas@example.com', password: 'Abcd1234!', strict: false,
  }), null);
  eq('login restore ignores wrong PAN', await verifyRestoreIdentity(opened, {
    email: 'vikas@example.com', password: 'Abcd1234!', pan: 'XXXXX1234X', strict: false,
  }), null);
  eq('new device needs matching PAN', await verifyRestoreIdentity(opened, {
    email: 'vikas@example.com', password: 'Abcd1234!', pan: 'XXXXX1234X',
    dateOfBirth: '1980-04-01', q1Id: 'pet', q1Answer: 'Fluffy', q2Id: 'city', q2Answer: 'Pune', strict: true,
  }), 'PAN does not match this backup.');
  eq('new device needs matching DOB', await verifyRestoreIdentity(opened, {
    email: 'vikas@example.com', password: 'Abcd1234!', pan: 'ABCDE1234F',
    dateOfBirth: '1990-01-01', q1Id: 'pet', q1Answer: 'Fluffy', q2Id: 'city', q2Answer: 'Pune', strict: true,
  }), 'Date of birth does not match this backup.');
  eq('new device needs matching answers', await verifyRestoreIdentity(opened, {
    email: 'vikas@example.com', password: 'Abcd1234!', pan: 'ABCDE1234F',
    dateOfBirth: '1980-04-01', q1Id: 'pet', q1Answer: 'Wrong', q2Id: 'city', q2Answer: 'Pune', strict: true,
  }), 'Security answers do not match this backup.');
  eq('new device full identity passes', await verifyRestoreIdentity(opened, {
    email: 'vikas@example.com', password: 'Abcd1234!', pan: 'ABCDE1234F',
    dateOfBirth: '1980-04-01', q1Id: 'pet', q1Answer: 'fluffy', q2Id: 'city', q2Answer: 'Pune', strict: true,
  }), null);

  console.log('\n--- Y. Licence and onboarding ---');
  eq('trial is 30 days', TRIAL_DAYS, 30);
  eq('only two prices', Object.keys(PRICE_INR).sort().join(','), 'lifetime,supportAnnual');
  eq('lifetime price 249', PRICE_INR.lifetime, 249);
  eq('support annual 299', PRICE_INR.supportAnnual, 299);
  eq('no first-year intro price', !('supportWithLicence' in PRICE_INR), true);
  const trial = derivePlan({ lifetime: false, supportActive: false, trialStartedAt: new Date(Date.now() - 2*86400000).toISOString() });
  eq('two days in still trial', trial.plan, 'trial');
  eq('days left roughly 28', trial.daysLeft >= 27 && trial.daysLeft <= 28, true);
  const expired = derivePlan({ lifetime: false, supportActive: false, trialStartedAt: new Date(Date.now() - 40*86400000).toISOString() });
  eq('day 40 is expired', expired.plan, 'expired');
  eq('expired app locked', appUnlocked({ ...expired, lifetime: false, supportActive: false, trialStartedAt: null, sideloadBlocked: false, playAccountBound: false, source: 'preview', supportRenewsAt: null } as any), false);
  const life = derivePlan({ lifetime: true, supportActive: false, trialStartedAt: null });
  eq('lifetime plan', life.plan, 'lifetime');
  eq('lifetime unlocks', appUnlocked({ plan: 'lifetime', lifetime: true, supportActive: false, daysLeft: 0, trialStartedAt: null, trialEndsAt: null, supportRenewsAt: null, playAccountBound: true, sideloadBlocked: false, source: 'play' }), true);
  eq('support enhancements need both', enhancementsEnabled({ plan: 'lifetime_support', lifetime: true, supportActive: true, daysLeft: 0, trialStartedAt: null, trialEndsAt: null, supportRenewsAt: null, playAccountBound: true, sideloadBlocked: false, source: 'play' }), true);
  eq('no support no enhancements', enhancementsEnabled({ plan: 'lifetime', lifetime: true, supportActive: false, daysLeft: 0, trialStartedAt: null, trialEndsAt: null, supportRenewsAt: null, playAccountBound: true, sideloadBlocked: false, source: 'play' }), false);
  eq('onboarding has six slides', ONBOARDING_SLIDES.length, 6);
  eq('price slide mentions trial', ONBOARDING_SLIDES.some(s => /30/.test(s.enBody) && /249/.test(s.enBody)), true);
  eq('price slide has 299 not a bundle', ONBOARDING_SLIDES.some(s => /299/.test(s.enBody) && !/250/.test(s.enBody) && !/499/.test(s.enBody)), true);
  eq('hindi price has no first-year 250', ONBOARDING_SLIDES.every(s => !/₹250/.test(s.hiBody) && !/₹499/.test(s.hiBody)), true);
  eq('hindi columns present', ONBOARDING_SLIDES.every(s => s.hiTitle.length > 4 && s.enTitle.length > 4), true);
  eq('days remaining helper', daysRemaining(new Date().toISOString()) , 30);
  eq('sideload without licence locked', appUnlocked({ plan: 'trial', lifetime: false, supportActive: false, daysLeft: 30, trialStartedAt: new Date().toISOString(), trialEndsAt: null, supportRenewsAt: null, playAccountBound: false, sideloadBlocked: true, source: 'play' }), false);
  eq('sideload with lifetime open', appUnlocked({ plan: 'lifetime', lifetime: true, supportActive: false, daysLeft: 0, trialStartedAt: null, trialEndsAt: null, supportRenewsAt: null, playAccountBound: true, sideloadBlocked: true, source: 'play' }), true);
  eq('support blocked without lifetime', canPurchaseSupport({ plan: 'trial', lifetime: false, supportActive: false, daysLeft: 30, trialStartedAt: new Date().toISOString(), trialEndsAt: null, supportRenewsAt: null, playAccountBound: false, sideloadBlocked: false, source: 'preview' }), false);
  eq('support allowed after lifetime', canPurchaseSupport({ plan: 'lifetime', lifetime: true, supportActive: false, daysLeft: 0, trialStartedAt: null, trialEndsAt: null, supportRenewsAt: null, playAccountBound: true, sideloadBlocked: false, source: 'play' }), true);
  eq('envelope magic', encTest.MAGIC, 'GUDENC01');
  const wrapSalt = crypto.getRandomValues(new Uint8Array(16));
  const wrapKey = await encTest.deriveDeviceKey(wrapSalt);
  const wrapped = await encTest.encryptBytes(new TextEncoder().encode('vault-bytes'), wrapKey, wrapSalt);
  eq('magic on packed vault', encTest.hasMagic(wrapped), true);
  const unwrapped = await encTest.decryptBytes(wrapped, wrapKey, true);
  eq('envelope round trip', new TextDecoder().decode(unwrapped), 'vault-bytes');

  console.log('\n--- Z. Terms and backup schedule ---');
  eq('terms version dated', TERMS_VERSION, '2026-09-21');
  eq('terms text id stable', TERMS_TEXT_ID, 'GUD-TOS-2026-09-21-v2');
  eq('terms has 21 sections', TERMS_SECTIONS.length, 21);
  const tos = termsPlainText();
  eq('no personal name in terms', /Vikas Shah/i.test(tos), false);
  eq('no proprietor in terms', /Proprietor/i.test(tos), false);
  eq('company grants the licence', /licence to use the App is granted and distributed solely by GrowUrDhan/.test(tos), true);
  eq('only two paid products', /only two paid products/.test(tos) && /₹249/.test(tos) && /₹299/.test(tos), true);
  eq('lifetime mandatory before support', /lifetime licence is mandatory before you may buy the support/.test(tos), true);
  eq('no leftover bundle in terms', !/₹250/.test(tos) && !/₹499/.test(tos), true);
  eq('not an investment adviser', /Investment Adviser/i.test(tos), true);
  eq('SEBI named', /SEBI/i.test(tos), true);
  eq('liability cap present', /1,000/.test(tos), true);
  eq('IT Act electronic record', /Information Technology Act/.test(tos), true);
  eq('password not stored for schedule', /memory only/i.test(tos), true);
  eq('Drive/OneDrive described', /Google Drive/.test(tos) && /OneDrive/.test(tos), true);
  eq('not accepted before tap', hasAcceptedCurrentTerms(), false);
  const accepted = acceptCurrentTerms();
  eq('accept records version', accepted.version, TERMS_VERSION);
  eq('accepted after tap', hasAcceptedCurrentTerms(), true);
  const extra = pruneToLastN(
    [{ n: 'a', t: 1 }, { n: 'b', t: 5 }, { n: 'c', t: 3 }, { n: 'd', t: 4 }, { n: 'e', t: 2 }, { n: 'f', t: 6 }],
    BACKUP_KEEP_LAST,
    x => x.t,
  );
  eq('prune drops the oldest beyond 5', extra.map(x => x.n).join(','), 'a');
  eq('keep last is 5', BACKUP_KEEP_LAST, 5);
  eq('schedule defaults off', DEFAULT_BACKUP_SCHEDULE.enabled, false);
  eq('both triggers on by default', DEFAULT_BACKUP_SCHEDULE.onOpen && DEFAULT_BACKUP_SCHEDULE.onCasImport, true);
  const scheduledName = suggestedScheduledBackupName(new Date('2026-09-20T12:34:56.000Z'));
  eq('scheduled name has time', /growurdhan_20260920_123456\.kosha/.test(scheduledName), true);
  eq('manual name is date only', suggestedBackupName(new Date('2026-09-20T12:34:56.000Z')), 'growurdhan_2026-09-20.kosha');

  console.log('\n--- AA. Play review prompt ---');
  eq('first ask is 7 days', FEEDBACK_FIRST_DELAY_DAYS, 7);
  eq('snooze is 2 days', FEEDBACK_SNOOZE_DAYS, 2);
  eq('play package id', PLAY_PACKAGE_ID, 'in.growurdhan.app');
  resetFeedbackSession();
  const now = new Date('2026-09-21T12:00:00.000Z');
  const fresh = seedFirstSeen(EMPTY_FEEDBACK, now.toISOString(), now);
  eq('fresh clock uses trial start', fresh.firstSeenAt, now.toISOString());
  eq('day 0 hidden', shouldShowFeedbackPrompt(fresh, now), false);
  eq('day 6 hidden', shouldShowFeedbackPrompt(fresh, new Date('2026-09-27T11:59:00.000Z')), false);
  eq('day 7 shown', shouldShowFeedbackPrompt(fresh, new Date('2026-09-28T12:00:00.000Z')), true);
  const snoozed = snoozeFeedback(fresh, new Date('2026-09-28T12:00:00.000Z'));
  eq('just closed hidden', shouldShowFeedbackPrompt(snoozed, new Date('2026-09-28T12:00:00.000Z')), false);
  eq('one day later still hidden', shouldShowFeedbackPrompt(snoozed, new Date('2026-09-29T12:00:00.000Z')), false);
  eq('two days later shown again', shouldShowFeedbackPrompt(snoozed, new Date('2026-09-30T12:00:01.000Z')), true);
  const rated = completeFeedback(fresh, new Date('2026-09-28T12:00:00.000Z'));
  eq('after rate never shown', shouldShowFeedbackPrompt(rated, new Date('2026-12-01T00:00:00.000Z')), false);
  let nag = seedFirstSeen(EMPTY_FEEDBACK, '2026-01-01T00:00:00.000Z', now);
  for (let i = 0; i < FEEDBACK_MAX_PROMPTS; i++) nag = snoozeFeedback(nag, now);
  eq('stops after max dismissals', shouldShowFeedbackPrompt(nag, new Date('2026-12-01T00:00:00.000Z')), false);
  resetFeedbackSession();
  eq('session claim first', claimFeedbackPrompt(seedFirstSeen(EMPTY_FEEDBACK, '2026-01-01T00:00:00.000Z', now), now), true);
  eq('session claim once', claimFeedbackPrompt(seedFirstSeen(EMPTY_FEEDBACK, '2026-01-01T00:00:00.000Z', now), now), false);
  resetFeedbackSession();

  const cdsl = classifyStatementFilename('AUG2026_AA07970379_TXN.pdf');
  eq('cdsl cas id', cdsl && cdsl.casId, 'AA07970379');
  eq('cdsl holdings file', classifyStatementFilename('MAR2022_AA07970379_HLD.pdf')?.variant, 'HLD');
  eq('nps pran', classifyStatementFilename('1120330238_Aug2026.pdf')?.pran, '1120330238');
  eq('cdsl sender', issuerFromSender('eCAS <eCAS@cdslstatement.com>'), 'CDSL');
  eq('nps sender', issuerFromSender('nps-statements@mailer.proteantech.in'), 'NPS');
  const members = [
    { id: 'a', gmailSync: true, casId: 'AA07970379', pran: '1120330238' },
    { id: 'b', gmailSync: false, casId: 'ZZ00000000' },
  ];
  eq('cdsl match', decideStatementMail({ filename: 'AUG2026_AA07970379_TXN.pdf', from: 'eCAS@cdslstatement.com' }, members).action, 'download');
  eq('other cas skipped', decideStatementMail({ filename: 'AUG2026_BB11111111_TXN.pdf', from: 'eCAS@cdslstatement.com' }, members).action, 'skip');
  eq('nps match', decideStatementMail({ filename: '1120330238_Aug2026.pdf', from: 'nps-statements@mailer.proteantech.in' }, members).action, 'download');
  eq('cams needs pan later', decideStatementMail({ filename: 'statement.pdf', from: 'donotreply@camsonline.com' }, members).action, 'download');
  const picked = newestNamedStatements([
    { filename: 'JUL2021_AA07970379_TXN.pdf' },
    { filename: 'AUG2026_AA07970379_HLD.pdf' },
    { filename: 'AUG2026_AA07970379_TXN.pdf' },
    { filename: '1120330238_Jul2021.pdf' },
    { filename: '1120330238_Aug2026.pdf' },
  ]);
  eq('keeps newest cdsl and nps', picked.keep.length, 2);
  eq('keeps txn over holdings', picked.keep.some(f => f.filename === 'AUG2026_AA07970379_TXN.pdf'), true);
  eq('keeps newest nps', picked.keep.some(f => f.filename === '1120330238_Aug2026.pdf'), true);
  eq('older months set aside', picked.older.length, 3);
  const history = filesForMember([
    { filename: 'JUL2021_AA07970379_TXN.pdf' },
    { filename: 'AUG2026_AA07970379_TXN.pdf' },
    { filename: '1113981321_Jan2024.pdf' },
    { filename: '1113981321_Aug2026.pdf' },
  ], { id: 'a', casId: 'AA07970379', pran: '1113981321', casIdSource: 'profile', pranSource: 'profile' });
  eq('typed id keeps newest 2026 files only', history.keep.map(f => f.filename).sort().join(','), '1113981321_Aug2026.pdf,AUG2026_AA07970379_TXN.pdf');
  const after = filesForMember([
    { filename: '1113981321_Jan2024.pdf' },
    { filename: '1113981321_Aug2026.pdf' },
  ], { id: 'a', pran: '1113981321', pranSource: 'import', pranSyncAfter: '2024-01-31' });
  eq('hand import keeps only later nps', after.keep.map(f => f.filename).join(','), '1113981321_Aug2026.pdf');

  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail?1:0);
})();
