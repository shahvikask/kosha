import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue, PrivacyDelta } from '../common/PrivacyValue';
import { Donut, HBars, AreaFlow, StackedFy, SplitBar } from '../growth/Charts';
import {
  buildGrowthModel,
  formatPct,
  GrowthHoldingRow,
} from '../../lib/growth';
import { formatStatementDate } from '../../lib/casPython';
import { TrendingUp, ChevronRight, X } from 'lucide-react';
import { FAMILY_BUCKETS, FamilyBucketId } from '../../lib/holdingView';
import { ExposureNode, EXPOSURES } from '../../lib/taxonomy';

type Drill =
  | { kind: 'none' }
  | { kind: 'bucket'; id: string }
  | { kind: 'exposure'; id: string }
  | { kind: 'member'; id: string }
  | { kind: 'fy'; id: string };

interface Props {
  onOpenHoldingDetail: (id: string) => void;
}

export const GrowthScreen: React.FC<Props> = ({ onOpenHoldingDetail }) => {
  const {
    filteredHoldings,
    ledgerEvents,
    familyMembers,
    selectedMemberId,
    derived,
    sgbValuationMode,
    lastStatementAsOf,
    pricesAsOf,
    setActiveScreen,
  } = useKosha();

  const [drill, setDrill] = useState<Drill>({ kind: 'none' });

  const asOf = new Date().toISOString().slice(0, 10);
  const model = useMemo(
    () =>
      buildGrowthModel(
        filteredHoldings,
        ledgerEvents.filter(e =>
          selectedMemberId === 'ALL' ? true : e.ownerId === selectedMemberId,
        ),
        derived,
        familyMembers,
        sgbValuationMode,
        asOf,
      ),
    [filteredHoldings, ledgerEvents, selectedMemberId, derived, familyMembers, sgbValuationMode, asOf],
  );

  const who =
    selectedMemberId === 'ALL'
      ? 'the family'
      : familyMembers.find(m => m.id === selectedMemberId)?.name || 'this profile';

  const filteredRows = useMemo(() => {
    const hs = model.holdings.filter(h => h.bucket !== 'LOAN');
    if (drill.kind === 'bucket') return hs.filter(h => h.bucket === drill.id);
    if (drill.kind === 'exposure') return hs.filter(h => h.exposure === drill.id);
    if (drill.kind === 'member') return hs.filter(h => h.ownerId === drill.id);
    return hs;
  }, [model.holdings, drill]);

  const fyEvents = useMemo(() => {
    if (drill.kind !== 'fy') return [];
    return ledgerEvents.filter(
      e =>
        (e.eventType === 'DIVIDEND' || e.eventType === 'INTEREST') &&
        e.eventDate >= `${drill.id.split('-')[0]}-04-01` &&
        e.eventDate < `${Number(drill.id.split('-')[0]) + 1}-04-01` &&
        (selectedMemberId === 'ALL' || e.ownerId === selectedMemberId),
    );
  }, [drill, ledgerEvents, selectedMemberId]);

  const drillTitle = (() => {
    if (drill.kind === 'bucket')
      return FAMILY_BUCKETS.find(b => b.id === (drill.id as FamilyBucketId))?.title || drill.id;
    if (drill.kind === 'exposure')
      return EXPOSURES.find(e => e.id === (drill.id as ExposureNode))?.label || drill.id;
    if (drill.kind === 'member')
      return familyMembers.find(m => m.id === drill.id)?.name || drill.id;
    if (drill.kind === 'fy') return `Income · FY ${drill.id}`;
    return null;
  })();

  if (filteredHoldings.length === 0) {
    return (
      <div className="card p-8 text-center space-y-3">
        <TrendingUp className="w-8 h-8 mx-auto" style={{ color: 'var(--accent)' }} />
        <h2 className="text-xl font-bold">Growth needs holdings</h2>
        <p className="text-sm max-w-md mx-auto" style={{ color: 'var(--muted)' }}>
          Confirm a CAS statement or add holdings. Charts are built from the vault on this computer — nothing is sent away.
        </p>
        <button
          type="button"
          onClick={() => setActiveScreen('DOCUMENTS')}
          className="px-5 py-2.5 rounded-xl font-bold text-sm"
          style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
        >
          Read a statement
        </button>
      </div>
    );
  }

  const k = model.kpis;
  const gainGood = k.absoluteGainMinor >= 0n;

  return (
    <div className="space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-bold">Growth</h1>
        <p className="text-sm mt-1" style={{ color: 'var(--muted)' }}>
          How {who}'s money has moved. Tap a slice to drill in.
          {pricesAsOf ? ` Prices as on ${formatStatementDate(pricesAsOf)} — last close / NAV, not live.` : ''}
          {lastStatementAsOf ? ` Statement through ${formatStatementDate(lastStatementAsOf)}.` : ''}
        </p>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-5 gap-3">
        <Kpi label="You own" minor={k.assetsMinor} />
        <Kpi label="Put in" minor={k.investedMinor} />
        <Kpi
          label="Absolute return"
          extra={formatPct(k.absoluteReturnPct)}
          extraGood={gainGood}
        >
          <PrivacyDelta minor={k.absoluteGainMinor} className="text-lg font-bold" />
        </Kpi>
        <Kpi label="XIRR" extra={k.yearsHeld != null ? `${k.yearsHeld.toFixed(1)}y` : undefined}>
          <span className="text-lg font-bold" style={{ color: (k.xirrPct ?? 0) >= 0 ? 'var(--good)' : 'var(--bad)' }}>
            {formatPct(k.xirrPct)}
          </span>
        </Kpi>
        <Kpi label="CAGR" extra="point to point">
          <span className="text-lg font-bold" style={{ color: (k.cagrPct ?? 0) >= 0 ? 'var(--good)' : 'var(--bad)' }}>
            {formatPct(k.cagrPct)}
          </span>
        </Kpi>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <section className="card p-4 sm:p-5">
          <h2 className="text-sm font-bold mb-3">Where the money sits</h2>
          {model.buckets.length === 0 ? (
            <p className="text-xs" style={{ color: 'var(--muted)' }}>No assets to chart.</p>
          ) : (
            <Donut
              slices={model.buckets}
              selectedId={drill.kind === 'bucket' ? drill.id : null}
              onSelect={id => setDrill(d => (d.kind === 'bucket' && d.id === id ? { kind: 'none' } : { kind: 'bucket', id }))}
              center={
                <>
                  <span className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--muted)' }}>Assets</span>
                  <PrivacyValue minor={k.assetsMinor} includePaise={false} showCompact className="text-sm font-bold" compactClassName="hidden" />
                </>
              }
            />
          )}
        </section>

        <section className="card p-4 sm:p-5">
          <h2 className="text-sm font-bold mb-1">Look-through mix</h2>
          <p className="text-[11px] mb-3" style={{ color: 'var(--muted)' }}>
            Equity, debt and gold after opening hybrid funds — SEBI-style, not the wrapper.
          </p>
          <HBars
            slices={model.exposures}
            selectedId={drill.kind === 'exposure' ? drill.id : null}
            onSelect={id => setDrill(d => (d.kind === 'exposure' && d.id === id ? { kind: 'none' } : { kind: 'exposure', id }))}
          />
        </section>
      </div>

      {selectedMemberId === 'ALL' && model.members.length > 1 && (
        <section className="card p-4 sm:p-5">
          <h2 className="text-sm font-bold mb-3">Who holds it</h2>
          <HBars
            slices={model.members}
            selectedId={drill.kind === 'member' ? drill.id : null}
            onSelect={id => setDrill(d => (d.kind === 'member' && d.id === id ? { kind: 'none' } : { kind: 'member', id }))}
          />
        </section>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        <section className="card p-4 sm:p-5">
          <h2 className="text-sm font-bold mb-1">Money in, over time</h2>
          <p className="text-[11px] mb-2" style={{ color: 'var(--muted)' }}>
            <span className="inline-block w-2 h-2 rounded-sm mr-1" style={{ background: '#f59e0b' }} />
            Put in (buys & openings)
            <span className="inline-block w-2 h-2 rounded-sm ml-3 mr-1" style={{ background: '#10b981' }} />
            Dividends & interest
          </p>
          <AreaFlow months={model.months} />
        </section>

        <section className="card p-4 sm:p-5">
          <h2 className="text-sm font-bold mb-1">Income by Indian FY</h2>
          <p className="text-[11px] mb-2" style={{ color: 'var(--muted)' }}>
            April–March ·{' '}
            <span style={{ color: '#3b82f6' }}>Dividend</span>
            {' · '}
            <span style={{ color: '#10b981' }}>Interest / SGB coupon</span>
            {' · '}
            <span style={{ color: '#8b5cf6' }}>Rental</span>
          </p>
          <StackedFy
            years={model.incomeYears}
            selectedFy={drill.kind === 'fy' ? drill.id : null}
            onSelect={id => setDrill(d => (d.kind === 'fy' && d.id === id ? { kind: 'none' } : { kind: 'fy', id }))}
          />
          <div className="mt-3 text-xs" style={{ color: 'var(--muted)' }}>
            This FY {model.currentFy}:{' '}
            <PrivacyValue minor={k.incomeThisFyMinor} includePaise={false} className="text-xs font-semibold" />
          </div>
        </section>
      </div>

      <section className="card p-4 sm:p-5 space-y-3">
        <h2 className="text-sm font-bold">Gain that is booked vs still on paper</h2>
        <SplitBar
          leftLabel="Unrealised"
          rightLabel="Realised"
          leftMinor={k.unrealisedMinor > 0n ? k.unrealisedMinor : 0n}
          rightMinor={k.realisedMinor > 0n ? k.realisedMinor : 0n}
          leftColor="#3b82f6"
          rightColor="#10b981"
        />
        <p className="text-[11px]" style={{ color: 'var(--muted)' }}>
          Unrealised is current value minus what you put in. Realised is from sales in the record book. Tax on equity sales is on Documents.
        </p>
      </section>

      {drill.kind !== 'none' && (
        <section className="card p-4 sm:p-5">
          <div className="flex items-center justify-between gap-2 mb-3">
            <h2 className="text-sm font-bold truncate">{drillTitle}</h2>
            <button
              type="button"
              onClick={() => setDrill({ kind: 'none' })}
              className="text-xs font-semibold flex items-center gap-1"
              style={{ color: 'var(--accent)' }}
            >
              <X className="w-3.5 h-3.5" /> Clear
            </button>
          </div>
          {drill.kind === 'fy' ? (
            fyEvents.length === 0 ? (
              <p className="text-xs" style={{ color: 'var(--muted)' }}>No income rows in that year.</p>
            ) : (
              <ul className="divide-y" style={{ borderColor: 'var(--border)' }}>
                {fyEvents.map(ev => (
                  <li key={ev.id} className="py-2 flex justify-between gap-2 text-sm">
                    <span className="truncate">
                      {ev.eventType === 'DIVIDEND' ? 'Dividend' : 'Interest'} · {ev.holdingName}
                      <span className="block text-[11px]" style={{ color: 'var(--muted)' }}>{ev.eventDate}</span>
                    </span>
                    <PrivacyValue minor={BigInt(ev.totalAmountMinor || '0')} includePaise={false} className="text-sm font-semibold" />
                  </li>
                ))}
              </ul>
            )
          ) : (
            <HoldingTable rows={filteredRows} onOpen={onOpenHoldingDetail} />
          )}
        </section>
      )}

      {drill.kind === 'none' && (
        <section className="card p-4 sm:p-5">
          <h2 className="text-sm font-bold mb-3">What moved the needle</h2>
          <HoldingTable rows={filteredRows.slice(0, 12)} onOpen={onOpenHoldingDetail} />
        </section>
      )}

      <p className="text-[11px] leading-relaxed" style={{ color: 'var(--muted)' }}>
        XIRR is money-weighted (buys, sells, dividends, interest, plus today's value). CAGR is
        (current ÷ put in)^(1 ÷ years) from the first record — usually the statement date if you
        imported a CAS. Absolute return is not annualised. CMP is previous close / AMFI NAV, not live LTP.
      </p>
    </div>
  );
};

const Kpi: React.FC<{
  label: string;
  minor?: bigint;
  extra?: string;
  extraGood?: boolean;
  children?: React.ReactNode;
}> = ({ label, minor, extra, extraGood, children }) => (
  <div className="card p-3.5">
    <div className="text-[11px] uppercase tracking-wide font-semibold mb-1" style={{ color: 'var(--muted)' }}>
      {label}
    </div>
    {children || (minor != null && (
      <PrivacyValue minor={minor} includePaise={false} className="text-lg font-bold" />
    ))}
    {extra && (
      <div className="text-[11px] mt-0.5" style={{ color: extraGood === false ? 'var(--bad)' : extraGood ? 'var(--good)' : 'var(--muted)' }}>
        {extra}
      </div>
    )}
  </div>
);

const HoldingTable: React.FC<{
  rows: GrowthHoldingRow[];
  onOpen: (id: string) => void;
}> = ({ rows, onOpen }) => {
  if (rows.length === 0) {
    return <p className="text-xs" style={{ color: 'var(--muted)' }}>Nothing in this slice.</p>;
  }
  return (
    <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
      {rows.map(r => (
        <button
          key={r.id}
          type="button"
          onClick={() => onOpen(r.id)}
          className="w-full text-left py-2.5 flex items-center gap-3 hover:opacity-90"
        >
          <div className="min-w-0 flex-1">
            <div className="text-sm font-medium truncate">{r.name}</div>
            <div className="text-[11px] truncate" style={{ color: 'var(--muted)' }}>
              {r.ownerName} · {FAMILY_BUCKETS.find(b => b.id === r.bucket)?.title}
            </div>
          </div>
          <div className="text-right shrink-0">
            <PrivacyValue minor={r.valueMinor} includePaise={false} className="text-sm font-semibold" />
            <div className="text-[11px]">
              <PrivacyDelta minor={r.gainMinor} className="text-[11px] font-medium" />
            </div>
          </div>
          <ChevronRight className="w-4 h-4 shrink-0" style={{ color: 'var(--muted)' }} />
        </button>
      ))}
    </div>
  );
};
