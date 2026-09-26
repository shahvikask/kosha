import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue, PrivacyDelta } from '../common/PrivacyValue';
import { FAMILY_BUCKETS, upcomingFromHoldings, formatCalendarDate, FamilyBucketId, bucketOf } from '../../lib/holdingView';
import { FileSpreadsheet, Target, Calendar, ChevronRight, ArrowUpRight, ShieldCheck, ShieldAlert, Plus, WalletCards, Landmark } from 'lucide-react';
import { Donut, HBars, SplitBar, TimeStepChart } from '../growth/Charts';
import { buildGrowthModel, formatPct, GrowthHoldingRow, BUCKET_COLORS } from '../../lib/growth';
import { ExposureNode, EXPOSURES } from '../../lib/taxonomy';
import { formatStatementDateShort } from '../../lib/casPython';
import { buildBooksHealth, buildStatementSteps, buildSinceLastStatement, concentrationOf, freshnessTone, firstToLastPct, HealthTone, investedSeriesFrom, issuerLabel, unknownCostRows } from '../../lib/homeDashboard';
import { UNKNOWN_INVESTED_LABEL } from '../../lib/costBasis';
import { MoneyEngine } from '../../lib/money';

interface HomeScreenProps {
  onOpenAddHolding: () => void;
  onOpenHoldingDetail: (holdingId: string) => void;
}

type Drill =
  | { kind: 'none' }
  | { kind: 'bucket'; id: string }
  | { kind: 'exposure'; id: string }
  | { kind: 'member'; id: string }
  | { kind: 'fy'; id: string };

