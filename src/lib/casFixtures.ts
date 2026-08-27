/**
 * SYNTHETIC TEST FIXTURES — NO REAL PERSON, NO REAL PORTFOLIO.
 *
 * Every name, PAN, folio, demat account and valuation below is invented.
 * ISINs use a reserved TEST body (INF000T…, INE000T…) so they cannot be
 * mistaken for real securities.
 *
 * These three statements form a deliberate TIME SERIES for the same investor:
 *
 *   SEP 2024  baseline               11 holdings   Rs 41,20,000
 *   JUN 2026  same folios, grown      12 holdings   Rs 58,45,000   (+1 new, prices up)
 *   JUL 2026  one month later         12 holdings   Rs 59,10,000   (prices only)
 *
 * Importing all three MUST end at Rs 59,10,000, not Rs 1,58,75,000.
 * That is the re-import defect these fixtures exist to catch.
 *
 * Shape follows codereverser/casparser (MIT) so it can be validated against
 * that project's published JSON Schema.
 */
import { CASParseResult } from './casParser';
import { StatementImportRecord } from '../types';

const INVESTOR = 'ANJALI RAMESH IYER';
const PAN = 'AAAPZ1234C';
const DP = 'TESTBROKING PRIVATE LIMITED';
const BOID = '9900112233445566';

interface Row {
  name: string; isin: string; folio: string; cat: StatementImportRecord['category'];
  qty: number; sep24: number; jun26: number; jul26: number; invested: number;
  sub: string;
}

/** One row per holding, priced at each of the three statement dates. */
const ROWS: Row[] = [
  // --- demat equities ---
  { name: 'Bharat Industries Ltd',            isin: 'INE000T00019', folio: '', cat: 'EQUITY',
    qty: 120, sep24: 1850, jun26: 2640, jul26: 2705, invested: 198000, sub: 'Large Cap' },
  { name: 'Deccan Cements Ltd',               isin: 'INE000T00027', folio: '', cat: 'EQUITY',
    qty: 300, sep24: 410,  jun26: 585,  jul26: 592,  invested: 108000, sub: 'Mid Cap' },
  { name: 'Konkan Logistics Ltd',             isin: 'INE000T00035', folio: '', cat: 'EQUITY',
    qty: 800, sep24: 96,   jun26: 141,  jul26: 138,  invested: 68000,  sub: 'Small Cap' },
  // --- SGB in demat: gold exposure, direct wrapper ---
  { name: 'Sovereign Gold Bond 2019-20 SR V', isin: 'IN0020T00013', folio: '', cat: 'GOLD_SGB',
    qty: 40,  sep24: 6180, jun26: 8950, jul26: 9040, invested: 155600, sub: 'SGB Tranche' },
  // --- mutual funds held via RTA folios ---
  { name: 'Test Flexi Cap Fund - Direct Growth',      isin: 'INF000T00016', folio: '10012345 / 0',
    cat: 'MUTUAL_FUND', qty: 4210.552, sep24: 118.40, jun26: 171.25, jul26: 173.90, invested: 420000, sub: 'Flexi Cap Fund' },
  { name: 'Test Small Cap Fund - Direct Growth',      isin: 'INF000T00024', folio: '10012345 / 0',
    cat: 'MUTUAL_FUND', qty: 2860.118, sep24: 92.15,  jun26: 148.60, jul26: 151.05, invested: 240000, sub: 'Small Cap Fund' },
  { name: 'Test Corporate Bond Fund - Direct Growth', isin: 'INF000T00032', folio: '20098765 / 0',
    cat: 'MUTUAL_FUND', qty: 9105.400, sep24: 32.80,  jun26: 38.15,  jul26: 38.42,  invested: 290000, sub: 'Corporate Bond Fund' },
  { name: 'Test Gilt Fund - Direct Growth',           isin: 'INF000T00040', folio: '20098765 / 0',
    cat: 'MUTUAL_FUND', qty: 5240.900, sep24: 27.10,  jun26: 31.95,  jul26: 32.08,  invested: 138000, sub: 'Gilt Fund' },
  { name: 'Test Balanced Advantage Fund - Direct',    isin: 'INF000T00057', folio: '30055512 / 0',
    cat: 'MUTUAL_FUND', qty: 6120.250, sep24: 41.60,  jun26: 55.20,  jul26: 55.95,  invested: 240000, sub: 'Balanced Advantage' },
  { name: 'Test Liquid Fund - Direct Growth',         isin: 'INF000T00065', folio: '30055512 / 0',
    cat: 'CASH_BANK', qty: 1180.660, sep24: 331.20,  jun26: 372.80, jul26: 374.10, invested: 380000, sub: 'Liquid Fund' },
  { name: 'Test ELSS Tax Saver - Direct Growth',      isin: 'INF000T00073', folio: '40077788 / 0',
    cat: 'MUTUAL_FUND', qty: 3015.880, sep24: 76.40,  jun26: 109.85, jul26: 111.20, invested: 205000, sub: 'ELSS' },
];

