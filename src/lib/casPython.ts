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

/** Protean / CRA NPS PDF password: first 4 letters of first name + DDMM of DOB.
 *  Example: "Vikas Shah", "1986-03-22" → "vika2203". */
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

/** Passwords to try for a statement PDF: typed value, then NPS convention, then PAN. */
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

const PYTHON_DOWN =
  'The Python CAS reader is not running. From the project folder run npm run dev — it starts the reader automatically. Or run: python3 scripts/cas_parse_server.py';

const PYTHON_DOWN_PHONE =
  'Python casparser could not start on this phone. Reinstall the latest GrowUrDhan APK.';

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
  throw new Error(data?.error || 'The Python CAS reader could not parse this file.');
}

export async function pingCasParser(): Promise<boolean> {
  if (isNativeShell()) {
    try {
      const body = await nativeCommand<{ ok?: boolean }>('cas_health', {});
      return body?.ok === true;
    } catch {
      return false;
    }
  }
  try {
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
      throw new Error(e instanceof Error ? e.message : PYTHON_DOWN_PHONE);
    }
    const data = (raw?.payload ? JSON.parse(raw.payload) : raw) as CasPythonResult;
    if (!data?.ok) raiseFromPythonResult(data);
    return pythonResultToCasParseResult(data, file.name);
  }

  const up = await pingCasParser();
  if (!up) {
    throw new Error(PYTHON_DOWN);
  }

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
