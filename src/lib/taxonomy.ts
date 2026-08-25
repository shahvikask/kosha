/**
 * Two-axis instrument taxonomy. Implements SRS Annex C.
 *
 * VERTICAL   (exposure) = what the money is exposed to
 * HORIZONTAL (wrapper)  = how that exposure is held
 *
 * These are orthogonal. Every combination exists in the Indian market:
 * equity held directly, through a fund, through a SIF, through NPS.
 * Neither axis may be nested inside the other.
 */
import { Holding, AssetCategory } from '../types';

export type ExposureNode =
  | 'EQUITY' | 'DEBT' | 'GOLD' | 'PROPERTY'
  | 'CASH' | 'PRIVATE' | 'PROTECTION' | 'LIABILITY';

export type Wrapper =
  | 'DIRECT' | 'MF' | 'INDEX_ETF' | 'SIF' | 'AIF' | 'PMS'
  | 'DEPOSIT' | 'SMALL_SAVINGS' | 'RETIREMENT' | 'INSURANCE' | 'PHYSICAL';

export interface ExposureMeta {
  id: ExposureNode;
  label: string;
  order: number;
  l2: { id: string; label: string }[];
}

/** L1 and L2, in display order. Annex C §C3. */
export const EXPOSURES: ExposureMeta[] = [
  { id: 'EQUITY', label: 'Equity', order: 1, l2: [
    { id: 'LARGE_CAP', label: 'Large cap' },
    { id: 'MID_CAP', label: 'Mid cap' },
    { id: 'SMALL_CAP', label: 'Small cap' },
    { id: 'MULTI_FLEXI', label: 'Multi & flexi cap' },
    { id: 'STYLE', label: 'Style' },
    { id: 'SECTOR', label: 'Sector & thematic' },
    { id: 'INTERNATIONAL', label: 'International' },
    { id: 'ELSS', label: 'Tax-saving (ELSS)' },
  ]},
  { id: 'DEBT', label: 'Debt & fixed income', order: 2, l2: [
    { id: 'GOVERNMENT', label: 'Government' },
    { id: 'CORPORATE', label: 'Corporate' },
    { id: 'DEPOSITS', label: 'Bank & company deposits' },
    { id: 'SMALL_SAVINGS', label: 'Small savings & provident' },
    { id: 'MONEY_MARKET', label: 'Money market & liquid' },
    { id: 'DURATION', label: 'Duration & dynamic' },
  ]},
  { id: 'GOLD', label: 'Gold & commodities', order: 3, l2: [
    { id: 'GOLD', label: 'Gold' },
    { id: 'SILVER', label: 'Silver' },
    { id: 'OTHER_COMMODITY', label: 'Other commodities' },
  ]},
  { id: 'PROPERTY', label: 'Property', order: 4, l2: [
    { id: 'RESIDENTIAL', label: 'Residential' },
    { id: 'COMMERCIAL', label: 'Commercial' },
    { id: 'LAND', label: 'Land' },
    { id: 'REIT', label: 'Listed property (REIT)' },
    { id: 'INVIT', label: 'Infrastructure (InvIT)' },
  ]},
  { id: 'CASH', label: 'Cash & liquid', order: 5, l2: [
    { id: 'BANK', label: 'Bank balances' },
    { id: 'LIQUID', label: 'Liquid & overnight' },
    { id: 'ARBITRAGE', label: 'Arbitrage' },
  ]},
  { id: 'PRIVATE', label: 'Private & unlisted', order: 6, l2: [
    { id: 'UNLISTED', label: 'Unlisted equity' },
    { id: 'ESOP', label: 'Employee shares' },
    { id: 'PRIVATE_CREDIT', label: 'Private credit' },
    { id: 'DIGITAL', label: 'Digital assets' },
  ]},
  { id: 'PROTECTION', label: 'Protection', order: 7, l2: [
    { id: 'PURE', label: 'Pure protection' },
    { id: 'SAVINGS_LINKED', label: 'Savings-linked policies' },
    { id: 'ANNUITY', label: 'Annuity' },
  ]},
  { id: 'LIABILITY', label: 'What you owe', order: 8, l2: [
    { id: 'HOME_LOAN', label: 'Home loans' },
    { id: 'AGAINST_ASSETS', label: 'Loans against assets' },
    { id: 'PERSONAL', label: 'Personal & other' },
    { id: 'CARDS', label: 'Cards' },
  ]},
];

