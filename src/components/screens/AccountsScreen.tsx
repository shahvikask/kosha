import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  metricsOf,
  displayHoldingName,
  accountTitle,
  unitWord,
} from '../../lib/holdingView';
import { MoneyEngine } from '../../lib/money';
import { ChevronDown, ChevronRight } from 'lucide-react';

interface Props {
  onOpenHoldingDetail?: (id: string) => void;
}

export const AccountsScreen: React.FC<Props> = ({ onOpenHoldingDetail }) => {
  const {
    accounts,
    holdings,
    familyMembers,
    derived,
    sgbValuationMode,
    selectedMemberId,
  } = useKosha();

  const [openIds, setOpenIds] = useState<Set<string>>(() => new Set());
  const [kindFilter, setKindFilter] = useState<'ALL' | 'MF' | 'DEMAT'>('ALL');

  const toggle = (id: string) => {
    setOpenIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const rows = useMemo(() => {
    return accounts
      .filter(a => selectedMemberId === 'ALL' || a.ownerId === selectedMemberId)
      .map(a => {
        const hs = holdings.filter(h => h.accountId === a.id || (
          !h.accountId && (h.accountNumberMasked === a.identifierMasked || h.symbolOrFolio === a.identifierMasked)
        ));
        const valued = hs
          .map(h => ({ h, m: metricsOf(h, derived, sgbValuationMode) }))
          .filter(r => r.h.category === 'LIABILITY' || r.m.qty > 0n)
          .sort((x, y) => (x.m.value < y.m.value ? 1 : -1));
        const value = valued.reduce((s, r) => s + r.m.value, 0n);
        const owner = familyMembers.find(m => m.id === a.ownerId);
        const isFolio = a.accountType === 'MF_FOLIO';
        return {
          a,
          hs: valued,
          value,
          owner,
          title: accountTitle(a.custodian, hs, a.accountType),
          kind: isFolio ? 'Mutual fund' : 'Demat',
          number: a.identifierMasked || (a.dpId && a.clientId ? `${a.dpId} ${a.clientId}` : a.boId) || '',
        };
      })
      .filter(r => {
        if (kindFilter === 'ALL') return true;
        if (kindFilter === 'MF') return r.kind === 'Mutual fund';
        return r.kind === 'Demat';
      })
      .sort((x, y) => (x.value < y.value ? 1 : -1));
  }, [accounts, holdings, familyMembers, derived, sgbValuationMode, selectedMemberId, kindFilter]);

  return (
    <div className="space-y-4 pb-12">
      <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold">Accounts</h1>
          <p className="text-sm" style={{ color: 'var(--muted)' }}>
            Demat accounts and mutual-fund folios from your statements. Tap the holding count to see what sits on that folio.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {([
            ['ALL', 'All'],
            ['MF', 'Mutual fund'],
            ['DEMAT', 'Demat'],
          ] as const).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setKindFilter(id)}
              className="px-3 py-1.5 rounded-full text-xs font-semibold border"
              style={{
                background: kindFilter === id ? 'var(--accent)' : 'var(--card)',
                color: kindFilter === id ? 'var(--accent-fg)' : 'var(--text)',
                borderColor: kindFilter === id ? 'var(--accent)' : 'var(--border)',
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      {rows.length === 0 ? (
        <div className="card p-8 text-center text-sm" style={{ color: 'var(--muted)' }}>
          No demat or folio yet. They appear after you confirm a CDSL, NSDL, CAMS or KFintech PDF.
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {rows.map(({ a, hs, value, owner, title, kind, number }) => {
            const expanded = openIds.has(a.id);
            return (
              <div key={a.id} className="card p-3.5 flex flex-col min-w-0">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="font-bold text-sm truncate">{title}</div>
                    <div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>
                      {kind}
                      {owner ? ` · ${owner.name}` : ''}
                      {number ? ` · ${number}` : ''}
                    </div>
                  </div>
                  <PrivacyValue minor={value} includePaise={false} className="text-sm font-bold shrink-0" />
                </div>

                <button
                  type="button"
                  onClick={() => toggle(a.id)}
                  className="mt-2 text-[11px] font-semibold flex items-center gap-0.5 w-fit"
                  style={{ color: 'var(--accent)' }}
                  aria-expanded={expanded}
                >
                  {expanded ? <ChevronDown className="w-3.5 h-3.5" /> : <ChevronRight className="w-3.5 h-3.5" />}
                  {hs.length} holding{hs.length === 1 ? '' : 's'}
                </button>

                {expanded && (
                  <div className="mt-2 pt-1 border-t divide-y" style={{ borderColor: 'var(--border)' }}>
                    {hs.length === 0 ? (
                      <div className="py-2 text-[11px]" style={{ color: 'var(--muted)' }}>
                        Nothing on this account right now.
                      </div>
                    ) : (
                      hs.map(({ h, m }) => {
                        const body = (
                          <>
                            <div className="min-w-0">
                              <div className="text-xs font-medium truncate">{displayHoldingName(h.name)}</div>
                              <div className="text-[10px] mt-0.5" style={{ color: 'var(--muted)' }}>
                                {unitWord(h) && m.qty > 0n
                                  ? `${MoneyEngine.toQty(m.qty).toLocaleString('en-IN')} ${unitWord(h)}`
                                  : '—'}
                              </div>
                            </div>
                            <PrivacyValue minor={m.value} includePaise={false} className="text-xs font-semibold shrink-0" />
                          </>
                        );
                        return onOpenHoldingDetail ? (
                          <button
                            key={h.id}
                            type="button"
                            onClick={() => onOpenHoldingDetail(h.id)}
                            className="w-full text-left py-2 flex items-center justify-between gap-2 hover:opacity-90"
                          >
                            {body}
                          </button>
                        ) : (
                          <div key={h.id} className="py-2 flex items-center justify-between gap-2">
                            {body}
                          </div>
                        );
                      })
                    )}
                  </div>
                )}

                {a.nominee ? (
                  <div className="mt-2 text-[10px] leading-snug" style={{ color: 'var(--muted)' }}>
                    Family note · {a.nominee}. This is not the nominee on the registrar — that comes from the statement.
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
