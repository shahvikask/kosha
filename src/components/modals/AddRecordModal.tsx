import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { EventType } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { displayHoldingName } from '../../lib/holdingView';
import { X, ArrowRightLeft, ShieldCheck, CheckCircle2 } from 'lucide-react';

interface AddRecordModalProps {
  isOpen: boolean;
  onClose: () => void;
  preselectedHoldingId?: string;
}

export const AddRecordModal: React.FC<AddRecordModalProps> = ({
  isOpen,
  onClose,
  preselectedHoldingId,
}) => {
  const { holdings, appendLedgerEvent, updateHolding, familyMembers } = useKosha();

  const [holdingId, setHoldingId] = useState<string>(
    preselectedHoldingId || (holdings[0]?.id || '')
  );
  const [eventType, setEventType] = useState<EventType>('BUY');
  const [eventDate, setEventDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [quantity, setQuantity] = useState<string>('1');
  const [unitPriceRupees, setUnitPriceRupees] = useState<string>('');
  const [totalAmountRupees, setTotalAmountRupees] = useState<string>('');
  const [chargesRupees, setChargesRupees] = useState<string>('0');
  const [notes, setNotes] = useState<string>('');

  if (!isOpen) return null;

  const selectedHolding = holdings.find(h => h.id === holdingId);

  // Auto calculate total amount
  const handlePriceOrQtyChange = (newQty: string, newPrice: string) => {
    const q = parseFloat(newQty) || 0;
    const p = parseFloat(newPrice) || 0;
    if (q > 0 && p > 0) {
      setTotalAmountRupees((q * p).toString());
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedHolding) return;

    const qtyNum = parseFloat(quantity) || 1;
    const priceNum = parseFloat(unitPriceRupees) || 0;
    const totalNum = parseFloat(totalAmountRupees) || (qtyNum * priceNum);
    const chargesNum = parseFloat(chargesRupees) || 0;

    const qtyScaled = MoneyEngine.fromQty(qtyNum).toString();
    const unitPriceMinor = MoneyEngine.fromRupees(priceNum).toString();
    const totalAmountMinor = MoneyEngine.fromRupees(totalNum).toString();
    const chargesMinor = MoneyEngine.fromRupees(chargesNum).toString();

    // Append to immutable ledger
    appendLedgerEvent({
      holdingId: selectedHolding.id,
      holdingName: selectedHolding.name,
      ownerId: selectedHolding.ownerId,
      eventType,
      eventDate,
      quantityScaled: qtyScaled,
      unitPriceMinor,
      totalAmountMinor,
      chargesMinor,
      notes: notes.trim() || undefined,
    });

    // Update holding balance
    const currentQtyScaled = BigInt(selectedHolding.quantityScaled || '0');
    const changeQtyScaled = BigInt(qtyScaled);

    if (eventType === 'BUY' || eventType === 'BONUS') {
      const newQtyScaled = currentQtyScaled + changeQtyScaled;
      updateHolding(selectedHolding.id, {
        quantityScaled: newQtyScaled.toString(),
      });
    } else if (eventType === 'SELL') {
      const newQtyScaled = currentQtyScaled > changeQtyScaled ? currentQtyScaled - changeQtyScaled : 0n;
      updateHolding(selectedHolding.id, {
        quantityScaled: newQtyScaled.toString(),
      });
    } else if (eventType === 'REPAYMENT' && selectedHolding.category === 'LIABILITY') {
      // Reduce loan principal
      const currentOutstanding = BigInt(selectedHolding.currentPriceMinor || '0');
      const paidPrincipal = MoneyEngine.fromRupees(totalNum);
      const newOutstanding = currentOutstanding > paidPrincipal ? currentOutstanding - paidPrincipal : 0n;
      updateHolding(selectedHolding.id, {
        currentPriceMinor: newOutstanding.toString(),
      });
    }

    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in">
      <div
        className="bg-slate-900 rounded-3xl max-w-lg w-full p-6 sm:p-7 shadow-2xl border border-slate-800 space-y-5 text-slate-100"
        onClick={e => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-lg font-bold text-white font-serif">Record Transaction</h2>
            <p className="text-xs text-slate-400">Append an action into your permanent local ledger</p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          
          {/* Target Holding */}
          <div>
            <label className="text-xs font-bold text-slate-300 block mb-1">Target Holding *</label>
            <select
              value={holdingId}
              onChange={e => setHoldingId(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm font-semibold text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
            >
              {holdings.map(h => (
                <option key={h.id} value={h.id}>
                  {displayHoldingName(h.name)} ({h.symbolOrFolio})
                </option>
              ))}
            </select>
          </div>

          {/* Action Type & Date */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Action Type *</label>
              <select
                value={eventType}
                onChange={e => setEventType(e.target.value as EventType)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm font-semibold text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              >
                <option value="BUY">BUY (Purchase / SIP)</option>
                <option value="SELL">SELL (Redemption / Sale)</option>
                <option value="DIVIDEND">DIVIDEND (Dividend Credit)</option>
                <option value="INTEREST">INTEREST (SGB / FD / Interest)</option>
                <option value="BONUS">BONUS (Bonus Issue)</option>
                <option value="REPAYMENT">REPAYMENT (Loan EMI)</option>
              </select>
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Date *</label>
              <input
                type="date"
                required
                value={eventDate}
                onChange={e => setEventDate(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>
          </div>

          {/* Quantity & Price */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Units / Qty *</label>
              <input
                type="number"
                step="any"
                required
                value={quantity}
                onChange={e => {
                  setQuantity(e.target.value);
                  handlePriceOrQtyChange(e.target.value, unitPriceRupees);
                }}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Price per unit (₹)</label>
              <input
                type="number"
                step="any"
                placeholder="e.g. 1540"
                value={unitPriceRupees}
                onChange={e => {
                  setUnitPriceRupees(e.target.value);
                  handlePriceOrQtyChange(quantity, e.target.value);
                }}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>

            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Total Amount (₹) *</label>
              <input
                type="number"
                step="any"
                required
                value={totalAmountRupees}
                onChange={e => setTotalAmountRupees(e.target.value)}
                className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm font-semibold text-amber-400 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              />
            </div>
          </div>

          {/* Charges / Brokerage / STT */}
          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1">Brokerage / STT / Stamp Duty (₹)</label>
            <input
              type="number"
              step="any"
              placeholder="0.00"
              value={chargesRupees}
              onChange={e => setChargesRupees(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
            />
          </div>

          {/* Notes */}
          <div>
            <label className="text-xs font-semibold text-slate-400 block mb-1">Notes / Description</label>
            <input
              type="text"
              placeholder="e.g. Monthly SIP execution on Zerodha"
              value={notes}
              onChange={e => setNotes(e.target.value)}
              className="w-full px-3.5 py-2 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
            />
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
              Append to Ledger
            </button>
          </div>

        </form>
      </div>
    </div>
  );
};
