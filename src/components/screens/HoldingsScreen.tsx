import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue, PrivacyDelta } from '../common/PrivacyValue';
import { FAMILY_BUCKETS, FamilyBucketId, bucketOf, metricsOf, plainCategory, unitWord, whereHeld, displayHoldingName, isOpenPosition } from '../../lib/holdingView';
import { MoneyEngine } from '../../lib/money';
import { Search, Plus, ChevronRight } from 'lucide-react';
import { absoluteReturnPct, formatPct } from '../../lib/growth';

interface Props {
  onOpenAddHolding: () => void;
  onOpenHoldingDetail: (id: string) => void;
}

export const HoldingsScreen: React.FC<Props> = ({ onOpenAddHolding, onOpenHoldingDetail }) => {
  const { filteredHoldings, familyMembers, derived, sgbValuationMode } = useKosha();
  const [q, setQ] = useState('');
  const [bucket, setBucket] = useState<FamilyBucketId | 'ALL'>('ALL');
  const [showClosed, setShowClosed] = useState(false);

  const closedCount = useMemo(() => filteredHoldings.filter(h => !isOpenPosition(h, derived)).length, [filteredHoldings, derived]);
  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return filteredHoldings
      .map(h => {
        const m = metricsOf(h, derived, sgbValuationMode);
        return { h, m, b: bucketOf(h), abs: absoluteReturnPct(m.cost, m.value) };
      })
      .filter(r => showClosed || isOpenPosition(r.h, derived))
      .filter(r => bucket === 'ALL' || r.b === bucket)
      .filter(r => {
        if (!needle) return true;
        const owner = familyMembers.find(m => m.id === r.h.ownerId)?.name || '';
        return [r.h.name, r.h.isin, r.h.amc, r.h.symbolOrFolio, owner, r.h.institutionName].filter(Boolean).join(' ').toLowerCase().includes(needle);
      })
      .sort((a, b) => (a.m.value < b.m.value ? 1 : -1));
  }, [filteredHoldings, derived, sgbValuationMode, q, bucket, familyMembers, showClosed]);

  return (
    <div className="space-y-5 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
        <div><h1 className="text-2xl font-bold">Holdings</h1><p className="text-sm" style={{ color: 'var(--muted)' }}>Your portfolio, with invested value, current value and absolute return.</p></div>
        <button onClick={onOpenAddHolding} className="px-4 py-2 rounded-xl text-sm font-bold flex items-center gap-1.5" style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}><Plus className="w-4 h-4" /> Add manually</button>
      </div>

      <div className="relative"><Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} /><input value={q} onChange={e => setQ(e.target.value)} placeholder="Search holding, folio, ISIN or owner…" className="w-full pl-10 pr-3 py-2.5 rounded-xl border text-sm" style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }} /></div>

      <div className="flex flex-wrap gap-2">
        <Chip active={bucket === 'ALL'} onClick={() => setBucket('ALL')}>All</Chip>
        {FAMILY_BUCKETS.map(b => <Chip key={b.id} active={bucket === b.id} onClick={() => setBucket(b.id)}>{b.title}</Chip>)}
        {closedCount > 0 && <Chip active={showClosed} onClick={() => setShowClosed(v => !v)}>{showClosed ? 'Hide closed' : `Show closed (${closedCount})`}</Chip>}
      </div>

      {rows.length === 0 ? (
        <div className="card p-8 text-center text-sm" style={{ color: 'var(--muted)' }}>{filteredHoldings.length === 0 ? 'Nothing here yet. Read a statement or add PPF / property manually.' : closedCount > 0 && !showClosed && !q.trim() && bucket === 'ALL' ? `Closed holdings are hidden. Show closed (${closedCount}) to review them.` : 'No holding matches that search.'}</div>
      ) : (
        <div className="card overflow-hidden" style={{ borderColor: 'var(--border)' }}>
          <div className="hidden md:grid grid-cols-[minmax(0,1.7fr)_0.9fr_0.9fr_0.8fr_24px] gap-4 px-4 py-3 border-b text-[11px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)', borderColor: 'var(--border)' }}>
            <span>Holding</span><span className="text-right">Invested</span><span className="text-right">Current</span><span className="text-right">Absolute return</span><span />
          </div>
          <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
            {rows.map(({ h, m, abs }) => {
              const owner = familyMembers.find(fm => fm.id === h.ownerId);
              const good = (abs ?? 0) >= 0;
              return <button key={h.id} onClick={() => onOpenHoldingDetail(h.id)} className="w-full text-left px-4 py-3.5 hover:opacity-90">
                <div className="grid md:grid-cols-[minmax(0,1.7fr)_0.9fr_0.9fr_0.8fr_24px] gap-3 md:gap-4 items-center">
                  <div className="min-w-0"><div className="font-semibold text-sm truncate">{displayHoldingName(h.name)}</div><div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>{plainCategory(h.category)}{owner ? ` · ${owner.name}` : ''} · {whereHeld(h)}{unitWord(h) && m.qty > 0n ? ` · ${MoneyEngine.toQty(m.qty).toLocaleString('en-IN')} ${unitWord(h)}` : ''}</div></div>
                  <div className="md:text-right"><div className="md:hidden text-[10px] uppercase tracking-wide font-bold mb-0.5" style={{ color: 'var(--muted)' }}>Invested</div><PrivacyValue minor={m.cost} includePaise={false} className="text-sm" /></div>
                  <div className="md:text-right"><div className="md:hidden text-[10px] uppercase tracking-wide font-bold mb-0.5" style={{ color: 'var(--muted)' }}>Current</div><PrivacyValue minor={m.value} includePaise={false} className="text-sm font-bold" /></div>
                  <div className="md:text-right"><div className="md:hidden text-[10px] uppercase tracking-wide font-bold mb-0.5" style={{ color: 'var(--muted)' }}>Return</div><PrivacyDelta minor={m.gain} className="text-sm font-semibold" /><div className="text-[11px] font-medium" style={{ color: good ? 'var(--good)' : 'var(--bad)' }}>{formatPct(abs)}</div></div>
                  <ChevronRight className="hidden md:block w-4 h-4" style={{ color: 'var(--muted)' }} />
                </div>
              </button>;
            })}
          </div>
        </div>
      )}
    </div>
  );
};

const Chip: React.FC<{ active: boolean; onClick: () => void; children: React.ReactNode }> = ({ active, onClick, children }) => <button onClick={onClick} className="px-3 py-1.5 rounded-full text-xs font-semibold border" style={active ? { background: 'var(--accent)', color: 'var(--accent-fg)', borderColor: 'transparent' } : { borderColor: 'var(--border)', color: 'var(--text)' }}>{children}</button>;
