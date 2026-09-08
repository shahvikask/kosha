import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, AssetCategory } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { X, Plus, ShieldCheck, Coins, Building, TrendingUp, Tag, Target, User, Check } from 'lucide-react';

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

export const AddHoldingModal: React.FC<AddHoldingModalProps> = ({ isOpen, onClose }) => {
  const { familyMembers, addHolding, selectedMemberId, goals } = useKosha();

  const [category, setCategory] = useState<AssetCategory>('EQUITY');
  const [name, setName] = useState('');
  const [symbolOrFolio, setSymbolOrFolio] = useState('');
  const [subCategory, setSubCategory] = useState('');
  const [ownerId, setOwnerId] = useState(
    selectedMemberId === 'ALL' ? familyMembers[0]?.id || 'fam_1' : selectedMemberId
  );
  const [quantity, setQuantity] = useState('');
  const [costPerUnitRupees, setCostPerUnitRupees] = useState('');
  const [currentPriceRupees, setCurrentPriceRupees] = useState('');
  const [institutionName, setInstitutionName] = useState('');
  const [notes, setNotes] = useState('');

  // Tags & Goals categorization
  const [tagInput, setTagInput] = useState('');
  const [selectedTags, setSelectedTags] = useState<string[]>([]);

  // SGB / Fixed Income / Loan fields
  const [couponOrInterestRate, setCouponOrInterestRate] = useState('');
  const [maturityDate, setMaturityDate] = useState('');
  const [location, setLocation] = useState('');

  if (!isOpen) return null;

  const handleAddCustomTag = (tagToAdd?: string) => {
    const raw = tagToAdd || tagInput;
    if (!raw.trim()) return;
    
    // Split by comma in case multiple tags were typed
    const parts = raw
      .split(',')
      .map(t => t.trim())
      .filter(t => t.length > 0);

    const updated = [...selectedTags];
    parts.forEach(p => {
      if (!updated.includes(p)) {
        updated.push(p);
      }
    });

    setSelectedTags(updated);
    if (!tagToAdd) {
      setTagInput('');
    }
  };

  const handleToggleTag = (tag: string) => {
    if (selectedTags.includes(tag)) {
      setSelectedTags(selectedTags.filter(t => t !== tag));
    } else {
      setSelectedTags([...selectedTags, tag]);
    }
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setSelectedTags(selectedTags.filter(t => t !== tagToRemove));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;

    const qtyNum = parseFloat(quantity) || 1;
    const costNum = parseFloat(costPerUnitRupees) || 0;
    const currentPriceNum = parseFloat(currentPriceRupees) || costNum;

    const qtyScaled = MoneyEngine.fromQty(qtyNum).toString();
    const costMinor = MoneyEngine.fromRupees(costNum).toString();
    const currentPriceMinor = MoneyEngine.fromRupees(currentPriceNum).toString();

    // Consolidate tags
    const finalTags = [...selectedTags];
    if (tagInput.trim()) {
      tagInput
        .split(',')
        .map(t => t.trim())
        .filter(t => t.length > 0)
        .forEach(t => {
          if (!finalTags.includes(t)) finalTags.push(t);
        });
    }

    const primaryTag = finalTags.length > 0 ? finalTags[0] : undefined;

    addHolding({
      name: name.trim(),
      symbolOrFolio: symbolOrFolio.trim() || name.substring(0, 8).toUpperCase(),
      category,
      subCategory: subCategory.trim() || ASSET_CATEGORIES[category].name,
      ownerId,
      quantityScaled: qtyScaled,
      averageCostPerUnitMinor: costMinor,
      currentPriceMinor: currentPriceMinor,
      priceSource: 'Manual Initial Entry',
      institutionName: institutionName.trim() || undefined,
      notes: notes.trim() || undefined,
      tag: primaryTag,
      tags: finalTags.length > 0 ? finalTags : undefined,
      couponRatePercent: couponOrInterestRate ? parseFloat(couponOrInterestRate) : undefined,
      interestRatePercent: couponOrInterestRate ? parseFloat(couponOrInterestRate) : undefined,
      maturityDate: maturityDate || undefined,
      location: location.trim() || undefined,
    });

    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in">
      <div
        className="bg-slate-900 rounded-3xl max-w-xl w-full p-6 sm:p-7 shadow-2xl border border-slate-800 max-h-[90vh] overflow-y-auto space-y-5 text-slate-100"
        onClick={e => e.stopPropagation()}
      >
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-lg font-bold text-white font-serif">Add New Holding</h2>
            <p className="text-xs text-slate-400">Record a new asset or loan with goal and family tags in your local vault</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          
          {/* Asset Category Select */}
          <div>
            <label className="text-xs font-bold text-slate-300 block mb-1.5">Asset Class *</label>
            <select
              value={category}
              onChange={e => setCategory(e.target.value as AssetCategory)}
              className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm font-semibold text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
            >
              {(Object.keys(ASSET_CATEGORIES) as AssetCategory[]).map(cat => (
                <option key={cat} value={cat}>
                  {ASSET_CATEGORIES[cat].name}
                </option>
              ))}
            </select>
          </div>

          {/* Holding Name & Owner */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Holding Name *</label>
              <input
                type="text"
                required
                placeholder={
                  category === 'EQUITY'
                    ? 'e.g. State Bank of India'
                    : category === 'MUTUAL_FUND'
                    ? 'e.g. Parag Parikh Flexi Cap'
                    : category === 'GOLD_SGB'
                    ? 'e.g. SGB 2021-22 Series V'
                    : category === 'REAL_ESTATE'
                    ? 'e.g. 2BHK Flat Indiranagar'
                    : category === 'LIABILITY'
                    ? 'e.g. HDFC Home Loan'
                    : 'e.g. SBI Fixed Deposit'
                }
                value={name}
                onChange={e => setName(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Family Owner *</label>
              <select
                value={ownerId}
                onChange={e => setOwnerId(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              >
                {familyMembers.map(m => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.relation})
                  </option>
                ))}
              </select>
            </div>
          </div>

          {/* Symbol/Folio and Subcategory */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">
                {category === 'EQUITY'
                  ? 'NSE/BSE Symbol'
                  : category === 'MUTUAL_FUND'
                  ? 'Folio Number'
                  : category === 'GOLD_SGB'
                  ? 'SGB Certificate / Series'
                  : 'Identifier / Account Number'}
              </label>
              <input
                type="text"
                placeholder="e.g. SBIN / Folio 9840192"
                value={symbolOrFolio}
                onChange={e => setSymbolOrFolio(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm font-mono text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">Subcategory</label>
              <input
                type="text"
                placeholder="e.g. Large Cap / EEE Govt Scheme"
                value={subCategory}
                onChange={e => setSubCategory(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>
          </div>

          {/* Tags & Goal Categorization Field */}
          <div className="p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800 space-y-2.5">
            <div className="flex items-center justify-between">
              <label className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                <Tag className="w-3.5 h-3.5 text-amber-400" />
                <span>Tags & Categorization (Goal / Family Member)</span>
              </label>
              <span className="text-[11px] text-slate-400">Optional tag for filtering</span>
            </div>

            {/* Custom Tag Input */}
            <div className="flex items-center gap-2">
              <div className="relative flex-1">
                <input
                  id="holding-tag-input"
                  type="text"
                  placeholder="Type custom tag (e.g. Retirement, Emergency, or Ananya)..."
                  value={tagInput}
                  onChange={e => setTagInput(e.target.value)}
                  onKeyDown={e => {
                    if (e.key === 'Enter' || e.key === ',') {
                      e.preventDefault();
                      handleAddCustomTag();
                    }
                  }}
                  className="w-full px-3.5 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs sm:text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                />
              </div>
              <button
                type="button"
                onClick={() => handleAddCustomTag()}
                className="px-3 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 rounded-xl text-xs font-semibold cursor-pointer transition-colors"
              >
                Add Tag
              </button>
            </div>

            {/* Active Selected Tags */}
            {selectedTags.length > 0 && (
              <div className="flex items-center gap-1.5 flex-wrap pt-1">
                <span className="text-[11px] text-slate-400 font-medium">Applied:</span>
                {selectedTags.map(t => (
                  <span
                    key={t}
                    className="inline-flex items-center gap-1 px-2.5 py-1 bg-amber-400/15 text-amber-300 border border-amber-500/30 rounded-lg text-xs font-medium"
                  >
                    <span>{t}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveTag(t)}
                      className="hover:text-amber-100 cursor-pointer ml-0.5"
                    >
                      <X className="w-3 h-3" />
                    </button>
                  </span>
                ))}
              </div>
            )}

            {/* Quick Suggestion Chips */}
            <div className="space-y-2 pt-1 border-t border-slate-800/80">
              {/* Goal Presets */}
              <div>
                <div className="text-[11px] text-slate-400 font-medium flex items-center gap-1 mb-1">
                  <Target className="w-3 h-3 text-emerald-400" />
                  <span>Quick Goal Presets:</span>
                </div>
                <div className="flex items-center gap-1.5 flex-wrap">
                  {[...new Set([...goals.map(g => g.name), ...COMMON_GOAL_TAGS])].map(gt => {
                    const isSelected = selectedTags.includes(gt);
                    return (
                      <button
                        key={gt}
                        type="button"
                        onClick={() => handleToggleTag(gt)}
                        className={`text-[11px] px-2.5 py-1 rounded-lg border transition-all cursor-pointer flex items-center gap-1 ${
                          isSelected
                            ? 'bg-emerald-950/80 border-emerald-500/60 text-emerald-300 font-semibold shadow-xs'
                            : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700'
                        }`}
                      >
                        {isSelected && <Check className="w-3 h-3 text-emerald-400" />}
                        <span>{gt}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* Family Member Presets */}
              <div>
                <div className="text-[11px] text-slate-400 font-medium flex items-center gap-1 mb-1">
                  <User className="w-3 h-3 text-sky-400" />
                  <span>Quick Family Member Tags:</span>
                </div>
                <div className="flex items-center gap-1.5 flex-wrap">
                  {familyMembers.map(fm => {
                    const memberTag = `${fm.name} (${fm.relation})`;
                    const isSelected = selectedTags.includes(memberTag) || selectedTags.includes(fm.name);
                    return (
                      <button
                        key={fm.id}
                        type="button"
                        onClick={() => handleToggleTag(memberTag)}
                        className={`text-[11px] px-2.5 py-1 rounded-lg border transition-all cursor-pointer flex items-center gap-1 ${
                          isSelected
                            ? 'bg-sky-950/80 border-sky-500/60 text-sky-300 font-semibold shadow-xs'
                            : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-200 hover:border-slate-700'
                        }`}
                      >
                        {isSelected && <Check className="w-3 h-3 text-sky-400" />}
                        <span>{memberTag}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Quantity & Pricing */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">
                {category === 'GOLD_SGB' ? 'Quantity (Grams) *' : category === 'REAL_ESTATE' || category === 'LIABILITY' || category === 'FIXED_INCOME' ? 'Units / Count *' : 'Shares / Units *'}
              </label>
              <input
                type="number"
                step="any"
                required
                placeholder="e.g. 100"
                value={quantity}
                onChange={e => setQuantity(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">
                {category === 'LIABILITY' ? 'Original Principal (₹)' : 'Buy Price per unit (₹) *'}
              </label>
              <input
                type="number"
                step="any"
                required
                placeholder="e.g. 1450.50"
                value={costPerUnitRupees}
                onChange={e => setCostPerUnitRupees(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">
                {category === 'LIABILITY' ? 'Current Outstanding (₹)' : 'Current Price (₹)'}
              </label>
              <input
                type="number"
                step="any"
                placeholder="Leave blank to use buy price"
                value={currentPriceRupees}
                onChange={e => setCurrentPriceRupees(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>
          </div>

          {/* SGB or FD specific rates */}
          {(category === 'GOLD_SGB' || category === 'FIXED_INCOME' || category === 'LIABILITY') && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 p-3.5 bg-slate-950/80 rounded-2xl border border-slate-800">
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  {category === 'GOLD_SGB' ? 'SGB Coupon Rate (% p.a.)' : category === 'LIABILITY' ? 'Loan Interest Rate (%)' : 'Interest Rate (% p.a.)'}
                </label>
                <input
                  type="number"
                  step="0.01"
                  placeholder={category === 'GOLD_SGB' ? '2.5' : '7.25'}
                  value={couponOrInterestRate}
                  onChange={e => setCouponOrInterestRate(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-sm text-slate-100"
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">Maturity / End Date</label>
                <input
                  type="date"
                  value={maturityDate}
                  onChange={e => setMaturityDate(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-lg text-sm text-slate-100"
                />
              </div>
            </div>
          )}

          {/* Real Estate Location */}
          {category === 'REAL_ESTATE' && (
            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-1">Property Location / Address</label>
              <input
                type="text"
                placeholder="e.g. Flat 402, Green Glen Layout, Bengaluru"
                value={location}
                onChange={e => setLocation(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100"
              />
            </div>
          )}

          {/* Institution & Notes */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">Institution / Broker / Bank</label>
              <input
                type="text"
                placeholder="e.g. Zerodha / HDFC Bank / SBI"
                value={institutionName}
                onChange={e => setInstitutionName(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500"
              />
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-400 block mb-1">Private Notes</label>
              <input
                type="text"
                placeholder="e.g. Long term retirement core"
                value={notes}
                onChange={e => setNotes(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500"
              />
            </div>
          </div>

          <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-800">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-sm font-semibold text-slate-400 hover:text-slate-200 cursor-pointer"
            >
              Cancel
            </button>

            <button
              type="submit"
              className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-sm font-bold rounded-xl shadow-md shadow-amber-400/10 transition-all cursor-pointer"
            >
              Save Holding
            </button>
          </div>

        </form>
      </div>
    </div>
  );
};
