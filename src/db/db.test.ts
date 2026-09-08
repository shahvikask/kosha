import { NodeSqlDriver } from './nodeDriver';
import { Repository } from './repository';
import { migrateFromLocalStorage, LS_KEYS } from './migrate';
import { isAppendOnlyViolation } from './driver';
import { derivePositions } from '../lib/positions';
import { QTY_SCALE } from '../lib/money';
import { LedgerEvent, Holding, FamilyMember } from '../types';

let pass=0, fail=0;
const eq=(n:string,a:any,b:any)=>{const ok=String(a)===String(b);ok?pass++:fail++;console.log(`${ok?'ok  ':'FAIL'} ${n}: ${a}${ok?'':'  | want '+b}`)};

const ev=(id:string,t:any,d:string,q:number,p:number,hid='h1'):LedgerEvent=>({
  id,holdingId:hid,holdingName:'Reliance',ownerId:'f1',eventType:t,eventDate:d,
  quantityScaled:String(BigInt(Math.round(q*1e8))),unitPriceMinor:String(BigInt(Math.round(p*1e4))),
  totalAmountMinor:String(BigInt(Math.round(q*p*1e4))),createdAt:'2024-01-01T00:00:00Z'} as LedgerEvent);
const hold=(id:string,name:string,price:number):Holding=>({
  id,name,symbolOrFolio:'X',category:'EQUITY',subCategory:'Large Cap',ownerId:'f1',
  quantityScaled:'0',averageCostPerUnitMinor:'0',currentPriceMinor:String(price*10000),
  lastPriceUpdated:'2026-08-01T00:00:00Z',isPriceStale:false,priceSource:'t'} as Holding);
const fam:FamilyMember = {id:'f1',name:'Self',relation:'Self',color:'#000',panMasked:'ABCDE****F',isPrimary:true};
const famSpouse:FamilyMember = {id:'f2',name:'Spouse',relation:'Spouse',color:'#ec4899',isPrimary:false};

