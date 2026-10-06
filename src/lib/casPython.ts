import { AssetCategory, StatementImportRecord } from '../types';
import { CASParseResult, ParsedDematHolding } from './casParser';
import { recategorizeCasAsset } from './holdingForm';
import { isNativeShell, nativeCommand } from './license';

export type CasFileType = 'CDSL' | 'NSDL' | 'CAMS' | 'KFINTECH' | 'NPS';

export const CAS_FILE_TYPE_LABEL: Record<CasFileType, string> = {
  CDSL: 'CDSL (Demat + Mutual Funds)',
  NSDL: 'NSDL (Demat + Mutual Funds)',
  CAMS: 'CAMS Mutual Funds',
  KFINTECH: 'KFintech Mutual Funds',
  NPS: 'NPS (Protean / CRA statement)',
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
  investorPran?: string | null;
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

export function npsStatementPassword(
  name?: string | null,
  dateOfBirth?: string | null
): string | null {
  if (!name || !dateOfBirth) return null;
  const first = name.trim().split(/\s+/)[0] || '';
  const letters = first.replace(/[^A-Za-z]/g, '').slice(0, 4).toLowerCase();
  if (!letters) return null;
  const iso = /^(\d{4})-(\d{2})-(\d{2})/.exec(dateOfBirth.trim());
  let ddmm: string | null = null;
  if (iso) ddmm = `${iso[3]}${iso[2]}`;
  else {
    const dmy = /^(\d{2})[/-](\d{2})[/-](\d{4})/.exec(dateOfBirth.trim());
    if (dmy) ddmm = `${dmy[1]}${dmy[2]}`;
  }
  if (!ddmm) return null;
  return `${letters}${ddmm}`;
}

export function statementPasswordsToTry(opts: {
  typed?: string | null;
  pan?: string | null;
  name?: string | null;
  dateOfBirth?: string | null;
}): string[] {
  const out: string[] = [];
  const add = (s?: string | null) => {
    const t = (s || '').trim();
    if (t && !out.includes(t)) out.push(t);
  };
  add(opts.typed);
  add(npsStatementPassword(opts.name, opts.dateOfBirth));
  add(opts.pan);
  return out;
}

export function parseCasLatest(raw: string | null | undefined): CasLatestState {
  if (!raw) return { ...EMPTY_CAS_LATEST, byOwner: {} };
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === 'object' && parsed.byOwner) {
      return { asOf: parsed.asOf || null, byOwner: parsed.byOwner || {} };
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

const PYTHON_DOWN = 'Engine is not running.';
const PYTHON_DOWN_PHONE = 'Engine is not running.';

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

function raiseFromPythonResult(data: CasPythonResult): never {
  if (data?.code === 'BAD_PASSWORD' || /password/i.test(data?.error || '')) {
    const err = new Error('Incorrect Password');
    (err as Error & { name: string }).name = 'PasswordException';
    throw err;
  }
  throw new Error(data?.error || 'Engine is not running.');
}

export const DIAGNOSTICS_PATH = 'Download/GrowUrDhan-diagnostics.txt';

export interface CasHealth {
  ok: boolean;
  error?: string;
  detail?: string;
  engine?: string;
  logPath?: string;
  logPrivate?: string;
  logError?: string;
}

export async function readCasHealth(): Promise<CasHealth> {
  if (!isNativeShell()) {
    try {
      const res = await fetch('/api/cas/health', { method: 'GET' });
      if (!res.ok) return { ok: false, error: `health HTTP ${res.status}` };
      const body = await res.json();
      return { ok: body?.ok === true, error: body?.error, engine: body?.engine };
    } catch (e) {
      return { ok: false, error: e instanceof Error ? e.message : 'Engine is not running.' };
    }
  }
  try {
    const body = await nativeCommand<CasHealth>('cas_health', {});
    const detail = typeof body?.detail === 'string' ? body.detail.slice(0, 800) : undefined;
    return {
      ok: body?.ok === true,
      error: body?.error,
      detail,
      engine: body?.engine,
      logPath: body?.logPath || DIAGNOSTICS_PATH,
      logPrivate: body?.logPrivate,
      logError: body?.logError,
    };
  } catch (e) {
    return {
      ok: false,
      error: e instanceof Error ? e.message : 'Engine is not running.',
      logPath: DIAGNOSTICS_PATH,
    };
  }
}

export async function pingCasParser(): Promise<boolean> {
  const health = await readCasHealth();
  return health.ok;
}

export async function parseCasPdfWithPython(
  file: File,
  password?: string
): Promise<CASParseResult> {
  const pdfBase64 = await fileToBase64(file);
  if (isNativeShell()) {
    let raw: { payload?: string } & Partial<CasPythonResult>;
    try {
      raw = await nativeCommand<{ payload?: string } & Partial<CasPythonResult>>('cas_parse', {
        pdfBase64,
        password: (password || '').trim(),
        filename: file.name,
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : '';
      throw new Error(msg || PYTHON_DOWN_PHONE);
    }
    const data = (raw?.payload ? JSON.parse(raw.payload) : raw) as CasPythonResult;
    if (!data?.ok) raiseFromPythonResult(data);
    return pythonResultToCasParseResult(data, file.name);
  }
  const up = await pingCasParser();
  if (!up) throw new Error(PYTHON_DOWN);
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
    throw new Error(PYTHON_DOWN);
  }
  const data = (await res.json()) as CasPythonResult;
  if (!data?.ok) raiseFromPythonResult(data);
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
    const category = recategorizeCasAsset(
      h.name || '',
      (h.category || 'MUTUAL_FUND') as AssetCategory,
      h.schemeType
    );
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
        h.kind === 'nps' ? 'NPS' : null,
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
      schemeType: h.schemeType
        || (category === 'SPECIALISED' ? 'AIF' : category === 'REAL_ESTATE' ? 'REIT' : undefined),
      ucc: h.ucc || undefined,
      exchange: h.exchange || undefined,
      dpName: h.dpName || undefined,
      dpId: h.dpId || undefined,
      clientId: h.clientId || undefined,
      casFileType: data.fileType,
    });
    if (category === 'EQUITY' || category === 'SPECIALISED' || category === 'GOLD_SGB' || category === 'REAL_ESTATE') {
      dematHoldings.push({
        type: category === 'EQUITY' ? 'EQUITY' : category === 'GOLD_SGB' ? 'GOLD_SGB' : category === 'REAL_ESTATE' ? 'EQUITY' : 'SPECIALISED',
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
    investorPran: data.investorPran || undefined,
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

export function formatInrAmount(n: number): string {
  if (!Number.isFinite(n)) return '₹ 0.00';
  const neg = n < 0;
  const abs = Math.abs(n);
  const [whole, frac] = abs.toFixed(2).split('.');
  const grouped = Number(whole).toLocaleString('en-IN');
  return `${neg ? '-' : ''}₹ ${grouped}.${frac}`;
}