/** The closed wrapper vocabulary. Annex C §C2. Identical under every exposure. */
export const WRAPPERS: { id: Wrapper; label: string; order: number }[] = [
  { id: 'DIRECT', label: 'Direct', order: 1 },
  { id: 'MF', label: 'Mutual fund', order: 2 },
  { id: 'INDEX_ETF', label: 'Index & ETF', order: 3 },
  { id: 'SIF', label: 'SIF', order: 4 },
  { id: 'AIF', label: 'AIF', order: 5 },
  { id: 'PMS', label: 'PMS', order: 6 },
  { id: 'DEPOSIT', label: 'Deposit', order: 7 },
  { id: 'SMALL_SAVINGS', label: 'Small savings', order: 8 },
  { id: 'RETIREMENT', label: 'Retirement', order: 9 },
  { id: 'INSURANCE', label: 'Insurance', order: 10 },
  { id: 'PHYSICAL', label: 'Physical', order: 11 },
];

export function exposureLabel(id: ExposureNode): string {
  return EXPOSURES.find(e => e.id === id)?.label ?? id;
}
export function l2Label(exposure: ExposureNode, l2: string): string {
  return EXPOSURES.find(e => e.id === exposure)?.l2.find(n => n.id === l2)?.label ?? l2;
}
export function wrapperLabel(id: Wrapper): string {
  return WRAPPERS.find(w => w.id === id)?.label ?? id;
}

export interface Placement {
  exposure: ExposureNode;
  l2: string;
  wrapper: Wrapper;
  /** true when inferred from name/category rather than explicitly set by the user */
  inferred: boolean;
}

const has = (s: string, ...words: string[]) => words.some(w => s.includes(w));

/** Wrapper inference. Annex C §C5.2: placement follows what it is, not who issued it. */
function inferWrapper(h: Holding, text: string): Wrapper {
  if (has(text, 'sif ', 'specialised investment', 'specialized investment')) return 'SIF';
  if (has(text, ' aif', 'alternative investment', 'category iii', 'cat iii')) return 'AIF';
  if (has(text, 'pms', 'portfolio management')) return 'PMS';
  if (has(text, 'ulip', 'endowment', 'money back', 'money-back', 'term plan',
    'term insurance', 'health insurance', 'annuity', 'policy')) return 'INSURANCE';
  if (has(text, 'nps', 'pension', 'provident', 'epf', 'vpf', 'gratuity',
    'superannuation')) return 'RETIREMENT';
  if (has(text, 'ppf', 'sukanya', 'nsc', 'kvp', 'kisan vikas', 'scss',
    'senior citizen', 'post office', 'mahila samman', 'small savings')) return 'SMALL_SAVINGS';
  if (has(text, 'etf', 'index fund', 'nifty', 'sensex', 'bees')) return 'INDEX_ETF';
  if (has(text, 'fixed deposit', 'term deposit', 'recurring deposit', ' fd ',
    'corporate deposit')) return 'DEPOSIT';
  if (has(text, 'physical', 'bullion', 'jewell', 'coin', 'apartment', 'plot',
    'land', 'villa', 'house', 'flat')) return 'PHYSICAL';
  if (h.category === 'MUTUAL_FUND' || has(text, 'fund', 'scheme')) return 'MF';
  return 'DIRECT';
}

