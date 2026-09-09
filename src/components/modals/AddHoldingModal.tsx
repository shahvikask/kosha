import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, ASSET_SUBCATEGORIES, AssetCategory } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { addCalendarMonths } from '../../lib/emi';
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

function usesUnits(category: AssetCategory): boolean {
  return category === 'EQUITY' || category === 'MUTUAL_FUND' || category === 'GOLD_SGB';
}

function defaultNextEmiDate(): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = String(now.getMonth() + 1).padStart(2, '0');
  const fifth = `${y}-${m}-05`;
  const today = `${y}-${m}-${String(now.getDate()).padStart(2, '0')}`;
  return fifth > today ? fifth : addCalendarMonths(fifth, 1);
}

function identifierLabel(category: AssetCategory): string {
  switch (category) {
    case 'EQUITY':
      return 'NSE/BSE Symbol';
    case 'MUTUAL_FUND':
      return 'Folio Number';
    case 'GOLD_SGB':
      return 'SGB Certificate / Series';
    case 'LIABILITY':
      return 'Loan account number';
    case 'REAL_ESTATE':
      return 'Survey / door number (optional)';
    default:
      return 'Account number';
  }
}

function namePlaceholder(category: AssetCategory): string {
  switch (category) {
    case 'EQUITY':
      return 'e.g. State Bank of India';
    case 'MUTUAL_FUND':
      return 'e.g. Parag Parikh Flexi Cap';
    case 'GOLD_SGB':
      return 'e.g. SGB 2021-22 Series V';
    case 'REAL_ESTATE':
      return 'e.g. 2BHK Flat Indiranagar';
    case 'LIABILITY':
      return 'e.g. HDFC Car Loan';
    case 'FIXED_INCOME':
      return 'e.g. SBI PPF';
    case 'CASH_BANK':
      return 'e.g. HDFC Savings';
    default:
      return 'e.g. PMS mandate';
  }
}