export const HomeScreen: React.FC<HomeScreenProps> = ({ onOpenAddHolding, onOpenHoldingDetail }) => {
  const { summary, filteredHoldings, familyMembers, selectedMemberId, setActiveScreen, sgbValuationMode, ibjaRupeesPerGram, derived, lastStatementAsOf, pricesAsOf, goals, ledgerEvents, casLatest, statementSnaps, statementHoldingSnaps } = useKosha();
  const [drill, setDrill] = useState<Drill>({ kind: 'none' });
  const [showAllMovers, setShowAllMovers] = useState(false);
  const who = selectedMemberId === 'ALL' ? 'The family' : familyMembers.find(m => m.id === selectedMemberId)?.name || 'Family';
  const asOf = new Date().toISOString().slice(0, 10);
  const ibja = ibjaRupeesPerGram && ibjaRupeesPerGram > 0 ? MoneyEngine.fromRupees(ibjaRupeesPerGram) : null;
  const model = useMemo(() => buildGrowthModel(filteredHoldings, ledgerEvents.filter(e => selectedMemberId === 'ALL' || e.ownerId === selectedMemberId), derived, familyMembers, sgbValuationMode, asOf, ibja), [filteredHoldings, ledgerEvents, selectedMemberId, derived, familyMembers, sgbValuationMode, asOf, ibja]);
  const upcoming = useMemo(() => upcomingFromHoldings(filteredHoldings, goals), [filteredHoldings, goals]);
  const health = useMemo(() => buildBooksHealth({
    holdings: filteredHoldings,
    events: ledgerEvents.filter(e => selectedMemberId === 'ALL' || e.ownerId === selectedMemberId),
    derived,
    members: familyMembers,
    casLatest,
    selectedMemberId,
    sgbMode: sgbValuationMode,
    ibjaPerGramMinor: ibja,
    todayIso: asOf,
  }), [filteredHoldings, ledgerEvents, selectedMemberId, derived, familyMembers, casLatest, sgbValuationMode, asOf, ibja]);
  const datesTone = freshnessTone({ statementAsOf: lastStatementAsOf, pricesAsOf, todayIso: asOf });
  const biggest = useMemo(() => {
    const assetRows = model.holdings.filter(h => h.bucket !== 'LOAN' && h.valueMinor > 0n);
    return concentrationOf(assetRows, model.kpis.assetsMinor, 5);
  }, [model.holdings, model.kpis.assetsMinor]);
  const statementSteps = useMemo(
    () => buildStatementSteps({ rows: statementSnaps, selectedMemberId, todayIso: asOf }),
    [statementSnaps, selectedMemberId, asOf],
  );
  const sinceLast = useMemo(
    () => buildSinceLastStatement({
      snaps: statementHoldingSnaps,
      holdings: filteredHoldings,
      derived,
      events: ledgerEvents.filter(e => selectedMemberId === 'ALL' || e.ownerId === selectedMemberId),
      selectedMemberId,
      sgbMode: sgbValuationMode,
      ibjaPerGramMinor: ibja,
      todayIso: asOf,
    }),
    [statementHoldingSnaps, filteredHoldings, derived, ledgerEvents, selectedMemberId, sgbValuationMode, asOf, ibja],
  );
  const needCost = useMemo(
    () => unknownCostRows({
      holdings: filteredHoldings,
      events: ledgerEvents.filter(e => selectedMemberId === 'ALL' || e.ownerId === selectedMemberId),
      derived,
      sgbMode: sgbValuationMode,
      ibjaPerGramMinor: ibja,
    }),
    [filteredHoldings, ledgerEvents, selectedMemberId, derived, sgbValuationMode, ibja],
  );
  const filedSteps = statementSteps.filter(s => !s.isLivePad);
  const empty = filteredHoldings.length === 0;
  const k = model.kpis;
  const investedSteps = useMemo(
    () => investedSeriesFrom(statementSteps, model.kpis.investedMinor),
    [statementSteps, model.kpis.investedMinor],
  );
  const assetsPct = firstToLastPct(statementSteps.map(s => s.valueMinor));
  const investedPct = firstToLastPct(investedSteps.map(s => s.investedMinor));
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
    <div className="space-y-3 md:space-y-6 pb-2 md:pb-8">
      <section className="card card-hero p-4 md:p-8 relative overflow-hidden">
        <div className="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3 md:gap-5">
          <div className="min-w-0">
            <div className="text-[10px] md:text-xs uppercase tracking-wider font-bold mb-1 md:mb-2" style={{ color: 'var(--muted)' }}>{who} · Net worth</div>
            <PrivacyValue minor={summary.netWorthMinor} showCompact className="text-3xl md:text-5xl font-extrabold tracking-tight tabular-nums" includePaise={false} />
            <div className="mt-3 flex flex-wrap gap-1.5 md:gap-2">
              <DateChip
                label="Last statement"
                value={lastStatementAsOf ? formatStatementDateShort(lastStatementAsOf) : 'None yet'}
                tone={datesTone}
              />
              <DateChip
                label="Prices"
                value={pricesAsOf ? formatStatementDateShort(pricesAsOf) : 'Not refreshed'}
                tone={datesTone === 'ok' ? 'ok' : datesTone}
              />
              <DateChip
                label={`FY ${model.currentFy} income`}
                valueNode={<PrivacyValue minor={k.incomeThisFyMinor} includePaise={false} className="text-xs font-bold" />}
                tone="ok"
              />
            </div>
          </div>
          <div className="rounded-xl md:rounded-2xl border p-3 md:p-4 min-w-0 sm:min-w-[220px]" style={{ borderColor: k.unknownInvestedCount > 0 && k.investedMinor <= 0n ? 'var(--bad)' : 'var(--border)', background: 'var(--input)' }}>
            <div className="text-[11px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>Absolute return</div>
            {k.unknownInvestedCount > 0 && k.investedMinor <= 0n ? (
              <>
                <div className="mt-1 text-xl font-extrabold" style={{ color: 'var(--bad)' }}>{UNKNOWN_INVESTED_LABEL}</div>
                <div className="text-[11px] mt-1" style={{ color: 'var(--muted)' }}>CAS has no buy price on {k.unknownInvestedCount === 1 ? 'this holding' : `${k.unknownInvestedCount} holdings`}</div>
              </>
            ) : (
              <>
                <div className="mt-1 flex items-baseline gap-2">
                  <PrivacyDelta minor={k.absoluteGainMinor} className="text-xl font-extrabold" />
                  <span className="text-sm font-bold" style={{ color: gainGood ? 'var(--good)' : 'var(--bad)' }}>{formatPct(k.absoluteReturnPct)}</span>
                </div>
                <div className="text-[11px] mt-1" style={{ color: 'var(--muted)' }}>
                  {k.unknownInvestedCount > 0
                    ? `On holdings with a buy price · ${k.unknownInvestedCount} still need cost`
                    : 'Current value compared with invested amount'}
                </div>
              </>
            )}
          </div>
        </div>
        <div className="mt-4 md:mt-5 space-y-1.5 md:space-y-2">
          <WaterRow label="Invested" value={k.investedMinor} widthPct={barPct(k.investedMinor, k.assetsMinor)} color="var(--accent)" />
          <WaterRow label="Assets" value={k.assetsMinor} widthPct={100} color="var(--accent-2)" />
          <WaterRow label="Loans" value={k.liabilitiesMinor} widthPct={barPct(k.liabilitiesMinor, k.assetsMinor)} negative color="var(--bad)" />
          <WaterRow label="Net worth" value={summary.netWorthMinor} widthPct={barPct(summary.netWorthMinor > 0n ? summary.netWorthMinor : 0n, k.assetsMinor)} strong color="var(--bucket-shares)" />
        </div>
      </section>

      {empty && (
        <div className="card p-5 md:p-8 text-center space-y-3">
          <h2 className="text-lg md:text-xl font-bold">Start with your statement</h2>
          <p className="text-sm max-w-md mx-auto" style={{ color: 'var(--muted)' }}>Drop your CAS PDF. We read it on this phone and show every holding for you to confirm. Nothing is added until you say so.</p>
          <div className="flex flex-wrap justify-center gap-3">
            <button onClick={() => setActiveScreen('DOCUMENTS')} className="px-5 py-2.5 rounded-xl font-bold text-sm flex items-center gap-2" style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}><FileSpreadsheet className="w-4 h-4" />Read a statement</button>
            <button onClick={onOpenAddHolding} className="px-5 py-2.5 rounded-xl font-semibold text-sm border" style={{ borderColor: 'var(--border)', color: 'var(--text)' }}>Add PPF, property or a loan</button>
          </div>
        </div>
      )}

      {!empty && (
        <>
          <div className="grid grid-cols-2 gap-2 md:grid-cols-4 md:gap-3">
            <Action title="Read a statement" hint="CDSL, CAMS, KFin" onClick={() => setActiveScreen('DOCUMENTS')} tint="var(--bucket-govt)" icon={FileSpreadsheet} />
            <Action title="Add manually" hint="PPF, FD, Gold, etc." onClick={onOpenAddHolding} tint="var(--accent)" icon={Plus} />
            <Action title="See holdings" hint="Search & compare" onClick={() => setActiveScreen('HOLDINGS')} tint="var(--bucket-shares)" icon={WalletCards} />
            <Action title="Demat & folios" hint="Where it sits" onClick={() => setActiveScreen('ACCOUNTS')} tint="var(--bucket-bank)" icon={Landmark} />
          </div>

          <section className="card p-4 sm:p-5">
            <div className="flex items-start justify-between gap-3 mb-2">
              <div className="flex items-center gap-2">
                {health.tone === 'ok'
                  ? <ShieldCheck className="w-4 h-4" style={{ color: 'var(--good)' }} />
                  : <ShieldAlert className="w-4 h-4" style={{ color: health.tone === 'red' ? 'var(--bad)' : 'var(--accent)' }} />}
                <h2 className="text-sm font-bold">Books health</h2>
              </div>
              <span className="text-[11px] font-semibold" style={{ color: health.tone === 'ok' ? 'var(--good)' : health.tone === 'red' ? 'var(--bad)' : 'var(--accent)' }}>{health.headline}</span>
            </div>
            {health.findings.length === 0 ? (
              <p className="text-xs" style={{ color: 'var(--muted)' }}>Invested, statements and family PAN look in order for this view.</p>
            ) : (
              <ul className="space-y-2">
                {health.findings.map(f => (
                  <li key={f.kind}>
                    <button type="button" onClick={() => setActiveScreen(f.go)} className="w-full text-left rounded-xl border px-3 py-2 hover:opacity-90" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
                      <div className="text-sm font-semibold">{f.title}</div>
                      <div className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>{f.detail}</div>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {needCost.length > 0 && (
            <section className="card p-4 sm:p-5">
              <div className="flex items-start justify-between gap-3 mb-2">
                <div>
                  <h2 className="text-sm font-bold">Add what you paid</h2>
                  <p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>
                    CAS printed today's value as invested. Return stays blank until you edit these.
                  </p>
                </div>
                <span className="text-[11px] font-semibold" style={{ color: 'var(--bad)' }}>{needCost.length}</span>
              </div>
              <ul className="space-y-2">
                {needCost.slice(0, 6).map(r => (
                  <li key={r.id}>
                    <button type="button" onClick={() => onOpenHoldingDetail(r.id)} className="w-full text-left rounded-xl border px-3 py-2 hover:opacity-90" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-sm font-semibold truncate">{r.name}</span>
                        <PrivacyValue minor={r.valueMinor} includePaise={false} className="text-xs font-bold shrink-0" />
                      </div>
                      <div className="text-[11px] mt-0.5" style={{ color: 'var(--bad)' }}>{UNKNOWN_INVESTED_LABEL}</div>
                    </button>
                  </li>
                ))}
              </ul>
              {needCost.length > 6 && (
                <button type="button" onClick={() => setActiveScreen('HOLDINGS')} className="mt-2 text-xs font-semibold" style={{ color: 'var(--accent)' }}>
                  View all {needCost.length}
                </button>
              )}
            </section>
          )}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-3">Where the money sits</h2>{model.buckets.length === 0 ? <p className="text-xs" style={{ color: 'var(--muted)' }}>No assets to chart.</p> : <Donut slices={model.buckets} selectedId={drill.kind === 'bucket' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'bucket' && d.id === id ? { kind: 'none' } : { kind: 'bucket', id })} center={<><span className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--muted)' }}>Assets</span><PrivacyValue minor={k.assetsMinor} includePaise={false} showCompact className="text-sm font-bold" compactClassName="hidden" /></>} />}</section>
            <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-1">Look-through mix</h2><p className="text-[11px] mb-3" style={{ color: 'var(--muted)' }}>Equity, debt and gold after opening hybrid funds.</p><HBars slices={model.exposures} selectedId={drill.kind === 'exposure' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'exposure' && d.id === id ? { kind: 'none' } : { kind: 'exposure', id })} /></section>
          </div>

          {selectedMemberId === 'ALL' && model.members.length > 1 && <section className="card p-4 sm:p-5"><h2 className="text-sm font-bold mb-3">Whose name it is in</h2><HBars slices={model.members} selectedId={drill.kind === 'member' ? drill.id : null} onSelect={id => setDrill(d => d.kind === 'member' && d.id === id ? { kind: 'none' } : { kind: 'member', id })} /></section>}

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <section className="card p-4 sm:p-5">
              <div className="flex items-start justify-between gap-3 mb-1">
                <div>
                  <h2 className="text-sm font-bold">Assets over time</h2>
                  <p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>
                    Statement value. Flat between files.
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <div className="text-[10px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>Change</div>
                  <div className="text-sm font-extrabold" style={{ color: (assetsPct ?? 0) >= 0 ? 'var(--good)' : 'var(--bad)' }}>
                    {formatPct(assetsPct)}
                  </div>
                </div>
              </div>
              <TimeStepChart
                id="assets"
                steps={statementSteps}
                color="var(--accent-2)"
                valueOf={s => s.valueMinor}
                empty="Import two statements to see assets move."
              />
              {filedSteps.length > 0 && (
                <p className="text-[11px] mt-1" style={{ color: 'var(--muted)' }}>
                  {filedSteps.length === 1
                    ? `${issuerLabel(filedSteps[0].issuers[0] || 'CAS')} through ${formatStatementDateShort(filedSteps[0].date)}`
                    : filedSteps.slice(-3).map(s => `${s.issuers.map(issuerLabel).join(' + ')} ${formatStatementDateShort(s.date)}`).join(' · ')}
                </p>
              )}
            </section>
            <section className="card p-4 sm:p-5">
              <div className="flex items-start justify-between gap-3 mb-1">
                <div>
                  <h2 className="text-sm font-bold">Invested over time</h2>
                  <p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>
                    Cost printed on each file, carried forward.
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <div className="text-[10px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>Change</div>
                  <div className="text-sm font-extrabold" style={{ color: (investedPct ?? 0) >= 0 ? 'var(--good)' : 'var(--bad)' }}>
                    {formatPct(investedPct)}
                  </div>
                </div>
              </div>
              <TimeStepChart
                id="invested"
                steps={investedSteps}
                color="var(--accent)"
                valueOf={s => s.investedMinor}
                empty="CAMS/KFin prints cost. CDSL usually does not — add what you paid."
              />
            </section>
          </div>

          {sinceLast.asOf && (
            <section className="card p-4 sm:p-5">
              <div className="flex items-start justify-between gap-3 mb-3">
                <div>
                  <h2 className="text-sm font-bold">Since last statement</h2>
                  <p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>
                    {formatStatementDateShort(sinceLast.asOf)} file vs today's prices on those same holdings.
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <PrivacyDelta minor={sinceLast.deltaMinor} className="text-sm font-extrabold" />
                  {sinceLast.newCount > 0 && (
                    <div className="text-[11px]" style={{ color: 'var(--muted)' }}>{sinceLast.newCount} new since then</div>
                  )}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3 mb-3">
                <div className="rounded-xl border px-3 py-2" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
                  <div className="text-[10px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>On the file</div>
                  <PrivacyValue minor={sinceLast.thenMinor} includePaise={false} className="text-sm font-bold" />
                </div>
                <div className="rounded-xl border px-3 py-2" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
                  <div className="text-[10px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>Now</div>
                  <PrivacyValue minor={sinceLast.nowMinor} includePaise={false} className="text-sm font-bold" />
                </div>
              </div>
              {sinceLast.movers.length === 0 ? (
                <p className="text-xs" style={{ color: 'var(--muted)' }}>Prices have not moved from the statement yet, or refresh EOD prices.</p>
              ) : (
                <ul className="space-y-2">
                  {sinceLast.movers.map(r => (
                    <li key={r.id}>
                      <button type="button" onClick={() => onOpenHoldingDetail(r.id)} className="w-full text-left flex items-center justify-between gap-2">
                        <span className="truncate text-sm font-medium">{r.name}</span>
                        <PrivacyDelta minor={r.deltaMinor} className="text-xs font-bold shrink-0" />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          <section className="card p-4 sm:p-5 space-y-3"><h2 className="text-sm font-bold">Gain booked vs still on paper</h2><SplitBar leftLabel="Unrealised" rightLabel="Realised" leftMinor={k.unrealisedMinor > 0n ? k.unrealisedMinor : 0n} rightMinor={k.realisedMinor > 0n ? k.realisedMinor : 0n} leftColor="#3b82f6" rightColor="#10b981" /></section>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
            <section className="card p-4 sm:p-5">
              <h2 className="text-sm font-bold">Largest holdings</h2>
              <p className="text-[11px] mt-0.5 mb-3" style={{ color: 'var(--muted)' }}>
                {biggest.rows.length === 0
                  ? 'No assets to rank.'
                  : `Top ${biggest.rows.length} = ${biggest.topSharePct.toFixed(0)}% of assets`}
              </p>
              {biggest.rows.length === 0 ? null : (
                <ul className="space-y-2">
                  {biggest.rows.map(r => {
                    const hold = filteredHoldings.find(h => h.id === r.id);
                    const barColor = hold ? BUCKET_COLORS[bucketOf(hold)] : 'var(--accent)';
                    return (
                    <li key={r.id}>
                      <button type="button" onClick={() => onOpenHoldingDetail(r.id)} className="w-full text-left">
                        <div className="flex items-center justify-between gap-2 text-sm">
                          <span className="truncate font-medium">{r.name}</span>
                          <span className="shrink-0 text-[11px] font-bold" style={{ color: 'var(--muted)' }}>{r.pct.toFixed(0)}%</span>
                        </div>
                        <div className="mt-1 h-1.5 rounded-full overflow-hidden" style={{ background: 'var(--chip)' }}>
                          <div className="h-full rounded-full" style={{ width: `${Math.min(100, r.pct)}%`, background: barColor }} />
                        </div>
                      </button>
                    </li>
                    );
                  })}
                </ul>
              )}
            </section>
          <section className="card p-4 sm:p-5"><div className="flex items-center justify-between mb-3"><div><h2 className="text-sm font-bold">{drillTitle || 'What moved the needle'}</h2><p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>Holdings ranked by absolute gain.</p></div><button onClick={() => setActiveScreen('HOLDINGS')} className="text-xs font-bold flex items-center gap-1" style={{ color: 'var(--accent)' }}>All holdings <ArrowUpRight className="w-3.5 h-3.5" /></button></div><HoldingTable rows={movers} onOpen={onOpenHoldingDetail} />{filteredRows.length > 3 && <button type="button" onClick={() => setShowAllMovers(v => !v)} className="mt-2 text-xs font-semibold" style={{ color: 'var(--accent)' }}>{showAllMovers ? 'Show less' : `View more (${filteredRows.length - 3})`}</button>}</section>
          </div>

          {goalRows.length > 0 && <div className="card p-6 space-y-4"><div className="flex items-center justify-between"><h2 className="text-base font-bold flex items-center gap-2"><Target className="w-4 h-4" /> Goals</h2><button className="text-xs font-semibold" style={{ color: 'var(--accent)' }} onClick={() => setActiveScreen('FAMILY')}>Edit</button></div>{goalRows.map(row => <div key={row.g.id} className="space-y-1"><div className="flex justify-between text-sm"><span className="font-medium">{row.g.name}</span><span style={{ color: 'var(--muted)' }}>{row.pct}%</span></div><div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--chip)' }}><div className="h-full rounded-full" style={{ width: `${row.pct}%`, background: 'var(--accent)' }} /></div></div>)}</div>}
          {upcoming.length > 0 && <div className="card p-6 space-y-3"><h2 className="text-base font-bold flex items-center gap-2"><Calendar className="w-4 h-4" /> Coming up</h2>{upcoming.map(u => <button key={u.id} onClick={() => u.holdingId && onOpenHoldingDetail(u.holdingId)} className="w-full flex items-center justify-between text-left text-sm py-1"><span>{u.title}</span><span style={{ color: 'var(--muted)' }}>{formatCalendarDate(u.date)}</span></button>)}</div>}
        </>
      )}
    </div>
  );
};

const barPct = (part: bigint, whole: bigint): number => {
  if (whole <= 0n) return 0;
  const n = Number((part * 10000n) / whole) / 100;
  return Math.max(0, Math.min(100, n));
};

const DateChip: React.FC<{ label: string; value?: string; valueNode?: React.ReactNode; tone: HealthTone }> = ({ label, value, valueNode, tone }) => {
  const tint = tone === 'red' ? 'var(--bad)' : tone === 'amber' ? 'var(--warn)' : 'var(--good)';
  return (
  <div
    className="rounded-xl border px-2.5 py-1 md:px-3 md:py-1.5"
    style={{
      borderColor: `color-mix(in srgb, ${tint} 45%, var(--border))`,
      background: `color-mix(in srgb, ${tint} 14%, var(--input))`,
    }}
  >
    <div className="text-[10px] uppercase tracking-wide font-bold" style={{ color: 'var(--muted)' }}>{label}</div>
    <div className="text-xs font-bold mt-0.5">{valueNode || value}</div>
  </div>
  );
};

const WaterRow: React.FC<{ label: string; value: bigint; widthPct: number; negative?: boolean; strong?: boolean; color: string }> = ({ label, value, widthPct, negative, strong, color }) => (
  <div className="grid grid-cols-[4.5rem_1fr_auto] sm:grid-cols-[8.5rem_1fr_auto] items-center gap-2 sm:gap-3">
    <div className="text-[11px] sm:text-xs font-semibold" style={{ color: strong ? 'var(--text)' : 'var(--muted)' }}>{label}</div>
    <div className="h-1.5 sm:h-2 rounded-full overflow-hidden" style={{ background: 'var(--chip)' }}>
      <div
        className="h-full rounded-full"
        style={{ width: `${widthPct}%`, background: color, opacity: negative ? 0.75 : 1 }}
      />
    </div>
    <PrivacyValue minor={value} includePaise={false} className={`text-xs sm:text-sm tabular-nums ${strong ? 'font-extrabold' : 'font-semibold'}`} prefix={negative && value > 0n ? '−' : ''} />
  </div>
);

const Action: React.FC<{ title: string; hint: string; onClick: () => void; tint: string; icon: typeof FileSpreadsheet }> = ({ title, hint, onClick, tint, icon: Icon }) => (
  <button onClick={onClick} className="card p-3 md:p-4 text-left hover:opacity-90 min-h-20 min-w-0" style={{ borderColor: `color-mix(in srgb, ${tint} 35%, var(--border))` }}>
    <span className="w-8 h-8 md:w-9 md:h-9 rounded-xl flex items-center justify-center mb-1.5 md:mb-2" style={{ background: `color-mix(in srgb, ${tint} 18%, transparent)`, color: tint }}>
      <Icon className="w-4 h-4" />
    </span>
    <div className="font-bold text-sm leading-tight">{title}</div>
    <div className="text-[11px] md:text-xs mt-0.5 leading-snug" style={{ color: 'var(--muted)' }}>{hint}</div>
  </button>
);
const HoldingTable: React.FC<{ rows: GrowthHoldingRow[]; onOpen: (id: string) => void }> = ({ rows, onOpen }) => rows.length === 0 ? <p className="text-xs" style={{ color: 'var(--muted)' }}>Nothing in this slice.</p> : <div className="divide-y" style={{ borderColor: 'var(--border)' }}>{rows.map(r => <button key={r.id} type="button" onClick={() => onOpen(r.id)} className="w-full text-left py-3 flex items-center gap-3 hover:opacity-90"><span className="w-2 h-2 rounded-full shrink-0" style={{ background: BUCKET_COLORS[r.bucket] }} /><div className="min-w-0 flex-1"><div className="text-sm font-medium truncate">{r.name}</div><div className="text-[11px] truncate" style={{ color: 'var(--muted)' }}>{r.ownerName} · {FAMILY_BUCKETS.find(b => b.id === r.bucket)?.title}</div></div><div className="text-right shrink-0">{r.unknownInvested ? <div className="text-xs font-semibold" style={{ color: 'var(--bad)' }}>{UNKNOWN_INVESTED_LABEL}</div> : <><PrivacyValue minor={r.valueMinor} includePaise={false} className="text-sm font-semibold" /><div className="text-[11px]"><PrivacyDelta minor={r.gainMinor} className="text-[11px] font-medium" /></div></>}</div><ChevronRight className="w-4 h-4 shrink-0" style={{ color: 'var(--muted)' }} /></button>)}</div>;
