import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, EmiStatus } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { PrivacyValue, PrivacyDelta } from '../common/PrivacyValue';
import {
  metricsOf,
  eventLabel,
  unitWord,
  formatCalendarDate,
  whereHeld,
  displayHoldingName,
} from '../../lib/holdingView';
import { X, Edit3, Plus, Trash2, Calendar, Coins } from 'lucide-react';
import { resumeEmiDate } from '../../lib/emi';

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
    accounts,
    updateHolding,
    updateHoldingPrice,
    deleteHolding,
    sgbValuationMode,
    derived,
  } = useKosha();

  const [isEditingPrice, setIsEditingPrice] = useState(false);
  const [newPriceInput, setNewPriceInput] = useState('');
  const [isEditingAll, setIsEditingAll] = useState(false);
  const [editName, setEditName] = useState('');
  const [editSymbol, setEditSymbol] = useState('');
  const [editPrice, setEditPrice] = useState('');
  const [editCoupon, setEditCoupon] = useState('');
  const [editNextCoupon, setEditNextCoupon] = useState('');
  const [editMaturity, setEditMaturity] = useState('');
  const [editEmi, setEditEmi] = useState('');
  const [editRemaining, setEditRemaining] = useState('');
  const [editRate, setEditRate] = useState('');

  if (!holdingId) return null;
  const holding = holdings.find(h => h.id === holdingId);
  if (!holding) return null;

  const catInfo = ASSET_CATEGORIES[holding.category];
  const owner = familyMembers.find(m => m.id === holding.ownerId);
  const m = metricsOf(holding, derived, sgbValuationMode);
  const holdingEvents = ledgerEvents
    .filter(ev => ev.holdingId === holding.id)
    .sort((a, b) => b.eventDate.localeCompare(a.eventDate));
  const acct = accounts.find(a => a.id === holding.accountId);
  const isElss = /elss|tax.?sav/i.test(`${holding.name} ${holding.subCategory} ${holding.schemeType || ''}`);
  const isGovt = /ppf|epf|nps|sukanya|ssy/i.test(`${holding.name} ${holding.subCategory}`);

  const handleStartEditAll = () => {
    setEditName(holding.name);
    setEditSymbol(holding.symbolOrFolio);
    setEditPrice(MoneyEngine.toRupees(m.price).toString());
    setEditCoupon(holding.couponRatePercent != null ? String(holding.couponRatePercent) : '');
    setEditNextCoupon(holding.nextCouponDate || '');
    setEditMaturity(holding.maturityDate || '');
    setEditEmi(holding.monthlyEmiMinor ? String(MoneyEngine.toRupees(BigInt(holding.monthlyEmiMinor))) : '');
    setEditRemaining(holding.remainingEmiCount != null ? String(holding.remainingEmiCount) : '');
    setEditRate(holding.interestRatePercent != null ? String(holding.interestRatePercent) : '');
    setIsEditingAll(true);
  };

  const handleSaveEditAll = () => {
    const priceVal = parseFloat(editPrice);
    if (!editName.trim()) return;
    if (isNaN(priceVal) || priceVal < 0) return;
    const coupon = parseFloat(editCoupon);
    const rate = parseFloat(editRate);
    const emiVal = parseFloat(editEmi);
    const remainVal = parseInt(editRemaining, 10);
    updateHolding(holding.id, {
      name: editName.trim(),
      symbolOrFolio: editSymbol.trim() || holding.symbolOrFolio,
      currentPriceMinor: MoneyEngine.fromRupees(priceVal).toString(),
      lastPriceUpdated: new Date().toISOString(),
      priceSource: 'Manual Verified Entry',
      couponRatePercent: !isNaN(coupon) ? coupon : holding.couponRatePercent,
      interestRatePercent: !isNaN(rate) ? rate : holding.interestRatePercent,
      nextCouponDate: editNextCoupon || undefined,
      maturityDate: editMaturity || undefined,
      monthlyEmiMinor: !isNaN(emiVal) && emiVal > 0 ? MoneyEngine.fromRupees(emiVal).toString() : holding.monthlyEmiMinor,
      remainingEmiCount: !isNaN(remainVal) ? remainVal : holding.remainingEmiCount,
    });
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

  const field = (label: string, value?: string | null) =>
    value ? (
      <div className="text-xs">
        <div style={{ color: 'var(--muted)' }}>{label}</div>
        <div className="font-medium mt-0.5 break-all">{value}</div>
      </div>
    ) : null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60">
      <div
        className="card max-w-2xl w-full p-6 sm:p-7 max-h-[90vh] overflow-y-auto space-y-6"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="space-y-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="w-3 h-3 rounded-full" style={{ backgroundColor: catInfo.color }} />
              <h2 className="text-xl font-bold">{displayHoldingName(holding.name)}</h2>
              {owner && (
                <span
                  className="text-xs font-semibold px-2 py-0.5 rounded-full text-white"
                  style={{ backgroundColor: owner.color }}
                >
                  {owner.name}
                </span>
              )}
            </div>
            <div className="text-xs" style={{ color: 'var(--muted)' }}>
              {catInfo.shortName}
              {holding.subCategory ? ` · ${holding.subCategory}` : ''}
              {` · ${whereHeld(holding)}`}
            </div>
          </div>
          <div className="flex items-center gap-1">
            <button
              onClick={handleStartEditAll}
              className="px-2.5 py-1 text-xs rounded-lg border flex items-center gap-1"
              style={{ borderColor: 'var(--border)' }}
            >
              <Edit3 className="w-3 h-3" /> Edit
            </button>
            <button onClick={onClose} className="p-2 rounded-xl">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {isEditingAll && (
          <div className="p-4 rounded-2xl border space-y-3" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
              <label className="block">
                <span style={{ color: 'var(--muted)' }}>Name</span>
                <input
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  className="w-full mt-1 px-3 py-2 rounded-xl border"
                  style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                />
              </label>
              <label className="block">
                <span style={{ color: 'var(--muted)' }}>Folio / ticker</span>
                <input
                  value={editSymbol}
                  onChange={e => setEditSymbol(e.target.value)}
                  className="w-full mt-1 px-3 py-2 rounded-xl border font-mono"
                  style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                />
              </label>
              <label className="block">
                <span style={{ color: 'var(--muted)' }}>{holding.category === 'LIABILITY' ? 'Outstanding (₹)' : 'Price per unit (₹)'}</span>
                <input
                  type="number"
                  step="any"
                  value={editPrice}
                  onChange={e => setEditPrice(e.target.value)}
                  className="w-full mt-1 px-3 py-2 rounded-xl border"
                  style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                />
              </label>
              {(holding.category === 'GOLD_SGB' || holding.category === 'FIXED_INCOME') && (
                <>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Interest / coupon % a year</span>
                    <input
                      type="number"
                      step="any"
                      value={editCoupon}
                      onChange={e => setEditCoupon(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Next interest date</span>
                    <input
                      type="date"
                      value={editNextCoupon}
                      onChange={e => setEditNextCoupon(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Matures / lock-in ends</span>
                    <input
                      type="date"
                      value={editMaturity}
                      onChange={e => setEditMaturity(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                </>
              )}
              {holding.category === 'LIABILITY' && (
                <>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Monthly EMI (₹)</span>
                    <input
                      type="number"
                      step="any"
                      value={editEmi}
                      onChange={e => setEditEmi(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Interest % a year</span>
                    <input
                      type="number"
                      step="any"
                      value={editRate}
                      onChange={e => setEditRate(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Remaining EMI months</span>
                    <input
                      type="number"
                      step="1"
                      value={editRemaining}
                      onChange={e => setEditRemaining(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                  <label className="block">
                    <span style={{ color: 'var(--muted)' }}>Next EMI date</span>
                    <input
                      type="date"
                      value={editNextCoupon}
                      onChange={e => setEditNextCoupon(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl border"
                      style={{ background: 'var(--card)', borderColor: 'var(--border)', color: 'var(--text)' }}
                    />
                  </label>
                </>
              )}
            </div>
            <p className="text-[11px]" style={{ color: 'var(--muted)' }}>
              Units come from your statement. To change them, record a buy or sell.
            </p>
            <div className="flex gap-2">
              <button
                onClick={handleSaveEditAll}
                className="px-4 py-2 rounded-xl text-xs font-bold"
                style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
              >
                Save
              </button>
              <button onClick={() => setIsEditingAll(false)} className="px-4 py-2 rounded-xl text-xs border" style={{ borderColor: 'var(--border)' }}>
                Cancel
              </button>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="p-4 rounded-2xl border" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <div className="text-xs mb-1" style={{ color: 'var(--muted)' }}>
              {catInfo.isLiability ? 'Still owed' : 'Worth today'}
            </div>
            <PrivacyValue minor={m.value} className="text-xl font-bold" showCompact includePaise={false} />
          </div>
          <div className="p-4 rounded-2xl border" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <div className="text-xs mb-1" style={{ color: 'var(--muted)' }}>Put in (Invested)</div>
            <PrivacyValue minor={m.cost} className="text-xl font-bold" includePaise={false} />
          </div>
          <div className="p-4 rounded-2xl border" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <div className="text-xs mb-1" style={{ color: 'var(--muted)' }}>Gain or loss</div>
            <PrivacyDelta minor={m.gain} className="text-xl font-bold" />
          </div>
        </div>

        <div className="text-sm" style={{ color: 'var(--muted)' }}>
          {unitWord(holding) && m.qty > 0n
            ? `${MoneyEngine.toQty(m.qty).toLocaleString('en-IN')} ${unitWord(holding)} · `
            : ''}
          {MoneyEngine.formatRupees(m.price, true)} each
          {isEditingPrice ? (
            <span className="ml-2 inline-flex gap-1">
              <input
                value={newPriceInput}
                onChange={e => setNewPriceInput(e.target.value)}
                className="w-24 px-2 py-0.5 rounded border text-xs"
                style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
              />
              <button onClick={handleSavePrice} className="text-xs font-bold" style={{ color: 'var(--accent)' }}>
                Save
              </button>
            </span>
          ) : (
            <button onClick={() => setIsEditingPrice(true)} className="ml-2 text-xs" style={{ color: 'var(--accent)' }}>
              change price
            </button>
          )}
        </div>
        {holding.lastPriceUpdated && (
          <div className="text-[11px] -mt-1" style={{ color: 'var(--muted)' }}>
            {holding.priceSource || 'Price'} as on {holding.lastPriceUpdated.slice(0, 10)}
            {/NSE|BSE|AMFI/i.test(holding.priceSource || '') ? ' — last close, not live' : ''}
          </div>
        )}

        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {field('AMC', holding.amc)}
          {field('RTA', holding.rta)}
          {field('AMFI code', holding.amfiCode)}
          {field('ISIN', holding.isin)}
          {field('Folio', holding.symbolOrFolio)}
          {field('Demat / BOID', holding.accountNumberMasked)}
          {field('Depository', holding.dpName)}
          {field('Exchange', holding.exchange)}
          {field('Advisor', holding.advisor)}
          {acct?.nominee && field('Family note (not the registrar nominee)', acct.nominee)}
        </div>

        {holding.category === 'LIABILITY' && (
          <div className="p-4 rounded-2xl border space-y-3" style={{ borderColor: 'var(--border)' }}>
            <div className="text-sm font-bold">Loan EMI</div>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-sm">
              {holding.monthlyEmiMinor && (
                <div>
                  <div className="text-xs" style={{ color: 'var(--muted)' }}>Monthly EMI</div>
                  <PrivacyValue minor={BigInt(holding.monthlyEmiMinor)} includePaise={false} className="font-semibold" />
                </div>
              )}
              {holding.interestRatePercent != null && (
                <div>
                  <div className="text-xs" style={{ color: 'var(--muted)' }}>Interest</div>
                  <div className="font-semibold">{holding.interestRatePercent}% a year</div>
                </div>
              )}
              {holding.remainingEmiCount != null && (
                <div>
                  <div className="text-xs" style={{ color: 'var(--muted)' }}>Remaining</div>
                  <div className="font-semibold">{holding.remainingEmiCount} months</div>
                </div>
              )}
              {holding.nextCouponDate && (
                <div>
                  <div className="text-xs" style={{ color: 'var(--muted)' }}>Next EMI</div>
                  <div className="font-semibold">{formatCalendarDate(holding.nextCouponDate)}</div>
                </div>
              )}
              <div>
                <div className="text-xs" style={{ color: 'var(--muted)' }}>Status</div>
                <div className="font-semibold">
                  {holding.emiStatus === 'PAUSED' ? 'Paused' : holding.emiStatus === 'STOPPED' ? 'Stopped' : 'Paying'}
                </div>
              </div>
            </div>
            <p className="text-[11px]" style={{ color: 'var(--muted)' }}>
              While Paying, each due EMI is written into the record book when you open the vault, and outstanding falls by the EMI amount.
            </p>
            <div className="flex flex-wrap gap-2">
              {holding.emiStatus !== 'PAYING' && (
                <button
                  type="button"
                  onClick={() => {
                    const asOf = new Date().toISOString().slice(0, 10);
                    updateHolding(holding.id, {
                      emiStatus: 'PAYING' as EmiStatus,
                      nextCouponDate: resumeEmiDate(holding.nextCouponDate, asOf),
                    });
                  }}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold border"
                  style={{ borderColor: 'var(--border)' }}
                >
                  Resume EMI
                </button>
              )}
              {holding.emiStatus !== 'PAUSED' && holding.emiStatus !== 'STOPPED' && (
                <button
                  type="button"
                  onClick={() => updateHolding(holding.id, { emiStatus: 'PAUSED' as EmiStatus })}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold border"
                  style={{ borderColor: 'var(--border)' }}
                >
                  Pause EMI
                </button>
              )}
              {holding.emiStatus !== 'STOPPED' && (
                <button
                  type="button"
                  onClick={() => updateHolding(holding.id, { emiStatus: 'STOPPED' as EmiStatus })}
                  className="px-3 py-1.5 rounded-lg text-xs font-semibold border"
                  style={{ borderColor: 'var(--border)', color: 'var(--bad)' }}
                >
                  Stop EMI
                </button>
              )}
            </div>
          </div>
        )}

        {(holding.nextCouponDate || holding.maturityDate || holding.couponRatePercent || isElss || isGovt) && (
          <div className="p-4 rounded-2xl border space-y-2" style={{ borderColor: 'var(--border)' }}>
            <div className="text-sm font-bold flex items-center gap-2">
              <Calendar className="w-4 h-4" /> Dates that matter
            </div>
            {holding.couponRatePercent != null && (
              <div className="text-sm flex items-center gap-2">
                <Coins className="w-3.5 h-3.5" />
                {holding.couponRatePercent}% a year
                {holding.category === 'GOLD_SGB' ? ' (gold bond interest, twice a year)' : ''}
              </div>
            )}
            {holding.nextCouponDate && (
              <div className="text-sm">Next interest · {formatCalendarDate(holding.nextCouponDate)}</div>
            )}
            {holding.maturityDate && (
              <div className="text-sm">
                {holding.category === 'GOLD_SGB'
                  ? 'Matures'
                  : isGovt
                    ? 'Matures / withdrawable'
                    : isElss
                      ? 'Lock-in ends'
                      : 'Matures'}{' '}
                · {formatCalendarDate(holding.maturityDate)}
              </div>
            )}
            {isElss && !holding.maturityDate && (
              <div className="text-xs" style={{ color: 'var(--muted)' }}>
                ELSS funds lock in for 3 years from each purchase. Add the end date above if you know it.
              </div>
            )}
            {isGovt && !holding.maturityDate && (
              <div className="text-xs" style={{ color: 'var(--muted)' }}>
                PPF, EPF and NPS have their own lock-in. Add a maturity date if you want it on the Home calendar.
              </div>
            )}
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => onOpenAddRecord(holding.id)}
            className="px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5"
            style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
          >
            <Plus className="w-3.5 h-3.5" /> Record a buy or sell
          </button>
          <button
            onClick={() => {
              if (window.confirm('Remove this holding? Buy/sell history is kept.')) {
                deleteHolding(holding.id);
                onClose();
              }
            }}
            className="px-4 py-2 rounded-xl text-xs font-semibold flex items-center gap-1.5 border"
            style={{ borderColor: 'var(--border)', color: 'var(--bad)' }}
          >
            <Trash2 className="w-3.5 h-3.5" /> Remove holding
          </button>
        </div>

        <div>
          <h3 className="text-sm font-bold mb-2">Record book</h3>
          {holdingEvents.length === 0 ? (
            <p className="text-xs" style={{ color: 'var(--muted)' }}>
              No rows yet. Confirm a statement or add a buy.
            </p>
          ) : (
            <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
              {holdingEvents.slice(0, 20).map(ev => (
                <div key={ev.id} className="py-2 flex items-center justify-between text-sm gap-3">
                  <div>
                    <div className="font-medium">{eventLabel(ev.eventType)}</div>
                    <div className="text-[11px]" style={{ color: 'var(--muted)' }}>
                      {formatCalendarDate(ev.eventDate)}
                      {ev.notes ? ` · ${ev.notes}` : ''}
                    </div>
                  </div>
                  <PrivacyValue
                    minor={BigInt(ev.totalAmountMinor || '0')}
                    includePaise={false}
                    className="text-sm font-semibold"
                  />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
