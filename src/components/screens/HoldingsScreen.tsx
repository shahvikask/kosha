import React, { useEffect, useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue, PrivacyDelta } from '../common/PrivacyValue';
import { FamilyBucketId, bucketOf, metricsOf, plainCategory, unitWord, whereHeld, displayHoldingName, isOpenPosition, orderBucketsByPresence } from '../../lib/holdingView';
import { MoneyEngine } from '../../lib/money';
import { Search, Plus, ChevronRight, SlidersHorizontal, UserRound, X } from 'lucide-react';
import { holdingReturn } from '../../lib/returnMetrics';
import { absoluteReturnPct, formatPct, BUCKET_COLORS } from '../../lib/growth';
import { isUnknownInvested, UNKNOWN_INVESTED_LABEL } from '../../lib/costBasis';

interface Props {
  onOpenAddHolding: () => void;
  onOpenHoldingDetail: (id: string) => void;
}

export const HoldingsScreen: React.FC<Props> = ({ onOpenAddHolding, onOpenHoldingDetail }) => {
  const { filteredHoldings, familyMembers, derived, sgbValuationMode, ibjaRupeesPerGram, ledgerEvents } = useKosha();
  const [q, setQ] = useState('');
  const [bucket, setBucket] = useState<FamilyBucketId | 'ALL'>('ALL');
  const [ownerId, setOwnerId] = useState('ALL');
  const [showClosed, setShowClosed] = useState(false);

  const ibja = ibjaRupeesPerGram && ibjaRupeesPerGram > 0 ? MoneyEngine.fromRupees(ibjaRupeesPerGram) : null;
  const closedCount = useMemo(() => filteredHoldings.filter(h => !isOpenPosition(h, derived)).length, [filteredHoldings, derived]);
  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return filteredHoldings
      .map(h => {
        const m = metricsOf(h, derived, sgbValuationMode, ibja);
        const unknown = isUnknownInvested(h, ledgerEvents, m.cost, m.value);
        const ret = holdingReturn(h, ledgerEvents, derived, sgbValuationMode, new Date().toISOString().slice(0, 10), ibja);
        return { h, m, b: bucketOf(h), abs: ret.primaryPct, unknown, ann: ret.secondaryText, annOk: ret.annualReason === 'ok' };
      })
      .filter(r => showClosed || isOpenPosition(r.h, derived))
      .filter(r => bucket === 'ALL' || r.b === bucket)
      .filter(r => ownerId === 'ALL' || r.h.ownerId === ownerId)
      .filter(r => {
        if (!needle) return true;
        const owner = familyMembers.find(m => m.id === r.h.ownerId)?.name || '';
        return [r.h.name, r.h.isin, r.h.amc, r.h.symbolOrFolio, owner, r.h.institutionName, r.h.dpName].filter(Boolean).join(' ').toLowerCase().includes(needle);
      })
      .sort((a, b) => (a.m.value < b.m.value ? 1 : -1));
  }, [filteredHoldings, derived, sgbValuationMode, ibja, q, bucket, familyMembers, ownerId, showClosed, ledgerEvents]);

  const totals = useMemo(() => rows.reduce((acc, row) => ({
    invested: acc.invested + (row.unknown ? 0n : row.m.cost),
    current: acc.current + row.m.value,
    gain: acc.gain + (row.unknown ? 0n : row.m.gain),
    unknown: acc.unknown + (row.unknown ? 1 : 0),
    knownValue: acc.knownValue + (row.unknown ? 0n : row.m.value),
  }), { invested: 0n, current: 0n, gain: 0n, unknown: 0, knownValue: 0n }), [rows]);
  const totalReturn = totals.unknown === rows.length && rows.length > 0
    ? null
    : absoluteReturnPct(totals.invested, totals.knownValue);
  const hasFilters = !!q.trim() || bucket !== 'ALL' || ownerId !== 'ALL' || showClosed;
  const classChips = useMemo(() => {
    const source = ownerId === 'ALL' ? filteredHoldings : filteredHoldings.filter(h => h.ownerId === ownerId);
    return orderBucketsByPresence(source.map(h => bucketOf(h)));
  }, [filteredHoldings, ownerId]);

  useEffect(() => {
    if (bucket !== 'ALL' && classChips.find(b => b.id === bucket)?.empty) setBucket('ALL');
  }, [bucket, classChips]);

  const clearFilters = () => {
    setQ('');
    setBucket('ALL');
    setOwnerId('ALL');
    setShowClosed(false);
  };

  return (
    <div className="space-y-3 md:space-y-5 pb-2 md:pb-8">
      <div className="flex items-end justify-between gap-3">
        <div className="min-w-0">
          <div className="page-kicker text-[10px] uppercase tracking-[0.16em] font-bold mb-1" style={{ color: 'var(--muted)' }}>Portfolio</div>
          <h1 className="page-title text-2xl sm:text-3xl font-bold tracking-tight">Holdings</h1>
          <p className="page-lead text-sm mt-1" style={{ color: 'var(--muted)' }}>See what you own, where it sits, and how much it has gained.</p>
        </div>
        <button onClick={onOpenAddHolding} className="hidden md:flex px-4 py-2.5 rounded-xl text-sm font-bold items-center justify-center gap-1.5 shrink-0" style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}><Plus className="w-4 h-4" /> Add manually</button>
      </div>

      {filteredHoldings.length > 0 && (
        <div className="grid grid-cols-3 gap-2 md:gap-3">
          <Metric label="Invested" value={totals.invested} empty={totals.unknown > 0 && totals.invested === 0n} hint={totals.unknown > 0 ? `${totals.unknown} need cost` : undefined} />
          <Metric label="Current" value={totals.current} emphasis />
          <div className="rounded-xl md:rounded-2xl border p-2.5 md:p-4 min-w-0" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <div className="text-[10px] md:text-xs" style={{ color: 'var(--muted)' }}>Gain</div>
            {totals.unknown > 0 && totals.invested === 0n ? (
              <div className="mt-0.5 text-sm md:text-lg font-bold leading-tight" style={{ color: 'var(--bad)' }}>{UNKNOWN_INVESTED_LABEL}</div>
            ) : (
              <div className="mt-0.5">
                <PrivacyDelta minor={totals.gain} className="text-sm md:text-lg font-bold tabular-nums" />
                <div className="text-[11px] font-bold" style={{ color: (totalReturn ?? 0) >= 0 ? 'var(--good)' : 'var(--bad)' }}>{formatPct(totalReturn)}</div>
              </div>
            )}
            {totals.unknown > 0 && totals.invested > 0n && (
              <div className="text-[10px] mt-0.5 hidden md:block" style={{ color: 'var(--muted)' }}>{totals.unknown} excluded — no buy price</div>
            )}
          </div>
        </div>
      )}

      <section className="card p-2.5 md:p-4 space-y-2 md:space-y-3">
        <div className="flex flex-col sm:flex-row gap-2">
          <div className="relative flex-1"><Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} /><input value={q} onChange={e => setQ(e.target.value)} aria-label="Search holdings" placeholder="Search name, folio, ISIN…" className="w-full pl-10 pr-3 py-2.5 rounded-xl border text-sm" style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }} /></div>
          <div className="relative"><UserRound className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} /><select value={ownerId} onChange={e => setOwnerId(e.target.value)} aria-label="Filter by family member" className="w-full sm:w-48 pl-9 pr-8 py-2.5 rounded-xl border text-sm appearance-none" style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}><option value="ALL">Everyone</option>{familyMembers.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}</select></div>
        </div>
        <div className="flex items-center gap-2 overflow-x-auto pb-0.5 -mx-0.5 px-0.5">
          <SlidersHorizontal className="w-4 h-4 flex-shrink-0" style={{ color: 'var(--muted)' }} />
          <Chip active={bucket === 'ALL'} onClick={() => setBucket('ALL')}>All</Chip>
          {classChips.map(b => (
            <Chip
              key={b.id}
              active={bucket === b.id}
              disabled={b.empty}
              tone={BUCKET_COLORS[b.id]}
              onClick={() => setBucket(b.id)}
            >
              {b.title}
            </Chip>
          ))}
          {closedCount > 0 && <Chip active={showClosed} onClick={() => setShowClosed(v => !v)}>{showClosed ? 'Closed shown' : `Closed (${closedCount})`}</Chip>}
          {hasFilters && <button onClick={clearFilters} className="ml-auto flex-shrink-0 text-xs font-semibold flex items-center gap-1 px-2 py-1.5" style={{ color: 'var(--accent)' }}><X className="w-3 h-3" /> Clear</button>}
        </div>
      </section>

      <div className="flex items-center justify-between px-0.5">
        <div className="text-xs font-semibold" style={{ color: 'var(--muted)' }}>{rows.length} {rows.length === 1 ? 'holding' : 'holdings'}</div>
        {closedCount > 0 && !showClosed && <div className="text-[11px]" style={{ color: 'var(--muted)' }}>{closedCount} closed hidden</div>}
      </div>

      {rows.length === 0 ? (
        <div className="card p-6 md:p-8 text-center space-y-2"><div className="text-sm font-semibold">{filteredHoldings.length === 0 ? 'Nothing here yet' : 'No holdings match these filters'}</div><p className="text-xs" style={{ color: 'var(--muted)' }}>{filteredHoldings.length === 0 ? 'Read a statement or add PPF, property, gold or a loan manually.' : 'Try clearing a filter or searching for another investment.'}</p>{hasFilters && filteredHoldings.length > 0 && <button onClick={clearFilters} className="text-xs font-bold" style={{ color: 'var(--accent)' }}>Clear filters</button>}</div>
      ) : (
        <div className="card overflow-hidden" style={{ borderColor: 'var(--border)' }}>
          <div className="hidden md:grid grid-cols-[minmax(0,1.7fr)_0.9fr_0.9fr_0.8fr_24px] gap-4 px-4 py-3 border-b text-[10px] uppercase tracking-[0.12em] font-bold" style={{ color: 'var(--muted)', borderColor: 'var(--border)' }}><span>Investment</span><span className="text-right">Invested</span><span className="text-right">Current</span><span className="text-right">Abs. / Ann.</span><span /></div>
          <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
            {rows.map(({ h, m, abs, unknown, ann, annOk }) => {
              const owner = familyMembers.find(fm => fm.id === h.ownerId);
              const good = (abs ?? 0) >= 0;
              const meta = [plainCategory(h.category), owner?.name, whereHeld(h), unitWord(h) && m.qty > 0n ? `${MoneyEngine.toQty(m.qty).toLocaleString('en-IN')} ${unitWord(h)}` : '']
                .filter(Boolean)
                .join(' · ');
              return (
                <button key={h.id} onClick={() => onOpenHoldingDetail(h.id)} className="w-full text-left px-3 py-2.5 md:px-4 md:py-3.5 hover:bg-black/5 dark:hover:bg-white/5 transition-colors">
                  <div className="flex md:hidden items-center gap-2.5">
                    <span className="w-2 h-2 rounded-full shrink-0" style={{ background: BUCKET_COLORS[bucketOf(h)] }} />
                    <div className="min-w-0 flex-1">
                      <div className="font-semibold text-sm truncate">{displayHoldingName(h.name)}</div>
                      <div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>{meta}</div>
                    </div>
                    <div className="text-right shrink-0">
                      <PrivacyValue minor={m.value} includePaise={false} className="text-sm font-bold tabular-nums" />
                      {unknown ? (
                        <div className="text-[11px] font-semibold" style={{ color: 'var(--bad)' }}>{UNKNOWN_INVESTED_LABEL}</div>
                      ) : (
                        <div className="text-[11px] font-medium" style={{ color: good ? 'var(--good)' : 'var(--bad)' }}>
                          <PrivacyDelta minor={m.gain} className="text-[11px] font-medium" /> {formatPct(abs)}
                        </div>
                      )}
                    </div>
                  </div>
                  <div className="hidden md:grid grid-cols-[minmax(0,1.7fr)_0.9fr_0.9fr_0.8fr_24px] gap-4 items-center">
                    <div className="min-w-0 flex items-start gap-2">
                      <span className="mt-1.5 w-2 h-2 rounded-full shrink-0" style={{ background: BUCKET_COLORS[bucketOf(h)] }} />
                      <div className="min-w-0">
                        <div className="font-semibold text-sm truncate">{displayHoldingName(h.name)}</div>
                        <div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>{meta}</div>
                      </div>
                    </div>
                    <div className="text-right">{unknown ? <span className="text-sm" style={{ color: 'var(--muted)' }}>—</span> : <PrivacyValue minor={m.cost} includePaise={false} className="text-sm" />}</div>
                    <div className="text-right"><PrivacyValue minor={m.value} includePaise={false} className="text-sm font-bold" /></div>
                    <div className="text-right">
                      {unknown ? (
                        <div className="text-sm font-semibold" style={{ color: 'var(--bad)' }}>{UNKNOWN_INVESTED_LABEL}</div>
                      ) : (
                        <>
                          <PrivacyDelta minor={m.gain} className="text-sm font-semibold" />
                          <div className="text-[11px] font-medium" style={{ color: good ? 'var(--good)' : 'var(--bad)' }}>{formatPct(abs)}</div>
                          <div className="text-[11px]" style={{ color: annOk ? 'var(--text)' : 'var(--muted)' }}>Ann. {ann}</div>
                        </>
                      )}
                    </div>
                    <ChevronRight className="w-4 h-4" style={{ color: 'var(--muted)' }} />
                  </div>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

const Metric: React.FC<{ label: string; value: bigint; emphasis?: boolean; empty?: boolean; hint?: string }> = ({ label, value, emphasis, empty, hint }) => (
  <div className="rounded-xl md:rounded-2xl border p-2.5 md:p-4 min-w-0" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
    <div className="text-[10px] md:text-xs" style={{ color: 'var(--muted)' }}>{label}</div>
    {empty
      ? <div className={`${emphasis ? 'font-bold' : 'font-semibold'} text-sm md:text-lg mt-0.5`} style={{ color: 'var(--muted)' }}>—</div>
      : <PrivacyValue minor={value} includePaise={false} className={`${emphasis ? 'font-bold' : 'font-semibold'} text-sm md:text-lg mt-0.5 tabular-nums`} />}
    {hint && <div className="text-[10px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>{hint}</div>}
  </div>
);
const Chip: React.FC<{ active: boolean; disabled?: boolean; onClick: () => void; children: React.ReactNode; tone?: string }> = ({ active, disabled, onClick, children, tone }) => (
  <button
    type="button"
    onClick={disabled ? undefined : onClick}
    disabled={disabled}
    aria-disabled={disabled || undefined}
    className="px-3 py-1.5 rounded-full text-xs font-semibold border whitespace-nowrap"
    style={
      disabled
        ? { borderColor: 'var(--border)', color: 'var(--muted)', background: 'transparent', opacity: 0.45, cursor: 'not-allowed' }
        : active
          ? { background: tone || 'var(--accent)', color: tone ? '#0b1220' : 'var(--accent-fg)', borderColor: 'transparent' }
          : { borderColor: tone ? `color-mix(in srgb, ${tone} 45%, var(--border))` : 'var(--border)', color: tone || 'var(--text)', background: tone ? `color-mix(in srgb, ${tone} 14%, var(--card))` : 'var(--card)' }
    }
  >
    {children}
  </button>
);
