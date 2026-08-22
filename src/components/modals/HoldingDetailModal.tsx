import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, Holding } from '../../types';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
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
    updateHoldingPrice,
    deleteHolding,
    sgbValuationMode,
  } = useKosha();

  const [isEditingPrice, setIsEditingPrice] = useState(false);
  const [newPriceInput, setNewPriceInput] = useState('');

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

  const handleSavePrice = () => {
    const p = parseFloat(newPriceInput);
    if (!isNaN(p) && p > 0) {
      updateHoldingPrice(holding.id, p);
      setIsEditingPrice(false);
      setNewPriceInput('');
    }
  };

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
          </div>

          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

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
