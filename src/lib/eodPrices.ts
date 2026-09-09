import { AssetCategory, Holding, ListedSecurity } from '../types';
import { MoneyEngine } from './money';
import { pingCasParser } from './casPython';

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
  const out: ListedSecurity[] = [];
  for (const row of catalog) {
    if (category && row.category !== category) continue;
    const blob = `${row.name} ${row.symbol || ''} ${row.nseSymbol || ''} ${row.bseSymbol || ''} ${row.isin} ${row.amfiCode || ''}`.toLowerCase();
    if (!blob.includes(q)) continue;
    out.push(row);
    if (out.length >= limit) break;
  }
  return out;
}

export async function refreshEodFromSidecar(): Promise<EodRefreshResult> {
  const up = await pingCasParser();
  if (!up) {
    return {
      ok: false,
      error: 'The local price reader is not running. Start the app with npm run dev.',
    };
  }
  const res = await fetch('/api/cas/prices/refresh', { method: 'POST' });
  const body = (await res.json()) as EodRefreshResult;
  if (!res.ok || !body?.ok) {
    return { ok: false, error: body?.error || 'Could not read end-of-day files.' };
  }
  return body;
}
