// FILE: src/components/modals/HoldingDetailModal.tsx
import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, Holding } from '../../types';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  ExposureNode,
  Wrapper,
  EXPOSURES,
  WRAPPERS,
  exposureLabel,
  l2Label,
  wrapperLabel,
  classifyHolding,
} from '../../lib/taxonomy';
import { resolveSplit, isHybrid, SplitSlice } from '../../lib/lookthrough';
import {
  X,
  Clock,
  Coins,
  ShieldCheck,
  TrendingUp,
  Edit3,
  Calendar,
  Layers,
  Building,
  CheckCircle2,
  Trash2,
  Plus,
  Tag,
  Target,
  Sliders,
  AlertCircle,
} from 'lucide-react';

interface HoldingDetailModalProps {
  holdingId: string | null;
  onClose: () => void;
  onOpenAddRecord: (holdingId: string) => void;
}

export const HoldingDetailModal: React.FC<HoldingDetailModalProps> = ({
  holdingId,
  onClose,
  onOpenAddRecord,
}) => {
  const {
    holdings,
    ledgerEvents,
    familyMembers,
    updateHolding,
    updateHoldingPrice,
    deleteHolding,
    sgbValuationMode,
  } = useKosha();

  const [isEditingPrice, setIsEditingPrice] = useState(false);
  const [newPriceInput, setNewPriceInput] = useState('');

  const [isEditingAll, setIsEditingAll] = useState(false);
  const [editName, setEditName] = useState('');
  const [editSymbol, setEditSymbol] = useState('');
  const [editQty, setEditQty] = useState('');
  const [editPrice, setEditPrice] = useState('');
  const [editAvgCost, setEditAvgCost] = useState('');

  // Classification & Split Editing state
  const [isEditingGrouping, setIsEditingGrouping] = useState(false);
  const [selectedExposure, setSelectedExposure] = useState<ExposureNode>('EQUITY');
  const [selectedL2, setSelectedL2] = useState<string>('LARGE_CAP');
  const [selectedWrapper, setSelectedWrapper] = useState<Wrapper>('DIRECT');

  // Hybrid Split State
  const [slicesEdit, setSlicesEdit] = useState<{ exposure: ExposureNode; percent: number }[]>([]);
  const [groupingError, setGroupingError] = useState<string | null>(null);

  if (!holdingId) return null;

  const holding = holdings.find(h => h.id === holdingId);
  if (!holding) return null;

  const catInfo = ASSET_CATEGORIES[holding.category];
  const owner = familyMembers.find(m => m.id === holding.ownerId);

  const qtyScaled = BigInt(holding.quantityScaled || '0');
  const costPerUnitMinor = BigInt(holding.averageCostPerUnitMinor || '0');
  
  let currentPriceMinor = BigInt(holding.currentPriceMinor || '0');
  if (holding.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && holding.benchmarkPriceMinor) {
    currentPriceMinor = BigInt(holding.benchmarkPriceMinor);
  }

  const totalValueMinor = (qtyScaled * currentPriceMinor) / QTY_SCALE;
  const totalCostMinor = (qtyScaled * costPerUnitMinor) / QTY_SCALE;
  const gainMinor = totalValueMinor - totalCostMinor;
  const gainPercent =
    Number(totalCostMinor) > 0 ? (Number(gainMinor) / Number(totalCostMinor)) * 100 : 0;
  const gainFormatted = MoneyEngine.formatChange(gainMinor, gainPercent);

  // Associated ledger events
  const holdingEvents = ledgerEvents.filter(ev => ev.holdingId === holding.id);

  // Placement & split for display
  const currentPlacement = classifyHolding(holding);
  const currentSplit = resolveSplit(holding, currentPlacement);
  const hybrid = isHybrid(currentSplit);

  const handleStartEditGrouping = () => {
    setSelectedExposure(currentPlacement.exposure);
    setSelectedL2(currentPlacement.l2);
    setSelectedWrapper(currentPlacement.wrapper);
    
    // Set slices
    setSlicesEdit(
      currentSplit.slices.map(s => ({
        exposure: s.exposure,
        percent: s.basisPoints / 100,
      }))
    );
    setGroupingError(null);
    setIsEditingGrouping(true);
  };

  const handleExposureChange = (newExposure: ExposureNode) => {
    setSelectedExposure(newExposure);
    const expMeta = EXPOSURES.find(e => e.id === newExposure);
    if (expMeta && expMeta.l2.length > 0) {
      setSelectedL2(expMeta.l2[0].id);
    }
  };

  const handleSlicePercentChange = (index: number, val: number) => {
    const next = [...slicesEdit];
    next[index] = { ...next[index], percent: val };
    setSlicesEdit(next);
  };

  const handleAddSlice = () => {
    setSlicesEdit(prev => [...prev, { exposure: 'DEBT', percent: 0 }]);
  };

  const handleRemoveSlice = (index: number) => {
    if (slicesEdit.length <= 1) return;
    setSlicesEdit(prev => prev.filter((_, i) => i !== index));
  };

  const handleSaveGrouping = () => {
    // If hybrid or editing slices
    if (slicesEdit.length > 1) {
      const sumPercent = slicesEdit.reduce((sum, s) => sum + (Number(s.percent) || 0), 0);
      if (Math.round(sumPercent * 100) !== 10000) {
        setGroupingError(`Percentages must sum to exactly 100% (currently ${sumPercent}%).`);
        return;
      }
    }

    const updates: Partial<Holding> = {
      exposureNode: selectedExposure,
      l2Node: selectedL2,
      wrapper: selectedWrapper,
    };

    if (slicesEdit.length > 1) {
      updates.allocationSplit = slicesEdit.map(s => ({
        exposure: s.exposure,
        percent: Number(s.percent),
      }));
    } else {
      updates.allocationSplit = [
        {
          exposure: selectedExposure,
          percent: 100,
        },
      ];
    }

    updateHolding(holding.id, updates);
    setIsEditingGrouping(false);
    setGroupingError(null);
  };

  const handleStartEditAll = () => {
    setEditName(holding.name);
    setEditSymbol(holding.symbolOrFolio);
    setEditQty(MoneyEngine.toQty(holding.quantityScaled).toString());
    setEditPrice(MoneyEngine.toRupees(BigInt(holding.currentPriceMinor || '0')).toString());
    setEditAvgCost(MoneyEngine.toRupees(BigInt(holding.averageCostPerUnitMinor || '0')).toString());
    setIsEditingAll(true);
  };

  const handleSaveEditAll = () => {
    const qtyVal = parseFloat(editQty);
    const priceVal = parseFloat(editPrice);
    const costVal = parseFloat(editAvgCost);

    if (!editName.trim()) {
      alert('Holding name is required');
      return;
    }
    if (isNaN(qtyVal) || qtyVal < 0) {
      alert('Please enter a valid quantity');
      return;
    }
    if (isNaN(priceVal) || priceVal < 0) {
      alert('Please enter a valid price');
      return;
    }

    const updates: Partial<Holding> = {
      name: editName.trim(),
      symbolOrFolio: editSymbol.trim() || holding.symbolOrFolio,
      quantityScaled: MoneyEngine.fromQty(qtyVal).toString(),
      currentPriceMinor: MoneyEngine.fromRupees(priceVal).toString(),
      averageCostPerUnitMinor: !isNaN(costVal) && costVal >= 0 ? MoneyEngine.fromRupees(costVal).toString() : holding.averageCostPerUnitMinor,
      lastPriceUpdated: new Date().toISOString(),
      priceSource: 'Manual Verified Entry',
    };

    updateHolding(holding.id, updates);
    setIsEditingAll(false);
  };

  const handleSavePrice = () => {
    const p = parseFloat(newPriceInput);
    if (!isNaN(p) && p > 0) {
      updateHoldingPrice(holding.id, p);
      setIsEditingPrice(false);
      setNewPriceInput('');
    }
  };

  const availableL2Options = EXPOSURES.find(e => e.id === selectedExposure)?.l2 || [];
  const currentSlicesSum = slicesEdit.reduce((sum, s) => sum + (Number(s.percent) || 0), 0);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in">
      <div
        className="bg-slate-900 rounded-3xl max-w-2xl w-full p-6 sm:p-7 shadow-2xl border border-slate-800 max-h-[90vh] overflow-y-auto space-y-6 text-slate-100"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-start justify-between border-b border-slate-800 pb-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <span
                className="w-3 h-3 rounded-full ring-2 ring-slate-800"
                style={{ backgroundColor: catInfo.color }}
              />
              <h2 className="text-xl font-bold text-white font-serif">{holding.name}</h2>
              {owner && (
                <span
                  className="text-xs font-semibold px-2 py-0.5 rounded-full text-white ring-1 ring-white/20"
                  style={{ backgroundColor: owner.color }}
                >
                  {owner.name} ({owner.relation})
                </span>
              )}
              <button
                onClick={handleStartEditAll}
                className="ml-2 px-2.5 py-0.5 text-xs bg-slate-800 hover:bg-slate-700 text-amber-300 rounded-lg border border-slate-700 flex items-center gap-1 font-medium transition-colors cursor-pointer"
                title="Edit holding name, units, price, cost"
              >
                <Edit3 className="w-3 h-3 text-amber-400" />
                <span>Edit</span>
              </button>
            </div>
            <div className="text-xs text-slate-400 flex items-center gap-2 flex-wrap">
              <span className="font-mono font-medium text-slate-300">{holding.symbolOrFolio}</span>
              <span>•</span>
              <span>{catInfo.name} ({holding.subCategory})</span>
              {holding.institutionName && (
                <>
                  <span>•</span>
                  <span>{holding.institutionName}</span>
                </>
              )}
            </div>

            {/* Display Tags */}
            {([holding.tag, ...(holding.tags || [])].filter(Boolean) as string[]).length > 0 && (
              <div className="flex items-center gap-1.5 flex-wrap pt-1">
                {Array.from(new Set([holding.tag, ...(holding.tags || [])].filter(Boolean) as string[])).map(t => (
                  <span
                    key={t}
                    className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-lg text-xs font-semibold bg-amber-400/15 text-amber-300 border border-amber-500/30"
                  >
                    <Tag className="w-3 h-3 text-amber-400" />
                    <span>{t}</span>
                  </span>
                ))}
              </div>
            )}
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Inline Edit Form */}
        {isEditingAll && (
          <div className="bg-slate-950 p-5 rounded-2xl border border-amber-500/40 space-y-4 animate-in fade-in">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-amber-300 flex items-center gap-1.5">
                <Edit3 className="w-4 h-4 text-amber-400" />
                <span>Edit Holding Details</span>
              </h3>
              <span className="text-[11px] text-slate-400">Updates will recalculate valuation immediately</span>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <div>
                <label className="block text-slate-400 mb-1">Holding / Scheme Name</label>
                <input
                  type="text"
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 focus:outline-none focus:border-amber-400 text-xs"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Folio / Ticker / ISIN</label>
                <input
                  type="text"
                  value={editSymbol}
                  onChange={e => setEditSymbol(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 focus:outline-none focus:border-amber-400 text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Units Quantity</label>
                <input
                  type="number"
                  step="any"
                  value={editQty}
                  onChange={e => setEditQty(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 focus:outline-none focus:border-amber-400 text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Current Unit Price (₹)</label>
                <input
                  type="number"
                  step="any"
                  value={editPrice}
                  onChange={e => setEditPrice(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 focus:outline-none focus:border-amber-400 text-xs font-mono"
                />
              </div>

              <div>
                <label className="block text-slate-400 mb-1">Average Buy Cost / Unit (₹)</label>
                <input
                  type="number"
                  step="any"
                  value={editAvgCost}
                  onChange={e => setEditAvgCost(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 focus:outline-none focus:border-amber-400 text-xs font-mono"
                />
              </div>

              <div className="flex items-end gap-2 pt-1">
                <button
                  onClick={handleSaveEditAll}
                  className="flex-1 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 font-bold rounded-xl text-xs cursor-pointer transition-colors shadow-md"
                >
                  Save Changes
                </button>
                <button
                  onClick={() => setIsEditingAll(false)}
                  className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-xs cursor-pointer transition-colors"
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Current Valuation & Gain Bento Card */}
        <div className="bg-slate-950/80 rounded-2xl p-5 border border-slate-800 grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <div className="text-xs text-slate-400 font-medium">
              {catInfo.isLiability ? 'Principal Remaining' : 'Current Worth'}
            </div>
            <PrivacyValue
              minor={totalValueMinor}
              className={`text-xl font-bold ${catInfo.isLiability ? 'text-rose-400' : 'text-slate-100'}`}
              showCompact
            />
            <div className="text-xs text-slate-400 mt-0.5">
              {MoneyEngine.formatQty(holding.quantityScaled)} {holding.category === 'GOLD_SGB' ? 'grams' : 'units'}
            </div>
          </div>

          <div>
            <div className="text-xs text-slate-400 font-medium">Current Price / Unit</div>
            <div className="text-xl font-bold text-slate-100">
              ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(currentPriceMinor))}
            </div>
            <div className="text-xs text-slate-400 mt-0.5">
              Avg Cost: ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(costPerUnitMinor))}
            </div>
          </div>

          {!catInfo.isLiability && (
            <div>
              <div className="text-xs text-slate-400 font-medium">Total Growth</div>
              <div
                className={`text-base font-bold ${
                  gainFormatted.isPositive ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {gainFormatted.fullText}
              </div>
              <div className="text-xs text-slate-400 mt-0.5">
                Invested: ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(totalCostMinor))}
              </div>
            </div>
          )}
        </div>

        {/* TASK 6B: "How this is grouped" Section */}
        <div className="bg-slate-950/80 rounded-2xl p-5 border border-slate-800 space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-100 flex items-center gap-2">
              <Sliders className="w-4 h-4 text-amber-400" />
              <span>How this is grouped</span>
            </h3>
            {!isEditingGrouping && (
              <button
                onClick={handleStartEditGrouping}
                className="px-2.5 py-1 text-xs bg-slate-800 hover:bg-slate-700 text-amber-300 rounded-lg border border-slate-700 flex items-center gap-1 font-medium transition-colors cursor-pointer"
              >
                <Edit3 className="w-3 h-3 text-amber-400" />
                <span>Change</span>
              </button>
            )}
          </div>

          {!isEditingGrouping ? (
            /* Read-Only Placement View */
            <div className="space-y-3 text-xs sm:text-sm">
              <div className="p-3.5 bg-slate-900/90 rounded-xl border border-slate-800 text-slate-200">
                <span>
                  Grouped under{' '}
                  <strong className="text-white">{exposureLabel(currentPlacement.exposure)}</strong>,{' '}
                  <strong className="text-white">{l2Label(currentPlacement.exposure, currentPlacement.l2)}</strong>,{' '}
                  held {currentPlacement.wrapper === 'DIRECT' ? 'directly' : `through ${wrapperLabel(currentPlacement.wrapper)}`}
                </span>
                {currentPlacement.inferred && (
                  <div className="text-xs text-slate-400 mt-1">
                    We worked this out automatically.
                  </div>
                )}
              </div>

              {/* Hybrid Split Display */}
              {hybrid && (
                <div className="p-3.5 bg-slate-900/90 rounded-xl border border-slate-800 space-y-2">
                  <div className="text-xs font-semibold text-slate-300 flex items-center justify-between">
                    <span>Portfolio Split</span>
                    <span className="text-slate-500 font-normal">{currentSplit.source}</span>
                  </div>
                  <div className="flex items-center gap-2 flex-wrap">
                    {currentSplit.slices.map((slice, idx) => (
                      <span
                        key={idx}
                        className="px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 text-xs font-medium text-slate-200"
                      >
                        {exposureLabel(slice.exposure)}: <strong>{slice.basisPoints / 100}%</strong>
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            /* Editing Placement Form */
            <div className="space-y-4 animate-in fade-in text-xs">
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {/* What it's exposed to */}
                <div>
                  <label className="block text-slate-400 mb-1 font-medium">What it's exposed to</label>
                  <select
                    value={selectedExposure}
                    onChange={e => handleExposureChange(e.target.value as ExposureNode)}
                    className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 text-xs focus:outline-none focus:border-amber-400"
                  >
                    {EXPOSURES.map(exp => (
                      <option key={exp.id} value={exp.id}>
                        {exp.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Which kind */}
                <div>
                  <label className="block text-slate-400 mb-1 font-medium">Which kind</label>
                  <select
                    value={selectedL2}
                    onChange={e => setSelectedL2(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 text-xs focus:outline-none focus:border-amber-400"
                  >
                    {availableL2Options.map(l2 => (
                      <option key={l2.id} value={l2.id}>
                        {l2.label}
                      </option>
                    ))}
                  </select>
                </div>

                {/* How you hold it */}
                <div>
                  <label className="block text-slate-400 mb-1 font-medium">How you hold it</label>
                  <select
                    value={selectedWrapper}
                    onChange={e => setSelectedWrapper(e.target.value as Wrapper)}
                    className="w-full px-3 py-2 bg-slate-900 border border-slate-700 rounded-xl text-slate-100 text-xs focus:outline-none focus:border-amber-400"
                  >
                    {WRAPPERS.map(w => (
                      <option key={w.id} value={w.id}>
                        {w.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Editable Hybrid Splits */}
              <div className="p-4 bg-slate-900 rounded-xl border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="font-semibold text-slate-200">Portfolio Split</div>
                  <button
                    type="button"
                    onClick={handleAddSlice}
                    className="text-xs text-amber-400 hover:text-amber-300 font-semibold flex items-center gap-1 cursor-pointer"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Add Slice</span>
                  </button>
                </div>

                <div className="space-y-2">
                  {slicesEdit.map((slice, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <select
                        value={slice.exposure}
                        onChange={e => {
                          const next = [...slicesEdit];
                          next[idx] = { ...next[idx], exposure: e.target.value as ExposureNode };
                          setSlicesEdit(next);
                        }}
                        className="flex-1 px-3 py-1.5 bg-slate-950 border border-slate-700 rounded-lg text-slate-100 text-xs focus:outline-none focus:border-amber-400"
                      >
                        {EXPOSURES.map(exp => (
                          <option key={exp.id} value={exp.id}>
                            {exp.label}
                          </option>
                        ))}
                      </select>

                      <div className="flex items-center gap-1 w-24">
                        <input
                          type="number"
                          step="any"
                          min="0"
                          max="100"
                          value={slice.percent}
                          onChange={e => handleSlicePercentChange(idx, parseFloat(e.target.value) || 0)}
                          className="w-16 px-2 py-1.5 bg-slate-950 border border-slate-700 rounded-lg text-slate-100 text-xs font-mono focus:outline-none focus:border-amber-400 text-right"
                        />
                        <span className="text-slate-400 text-xs">%</span>
                      </div>

                      {slicesEdit.length > 1 && (
                        <button
                          type="button"
                          onClick={() => handleRemoveSlice(idx)}
                          className="p-1.5 text-slate-500 hover:text-rose-400 cursor-pointer"
                        >
                          <X className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  ))}
                </div>

                <div className="flex items-center justify-between text-xs pt-2 border-t border-slate-800 text-slate-400">
                  <span>Source: {currentSplit.source}</span>
                  <span className={`font-mono font-bold ${Math.round(currentSlicesSum * 100) === 10000 ? 'text-emerald-400' : 'text-rose-400'}`}>
                    Total: {currentSlicesSum.toFixed(1)}%
                  </span>
                </div>
              </div>

              {groupingError && (
                <div className="p-3 rounded-xl bg-rose-950/50 border border-rose-800/80 text-rose-300 text-xs flex items-center gap-2">
                  <AlertCircle className="w-4 h-4 flex-shrink-0 text-rose-400" />
                  <span>{groupingError}</span>
                </div>
              )}

              <div className="flex items-center justify-end gap-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsEditingGrouping(false)}
                  className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-xl text-xs cursor-pointer transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleSaveGrouping}
                  className="px-5 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 font-bold rounded-xl text-xs cursor-pointer transition-colors shadow-md"
                >
                  Save Grouping
                </button>
              </div>
            </div>
          )}
        </div>

        {/* SGB Dual Valuation Comparison (SRS §6.3 / SRS-VAL-005) */}
        {holding.category === 'GOLD_SGB' && (
          <div className="bg-amber-950/30 rounded-2xl p-5 border border-amber-700/40 space-y-3">
            <div className="flex items-center gap-2 text-amber-300 font-bold text-sm">
              <Coins className="w-4 h-4 text-amber-400" />
              <span>SGB Dual Valuation & Sovereign Coupon Schedule</span>
            </div>
            
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
              <div className="p-3.5 bg-slate-900 rounded-xl border border-slate-800">
                <div className="text-slate-400">RBI Issue Price</div>
                <div className="font-bold text-slate-100 mt-0.5">
                  ₹ {holding.issuePriceMinor ? MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(holding.issuePriceMinor))) : '3,119'} / gram
                </div>
              </div>

              <div className="p-3.5 bg-slate-900 rounded-xl border border-slate-800">
                <div className="text-slate-400">NSE Secondary Market</div>
                <div className="font-bold text-slate-100 mt-0.5">
                  ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(holding.currentPriceMinor)))} / gram
                </div>
              </div>

              <div className="p-3.5 bg-slate-900 rounded-xl border border-emerald-900/50">
                <div className="text-slate-400">RBI IBJA Gold Benchmark</div>
                <div className="font-bold text-emerald-400 mt-0.5">
                  ₹ {holding.benchmarkPriceMinor ? MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(holding.benchmarkPriceMinor))) : '7,580'} / gram
                </div>
              </div>
            </div>

            <div className="text-xs text-amber-200 leading-relaxed bg-amber-950/60 p-3 rounded-xl border border-amber-800/40">
              ✨ <strong>Sovereign Guarantee:</strong> SGB coupon of 2.50% p.a. is computed on the original issue price (₹{holding.issuePriceMinor ? MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(holding.issuePriceMinor))) : '3,119'}), and all capital gains are <strong>100% tax-free upon maturity with RBI</strong>.
            </div>
          </div>
        )}

        {/* Price Freshness & Manual Update */}
        <div className="flex flex-wrap items-center justify-between gap-3 p-4 bg-slate-950/80 rounded-2xl border border-slate-800 text-xs">
          <div className="flex items-center gap-2 text-slate-300">
            <Clock className="w-4 h-4 text-slate-400" />
            <span>Price Source: <strong>{holding.priceSource}</strong></span>
          </div>

          <div>
            {!isEditingPrice ? (
              <button
                onClick={() => setIsEditingPrice(true)}
                className="px-3 py-1.5 bg-slate-800 border border-slate-700 hover:bg-slate-700 text-slate-200 rounded-xl font-semibold flex items-center gap-1 cursor-pointer transition-colors"
              >
                <Edit3 className="w-3 h-3" />
                <span>Update Price</span>
              </button>
            ) : (
              <div className="flex items-center gap-1.5">
                <input
                  type="number"
                  placeholder="New price ₹"
                  value={newPriceInput}
                  onChange={e => setNewPriceInput(e.target.value)}
                  className="w-28 px-2.5 py-1 bg-slate-900 border border-slate-700 rounded-lg text-xs text-slate-100"
                />
                <button
                  onClick={handleSavePrice}
                  className="px-3 py-1 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-lg font-bold text-xs cursor-pointer"
                >
                  Save
                </button>
                <button
                  onClick={() => setIsEditingPrice(false)}
                  className="px-2.5 py-1 bg-slate-800 text-slate-300 rounded-lg text-xs cursor-pointer"
                >
                  Cancel
                </button>
              </div>
            )}
          </div>
        </div>

        {/* Transaction History for this holding */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <h3 className="text-sm font-bold text-slate-100">Holding Transaction History</h3>
            <button
              onClick={() => {
                onClose();
                onOpenAddRecord(holding.id);
              }}
              className="text-xs font-semibold text-amber-400 hover:text-amber-300 flex items-center gap-1 cursor-pointer"
            >
              <Plus className="w-3.5 h-3.5" />
              <span>Record New Trade</span>
            </button>
          </div>

          <div className="divide-y divide-slate-800 border border-slate-800 rounded-2xl overflow-hidden bg-slate-950/60">
            {holdingEvents.length === 0 ? (
              <div className="p-4 text-center text-xs text-slate-400">
                Initial holding balance created.
              </div>
            ) : (
              holdingEvents.map(ev => (
                <div key={ev.id} className="p-3.5 flex items-center justify-between text-xs hover:bg-slate-800/40 transition-colors">
                  <div>
                    <div className="font-semibold text-slate-200">
                      {ev.eventType} • {MoneyEngine.formatQty(ev.quantityScaled)} units @ ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(ev.unitPriceMinor)))}
                    </div>
                    <div className="text-slate-400 text-[11px] mt-0.5">
                      {ev.eventDate} {ev.notes && `• ${ev.notes}`}
                    </div>
                  </div>

                  <div className="text-right font-bold text-slate-100">
                    ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(ev.totalAmountMinor)))}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-between pt-4 border-t border-slate-800">
          <button
            onClick={() => {
              if (window.confirm(`Delete ${holding.name} from your portfolio?`)) {
                deleteHolding(holding.id);
                onClose();
              }
            }}
            className="text-xs font-semibold text-rose-400 hover:text-rose-300 flex items-center gap-1 cursor-pointer"
          >
            <Trash2 className="w-3.5 h-3.5" />
            <span>Remove Holding</span>
          </button>

          <button
            onClick={onClose}
            className="px-5 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs font-bold shadow-md cursor-pointer transition-all"
          >
            Done
          </button>
        </div>

      </div>
    </div>
  );
};
