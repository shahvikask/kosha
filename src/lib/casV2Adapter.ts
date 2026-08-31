import { AssetCategory, StatementImportRecord } from '../types';
import { CanonicalCASDocument, CASTransaction, CASHolding } from './casV2Model';

function asNumber(value: string | undefined): number {
  if (value === undefined) return 0;
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

function categoryFor(name: string, isin?: string): AssetCategory {
  const n = name.toUpperCase();
  if (/SGB|SOVEREIGN GOLD BOND|GOLD BOND/.test(n)) return 'GOLD_SGB';
  if (/AIF|ALTERNATIVE INVESTMENT|FUND II|PRIVATE EQUITY|VENTURE/.test(n)) return 'SPECIALISED';
  if (/NPS|NATIONAL PENSION/.test(n)) return 'FIXED_INCOME';
  if (isin?.toUpperCase().startsWith('INF')) return 'MUTUAL_FUND';
  return 'EQUITY';
}

function holdingToRecord(h: CASHolding, index: number): StatementImportRecord {
  const category = categoryFor(h.security.name, h.security.isin);
  return {
    id: `casv2_h_${index}_${h.security.isin || h.security.folio || 'holding'}`,
    date: h.asOfDate || new Date().toISOString().slice(0, 10),
    holdingName: h.security.name,
    category,
    action: 'BUY',
    quantity: asNumber(h.quantity),
    pricePerUnit: asNumber(h.marketPrice?.value),
    totalAmount: asNumber(h.marketValue?.value),
    status: 'NEW',
    statusMessage: 'CDSL V2 snapshot — source values extracted without estimation.',
    selected: true,
    isin: h.security.isin,
    folio: h.security.folio,
    recordType: 'HOLDING',
    currentValuation: asNumber(h.marketValue?.value),
    cumulativeInvested: asNumber(h.costValue?.value),
  };
}

function transactionToRecord(t: CASTransaction, index: number): StatementImportRecord | null {
  const category = categoryFor(t.security.name, t.security.isin);
  const isTransfer = /S ?T ?P|LATERAL SHIFT|SWITCH|TRANSFER/.test(t.rawDescription.toUpperCase());
  const amount = asNumber(t.amount?.value);
  const units = asNumber(t.units);
  const nav = asNumber(t.nav?.value);

  // Demat CAS does not print rupee trade consideration. Do not fabricate it.
  // Such records remain visible in parser output but are not automatically committed.
  if (amount === 0 && (t.source === 'CDSL' && !t.security.folio)) {
    return null;
  }

  return {
    id: `casv2_t_${index}_${t.date}_${t.rawSourceId || 'tx'}`,
    date: t.date,
    holdingName: t.security.name,
    category,
    action: t.action === 'BONUS' ? 'BUY' : (t.action as 'BUY' | 'SELL' | 'DIVIDEND' | 'INTEREST'),
    quantity: Math.abs(units),
    pricePerUnit: nav,
    totalAmount: amount,
    status: isTransfer ? 'CONFLICT_OR_NEEDS_REVIEW' : 'NEW',
    statusMessage: isTransfer
      ? `Source transaction is a transfer (${t.rawDescription}); it is not treated as a cash purchase/sale automatically.`
      : 'CDSL V2 transaction — amount/NAV read from statement.',
    selected: !isTransfer,
    isin: t.security.isin,
    folio: t.security.folio,
    recordType: 'TRANSACTION',
  };
}

export interface CDSLV2ParseBundle {
  document: CanonicalCASDocument;
  importRecords: StatementImportRecord[];
}

export function canonicalCdslToImportRecords(document: CanonicalCASDocument): StatementImportRecord[] {
  const holdings = document.holdings.map(holdingToRecord);
  const transactions = document.transactions
    .map(transactionToRecord)
    .filter((v): v is StatementImportRecord => Boolean(v));
  return [...holdings, ...transactions];
}

export function canonicalCdslToLegacyResult(document: CanonicalCASDocument): {
  statementType: 'CDSL_NSDL_DEMAT';
  investorPan?: string;
  periodFrom?: string;
  periodTo?: string;
  schemes: [];
  dematHoldings: Array<{
    type: AssetCategory;
    isin: string;
    name: string;
    quantity: number;
    marketPrice: number;
    currentValue: number;
    totalCost?: number;
    folioOrBoid?: string;
  }>;
  importRecords: StatementImportRecord[];
  summary: {
    totalFolios: number;
    totalTransactions: number;
    totalValuation: number;
    dematValuation?: number;
    mfValuation?: number;
    aifValuation?: number;
  };
  warnings: string[];
} {
  const importRecords = canonicalCdslToImportRecords(document);
  const dematHoldings = document.holdings.map(h => ({
    type: categoryFor(h.security.name, h.security.isin),
    isin: h.security.isin || '',
    name: h.security.name,
    quantity: asNumber(h.quantity),
    marketPrice: asNumber(h.marketPrice?.value),
    currentValue: asNumber(h.marketValue?.value),
    totalCost: h.costValue ? asNumber(h.costValue.value) : undefined,
    folioOrBoid: h.security.folio || h.security.boid,
  }));
  const dematValuation = document.summary?.dematValue ? asNumber(document.summary.dematValue.value) : undefined;
  const mfValuation = document.summary?.mutualFundValue ? asNumber(document.summary.mutualFundValue.value) : undefined;
  const aifValuation = document.summary?.aifValue ? asNumber(document.summary.aifValue.value) : undefined;
  const grand = document.summary?.grandTotal ? asNumber(document.summary.grandTotal.value) : dematValuation || 0;

  return {
    statementType: 'CDSL_NSDL_DEMAT',
    investorPan: document.investorPan,
    periodFrom: document.periodFrom,
    periodTo: document.periodTo,
    schemes: [],
    dematHoldings,
    importRecords,
    summary: {
      totalFolios: document.holdings.filter(h => h.security.folio).length,
      totalTransactions: document.transactions.length,
      totalValuation: grand,
      dematValuation,
      mfValuation,
      aifValuation,
    },
    warnings: document.warnings,
  };
}
