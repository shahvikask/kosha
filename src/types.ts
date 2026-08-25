/**
 * Project Kosha Data Model
 * Aligned with SRS v1.0, Annex A (Data Model) and Annex B (UI/UX).
 */

export type AssetCategory =
  | 'EQUITY'
  | 'MUTUAL_FUND'
  | 'GOLD_SGB'
  | 'FIXED_INCOME'
  | 'REAL_ESTATE'
  | 'SPECIALISED'
  | 'CASH_BANK'
  | 'LIABILITY';

export interface CategoryInfo {
  id: AssetCategory;
  name: string;
  shortName: string;
  description: string;
  iconName: string;
  isLiability: boolean;
  color: string;
}

export const ASSET_CATEGORIES: Record<AssetCategory, CategoryInfo> = {
  EQUITY: {
    id: 'EQUITY',
    name: 'Indian Equities',
    shortName: 'Stocks',
    description: 'Direct shares listed on NSE and BSE',
    iconName: 'TrendingUp',
    isLiability: false,
    color: '#0284c7', // Sky Blue
  },
  MUTUAL_FUND: {
    id: 'MUTUAL_FUND',
    name: 'Mutual Funds & ETFs',
    shortName: 'Funds',
    description: 'Equity, debt, hybrid & index mutual funds',
    iconName: 'PieChart',
    isLiability: false,
    color: '#0d9488', // Teal
  },
  GOLD_SGB: {
    id: 'GOLD_SGB',
    name: 'Gold & SGBs',
    shortName: 'Gold',
    description: 'Sovereign Gold Bonds, 24K Physical & Digital Gold',
    iconName: 'Coins',
    isLiability: false,
    color: '#d97706', // Amber Gold
  },
  FIXED_INCOME: {
    id: 'FIXED_INCOME',
    name: 'Fixed Income & Govt Schemes',
    shortName: 'Fixed Income',
    description: 'PPF, EPF, SSY, NPS, Bank FDs & Govt Bonds',
    iconName: 'ShieldCheck',
    isLiability: false,
    color: '#16a34a', // Emerald Green
  },
  REAL_ESTATE: {
    id: 'REAL_ESTATE',
    name: 'Real Estate & Property',
    shortName: 'Real Estate',
    description: 'Residential flats, commercial plots, land & REITs',
    iconName: 'Home',
    isLiability: false,
    color: '#7c3aed', // Purple
  },
  SPECIALISED: {
    id: 'SPECIALISED',
    name: 'Specialised & Alternative',
    shortName: 'Specialised',
    description: 'PMS, AIF, Unlisted equity & private investments',
    iconName: 'Briefcase',
    isLiability: false,
    color: '#4f46e5', // Indigo
  },
  CASH_BANK: {
    id: 'CASH_BANK',
    name: 'Savings & Emergency Liquid',
    shortName: 'Bank & Liquid',
    description: 'Bank savings accounts and overnight liquid funds',
    iconName: 'Landmark',
    isLiability: false,
    color: '#0891b2', // Cyan
  },
  LIABILITY: {
    id: 'LIABILITY',
    name: 'Loans & Liabilities',
    shortName: 'Loans',
    description: 'Home loan, car loan, education & personal borrowings',
    iconName: 'CreditCard',
    isLiability: true,
    color: '#e11d48', // Rose
  },
};

export type FamilyRelation = 'Self' | 'Spouse' | 'Parent' | 'Child' | 'HUF' | 'Joint';

export interface FamilyMember {
  id: string;
  name: string;
  relation: FamilyRelation;
  panMasked?: string;
  dateOfBirth?: string; // ISO date YYYY-MM-DD
  color: string;
  notes?: string;
}

export type EventType =
  | 'OPENING_BALANCE'
  | 'BUY'
  | 'SELL'
  | 'BONUS'
  | 'SPLIT'
  | 'DIVIDEND'
  | 'INTEREST'
  | 'VALUATION_UPDATE'
  | 'REPAYMENT'
  | 'CORRECTION';

export interface LedgerEvent {
  id: string;
  holdingId: string;
  holdingName: string;
  ownerId: string;
  eventType: EventType;
  eventDate: string; // ISO date YYYY-MM-DD
  quantityScaled: string; // bigint stored as string for JSON safety
  unitPriceMinor: string; // bigint stored as string
  totalAmountMinor: string; // bigint stored as string
  chargesMinor?: string; // STT, Stamp duty, brokerage
  notes?: string;
  supersedesEventId?: string;
  createdAt: string;
}

export interface PurchaseParcel {
  eventId: string;
  purchaseDate: string;
  originalQtyScaled: bigint;
  remainingQtyScaled: bigint;
  purchasePriceMinor: bigint;
  totalCostMinor: bigint;
}

