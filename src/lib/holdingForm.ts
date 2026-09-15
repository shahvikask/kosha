/**
 * Subcategory-driven Add Holding fields for Indian instruments.
 * Equities and funds keep the listed picker; this module also covers
 * gold, govt schemes, property, alternatives, bank cash and loans.
 */
import { AssetCategory, Holding, HoldingDetails, MfHeldAs, MfOption, MfPlan } from '../types';
import { addCalendarMonths } from './emi';

export function usesUnits(
  category: AssetCategory,
  subCategory?: string,
  details?: HoldingDetails | null
): boolean {
  if (category === 'EQUITY' || category === 'MUTUAL_FUND' || category === 'GOLD_SGB') return true;
  const sub = (subCategory || '').toLowerCase();
  if (category === 'REAL_ESTATE') {
    if (!isReitLike(subCategory)) return false;
    return details?.altHoldMode !== 'LUMPSUM';
  }
  if (category === 'SPECIALISED') {
    if (/unlisted|esop/.test(sub)) return true;
    if (isAifSub(subCategory)) return details?.altHoldMode === 'DEMAT';
    return false;
  }
  if (category === 'FIXED_INCOME') return /bond|goi/.test(sub);
  return false;
}

export function isReitLike(subCategory?: string): boolean {
  return /\breit\b|\binvit\b/i.test(subCategory || '');
}

export function isAifSub(subCategory?: string): boolean {
  return /\baif\b/i.test(subCategory || '');
}

export function nameLooksLikeReit(name?: string): boolean {
  return /\bREITs?\b|REAL ESTATE INVESTMENT TRUST|REAL ESTATE TRUST/i.test(name || '');
}

export function nameLooksLikeInvit(name?: string): boolean {
  return /\bINVITs?\b|INFRASTRUCTURE INVESTMENT TRUST/i.test(name || '');
}

export function nameLooksLikeAif(name?: string): boolean {
  return /\bAIF\b|ALTERNATIVE INVESTMENT|DISCOVERY FUND|CATEGORY III|\bCAT\s*III\b/i.test(name || '');
}

export function recategorizeCasAsset(
  name: string,
  category: AssetCategory,
  schemeType?: string | null
): AssetCategory {
  const st = (schemeType || '').toUpperCase();
  if (nameLooksLikeSgbName(name)) return 'GOLD_SGB';
  if (nameLooksLikeReit(name) || nameLooksLikeInvit(name) || st === 'REIT' || st === 'INVIT') {
    return 'REAL_ESTATE';
  }
  if (st === 'AIF' || nameLooksLikeAif(name)) return 'SPECIALISED';
  return category;
}

function nameLooksLikeSgbName(name?: string): boolean {
  const n = (name || '').toUpperCase();
  return /\bSGB\b/.test(n) || n.includes('SOVEREIGN GOLD');
}

export function casHoldingSubCategory(
  category: AssetCategory,
  name: string,
  schemeType?: string | null
): string {
  const n = name || '';
  const st = schemeType || '';
  if (category === 'REAL_ESTATE') return nameLooksLikeInvit(n) || st.toUpperCase() === 'INVIT' ? 'InvIT' : 'REIT';
  if (category === 'SPECIALISED' || st.toUpperCase() === 'AIF') return 'AIF';
  if (category === 'GOLD_SGB') return 'Sovereign Gold Bond';
  if (category === 'FIXED_INCOME') {
    if (/nps/i.test(n) || st.toUpperCase() === 'NPS') return 'NPS';
    return 'Other';
  }
  if (category === 'EQUITY') return 'Other';
  if (category === 'MUTUAL_FUND') {
    if (nameLooksLikeEtf(n) || st.toUpperCase() === 'ETF') return 'Index / ETF';
    return 'Other';
  }
  return 'Other';
}

export function casImportDetails(opts: {
  category: AssetCategory;
  casFileType?: string | null;
}): HoldingDetails | undefined {
  if (opts.category !== 'REAL_ESTATE' && opts.category !== 'SPECIALISED') return undefined;
  const d: HoldingDetails = { altHoldMode: 'DEMAT' };
  const ft = (opts.casFileType || '').toUpperCase();
  if (ft === 'NSDL') d.depository = 'NSDL';
  else if (ft === 'CDSL') d.depository = 'CDSL';
  return d;
}

export function isElssSub(subCategory?: string): boolean {
  return /elss/i.test(subCategory || '');
}

