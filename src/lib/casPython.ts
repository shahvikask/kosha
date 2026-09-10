import { AssetCategory, StatementImportRecord } from '../types';
import { CASParseResult, ParsedDematHolding } from './casParser';
import { isDesktop } from '../db/desktopStore';

export type CasFileType = 'CDSL' | 'NSDL' | 'CAMS' | 'KFINTECH';

export const CAS_FILE_TYPE_LABEL: Record<CasFileType, string> = {
  CDSL: 'CDSL (Demat + Mutual Funds)',
  NSDL: 'NSDL (Demat + Mutual Funds)',
  CAMS: 'CAMS Mutual Funds',
  KFINTECH: 'KFintech Mutual Funds',
};

export interface CasAccountSummary {
  name: string;
  type?: string;
  dpId?: string;
  clientId?: string;
  folios?: number;
  balance: number;
}

export interface CasPythonHolding {
  kind: string;
  name: string;
  isin?: string | null;
  quantity: number;
  price: number;
  value: number;
  cost?: number | null;
  folio?: string | null;
  amc?: string | null;
  rta?: string | null;
  amfi?: string | null;
  advisor?: string | null;
  schemeType?: string | null;
  ucc?: string | null;
  symbol?: string | null;
  exchange?: string | null;
  dpName?: string | null;
  dpId?: string | null;
  clientId?: string | null;
  category: AssetCategory;
  accountType?: string | null;
}

export interface CasPythonResult {
  ok: boolean;
  engine: 'PYTHON_CASPARSER';
  fileType: CasFileType;
  casType?: string;
  filename?: string;
  investorName?: string | null;
  investorPan?: string | null;
  investorEmail?: string | null;
  periodFrom?: string | null;
  periodTo?: string | null;
  accounts: CasAccountSummary[];
  holdings: CasPythonHolding[];
  warnings: string[];
  skippedNilHoldings?: number;
  pdfTotals: { demat: number; mutualFund: number; aif: number; grand: number };
  parsedTotals: { holdingsCount: number; holdingsValue: number };
  delta: number;
  complete: boolean;
  error?: string;
  code?: string;
}

export interface CasImportMeta {
  fileType: CasFileType;
  filename: string;
  periodFrom?: string;
  periodTo?: string;
  investorName?: string;
  investorPan?: string;
  pdfGrandTotal: number;
  parsedGrandTotal: number;
  complete: boolean;
  holdingIds?: string[];
  closeHoldingIds?: string[];
  importedAt?: string;
}

export interface CasLatestState {
  asOf: string | null;
  byOwner: Partial<Record<string, Partial<Record<CasFileType, CasImportMeta>>>>;
}

export const EMPTY_CAS_LATEST: CasLatestState = { asOf: null, byOwner: {} };

export function parseCasLatest(raw: string | null | undefined): CasLatestState {
  if (!raw) return { ...EMPTY_CAS_LATEST, byOwner: {} };
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && parsed.byOwner) {
      return {
        asOf: parsed.asOf || null,
        byOwner: parsed.byOwner || {},
      };
    }
    return { ...EMPTY_CAS_LATEST, byOwner: {} };
  } catch {
    return { ...EMPTY_CAS_LATEST, byOwner: {} };
  }
}

export function upsertCasLatest(
  prev: CasLatestState,
  ownerId: string,
  meta: CasImportMeta
): CasLatestState {
  const byOwner: CasLatestState['byOwner'] = { ...prev.byOwner };
  const forOwner = { ...(byOwner[ownerId] || {}) };
  forOwner[meta.fileType] = {
    ...meta,
    closeHoldingIds: undefined,
    importedAt: meta.importedAt || new Date().toISOString(),
  };
  byOwner[ownerId] = forOwner;
  let asOf: string | null = null;
  for (const owner of Object.values(byOwner)) {
    for (const m of Object.values(owner || {})) {
      if (m?.periodTo && (!asOf || m.periodTo > asOf)) asOf = m.periodTo;
    }
  }
  return { asOf, byOwner };
}

const PYTHON_DOWN_DEV =
  'The Python CAS reader is not running. From the project folder run npm run dev — it starts the reader automatically. Or run: python3 scripts/cas_parse_server.py';
