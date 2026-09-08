import React, { useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { PrivacyValue } from '../common/PrivacyValue';
import { metricsOf } from '../../lib/holdingView';

export const AccountsScreen: React.FC = () => {
  const {
    accounts,
    holdings,
    familyMembers,
    derived,
    sgbValuationMode,
    selectedMemberId,
    updateAccountNominee,
  } = useKosha();

  const [editing, setEditing] = useState<string | null>(null);
  const [nomineeDraft, setNomineeDraft] = useState('');

  const rows = useMemo(() => {
    return accounts
      .filter(a => selectedMemberId === 'ALL' || a.ownerId === selectedMemberId)
      .map(a => {
        const hs = holdings.filter(h => h.accountId === a.id || (
          !h.accountId && (h.accountNumberMasked === a.identifierMasked || h.symbolOrFolio === a.identifierMasked)
        ));
        const value = hs.reduce((s, h) => s + metricsOf(h, derived, sgbValuationMode).value, 0n);
        const owner = familyMembers.find(m => m.id === a.ownerId);
        return { a, hs, value, owner };
      })
      .sort((x, y) => (x.value < y.value ? 1 : -1));
  }, [accounts, holdings, familyMembers, derived, sgbValuationMode, selectedMemberId]);

  const saveNominee = async (id: string) => {
    await updateAccountNominee(id, nomineeDraft.trim());
    setEditing(null);
  };

  return (
    <div className="space-y-5 pb-12">
      <div>
        <h1 className="text-2xl font-bold">Accounts</h1>
        <p className="text-sm" style={{ color: 'var(--muted)' }}>
          Demat accounts and mutual-fund folios from your statements. Add a nominee so the family printout is complete.
        </p>
      </div>

      {rows.length === 0 ? (
        <div className="card p-8 text-center text-sm" style={{ color: 'var(--muted)' }}>
          No demat or folio yet. They appear after you confirm a CDSL, NSDL, CAMS or KFintech PDF.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4">
          {rows.map(({ a, hs, value, owner }) => (
            <div key={a.id} className="card p-5 space-y-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="font-bold">{a.custodian}</div>
                  <div className="text-xs mt-0.5" style={{ color: 'var(--muted)' }}>
                    {a.accountType === 'MF_FOLIO' ? 'Mutual fund folio' : 'Demat account'}
                    {owner ? ` · ${owner.name}` : ''}
                  </div>
                </div>
                <PrivacyValue minor={value} includePaise={false} className="text-base font-bold" />
              </div>
              <div className="text-xs space-y-1" style={{ color: 'var(--muted)' }}>
                {a.identifierMasked && <div>Folio / BOID · {a.identifierMasked}</div>}
                {a.dpId && <div>DP ID · {a.dpId}{a.clientId ? ` · Client ${a.clientId}` : ''}</div>}
                <div>{hs.length} holding{hs.length === 1 ? '' : 's'} on this account</div>
              </div>
              <div className="pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
                <div className="text-xs font-semibold mb-1">If something happens, who gets this?</div>
                {editing === a.id ? (
                  <div className="flex gap-2">
                    <input
                      value={nomineeDraft}
                      onChange={e => setNomineeDraft(e.target.value)}
                      className="flex-1 px-3 py-1.5 rounded-lg border text-sm"
                      style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
                      placeholder="Spouse name, or joint"
                    />
                    <button
                      onClick={() => saveNominee(a.id)}
                      className="px-3 py-1.5 rounded-lg text-xs font-bold"
                      style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
                    >
                      Save
                    </button>
                  </div>
                ) : (
                  <button
                    className="text-sm"
                    onClick={() => {
                      setEditing(a.id);
                      setNomineeDraft(a.nominee || '');
                    }}
                  >
                    {a.nominee ? (
                      <span>Nominee · <strong>{a.nominee}</strong> (tap to change)</span>
                    ) : (
                      <span style={{ color: 'var(--accent)' }}>Add a nominee</span>
                    )}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
