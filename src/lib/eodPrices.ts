import { AssetCategory, Holding, ListedSecurity } from '../types';
import { MoneyEngine } from './money';
import { pingCasParser } from './casPython';
import { isNativeShell, nativeCommand } from './license';

export interface EodCatalogRow {
  isin: string;
  name: string;
  symbol?: string;
  nse?: string;
  bse?: string;
  amfi?: string;
  cat: string;
  px: number;
  dt: string;
  src: string;
}

export interface EodRefreshResult {
  ok: boolean;
  asOf?: string | null;
  nseAsOf?: string | null;
  bseAsOf?: string | null;
  amfiAsOf?: string | null;
  sources?: { name: string; asOf: string; count: number }[];
  warnings?: string[];
  catalog?: EodCatalogRow[];
  catalogCount?: number;
  error?: string;
}

const EOD_SOURCE = /^(NSE|BSE|AMFI)\b/i;

let heldIsins: () => string[] = () => [];

export function bindHeldIsins(fn: () => string[]): void {
  heldIsins = fn;
}

export function isEodPriceSource(source?: string): boolean {
  return !!source && EOD_SOURCE.test(source);
}

export function catalogToListed(row: EodCatalogRow): ListedSecurity | null {
  const isin = (row.isin || '').trim().toUpperCase();
  if (isin.length < 12 || !(row.px > 0)) return null;
  const cat = (row.cat || 'EQUITY') as AssetCategory;
  return {
    isin,
    name: row.name || row.symbol || isin,
    symbol: row.symbol || row.nse || row.bse || undefined,
    nseSymbol: row.nse || undefined,
    bseSymbol: row.bse || undefined,
    amfiCode: row.amfi || undefined,
    category: cat === 'MUTUAL_FUND' || cat === 'GOLD_SGB' ? cat : 'EQUITY',
    closeMinor: MoneyEngine.fromRupees(row.px).toString(),
    priceDate: row.dt,
    source: row.src || 'EOD close',
  };
}

export function applyCatalogPrices(
  holdings: Holding[],
  catalog: ListedSecurity[],
): { holdings: Holding[]; updated: number } {
  const byIsin = new Map(catalog.map(s => [s.isin.toUpperCase(), s]));
  let updated = 0;
  const next = holdings.map(h => {
    const isin = (h.isin || '').toUpperCase();
    if (!isin) return h;
    if (h.category === 'LIABILITY' || h.category === 'REAL_ESTATE' || h.category === 'CASH_BANK') {
      return h;
    }
    const row = byIsin.get(isin);
    if (!row) return h;
    if (h.currentPriceMinor === row.closeMinor && isEodPriceSource(h.priceSource)) {
      return {
        ...h,
        lastPriceUpdated: `${row.priceDate}T16:00:00+05:30`,
        priceSource: row.source,
        isPriceStale: false,
      };
    }
    updated += 1;
    return {
      ...h,
      currentPriceMinor: row.closeMinor,
      lastPriceUpdated: `${row.priceDate}T16:00:00+05:30`,
      priceSource: row.source,
      isPriceStale: false,
    };
  });
  return { holdings: next, updated };
}

export function searchListed(
  catalog: ListedSecurity[],
  query: string,
  category?: AssetCategory,
  limit = 20,
): ListedSecurity[] {
  const q = query.trim().toLowerCase();
  if (q.length < 2) return [];
  const scored: { row: ListedSecurity; s: number }[] = [];
  for (const row of catalog) {
    if (category && row.category !== category) continue;
    const name = (row.name || '').toLowerCase();
    const isin = (row.isin || '').toLowerCase();
    const amfi = (row.amfiCode || '').toLowerCase();
    const sym = `${row.symbol || ''} ${row.nseSymbol || ''} ${row.bseSymbol || ''}`.toLowerCase();
    if (!name.includes(q) && !isin.includes(q) && !amfi.includes(q) && !sym.includes(q)) continue;
    let s = 0;
    if (isin === q) s += 1000;
    else if (isin.startsWith(q)) s += 600;
    if (amfi === q) s += 500;
    if (name.startsWith(q)) s += 400;
    else if (name.includes(q)) s += 200;
    if (/\bgrowth\b/.test(name)) s += 40;
    if (/\bdirect\b/.test(name)) s += 25;
    if (/\bidcw\b|\bdividend\b|\breinvestment\b/.test(name)) s -= 50;
    scored.push({ row, s });
  }
  scored.sort((a, b) => b.s - a.s || a.row.name.localeCompare(b.row.name));
  return scored.slice(0, limit).map(x => x.row);
}

export async function refreshEodFromSidecar(): Promise<EodRefreshResult> {
  if (isNativeShell()) {
    try {
      const isins = heldIsins()
        .map(s => (s || '').trim().toUpperCase())
        .filter(s => s.length >= 12);
      const remote = await nativeCommand<EodRefreshResult>('prices_refresh', {
        isins: isins.join(','),
      });
      if (remote && typeof remote === 'object') {
        return remote;
      }
    } catch (e) {
      return {
        ok: false,
        error: e instanceof Error ? e.message : 'Could not refresh prices on this phone.',
      };
    }
  }
  const up = await pingCasParser();
  if (!up) {
    return {
      ok: false,
      error: isNativeShell()
        ? 'Could not download last close / NAV on this phone.'
        : 'The local price reader is not running. Start the app with npm run dev.',
    };
  }
  const res = await fetch('/api/cas/prices/refresh', { method: 'POST' });
  const body = (await res.json()) as EodRefreshResult;
  if (!res.ok || !body?.ok) {
    return { ok: false, error: body?.error || 'Could not read end-of-day files.' };
  }
  return body;
}