export const AddHoldingModal: React.FC<AddHoldingModalProps> = ({ isOpen, onClose }) => {
  const { familyMembers, addHolding, selectedMemberId, goals } = useKosha();

  const [category, setCategory] = useState<AssetCategory>('EQUITY');
  const [name, setName] = useState('');
  const [symbolOrFolio, setSymbolOrFolio] = useState('');
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

  const handleCategory = (next: AssetCategory) => {
    setCategory(next);
    setSubCategory(ASSET_SUBCATEGORIES[next][0]);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const unitAsset = usesUnits(category);
    const qtyNum = unitAsset ? parseFloat(quantity) || 0 : 1;
    if (unitAsset && qtyNum <= 0) return;

    const costNum = parseFloat(costPerUnitRupees) || 0;
    const currentPriceNum = parseFloat(currentPriceRupees) || costNum;
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

    addHolding({
      name: name.trim(),
      symbolOrFolio: symbolOrFolio.trim() || name.substring(0, 8).toUpperCase(),
      category,
      subCategory: subCategory.trim() || ASSET_CATEGORIES[category].name,
      ownerId,
      quantityScaled: qtyScaled,
      averageCostPerUnitMinor: costMinor,
      currentPriceMinor,
      priceSource: 'Manual Initial Entry',
      institutionName: institutionName.trim() || undefined,
      accountNumberMasked: symbolOrFolio.trim() || undefined,
      notes: notes.trim() || undefined,
      tag: finalTags[0],
      tags: finalTags.length > 0 ? finalTags : undefined,
      couponRatePercent:
        category === 'GOLD_SGB' && couponOrInterestRate ? parseFloat(couponOrInterestRate) : undefined,
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
          : category === 'GOLD_SGB' && nextEmiDate
            ? nextEmiDate
            : undefined,
      emiStatus: hasEmi ? 'PAYING' : undefined,
    });

    onClose();
  };

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
            <div>
              <label className={lbl}>Holding Name *</label>
              <input type="text" required placeholder={namePlaceholder(category)} value={name} onChange={e => setName(e.target.value)} className={inp} />
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
              <label className="text-xs font-semibold text-slate-400 block mb-1">{identifierLabel(category)}</label>
              <input type="text" placeholder={category === 'EQUITY' ? 'e.g. SBIN' : 'optional'} value={symbolOrFolio} onChange={e => setSymbolOrFolio(e.target.value)} className={inp + ' font-mono'} />
            </div>
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">Subcategory *</label>
              <select value={subCategory} onChange={e => setSubCategory(e.target.value)} className={inp}>
                {ASSET_SUBCATEGORIES[category].map(s => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
            </div>
          </div>

          {category === 'LIABILITY' ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800">
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
                <label className={lbl}>Remaining EMI months *</label>
                <input type="number" step="1" min="1" required placeholder="e.g. 36" value={remainingEmis} onChange={e => setRemainingEmis(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>Next EMI date *</label>
                <input type="date" required value={nextEmiDate} onChange={e => setNextEmiDate(e.target.value)} className={inp} />
              </div>
              <div className="sm:col-span-2">
                <label className="text-xs font-semibold text-slate-400 block mb-1">Lender</label>
                <input type="text" placeholder="e.g. HDFC Bank" value={institutionName} onChange={e => setInstitutionName(e.target.value)} className={inp} />
              </div>
              <p className="sm:col-span-2 text-[11px] text-slate-500">
                EMI is recorded automatically each due month and outstanding is reduced, unless you pause or stop it on the loan page.
              </p>
            </div>
          ) : usesUnits(category) ? (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className={lbl}>{category === 'GOLD_SGB' ? 'Quantity (grams) *' : category === 'EQUITY' ? 'Shares *' : 'Units *'}</label>
                <input type="number" step="any" required placeholder="e.g. 100" value={quantity} onChange={e => setQuantity(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>{category === 'MUTUAL_FUND' ? 'Buy NAV (₹) *' : 'Buy price per unit (₹) *'}</label>
                <input type="number" step="any" required placeholder="e.g. 1450.50" value={costPerUnitRupees} onChange={e => setCostPerUnitRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className={lbl}>{category === 'MUTUAL_FUND' ? 'Current NAV (₹)' : 'Current price (₹)'}</label>
                <input type="number" step="any" placeholder="Leave blank to use buy price" value={currentPriceRupees} onChange={e => setCurrentPriceRupees(e.target.value)} className={inp} />
              </div>
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className={lbl}>
                  {category === 'REAL_ESTATE' ? 'Current value (₹) *' : 'Current balance (₹) *'}
                </label>
                <input type="number" step="any" required placeholder="e.g. 250000" value={currentPriceRupees} onChange={e => { setCurrentPriceRupees(e.target.value); if (!costPerUnitRupees) setCostPerUnitRupees(e.target.value); }} className={inp} />
              </div>
              <div>
                <label className={lbl}>Amount put in (₹)</label>
                <input type="number" step="any" placeholder="Same as current if blank" value={costPerUnitRupees} onChange={e => setCostPerUnitRupees(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'GOLD_SGB' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800">
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Issue price (₹ / g)</label>
                <input type="number" step="any" placeholder="e.g. 3119" value={issuePriceRupees} onChange={e => setIssuePriceRupees(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">SGB coupon (% p.a.)</label>
                <input type="number" step="0.01" placeholder="2.5" value={couponOrInterestRate} onChange={e => setCouponOrInterestRate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Next interest date</label>
                <input type="date" value={nextEmiDate} onChange={e => setNextEmiDate(e.target.value)} className={inp} />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Matures</label>
                <input type="date" value={maturityDate} onChange={e => setMaturityDate(e.target.value)} className={inp} />
              </div>
            </div>
          )}

          {category === 'FIXED_INCOME' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800">
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

          {category === 'REAL_ESTATE' && (
            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-1">Property location / address</label>
              <input type="text" placeholder="e.g. Flat 402, Green Glen Layout, Bengaluru" value={location} onChange={e => setLocation(e.target.value)} className={inp} />
            </div>
          )}

          {(category === 'CASH_BANK' || category === 'EQUITY' || category === 'MUTUAL_FUND' || category === 'SPECIALISED') && (
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">
                {category === 'EQUITY' ? 'Broker' : category === 'CASH_BANK' ? 'Bank' : 'Institution / AMC'}
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
              Save Holding
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