const PYTHON_DOWN_DESKTOP =
  'The statement reader is not running. Close GrowUrDhan and open it again. If it continues, see %LOCALAPPDATA%\\GrowUrDhan\\engine.log or reinstall.';

function pythonDownMessage(): string {
  return isDesktop() ? PYTHON_DOWN_DESKTOP : PYTHON_DOWN_DEV;
}

async function desktopInvoke<T>(cmd: string, args?: Record<string, unknown>): Promise<T> {
  const { invoke } = await import('@tauri-apps/api/core');
  return invoke<T>(cmd, args);
}

async function fileToBase64(file: File): Promise<string> {
  const buf = await file.arrayBuffer();
  const bytes = new Uint8Array(buf);
  const chunk = 0x8000;
  let binary = '';
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

export async function pingCasParser(): Promise<boolean> {
  try {
    if (isDesktop()) {
      return await desktopInvoke<boolean>('cas_health');
    }
    const res = await fetch('/api/cas/health', { method: 'GET' });
    if (!res.ok) return false;
    const body = await res.json();
    return body?.ok === true;
  } catch {
    return false;
  }
}

export async function parseCasPdfWithPython(
  file: File,
  password?: string
): Promise<CASParseResult> {
  if (isDesktop()) {
    try {
      await desktopInvoke('engine_ensure');
    } catch (e) {
      throw new Error(e instanceof Error ? e.message : pythonDownMessage());
    }
    const pdfBase64 = await fileToBase64(file);
    let data: CasPythonResult;
    try {
      data = await desktopInvoke<CasPythonResult>('cas_parse', {
        filename: file.name,
        password: (password || '').trim(),
        pdfBase64,
      });
    } catch (e) {
      throw new Error(e instanceof Error ? e.message : pythonDownMessage());
    }
    if (!data?.ok) {
      if (data?.code === 'BAD_PASSWORD' || /password/i.test(data?.error || '')) {
        const err = new Error('Incorrect Password');
        (err as Error & { name: string }).name = 'PasswordException';
        throw err;
      }
      throw new Error(data?.error || 'The statement reader could not parse this file.');
    }
    return pythonResultToCasParseResult(data, file.name);
  }

  const up = await pingCasParser();
  if (!up) {
    throw new Error(pythonDownMessage());
  }

  const pdfBase64 = await fileToBase64(file);
  let res: Response;
  try {
    res = await fetch('/api/cas/parse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        filename: file.name,
        password: (password || '').trim(),
        pdfBase64,
      }),
    });
  } catch {
    throw new Error(pythonDownMessage());
  }

  const data = (await res.json()) as CasPythonResult;
  if (!data?.ok) {
    if (data?.code === 'BAD_PASSWORD' || /password/i.test(data?.error || '')) {
      const err = new Error('Incorrect Password');
      (err as Error & { name: string }).name = 'PasswordException';
      throw err;
    }
    throw new Error(data?.error || 'The Python CAS reader could not parse this file.');
  }

  return pythonResultToCasParseResult(data, file.name);
}

