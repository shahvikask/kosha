import { CanonicalCASDocument, CASHolding, CASTransaction, normalizeDecimal } from './casV2Model';

export type CASValidationSeverity = 'ERROR' | 'WARNING';

export interface CASValidationFinding {
  severity: CASValidationSeverity;
  code: string;
  message: string;
  recordIndex?: number;
}

export interface CASValidationResult {
  valid: boolean;
  findings: CASValidationFinding[];
}

function add(findings: CASValidationFinding[], severity: CASValidationSeverity, code: string, message: string, recordIndex?: number) {
  findings.push({ severity, code, message, recordIndex });
}

function isPositiveDecimal(value: string | undefined): boolean {
  if (value === undefined) return false;
  const n = Number(value);
  return Number.isFinite(n) && n > 0;
}

function nearlyEqual(a: string | undefined, b: string | undefined, tolerance = 0.02): boolean {
  if (a === undefined || b === undefined) return false;
  const an = Number(a);
  const bn = Number(b);
  if (!Number.isFinite(an) || !Number.isFinite(bn)) return false;
  return Math.abs(an - bn) <= Math.max(tolerance, Math.abs(bn) * 0.0001);
}

export function validateHolding(holding: CASHolding, index: number): CASValidationFinding[] {
  const findings: CASValidationFinding[] = [];
  if (!holding.ownerId) add(findings, 'ERROR', 'HOLDING_OWNER_MISSING', 'Holding has no target owner.', index);
  if (!holding.security.name.trim()) add(findings, 'ERROR', 'HOLDING_NAME_MISSING', 'Holding has no security name.', index);
  if (!normalizeDecimal(holding.quantity)) add(findings, 'ERROR', 'HOLDING_QUANTITY_INVALID', 'Holding quantity is not a valid decimal.', index);
  if (holding.marketValue && !normalizeDecimal(holding.marketValue.value)) {
    add(findings, 'ERROR', 'HOLDING_VALUE_INVALID', 'Holding market value is not a valid decimal.', index);
  }
  if (holding.marketPrice && !normalizeDecimal(holding.marketPrice.value)) {
    add(findings, 'ERROR', 'HOLDING_PRICE_INVALID', 'Holding market price is not a valid decimal.', index);
  }

  if (holding.marketValue && holding.marketPrice && isPositiveDecimal(holding.quantity)) {
    const implied = Number(holding.quantity) * Number(holding.marketPrice.value);
    const stated = Number(holding.marketValue.value);
    if (Number.isFinite(implied) && Number.isFinite(stated) && Math.abs(implied - stated) > Math.max(2, stated * 0.02)) {
      add(findings, 'WARNING', 'HOLDING_VALUE_MISMATCH', `Quantity × market price (${implied}) does not reconcile to stated value (${stated}).`, index);
    }
  }
  return findings;
}

export function validateTransaction(transaction: CASTransaction, index: number): CASValidationFinding[] {
  const findings: CASValidationFinding[] = [];
  if (!transaction.ownerId) add(findings, 'ERROR', 'TX_OWNER_MISSING', 'Transaction has no target owner.', index);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(transaction.date)) add(findings, 'ERROR', 'TX_DATE_INVALID', `Transaction date is not ISO format: ${transaction.date}`, index);
  if (!transaction.security.name.trim()) add(findings, 'ERROR', 'TX_NAME_MISSING', 'Transaction has no security name.', index);
  if (transaction.amount && !normalizeDecimal(transaction.amount.value)) add(findings, 'ERROR', 'TX_AMOUNT_INVALID', 'Transaction amount is not a valid decimal.', index);
  if (transaction.units !== undefined && !normalizeDecimal(transaction.units)) add(findings, 'ERROR', 'TX_UNITS_INVALID', 'Transaction units are not a valid decimal.', index);
  if (transaction.nav && !normalizeDecimal(transaction.nav.value)) add(findings, 'ERROR', 'TX_NAV_INVALID', 'Transaction NAV is not a valid decimal.', index);

  if (transaction.amount && transaction.units && transaction.nav) {
    const units = Number(transaction.units);
    const nav = Number(transaction.nav.value);
    const amount = Number(transaction.amount.value);
    if (units > 0 && nav > 0 && Number.isFinite(amount)) {
      const expected = units * nav;
      if (!nearlyEqual(String(amount), String(expected), Math.max(1, amount * 0.005))) {
        add(findings, 'WARNING', 'TX_AMOUNT_UNIT_NAV_MISMATCH', `Units × NAV (${expected}) does not reconcile to transaction amount (${amount}).`, index);
      }
    }
  }
  return findings;
}

/**
 * Validate the canonical representation before it is handed to reconciliation
 * or the ledger. Errors block import; warnings are retained as provenance.
 */
export function validateCanonicalCAS(document: CanonicalCASDocument): CASValidationResult {
  const findings: CASValidationFinding[] = [];

  if (document.schemaVersion !== 1) add(findings, 'ERROR', 'SCHEMA_VERSION_UNSUPPORTED', `Unsupported CAS schema version: ${document.schemaVersion}`);
  if (!document.ownerId) add(findings, 'ERROR', 'DOCUMENT_OWNER_MISSING', 'CAS document has no target owner.');
  if (!document.source || document.source === 'UNKNOWN') add(findings, 'WARNING', 'SOURCE_UNKNOWN', 'CAS source could not be identified confidently.');
  if (document.investorPan && !/^[A-Z]{5}\d{4}[A-Z]$/.test(document.investorPan)) add(findings, 'WARNING', 'PAN_INVALID', 'Investor PAN is not in the expected uppercase format.');

  document.holdings.forEach((holding, index) => findings.push(...validateHolding(holding, index)));
  document.transactions.forEach((transaction, index) => findings.push(...validateTransaction(transaction, index)));

  if (document.summary?.grandTotal && document.summary.dematValue && document.summary.mutualFundValue) {
    const grand = Number(document.summary.grandTotal.value);
    const components = Number(document.summary.dematValue.value) + Number(document.summary.mutualFundValue.value) + Number(document.summary.aifValue?.value || 0);
    if (Number.isFinite(grand) && Number.isFinite(components) && Math.abs(grand - components) > Math.max(2, grand * 0.005)) {
      add(findings, 'WARNING', 'SUMMARY_COMPONENT_MISMATCH', `Summary components (${components}) do not reconcile to grand total (${grand}).`);
    }
  }

  return {
    valid: findings.every(f => f.severity !== 'ERROR'),
    findings,
  };
}
