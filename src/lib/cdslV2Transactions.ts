import { CASTransaction, CanonicalCASDocument, CASSecurity, normalizeDecimal, normalizeISIN } from './casV2Model';

const ISIN_RE = /\b([A-Z]{2}[A-Z0-9]{10})\b/i;
const DATE_RE = /^(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b/;

function toIsoDate(value: string): string | undefined {
  const m = value.trim().match(DATE_RE);
  if (!m) return undefined;
  let year = m[3];
  if (year.length === 2) year = `20${year}`;
  return `${year}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
}

function statementTokens(text: string): string[] {
  return text.match(/--|-?(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d+)?/g) || [];
}

function decimalToken(value: string | undefined): string | undefined {
  if (!value || value === '--') return undefined;
  return normalizeDecimal(value);
}

function cleanName(lines: string[], index: number): string {
  const out: string[] = [];
  for (let i = index - 1; i >= Math.max(0, index - 4); i--) {
    const line = lines[i].trim();
    if (!line) continue;
    if (/^(ISIN|Transaction|Date|Op\. Bal|Credit|Debit|Cl\. Bal|Stamp|DP Name|BO ID|STATEMENT OF TRANSACTIONS|HOLDING STATEMENT|Central Depository|CONSOLIDATED ACCOUNT|Summary of)/i.test(line)) continue;
    if (/^(ON-CR|IN-CR|OUT-DR|EP-DR|CA-Split|TX:|SET:|CtBo:|Cr Current|Db Current)/i.test(line)) continue;
    out.unshift(line.replace(/\s+/g, ' '));
  }
  return out.join(' ').trim() || 'Unnamed Security';
}

function actionFromDemat(description: string, credit?: string, debit?: string): 'BUY' | 'SELL' | 'BONUS' | undefined {
  const d = description.toUpperCase();
  if (/CA[- ]?SPLIT|BONUS/.test(d)) return 'BONUS';
  if (/ON-CR|IN-CR|CREDIT|RECEIPT/.test(d)) return 'BUY';
  if (/EP-DR|OUT-DR|DEBIT|DELIVERY/.test(d)) return 'SELL';
  if (credit && credit !== '--' && decimalToken(credit) !== '0') return 'BUY';
  if (debit && debit !== '--' && decimalToken(debit) !== '0') return 'SELL';
  return undefined;
}

function parseDematTransactions(lines: string[], ownerId: string): CASTransaction[] {
  const out: CASTransaction[] = [];
  let inSection = false;
  let currentIsin: string | undefined;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (/STATEMENT OF TRANSACTIONS FOR THE PERIOD/i.test(line)) {
      inSection = true;
      currentIsin = undefined;
      continue;
    }
    if (/HOLDING STATEMENT AS ON|MUTUAL FUND UNITS HELD WITH MF\/RTA|MUTUAL FUND UNITS HELD AS ON/i.test(line)) {
      inSection = false;
      currentIsin = undefined;
      continue;
    }
    if (!inSection || /No Transaction during the period/i.test(line)) continue;

    const isinMatch = line.match(ISIN_RE);
    if (isinMatch) {
      currentIsin = normalizeISIN(isinMatch[1]);
      continue;
    }

    const dateMatch = line.match(DATE_RE);
    if (!dateMatch || !currentIsin) continue;
    const date = toIsoDate(dateMatch[0]);
    if (!date) continue;

    const tokens = statementTokens(line.slice(dateMatch[0].length));
    if (tokens.length < 3) continue;

    // Preserve '--' so blank credit/debit columns cannot be mistaken for the closing balance.
    // Layout: Opening, Credit, Debit, Closing, Stamp Duty (last column can be absent).
    const credit = tokens[1];
    const debit = tokens[2];
    const closingIndex = tokens.length >= 5 ? 3 : tokens.length - 1;
    const closing = decimalToken(tokens[closingIndex]);
    const description = lines.slice(Math.max(0, i - 4), i).join(' ').replace(/\s+/g, ' ').trim();
    const action = actionFromDemat(description, credit, debit);
    if (!action) continue;

    const quantity = action === 'BUY'
      ? decimalToken(credit)
      : action === 'SELL'
        ? decimalToken(debit)
        : decimalToken(credit) || decimalToken(debit);
    if (!quantity || quantity === '0') continue;

    const security: CASSecurity = { isin: currentIsin, name: cleanName(lines, i) };
    const signedUnits = action === 'SELL' ? `-${quantity}` : quantity;

    out.push({
      kind: 'TRANSACTION',
      source: 'CDSL',
      ownerId,
      date,
      security,
      action,
      units: normalizeDecimal(signedUnits),
      balance: closing,
      rawDescription: description,
      rawSourceId: currentIsin,
    });
  }
  return out;
}

function mfAction(description: string): string | undefined {
  const d = description.toUpperCase().replace(/\s+/g, ' ').trim();
  if (/DIVIDEND|PAYOUT|INTEREST/.test(d)) return /INTEREST/.test(d) ? 'INTEREST' : 'DIVIDEND';
  if (/REDEMPTION|S T P OUT|STP OUT|LATERAL SHIFT OUT|SWITCH OUT|SWITCH-OUT/.test(d)) return 'SELL';
  if (/PURCHASE|SIP|SYSTEMATIC INVESTMENT|S T P IN|STP IN|LATERAL SHIFT IN|SWITCH IN|SWITCH-IN|ONLINE PURCHASE/.test(d)) return 'BUY';
  return undefined;
}

function parseMfTransactions(lines: string[], ownerId: string): CASTransaction[] {
  const out: CASTransaction[] = [];
  let inMfTransactions = false;
  let currentIsin: string | undefined;
  let currentScheme = 'Unnamed Mutual Fund';

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (/MUTUAL FUND UNITS HELD WITH MF\/RTA/i.test(line)) {
      inMfTransactions = false;
      continue;
    }
    if (/STATEMENT OF TRANSACTIONS FOR THE PERIOD/i.test(line)) {
      inMfTransactions = true;
      currentIsin = undefined;
      continue;
    }
    if (/HOLDING STATEMENT AS ON|MUTUAL FUND UNITS HELD AS ON/i.test(line)) {
      inMfTransactions = false;
      currentIsin = undefined;
      continue;
    }
    if (!inMfTransactions) continue;

    const isinMatch = line.match(/ISIN\s*:\s*([A-Z0-9-]{12,15})/i);
    if (isinMatch) {
      currentIsin = normalizeISIN(isinMatch[1]);
      currentScheme = lines.slice(Math.max(0, i - 2), i)
        .map(v => v.trim()).filter(Boolean)
        .filter(v => !/^(CONSOLIDATED ACCOUNT|Summary of|STATEMENT OF|Date\s+Transaction)/i.test(v))
        .join(' ').replace(/\s+/g, ' ').trim() || currentScheme;
      continue;
    }

    const dateMatch = line.match(DATE_RE);
    if (!dateMatch || !currentIsin) continue;
    const date = toIsoDate(dateMatch[0]);
    if (!date) continue;

    const descriptionAndValues = line.slice(dateMatch[0].length);
    const action = mfAction(descriptionAndValues);
    if (!action) continue;

    const nums = statementTokens(descriptionAndValues).filter(v => v !== '--');
    if (nums.length < 4) continue;

    // CDSL MF/RTA rows contain a transaction reference before Amount. It is an
    // integer token; Amount may itself be an integer, so do not require a decimal.
    let offset = 0;
    if (nums.length >= 7 && /^\d+$/.test(nums[0])) offset = 1;
    if (nums.length - offset < 4) continue;

    const amount = decimalToken(nums[offset]);
    const nav = decimalToken(nums[offset + 1]);
    const units = decimalToken(nums[offset + 3]);
    if (!amount || !nav || !units) continue;

    const security: CASSecurity = { isin: currentIsin, name: currentScheme };
    out.push({
      kind: 'TRANSACTION',
      source: 'CDSL',
      ownerId,
      date,
      security,
      action,
      amount: { value: amount.replace(/^-/, ''), currency: 'INR' },
      units: normalizeDecimal(units),
      nav: { value: nav, currency: 'INR' },
      rawDescription: descriptionAndValues.replace(/\s+/g, ' ').trim(),
      rawSourceId: currentIsin,
    });
  }
  return out;
}

export function parseCdslTransactionsV2Text(fullText: string, ownerId: string): CASTransaction[] {
  const lines = fullText.split(/\r?\n/).map(v => v.trim()).filter(Boolean);
  return [...parseDematTransactions(lines, ownerId), ...parseMfTransactions(lines, ownerId)]
    .sort((a, b) => a.date.localeCompare(b.date));
}

export function attachCdslTransactions(document: CanonicalCASDocument, fullText: string): CanonicalCASDocument {
  return { ...document, transactions: parseCdslTransactionsV2Text(fullText, document.ownerId) };
}
