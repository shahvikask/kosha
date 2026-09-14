/**
 * Match a CAS/statement row to an existing holding.
 *
 * CDSL prints many schemes under one AMC folio (e.g. 17001385/94).
 * Folio is an account, not a fund — matching on folio prefix merged
 * ICICI / Edelweiss / Nippon / WhiteOak schemes and then priced the
 * lump with the last scheme's NAV (Mar 2026 Home showed ₹5.44 cr vs
 * the PDF's ₹2.03 cr).
 *
 * Identity is ISIN, then AMFI, then folio+name. Never folio alone
 * for two real scheme names. A CDSL placeholder ("MUTUAL FUND UNITS",
 * scheme-code-only) may attach to the single unnamed row on that folio.
 */
import { Holding, StatementImportRecord } from '../types';

export function normalizeHoldingName(str: string): string {
  return (str || '')
    .toLowerCase()
    .replace(/\b(fund|direct|regular|growth|plan|ltd|limited|equity|shares|growth|idcw|dividend|class|series|option|institutional|flexi cap|large cap|small cap|mid cap)\b/gi, '')
    .replace(/[^a-z0-9]/gi, '')
    .trim();
}

export function folioPrefix(folio?: string | null): string {
  if (!folio || folio === 'MF_FOLIO' || folio === 'PORTFOLIO') return '';
  return folio.split('/')[0].replace(/[^A-Za-z0-9]/gi, '').toUpperCase();
}

/** CDSL prints AMC#scheme or "CODE - Fund name". Strip those for comparison. */
function schemeBody(name: string): string {
  const n = (name || '').trim();
  if (!n) return '';
  const hash = n.indexOf('#');
  const afterHash = hash >= 0 && hash < n.length - 3 ? n.slice(hash + 1).trim() : n;
  const m = afterHash.match(/^([A-Z0-9]{1,8})\s*-\s+(.+)$/i);
  return (m ? m[2] : afterHash).trim();
}

const GENERIC_NAME =
  /^(mutual fund( units| folios?)?|mf (units|folios?)|cas|equity shares?|units|portfolio|holdings?)$/i;

/**
 * True when CDSL (or a stub row) did not print a real scheme / scrip name.
 * Those must not become a second holding next to the named scheme.
 */
export function isPlaceholderName(name: string | null | undefined): boolean {
  const n = (name || '').trim();
  if (!n) return true;
  if (/^\d{5,}(\/\d+)?$/.test(n.replace(/\s/g, ''))) return true;
  if (/^[A-Z0-9]{1,8}$/i.test(n)) return true;
  const body = schemeBody(n);
  if (!body) return true;
  if (GENERIC_NAME.test(body)) return true;
  if (normalizeHoldingName(body).length < 4) return true;
  return false;
}

/** Keep a real scheme name; replace a CDSL placeholder when a better one arrives. */
export function preferSchemeName(current: string, incoming: string): string {
  const cur = (current || '').trim();
  const inc = (incoming || '').trim();
  if (!inc) return cur;
  if (!cur) return inc;
  const curPh = isPlaceholderName(cur);
  const incPh = isPlaceholderName(inc);
  if (curPh && !incPh) return inc;
  if (!curPh && incPh) return cur;
  const curBody = schemeBody(cur);
  const incBody = schemeBody(inc);
  if (incBody.length > curBody.length + 10 && !incPh) return inc;
  return cur;
}

function namesLikelySame(a: string, b: string): boolean {
  if (!a || !b) return false;
  if (a === b) return true;
  if (a.length >= 8 && b.length >= 8 && (a.includes(b) || b.includes(a))) return true;
  return false;
}

export function findMatchingHolding(
  existingHoldings: Holding[],
  rec: StatementImportRecord,
  ownerId: string
): Holding | undefined {
  const mine = existingHoldings.filter(h => h.ownerId === ownerId);
  const recIsin = rec.isin && rec.isin.length >= 10 ? rec.isin.toUpperCase() : '';

  if (recIsin) {
    const isinMatch = mine.find(h => h.isin && h.isin.toUpperCase() === recIsin);
    if (isinMatch) return isinMatch;
  }

  if (rec.amfiCode) {
    const amfiMatch = mine.find(h => h.amfiCode && h.amfiCode === rec.amfiCode);
    if (amfiMatch) return amfiMatch;
  }

  const recFolio = folioPrefix(rec.folio);
  const recName = normalizeHoldingName(rec.holdingName);

  // Folio + name: never against a holding that already has a *different*
  // ISIN. If this row has an ISIN, only backfill a holding that has none.
  if (recFolio.length >= 5 && recName.length >= 4 && !isPlaceholderName(rec.holdingName)) {
    const folioNameMatch = mine.find(h => {
      const hIsin = h.isin && h.isin.length >= 10 ? h.isin.toUpperCase() : '';
      if (recIsin && hIsin) return false;
      const hFolio = folioPrefix(h.symbolOrFolio) || folioPrefix(h.accountNumberMasked);
      if (hFolio !== recFolio) return false;
      return namesLikelySame(normalizeHoldingName(h.name), recName);
    });
    if (folioNameMatch) return folioNameMatch;
  }

  // Row already identified by ISIN and nothing matched — new scheme.
  if (recIsin) return undefined;

  if (recName.length >= 4 && !isPlaceholderName(rec.holdingName)) {
    const nameMatch = mine.find(h => {
      if (h.isin && h.isin.length >= 10) return false;
      return namesLikelySame(normalizeHoldingName(h.name), recName);
    });
    if (nameMatch) return nameMatch;
  }

  // Placeholder + folio, no ISIN: attach to the single unnamed row on that
  // folio. Two named schemes on the same folio must stay distinct.
  if (recFolio.length >= 5 && isPlaceholderName(rec.holdingName)) {
    const stubs = mine.filter(h => {
      const hFolio = folioPrefix(h.symbolOrFolio) || folioPrefix(h.accountNumberMasked);
      if (hFolio !== recFolio) return false;
      if (h.isin && h.isin.length >= 10) return false;
      return true;
    });
    if (stubs.length === 1) return stubs[0];
  }

  return undefined;
}

/** Dedup key: ISIN, else name+folio — never folio alone. */
export function holdingIdentityKey(h: Pick<Holding, 'ownerId' | 'isin' | 'name' | 'symbolOrFolio' | 'accountNumberMasked'>): string {
  if (h.isin && h.isin.length >= 10) {
    return `${h.ownerId}_ISIN_${h.isin.toUpperCase()}`;
  }
  const norm = normalizeHoldingName(h.name);
  const folio = folioPrefix(h.symbolOrFolio) || folioPrefix(h.accountNumberMasked);
  return `${h.ownerId}_NAME_${norm || (h.name || '').toLowerCase()}_FOLIO_${folio}`;
}
