/**
 * Match a CAS/statement row to an existing holding.
 *
 * CDSL prints many schemes under one AMC folio (e.g. 17001385/94).
 * Folio is an account, not a fund — matching on folio prefix merged
 * ICICI / Edelweiss / Nippon / WhiteOak schemes and then priced the
 * lump with the last scheme's NAV (Mar 2026 Home showed ₹5.44 cr vs
 * the PDF's ₹2.03 cr).
 *
 * Identity is ISIN, then AMFI, then folio+name. Never folio alone.
 * A row with an ISIN must not fall back to a stripped name — "WhiteOak
 * Capital Fund" is the same string after dropping "mid cap" / "large cap".
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
  if (recFolio.length >= 5 && recName.length >= 4) {
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

  if (recName.length >= 4) {
    const nameMatch = mine.find(h => {
      if (h.isin && h.isin.length >= 10) return false;
      return namesLikelySame(normalizeHoldingName(h.name), recName);
    });
    if (nameMatch) return nameMatch;
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