export function pythonResultToCasParseResult(
  data: CasPythonResult,
  filename?: string
): CASParseResult {
  const periodTo = data.periodTo || new Date().toISOString().split('T')[0];
  const importRecords: StatementImportRecord[] = [];
  const dematHoldings: ParsedDematHolding[] = [];
  let i = 1;

  for (const h of data.holdings || []) {
    let category = (h.category || 'MUTUAL_FUND') as AssetCategory;
    const nm = (h.name || '').toUpperCase();
    if (/\bSGB\b/.test(nm) || nm.includes('SOVEREIGN GOLD')) category = 'GOLD_SGB';
    importRecords.push({
      id: `py_${data.fileType}_${i++}`,
      date: periodTo,
      holdingName: h.name,
      category,
      action: 'BUY',
      quantity: h.quantity,
      pricePerUnit: h.price,
      totalAmount: h.value,
      status: 'NEW',
      statusMessage: [
        h.isin ? `ISIN ${h.isin}` : null,
        h.folio ? `Folio ${h.folio}` : null,
        h.schemeType ? h.schemeType : null,
      ].filter(Boolean).join(' • ') || 'CAS holding',
      selected: true,
      isin: h.isin || undefined,
      folio: h.folio || undefined,
      recordType: 'HOLDING',
      currentValuation: h.value,
      cumulativeInvested: h.cost || undefined,
      dematAccount: h.clientId && h.dpId ? `${h.dpId}${h.clientId}` : undefined,
      amc: h.amc || undefined,
      rta: h.rta || undefined,
      amfiCode: h.amfi || undefined,
      advisor: h.advisor || undefined,
      schemeType: h.schemeType || (category === 'SPECIALISED' ? 'AIF' : undefined),
      ucc: h.ucc || undefined,
      exchange: h.exchange || undefined,
      dpName: h.dpName || undefined,
      dpId: h.dpId || undefined,
      clientId: h.clientId || undefined,
      casFileType: data.fileType,
    });

    if (category === 'EQUITY' || category === 'SPECIALISED' || category === 'GOLD_SGB') {
      dematHoldings.push({
        type: category === 'EQUITY' ? 'EQUITY' : category === 'GOLD_SGB' ? 'GOLD_SGB' : 'SPECIALISED',
        isin: h.isin || '',
        name: h.name,
        symbol: h.symbol || undefined,
        quantity: h.quantity,
        marketPrice: h.price,
        currentValue: h.value,
        totalCost: h.cost || undefined,
        folioOrBoid: h.folio || undefined,
        dpName: h.dpName || undefined,
      });
    }
  }

  const statementType =
    data.fileType === 'CAMS' || data.fileType === 'KFINTECH'
      ? 'CAMS_KFIN_MF'
      : 'CDSL_NSDL_DEMAT';

  const warnings = [...(data.warnings || [])];
  if (data.skippedNilHoldings) {
    warnings.push(
      `${data.skippedNilHoldings} closed / nil holding(s) were skipped (quantity and value both zero).`
    );
  }

  return {
    statementType,
    engine: 'PYTHON_CASPARSER',
    fileType: data.fileType,
    filename: data.filename || filename,
    investorName: data.investorName || undefined,
    investorPan: data.investorPan || undefined,
    investorEmail: data.investorEmail || undefined,
    periodFrom: data.periodFrom || undefined,
    periodTo: periodTo,
    schemes: [],
    dematHoldings,
    importRecords,
    accounts: data.accounts || [],
    pdfTotals: data.pdfTotals,
    parsedTotals: data.parsedTotals,
    totalsDelta: data.delta,
    parseComplete: data.complete,
    summary: {
      totalFolios: data.accounts?.length || data.parsedTotals.holdingsCount,
      totalTransactions: importRecords.length,
      totalValuation: data.parsedTotals.holdingsValue,
      dematValuation: data.pdfTotals.demat,
      mfValuation: data.pdfTotals.mutualFund,
      aifValuation: data.pdfTotals.aif,
    },
    warnings,
  };
}

/** Format YYYY-MM-DD as a calendar date without UTC shift. */
export function formatStatementDate(iso?: string | null): string {
  if (!iso) return '';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
}

export function formatStatementDateShort(iso?: string | null): string {
  if (!iso) return '';
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return iso;
  const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Latest periodTo across every statement type for one family member. */
export function latestAsOfForOwner(state: CasLatestState, ownerId: string): string | null {
  const owner = state.byOwner[ownerId];
  if (!owner) return null;
  let asOf: string | null = null;
  for (const m of Object.values(owner)) {
    if (m?.periodTo && (!asOf || m.periodTo > asOf)) asOf = m.periodTo;
  }
  return asOf;
}

export interface MemberStatementAsOf {
  id: string;
  name: string;
  relation?: string;
  asOf: string;
}

export function statementAsOfByMember(
  state: CasLatestState,
  members: { id: string; name: string; relation?: string }[]
): MemberStatementAsOf[] {
  const out: MemberStatementAsOf[] = [];
  for (const m of members) {
    const asOf = latestAsOfForOwner(state, m.id);
    if (asOf) out.push({ id: m.id, name: m.name, relation: m.relation, asOf });
  }
  return out;
}

/** Indian grouping for a rupee amount that still has paise. */
export function formatInrAmount(n: number): string {
  if (!Number.isFinite(n)) return '₹ 0.00';
  const neg = n < 0;
  const abs = Math.abs(n);
  const [whole, frac] = abs.toFixed(2).split('.');
  const grouped = Number(whole).toLocaleString('en-IN');
  return `${neg ? '-' : ''}₹ ${grouped}.${frac}`;
}
