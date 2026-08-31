import { CanonicalCASDocument, CASHolding, CASSecurity, CASSource, CASTransaction, money, normalizeDecimal, normalizeISIN, normalizePan } from './casV2Model';
import { validateCanonicalCAS, CASValidationResult } from './casV2Validation';

const ISIN_RE = /\b([A-Z]{2}[A-Z0-9]{9})\b/i;
const DATE_RE = /(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})/;
const ALPHA_DATE_RE = /(\d{1,2})[-/]([A-Za-z]{3,9})[-/](\d{2,4})/;
const MONTHS: Record<string, string> = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

function indianDate(value: string | undefined): string | undefined {
  if (!value) return undefined;
  const raw = value.trim();
  const alpha = raw.match(ALPHA_DATE_RE);
  if (alpha) {
    let year = alpha[3];
    if (year.length === 2) year = `20${year}`;
    const month = MONTHS[alpha[2].slice(0, 3).toLowerCase()];
    return month ? `${year}-${month}-${alpha[1].padStart(2, '0')}` : undefined;
  }
  const numeric = raw.match(DATE_RE);
  if (numeric) {
    let year = numeric[3];
    if (year.length === 2) year = `20${year}`;
    return `${year}-${numeric[2].padStart(2, '0')}-${numeric[1].padStart(2, '0')}`;
  }
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : undefined;
}

function numbers(text: string): string[] {
  return (text.match(/-?[0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]+)?/g) || [])
    .map(v => normalizeDecimal(v))
    .filter((v): v is string => Boolean(v));
}

function valueFromLabel(lines: string[], label: RegExp): string | undefined {
  for (const line of lines.slice(0, 120)) {
    const match = line.match(label);
    if (match) {
      const tail = line.slice(match.index! + match[0].length);
      const vals = numbers(tail);
      if (vals.length) return vals[vals.length - 1];
    }
  }
  return undefined;
}

function parseSummary(lines: string[], ownerId: string, periodFrom?: string, periodTo?: string) {
  const demat = valueFromLabel(lines, /CDSL\s+Demat\s+Accounts?/i);
  const mf = valueFromLabel(lines, /Mutual\s+Fund\s+Folios/i);
  const grand = valueFromLabel(lines, /Total\s+Portfolio\s+Value/i);
  const aif = valueFromLabel(lines, /Alternative\s+Investment\s+Fund/i);
  return {
    source: 'CDSL' as CASSource,
    ownerId,
    periodFrom,
    periodTo,
    dematValue: money(demat),
    mutualFundValue: money(mf),
    aifValue: money(aif),
    grandTotal: money(grand),
  };
}

function nearestName(lines: string[], index: number): string {
  const candidates: string[] = [];
  for (let i = index - 1; i >= Math.max(0, index - 4); i--) {
    const line = lines[i].trim();
    if (!line || /^(ISIN|Scheme Name|Security|Current|Frozen|Pledge|Market|Value|Folio|Closing|Portfolio Value|Page\s)/i.test(line)) continue;
    if (/^(CDSL|NSDL|Mutual Fund|HOLDING|STATEMENT|Summary|For any queries|Central Depository)/i.test(line)) continue;
    candidates.unshift(line);
  }
  return candidates.join(' ').replace(/\s+/g, ' ').trim() || 'Unnamed Security';
}

function parseDematHoldings(lines: string[], ownerId: string, periodTo?: string): CASHolding[] {
  const holdings: CASHolding[] = [];
  let active = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/HOLDING STATEMENT AS ON/i.test(line) && !/OF AIF/i.test(line)) {
      active = true;
      continue;
    }
    if (active && (/MUTUAL FUND UNITS HELD/i.test(line) || /STATEMENT OF TRANSACTIONS/i.test(line) || /For any queries/i.test(line))) {
      active = false;
    }
    if (!active) continue;

    const isinMatch = line.match(ISIN_RE);
    if (!isinMatch) continue;
    const isin = normalizeISIN(isinMatch[1]);
    if (!isin) continue;

    const afterIsin = line.slice((isinMatch.index || 0) + isinMatch[0].length);
    const vals = numbers(afterIsin);
    if (vals.length < 2) continue;

    // CDSL demat table repeats the free balance after pledge/frozen columns.
    // The final two numeric columns are market price/face value and valuation.
    const quantity = vals[0];
    const marketValue = vals[vals.length - 1];
    const marketPrice = vals.length >= 3 ? vals[vals.length - 2] : undefined;
    if (!marketValue || !quantity) continue;

    const security: CASSecurity = { isin, name: nearestName(lines, i) };
    holdings.push({
      kind: 'HOLDING', source: 'CDSL', ownerId, asOfDate: periodTo,
      security,
      quantity,
      marketPrice: money(marketPrice),
      marketValue: money(marketValue),
    });
  }
  return holdings;
}

