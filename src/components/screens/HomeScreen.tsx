import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue, PrivacyDelta } from '../common/PrivacyValue';
import { FAMILY_BUCKETS, upcomingFromHoldings, formatCalendarDate, FamilyBucketId } from '../../lib/holdingView';
import { formatStatementDate } from '../../lib/casPython';
import { FileSpreadsheet, Target, Calendar, ChevronRight, ArrowUpRight } from 'lucide-react';
import { Donut, HBars, AreaFlow, StackedFy, SplitBar } from '../growth/Charts';
import { buildGrowthModel, formatPct, GrowthHoldingRow } from '../../lib/growth';
import { ExposureNode, EXPOSURES } from '../../lib/taxonomy';

interface HomeScreenProps {
  onOpenAddHolding: () => void;
  onOpenAddRecord: () => void;
  onOpenHoldingDetail: (holdingId: string) => void;
}

type Drill =
  | { kind: 'none' }
  | { kind: 'bucket'; id: string }
  | { kind: 'exposure'; id: string }
  | { kind: 'member'; id: string }
  | { kind: 'fy'; id: string };

export const HomeScreen: React.FC<HomeScreenProps> = ({ onOpenAddHolding, onOpenHoldingDetail }) => {
  const { summary, filteredHoldings, familyMembers, selectedMemberId, setActiveScreen, sgbValuationMode, derived, lastStatementAsOf, pricesAsOf, goals, ledgerEvents } = useKosha();
  const [drill, setDrill] = useState<Drill>({ kind: 'none' });
  const [showAllMovers, setShowAllMovers] = useState(false);
  const who = selectedMemberId === 'ALL' ? 'The family' : familyMembers.find(m => m.id === selectedMemberId)?.name || 'Family';
  const asOf = new Date().toISOString().slice(0, 10);
  const model = useMemo(() => buildGrowthModel(filteredHoldings, ledgerEvents.filter(e => selectedMemberId === 'ALL' || e.ownerId === selectedMemberId), derived, familyMembers, sgbValuationMode, asOf), [filteredHoldings, ledgerEvents, selectedMemberId, derived, familyMembers, sgbValuationMode, asOf]);
  const upcoming = useMemo(() => upcomingFromHoldings(filteredHoldings, goals), [filteredHoldings, goals]);
  const goalRows = useMemo(() => goals.map(g => {
    const tagged = filteredHoldings.filter(h => [h.tag, ...(h.tags || [])].some(t => (t || '').toLowerCase() === g.name.toLowerCase()));
    const current = tagged.reduce((s, h) => s + (model.holdings.find(x => x.id === h.id)?.valueMinor || 0n), 0n);
    const target = BigInt(g.targetMinor || '0');
    return { g, current, target, pct: target > 0n ? Math.min(100, Number((current * 100n) / target)) : 0 };
  }), [goals, filteredHoldings, model.holdings]);
  const filteredRows = useMemo(() => {
    const hs = model.holdings.filter(h => h.bucket !== 'LOAN');
    if (drill.kind === 'bucket') return hs.filter(h => h.bucket === drill.id);
    if (drill.kind === 'exposure') return hs.filter(h => h.exposure === drill.id);
    if (drill.kind === 'member') return hs.filter(h => h.ownerId === drill.id);
    return hs;
  }, [model.holdings, drill]);
  const empty = filteredHoldings.length === 0;
  const k = model.kpis;
  const gainGood = k.absoluteGainMinor >= 0n;
  const movers = showAllMovers ? filteredRows : filteredRows.slice(0, 3);
  const drillTitle = drill.kind === 'bucket'
    ? FAMILY_BUCKETS.find(b => b.id === (drill.id as FamilyBucketId))?.title
    : drill.kind === 'exposure'
      ? EXPOSURES.find(e => e.id === (drill.id as ExposureNode))?.label
      : drill.kind === 'member'
        ? familyMembers.find(m => m.id === drill.id)?.name
        : drill.kind === 'fy' ? `Income · FY ${drill.id}` : null;

  return (
    <div className="space-y-5 sm:space-y-6 pb-12">
      <section className="card p-5 sm:p-8 relative overflow-hidden">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-5">
          <div>
            <div className="text-xs uppercase tracking-wider font-bold mb-2" style={{ color: 'var(--muted)' }}>{who} · Net worth</div>
            <PrivacyValue minor={summary.netWorthMinor} showCompact className="text-4xl sm:text-5xl font-extrabold tracking-tight" />
            <div className="mt-2 text-xs" style={{ color: 'var(--muted)' }}>
              {lastStatementAsOf ? `Updated through ${formatStatementDate(lastStatementAsOf)}` : 'Import a statement to build your portfolio'}
              {pricesAsOf ? ` · Prices ${formatStatementDate(pricesAsOf)}` : ''}
            </div>
          </div>
          <div className="rounded-2xl border p-4 min-w-[220px]" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <div className="text-[11px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>Absolute return</div>
            <div className="mt-1 flex items-baseline gap-2">
              <PrivacyDelta minor={k.absoluteGainMinor} className="text-xl font-extrabold" />
              <span className="text-sm font-bold" style={{ color: gainGood ? 'var(--good)' : 'var(--bad)' }}>{formatPct(k.absoluteReturnPct)}</span>
            </div>
            <div className="text-[11px] mt-1" style={{ color: 'var(--muted)' }}>Current value compared with invested amount</div>
          </div>
        </div>
        <div className="mt-5 grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Mini label="Invested" value={summary.totalInvestedMinor} />
          <Mini label="Assets" value={k.assetsMinor} />
          <Mini label="Liabilities" value={summary.totalLiabilitiesMinor} />
        </div>
      </section>

      {empty && (
        <div className="card p-8 text-center space-y-4">
          <h2 className="text-xl font-bold">Start with your statement</h2>
          <p className="text-sm max-w-md mx-auto" style={{ color: 'var(--muted)' }}>Drop your CAS PDF. We read it on this computer and show every holding for you to confirm. Nothing is added until you say so.</p>
          <div className="flex flex-wrap justify-center gap-3">
            <button onClick={() => setActiveScreen('DOCUMENTS')} className="px-5 py-2.5 rounded-xl font-bold text-sm flex items-center gap-2" style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}><FileSpreadsheet className="w-4 h-4" />Read a statement</button>
            <button onClick={onOpenAddHolding} className="px-5 py-2.5 rounded-xl font-semibold text-sm border" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>Add PPF, property or a loan</button>
          </div>
        </div>
      )}

      {!empty && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Action title="Read a statement" hint="CDSL, CAMS, KFin" onClick={() => setActiveScreen('DOCUMENTS')} />
            <Action title="Add manually" hint="PPF, home, gold" onClick={onOpenAddHolding} />
            <Action title="See holdings" hint="Search & compare" onClick={() => setActiveScreen('HOLDINGS')} />
            <Action title="Demat & folios" hint="Where it sits" onClick={() => setActiveScreen('ACCOUNTS')} />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-3">Where the money sits</h2>{model.buckets.length === 0 ? <p className="text-xs" style={{ color: 'var(--muted)' }}>No assets to chart.</p> : <Donut slices={model.buckets} selectedId={drill.kind === 'bucket' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'bucket' && d.id === id ? { kind: 'none' } : { kind: 'bucket', id })} center={<><span className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--muted)' }}>Assets</span><PrivacyValue minor={k.assetsMinor} includePaise={false} showCompact className="text-sm font-bold" compactClassName="hidden" /></>} />}</section>
            <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-1">Look-through mix</h2><p className="text-[11px] mb-3" style={{ color: 'var(--muted)' }}>Equity, debt and gold after opening hybrid funds.</p><HBars slices={model.exposures} selectedId={drill.kind === 'exposure' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'exposure' && d.id === id ? { kind: 'none' } : { kind: 'exposure', id })} /></section>
          </div>

          {selectedMemberId === 'ALL' && model.members.length > 1 && <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-3">Whose name it is in</h2><HBars slices={model.members} selectedId={drill.kind === 'member' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'member' && d.id === id ? { kind: 'none' } : { kind: 'member', id })} /></section>}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-1">Money in, over time</h2><p className="text-[11px] mb-2" style={{ color: 'var(--muted)' }}>Invested capital and dividends / interest.</p><AreaFlow months={model.months} /></section>
            <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-1">Income by Indian FY</h2><StackedFy years={model.incomeYears} selectedFy={drill.kind === 'fy' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'fy' && d.id === id ? { kind: 'none' } : { kind: 'fy', id })} /></section>
          </div>

          <section className="card p-4 sm:p-5 space-y-3"><h2 className="text-sm font-bold">Gain booked vs still on paper</h2><SplitBar leftLabel="Unrealised" rightLabel="Realised" leftMinor={k.unrealisedMinor > 0n ? k.unrealisedMinor : 0n} rightMinor={k.realisedMinor > 0n ? k.realisedMinor : 0n} leftColor="#3b82f6" rightColor="#10b981" /></section>

          <section className="card p-4 sm:p-5"><div className="flex items-center justify-between mb-3"><div><h2 className="text-sm font-bold">{drillTitle || 'What moved the needle'}</h2><p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>Holdings ranked by absolute gain.</p></div><button onClick={() => setActiveScreen('HOLDINGS')} className="text-xs font-bold flex items-center gap-1" style={{ color: 'var(--accent)' }}>All holdings <ArrowUpRight className="w-3.5 h-3.5" /></button></div><HoldingTable rows={movers} onOpen={onOpenHoldingDetail} />{filteredRows.length > 3 && <button type="button" onClick={() => setShowAllMovers(v => !v)} className="mt-2 text-xs font-semibold" style={{ color: 'var(--accent)' }}>{showAllMovers ? 'Show less' : `View more (${filteredRows.length - 3})`}</button>}</section>

          {goalRows.length > 0 && <div className="card p-6 space-y-4"><div className="flex items-center justify-between"><h2 className="text-base font-bold flex items-center gap-2"><Target className="w-4 h-4" /> Goals</h2><button className="text-xs font-semibold" style={{ color: 'var(--accent)' }} onClick={() => setActiveScreen('FAMILY')}>Edit</button></div>{goalRows.map(row => <div key={row.g.id} className="space-y-1"><div className="flex justify-between text-sm"><span className="font-medium">{row.g.name}</span><span style={{ color: 'var(--muted)' }}>{row.pct}%</span></div><div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--chip)' }}><div className="h-full rounded-full" style={{ width: `${row.pct}%`, background: 'var(--accent)' }} /></div></div>)}</div>}
          {upcoming.length > 0 && <div className="card p-6 space-y-3"><h2 className="text-base font-bold flex items-center gap-2"><Calendar className="w-4 h-4" /> Coming up</h2>{upcoming.map(u => <button key={u.id} onClick={() => u.holdingId && onOpenHoldingDetail(u.holdingId)} className="w-full flex items-center justify-between text-left text-sm py-1"><span>{u.title}</span><span style={{ color: 'var(--muted)' }}>{formatCalendarDate(u.date)}</span></button>)}</div>}
        </>
      )}
    </div>
  );
};

const Mini: React.FC<{ label: string; value: bigint }> = ({ label, value }) => <div className="rounded-2xl p-4 border" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}><div className="text-xs mb-1" style={{ color: 'var(--muted)' }}>{label}</div><PrivacyValue minor={value} className="text-lg font-bold" includePaise={false} /></div>;
const Action: React.FC<{ title: string; hint: string; onClick: () => void }> = ({ title, hint, onClick }) => <button onClick={onClick} className="card p-4 text-left hover:opacity-90"><div className="font-bold text-sm">{title}</div><div className="text-xs mt-0.5" style={{ color: 'var(--muted)' }}>{hint}</div></button>;
const HoldingTable: React.FC<{ rows: GrowthHoldingRow[]; onOpen: (id: string) => void }> = ({ rows, onOpen }) => rows.length === 0 ? <p className="text-xs" style={{ color: 'var(--muted)' }}>Nothing in this slice.</p> : <div className="divide-y" style={{ borderColor: 'var(--border)' }}>{rows.map(r => <button key={r.id} type="button" onClick={() => onOpen(r.id)} className="w-full text-left py-3 flex items-center gap-3 hover:opacity-90"><div className="min-w-0 flex-1"><div className="text-sm font-medium truncate">{r.name}</div><div className="text-[11px] truncate" style={{ color: 'var(--muted)' }}>{r.ownerName} · {FAMILY_BUCKETS.find(b => b.id === r.bucket)?.title}</div></div><div className="text-right shrink-0"><PrivacyValue minor={r.valueMinor} includePaise={false} className="text-sm font-semibold" /><div className="text-[11px]"><PrivacyDelta minor={r.gainMinor} className="text-[11px] font-medium" /></div></div><ChevronRight className="w-4 h-4 shrink-0" style={{ color: 'var(--muted)' }} /></button>)}</div>;
