import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import {
  ASSET_CATEGORIES,
  ASSET_SUBCATEGORIES,
  AssetCategory,
  HoldingDetails,
  ListedSecurity,
} from '../../types';
import { MoneyEngine } from '../../lib/money';
import { addCalendarMonths } from '../../lib/emi';
import { searchListed } from '../../lib/eodPrices';
import { formatStatementDate } from '../../lib/casPython';
import {
  acquiredOnLabel,
  defaultSgbCoupon,
  dematAccountMask,
  identifierLabel,
  inferMfOption,
  inferMfPlan,
  isElssSub,
  isIndexOrEtfSub,
  nameLooksLikeEtf,
  namePlaceholder,
  suggestLockInDates,
  usesUnits,
} from '../../lib/holdingForm';
import { X, Tag, Target, Check } from 'lucide-react';

interface AddHoldingModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const COMMON_GOAL_TAGS = [
  'Retirement',
  'Child Higher Education',
  'Emergency Fund',
  'Wealth Compounding',
  'House Purchase',
  'Tax Saving (80C)',
  'Vacation / Travel',
];

const inp =
  'w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50';
const lbl = 'text-xs font-bold text-slate-300 block mb-1';
const panel = 'grid grid-cols-1 sm:grid-cols-2 gap-3 p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800';

function defaultNextEmiDate(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const fifth = `${y}-${m}-05`;
  const today = `${y}-${m}-${String(now.getDate()).padStart(2, '0')}`;
  return fifth > today ? fifth : addCalendarMonths(fifth, 1);
}

function rupeesToMinor(raw: string): string | undefined {
  const n = parseFloat(raw);
  if (!raw.trim() || isNaN(n) || n < 0) return undefined;
  return MoneyEngine.fromRupees(n).toString();
}