export interface Holding {
  id: string;
  name: string;
  symbolOrFolio: string;
  isin?: string;
  category: AssetCategory;
  subCategory: string;
  ownerId: string; // Family member ID
  quantityScaled: string; // bigint string
  averageCostPerUnitMinor: string; // bigint string
  currentPriceMinor: string; // bigint string
  dayChangePercent?: number; // % day move e.g. +1.4%
  dayChangeMinor?: string; // bigint string
  needsReview?: boolean;
  reviewReasons?: string[];
  
  // Specific attributes for SGB / Gold (Annex A / SRS §6.3)
  benchmarkPriceMinor?: string; // e.g. RBI gold 999 purity redemption benchmark
  issuePriceMinor?: string; // e.g. original ₹3,119 issue price
  couponRatePercent?: number; // e.g. 2.5% p.a. paid semi-annually
  nextCouponDate?: string;
  maturityDate?: string;

  // Fixed Income / Loan attributes
  interestRatePercent?: number;
  institutionName?: string;
  accountNumberMasked?: string;
  monthlyEmiMinor?: string;

  lastPriceUpdated: string;
  isPriceStale: boolean;
  priceSource: string; // e.g. "NSE Live Close", "AMFI NAV", "RBI Gold Rate", "Self Valuation"
  fmv31Jan2018Minor?: string;
  // Annex C two-axis taxonomy. Optional: inferred when absent.
  exposureNode?: string;   // ExposureNode
  l2Node?: string;
  wrapper?: string;        // Wrapper
  /** Hybrid look-through. Percentages must sum to 100. */
  allocationSplit?: { exposure: string; percent: number }[];
  notes?: string;
  location?: string; // for Real Estate
  tag?: string; // e.g. "Retirement", "Child Higher Education", "Emergency", "Tax Saving", or Family Member tag
  tags?: string[]; // Array of tags for multi-tagging and goal filtering
}

export interface PortfolioSummary {
  totalAssetsMinor: bigint;
  totalLiabilitiesMinor: bigint;
  netWorthMinor: bigint;
  totalInvestedMinor: bigint;
  unrealisedGainMinor: bigint;
  unrealisedGainPercent: number;
  dayChangeMinor: bigint;
  dayChangePercent: number;
  totalDividendsInterestMinor: bigint;
  totalRealisedGainsMinor?: bigint;
  realisedGainErrors?: string[];
  xirrPercent: number | null;
  totalGainPercent: number;
}

export interface TaxGainItem {
  id: string;
  holdingId?: string;
  holdingName: string;
  category: AssetCategory;
  saleDate: string;
  purchaseDate: string;
  holdingDays: number;
  saleQuantity: number;
  saleAmountMinor: bigint;
  buyCostMinor: bigint;
  gainMinor: bigint;
  taxType: 'LTCG_EQUITY' | 'STCG_EQUITY' | 'DEBT_SLAB' | 'SGB_EXEMPT' | 'REAL_ESTATE_LTCG';
  applicableRate: string;
  estimatedTaxMinor: bigint;
}

export interface TaxSummary {
  financialYear: string;
  ltcgEquityTotalMinor: bigint;
  ltcgEquityExemptionMinor: bigint; // ₹1,25,000 exemption under Sec 112A
  ltcgEquityTaxableMinor: bigint;
  ltcgEquityTaxMinor: bigint; // 12.5%
  stcgEquityTotalMinor: bigint;
  stcgEquityTaxMinor: bigint; // 20%
  debtSlabGainsMinor: bigint;
  sgbExemptGainsMinor: bigint;
  dividendIncomeMinor: bigint;
  interestIncomeMinor: bigint;
  totalEstimatedTaxMinor: bigint;
  records: TaxGainItem[];
  errors?: string[];
}

export type StatementImportStatus = 'NEW' | 'ALREADY_EXISTS' | 'CONFLICT_OR_NEEDS_REVIEW';

export interface StatementImportRecord {
  id: string;
  date: string;
  holdingName: string;
  category: AssetCategory;
  action: 'BUY' | 'SELL' | 'DIVIDEND' | 'INTEREST';
  quantity: number;
  pricePerUnit: number;
  totalAmount: number;
  status: StatementImportStatus;
  statusMessage: string;
  selected: boolean;
  isin?: string;
  folio?: string;
  recordType?: 'HOLDING' | 'TRANSACTION';
  currentValuation?: number;
  cumulativeInvested?: number;
  dematAccount?: string;
}

export interface EmergencyDossierItem {
  assetName: string;
  categoryName: string;
  holderName: string;
  institution: string;
  identifier: string; // Folio, Demat BOID, PPF A/c, SGB Certificate
  estimatedValueMinor: bigint;
  offlineActionSteps: string;
}