/** Exposure and L2 inference. Annex C §C5.3: exposure wins over regulatory label. */
function inferExposure(h: Holding, text: string): { exposure: ExposureNode; l2: string } {
  const cat: AssetCategory = h.category;

  if (cat === 'LIABILITY') {
    if (has(text, 'home', 'housing', 'mortgage')) return { exposure: 'LIABILITY', l2: 'HOME_LOAN' };
    if (has(text, 'against', 'las', 'gold loan', 'margin')) return { exposure: 'LIABILITY', l2: 'AGAINST_ASSETS' };
    if (has(text, 'card')) return { exposure: 'LIABILITY', l2: 'CARDS' };
    return { exposure: 'LIABILITY', l2: 'PERSONAL' };
  }

  // Gold before debt: an SGB is a government security carrying GOLD exposure.
  if (cat === 'GOLD_SGB' || has(text, 'gold', 'sovereign gold', 'sgb')) {
    if (has(text, 'silver')) return { exposure: 'GOLD', l2: 'SILVER' };
    return { exposure: 'GOLD', l2: 'GOLD' };
  }
  if (has(text, 'silver')) return { exposure: 'GOLD', l2: 'SILVER' };

  if (cat === 'REAL_ESTATE' || has(text, 'apartment', 'residential', 'commercial',
    'plot', 'land', 'reit', 'invit', 'property')) {
    if (has(text, 'invit', 'infrastructure trust')) return { exposure: 'PROPERTY', l2: 'INVIT' };
    if (has(text, 'reit')) return { exposure: 'PROPERTY', l2: 'REIT' };
    if (has(text, 'commercial', 'office', 'shop')) return { exposure: 'PROPERTY', l2: 'COMMERCIAL' };
    if (has(text, 'plot', 'land', 'agricultur')) return { exposure: 'PROPERTY', l2: 'LAND' };
    return { exposure: 'PROPERTY', l2: 'RESIDENTIAL' };
  }

  if (has(text, 'ulip', 'endowment', 'money back', 'money-back', 'annuity',
    'term plan', 'term insurance', 'health insurance')) {
    if (has(text, 'annuity')) return { exposure: 'PROTECTION', l2: 'ANNUITY' };
    if (has(text, 'term plan', 'term insurance', 'health insurance'))
      return { exposure: 'PROTECTION', l2: 'PURE' };
    return { exposure: 'PROTECTION', l2: 'SAVINGS_LINKED' };
  }

  if (has(text, 'unlisted', 'pre-ipo', 'pre ipo', 'private placement'))
    return { exposure: 'PRIVATE', l2: 'UNLISTED' };
  if (has(text, 'esop', 'rsu', 'employee stock'))
    return { exposure: 'PRIVATE', l2: 'ESOP' };
  if (has(text, 'p2p', 'peer to peer', 'private credit', 'invoice discount'))
    return { exposure: 'PRIVATE', l2: 'PRIVATE_CREDIT' };
  if (has(text, 'crypto', 'bitcoin', 'ethereum', 'vda', 'digital asset'))
    return { exposure: 'PRIVATE', l2: 'DIGITAL' };

  if (cat === 'CASH_BANK') {
    if (has(text, 'liquid', 'overnight')) return { exposure: 'CASH', l2: 'LIQUID' };
    if (has(text, 'arbitrage')) return { exposure: 'CASH', l2: 'ARBITRAGE' };
    return { exposure: 'CASH', l2: 'BANK' };
  }
  if (has(text, 'arbitrage')) return { exposure: 'CASH', l2: 'ARBITRAGE' };
  if (has(text, 'liquid', 'overnight')) return { exposure: 'CASH', l2: 'LIQUID' };

  // Debt
  if (has(text, 'gilt', 'g-sec', 'gsec', 'government security', 'sdl',
    'treasury bill', 't-bill', 'state development'))
    return { exposure: 'DEBT', l2: 'GOVERNMENT' };
  if (has(text, 'ppf', 'sukanya', 'nsc', 'kvp', 'kisan vikas', 'scss',
    'senior citizen', 'post office', 'mahila samman', 'provident', 'epf',
    'vpf', 'small savings'))
    return { exposure: 'DEBT', l2: 'SMALL_SAVINGS' };
  if (has(text, 'nps', 'pension')) return { exposure: 'DEBT', l2: 'SMALL_SAVINGS' };
  if (has(text, 'fixed deposit', 'term deposit', 'recurring deposit', 'corporate deposit'))
    return { exposure: 'DEBT', l2: 'DEPOSITS' };
  if (has(text, 'ncd', 'debenture', 'corporate bond', 'credit risk', 'banking and psu'))
    return { exposure: 'DEBT', l2: 'CORPORATE' };
  if (has(text, 'duration', 'dynamic bond', 'bond fund', 'income fund'))
    return { exposure: 'DEBT', l2: 'DURATION' };
  if (has(text, 'money market', 'ultra short', 'low duration', 'short duration'))
    return { exposure: 'DEBT', l2: 'MONEY_MARKET' };
  if (cat === 'FIXED_INCOME') return { exposure: 'DEBT', l2: 'DEPOSITS' };
  if (has(text, ' debt')) return { exposure: 'DEBT', l2: 'DURATION' };

  // Equity
  if (has(text, 'elss', 'tax saver', 'tax saving')) return { exposure: 'EQUITY', l2: 'ELSS' };
  if (has(text, 'international', 'us equity', 'nasdaq', 's&p 500', 'global', 'overseas'))
    return { exposure: 'EQUITY', l2: 'INTERNATIONAL' };
  // Hybrids span capitalisations by mandate, so they belong in multi/flexi,
  // never in a single cap band. Their value is still split by look-through.
  if (has(text, 'flexi cap', 'multi cap', 'multicap', 'flexicap', 'large & mid',
    'large and mid', 'balanced advantage', 'dynamic asset allocation',
    'aggressive hybrid', 'conservative hybrid', 'balanced hybrid',
    'multi asset', 'multi-asset', 'equity savings', 'hybrid'))
    return { exposure: 'EQUITY', l2: 'MULTI_FLEXI' };
  if (has(text, 'small cap', 'smallcap')) return { exposure: 'EQUITY', l2: 'SMALL_CAP' };
  if (has(text, 'mid cap', 'midcap')) return { exposure: 'EQUITY', l2: 'MID_CAP' };
  if (has(text, 'value', 'contra', 'focused', 'dividend yield'))
    return { exposure: 'EQUITY', l2: 'STYLE' };
  if (has(text, 'sector', 'thematic', 'pharma', 'banking', 'infra', 'technology',
    'capital goods', ' it ', 'psu', 'consumption', 'energy'))
    return { exposure: 'EQUITY', l2: 'SECTOR' };
  if (has(text, 'large cap', 'largecap', 'bluechip', 'top 100', 'nifty 50', 'sensex'))
    return { exposure: 'EQUITY', l2: 'LARGE_CAP' };

  return { exposure: 'EQUITY', l2: 'LARGE_CAP' };
}

/**
 * Place a holding on both axes. An explicit user setting always wins;
 * inference is a fallback and is flagged so the interface can show it.
 */
export function classifyHolding(h: Holding): Placement {
  if (h.exposureNode && h.l2Node && h.wrapper) {
    return {
      exposure: h.exposureNode as ExposureNode,
      l2: h.l2Node,
      wrapper: h.wrapper as Wrapper,
      inferred: false,
    };
  }
  const text = `${h.name} ${h.subCategory} ${h.symbolOrFolio ?? ''}`.toLowerCase();
  const { exposure, l2 } = inferExposure(h, text);
  const wrapper = inferWrapper(h, text);
  return { exposure, l2, wrapper, inferred: true };
}