export const AddHoldingModal: React.FC<AddHoldingModalProps> = ({ isOpen, onClose }) => {
  const {
    familyMembers,
    addHolding,
    selectedMemberId,
    goals,
    listedSecurities,
    refreshEodPrices,
    pricesRefreshing,
    pricesAsOf,
    pricesError,
    holdings,
  } = useKosha();

  const [category, setCategory] = useState<AssetCategory>('EQUITY');
  const [name, setName] = useState('');
  const [symbolOrFolio, setSymbolOrFolio] = useState('');
  const [isinManual, setIsinManual] = useState('');
  const [picked, setPicked] = useState<ListedSecurity | null>(null);
  const [searchQ, setSearchQ] = useState('');
  const [subCategory, setSubCategory] = useState(ASSET_SUBCATEGORIES.EQUITY[0]);
  const [ownerId, setOwnerId] = useState(
    selectedMemberId === 'ALL' ? familyMembers[0]?.id || 'fam_1' : selectedMemberId
  );
  const [quantity, setQuantity] = useState('');
  const [costPerUnitRupees, setCostPerUnitRupees] = useState('');
  const [currentPriceRupees, setCurrentPriceRupees] = useState('');
  const [institutionName, setInstitutionName] = useState('');
  const [notes, setNotes] = useState('');
  const [tagInput, setTagInput] = useState('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [couponOrInterestRate, setCouponOrInterestRate] = useState('');
  const [maturityDate, setMaturityDate] = useState('');
  const [location, setLocation] = useState('');
  const [issuePriceRupees, setIssuePriceRupees] = useState('');
  const [monthlyEmiRupees, setMonthlyEmiRupees] = useState('');
  const [remainingEmis, setRemainingEmis] = useState('');
  const [nextEmiDate, setNextEmiDate] = useState('');
  const [acquiredOn, setAcquiredOn] = useState('');
  const [fyContributionRupees, setFyContributionRupees] = useState('');
  const [commitmentRupees, setCommitmentRupees] = useState('');
  const [capitalCalledRupees, setCapitalCalledRupees] = useState('');
  const [faceValueRupees, setFaceValueRupees] = useState('');
  const [extra, setExtra] = useState<HoldingDetails>({});
  const [listedExchange, setListedExchange] = useState('');
  const [rta, setRta] = useState('');
  const [chargesRupees, setChargesRupees] = useState('');

  const listedPicker = category === 'EQUITY' || category === 'MUTUAL_FUND';
  const unitAsset = usesUnits(category, subCategory);
  const isAif = category === 'SPECIALISED' && subCategory === 'AIF';
  const isPms = category === 'SPECIALISED' && subCategory === 'PMS';
  const isEtf = category === 'MUTUAL_FUND' && (nameLooksLikeEtf(name) || nameLooksLikeEtf(picked?.name));
  const heldAsDemat = extra.mfHeldAs === 'DEMAT' || isEtf;
  const showAcquiredOn = true;
  const showDematIds = category === 'EQUITY' || heldAsDemat;
  const showExchange = category === 'EQUITY' || isEtf || (category === 'MUTUAL_FUND' && heldAsDemat && isIndexOrEtfSub(subCategory));
  const matches = useMemo(
    () => (listedPicker ? searchListed(listedSecurities, searchQ, category, 20) : []),
    [listedPicker, listedSecurities, searchQ, category],
  );
  const existingSameIsin = useMemo(() => {
    if (!picked?.isin) return undefined;
    return holdings.find(
      h => h.ownerId === ownerId && (h.isin || '').toUpperCase() === picked.isin.toUpperCase()
    );
  }, [picked, holdings, ownerId]);

  const linkable = holdings.filter(h => {
    if (h.ownerId !== ownerId) return false;
    if (category === 'LIABILITY') return h.category === 'REAL_ESTATE' && !/reit/i.test(h.subCategory);
    if (category === 'REAL_ESTATE' && !/reit/i.test(subCategory)) return h.category === 'LIABILITY';
    return false;
  });

  const patchExtra = (p: Partial<HoldingDetails>) => setExtra(prev => ({ ...prev, ...p }));

  if (!isOpen) return null;

  const handleAddCustomTag = (tagToAdd?: string) => {
    const raw = tagToAdd || tagInput;
    if (!raw.trim()) return;
    const parts = raw.split(',').map(t => t.trim()).filter(t => t.length > 0);
    const updated = [...selectedTags];
    parts.forEach(p => {
      if (!updated.includes(p)) updated.push(p);
    });
    setSelectedTags(updated);
    if (!tagToAdd) setTagInput('');
  };

  const handleToggleTag = (tag: string) => {
    setSelectedTags(selectedTags.includes(tag) ? selectedTags.filter(t => t !== tag) : [...selectedTags, tag]);
  };

  const applySubDefaults = (cat: AssetCategory, sub: string) => {
    if (cat === 'GOLD_SGB' && sub.startsWith('Sovereign')) {
      setCouponOrInterestRate(String(defaultSgbCoupon()));
    } else if (cat === 'GOLD_SGB') {
      setCouponOrInterestRate('');
      setIssuePriceRupees('');
    }
  };

  const handleCategory = (next: AssetCategory) => {
    setCategory(next);
    const sub = ASSET_SUBCATEGORIES[next][0];
    setSubCategory(sub);
    setPicked(null);
    setSearchQ('');
    setName('');
    setSymbolOrFolio('');
    setIsinManual('');
    setCurrentPriceRupees('');
    setQuantity('');
    setCostPerUnitRupees('');
    setInstitutionName('');
    setLocation('');
    setMaturityDate('');
    setAcquiredOn('');
    setExtra({});
    setFyContributionRupees('');
    setCommitmentRupees('');
    setCapitalCalledRupees('');
    setFaceValueRupees('');
    setListedExchange('');
    setRta('');
    setChargesRupees('');
    applySubDefaults(next, sub);
  };

  const handleSubCategory = (sub: string) => {
    setSubCategory(sub);
    applySubDefaults(category, sub);
    if (acquiredOn) {
      const suggested = suggestLockInDates(category, sub, acquiredOn);
      if (suggested.maturity && !maturityDate) setMaturityDate(suggested.maturity);
      if (suggested.earlyExit && !extra.earlyExitDate) patchExtra({ earlyExitDate: suggested.earlyExit });
    }
  };

  const handleAcquiredOn = (iso: string) => {
    setAcquiredOn(iso);
    const suggested = suggestLockInDates(category, subCategory, iso);
    if (suggested.maturity) setMaturityDate(prev => prev || suggested.maturity!);
    if (suggested.earlyExit) {
      setExtra(prev => ({ ...prev, earlyExitDate: prev.earlyExitDate || suggested.earlyExit }));
    }
  };

  const tickerFromPick = (row: ListedSecurity, exch: string) => {
    if (exch === 'BSE') return row.bseSymbol || row.symbol || row.nseSymbol || '';
    return row.nseSymbol || row.symbol || row.bseSymbol || '';
  };

  const applyExchange = (ex: string) => {
    setListedExchange(ex);
    if (picked && (category === 'EQUITY' || heldAsDemat)) {
      const sym = tickerFromPick(picked, ex);
      if (sym) setSymbolOrFolio(sym);
    }
  };

  const pickSecurity = (row: ListedSecurity) => {
    setPicked(row);
    setName(row.name);
    const exch = row.nseSymbol ? 'NSE' : row.bseSymbol ? 'BSE' : '';
    setListedExchange(exch);
    if (category === 'EQUITY') {
      setSymbolOrFolio(tickerFromPick(row, exch) || '');
    } else {
      const etf = nameLooksLikeEtf(row.name);
      setSymbolOrFolio(etf ? tickerFromPick(row, exch) || row.amfiCode || '' : '');
      const plan = inferMfPlan(row.name);
      const option = inferMfOption(row.name);
      setExtra(prev => ({
        ...prev,
        mfPlan: plan || prev.mfPlan,
        mfOption: option || prev.mfOption,
        mfHeldAs: etf ? 'DEMAT' : (prev.mfHeldAs || 'FOLIO'),
      }));
    }
    setCurrentPriceRupees(MoneyEngine.toRupees(BigInt(row.closeMinor)).toString());
    setSearchQ('');
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (listedPicker && !picked) return;
    if (!name.trim()) return;

    const qtyNum = unitAsset ? parseFloat(quantity) || 0 : 1;
    if (unitAsset && qtyNum <= 0) return;

    let costNum = parseFloat(costPerUnitRupees) || 0;
    let currentPriceNum = parseFloat(currentPriceRupees) || costNum;
    if (isAif) {
      const called = parseFloat(capitalCalledRupees);
      const commit = parseFloat(commitmentRupees);
      if (!costNum) costNum = (!isNaN(called) && called > 0 ? called : (!isNaN(commit) ? commit : 0));
      if (!currentPriceNum) currentPriceNum = costNum;
    }
    if (costNum < 0 || currentPriceNum < 0) return;

    const qtyScaled = MoneyEngine.fromQty(qtyNum).toString();
    const costMinor = MoneyEngine.fromRupees(costNum).toString();
    const currentPriceMinor = MoneyEngine.fromRupees(currentPriceNum).toString();

    const finalTags = [...selectedTags];
    if (tagInput.trim()) {
      tagInput.split(',').map(t => t.trim()).filter(Boolean).forEach(t => {
        if (!finalTags.includes(t)) finalTags.push(t);
      });
    }

    const emiNum = parseFloat(monthlyEmiRupees) || 0;
    const remainNum = parseInt(remainingEmis, 10);
    const hasEmi = category === 'LIABILITY' && emiNum > 0 && remainNum > 0;
    const nextEmi = hasEmi ? (nextEmiDate || defaultNextEmiDate()) : undefined;
    const issueNum = parseFloat(issuePriceRupees);
    const isin = (picked?.isin || isinManual.trim() || (subCategory.startsWith('Bond') ? symbolOrFolio.trim() : '') || '').toUpperCase() || undefined;

    const details: HoldingDetails = { ...extra };
    const fy = rupeesToMinor(fyContributionRupees);
    if (fy) details.annualContributionMinor = fy;
    const commit = rupeesToMinor(commitmentRupees);
    if (commit) details.commitmentMinor = commit;
    const called = rupeesToMinor(capitalCalledRupees);
    if (called) details.capitalCalledMinor = called;
    const face = rupeesToMinor(faceValueRupees);
    if (face) details.faceValueMinor = face;
    if (category === 'GOLD_SGB' && subCategory.startsWith('Physical') && !details.goldPurity) {
      details.goldPurity = '24K';
    }
    if (category === 'FIXED_INCOME' && subCategory === 'NPS' && !details.npsTier) {
      details.npsTier = 'I';
    }
    if (category === 'LIABILITY' && !details.originalTenorMonths && remainNum > 0) {
      details.originalTenorMonths = remainNum;
    }
    if (isEtf && !details.mfHeldAs) details.mfHeldAs = 'DEMAT';
    if (details.dpId) details.dpId = details.dpId.replace(/\s/g, '').toUpperCase();
    if (details.clientId) details.clientId = details.clientId.replace(/\s/g, '');
    if (details.boId) details.boId = details.boId.replace(/\s/g, '');
    (Object.keys(details) as (keyof HoldingDetails)[]).forEach(k => {
      const v = details[k];
      if (v === undefined || v === '' || v === null) delete details[k];
    });

    const sgb = category === 'GOLD_SGB' && subCategory.startsWith('Sovereign');
    const couponNum = parseFloat(couponOrInterestRate);
    const chargesNum = parseFloat(chargesRupees);
    const dematMask = dematAccountMask(details);

    addHolding({
      name: name.trim(),
      symbolOrFolio: symbolOrFolio.trim() || name.substring(0, 8).toUpperCase(),
      isin: isin && isin.length >= 12 ? isin : undefined,
      category,
      subCategory: subCategory.trim() || ASSET_CATEGORIES[category].name,
      ownerId,
      quantityScaled: qtyScaled,
      averageCostPerUnitMinor: costMinor,
      currentPriceMinor,
      priceSource: picked?.source || 'Manual Initial Entry',
      amfiCode: picked?.amfiCode,
      institutionName: institutionName.trim() || extra.custodian || undefined,
      accountNumberMasked: dematMask || symbolOrFolio.trim() || undefined,
      notes: notes.trim() || undefined,
      tag: finalTags[0],
      tags: finalTags.length > 0 ? finalTags : undefined,
      couponRatePercent: sgb ? (isNaN(couponNum) ? defaultSgbCoupon() : couponNum) : undefined,
      interestRatePercent:
        (category === 'FIXED_INCOME' || category === 'LIABILITY') && couponOrInterestRate
          ? parseFloat(couponOrInterestRate)
          : undefined,
      maturityDate: maturityDate || undefined,
      location: location.trim() || undefined,
      issuePriceMinor: !isNaN(issueNum) && issueNum > 0 ? MoneyEngine.fromRupees(issueNum).toString() : undefined,
      monthlyEmiMinor: hasEmi ? MoneyEngine.fromRupees(emiNum).toString() : undefined,
      remainingEmiCount: hasEmi ? remainNum : undefined,
      nextCouponDate:
        category === 'LIABILITY'
          ? nextEmi
          : sgb && nextEmiDate
            ? nextEmiDate
            : undefined,
      emiStatus: hasEmi ? 'PAYING' : undefined,
      acquiredOn: acquiredOn || undefined,
      details: Object.keys(details).length ? details : undefined,
      exchange: listedExchange || undefined,
      rta: rta || undefined,
      amc: category === 'MUTUAL_FUND' ? (institutionName.trim() || undefined) : undefined,
      dpName: extra.depository || undefined,
      chargesMinor: !isNaN(chargesNum) && chargesNum > 0 ? MoneyEngine.fromRupees(chargesNum).toString() : undefined,
    });

    onClose();
  };

  const qtyLabel =
    category === 'GOLD_SGB' ? 'Quantity (grams) *'
    : category === 'EQUITY' || /unlisted|esop/i.test(subCategory) ? 'Shares *'
    : isEtf ? 'Units *'
    : 'Units *';
  const buyLabel =
    category === 'EQUITY' || /unlisted|esop/i.test(subCategory) ? 'Buy price per share (₹) *'
    : isEtf ? 'Buy price per unit (₹) *'
    : category === 'MUTUAL_FUND' || /reit/i.test(subCategory) ? 'Buy NAV (₹) *'
    : category === 'GOLD_SGB' ? 'Buy price per gram (₹) *'
    : 'Buy price per unit (₹) *';
  const nowLabel =
    category === 'EQUITY' || /unlisted|esop/i.test(subCategory) ? 'Current price (₹)'
    : isEtf ? 'Current price (₹)'
    : category === 'MUTUAL_FUND' || /reit/i.test(subCategory) ? 'Current NAV (₹)'
    : category === 'GOLD_SGB' ? 'Current price (₹ / g)'
    : 'Current price (₹)';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in">
      <div
        className="bg-slate-900 rounded-3xl max-w-xl w-full p-6 sm:p-7 shadow-2xl border border-slate-800 max-h-[90vh] overflow-y-auto space-y-5 text-slate-100"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-lg font-bold text-white font-serif">Add New Holding</h2>
            <p className="text-xs text-slate-400">Record an asset or loan in your local vault</p>
          </div>
          <button onClick={onClose} className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 cursor-pointer">
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className={lbl}>Asset Class *</label>
            <select
              value={category}
              onChange={e => handleCategory(e.target.value as AssetCategory)}
              className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm font-semibold text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
            >
              {(Object.keys(ASSET_CATEGORIES) as AssetCategory[]).map(cat => (
                <option key={cat} value={cat}>
                  {ASSET_CATEGORIES[cat].name}
                </option>
              ))}
            </select>
            <p className="text-[11px] text-slate-500 mt-1">{ASSET_CATEGORIES[category].description}</p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className={listedPicker ? 'sm:col-span-2' : ''}>
              <label className={lbl}>{listedPicker ? 'Search NSE / BSE / AMFI *' : 'Holding Name *'}</label>
              {listedPicker ? (
                <div className="space-y-2">
                  {picked ? (
                    <div className="px-3.5 py-2.5 rounded-xl border border-slate-700 bg-slate-950">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="font-semibold truncate">{picked.name}</div>
                          <div className="text-[11px] text-slate-400 font-mono">
                            {picked.symbol || picked.nseSymbol || picked.amfiCode} · {picked.isin} · {picked.source}
                          </div>
                          <div className="text-[11px] text-slate-400">
                            Close {MoneyEngine.formatRupees(BigInt(picked.closeMinor), false)} as on{' '}
                            {formatStatementDate(picked.priceDate)}
                          </div>
                          {existingSameIsin && (
                            <p className="text-[11px] text-amber-300 mt-1">
                              {existingSameIsin.name} is already on this profile. Saving records a buy on
                              that holding — not a second line.
                            </p>
                          )}
                        </div>
                        <button type="button" onClick={() => { setPicked(null); setName(''); }} className="text-xs text-amber-300">
                          Change
                        </button>
                      </div>
                    </div>
                  ) : (
                    <>
                      <input
                        type="text"
                        value={searchQ}
                        onChange={e => setSearchQ(e.target.value)}
                        placeholder={category === 'EQUITY' ? 'Type TCS or Tata Consultancy' : 'Type Parag Parikh or ISIN'}
                        className={inp}
                        autoComplete="off"
                      />
                      {listedSecurities.length === 0 && (
                        <div className="text-xs text-amber-200/90 flex items-center justify-between gap-2">
                          <span>Refresh market prices once to fill this list. Typing a name by hand is off for shares and funds.</span>
                          <button
                            type="button"
                            onClick={() => refreshEodPrices()}
                            disabled={pricesRefreshing}
                            className="shrink-0 px-2.5 py-1 rounded-lg bg-amber-400 text-slate-950 text-[11px] font-bold"
                          >
                            {pricesRefreshing ? 'Reading…' : 'Refresh prices'}
                          </button>
                        </div>
                      )}
                      {pricesError && <div className="text-[11px] text-rose-300">{pricesError}</div>}
                      {pricesAsOf && listedSecurities.length > 0 && (
                        <div className="text-[11px] text-slate-500">Closes as on {formatStatementDate(pricesAsOf)}</div>
                      )}
                      {matches.length > 0 && (
                        <div className="max-h-48 overflow-y-auto rounded-xl border border-slate-700 divide-y divide-slate-800">
                          {matches.map(row => (
                            <button
                              key={row.isin}
                              type="button"
                              onClick={() => pickSecurity(row)}
                              className="w-full text-left px-3 py-2 hover:bg-slate-800"
                            >
                              <div className="text-sm font-semibold leading-snug">{row.name}</div>
                              <div className="text-[11px] text-slate-400 font-mono mt-0.5">
                                NAV {MoneyEngine.formatRupees(BigInt(row.closeMinor), false)}
                                {' · '}{row.amfiCode || row.symbol || 'AMFI'}
                                {' · '}{row.isin}
                              </div>
                            </button>
                          ))}
                        </div>
                      )}
                      {searchQ.trim().length >= 2 && matches.length === 0 && listedSecurities.length > 0 && (
                        <div className="text-[11px] text-slate-400">No listed {category === 'EQUITY' ? 'share' : 'fund'} matches.</div>
                      )}
                    </>
                  )}
                </div>
              ) : (
                <input type="text" required placeholder={namePlaceholder(category, subCategory)} value={name} onChange={e => setName(e.target.value)} className={inp} />
              )}
            </div>
            <div>
              <label className={lbl}>Family Owner *</label>
              <select value={ownerId} onChange={e => setOwnerId(e.target.value)} className={inp}>
                {familyMembers.map(m => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.relation})
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">
                {listedPicker
                  ? identifierLabel(category, subCategory, extra.mfHeldAs)
                  : identifierLabel(category, subCategory)}
              </label>
              <input
                type="text"
                readOnly={listedPicker && category === 'EQUITY'}
                placeholder={
                  category === 'EQUITY'
                    ? 'from bhavcopy'
                    : extra.mfHeldAs === 'DEMAT' || isEtf
                      ? 'from bhavcopy / AMFI'
                      : category === 'MUTUAL_FUND'
                        ? 'folio on the statement'
                        : 'optional'
                }
                value={symbolOrFolio}
                onChange={e => setSymbolOrFolio(e.target.value)}
                className={inp + ' font-mono'}
              />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">Subcategory *</label>
              <select value={subCategory} onChange={e => handleSubCategory(e.target.value)} className={inp}>
                {ASSET_SUBCATEGORIES[category].map(s => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          {showAcquiredOn && (
            <div>
              <label className={lbl}>{acquiredOnLabel(category, subCategory)}</label>
              <input type="date" value={acquiredOn} onChange={e => handleAcquiredOn(e.target.value)} className={inp} />
            </div>
          )}

          {category === 'LIABILITY' ? (
            <div className={panel}>
              <div>
                <label className={lbl}>Total loan amount (₹) *</label>
                <input type="number" step="any" required placeholder="e.g. 850000" value={costPerUnitRupees} onChange={e => setCostPerUnitRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Current outstanding (₹) *</label>
                <input type="number" step="any" required placeholder="e.g. 612000" value={currentPriceRupees} onChange={e => setCurrentPriceRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Monthly EMI (₹) *</label>
                <input type="number" step="any" required placeholder="e.g. 18500" value={monthlyEmiRupees} onChange={e => setMonthlyEmiRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Interest rate (% p.a.)</label>
                <input type="number" step="0.01" placeholder="e.g. 9.25" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Rate type</label>
                <select value={extra.rateType || ''} onChange={e => patchExtra({ rateType: (e.target.value || undefined) as HoldingDetails['rateType'] })} className={inp}>
                  <option value="">Not sure</option>
                  <option value="FLOATING">Floating</option>
                  <option value="FIXED">Fixed</option>
                </select>
              </div>
              <div>
                <label className={lbl}>Original tenor (months)</label>
                <input type="number" step="1" min="1" placeholder="e.g. 240" value={extra.originalTenorMonths ?? ''} onChange={e => patchExtra({ originalTenorMonths: e.target.value ? parseInt(e.target.value, 10) : undefined })} className={inp} />
              </div>
              <div>
                <label className={lbl}>Remaining EMI months *</label>
                <input type="number" step="1" min="1" required placeholder="e.g. 36" value={remainingEmis} onChange={e => setRemainingEmis(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Next EMI date *</label>
                <input type="date" required value={nextEmiDate} onChange={e => setNextEmiDate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-400 block mb-1">Lender</label>
                <input type="text" placeholder="e.g. HDFC Bank" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              {linkable.length > 0 && (
                <div>
                  <label className="text-xs font-semibold text-slate-400 block mb-1">Linked property</label>
                  <select value={extra.linkedHoldingId || ''} onChange={e => patchExtra({ linkedHoldingId: e.target.value || undefined })} className={inp}>
                    <option value="">None</option>
                    {linkable.map(h => (
                      <option key={h.id} value={h.id}>{h.name}</option>
                    ))}
                  </select>
                </div>
              )}
              <p className="sm:col-span-2 text-[11px] text-slate-500">
                EMI is recorded automatically each due month and outstanding is reduced, unless you pause or stop it on the loan page.
              </p>
            </div>
          ) : isAif ? (
            <div className={panel}>
              <div>
                <label className={lbl}>AIF category *</label>
                <select required value={extra.aifCategory || ''} onChange={e => patchExtra({ aifCategory: (e.target.value || undefined) as HoldingDetails['aifCategory'] })} className={inp}>
                  <option value="">Select</option>
                  <option value="I">Category I (venture / SME / infra / social)</option>
                  <option value="II">Category II (PE / debt, no leverage)</option>
                  <option value="III">Category III (hedge / listed)</option>
                </select>
              </div>
              <div>
                <label className={lbl}>Commitment (₹) *</label>
                <input type="number" step="any" required placeholder="e.g. 2500000" value={commitmentRupees} onChange={e => setCommitmentRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Capital called (₹)</label>
                <input type="number" step="any" placeholder="Drawn so far" value={capitalCalledRupees} onChange={e => { setCapitalCalledRupees(e.target.value); if (!costPerUnitRupees) setCostPerUnitRupees(e.target.value); }} className={inp} />
              </div>
              <div>
                <label className={lbl}>Current NAV / value (₹) *</label>
                <input type="number" step="any" required placeholder="From the last statement" value={currentPriceRupees} onChange={e => setCurrentPriceRupees(e.target.value)} className={inp} />
              </div>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-400 block mb-1">Fund manager / AIF</label>
                <input type="text" placeholder="e.g. Blume Ventures" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
            </div>
          ) : unitAsset ? (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className={lbl}>{qtyLabel}</label>
                <input type="number" step="any" required placeholder="e.g. 100" value={quantity} onChange={e => setQuantity(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>{buyLabel}</label>
                <input type="number" step="any" required placeholder="e.g. 1450.50" value={costPerUnitRupees} onChange={e => setCostPerUnitRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>{nowLabel}</label>
                <input
                  type="number"
                  step="any"
                  readOnly={!!picked}
                  placeholder={picked ? 'From last close' : 'Leave blank to use buy price'}
                  value={currentPriceRupees}
                  onChange={e => setCurrentPriceRupees(e.target.value)}
                  className={inp}
                />
                {picked && (
                  <p className="text-[11px] text-slate-500 mt-1">
                    Close as on {formatStatementDate(picked.priceDate)}. Not live.
                  </p>
                )}
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className={lbl}>
                  {category === 'REAL_ESTATE' ? 'Current value (₹) *' : isPms ? 'Current value (₹) *' : 'Current balance (₹) *'}
                </label>
                <input type="number" step="any" required placeholder="e.g. 250000" value={currentPriceRupees} onChange={e => { setCurrentPriceRupees(e.target.value); if (!costPerUnitRupees) setCostPerUnitRupees(e.target.value); }} className={inp} />
              </div>
              <div>
                <label className={lbl}>{isPms ? 'Amount invested (₹)' : 'Amount put in (₹)'}</label>
                <input type="number" step="any" placeholder="Same as current if blank" value={costPerUnitRupees} onChange={e => setCostPerUnitRupees(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'EQUITY' && (
            <div className={panel}>
              {showExchange && (
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Exchange</label>
                  <select value={listedExchange} onChange={e => applyExchange(e.target.value)} className={inp}>
                    <option value="">Not sure</option>
                    <option value="NSE">NSE</option>
                    <option value="BSE">BSE</option>
                  </select>
                </div>
              )}
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Depository</label>
                <select
                  value={extra.depository || ''}
                  onChange={e => patchExtra({ depository: (e.target.value || undefined) as HoldingDetails['depository'] })}
                  className={inp}
                >
                  <option value="">Not sure</option>
                  <option value="NSDL">NSDL (DP ID + Client ID)</option>
                  <option value="CDSL">CDSL (16-digit BOID)</option>
                </select>
              </div>
              {extra.depository === 'NSDL' && (
                <>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">DP ID</label>
                    <input
                      type="text"
                      placeholder="IN300214"
                      value={extra.dpId || ''}
                      onChange={e => patchExtra({ dpId: e.target.value.toUpperCase() || undefined })}
                      className={inp + ' font-mono uppercase'}
                    />
                    <p className="text-[11px] text-slate-500 mt-1">IN + 6 digits</p>
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Client ID</label>
                    <input
                      type="text"
                      placeholder="12345678"
                      value={extra.clientId || ''}
                      onChange={e => patchExtra({ clientId: e.target.value.replace(/\D/g, '').slice(0, 8) || undefined })}
                      className={inp + ' font-mono'}
                    />
                    <p className="text-[11px] text-slate-500 mt-1">8 digits</p>
                  </div>
                </>
              )}
              {extra.depository === 'CDSL' && (
                <div className="sm:col-span-2">
                  <label className="text-xs font-semibold text-slate-300 block mb-1">BOID</label>
                  <input
                    type="text"
                    placeholder="1208160012345678"
                    value={extra.boId || ''}
                    onChange={e => patchExtra({ boId: e.target.value.replace(/\D/g, '').slice(0, 16) || undefined })}
                    className={inp + ' font-mono'}
                  />
                  <p className="text-[11px] text-slate-500 mt-1">16-digit CDSL beneficiary ID</p>
                </div>
              )}
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Buy charges (₹)</label>
                <input
                  type="number"
                  step="any"
                  placeholder="STT, brokerage, stamp"
                  value={chargesRupees}
                  onChange={e => setChargesRupees(e.target.value)}
                  className={inp}
                />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Broker</label>
                <input type="text" placeholder="e.g. Zerodha" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <p className="sm:col-span-2 text-[11px] text-slate-500">
                Charges are added to cost. Cap bucket is a style tag — it does not change tax treatment.
              </p>
            </div>
          )}

          {category === 'MUTUAL_FUND' && (
            <div className={panel}>
              {!isEtf && (
                <>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Plan</label>
                    <select
                      value={extra.mfPlan || ''}
                      onChange={e => patchExtra({ mfPlan: (e.target.value || undefined) as HoldingDetails['mfPlan'] })}
                      className={inp}
                    >
                      <option value="">Not sure</option>
                      <option value="DIRECT">Direct</option>
                      <option value="REGULAR">Regular</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Option</label>
                    <select
                      value={extra.mfOption || ''}
                      onChange={e => patchExtra({ mfOption: (e.target.value || undefined) as HoldingDetails['mfOption'] })}
                      className={inp}
                    >
                      <option value="">Not sure</option>
                      <option value="GROWTH">Growth</option>
                      <option value="IDCW">IDCW</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">RTA</label>
                    <select value={rta} onChange={e => setRta(e.target.value)} className={inp}>
                      <option value="">Not sure</option>
                      <option value="CAMS">CAMS</option>
                      <option value="KFINTECH">KFin</option>
                    </select>
                  </div>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Held as</label>
                    <select
                      value={isEtf ? 'DEMAT' : (extra.mfHeldAs || '')}
                      onChange={e => {
                        const next = (e.target.value || undefined) as HoldingDetails['mfHeldAs'];
                        patchExtra({ mfHeldAs: next });
                        if (next === 'DEMAT' && picked) {
                          const sym = tickerFromPick(picked, listedExchange);
                          if (sym) setSymbolOrFolio(sym);
                        }
                      }}
                      className={inp}
                    >
                      <option value="">Not sure</option>
                      <option value="FOLIO">SOA folio (CAMS / KFin)</option>
                      <option value="DEMAT">Demat units</option>
                    </select>
                  </div>
                </>
              )}
              {showExchange && (
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Exchange</label>
                  <select value={listedExchange} onChange={e => applyExchange(e.target.value)} className={inp}>
                    <option value="">Not sure</option>
                    <option value="NSE">NSE</option>
                    <option value="BSE">BSE</option>
                  </select>
                </div>
              )}
              {showDematIds && (
                <>
                  <div>
                    <label className="text-xs font-semibold text-slate-300 block mb-1">Depository</label>
                    <select
                      value={extra.depository || ''}
                      onChange={e => patchExtra({ depository: (e.target.value || undefined) as HoldingDetails['depository'] })}
                      className={inp}
                    >
                      <option value="">Not sure</option>
                      <option value="NSDL">NSDL (DP ID + Client ID)</option>
                      <option value="CDSL">CDSL (16-digit BOID)</option>
                    </select>
                  </div>
                  {extra.depository === 'NSDL' && (
                    <>
                      <div>
                        <label className="text-xs font-semibold text-slate-300 block mb-1">DP ID</label>
                        <input
                          type="text"
                          placeholder="IN300214"
                          value={extra.dpId || ''}
                          onChange={e => patchExtra({ dpId: e.target.value.toUpperCase() || undefined })}
                          className={inp + ' font-mono uppercase'}
                        />
                      </div>
                      <div>
                        <label className="text-xs font-semibold text-slate-300 block mb-1">Client ID</label>
                        <input
                          type="text"
                          placeholder="12345678"
                          value={extra.clientId || ''}
                          onChange={e => patchExtra({ clientId: e.target.value.replace(/\D/g, '').slice(0, 8) || undefined })}
                          className={inp + ' font-mono'}
                        />
                      </div>
                    </>
                  )}
                  {extra.depository === 'CDSL' && (
                    <div className="sm:col-span-2">
                      <label className="text-xs font-semibold text-slate-300 block mb-1">BOID</label>
                      <input
                        type="text"
                        placeholder="1208160012345678"
                        value={extra.boId || ''}
                        onChange={e => patchExtra({ boId: e.target.value.replace(/\D/g, '').slice(0, 16) || undefined })}
                        className={inp + ' font-mono'}
                      />
                    </div>
                  )}
                </>
              )}
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">AMC</label>
                <input type="text" placeholder="e.g. PPFAS Mutual Fund" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Buy charges (₹)</label>
                <input
                  type="number"
                  step="any"
                  placeholder={isEtf ? 'STT, brokerage, stamp' : 'Stamp duty (optional)'}
                  value={chargesRupees}
                  onChange={e => setChargesRupees(e.target.value)}
                  className={inp}
                />
              </div>
              {isElssSub(subCategory) && (
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Lock-in ends (3 years)</label>
                  <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
                </div>
              )}
              <p className="sm:col-span-2 text-[11px] text-slate-500">
                {isEtf
                  ? 'ETFs trade like shares on NSE or BSE and sit in demat.'
                  : isElssSub(subCategory)
                    ? 'ELSS units lock for 3 years from this trade date (FIFO per lot).'
                    : extra.mfHeldAs === 'DEMAT'
                      ? 'Demat units have no CAMS/KFin SOA folio — use the BOID instead.'
                      : 'Direct has no distributor. Growth reinvests; IDCW pays out.'}
              </p>
            </div>
          )}

          {category === 'GOLD_SGB' && subCategory.startsWith('Sovereign') && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Issue price (₹ / g)</label>
                <input type="number" step="any" placeholder="e.g. 5117" value={issuePriceRupees} onChange={e => setIssuePriceRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">SGB coupon (% p.a.)</label>
                <input type="number" step="0.01" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
                <p className="text-[11px] text-slate-500 mt-1">RBI SGBs pay 2.5% on the issue price, twice a year.</p>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Held as</label>
                <select value={extra.goldHoldMode || ''} onChange={e => patchExtra({ goldHoldMode: (e.target.value || undefined) as HoldingDetails['goldHoldMode'] })} className={inp}>
                  <option value="">Not sure</option>
                  <option value="DEMAT">Demat</option>
                  <option value="CERTIFICATE">RBI / bank certificate</option>
                </select>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Next interest date</label>
                <input type="date" value={nextEmiDate} onChange={e => setNextEmiDate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">First exit (after year 5)</label>
                <input type="date" value={extra.earlyExitDate || ''} onChange={e => patchExtra({ earlyExitDate: e.target.value || undefined })} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Matures (8 years)</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'GOLD_SGB' && subCategory.startsWith('Physical') && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Purity</label>
                <select value={extra.goldPurity || '24K'} onChange={e => patchExtra({ goldPurity: e.target.value as HoldingDetails['goldPurity'] })} className={inp}>
                  <option value="24K">24K</option>
                  <option value="22K">22K</option>
                  <option value="999">999 fine</option>
                </select>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Jeweller</label>
                <input type="text" placeholder="e.g. Tanishq" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300 block mb-1">Hallmark / invoice no.</label>
                <input type="text" placeholder="optional" value={extra.goldHallmark || ''} onChange={e => patchExtra({ goldHallmark: e.target.value || undefined })} className={inp} />
              </div>
            </div>
          )}

          {category === 'GOLD_SGB' && subCategory.startsWith('Digital') && (
            <div className={panel}>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300 block mb-1">Provider</label>
                <input type="text" placeholder="e.g. SafeGold, MMTC-PAMP, Digital Gold India" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory === 'PPF' && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Interest rate (% p.a.)</label>
                <input type="number" step="0.01" placeholder="7.1" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">15-year block ends</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">This FY contribution (₹)</label>
                <input type="number" step="any" placeholder="Max ₹1.5 lakh" value={fyContributionRupees} onChange={e => setFyContributionRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Bank / post office</label>
                <input type="text" placeholder="e.g. SBI" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory === 'EPF' && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Interest rate (% p.a.)</label>
                <input type="number" step="0.01" placeholder="8.25" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Employer / EPFO</label>
                <input type="text" placeholder="e.g. Current employer" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <p className="sm:col-span-2 text-[11px] text-slate-500">Use the 12-digit UAN, not the old office member ID. All jobs sit under one UAN.</p>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory === 'NPS' && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Tier</label>
                <select value={extra.npsTier || 'I'} onChange={e => patchExtra({ npsTier: e.target.value as HoldingDetails['npsTier'] })} className={inp}>
                  <option value="I">Tier I (retirement)</option>
                  <option value="II">Tier II (withdrawable)</option>
                </select>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">CRA</label>
                <select value={extra.npsCra || ''} onChange={e => patchExtra({ npsCra: (e.target.value || undefined) as HoldingDetails['npsCra'] })} className={inp}>
                  <option value="">Not sure</option>
                  <option value="PROT">Protean</option>
                  <option value="CAMS">CAMS</option>
                  <option value="KFIN">KFin</option>
                </select>
              </div>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300 block mb-1">Pension fund (PFM)</label>
                <input type="text" placeholder="e.g. ICICI Prudential / HDFC / LIC" value={extra.npsPfm || ''} onChange={e => patchExtra({ npsPfm: e.target.value || undefined })} className={inp} />
              </div>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory.startsWith('Sukanya') && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Interest rate (% p.a.)</label>
                <input type="number" step="0.01" placeholder="8.2" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Matures (21 years)</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">This FY contribution (₹)</label>
                <input type="number" step="any" placeholder="Max ₹1.5 lakh" value={fyContributionRupees} onChange={e => setFyContributionRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Bank / post office</label>
                <input type="text" placeholder="e.g. SBI" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory === 'Bank FD' && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Interest rate (% p.a.)</label>
                <input type="number" step="0.01" placeholder="7.1" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Compounding</label>
                <select value={extra.compounding || ''} onChange={e => patchExtra({ compounding: (e.target.value || undefined) as HoldingDetails['compounding'] })} className={inp}>
                  <option value="">Not sure</option>
                  <option value="CUMULATIVE">Cumulative (paid at maturity)</option>
                  <option value="QUARTERLY">Quarterly payout</option>
                  <option value="MONTHLY">Monthly payout</option>
                  <option value="YEARLY">Yearly payout</option>
                </select>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Matures</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Bank</label>
                <input type="text" placeholder="e.g. HDFC Bank" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <label className="sm:col-span-2 flex items-center gap-2 text-xs text-slate-300">
                <input type="checkbox" checked={!!extra.tdsApplicable} onChange={e => patchExtra({ tdsApplicable: e.target.checked || undefined })} />
                TDS deducted (interest above the 194A limit)
              </label>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory.startsWith('Bond') && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">ISIN</label>
                <input type="text" placeholder="IN0020…" value={isinManual} onChange={e => setIsinManual(e.target.value)} className={inp + ' font-mono uppercase'} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Face value (₹)</label>
                <input type="number" step="any" placeholder="100" value={faceValueRupees} onChange={e => setFaceValueRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Coupon (% p.a.)</label>
                <input type="number" step="0.01" placeholder="7.10" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Matures</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'FIXED_INCOME' && subCategory === 'Other' && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Interest rate (% p.a.)</label>
                <input type="number" step="0.01" placeholder="7.1" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Matures / lock-in ends</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300 block mb-1">Bank / institution</label>
                <input type="text" placeholder="e.g. SBI" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'REAL_ESTATE' && /reit/i.test(subCategory) && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">ISIN</label>
                <input type="text" placeholder="INE0410…" value={isinManual} onChange={e => setIsinManual(e.target.value)} className={inp + ' font-mono uppercase'} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Exchange / AMC</label>
                <input type="text" placeholder="e.g. NSE" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'REAL_ESTATE' && !/reit/i.test(subCategory) && (
            <div className={panel}>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300 block mb-1">Property location / address</label>
                <input type="text" placeholder="e.g. Flat 402, Green Glen Layout, Bengaluru" value={location} onChange={e => setLocation(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Carpet / built-up (sq ft)</label>
                <input type="number" step="any" placeholder="e.g. 1250" value={extra.areaSqFt ?? ''} onChange={e => patchExtra({ areaSqFt: e.target.value ? parseFloat(e.target.value) : undefined })} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Ownership</label>
                <select value={extra.ownership || 'SELF'} onChange={e => patchExtra({ ownership: e.target.value as HoldingDetails['ownership'] })} className={inp}>
                  <option value="SELF">Self</option>
                  <option value="JOINT">Joint</option>
                  <option value="SPOUSE">Spouse</option>
                  <option value="HUF">HUF</option>
                </select>
              </div>
              {(extra.ownership === 'JOINT' || extra.ownership === 'SPOUSE') && (
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Your share (%)</label>
                  <input type="number" step="1" min="1" max="100" placeholder="50" value={extra.ownershipPercent ?? ''} onChange={e => patchExtra({ ownershipPercent: e.target.value ? parseFloat(e.target.value) : undefined })} className={inp} />
                </div>
              )}
              {linkable.length > 0 && (
                <div className="sm:col-span-2">
                  <label className="text-xs font-semibold text-slate-300 block mb-1">Linked home loan</label>
                  <select value={extra.linkedHoldingId || ''} onChange={e => patchExtra({ linkedHoldingId: e.target.value || undefined })} className={inp}>
                    <option value="">None</option>
                    {linkable.map(h => (
                      <option key={h.id} value={h.id}>{h.name}</option>
                    ))}
                  </select>
                </div>
              )}
            </div>
          )}

          {isPms && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Strategy name</label>
                <input type="text" placeholder="e.g. Indian Entrepreneur Portfolio" value={extra.strategyName || ''} onChange={e => patchExtra({ strategyName: e.target.value || undefined })} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">PMS house</label>
                <input type="text" placeholder="e.g. ASK / Marcellus" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-300 block mb-1">Custodian</label>
                <input type="text" placeholder="e.g. ICICI Bank custody" value={extra.custodian || ''} onChange={e => patchExtra({ custodian: e.target.value || undefined })} className={inp} />
              </div>
            </div>
          )}

          {category === 'CASH_BANK' && (
            <div className={panel}>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  {/liquid/i.test(subCategory) ? 'AMC' : 'Bank'}
                </label>
                <input type="text" placeholder={/liquid/i.test(subCategory) ? 'e.g. HDFC Mutual Fund' : 'e.g. HDFC Bank'} value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              {!/liquid/i.test(subCategory) && (
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">IFSC</label>
                  <input type="text" placeholder="HDFC0000123" value={extra.ifsc || ''} onChange={e => patchExtra({ ifsc: e.target.value.toUpperCase() || undefined })} className={inp + ' font-mono uppercase'} />
                </div>
              )}
              {/liquid/i.test(subCategory) && (
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">ISIN (optional)</label>
                  <input type="text" placeholder="INF…" value={isinManual} onChange={e => setIsinManual(e.target.value)} className={inp + ' font-mono uppercase'} />
                </div>
              )}
            </div>
          )}

          {category === 'SPECIALISED' && !isAif && !isPms && (
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">
                Company / manager
              </label>
              <input type="text" placeholder="e.g. Zerodha / HDFC Bank" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
            </div>
          )}

          <div className="p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800 space-y-2.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                <Tag className="w-3.5 h-3.5 text-amber-400" />
                <span>Goal tags</span>
              </label>
              <span className="text-[11px] text-slate-400">Optional</span>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="text"
                placeholder="Type a tag and press Add"
                value={tagInput}
                onChange={e => setTagInput(e.target.value)}
                onKeyDown={e => {
                  if (e.key === 'Enter' || e.key === ',') {
                    e.preventDefault();
                    handleAddCustomTag();
                  }
                }}
                className={inp}
              />
              <button type="button" onClick={() => handleAddCustomTag()} className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold cursor-pointer">
                Add Tag
              </button>
            </div>
            {selectedTags.length > 0 && (
              <div className="flex items-center gap-1.5 flex-wrap pt-1">
                <span className="text-[11px] text-slate-400 font-medium">Applied:</span>
                {selectedTags.map(t => (
                  <span key={t} className="inline-flex items-center gap-1 px-2.5 py-1 bg-amber-400/15 text-amber-300 border border-amber-500/30 rounded-lg text-xs font-medium">
                    <span>{t}</span>
                    <button type="button" onClick={() => handleToggleTag(t)} className="hover:text-amber-100 cursor-pointer">
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}
            <div className="space-y-2 pt-1 border-t border-slate-800/80">
              <div className="text-[11px] text-slate-400 font-medium flex items-center gap-1 mb-1">
                <Target className="w-3 h-3 text-emerald-400" />
                <span>Quick goal presets</span>
              </div>
              <div className="flex items-center gap-1.5 flex-wrap">
                {[...new Set([...goals.map(g => g.name), ...COMMON_GOAL_TAGS])].map(gt => {
                  const isSelected = selectedTags.includes(gt);
                  return (
                    <button
                      key={gt}
                      type="button"
                      onClick={() => handleToggleTag(gt)}
                      className={`text-[11px] px-2.5 py-1 rounded-lg border cursor-pointer flex items-center gap-1 ${
                        isSelected
                          ? 'bg-emerald-950/80 border-emerald-500/60 text-emerald-300 font-semibold'
                          : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200'
                      }`}
                    >
                      {isSelected && <Check className="w-3 h-3 text-emerald-400" />}
                      <span>{gt}</span>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1">Private notes</label>
            <input type="text" placeholder="e.g. Long term retirement core" value={notes} onChange={e => setNotes(e.target.value)} className={inp} />
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <button type="button" onClick={onClose} className="px-4 py-2 text-sm font-semibold text-slate-400 hover:text-slate-200 cursor-pointer">
              Cancel
            </button>
            <button type="submit" className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-sm font-bold rounded-xl cursor-pointer">
              {existingSameIsin ? 'Record buy on existing' : 'Save Holding'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
