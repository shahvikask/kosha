import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { metricsOf, whereHeld, formatCalendarDate, displayHoldingName } from '../../lib/holdingView';
import { X, Printer, ShieldCheck } from 'lucide-react';

interface EmergencySheetModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const EmergencySheetModal: React.FC<EmergencySheetModalProps> = ({ isOpen, onClose }) => {
  const { holdings, familyMembers, accounts, summary, derived, sgbValuationMode, ibjaRupeesPerGram, lastStatementAsOf } =
    useKosha();

  if (!isOpen) return null;

  const prepared = new Date().toLocaleDateString('en-IN', { dateStyle: 'full' });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/70 overflow-y-auto">
      <div
        className="card max-w-4xl w-full p-8 max-h-[92vh] overflow-y-auto space-y-8 print:p-0 print:border-none print:shadow-none"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center justify-between print:hidden">
          <div className="flex items-center gap-2 font-bold text-lg">
            <ShieldCheck className="w-6 h-6" style={{ color: 'var(--accent)' }} />
            Family sheet
          </div>
          <div className="flex items-center gap-2">
            <button
              onClick={() => window.print()}
              className="px-4 py-2 rounded-xl text-sm font-bold flex items-center gap-2"
              style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
            >
              <Printer className="w-4 h-4" /> Print
            </button>
            <button onClick={onClose} className="p-2 rounded-xl">
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        <div className="space-y-8">
          <div className="border-b pb-4" style={{ borderColor: 'var(--border)' }}>
            <h1 className="text-2xl font-black tracking-tight">Family money list</h1>
            <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              GrowUrDhan · Prepared {prepared}
              {lastStatementAsOf ? ` · Statement as of ${formatCalendarDate(lastStatementAsOf)}` : ''}
            </p>
            <p className="mt-4 p-4 rounded-2xl text-xs leading-relaxed" style={{ background: 'var(--input)' }}>
              For the family. This lists every holding and account stored in this vault. Nominee names appear only when they came from a statement — typing a name here does not register it with CDSL, CAMS or KFintech. Passwords are not stored. Blank rows mean the fact was never on a statement.
            </p>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-4 rounded-2xl border" style={{ borderColor: 'var(--border)' }}>
            <Stat label="What the family owns minus loans" value={MoneyEngine.formatRupees(summary.netWorthMinor, false)} />
            <Stat label="You own" value={MoneyEngine.formatRupees(summary.totalAssetsMinor, false)} />
            <Stat label="You owe (Loans & Liabilities)" value={MoneyEngine.formatRupees(summary.totalLiabilitiesMinor, false)} />
            <div>
              <div className="text-[11px] font-semibold uppercase" style={{ color: 'var(--muted)' }}>
                People
              </div>
              <div className="text-xs font-bold mt-1 space-y-0.5">
                {familyMembers.map(m => (
                  <div key={m.id}>
                    {m.name} ({m.relation})
                    {m.panMasked ? ` · PAN ${m.panMasked}` : ''}
                  </div>
                ))}
              </div>
            </div>
          </div>

          <section>
            <h2 className="text-base font-bold border-b pb-2 mb-3" style={{ borderColor: 'var(--border)' }}>
              1. Demat accounts and folios
            </h2>
            {accounts.length === 0 ? (
              <p className="text-xs" style={{ color: 'var(--muted)' }}>
                No demat or folio has been imported yet.
              </p>
            ) : (
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-left" style={{ color: 'var(--muted)' }}>
                    <th className="py-2">Where</th>
                    <th>Type</th>
                    <th>Number</th>
                    <th>Whose name</th>
                    <th>Nominee</th>
                  </tr>
                </thead>
                <tbody>
                  {accounts.map(a => {
                    const owner = familyMembers.find(m => m.id === a.ownerId);
                    return (
                      <tr key={a.id} className="border-t" style={{ borderColor: 'var(--border)' }}>
                        <td className="py-2 font-semibold">{a.custodian}</td>
                        <td>{a.accountType === 'MF_FOLIO' ? 'Mutual fund folio' : 'Demat'}</td>
                        <td className="font-mono">
                          {a.identifierMasked || '—'}
                          {a.dpId ? ` · DP ${a.dpId}` : ''}
                        </td>
                        <td>{owner?.name || '—'}</td>
                        <td>{a.nominee || 'Not on statement'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </section>

          <section>
            <h2 className="text-base font-bold border-b pb-2 mb-3" style={{ borderColor: 'var(--border)' }}>
              2. Holdings
            </h2>
            {holdings.length === 0 ? (
              <p className="text-xs" style={{ color: 'var(--muted)' }}>
                Nothing in the vault yet.
              </p>
            ) : (
              <table className="w-full text-xs">
                <thead>
                  <tr className="text-left" style={{ color: 'var(--muted)' }}>
                    <th className="py-2">Holding</th>
                    <th>Kind</th>
                    <th>Where it sits</th>
                    <th>Whose name</th>
                    <th className="text-right">Approx. value</th>
                  </tr>
                </thead>
                <tbody>
                  {holdings.map(h => {
                    const owner = familyMembers.find(m => m.id === h.ownerId);
                    const val = metricsOf(h, derived, sgbValuationMode, ibjaRupeesPerGram && ibjaRupeesPerGram > 0 ? MoneyEngine.fromRupees(ibjaRupeesPerGram) : null).value;
                    return (
                      <tr key={h.id} className="border-t" style={{ borderColor: 'var(--border)' }}>
                        <td className="py-2">
                          <div className="font-semibold">{displayHoldingName(h.name)}</div>
                          {h.isin && <div style={{ color: 'var(--muted)' }}>{h.isin}</div>}
                        </td>
                        <td>{ASSET_CATEGORIES[h.category].shortName}</td>
                        <td>{whereHeld(h)}</td>
                        <td>{owner?.name || '—'}</td>
                        <td className="text-right font-medium">
                          {MoneyEngine.formatRupees(val, false)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </section>

          <section>
            <h2 className="text-base font-bold border-b pb-2 mb-3" style={{ borderColor: 'var(--border)' }}>
              3. How to claim (generic steps)
            </h2>
            <ol className="list-decimal pl-5 space-y-2 text-xs leading-relaxed">
              <li>
                Mutual funds: write to the registrar (CAMS or KFintech) named on the folio with an attested death certificate and the nominee’s KYC.
              </li>
              <li>
                Demat shares and gold bonds: the depository participant (CDSL / NSDL broker) transmits to the nominee on the account.
              </li>
              <li>
                PPF, EPF and NPS: use the account number on the holding. We only print a UAN if you typed one into the folio field.
              </li>
            </ol>
          </section>
        </div>
      </div>
    </div>
  );
};

const Stat: React.FC<{ label: string; value: string }> = ({ label, value }) => (
  <div>
    <div className="text-[11px] font-semibold uppercase" style={{ color: 'var(--muted)' }}>
      {label}
    </div>
    <div className="text-lg font-bold mt-0.5">{value}</div>
  </div>
);