(async () => {
  console.log('--- A. Real Annex A DDL executes ---');
  const drv = new NodeSqlDriver();
  const repo = new Repository(drv);
  await repo.initialise();
  eq('schema version', await repo.schemaVersion(), 3);
  const tables = await drv.query<{name:string}>(
    `SELECT name FROM sqlite_master WHERE type='table' ORDER BY name`);
  eq('tables created', tables.length >= 13, true);
  const trig = await drv.query<{name:string}>(
    `SELECT name FROM sqlite_master WHERE type='trigger' ORDER BY name`);
  eq('triggers created', trig.map(t=>t.name).join(','),
     'tg_ledger_event__no_delete,tg_ledger_event__no_update,tg_tax_rule_set__no_update');
  eq('idempotent init', await (async()=>{await repo.initialise();return 'ok'})(), 'ok');

  console.log('\n--- B. Append-only is enforced by the DATABASE, not by code ---');
  await repo.upsertEntity(fam);
  await repo.upsertHolding(hold('h1','Reliance Industries',2950));
  await repo.appendEvent(ev('e1','BUY','2024-03-14',100,2450));
  let blocked=false, msg='';
  try { await drv.exec(`UPDATE ledger_event SET quantity_scaled = 999 WHERE ledger_event_id='e1'`); }
  catch(e){ blocked=isAppendOnlyViolation(e); msg=(e as Error).message; }
  eq('UPDATE blocked', blocked, true);
  blocked=false;
  try { await drv.exec(`DELETE FROM ledger_event WHERE ledger_event_id='e1'`); }
  catch(e){ blocked=isAppendOnlyViolation(e); }
  eq('DELETE blocked', blocked, true);
  eq('event survived', (await repo.allEvents()).length, 1);

  console.log('\n--- C. No REAL columns anywhere (SRS-LED-007) ---');
  const cols = await drv.query<{tbl:string,name:string,type:string}>(
    `SELECT m.name AS tbl, p.name, p.type FROM sqlite_master m
     JOIN pragma_table_info(m.name) p WHERE m.type='table'`);
  const reals = cols.filter(c => c.type.toUpperCase().includes('REAL') || c.type.toUpperCase().includes('FLOAT'));
  eq('zero float columns', reals.length, 0);

  console.log('\n--- D. Deleting a holding keeps its events ---');
  await repo.deleteHolding('h1');
  eq('holding gone', (await repo.allHoldings()).length, 0);
  eq('events kept', (await repo.allEvents()).length, 1);

  console.log('\n--- E. Duplicate detection in SQL ---');
  await repo.upsertHolding(hold('h1','Reliance Industries',2950));
  const e2 = ev('e2','BUY','2024-03-14',100,2450);
  eq('dup detected', await repo.findDuplicate('f1','h1','2024-03-14',e2.quantityScaled,e2.totalAmountMinor), true);
  eq('non-dup not flagged', await repo.findDuplicate('f1','h1','2025-01-01',e2.quantityScaled,e2.totalAmountMinor), false);

  console.log('\n--- F. Same file cannot be imported twice ---');
  await repo.recordBatch({id:'b1',filename:'cas_sept24.pdf',kind:'CAS',sha256:'abc123',
    rowsNew:5,rowsDuplicate:0,rowsConflict:0});
  const seen = await repo.batchBySha('abc123');
  eq('file recognised', seen?.filename, 'cas_sept24.pdf');
  eq('unknown file', await repo.batchBySha('zzz'), null);

  console.log('\n--- G. Prices are observations, never overwritten ---');
  await repo.addPrice('h1','2026-08-20','CLOSE','29000000','bundle-1');
  await repo.addPrice('h1','2026-08-21','CLOSE','29500000','bundle-2');
  const px = await repo.latestPrice('h1','2026-08-21');
  eq('latest price', px?.priceMinor, '29500000');
  const asOfYesterday = await repo.latestPrice('h1','2026-08-20');
  eq('historical price reproducible', asOfYesterday?.priceMinor, '29000000');
  const n = await drv.query<{n:number}>(`SELECT COUNT(*) AS n FROM price_observation`);
  eq('both rows kept', n[0].n, 2);

  console.log('\n--- H. localStorage migration, verified and non-destructive ---');
  const store = new Map<string,string>();
  const fakeLS = {
    getItem:(k:string)=>store.get(k) ?? null,
    setItem:(k:string,v:string)=>{store.set(k,v)},
    removeItem:(k:string)=>{store.delete(k)},
  };
  const oldHoldings=[hold('m1','HDFC Flexi Cap',125), hold('m2','Reliance',2950)];
  const oldEvents=[ev('me1','BUY','2024-05-19',1000,125,'m1'),
                   ev('me2','BUY','2024-03-14',100,2450,'m2'),
                   ev('me3','SELL','2025-04-09',40,2750,'m2')];
  store.set(LS_KEYS.holdings, JSON.stringify(oldHoldings));
  store.set(LS_KEYS.events, JSON.stringify(oldEvents));
  store.set(LS_KEYS.family, JSON.stringify([fam]));

  const drv2 = new NodeSqlDriver(); const repo2 = new Repository(drv2);
  await repo2.initialise();
  const rep = await migrateFromLocalStorage(repo2, fakeLS as any);
  eq('migration ran', rep.ran, true);
  eq('events moved', rep.events, 3);
  eq('holdings moved', rep.holdings, 2);
  eq('VERIFIED', rep.verified, true);
  const before = Number(rep.valueBeforeMinor)/1e4;
  const after  = Number(rep.valueAfterMinor)/1e4;
  console.log(`     value before Rs ${before.toLocaleString('en-IN')}  after Rs ${after.toLocaleString('en-IN')}`);
  eq('value preserved exactly', rep.valueAfterMinor, rep.valueBeforeMinor);
  eq('old data BACKED UP not deleted', store.has(LS_KEYS.holdings+'__pre_sqlite_backup'), true);
  eq('old key cleared', store.has(LS_KEYS.holdings), false);
  const rep2 = await migrateFromLocalStorage(repo2, fakeLS as any);
  eq('idempotent - second run does nothing', rep2.ran, false);

  console.log('\n--- I. Round-trip: SQLite data still folds correctly ---');
  const be = await repo2.allEvents(); const bh = await repo2.allHoldings();
  const d = derivePositions(be, bh);
  const rel = d.positions.get('m2')!;
  eq('qty after round trip', Number(rel.quantityScaled)/1e8, 60);
  eq('realised gain preserved', Number(rel.realisedGainMinor)/1e4, 12000);


  console.log('\n--- J. Holdings with no purchase history keep their balance ---');
  const store3 = new Map<string,string>();
  const ls3 = { getItem:(k:string)=>store3.get(k)??null,
    setItem:(k:string,v:string)=>{store3.set(k,v)}, removeItem:(k:string)=>{store3.delete(k)} };
  const legacy = [
    {...hold('g1','Sovereign Gold Bond 2018-19 VI',7450), quantityScaled:String(BigInt(100)*100000000n), averageCostPerUnitMinor:'60000000'},
    {...hold('g2','24K Physical Gold Bars',7580), quantityScaled:String(BigInt(50)*100000000n), averageCostPerUnitMinor:'55000000'},
  ] as Holding[];
  store3.set(LS_KEYS.holdings, JSON.stringify(legacy));
  store3.set(LS_KEYS.events, JSON.stringify([]));
  store3.set(LS_KEYS.family, JSON.stringify([fam]));
  const drv3 = new NodeSqlDriver(); const repo3 = new Repository(drv3);
  await repo3.initialise();
  const rep3 = await migrateFromLocalStorage(repo3, ls3 as any);
  eq('opening balances created', rep3.openingBalances, 2);
  eq('verified', rep3.verified, true);
  const h3 = await repo3.allHoldings(); const e3 = await repo3.allEvents();
  const d3 = derivePositions(e3, h3);
  eq('SGB qty preserved', Number(d3.positions.get('g1')!.quantityScaled)/1e8, 100);
  eq('bars qty preserved', Number(d3.positions.get('g2')!.quantityScaled)/1e8, 50);
  let tot3 = 0n;
  for (const h of h3) tot3 += (d3.positions.get(h.id)!.quantityScaled * BigInt(h.currentPriceMinor))/QTY_SCALE;
  eq('gold total', Number(tot3)/1e4, 100*7450 + 50*7580);
  eq('events are OPENING_BALANCE', e3.every(x=>x.eventType==='OPENING_BALANCE'), true);
  await drv3.close();

  console.log('\n--- K. Family profile persists and Self cannot be deleted ---');
  const drv4 = new NodeSqlDriver(); const repo4 = new Repository(drv4);
  await repo4.initialise();
  await repo4.upsertEntity(fam);
  await repo4.upsertEntity(famSpouse);
  const ents = await repo4.allEntities();
  const self = ents.find(e => e.id==='f1')!;
  const spouse = ents.find(e => e.id==='f2')!;
  eq('self relation', self.relation, 'Self');
  eq('self is primary', self.isPrimary, true);
  eq('spouse relation', spouse.relation, 'Spouse');
  eq('spouse not primary', spouse.isPrimary, false);
  let primaryBlocked=false;
  try { await repo4.deleteEntity('f1'); }
  catch(e){ primaryBlocked = String((e as Error).message).includes('cannot be deleted'); }
  eq('primary delete refused', primaryBlocked, true);
  eq('primary still present', (await repo4.allEntities()).some(e=>e.id==='f1'), true);

  console.log('\n--- L. Extra member cascade removes holdings and events ---');
  const spouseHold:Holding = {...hold('h_sp','Spouse TCS',4280), ownerId:'f2'};
  await repo4.upsertHolding(spouseHold);
  const spouseEv:LedgerEvent = {...ev('e_sp2','BUY','2024-06-02',5,3400,'h_sp'), ownerId:'f2'};
  await repo4.appendEvent(spouseEv);
  const cas = await repo4.deleteEntity('f2');
  eq('cascade holdings', cas.deletedHoldingsCount, 1);
  eq('cascade events', cas.deletedEventsCount, 1);
  eq('spouse gone', (await repo4.allEntities()).some(e=>e.id==='f2'), false);
  eq('self remains', (await repo4.allEntities()).some(e=>e.id==='f1'), true);
  eq('spouse holdings gone', (await repo4.allHoldings()).some(h=>h.ownerId==='f2'), false);
  await repo4.upsertHolding(hold('h1','Reliance Industries',2950));
  await repo4.appendEvent(ev('e_keep','BUY','2024-03-14',1,2450,'h1'));
  eq('append-only trigger restored', await (async()=>{
    let blocked=false;
    try { await drv4.exec(`DELETE FROM ledger_event WHERE ledger_event_id='e_keep'`); }
    catch{ blocked=true; }
    return blocked;
  })(), true);

  console.log('\n--- M. CAS instrument fields round-trip ---');
  const casH:Holding = {
    ...hold('h_mf','Parag Parikh Flexi Cap',84.15),
    amc:'PPFAS', rta:'CAMS', amfiCode:'122639', schemeType:'EQUITY',
    institutionName:'PPFAS Mutual Fund', accountNumberMasked:'Folio 12894019',
  };
  await repo4.upsertHolding(casH);
  const back = (await repo4.allHoldings()).find(h=>h.id==='h_mf')!;
  eq('amc stored', back.amc, 'PPFAS');
  eq('rta stored', back.rta, 'CAMS');
  eq('amfi stored', back.amfiCode, '122639');
  eq('scheme type stored', back.schemeType, 'EQUITY');
  eq('folio stored', back.accountNumberMasked, 'Folio 12894019');
  await drv4.close();

  console.log('\n--- N. CAS import must create the demat/folio account BEFORE the holding ---');
  const drv5 = new NodeSqlDriver();
  const repo5 = new Repository(drv5);
  await repo5.initialise();
  await repo5.upsertEntity(fam);
  let danglingRejected = false;
  try {
    await repo5.upsertHolding({ ...hold('h_fk', 'HDFC Flexi Cap', 100), accountId: 'acct_FOLIO12894019' });
  } catch (e) {
    danglingRejected = String((e as Error).message).toLowerCase().includes('foreign key');
  }
  eq('holding with missing account is rejected', danglingRejected, true);

  await repo5.upsertAccount({
    id: 'acct_FOLIO12894019',
    entityId: 'f1',
    accountType: 'MF_FOLIO',
    custodian: 'CAMS',
    identifierMasked: '12894019',
  });
  await repo5.upsertHolding({
    ...hold('h_ok', 'HDFC Flexi Cap', 84.15),
    accountId: 'acct_FOLIO12894019',
    accountNumberMasked: '12894019',
  });
  const savedOk = (await repo5.allHoldings()).find(h => h.id === 'h_ok');
  eq('holding saved once account exists', Boolean(savedOk), true);
  eq('account linked on holding', savedOk?.accountId, 'acct_FOLIO12894019');
  const accts = await repo5.allAccounts();
  eq('folio appears in accounts', accts.some(a => a.id === 'acct_FOLIO12894019'), true);
  await repo5.appendEvent({
    ...ev('e_ob', 'OPENING_BALANCE', '2026-03-31', 1234.56, 84.15, 'h_ok'),
    holdingName: 'HDFC Flexi Cap',
  });
  const derived = derivePositions(await repo5.allEvents(), await repo5.allHoldings());
  const pos = derived.positions.get('h_ok')!;
  eq('ledger qty after confirm', Number(pos.quantityScaled) / 1e8, 1234.56);
  await drv5.close();

  console.log(`\n${pass} passed, ${fail} failed`);
  await drv.close(); await drv2.close();
  process.exit(fail?1:0);
})();
