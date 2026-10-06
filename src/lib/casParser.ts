/**
 * Statement entry. PDFs go to the local Python casparser sidecar.
 * CSV tradebooks stay in the browser. The old pdf.js CAS engine is gone.
 */
import { AssetCategory, StatementImportRecord } from '../types';

export interface ParsedCASScheme {
  amc?: string;
  folio: string;
  scheme: string;
  isin?: string;
  amfi?: string;
  rta?: string;
  advisor?: string;
  nominees?: string[];
  openUnits?: number;
  closeUnits: number;
  nav?: number;
  navDate?: string;
  totalValue?: number;
  costValue?: number;
  transactions: ParsedCASTransaction[];
}

export interface ParsedCASTransaction {
  date: string;
  rawDate: string;
  description: string;
  type: 'BUY' | 'SELL' | 'DIVIDEND' | 'INTEREST';
  amount: number;
  units: number;
  nav: number;
  balance?: number;
  stampDuty?: number;
}

export interface ParsedDematHolding {
  type: 'EQUITY' | 'MUTUAL_FUND' | 'GOLD_SGB' | 'FIXED_INCOME' | 'SPECIALISED';
  isin: string;
  name: string;
  symbol?: string;
  quantity: number;
  marketPrice: number;
  currentValue: number;
  totalCost?: number;
  avgCost?: number;
  folioOrBoid?: string;
  dpName?: string;
}

export interface CASParseResult {
  statementType: 'CAMS_KFIN_MF' | 'CDSL_NSDL_DEMAT' | 'GENERIC_CSV_OR_PDF';
  engine?: 'PYTHON_CASPARSER';
  fileType?: 'CDSL' | 'NSDL' | 'CAMS' | 'KFINTECH' | 'NPS';
  filename?: string;
  investorName?: string;
  investorPan?: string;
  investorPran?: string;
  investorEmail?: string;
  periodFrom?: string;
  periodTo?: string;
  schemes: ParsedCASScheme[];
  dematHoldings: ParsedDematHolding[];
  importRecords: StatementImportRecord[];
  accounts?: { name: string; type?: string; dpId?: string; clientId?: string; folios?: number; balance: number }[];
  pdfTotals?: { demat: number; mutualFund: number; aif: number; grand: number };
  parsedTotals?: { holdingsCount: number; holdingsValue: number };
  totalsDelta?: number;
  parseComplete?: boolean;
  summary: {
    totalFolios: number;
    totalTransactions: number;
    totalValuation: number;
    dematValuation?: number;
    mfValuation?: number;
    aifValuation?: number;
  };
  warnings: string[];
}

const MONTH_MAP: Record<string, string> = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

export function normalizeIndianDate(dateStr: string): string {
  if (!dateStr) return new Date().toISOString().split('T')[0];
  const clean = dateStr.trim();

  const dmyAlpha = clean.match(/^(\d{1,2})[-/\s]([A-Za-z]{3,9})[-/\s](\d{2,4})$/);
  if (dmyAlpha) {
    const day = dmyAlpha[1].padStart(2, '0');
    const monKey = dmyAlpha[2].substring(0, 3).toLowerCase();
    const month = MONTH_MAP[monKey] || '01';
    let year = dmyAlpha[3];
    if (year.length === 2) year = `20${year}`;
    return `${year}-${month}-${day}`;
  }

  const dmyNum = clean.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})$/);
  if (dmyNum) {
    const day = dmyNum[1].padStart(2, '0');
    const month = dmyNum[2].padStart(2, '0');
    let year = dmyNum[3];
    if (year.length === 2) year = `20${year}`;
    return `${year}-${month}-${day}`;
  }

  if (/^\d{4}-\d{2}-\d{2}$/.test(clean)) return clean;
  return clean;
}

export async function parseStatementFile(
  file: File,
  password?: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[] = [],
  _targetOwnerId = 'fam_1'
): Promise<CASParseResult> {
  const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';
  if (!isPdf) {
    const text = await file.text();
    return parseCsvStatementText(text, file.name, existingLedger);
  }
  const { parseCasPdfWithPython } = await import('./casPython');
  return parseCasPdfWithPython(file, password);
}

export function parseCsvStatementText(
  csvContent: string,
  _fileName: string,
  existingLedger: { holdingName: string; eventDate: string; totalAmountMinor: string }[]
): CASParseResult {
  const lines = csvContent.split('\n').map(l => l.trim()).filter(Boolean);
  const importRecords: StatementImportRecord[] = [];
  let recordCounter = 1;

  if (lines.length > 1) {
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',').map(p => p.replace(/"/g, '').trim());
      if (parts.length >= 4) {
        const date = normalizeIndianDate(parts[0]);
        const holdingName = parts[1] || 'Imported share';
        const actionStr = (parts[2] || 'BUY').toUpperCase();
        const action = actionStr.includes('SELL') ? 'SELL' : actionStr.includes('DIV') ? 'DIVIDEND' : 'BUY';
        const quantity = Math.abs(parseFloat(parts[3]) || 1);
        const price = Math.abs(parseFloat(parts[4]) || 100);
        const total = quantity * price;
        const isDuplicate = existingLedger.some(
          ev => ev.holdingName.toLowerCase() === holdingName.toLowerCase() && ev.eventDate === date
        );

        let category: AssetCategory = 'EQUITY';
        if (holdingName.toLowerCase().includes('fund') || holdingName.toLowerCase().includes('etf')) {
          category = 'MUTUAL_FUND';
        } else if (holdingName.toLowerCase().includes('gold') || holdingName.toLowerCase().includes('sgb')) {
          category = 'GOLD_SGB';
        }

        importRecords.push({
          id: `imp_csv_${recordCounter++}`,
          date,
          holdingName,
          category,
          action,
          quantity,
          pricePerUnit: price,
          totalAmount: total,
          status: isDuplicate ? 'ALREADY_EXISTS' : 'NEW',
          statusMessage: isDuplicate ? 'Already in your records' : 'New row from file',
          selected: !isDuplicate,
          recordType: 'TRANSACTION',
        });
      }
    }
  }

  const totalValuation = importRecords.reduce((sum, r) => sum + r.totalAmount, 0);
  return {
    statementType: 'GENERIC_CSV_OR_PDF',
    schemes: [],
    dematHoldings: [],
    importRecords,
    summary: {
      totalFolios: importRecords.length,
      totalTransactions: importRecords.length,
      totalValuation,
    },
    warnings: [],
  };
}
