import React, { useMemo, useState } from 'react';
import { CASParseResult } from '../../lib/casParser';
import {
  CasFileType,
  CasImportMeta,
  CAS_FILE_TYPE_LABEL,
  formatInrAmount,
  formatStatementDate,
} from '../../lib/casPython';
import { ASSET_CATEGORIES } from '../../types';
import { displayHoldingName } from '../../lib/holdingView';
import {
  AlertTriangle,
  BadgeCheck,
  Building2,
  CalendarRange,
  ChevronDown,
  ChevronUp,
  FileText,
  Landmark,
  ShieldCheck,
  User,
} from 'lucide-react';

interface CasConfirmPanelProps {
  result: CASParseResult;
  previous?: CasImportMeta | null;
}

export const CasConfirmPanel: React.FC<CasConfirmPanelProps> = ({ result, previous }) => {
  const [showHoldings, setShowHoldings] = useState(true);
  const fileType = (result.fileType || 'CDSL') as CasFileType;
  const pdfGrand = result.pdfTotals?.grand ?? result.summary.totalValuation;
  const parsedGrand = result.parsedTotals?.holdingsValue ?? result.summary.totalValuation;
  const complete = result.parseComplete === true;
  const delta = result.totalsDelta ?? parsedGrand - pdfGrand;
  const holdings = useMemo(
    () =>
      [...result.importRecords]
        .filter(r => r.recordType !== 'TRANSACTION')
        .sort((a, b) => b.totalAmount - a.totalAmount),
    [result.importRecords]
  );

  return (
    <div className="card overflow-hidden space-y-4 p-4 md:p-7">
      <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3 pb-4 border-b border-slate-800">
        <div className="space-y-1">
          <div className="flex items-center gap-2 text-amber-300 text-xs font-bold uppercase tracking-wider">
            <FileText className="w-4 h-4" />
            Confirm parsed statement
          </div>
          <h3 className="text-lg font-bold font-serif text-slate-100">
            {CAS_FILE_TYPE_LABEL[fileType] || fileType}
          </h3>
          <p className="text-xs text-slate-400">
            Python casparser detected this file automatically. Review the totals
            and every holding before updating your records.
          </p>
        </div>
        {complete ? (
          <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-emerald-950/70 border border-emerald-700/60 text-emerald-300 text-xs font-bold">
            <BadgeCheck className="w-4 h-4" />
            100% parsed
          </div>
        ) : (
          <div className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-950/70 border border-amber-700/60 text-amber-300 text-xs font-bold">
            <AlertTriangle className="w-4 h-4" />
            Review totals
          </div>
        )}
      </div>

      <div className="md:hidden rounded-xl border overflow-hidden" style={{ borderColor: 'var(--border)' }}>
        {[
          { label: 'Printed on PDF', value: formatInrAmount(pdfGrand), tone: undefined as string | undefined },
          { label: 'Parsed from PDF', value: formatInrAmount(parsedGrand), tone: undefined },
          {
            label: 'Difference',
            value: delta === 0 ? '₹ 0.00' : formatInrAmount(delta),
            tone: complete ? 'var(--good)' : 'var(--warn)',
          },
        ].map(row => (
          <div
            key={row.label}
            className="flex items-center justify-between gap-3 px-3 py-2.5 border-b last:border-b-0"
            style={{ borderColor: 'var(--border)', background: 'var(--input)' }}
          >
            <div className="text-[11px] font-semibold uppercase tracking-wide shrink-0" style={{ color: 'var(--muted)' }}>
              {row.label}
            </div>
            <div
              className="text-sm font-bold font-mono tabular-nums whitespace-nowrap text-right"
              style={{ color: row.tone || 'var(--text)' }}
            >
              {row.value}
            </div>
          </div>
        ))}
      </div>

      <div className="hidden md:grid grid-cols-3 gap-3">
        <div className="rounded-2xl bg-slate-950/70 border border-slate-800 p-4 space-y-1 min-w-0">
          <div className="text-[11px] uppercase tracking-wider text-slate-500 font-semibold">
            Printed on PDF
          </div>
          <div className="text-xl font-bold text-slate-100 font-mono tabular-nums whitespace-nowrap">
            {formatInrAmount(pdfGrand)}
          </div>
          <div className="text-[11px] text-slate-500">Grand total from the statement</div>
        </div>
        <div className="rounded-2xl bg-slate-950/70 border border-slate-800 p-4 space-y-1 min-w-0">
          <div className="text-[11px] uppercase tracking-wider text-slate-500 font-semibold">
            Parsed from PDF
          </div>
          <div className="text-xl font-bold text-slate-100 font-mono tabular-nums whitespace-nowrap">
            {formatInrAmount(parsedGrand)}
          </div>
          <div className="text-[11px] text-slate-500">
            {result.parsedTotals?.holdingsCount ?? holdings.length} holding
            {(result.parsedTotals?.holdingsCount ?? holdings.length) === 1 ? '' : 's'}
          </div>
        </div>
        <div
          className={`rounded-2xl border p-4 space-y-1 min-w-0 ${
            complete
              ? 'bg-emerald-950/30 border-emerald-800/50'
              : 'bg-amber-950/30 border-amber-800/50'
          }`}
        >
          <div className="text-[11px] uppercase tracking-wider font-semibold text-slate-400">
            Difference
          </div>
          <div
            className={`text-xl font-bold font-mono tabular-nums whitespace-nowrap ${
              complete ? 'text-emerald-300' : 'text-amber-300'
            }`}
          >
            {delta === 0 ? '₹ 0.00' : formatInrAmount(delta)}
          </div>
          <div className={`text-[11px] ${complete ? 'text-emerald-400/80' : 'text-amber-300/90'}`}>
            {complete
              ? 'Parsed value matches the PDF total'
              : 'Parsed value does not match the PDF total'}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 text-xs">
        <MetaChip
          icon={<CalendarRange className="w-3.5 h-3.5 text-sky-400" />}
          label="Statement period"
          value={
            result.periodFrom || result.periodTo
              ? `${formatStatementDate(result.periodFrom) || '—'} → ${
                  formatStatementDate(result.periodTo) || '—'
                }`
              : 'Not printed'
          }
        />
        <MetaChip
          icon={<User className="w-3.5 h-3.5 text-amber-400" />}
          label="Investor"
          value={result.investorName || 'Not printed'}
        />
        <MetaChip
          icon={<ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />}
          label="PAN"
          value={result.investorPan || 'Not printed'}
        />
        <MetaChip
          icon={<FileText className="w-3.5 h-3.5 text-slate-400" />}
          label="File"
          value={result.filename || 'statement.pdf'}
        />
      </div>

      {(result.summary.dematValuation ||
        result.summary.mfValuation ||
        result.summary.aifValuation) && (
        <div className="flex flex-wrap gap-2 text-[11px]">
          {!!result.summary.dematValuation && (
            <span className="px-2.5 py-1 rounded-lg bg-sky-950/50 border border-sky-800/40 text-sky-200">
              Demat {formatInrAmount(result.summary.dematValuation)}
            </span>
          )}
          {!!result.summary.mfValuation && (
            <span className="px-2.5 py-1 rounded-lg bg-teal-950/50 border border-teal-800/40 text-teal-200">
              Mutual funds {formatInrAmount(result.summary.mfValuation)}
            </span>
          )}
          {!!result.summary.aifValuation && (
            <span className="px-2.5 py-1 rounded-lg bg-indigo-950/50 border border-indigo-800/40 text-indigo-200">
              AIF / specialised {formatInrAmount(result.summary.aifValuation)}
            </span>
          )}
        </div>
      )}

      {previous && (
        <div className="p-4 rounded-2xl bg-amber-950/40 border border-amber-500/40 text-amber-100 text-xs leading-relaxed">
          <div className="font-bold text-amber-300 mb-1">This file replaces the previous {fileType} statement</div>
          Totals are <strong>not added</strong>. The last {fileType} import
          {previous.periodTo ? ` (up to ${formatStatementDate(previous.periodTo)})` : ''}{' '}
          will be overwritten by this snapshot
          {result.periodTo ? ` dated ${formatStatementDate(result.periodTo)}` : ''}.
          Holdings that disappeared from this file will be marked closed.
        </div>
      )}

      {result.accounts && result.accounts.length > 0 && (
        <div className="space-y-2">
          <div className="text-[11px] uppercase tracking-wider text-slate-500 font-semibold flex items-center gap-1.5">
            <Landmark className="w-3.5 h-3.5" />
            Accounts on this statement
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            {result.accounts.map((acct, idx) => (
              <div
                key={`${acct.dpId || acct.name}-${idx}`}
                className="rounded-xl bg-slate-950/60 border border-slate-800 px-3 py-2.5 text-xs"
              >
                <div className="font-semibold text-slate-200 flex items-center gap-1.5">
                  <Building2 className="w-3.5 h-3.5 text-slate-500" />
                  {acct.name || acct.type || 'Account'}
                </div>
                <div className="text-slate-400 mt-0.5 font-mono">
                  {[acct.type, acct.dpId && `DP ${acct.dpId}`, acct.clientId && `Client ${acct.clientId}`, acct.folios ? `${acct.folios} folios` : null]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
                <div className="text-slate-200 font-mono mt-1">{formatInrAmount(acct.balance || 0)}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {result.warnings.length > 0 && (
        <div className="p-3 rounded-xl bg-slate-950 border border-slate-800 text-[11px] text-slate-400 space-y-1">
          {result.warnings.map((w, i) => (
            <div key={i}>• {w}</div>
          ))}
        </div>
      )}

      <div className="rounded-2xl border border-slate-800 overflow-hidden">
        <button
          type="button"
          onClick={() => setShowHoldings(v => !v)}
          className="w-full px-4 py-3 bg-slate-950/80 flex items-center justify-between text-xs font-semibold text-slate-200 cursor-pointer"
        >
          <span>
            Holdings to confirm ({holdings.length})
          </span>
          {showHoldings ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
        </button>
        {showHoldings && (
          <>
            <div className="md:hidden divide-y max-h-[420px] overflow-y-auto" style={{ borderColor: 'var(--border)' }}>
              {holdings.map(h => (
                <div key={h.id} className="px-3 py-2.5 flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-sm font-semibold leading-snug">{displayHoldingName(h.holdingName)}</div>
                    <div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>
                      {[ASSET_CATEGORIES[h.category]?.shortName || h.category, h.folio, h.quantity.toLocaleString('en-IN', { maximumFractionDigits: 3 }) + ' u']
                        .filter(Boolean)
                        .join(' · ')}
                    </div>
                  </div>
                  <div className="text-sm font-bold font-mono tabular-nums shrink-0">
                    {formatInrAmount(h.totalAmount)}
                  </div>
                </div>
              ))}
            </div>
            <div className="hidden md:block overflow-x-auto max-h-[420px] overflow-y-auto">
            <table className="w-full text-left text-xs">
              <thead className="sticky top-0 bg-slate-950 text-slate-500 uppercase tracking-wider text-[10px]">
                <tr>
                  <th className="px-3 py-2 font-semibold">Holding</th>
                  <th className="px-3 py-2 font-semibold">ISIN / Folio</th>
                  <th className="px-3 py-2 font-semibold">Type</th>
                  <th className="px-3 py-2 font-semibold text-right">Qty</th>
                  <th className="px-3 py-2 font-semibold text-right">Price</th>
                  <th className="px-3 py-2 font-semibold text-right">Value</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/80">
                {holdings.map(h => (
                  <tr key={h.id} className="bg-slate-950/40">
                    <td className="px-3 py-2.5 text-slate-100 font-medium max-w-[280px]">
                      {displayHoldingName(h.holdingName)}
                    </td>
                    <td className="px-3 py-2.5 font-mono text-slate-400 whitespace-nowrap">
                      <div>{h.isin || '—'}</div>
                      {h.folio && <div className="text-[10px] text-slate-500">{h.folio}</div>}
                    </td>
                    <td className="px-3 py-2.5 text-slate-400 whitespace-nowrap">
                      {ASSET_CATEGORIES[h.category]?.shortName || h.category}
                      {h.schemeType ? ` · ${h.schemeType}` : ''}
                    </td>
                    <td className="px-3 py-2.5 text-right font-mono text-slate-200">
                      {h.quantity.toLocaleString('en-IN', { maximumFractionDigits: 3 })}
                    </td>
                    <td className="px-3 py-2.5 text-right font-mono text-slate-300">
                      {formatInrAmount(h.pricePerUnit)}
                    </td>
                    <td className="px-3 py-2.5 text-right font-mono font-semibold text-slate-100">
                      {formatInrAmount(h.totalAmount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            </div>
          </>
        )}
      </div>
    </div>
  );
};

const MetaChip: React.FC<{ icon: React.ReactNode; label: string; value: string }> = ({
  icon,
  label,
  value,
}) => (
  <div className="rounded-xl bg-slate-950/60 border border-slate-800 px-3 py-2.5">
    <div className="flex items-center gap-1.5 text-[10px] uppercase tracking-wider text-slate-500 font-semibold mb-0.5">
      {icon}
      {label}
    </div>
    <div className="text-slate-200 font-medium truncate" title={value}>
      {value}
    </div>
  </div>
);
