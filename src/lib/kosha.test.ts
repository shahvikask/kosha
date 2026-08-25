import { foldHolding, financialYearOf, assessmentYearOf, calendarDays } from './lots';
import { derivePositions, openingBalanceFor, marketValueMinor } from './positions';
import { ruleSetFor, classifyGain, isEquityOriented, grandfatheredCostMinor, applyRateMinor } from './taxRules';
import { encryptJson, decryptJson, isEncryptedBlob, generateRecoveryKey, maskPan, WrongPassphraseError } from './vault';
import { xirr } from './returns';
import { LedgerEvent, Holding } from '../types';

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

  console.log(`\n${pass} passed, ${fail} failed`);
  process.exit(fail?1:0);
})();
