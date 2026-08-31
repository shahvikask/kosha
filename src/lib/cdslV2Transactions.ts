import { CASTransaction, CanonicalCASDocument, CASSecurity, normalizeDecimal, normalizeISIN } from './casV2Model';

const ISIN_RE = /\b([A-Z]{2}[A-Z0-9]{10})\b/i;
const DATE_RE = /^(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\b/;
const MONTHS: Record<string, string> = {
  jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
  jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
};

function toIsoDate(value: string): string | undefined {
  const m = value.trim().match(DATE_RE);
  if (!m) return undefined;
  let year = m[3];
  if (year.length === 2) year = `20${year}`;
  return `${year}-${m[2].padStart(2, '0')}-${m[1].padStart(2, '0')}`;
}

function numericTokens(text: string): string[] {
  return (text.match(/-?(?:\d{1,3}(?:,\d{2,3})+|\d+)(?:\.\d+)?/g) || [])
    .map(v => normalizeDecimal(v))
    .filter((v): v is string => Boolean(v));
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

function actionFromDemat(description: string, credit: string | undefined, debit: string | undefined): string {
  const d = description.toUpperCase();
  if (/CA[- ]?SPLIT|BONUS/.test(d)) return 'BONUS';
  if (/ON-CR|IN-CR|CREDIT|RECEIPT/.test(d)) return 'BUY';
  if (/EP-DR|OUT-DR|DEBIT|DELIVERY/.test(d)) return 'SELL';
  if (credit && credit !== '0') return 'BUY';
  if (debit && debit !== '0') return 'SELL';
  return 'UNKNOWN';
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

    const tail = line.slice(dateMatch[0].length);
    const nums = numericTokens(tail);
    if (nums.length < 3) continue;

    // CDSL demat transaction columns are: Opening, Credit, Debit, Closing, Stamp Duty.
    // Some columns are blank/"--", so we use the final four numeric values when
    // five are not present and never infer a rupee amount from them.
    const closing = nums[nums.length - 2] || nums[nums.length - 1];
    const bodyNums = nums.slice(0, Math.max(0, nums.length - 1));
    const opening = bodyNums.length >= 4 ? bodyNums[0] : undefined;
    const credit = bodyNums.length >= 4 ? bodyNums[1] : undefined;
    const debit = bodyNums.length >= 4 ? bodyNums[2] : undefined;
    const quantity = credit && credit !== '0' ? credit : debit && debit !== '0' ? debit : undefined;
    if (!quantity || quantity === '0') continue;

    const description = lines.slice(Math.max(0, i - 4), i).join(' ').replace(/\s+/g, ' ').trim();
    const action = actionFromDemat(description, credit, debit);
    if (action === 'UNKNOWN') continue;

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
      balance: normalizeDecimal(closing),
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
      // The MF section is identified by the presence of an ISIN: line before the first dated row.
      inMfTransactions = true;
      currentIsin = undefined;
      continue;
    }
    if (/HOLDING STATEMENT AS ON|MUTUAL FUND UNITS HELD AS ON|Page \d+/i.test(line)) {
      if (/HOLDING STATEMENT AS ON|MUTUAL FUND UNITS HELD AS ON/i.test(line)) inMfTransactions = false;
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

    const nums = numericTokens(descriptionAndValues);
    if (nums.length < 4) continue;

    // MF/RTA columns: Amount, NAV, Price, Units, Stamp Duty, Distribution, Withdrawal.
    // A transaction reference (e.g. 68723) can precede Amount. It is an integer token
    // immediately before a decimal amount; do not treat it as money.
    let offset = 0;
    if (nums.length >= 7 && /^\d+$/.test(nums[0]) && nums[1].includes('.')) offset = 1;
    if (nums.length - offset < 4) continue;

    const amount = nums[offset];
    const nav = nums[offset + 1];
    const units = nums[offset + 3];
    if (!amount || !nav || !units) continue;

    const security: CASSecurity = { isin: currentIsin, name: currentScheme };
    out.push({
      kind: 'TRANSACTION',
      source: 'CDSL',
      ownerId,
      date,
      security,
      action,
      amount: amount.startsWith('-') ? { value: amount.slice(1), currency: 'INR' } : { value: amount, currency: 'INR' },
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
