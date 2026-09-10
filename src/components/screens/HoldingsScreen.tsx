import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  FAMILY_BUCKETS,
  FamilyBucketId,
  bucketOf,
  metricsOf,
  plainCategory,
  unitWord,
  whereHeld,
  displayHoldingName,
} from '../../lib/holdingView';
import { MoneyEngine } from '../../lib/money';
import { Search, Plus } from 'lucide-react';
import { holdingReturn, bucketMetricHint } from '../../lib/returnMetrics';

interface Props {
  onOpenAddHolding: () => void;
  onOpenHoldingDetail: (id: string) => void;
}

export const HoldingsScreen: React.FC<Props> = ({ onOpenAddHolding, onOpenHoldingDetail }) => {
  const { filteredHoldings, familyMembers, derived, sgbValuationMode, ledgerEvents } = useKosha();
  const [q, setQ] = useState('');
  const [bucket, setBucket] = useState<FamilyBucketId | 'ALL'>('ALL');

  const asOf = new Date().toISOString().slice(0, 10);
  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return filteredHoldings
      .map(h => ({
        h,
        m: metricsOf(h, derived, sgbValuationMode),
        b: bucketOf(h),
        r: holdingReturn(h, ledgerEvents, derived, sgbValuationMode, asOf),
      }))
      .filter(r => (bucket === 'ALL' ? true : r.b === bucket))
      .filter(r => {
        if (!needle) return true;
        const owner = familyMembers.find(m => m.id === r.h.ownerId)?.name || '';
        return [r.h.name, r.h.isin, r.h.amc, r.h.symbolOrFolio, owner, r.h.institutionName]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
          .includes(needle);
      })
      .sort((a, b) => (a.m.value < b.m.value ? 1 : -1));
  }, [filteredHoldings, derived, sgbValuationMode, q, bucket, familyMembers, ledgerEvents, asOf]);

  const hint = bucket === 'ALL' ? null : bucketMetricHint(bucket);

  return (
    <div className="space-y-5 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Holdings</h1>
          <p className="text-sm" style={{ color: 'var(--muted)' }}>
            Search by name. Tap a row for folio, demat and ISIN.
          </p>
        </div>
        <button
          onClick={onOpenAddHolding}
          className="px-4 py-2 rounded-xl text-sm font-bold flex items-center gap-1.5"
          style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
        >
          <Plus className="w-4 h-4" /> Add manually
        </button>
      </div>

      <div className="relative">
        <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2" style={{ color: 'var(--muted)' }} />
        <input
          value={q}
          onChange={e => setQ(e.target.value)}
          placeholder="Edelweiss, HDFC Bank, folio…"
          className="w-full pl-10 pr-3 py-2.5 rounded-xl border text-sm"
          style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
        />
      </div>

      <div className="flex flex-wrap gap-2">
        <Chip active={bucket === 'ALL'} onClick={() => setBucket('ALL')}>All</Chip>
        {FAMILY_BUCKETS.map(b => (
          <Chip key={b.id} active={bucket === b.id} onClick={() => setBucket(b.id)}>
            {b.title}
          </Chip>
        ))}
      </div>
      {hint && (
        <p className="text-[11px] -mt-1" style={{ color: 'var(--muted)' }}>{hint}</p>
      )}

      {rows.length === 0 ? (
        <div className="card p-8 text-center text-sm" style={{ color: 'var(--muted)' }}>
          {filteredHoldings.length === 0
            ? 'Nothing here yet. Read a statement or add PPF / property manually.'
            : 'No holding matches that search.'}
        </div>
      ) : (
        <div className="card divide-y overflow-hidden" style={{ borderColor: 'var(--border)' }}>
          {rows.map(({ h, m, r }) => {
            const owner = familyMembers.find(fm => fm.id === h.ownerId);
            return (
              <button
                key={h.id}
                onClick={() => onOpenHoldingDetail(h.id)}
                className="w-full text-left px-4 py-3.5 flex items-center justify-between gap-3 hover:opacity-90"
              >
                <div className="min-w-0">
                  <div className="font-semibold text-sm truncate">{displayHoldingName(h.name)}</div>
                  <div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>
                    {plainCategory(h.category)}
                    {owner ? ` · ${owner.name}` : ''}
                    {` · ${whereHeld(h)}`}
                    {unitWord(h) && m.qty > 0n
                      ? ` · ${MoneyEngine.toQty(m.qty).toLocaleString('en-IN')} ${unitWord(h)}`
                      : ''}
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <PrivacyValue minor={m.value} includePaise={false} className="text-sm font-bold" />
                  {h.category !== 'LIABILITY' && (
                    <div className="text-[11px] font-medium" style={{ color: (r.primaryPct ?? 0) >= 0 ? 'var(--good)' : 'var(--bad)' }}>
                      {r.profile.primaryLabel} {r.primaryText}
                    </div>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};

const Chip: React.FC<{ active: boolean; onClick: () => void; children: React.ReactNode }> = ({
  active,
  onClick,
  children,
}) => (
  <button
    onClick={onClick}
    className="px-3 py-1.5 rounded-full text-xs font-semibold border"
    style={
      active
        ? { background: 'var(--accent)', color: 'var(--accent-fg)', borderColor: 'transparent' }
        : { borderColor: 'var(--border)', color: 'var(--text)' }
    }
  >
    {children}
  </button>
);
