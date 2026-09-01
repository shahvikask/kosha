import { CanonicalCASDocument, CASHolding, CASSecurity, CASSource, money, normalizeDecimal, normalizeISIN, normalizePan } from './casV2Model';
import { validateCanonicalCAS, CASValidationResult } from './casV2Validation';

// Indian ISIN: 12 characters with a numeric check digit at the end.
const ISIN_RE = /\b([A-Z]{2}[A-Z0-9]{9}[0-9])\b/i;
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
  return (text.match(/-?(?:[0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]+)?|\.[0-9]+)/g) || [])
    .map(v => v.replace(/,/g, ''))
    .filter(v => /^-?(?:\d+(?:\.\d+)?|\.\d+)$/.test(v));
}

function addDecimals(a: string, b: string): string {
  const na = normalizeDecimal(a) || '0';
  const nb = normalizeDecimal(b) || '0';
  const [ai, af = ''] = na.split('.');
  const [bi, bf = ''] = nb.split('.');
  const scale = Math.max(af.length, bf.length);
  const factor = 10n ** BigInt(scale);
  const av = BigInt(ai) * factor + BigInt((af + '0'.repeat(scale)).slice(0, scale) || '0');
  const bv = BigInt(bi) * factor + BigInt((bf + '0'.repeat(scale)).slice(0, scale) || '0');
  const sum = av + bv;
  if (scale === 0) return sum.toString();
  const negative = sum < 0n;
  const abs = negative ? -sum : sum;
  const raw = abs.toString().padStart(scale + 1, '0');
  const whole = raw.slice(0, -scale) || '0';
  const fraction = raw.slice(-scale).replace(/0+$/, '');
  return `${negative ? '-' : ''}${whole}${fraction ? `.${fraction}` : ''}`;
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

function cleanName(value: string): string {
  return value
    .replace(/\s+/g, ' ')
    .replace(/\b(?:HOLDING STATEMENT AS ON|MUTUAL FUND UNITS HELD AS ON).*$/i, '')
    .replace(/\b(?:ISIN|Scheme Name|Security|Current Bal|Frozen Bal|Pledge|Free Bal|Market Price|Value)\b/gi, ' ')
    .trim();
}

function nameAroundIsin(lines: string[], index: number, sectionStart: number): string {
  const current = lines[index];
  const isinMatch = current.match(ISIN_RE);
  if (!isinMatch) return 'Unnamed Security';

  const before = cleanName(current.slice(0, isinMatch.index));
  const continuation = index + 1 < lines.length ? cleanName(lines[index + 1]) : '';
  const candidates: string[] = [];
  if (before) candidates.push(before);

  if (!before) {
    for (let i = index - 1; i >= Math.max(sectionStart, index - 3); i--) {
      const prior = cleanName(lines[i]);
      if (!prior) continue;
      if (/^(?:DP Name|BO ID|Account Status|Nominee|Statement|Page|Portfolio Value)/i.test(prior)) break;
      candidates.unshift(prior);
      if (candidates.join(' ').length >= 12) break;
    }
  }

  if (continuation && !ISIN_RE.test(lines[index + 1]) &&
      !/^(?:Grand Total|Portfolio Value|DP Name|Page|For any queries)/i.test(continuation)) {
    candidates.push(continuation);
  }
  return cleanName(candidates.join(' ')) || 'Unnamed Security';
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

function parseDematHoldings(lines: string[], ownerId: string, periodTo?: string): CASHolding[] {
  const holdings: CASHolding[] = [];
  let active = false;
  let sectionStart = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/HOLDING STATEMENT AS ON/i.test(line) && !/OF AIF/i.test(line)) {
      active = true;
      sectionStart = i + 1;
      continue;
    }
    if (active && (/MUTUAL FUND UNITS HELD/i.test(line) || /STATEMENT OF TRANSACTIONS/i.test(line) || /For any queries/i.test(line))) active = false;
    if (!active) continue;

    const isinMatch = line.match(ISIN_RE);
    if (!isinMatch) continue;
    const isin = normalizeISIN(isinMatch[1]);
    if (!isin) continue;

    const afterIsin = line.slice((isinMatch.index || 0) + isinMatch[0].length);
    const vals = numbers(afterIsin);
    if (vals.length < 4) continue;

    const quantity = normalizeDecimal(vals[0]);
    const marketPrice = vals[vals.length - 2];
    const marketValue = vals[vals.length - 1];
    if (!quantity || marketPrice === undefined || marketValue === undefined) continue;

    holdings.push({
      kind: 'HOLDING', source: 'CDSL', ownerId, asOfDate: periodTo,
      security: { isin, name: nameAroundIsin(lines, i, sectionStart) },
      quantity,
      marketPrice: money(marketPrice),
      marketValue: money(marketValue),
    });
  }
  return holdings;
}

function parseMutualFundHoldings(lines: string[], ownerId: string, periodTo?: string): CASHolding[] {
  const rows = new Map<string, CASHolding>();
  let active = false;
  let sectionStart = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (/MUTUAL FUND UNITS HELD AS ON/i.test(line)) {
      active = true;
      sectionStart = i + 1;
      continue;
    }
    if (active && (/Grand Total|Details of Unclaimed Redemption|NOTES TO CAS/i.test(line))) active = false;
    if (!active) continue;

    const isinMatch = line.match(ISIN_RE);
    if (!isinMatch || !isinMatch[1].toUpperCase().startsWith('INF')) continue;
    const isin = normalizeISIN(isinMatch[1]);
    if (!isin) continue;

    let afterIsin = line.slice((isinMatch.index || 0) + isinMatch[0].length);
    afterIsin = afterIsin.replace(/\bARN[-:]?\s*[A-Z0-9-]+/gi, ' ');

    const folioMatch = afterIsin.match(/\b\d{1,14}\/\d+\b/) || afterIsin.match(/\b\d{5,14}\b/);
    const folio = folioMatch?.[0];
    if (!folio) continue;
    afterIsin = afterIsin.replace(folio, ' ');

    const vals = numbers(afterIsin);
    if (vals.length < 4) continue;
    const quantity = normalizeDecimal(vals[0]);
    const nav = vals[1];
    const costValue = vals[2];
    const marketValue = vals[3];
    if (!quantity) continue;

    const key = `${isin}|${folio}`;
    const existing = rows.get(key);
    if (!existing) {
      rows.set(key, {
        kind: 'HOLDING', source: 'CDSL', ownerId, asOfDate: periodTo,
        security: { isin, folio, name: nameAroundIsin(lines, i, sectionStart) },
        quantity,
        marketPrice: money(nav),
        marketValue: money(marketValue),
        costValue: money(costValue),
      });
      continue;
    }

    // Preserve exact decimal strings when the same ISIN/folio appears on
    // multiple rows. The NAV is normally identical; retain the first source NAV.
    existing.quantity = addDecimals(existing.quantity, quantity);
    existing.costValue = money(addDecimals(existing.costValue?.value || '0', costValue));
    existing.marketValue = money(addDecimals(existing.marketValue?.value || '0', marketValue));
  }

  return [...rows.values()];
}

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
