import React, { useMemo } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  FAMILY_BUCKETS,
  bucketOf,
  metricsOf,
  upcomingFromHoldings,
  formatCalendarDate,
} from '../../lib/holdingView';
import { MoneyEngine } from '../../lib/money';
import { FileSpreadsheet, Target, Calendar } from 'lucide-react';

interface HomeScreenProps {
  onOpenAddHolding: () => void;
  onOpenAddRecord: () => void;
  onOpenHoldingDetail: (holdingId: string) => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  onOpenAddHolding,
  onOpenHoldingDetail,
}) => {
  const {
    summary,
    filteredHoldings,
    familyMembers,
    selectedMemberId,
    setActiveScreen,
    sgbValuationMode,
    derived,
    lastStatementAsOf,
    goals,
  } = useKosha();

  const who =
    selectedMemberId === 'ALL'
      ? 'The family'
      : familyMembers.find(m => m.id === selectedMemberId)?.name || 'Family';

  const buckets = useMemo(() => {
    const map = new Map<string, bigint>();
    for (const h of filteredHoldings) {
      const id = bucketOf(h);
      map.set(id, (map.get(id) || 0n) + metricsOf(h, derived, sgbValuationMode).value);
    }
    return FAMILY_BUCKETS.map(b => ({
      ...b,
      value: map.get(b.id) || 0n,
    })).filter(b => b.id === 'LOAN' || b.value > 0n || filteredHoldings.length === 0);
  }, [filteredHoldings, derived, sgbValuationMode]);

  const byPerson = useMemo(() => {
    return familyMembers.map(m => {
      const hs = filteredHoldings.filter(h => h.ownerId === m.id);
      const value = hs.reduce((s, h) => s + metricsOf(h, derived, sgbValuationMode).value, 0n);
      return { member: m, value, count: hs.length };
    }).filter(r => selectedMemberId === 'ALL' || r.member.id === selectedMemberId);
  }, [familyMembers, filteredHoldings, derived, sgbValuationMode, selectedMemberId]);

  const upcoming = useMemo(
    () => upcomingFromHoldings(filteredHoldings, goals),
    [filteredHoldings, goals]
  );

  const goalRows = useMemo(() => {
    return goals.map(g => {
      const tagged = filteredHoldings.filter(h =>
        [h.tag, ...(h.tags || [])].some(t => (t || '').toLowerCase() === g.name.toLowerCase())
      );
      const current = tagged.reduce((s, h) => s + metricsOf(h, derived, sgbValuationMode).value, 0n);
      const target = BigInt(g.targetMinor || '0');
      const pct = target > 0n ? Math.min(100, Number((current * 100n) / target)) : 0;
      return { g, current, target, pct };
    });
  }, [goals, filteredHoldings, derived, sgbValuationMode]);

  const empty = filteredHoldings.length === 0;

  return (
    <div className="space-y-6 pb-12">
      <div className="card p-6 sm:p-8 relative overflow-hidden">
        <div className="text-xs uppercase tracking-wider font-bold mb-2" style={{ color: 'var(--muted)' }}>
          {who} · what you own minus loans
        </div>
        <PrivacyValue
          minor={summary.netWorthMinor}
          showCompact
          className="text-4xl sm:text-5xl font-extrabold tracking-tight"
        />
        <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-3">
          <Mini label="You own" value={summary.totalAssetsMinor} good />
          <Mini label="You owe" value={summary.totalLiabilitiesMinor} bad />
          <Mini label="Put in" value={summary.totalInvestedMinor} />
        </div>
        <div className="mt-3 text-xs" style={{ color: 'var(--muted)' }}>
          {lastStatementAsOf
            ? 'Figures follow your last confirmed statement, plus anything you added manually.'
            : 'Import a CDSL, NSDL, CAMS or KFintech PDF to fill this in.'}
        </div>
      </div>

      {empty && (
        <div className="card p-8 text-center space-y-4">
          <h2 className="text-xl font-bold">Start with your statement</h2>
          <p className="text-sm max-w-md mx-auto" style={{ color: 'var(--muted)' }}>
            Drop your CAS PDF. We read it on this computer and show every holding for you to confirm.
            Nothing is added until you say so.
          </p>
          <div className="flex flex-wrap justify-center gap-3">
            <button
              onClick={() => setActiveScreen('DOCUMENTS')}
              className="px-5 py-2.5 rounded-xl font-bold text-sm flex items-center gap-2"
              style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
            >
              <FileSpreadsheet className="w-4 h-4" />
              Read a statement
            </button>
            <button
              onClick={onOpenAddHolding}
              className="px-5 py-2.5 rounded-xl font-semibold text-sm border"
              style={{ borderColor: 'var(--border)', color: 'var(--text)' }}
            >
              Add PPF, property or a loan
            </button>
          </div>
        </div>
      )}

      {!empty && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            <Action
              title="Read a statement"
              hint="CDSL, CAMS, KFin"
              onClick={() => setActiveScreen('DOCUMENTS')}
            />
            <Action title="Add manually" hint="PPF, home, gold" onClick={onOpenAddHolding} />
            <Action title="See holdings" hint="Search by name" onClick={() => setActiveScreen('HOLDINGS')} />
            <Action title="Demat & folios" hint="Where it sits" onClick={() => setActiveScreen('ACCOUNTS')} />
          </div>

          <div className="card p-6 space-y-4">
            <h2 className="text-base font-bold">Where the money is</h2>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {buckets.map(b => (
                <button
                  key={b.id}
                  onClick={() => setActiveScreen('HOLDINGS')}
                  className="flex items-center justify-between p-3.5 rounded-2xl border text-left"
                  style={{ borderColor: 'var(--border)', background: 'var(--input)' }}
                >
                  <div>
                    <div className="font-semibold text-sm">{b.title}</div>
                    <div className="text-[11px]" style={{ color: 'var(--muted)' }}>{b.hint}</div>
                  </div>
                  <PrivacyValue
                    minor={b.value}
                    includePaise={false}
                    className="text-sm font-bold"
                  />
                </button>
              ))}
            </div>
          </div>

          {selectedMemberId === 'ALL' && byPerson.length > 1 && (
            <div className="card p-6 space-y-3">
              <h2 className="text-base font-bold">Whose name it is in</h2>
              {byPerson.map(row => (
                <div key={row.member.id} className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ background: row.member.color }} />
                    <span className="text-sm font-medium">{row.member.name}</span>
                    <span className="text-xs" style={{ color: 'var(--muted)' }}>{row.member.relation}</span>
                  </div>
                  <PrivacyValue minor={row.value} includePaise={false} className="text-sm font-bold" />
                </div>
              ))}
            </div>
          )}

          {goalRows.length > 0 && (
            <div className="card p-6 space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-base font-bold flex items-center gap-2">
                  <Target className="w-4 h-4" /> Goals
                </h2>
                <button
                  className="text-xs font-semibold"
                  style={{ color: 'var(--accent)' }}
                  onClick={() => setActiveScreen('FAMILY')}
                >
                  Edit
                </button>
              </div>
              {goalRows.map(row => (
                <div key={row.g.id} className="space-y-1">
                  <div className="flex justify-between text-sm">
                    <span className="font-medium">{row.g.name}</span>
                    <span style={{ color: 'var(--muted)' }}>{row.pct}%</span>
                  </div>
                  <div className="h-2 rounded-full overflow-hidden" style={{ background: 'var(--chip)' }}>
                    <div
                      className="h-full rounded-full"
                      style={{ width: `${row.pct}%`, background: 'var(--accent)' }}
                    />
                  </div>
                </div>
              ))}
            </div>
          )}

          {upcoming.length > 0 && (
            <div className="card p-6 space-y-3">
              <h2 className="text-base font-bold flex items-center gap-2">
                <Calendar className="w-4 h-4" /> Coming up
              </h2>
              {upcoming.map(u => (
                <button
                  key={u.id}
                  onClick={() => u.holdingId && onOpenHoldingDetail(u.holdingId)}
                  className="w-full flex items-center justify-between text-left text-sm py-1"
                >
                  <span>{u.title}</span>
                  <span style={{ color: 'var(--muted)' }}>{formatCalendarDate(u.date)}</span>
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
};

const Mini: React.FC<{ label: string; value: bigint; good?: boolean; bad?: boolean }> = ({
  label,
  value,
  good,
  bad,
}) => (
  <div className="rounded-2xl p-4 border" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
    <div className="text-xs mb-1" style={{ color: 'var(--muted)' }}>{label}</div>
    <PrivacyValue
      minor={value}
      className="text-lg font-bold"
      includePaise={false}
    />
  </div>
);

const Action: React.FC<{ title: string; hint: string; onClick: () => void }> = ({ title, hint, onClick }) => (
  <button
    onClick={onClick}
    className="card p-4 text-left hover:opacity-90"
  >
    <div className="font-bold text-sm">{title}</div>
    <div className="text-xs mt-0.5" style={{ color: 'var(--muted)' }}>{hint}</div>
  </button>
);
