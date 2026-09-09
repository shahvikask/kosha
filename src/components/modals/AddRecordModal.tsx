import React, { useEffect, useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { EventType, Holding } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { displayHoldingName } from '../../lib/holdingView';
import { X } from 'lucide-react';

interface AddRecordModalProps {
  isOpen: boolean;
  onClose: () => void;
  preselectedHoldingId?: string;
  /** When true, the form is locked to preselectedHoldingId (opened from a holding sheet). */
  lockToHolding?: boolean;
}

export const AddRecordModal: React.FC<AddRecordModalProps> = ({
  isOpen,
  onClose,
  preselectedHoldingId,
  lockToHolding = false,
}) => {
  const { holdings, filteredHoldings, selectedMemberId, appendLedgerEvent, updateHolding, familyMembers } =
    useKosha();

  const [holdingId, setHoldingId] = useState('');
  const [eventType, setEventType] = useState<EventType>('BUY');
  const [eventDate, setEventDate] = useState(new Date().toISOString().split('T')[0]);
  const [quantity, setQuantity] = useState('1');
  const [unitPriceRupees, setUnitPriceRupees] = useState('');
  const [totalAmountRupees, setTotalAmountRupees] = useState('');
  const [chargesRupees, setChargesRupees] = useState('0');
  const [notes, setNotes] = useState('');

  const pickerHoldings = lockToHolding ? holdings : filteredHoldings;

  useEffect(() => {
    if (!isOpen) return;
    const fallback = lockToHolding
      ? preselectedHoldingId || ''
      : preselectedHoldingId && pickerHoldings.some(h => h.id === preselectedHoldingId)
        ? preselectedHoldingId
        : pickerHoldings[0]?.id || '';
    setHoldingId(fallback);
    setEventType('BUY');
    setEventDate(new Date().toISOString().split('T')[0]);
    setQuantity('1');
    setUnitPriceRupees('');
    setTotalAmountRupees('');
    setChargesRupees('0');
    setNotes('');
  }, [isOpen, preselectedHoldingId, lockToHolding]);

  const grouped = useMemo(() => {
    const byOwner = new Map<string, Holding[]>();
    for (const h of pickerHoldings) {
      const list = byOwner.get(h.ownerId) || [];
      list.push(h);
      byOwner.set(h.ownerId, list);
    }
    return familyMembers
      .map(m => ({ member: m, holdings: byOwner.get(m.id) || [] }))
      .filter(g => g.holdings.length > 0);
  }, [pickerHoldings, familyMembers]);

  if (!isOpen) return null;

  const selectedHolding = holdings.find(h => h.id === holdingId);
  const owner = selectedHolding
    ? familyMembers.find(m => m.id === selectedHolding.ownerId)
    : undefined;
  const locked = lockToHolding && !!preselectedHoldingId;
  const showOwnerInPicker = selectedMemberId === 'ALL';

  const handlePriceOrQtyChange = (newQty: string, newPrice: string) => {
    const q = parseFloat(newQty) || 0;
    const p = parseFloat(newPrice) || 0;
    if (q > 0 && p > 0) setTotalAmountRupees((q * p).toString());
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedHolding) return;

    const qtyNum = parseFloat(quantity) || 1;
    const priceNum = parseFloat(unitPriceRupees) || 0;
    const totalNum = parseFloat(totalAmountRupees) || qtyNum * priceNum;
    const chargesNum = parseFloat(chargesRupees) || 0;

    appendLedgerEvent({
      holdingId: selectedHolding.id,
      holdingName: selectedHolding.name,
      ownerId: selectedHolding.ownerId,
      eventType,
      eventDate,
      quantityScaled: MoneyEngine.fromQty(qtyNum).toString(),
      unitPriceMinor: MoneyEngine.fromRupees(priceNum).toString(),
      totalAmountMinor: MoneyEngine.fromRupees(totalNum).toString(),
      chargesMinor: MoneyEngine.fromRupees(chargesNum).toString(),
      notes: notes.trim() || undefined,
    });

    const currentQtyScaled = BigInt(selectedHolding.quantityScaled || '0');
    const changeQtyScaled = MoneyEngine.fromQty(qtyNum);

    if (eventType === 'BUY' || eventType === 'BONUS') {
      updateHolding(selectedHolding.id, {
        quantityScaled: (currentQtyScaled + changeQtyScaled).toString(),
      });
    } else if (eventType === 'SELL') {
      const next = currentQtyScaled > changeQtyScaled ? currentQtyScaled - changeQtyScaled : 0n;
      updateHolding(selectedHolding.id, { quantityScaled: next.toString() });
    } else if (eventType === 'REPAYMENT' && selectedHolding.category === 'LIABILITY') {
      const currentOutstanding = BigInt(selectedHolding.currentPriceMinor || '0');
      const paid = MoneyEngine.fromRupees(totalNum);
      const newOutstanding = currentOutstanding > paid ? currentOutstanding - paid : 0n;
      updateHolding(selectedHolding.id, { currentPriceMinor: newOutstanding.toString() });
    }

    onClose();
  };

  const optionLabel = (h: Holding) => {
    const name = displayHoldingName(h.name);
    const ident = h.symbolOrFolio ? ` (${h.symbolOrFolio})` : '';
    if (!showOwnerInPicker) return `${name}${ident}`;
    const who = familyMembers.find(m => m.id === h.ownerId);
    return `${name}${ident} · ${who ? who.name : 'Unknown'}`;
  };

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md animate-in fade-in">
      <div
        className="bg-slate-900 rounded-3xl max-w-lg w-full p-6 sm:p-7 shadow-2xl border border-slate-800 space-y-5 text-slate-100 max-h-[90vh] overflow-y-auto"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div className="min-w-0 pr-2">
            <h2 className="text-lg font-bold text-white font-serif">Record Transaction</h2>
            <p className="text-xs text-slate-400 truncate">
              {locked && selectedHolding
                ? `On ${displayHoldingName(selectedHolding.name)}${owner ? ` · ${owner.name}` : ''}`
                : selectedMemberId === 'ALL'
                  ? 'Pick a holding. Names include the family member.'
                  : 'Holdings for the profile you are viewing.'}
            </p>
          </div>
          <button
            onClick={onClose}
            className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4">
          {locked ? (
            <div className="px-3.5 py-2.5 rounded-xl border border-slate-700 bg-slate-950 text-sm">
              <div className="text-[11px] text-slate-400 font-semibold uppercase tracking-wider">Holding</div>
              <div className="font-semibold text-slate-100 truncate">
                {selectedHolding ? displayHoldingName(selectedHolding.name) : '—'}
              </div>
              {owner && <div className="text-xs text-slate-400">{owner.name} ({owner.relation})</div>}
            </div>
          ) : (
            <div>
              <label className="text-xs font-bold text-slate-300 block mb-1">Target Holding *</label>
              <select
                value={holdingId}
                onChange={e => setHoldingId(e.target.value)}
                required
                className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm font-semibold text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
              >
                {pickerHoldings.length === 0 && <option value="">No holdings in this profile</option>}
                {showOwnerInPicker
                  ? grouped.map(g => (
                      <optgroup key={g.member.id} label={`${g.member.name} (${g.member.relation})`}>
                        {g.holdings.map(h => (
                          <option key={h.id} value={h.id}>
                            {optionLabel(h)}
                          </option>
                        ))}
                      </optgroup>
                    ))
                  : pickerHoldings.map(h => (
                      <option key={h.id} value={h.id}>
                        {optionLabel(h)}
                      </option>
                    ))}
              </select>
            </div>
          )}

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
              disabled={!selectedHolding}
              className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-sm font-bold rounded-xl cursor-pointer disabled:opacity-50"
            >
              Append to Ledger
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