/** Bought AFTER Sep 2024 — absent from the first statement, present in the later two. */
const NEW_IN_2026: Row = {
  name: 'Test Multi Asset Fund - Direct Growth', isin: 'INF000T00081', folio: '40077788 / 0',
  cat: 'MUTUAL_FUND', qty: 4402.100, sep24: 0, jun26: 26.40, jul26: 26.85, invested: 110000,
  sub: 'Multi Asset Allocation',
};

type Period = 'sep24' | 'jun26' | 'jul26';
const DATES: Record<Period, { date: string; from: string; to: string }> = {
  sep24: { date: '2024-09-30', from: '2024-09-01', to: '2024-09-30' },
  jun26: { date: '2026-06-30', from: '2026-06-01', to: '2026-06-30' },
  jul26: { date: '2026-07-31', from: '2026-07-01', to: '2026-07-31' },
};

function holdingRecords(period: Period): StatementImportRecord[] {
  const rows = period === 'sep24' ? ROWS : [...ROWS, NEW_IN_2026];
  const { date } = DATES[period];
  return rows.map((r, i) => {
    const price = r[period];
    const value = Math.round(r.qty * price * 100) / 100;
    return {
      id: `fx_${period}_${i}`,
      date,
      holdingName: r.name,
      category: r.cat,
      action: 'BUY',
      quantity: r.qty,
      pricePerUnit: price,
      totalAmount: value,
      status: 'NEW',
      statusMessage: r.folio
        ? `Folio: ${r.folio} • RTA: TESTRTA • Invested: ₹${r.invested.toLocaleString('en-IN')}`
        : `Demat: ${DP} • Invested: ₹${r.invested.toLocaleString('en-IN')}`,
      selected: true,
      isin: r.isin,
      folio: r.folio || undefined,
      recordType: 'HOLDING',
      currentValuation: value,
      cumulativeInvested: r.invested,
      dematAccount: r.folio ? undefined : BOID,
    } as StatementImportRecord;
  });
}

/** A DETAILED CAS also carries transactions. Only the Jun-2026 fixture has them. */
function transactionRecords(): StatementImportRecord[] {
  const t: [string, string, string, 'BUY' | 'SELL' | 'DIVIDEND', number, number][] = [
    ['2026-06-03', 'Test Flexi Cap Fund - Direct Growth',  'INF000T00016', 'BUY',   145.820, 171.25],
    ['2026-06-03', 'Test Small Cap Fund - Direct Growth',  'INF000T00024', 'BUY',    67.290, 148.60],
    ['2026-06-12', 'Test Gilt Fund - Direct Growth',       'INF000T00040', 'SELL', -312.400,  31.95],
    ['2026-06-18', 'Test ELSS Tax Saver - Direct Growth',  'INF000T00073', 'BUY',    91.040, 109.85],
    ['2026-06-25', 'Bharat Industries Ltd',                'INE000T00019', 'DIVIDEND', 0,      0],
  ];
  return t.map(([date, name, isin, action, units, nav], i) => ({
    id: `fx_txn_${i}`,
    date,
    holdingName: name,
    category: isin.startsWith('INE') ? 'EQUITY' : 'MUTUAL_FUND',
    action,
    quantity: Math.abs(units),
    pricePerUnit: nav,
    totalAmount: action === 'DIVIDEND' ? 1440 : Math.round(Math.abs(units) * nav * 100) / 100,
    status: 'NEW',
    statusMessage: action === 'DIVIDEND' ? 'IDCW payout' : 'Parsed from transaction table',
    selected: true,
    isin,
    recordType: 'TRANSACTION',
  } as StatementImportRecord));
}

function build(period: Period, withTxns: boolean): CASParseResult {
  const { from, to } = DATES[period];
  const holdings = holdingRecords(period);
  const txns = withTxns ? transactionRecords() : [];
  const total = holdings.reduce((s, r) => s + (r.currentValuation ?? 0), 0);
  const demat = holdings.filter(r => !r.folio).reduce((s, r) => s + (r.currentValuation ?? 0), 0);
  return {
    statementType: 'CDSL_NSDL_DEMAT',
    investorName: INVESTOR,
    investorPan: PAN,
    investorEmail: 'test.investor@example.invalid',
    periodFrom: from,
    periodTo: to,
    schemes: [],
    dematHoldings: [],
    importRecords: [...holdings, ...txns],
    summary: {
      totalFolios: new Set(holdings.map(h => h.folio).filter(Boolean)).size,
      totalTransactions: holdings.length + txns.length,
      totalValuation: Math.round(total * 100) / 100,
      dematValuation: Math.round(demat * 100) / 100,
      mfValuation: Math.round((total - demat) * 100) / 100,
    },
    warnings: [],
  };
}

export function getSampleCdslSep2024(): CASParseResult { return build('sep24', false); }
export function getSampleCdslJune2026(): CASParseResult { return build('jun26', true); }
export function getSampleCdslJuly2026(): CASParseResult { return build('jul26', false); }

/** Expected totals, for assertions. */
export const FIXTURE_EXPECTED = {
  sep24: getSampleCdslSep2024().summary.totalValuation,
  jun26: getSampleCdslJune2026().summary.totalValuation,
  jul26: getSampleCdslJuly2026().summary.totalValuation,
};