export function isIndexOrEtfSub(subCategory?: string): boolean {
  return /index|etf/i.test(subCategory || '');
}

export function nameLooksLikeEtf(name?: string): boolean {
  return /\bETF\b/i.test(name || '');
}

export function identifierLabel(
  category: AssetCategory,
  subCategory?: string,
  heldAs?: MfHeldAs,
  details?: HoldingDetails | null
): string {
  const sub = subCategory || '';
  if (category === 'EQUITY') return 'NSE/BSE Symbol';
  if (category === 'MUTUAL_FUND') {
    if (heldAs === 'DEMAT') return isIndexOrEtfSub(sub) ? 'NSE/BSE Symbol' : 'Folio (optional)';
    if (heldAs === 'FOLIO') return 'Folio Number';
    if (isIndexOrEtfSub(sub)) return 'Symbol or folio';
    return 'Folio Number';
  }
  if (category === 'GOLD_SGB') {
    if (sub.startsWith('Physical')) return 'Invoice / hallmark no.';
    if (sub.startsWith('Digital')) return 'Provider account (optional)';
    return 'SGB series';
  }
  if (category === 'FIXED_INCOME') {
    if (sub === 'PPF') return 'PPF account number';
    if (sub === 'EPF') return 'UAN (12 digits)';
    if (sub === 'NPS') return 'PRAN (12 digits)';
    if (sub.startsWith('Sukanya')) return 'SSY account number';
    if (sub === 'Bank FD') return 'Deposit receipt number';
    if (sub.startsWith('Bond')) return 'ISIN';
    return 'Account number';
  }
  if (category === 'REAL_ESTATE') {
    if (isReitLike(sub)) {
      return details?.altHoldMode === 'LUMPSUM' ? 'Agreement / folio (optional)' : 'ISIN';
    }
    return 'Survey / door number (optional)';
  }
  if (category === 'SPECIALISED') {
    if (sub === 'PMS') return 'PMS client code';
    if (isAifSub(sub)) {
      return details?.altHoldMode === 'DEMAT' ? 'ISIN' : 'Scheme / folio';
    }
    if (/unlisted|esop/i.test(sub)) return 'Share certificate / folio';
    return 'Account number';
  }
  if (category === 'CASH_BANK') {
    if (/liquid/i.test(sub)) return 'Folio (if a liquid fund)';
    return 'Account number';
  }
  if (category === 'LIABILITY') return 'Loan account number';
  return 'Account number';
}

export function namePlaceholder(category: AssetCategory, subCategory?: string): string {
  const sub = subCategory || '';
  if (category === 'EQUITY') return 'e.g. State Bank of India';
  if (category === 'MUTUAL_FUND') {
    if (isIndexOrEtfSub(sub)) return 'e.g. Nippon India ETF Nifty BeES';
    if (isElssSub(sub)) return 'e.g. Axis ELSS Tax Saver';
    return 'e.g. Parag Parikh Flexi Cap';
  }
  if (category === 'GOLD_SGB') {
    if (sub.startsWith('Physical')) return 'e.g. 24K jewellery / coins';
    if (sub.startsWith('Digital')) return 'e.g. SafeGold vault';
    return 'e.g. SGB 2020-21 Series VI';
  }
  if (category === 'FIXED_INCOME') {
    if (sub === 'PPF') return 'e.g. SBI PPF';
    if (sub === 'EPF') return 'e.g. EPFO — current employer';
    if (sub === 'NPS') return 'e.g. NPS Tier I';
    if (sub.startsWith('Sukanya')) return 'e.g. SSY — daughter';
    if (sub === 'Bank FD') return 'e.g. HDFC 3-year FD';
    if (sub.startsWith('Bond')) return 'e.g. 7.10% GS 2034';
    return 'e.g. SBI PPF';
  }
  if (category === 'REAL_ESTATE') {
    if (/\binvit\b/i.test(sub)) return 'e.g. IndiGrid InvIT';
    if (/\breit\b/i.test(sub)) return 'e.g. Embassy Office Parks REIT';
    return 'e.g. 2BHK Flat Indiranagar';
  }
  if (category === 'LIABILITY') return 'e.g. HDFC Home Loan';
  if (category === 'CASH_BANK') {
    if (/liquid/i.test(sub)) return 'e.g. HDFC Liquid — Direct Growth';
    return 'e.g. HDFC Savings';
  }
  if (sub === 'PMS') return 'e.g. Ask PMS — Indian Entrepreneur';
  if (sub === 'AIF') return 'e.g. Blume Ventures Fund IV';
  if (/esop/i.test(sub)) return 'e.g. Company ESOPs';
  if (/unlisted/i.test(sub)) return 'e.g. Unlisted shares — ABC Pvt Ltd';
  return 'e.g. PMS mandate';
}

