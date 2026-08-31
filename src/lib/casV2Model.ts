/**
 * Canonical CAS V2 model.
 *
 * This layer deliberately keeps source values as decimal strings. A CAS is
 * evidence, not the accounting database, and financial values must not be
 * silently rounded or fabricated while moving between parser stages.
 */

export type CASSource = 'CDSL' | 'NSDL' | 'CAMS' | 'KFINTECH' | 'UNKNOWN';
export type CASRecordKind = 'HOLDING' | 'TRANSACTION' | 'SUMMARY';

export interface CASMoney {
  /** Decimal value exactly as normalized from the statement. */
  value: string;
  currency: 'INR';
}

export interface CASSecurity {
  isin?: string;
  name: string;
  folio?: string;
  boid?: string;
}

export interface CASHolding {
  kind: 'HOLDING';
  source: CASSource;
  ownerId: string;
  asOfDate?: string;
  security: CASSecurity;
  quantity: string;
  marketPrice?: CASMoney;
  marketValue?: CASMoney;
  costValue?: CASMoney;
  rawSourceId?: string;
}

export interface CASTransaction {
  kind: 'TRANSACTION';
  source: CASSource;
  ownerId: string;
  date: string;
  security: CASSecurity;
  action: string;
  amount?: CASMoney;
  units?: string;
  nav?: CASMoney;
  balance?: string;
  rawDescription: string;
  rawSourceId?: string;
}

export interface CASSummary {
  source: CASSource;
  ownerId: string;
  periodFrom?: string;
  periodTo?: string;
  dematValue?: CASMoney;
  mutualFundValue?: CASMoney;
  aifValue?: CASMoney;
  grandTotal?: CASMoney;
}

export interface CanonicalCASDocument {
  schemaVersion: 1;
  source: CASSource;
  ownerId: string;
  investorPan?: string;
  periodFrom?: string;
  periodTo?: string;
  holdings: CASHolding[];
  transactions: CASTransaction[];
  summary?: CASSummary;
  warnings: string[];
}

/** Normalize a financial decimal without converting through JavaScript Number. */
export function normalizeDecimal(value: string | number | undefined | null): string | undefined {
  if (value === undefined || value === null) return undefined;
  const raw = String(value).trim().replace(/,/g, '');
  if (!raw) return undefined;
  if (!/^-?\d+(?:\.\d+)?$/.test(raw)) return undefined;

  const negative = raw.startsWith('-');
  const unsigned = negative ? raw.slice(1) : raw;
  const [whole, fraction] = unsigned.split('.');
  const normalizedWhole = whole.replace(/^0+(?=\d)/, '') || '0';
  const normalizedFraction = fraction?.replace(/0+$/, '');
  const result = normalizedFraction ? `${normalizedWhole}.${normalizedFraction}` : normalizedWhole;
  return negative && result !== '0' ? `-${result}` : result;
}

/** Indian ISINs are 12 characters: 2-letter country + 10 alphanumeric characters. */
export function normalizeISIN(isin: string | undefined): string | undefined {
  if (!isin) return undefined;
  const value = isin.replace(/[-\s]/g, '').toUpperCase();
  return /^[A-Z]{2}[A-Z0-9]{10}$/.test(value) ? value : undefined;
}

export function normalizePan(pan: string | undefined): string | undefined {
  if (!pan) return undefined;
  const value = pan.trim().toUpperCase();
  return /^[A-Z]{5}\d{4}[A-Z]$/.test(value) ? value : undefined;
}

export function money(value: string | number | undefined): CASMoney | undefined {
  const normalized = normalizeDecimal(value);
  return normalized === undefined ? undefined : { value: normalized, currency: 'INR' };
}
