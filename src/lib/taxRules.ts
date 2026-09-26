/**
 * Versioned tax rules. Implements SRS-TAX-004..006.
 *
 * A computation for a past year must remain correct after a statutory
 * change. Rules are therefore selected by the assessment year derived
 * from the sale date, never from the current system date, and a rule set
 * is never edited once published — a new year gets a new entry.
 *
 * RATES MUST BE VERIFIED against the Finance Act for each year before
 * release. The structure is the point; the numbers are inputs.
 */
import { AssetCategory, GoldHoldMode } from '../types';

export type GainClass =
  | 'LTCG_EQUITY'
  | 'STCG_EQUITY'
  | 'DEBT_SLAB'
  | 'SGB_EXEMPT'
  | 'REAL_ESTATE_LTCG';

export interface TaxRuleSet {
  assessmentYear: string;
  ltcgEquityRateBasisPoints: number;      // 1250 = 12.5%
  ltcgEquityExemptionRupees: number;
  stcgEquityRateBasisPoints: number;      // 2000 = 20%
  equityLongTermDays: number;
  realEstateLongTermDays: number;
  otherLongTermDays: number;
  /** Equity acquired on or before this date uses the higher of cost and FMV. */
  grandfatherDate: string | null;
  /** Debt mutual funds: taxed at slab, so no rate is applied here. */
  debtMfAtSlab: boolean;
  sgbRedemptionExempt: boolean;
  notes: string;
}

const RULE_SETS: Record<string, TaxRuleSet> = {
  'AY25-26': {
    assessmentYear: 'AY25-26',
    ltcgEquityRateBasisPoints: 1250,
    ltcgEquityExemptionRupees: 125000,
    stcgEquityRateBasisPoints: 2000,
    equityLongTermDays: 365,
    realEstateLongTermDays: 730,
    otherLongTermDays: 1095,
    grandfatherDate: '2018-01-31',
    debtMfAtSlab: true,
    sgbRedemptionExempt: true,
    notes: 'FY 2024-25. Rates changed mid-year by Finance (No.2) Act 2024; verify against the Act.',
  },
  'AY26-27': {
    assessmentYear: 'AY26-27',
    ltcgEquityRateBasisPoints: 1250,
    ltcgEquityExemptionRupees: 125000,
    stcgEquityRateBasisPoints: 2000,
    equityLongTermDays: 365,
    realEstateLongTermDays: 730,
    otherLongTermDays: 1095,
    grandfatherDate: '2018-01-31',
    debtMfAtSlab: true,
    sgbRedemptionExempt: true,
    notes: 'FY 2025-26. Verify against Finance Act before release.',
  },
  'AY27-28': {
    assessmentYear: 'AY27-28',
    ltcgEquityRateBasisPoints: 1250,
    ltcgEquityExemptionRupees: 125000,
    stcgEquityRateBasisPoints: 2000,
    equityLongTermDays: 365,
    realEstateLongTermDays: 730,
    otherLongTermDays: 1095,
    grandfatherDate: '2018-01-31',
    debtMfAtSlab: true,
    sgbRedemptionExempt: true,
    notes: 'FY 2026-27. PROVISIONAL — verify against Finance Act 2026.',
  },
};

/** Returns the rule set, or null if that year is not published. Never guesses. */
export function ruleSetFor(assessmentYear: string): TaxRuleSet | null {
  return RULE_SETS[assessmentYear] ?? null;
}

export function publishedAssessmentYears(): string[] {
  return Object.keys(RULE_SETS).sort();
}

/**
 * Whether a mutual fund holding is equity-oriented.
 * Debt funds are taxed at slab, not at the equity rates. The old code
 * applied 20% to every MUTUAL_FUND sale, which overstates tax on debt funds.
 */
export function isEquityOriented(category: AssetCategory, subCategory?: string): boolean {
  if (category === 'EQUITY') return true;
  if (category !== 'MUTUAL_FUND') return false;
  const s = (subCategory || '').toLowerCase();
  const debtWords = ['debt', 'gilt', 'liquid', 'overnight', 'money market',
    'bond', 'duration', 'credit risk', 'banking and psu', 'floater', 'income'];
  if (debtWords.some(w => s.includes(w))) return false;
  return true; // equity, hybrid-aggressive, index, ELSS, arbitrage
}

export function classifyGain(
  category: AssetCategory,
  subCategory: string | undefined,
  isLongTerm: boolean,
  goldHoldMode?: GoldHoldMode
): GainClass {
  if (category === 'GOLD_SGB') {
    const sub = (subCategory || '').toLowerCase();
    const physical = /physical|digital|other/.test(sub);
    const sovereign = !physical && (/sovereign|\bsgb\b/.test(sub) || sub === '');
    if (sovereign && goldHoldMode === 'CERTIFICATE') return 'SGB_EXEMPT';
    return 'DEBT_SLAB';
  }
  if (category === 'REAL_ESTATE') return 'REAL_ESTATE_LTCG';
  if (isEquityOriented(category, subCategory)) {
    return isLongTerm ? 'LTCG_EQUITY' : 'STCG_EQUITY';
  }
  return 'DEBT_SLAB';
}

/**
 * Grandfathered cost under section 112A: for equity acquired on or before
 * 31 January 2018, cost = max(actual cost, min(FMV on that date, proceeds)).
 * Returns the actual cost unchanged when the rule does not apply.
 */
export function grandfatheredCostMinor(
  rules: TaxRuleSet,
  purchaseDate: string,
  actualCostMinor: bigint,
  proceedsMinor: bigint,
  fmv31Jan2018Minor: bigint | null
): bigint {
  if (!rules.grandfatherDate) return actualCostMinor;
  if (purchaseDate > rules.grandfatherDate) return actualCostMinor;
  if (fmv31Jan2018Minor === null || fmv31Jan2018Minor <= 0n) return actualCostMinor;
  const capped = fmv31Jan2018Minor < proceedsMinor ? fmv31Jan2018Minor : proceedsMinor;
  return capped > actualCostMinor ? capped : actualCostMinor;
}

export function applyRateMinor(gainMinor: bigint, basisPoints: number): bigint {
  if (gainMinor <= 0n) return 0n;
  return (gainMinor * BigInt(basisPoints)) / 10000n;
}