export function acquiredOnLabel(category: AssetCategory, subCategory?: string, details?: HoldingDetails | null): string {
  if (category === 'EQUITY' || category === 'MUTUAL_FUND') return 'Trade date';
  if (category === 'LIABILITY') return 'Loan start date';
  if (category === 'REAL_ESTATE') {
    if (isReitLike(subCategory) && details?.altHoldMode !== 'LUMPSUM') return 'Trade date';
    return 'Purchase date';
  }
  if (category === 'CASH_BANK' || category === 'FIXED_INCOME') {
    if (/bond|nps|fd/i.test(subCategory || '')) return 'Bought on / opened on';
    return 'Balance as of';
  }
  if (category === 'SPECIALISED' && isAifSub(subCategory) && details?.altHoldMode === 'DEMAT') return 'Trade date';
  if (category === 'SPECIALISED' && /pms|aif/i.test(subCategory || '')) return 'Incept / first close';
  return 'Bought on';
}

export function defaultSgbCoupon(): number {
  return 2.5;
}

export function suggestLockInDates(
  category: AssetCategory,
  subCategory: string,
  acquiredOn: string
): { maturity?: string; earlyExit?: string } {
  if (!acquiredOn) return {};
  if (category === 'GOLD_SGB' && subCategory.startsWith('Sovereign')) {
    return {
      maturity: addCalendarMonths(acquiredOn, 96),
      earlyExit: addCalendarMonths(acquiredOn, 60),
    };
  }
  if (category === 'FIXED_INCOME' && subCategory === 'PPF') {
    return { maturity: addCalendarMonths(acquiredOn, 180) };
  }
  if (category === 'FIXED_INCOME' && subCategory.startsWith('Sukanya')) {
    return { maturity: addCalendarMonths(acquiredOn, 252) };
  }
  if (category === 'MUTUAL_FUND' && isElssSub(subCategory)) {
    return { maturity: addCalendarMonths(acquiredOn, 36) };
  }
  return {};
}

export function looksLikeUan(raw: string): boolean {
  return /^\d{12}$/.test(raw.replace(/\s/g, ''));
}

export function looksLikePran(raw: string): boolean {
  return /^\d{12}$/.test(raw.replace(/\s/g, ''));
}

export function looksLikeIsin(raw: string): boolean {
  return /^[A-Z]{2}[A-Z0-9]{9}[0-9]$/i.test(raw.trim());
}

/** NSDL DP ID: IN + 6 digits (e.g. IN300214). */
export function looksLikeNsdlDpId(raw: string): boolean {
  return /^IN\d{6}$/i.test(raw.replace(/\s/g, ''));
}

/** NSDL Client ID: 8 digits. */
export function looksLikeNsdlClientId(raw: string): boolean {
  return /^\d{8}$/.test(raw.replace(/\s/g, ''));
}

/** CDSL BOID: 16 digits. */
export function looksLikeBoid(raw: string): boolean {
  return /^\d{16}$/.test(raw.replace(/\s/g, ''));
}

export function inferMfPlan(name?: string): MfPlan | undefined {
  const n = (name || '').toUpperCase();
  if (/\bDIRECT\b/.test(n)) return 'DIRECT';
  if (/\bREGULAR\b/.test(n)) return 'REGULAR';
  return undefined;
}

export function inferMfOption(name?: string): MfOption | undefined {
  const n = (name || '').toUpperCase();
  if (/\bIDCW\b/.test(n) || /\bDIVIDEND\b/.test(n)) return 'IDCW';
  if (/\bGROWTH\b/.test(n)) return 'GROWTH';
  return undefined;
}

export function dematAccountMask(d: HoldingDetails): string | undefined {
  const bo = (d.boId || '').replace(/\s/g, '');
  if (bo) return bo;
  const dp = (d.dpId || '').replace(/\s/g, '').toUpperCase();
  const client = (d.clientId || '').replace(/\s/g, '');
  if (dp && client) return `${dp} ${client}`;
  return undefined;
}