function parseMutualFundHoldings(lines: string[], ownerId: string, periodTo?: string): CASHolding[] {
  const holdings: CASHolding[] = [];
  let active = false;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/MUTUAL FUND UNITS HELD AS ON/i.test(line)) {
      active = true;
      continue;
    }
    if (active && (/NOTES TO CAS|ABOUT CDSL|Load Structures|Grand Total/i.test(line))) active = false;
    if (!active) continue;

    const isinMatch = line.match(/\b(INF[A-Z0-9]{9})\b/i);
    if (!isinMatch) continue;
    const isin = normalizeISIN(isinMatch[1]);
    if (!isin) continue;

    const afterIsin = line.slice((isinMatch.index || 0) + isinMatch[0].length);
    const folioMatch = afterIsin.match(/\b\d{5,14}(?:\/\d+)?\b/);
    const folio = folioMatch?.[0];
    const withoutFolio = folio ? afterIsin.replace(folio, ' ') : afterIsin;
    const vals = numbers(withoutFolio);

    // CDSL MF holding columns: Closing units, NAV, cumulative invested,
    // valuation, unrealised P/L, annualised return. We only accept the
    // deterministic first/second/fourth values; no estimation is allowed.
    if (vals.length < 4) continue;
    const quantity = vals[0];
    const nav = vals[1];
    const marketValue = vals[3];
    const costValue = vals[2];
    if (!quantity || !nav || !marketValue) continue;

    const security: CASSecurity = {
      isin,
      folio,
      name: nearestName(lines, i),
    };

    holdings.push({
      kind: 'HOLDING', source: 'CDSL', ownerId, asOfDate: periodTo,
      security,
      quantity,
      marketPrice: money(nav),
      marketValue: money(marketValue),
      costValue: money(costValue),
    });
  }
  return holdings;
}

/**
 * V2 CDSL parser for the holdings/summary layer.
 * It intentionally does not manufacture transactions yet; transaction parsing
 * is the next source-specific stage after the holding model is validated.
 */
export function parseCdslCasV2Text(fullText: string, ownerId: string): CanonicalCASDocument {
  const lines = fullText.split(/\r?\n/).map(v => v.trim()).filter(Boolean);
  const periodMatch = fullText.match(/period from\s+([^\s]+)\s+to\s+([^\s]+)/i);
  const periodFrom = indianDate(periodMatch?.[1]);
  const periodTo = indianDate(periodMatch?.[2]);
  const panMatch = fullText.match(/PAN\s*[:.]?\s*([A-Z]{5}\d{4}[A-Z])/i);

  const document: CanonicalCASDocument = {
    schemaVersion: 1,
    source: 'CDSL',
    ownerId,
    investorPan: normalizePan(panMatch?.[1]),
    periodFrom,
    periodTo,
    holdings: [
      ...parseDematHoldings(lines, ownerId, periodTo),
      ...parseMutualFundHoldings(lines, ownerId, periodTo),
    ],
    transactions: [],
    summary: parseSummary(lines, ownerId, periodFrom, periodTo),
    warnings: [],
  };

  const validation = validateCanonicalCAS(document);
  document.warnings = validation.findings.map(f => `${f.severity}: ${f.code}: ${f.message}`);
  return document;
}

export function validateCdslCasV2Text(fullText: string, ownerId: string): { document: CanonicalCASDocument; validation: CASValidationResult } {
  const document = parseCdslCasV2Text(fullText, ownerId);
  return { document, validation: validateCanonicalCAS(document) };
}