export function detailsLines(h: Holding): { label: string; value: string }[] {
  const d: HoldingDetails = h.details || {};
  const out: { label: string; value: string }[] = [];
  const rupees = (minor?: string) => {
    if (!minor) return '';
    const n = Number(minor) / 10_000;
    if (!Number.isFinite(n)) return '';
    return `₹ ${n.toLocaleString('en-IN', { maximumFractionDigits: 0 })}`;
  };
  if (h.acquiredOn) out.push({ label: 'From', value: h.acquiredOn });
  if (d.altHoldMode) {
    out.push({
      label: 'Held as',
      value: d.altHoldMode === 'LUMPSUM' ? 'Direct lumpsum' : 'Demat (CAS)',
    });
  }
  if (d.mfPlan) out.push({ label: 'Plan', value: d.mfPlan === 'DIRECT' ? 'Direct' : 'Regular' });
  if (d.mfOption) out.push({ label: 'Option', value: d.mfOption === 'IDCW' ? 'IDCW' : 'Growth' });
  if (d.mfHeldAs) out.push({ label: 'Held as', value: d.mfHeldAs === 'DEMAT' ? 'Demat' : 'SOA folio' });
  if (d.dpId) out.push({ label: 'DP ID', value: d.dpId.toUpperCase() });
  if (d.clientId) out.push({ label: 'Client ID', value: d.clientId });
  if (d.boId) out.push({ label: 'BOID', value: d.boId });
  if (d.goldHoldMode) out.push({ label: 'Held as', value: d.goldHoldMode === 'DEMAT' ? 'Demat' : 'RBI / bank certificate' });
  if (d.goldPurity) out.push({ label: 'Purity', value: d.goldPurity });
  if (d.goldHallmark) out.push({ label: 'Hallmark / invoice', value: d.goldHallmark });
  if (d.earlyExitDate) out.push({ label: 'First exit (after year 5)', value: d.earlyExitDate });
  if (d.annualContributionMinor) out.push({ label: 'Yearly contribution', value: rupees(d.annualContributionMinor) });
  if (d.npsTier) out.push({ label: 'NPS tier', value: `Tier ${d.npsTier}` });
  if (d.npsCra) out.push({ label: 'CRA', value: d.npsCra === 'PROT' ? 'Protean' : d.npsCra });
  if (d.npsPfm) out.push({ label: 'Pension fund', value: d.npsPfm });
  if (d.compounding) out.push({ label: 'Compounding', value: d.compounding.toLowerCase() });
  if (d.tdsApplicable) out.push({ label: 'TDS', value: 'Yes (Form 16A / 26AS)' });
  if (d.faceValueMinor) out.push({ label: 'Face value', value: rupees(d.faceValueMinor) });
  if (d.areaSqFt) out.push({ label: 'Area', value: `${d.areaSqFt} sq ft` });
  if (d.ownership) {
    const share = d.ownershipPercent != null ? ` (${d.ownershipPercent}%)` : '';
    const label =
      d.ownership === 'JOINT' ? 'Joint' :
      d.ownership === 'HUF' ? 'HUF' :
      d.ownership === 'SPOUSE' ? 'Spouse' : 'Self';
    out.push({ label: 'Ownership', value: label + share });
  }
  if (d.aifCategory) out.push({ label: 'AIF category', value: `Category ${d.aifCategory}` });
  if (d.commitmentMinor) out.push({ label: 'Commitment', value: rupees(d.commitmentMinor) });
  if (d.capitalCalledMinor) out.push({ label: 'Capital called', value: rupees(d.capitalCalledMinor) });
  if (d.strategyName) out.push({ label: 'Strategy', value: d.strategyName });
  if (d.custodian) out.push({ label: 'Custodian', value: d.custodian });
  if (d.ifsc) out.push({ label: 'IFSC', value: d.ifsc.toUpperCase() });
  if (d.rateType) out.push({ label: 'Rate', value: d.rateType === 'FLOATING' ? 'Floating' : 'Fixed' });
  if (d.originalTenorMonths) out.push({ label: 'Original tenor', value: `${d.originalTenorMonths} months` });
  return out;
}

export function isEmptyDetails(d?: HoldingDetails | null): boolean {
  if (!d) return true;
  return Object.values(d).every(v => v === undefined || v === '' || v === null);
}
