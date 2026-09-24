// FILE: src/components/screens/DocumentsScreen.tsx
import React, { useEffect, useMemo, useState } from 'react';
import { useKosha, findMatchingHolding } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import { ErrorBoundary } from '../common/ErrorBoundary';
import { StatementImportRecord } from '../../types';
import { parseStatementFile, CASParseResult } from '../../lib/casParser';
import {
  SnapshotLine,
  ReconciliationResult,
  reconcileStatement,
  snapshotLinesToClose,
} from '../../lib/snapshot';
import { eventLabel, displayHoldingName } from '../../lib/holdingView';
import {
  CasFileType,
  CasImportMeta,
  formatStatementDate,
  pingCasParser,
  npsStatementPassword,
  statementPasswordsToTry,
} from '../../lib/casPython';
import { maybeRunScheduledBackup } from '../../lib/backupSchedule';
import { CasConfirmPanel } from './CasConfirmPanel';
import {
  checkImportPan,
  displayPan,
  panBlocksCommit,
  PanImportCheck,
} from '../../lib/pan';
import {
  UploadCloud,
  CheckCircle2,
  AlertTriangle,
  FileText,
  Printer,
  X,
  Lock,
  Eye,
  EyeOff,
  Users,
  FileCheck2,
} from 'lucide-react';

export const DocumentsScreen: React.FC = () => {
  const {
    familyMembers,
    selectedMemberId,
    holdings,
    ledgerEvents,
    computeTaxSummary,
    importStatementRecords,
    casLatest,
    importBatches,
  } = useKosha();

  const [activeTab, setActiveTab] = useState<'IMPORTER' | 'TAXES' | 'RECORD_BOOK'>('IMPORTER');
  const [selectedFY, setSelectedFY] = useState('2025-26');
  const [targetMemberId, setTargetMemberId] = useState(
    selectedMemberId === 'ALL' ? familyMembers[0]?.id || 'fam_1' : selectedMemberId
  );
  const activeTargetMember = useMemo(
    () => familyMembers.find(m => m.id === targetMemberId) || familyMembers[0],
    [familyMembers, targetMemberId]
  );

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [pdfPassword, setPdfPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [isParsing, setIsParsing] = useState(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [parseResult, setParseResult] = useState<CASParseResult | null>(null);
  const [rawRecords, setRawRecords] = useState<StatementImportRecord[]>([]);
  const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
  const [acceptedHoldingIds, setAcceptedHoldingIds] = useState<Set<string>>(new Set());
  const [importNotification, setImportNotification] = useState<{
    type: 'success' | 'error' | 'info';
    message: string;
  } | null>(null);
  const [pythonReady, setPythonReady] = useState<boolean | null>(null);
  const [isCommitting, setIsCommitting] = useState(false);
  const [recordBookFilterType, setRecordBookFilterType] = useState('ALL');

  const handleTargetMemberChange = (newMemberId: string) => {
    setTargetMemberId(newMemberId);
    const m = familyMembers.find(fm => fm.id === newMemberId);
    setPdfPassword(m?.panForStatements || npsStatementPassword(m?.name, m?.dateOfBirth) || '');
    if (parseResult) {
      buildReconciliation(parseResult, rawRecords, newMemberId);
      setImportNotification(null);
      return;
    }
    setSelectedFile(null);
    setRawRecords([]);
    setReconciliation(null);
    setAcceptedHoldingIds(new Set());
    setParseError(null);
    setImportNotification(null);
  };

  useEffect(() => {
    pingCasParser().then(setPythonReady);
  }, []);

  useEffect(() => {
    if (activeTargetMember?.panForStatements) {
      setPdfPassword(activeTargetMember.panForStatements);
    } else {
      const nps = npsStatementPassword(activeTargetMember?.name, activeTargetMember?.dateOfBirth);
      if (nps) setPdfPassword(nps);
    }
  }, [targetMemberId]);

  useEffect(() => {
    if (selectedMemberId !== 'ALL' && selectedMemberId !== targetMemberId) {
      handleTargetMemberChange(selectedMemberId);
    }
  }, [selectedMemberId]);

  const handleClearFile = () => {
    setSelectedFile(null);
    setParseResult(null);
    setRawRecords([]);
    setReconciliation(null);
    setAcceptedHoldingIds(new Set());
    setParseError(null);
    setImportNotification(null);
    setPdfPassword('');
  };

  const buildReconciliation = (
    result: CASParseResult,
    records: StatementImportRecord[],
    ownerId: string
  ) => {
    const holdingRecords = records.filter(r => r.recordType !== 'TRANSACTION');
    const statementDate =
      result.periodTo || holdingRecords[0]?.date || new Date().toISOString().split('T')[0];
    const snapshotLines: SnapshotLine[] = [];
    const currentWorkingHoldings = [...holdings];
    for (const rec of holdingRecords) {
      const matched = findMatchingHolding(currentWorkingHoldings, rec, ownerId);
      snapshotLines.push({
        holdingId: matched?.id || `tmp_${rec.id}`,
        holdingName: rec.holdingName,
        category: rec.category,
        statementDate: rec.date || statementDate,
        quantityScaled: BigInt(Math.round(rec.quantity * 1e8)),
        unitPriceMinor: BigInt(MoneyEngine.fromRupees(rec.pricePerUnit).toString()),
        cumulativeInvestedMinor: rec.cumulativeInvested
          ? BigInt(Math.round(rec.cumulativeInvested * 1e4))
          : undefined,
        isin: rec.isin,
        folio: rec.folio,
      });
    }
    if (result.engine === 'PYTHON_CASPARSER' && result.fileType) {
      const prevIds = casLatest.byOwner[ownerId]?.[result.fileType as CasFileType]?.holdingIds || [];
      snapshotLines.push(
        ...snapshotLinesToClose(prevIds, snapshotLines, holdings, statementDate, ledgerEvents)
      );
    }
    const recon = reconcileStatement(snapshotLines, ledgerEvents, holdings, ownerId);
    setReconciliation(recon);
    const overwrite = result.engine === 'PYTHON_CASPARSER';
    const initialAccepted = new Set<string>();
    for (const line of recon.lines) {
      if (!overwrite && (line.outcome === 'ADJUST_DOWN' || line.outcome === 'CLOSED')) continue;
      initialAccepted.add(line.line.holdingId);
    }
    setAcceptedHoldingIds(initialAccepted);
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setSelectedFile(file);
    setParseError(null);
    setImportNotification(null);
    setParseResult(null);
    setRawRecords([]);
    setReconciliation(null);
    const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';
    if (!isPdf) {
      processFile(file, [''], false);
      return;
    }
    const passwords = statementPasswordsToTry({
      typed: pdfPassword,
      pan: activeTargetMember?.panForStatements,
      name: activeTargetMember?.name,
      dateOfBirth: activeTargetMember?.dateOfBirth,
    });
    if (passwords.length) processFile(file, passwords, true);
    else {
      setImportNotification({
        type: 'info',
        message: `PDF file selected. Enter the password below — CAS uses the PAN of ${activeTargetMember?.name || 'the primary holder'} in capitals.`,
      });
    }
  };

  const processFile = async (file: File, passwordsToUse: string[], isAutoAttempt = false) => {
    setIsParsing(true);
    setParseError(null);
    setImportNotification(null);
    const memberLedger = ledgerEvents
      .filter(ev => ev.ownerId === targetMemberId)
      .map(ev => ({
        holdingName: ev.holdingName,
        eventDate: ev.eventDate,
        totalAmountMinor: ev.totalAmountMinor,
      }));
    const uniquePasswords = passwordsToUse.length ? passwordsToUse : [''];
    try {
      for (let i = 0; i < uniquePasswords.length; i += 1) {
        try {
          const result = await parseStatementFile(file, uniquePasswords[i], memberLedger, targetMemberId);
          if (result.importRecords.length === 0) {
            setParseError(
              'Python casparser did not find any valued holdings in this statement. Closed / nil lines are skipped. Check that the PDF is a CDSL, NSDL, CAMS, KFintech or NPS statement.'
            );
            return;
          }
          if (uniquePasswords[i] && uniquePasswords[i] !== pdfPassword) setPdfPassword(uniquePasswords[i]);
          setParseResult(result);
          setRawRecords(result.importRecords);
          buildReconciliation(result, result.importRecords, targetMemberId);
          return;
        } catch (err: any) {
          const errString = err?.message || String(err);
          const isPasswordIssue =
            /password|incorrect/i.test(errString) || err?.name === 'PasswordException';
          if (!isPasswordIssue || i === uniquePasswords.length - 1) throw err;
        }
      }
    } catch (err: any) {
      const errString = err?.message || String(err);
      const isPasswordIssue =
        /password|incorrect/i.test(errString) || err?.name === 'PasswordException';
      if (isPasswordIssue) {
        if (isAutoAttempt) {
          setImportNotification({
            type: 'info',
            message: `"${file.name}" is password-protected. CAS uses the PAN in capitals.`,
          });
        } else {
          setParseError(
            "That password did not open the file. CAS statements use the holder's PAN in capitals."
          );
        }
      } else {
        setParseError(`Failed to parse statement: ${errString || 'Unsupported file format.'}`);
      }
    } finally {
      setIsParsing(false);
    }
  };

  const handleDecryptAndParse = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;
    processFile(
      selectedFile,
      statementPasswordsToTry({
        typed: pdfPassword.trim(),
        pan: activeTargetMember?.panForStatements,
        name: activeTargetMember?.name,
        dateOfBirth: activeTargetMember?.dateOfBirth,
      }),
      false
    );
  };

  const handleToggleLine = (holdingId: string) => {
    setAcceptedHoldingIds(prev => {
      const next = new Set(prev);
      if (next.has(holdingId)) next.delete(holdingId);
      else next.add(holdingId);
      return next;
    });
  };

  const formattedStatementDate = useMemo(() => {
    const iso = parseResult?.periodTo || reconciliation?.statementDate;
    return iso ? formatStatementDate(iso) || iso : '';
  }, [parseResult?.periodTo, reconciliation?.statementDate]);

  const panCheck = useMemo(
    () => checkImportPan(activeTargetMember, familyMembers, parseResult?.investorPan),
    [activeTargetMember, familyMembers, parseResult?.investorPan]
  );
  const commitBlockedByPan = !!parseResult && panBlocksCommit(panCheck);

  const handleCommitUpdates = async () => {
    if (!reconciliation || isCommitting) return;
    if (commitBlockedByPan) {
      setImportNotification({
        type: 'error',
        message:
          panCheck.kind === 'SWITCH'
            ? `This statement belongs to ${panCheck.matched.name}. Switch to that profile before updating records.`
            : 'The PAN on this statement does not match the selected family member. Records were not changed.',
      });
      return;
    }
    setIsCommitting(true);
    setImportNotification(null);
    try {
      const recordsToImport = rawRecords.filter(r => {
        if (r.recordType === 'TRANSACTION' && parseResult?.engine === 'PYTHON_CASPARSER') return false;
        if (r.recordType === 'TRANSACTION') return true;
        const matched = findMatchingHolding(holdings, r, targetMemberId);
        return acceptedHoldingIds.has(matched?.id || `tmp_${r.id}`);
      });
      if (recordsToImport.length === 0 && parseResult?.engine === 'PYTHON_CASPARSER') {
        setImportNotification({
          type: 'error',
          message: 'Nothing was ticked to import. Tick the holdings you want, then try again.',
        });
        return;
      }
      const isPython = parseResult?.engine === 'PYTHON_CASPARSER';
      const fileType = parseResult?.fileType as CasFileType | undefined;
      const prevIds = fileType ? casLatest.byOwner[targetMemberId]?.[fileType]?.holdingIds || [] : [];
      const closeHoldingIds = reconciliation.lines
        .filter(
          l =>
            l.outcome === 'CLOSED' &&
            acceptedHoldingIds.has(l.line.holdingId) &&
            prevIds.includes(l.line.holdingId)
        )
        .map(l => l.line.holdingId);
      const casMeta: CasImportMeta | undefined =
        isPython && fileType
          ? {
              fileType,
              filename: parseResult?.filename || selectedFile?.name || 'statement.pdf',
              periodFrom: parseResult?.periodFrom,
              periodTo: parseResult?.periodTo,
              investorName: parseResult?.investorName,
              investorPan: parseResult?.investorPan,
              pdfGrandTotal: parseResult?.pdfTotals?.grand ?? parseResult?.summary.totalValuation ?? 0,
              parsedGrandTotal:
                parseResult?.parsedTotals?.holdingsValue ?? parseResult?.summary.totalValuation ?? 0,
              complete: parseResult?.parseComplete === true,
              closeHoldingIds,
            }
          : undefined;
      const res = await importStatementRecords(
        recordsToImport,
        targetMemberId,
        isPython ? 'REPLACE_SNAPSHOT' : 'SYNC_UPDATE',
        casMeta
      );
      const asOf = parseResult?.periodTo ? formatStatementDate(parseResult.periodTo) : formattedStatementDate;
      setImportNotification({
        type: 'success',
        message: isPython
          ? `Your ${fileType} records now match this statement${asOf ? ` dated ${asOf}` : ''}. ${res.imported} item(s) recorded.`
          : `Your records have been updated for ${activeTargetMember?.name}. ${res.imported} item(s) recorded.`,
      });
      setReconciliation(null);
      setRawRecords([]);
      setParseResult(null);
      setSelectedFile(null);
      setAcceptedHoldingIds(new Set());
      void maybeRunScheduledBackup('cas');
    } catch (err: any) {
      setImportNotification({
        type: 'error',
        message: err?.message || 'Could not save this statement. Nothing new was recorded.',
      });
    } finally {
      setIsCommitting(false);
    }
  };

  const taxSummary = useMemo(() => computeTaxSummary(selectedFY), [selectedFY, computeTaxSummary]);
  const filteredRecordBook = useMemo(
    () =>
      ledgerEvents.filter(ev => {
        const matchesType = recordBookFilterType === 'ALL' || ev.eventType === recordBookFilterType;
        const matchesMember = selectedMemberId === 'ALL' || ev.ownerId === selectedMemberId;
        return matchesType && matchesMember;
      }),
    [ledgerEvents, recordBookFilterType, selectedMemberId]
  );

  const tabBtn = (id: typeof activeTab, label: string) => (
    <button
      key={id}
      onClick={() => setActiveTab(id)}
      className={`px-3 md:px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${
        activeTab === id ? 'bg-amber-400 text-slate-950 font-bold shadow-xs' : 'text-slate-400 hover:text-slate-200'
      }`}
    >
      {label}
    </button>
  );

  return (
    <div className="space-y-4 md:space-y-6 pb-2 md:pb-8">
      <div className="flex flex-col gap-3">
        <div>
          <h1 className="page-title text-2xl font-bold font-serif tracking-tight">Documents</h1>
          <p className="page-lead text-xs sm:text-sm" style={{ color: 'var(--muted)' }}>
            Encrypted statement reader, Indian capital gains computation & immutable records
          </p>
        </div>
        <div
          className="flex items-center overflow-x-auto p-1 rounded-2xl border text-xs sm:text-sm font-semibold"
          style={{ background: 'var(--input)', borderColor: 'var(--border)' }}
        >
          {tabBtn('IMPORTER', 'Statement')}
          {tabBtn('TAXES', 'Tax')}
          {tabBtn('RECORD_BOOK', 'Records')}
        </div>
      </div>

      {activeTab === 'IMPORTER' && (
        <ErrorBoundary>
          <div className="space-y-4 md:space-y-6">
            <div className="card p-4 md:p-7 space-y-4 md:space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800">
                <div>
                  <label className="text-xs font-bold text-slate-300 uppercase tracking-wider block mb-1 flex items-center gap-1.5">
                    <Users className="w-4 h-4 text-sky-400" />
                    <span>1. Select Family Member or Entity</span>
                  </label>
                </div>
                <select
                  value={targetMemberId}
                  onChange={e => handleTargetMemberChange(e.target.value)}
                  className="px-3 md:px-4 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-bold text-slate-100 cursor-pointer w-full sm:min-w-[220px]"
                >
                  {familyMembers.map(member => (
                    <option key={member.id} value={member.id}>
                      {member.name} ({member.relation})
                    </option>
                  ))}
                </select>
              </div>

              <div className="border border-dashed rounded-2xl p-4 md:p-8 text-center space-y-3 border-slate-700 bg-slate-950/50">
                <div className="w-12 h-12 rounded-2xl bg-amber-400/10 border border-amber-400/30 text-amber-400 flex items-center justify-center mx-auto">
                  <UploadCloud className="w-6 h-6" />
                </div>
                <div className="max-w-lg mx-auto space-y-1">
                  <h3 className="text-base font-bold text-slate-100">
                    Drop Encrypted CAS or NPS PDF, or Statement CSV
                  </h3>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Supports password-protected <strong>CDSL, NSDL, CAMS, KFintech and NPS</strong> PDFs
                    via Python casparser. Same issuer overwrites the previous file.
                  </p>
                  {pythonReady === false && (
                    <p className="text-xs text-amber-300 pt-2">Engine is not running.</p>
                  )}
                  {pythonReady === true && (
                    <p className="text-[11px] text-emerald-400/90 pt-1">Engine is running.</p>
                  )}
                </div>
                <label className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs sm:text-sm font-bold rounded-xl cursor-pointer inline-flex items-center gap-2">
                  <FileText className="w-4 h-4 stroke-[2.5]" />
                  <span>Choose Statement File</span>
                  <input
                    type="file"
                    accept=".pdf,.csv,.txt"
                    className="hidden"
                    onClick={e => {
                      (e.target as HTMLInputElement).value = '';
                    }}
                    onChange={handleFileChange}
                  />
                </label>
                {selectedFile && (
                  <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-slate-800/90 border border-slate-700 text-xs text-slate-200 font-mono">
                    <FileCheck2 className="w-3.5 h-3.5 text-emerald-400" />
                    <span>{selectedFile.name}</span>
                    <button type="button" onClick={handleClearFile} className="ml-1.5 text-slate-400">
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                )}
              </div>

              {selectedFile && selectedFile.name.toLowerCase().endsWith('.pdf') && (
                <form
                  onSubmit={handleDecryptAndParse}
                  className="p-4 sm:p-5 rounded-2xl bg-slate-950 border border-amber-500/40 space-y-3"
                >
                  <div className="flex items-center gap-2">
                    <Lock className="w-4 h-4 text-amber-400" />
                    <span className="text-xs sm:text-sm font-bold text-slate-200">PDF Password (PAN or NPS)</span>
                  </div>
                  <div className="flex flex-col sm:flex-row gap-3">
                    <div className="relative flex-1">
                      <input
                        type={showPassword ? 'text' : 'password'}
                        value={pdfPassword}
                        onChange={e => setPdfPassword(e.target.value)}
                        className="w-full px-3.5 py-2.5 pr-10 bg-slate-900 border border-slate-700 rounded-xl text-sm font-mono text-slate-100"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword(!showPassword)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400"
                      >
                        {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                      </button>
                    </div>
                    <button
                      type="submit"
                      disabled={isParsing}
                      className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 disabled:opacity-50 text-slate-950 text-xs sm:text-sm font-bold rounded-xl flex items-center justify-center gap-2"
                    >
                      {isParsing ? 'Reading with Python casparser...' : 'Read Statement'}
                    </button>
                  </div>
                </form>
              )}

              {parseError && (
                <div className="p-4 rounded-2xl bg-rose-950/90 border border-rose-800 text-rose-200 text-xs sm:text-sm flex items-start gap-3">
                  <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
                  <div>
                    <div className="font-bold">Reading Error</div>
                    <div className="text-xs text-rose-300 leading-relaxed">{parseError}</div>
                  </div>
                </div>
              )}

              {importNotification && (
                <div
                  className={`p-4 rounded-2xl border text-xs sm:text-sm flex items-center justify-between ${
                    importNotification.type === 'success'
                      ? 'bg-emerald-950/80 border-emerald-800 text-emerald-200'
                      : importNotification.type === 'error'
                        ? 'bg-rose-950/80 border-rose-800 text-rose-200'
                        : 'bg-sky-950/80 border-sky-800 text-sky-200'
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
                    <span>{importNotification.message}</span>
                  </div>
                  <button onClick={() => setImportNotification(null)}>
                    <X className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>

            {parseResult && (
              <PanOwnerBanner
                check={panCheck}
                selectedName={activeTargetMember?.name || 'this member'}
                investorName={parseResult.investorName}
                printedPan={parseResult.investorPan}
                onSwitch={id => handleTargetMemberChange(id)}
              />
            )}

            {parseResult?.engine === 'PYTHON_CASPARSER' && (
              <CasConfirmPanel
                result={parseResult}
                previous={
                  parseResult.fileType
                    ? casLatest.byOwner[targetMemberId]?.[parseResult.fileType as CasFileType] || null
                    : null
                }
              />
            )}

            {reconciliation && (
              <div className="card overflow-hidden space-y-4 p-4 md:p-7">
                <h3 className="text-lg font-bold font-serif text-slate-100">
                  We read your statement dated {formattedStatementDate}
                </h3>
                <p className="text-xs text-slate-400">
                  This snapshot replaces the previous file of the same issuer. Only the difference is recorded.
                </p>
                <div className="space-y-2">
                  {reconciliation.lines.map(l => (
                    <label
                      key={l.line.holdingId}
                      className="flex items-start gap-2.5 py-2 text-xs border-b border-slate-800"
                    >
                      <input
                        type="checkbox"
                        checked={acceptedHoldingIds.has(l.line.holdingId)}
                        onChange={() => handleToggleLine(l.line.holdingId)}
                        className="mt-0.5 accent-amber-400"
                      />
                      <div className="min-w-0">
                        <div className="font-bold text-slate-100">
                          {l.outcome} · {displayHoldingName(l.line.holdingName)}
                        </div>
                        <div className="text-slate-400">{l.message}</div>
                      </div>
                    </label>
                  ))}
                </div>
                <button
                  onClick={handleCommitUpdates}
                  disabled={isCommitting || commitBlockedByPan}
                  className="px-6 py-2.5 bg-emerald-500 hover:bg-emerald-400 disabled:opacity-50 text-slate-950 text-xs sm:text-sm font-bold rounded-xl"
                >
                  {isCommitting ? 'Saving…' : 'Update my records'}
                </button>
              </div>
            )}

            {importBatches.length > 0 && (
              <div className="card p-6 space-y-3">
                <h3 className="text-base font-bold">Statements you have confirmed</h3>
                {importBatches.slice(0, 6).map(b => (
                  <div key={b.id} className="py-2 text-sm border-b border-slate-800">
                    <div className="font-semibold">{b.filename}</div>
                    <div className="text-xs text-slate-400">
                      {b.statementType || b.kind}
                      {b.periodTo ? ` · until ${formatStatementDate(b.periodTo)}` : ''}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </ErrorBoundary>
      )}

      {activeTab === 'TAXES' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3 bg-slate-900/80 p-4 rounded-3xl border border-slate-800">
            <select
              value={selectedFY}
              onChange={e => setSelectedFY(e.target.value)}
              className="px-3.5 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-semibold text-slate-200"
            >
              <option value="2025-26">April 2025 – March 2026</option>
              <option value="2024-25">April 2024 – March 2025</option>
            </select>
            <button
              onClick={() => window.print()}
              className="px-3.5 py-1.5 bg-amber-400 text-slate-950 rounded-xl text-xs font-bold flex items-center gap-1.5"
            >
              <Printer className="w-3.5 h-3.5" />
              Print Tax Dossier
            </button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 space-y-2">
              <div className="text-xs text-slate-400 font-semibold">Long-term share gains</div>
              <PrivacyValue minor={taxSummary.ltcgEquityTotalMinor} className="text-xl font-bold text-slate-100" showCompact />
            </div>
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 space-y-2">
              <div className="text-xs text-slate-400 font-semibold">Short-term share gains</div>
              <PrivacyValue minor={taxSummary.stcgEquityTotalMinor} className="text-xl font-bold text-slate-100" showCompact />
            </div>
          </div>
        </div>
      )}

      {activeTab === 'RECORD_BOOK' && (
        <div className="card overflow-hidden space-y-3 p-4 md:p-6">
          <div className="flex items-center justify-between gap-3">
            <h3 className="text-base font-bold">Record Book</h3>
            <select
              value={recordBookFilterType}
              onChange={e => setRecordBookFilterType(e.target.value)}
              className="px-3 py-2 rounded-xl text-xs font-semibold border"
              style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
            >
              <option value="ALL">All rows</option>
              <option value="BUY">Bought</option>
              <option value="SELL">Sold</option>
              <option value="SNAPSHOT_ADJUSTMENT">Updated to match statement</option>
            </select>
          </div>
          <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
            {filteredRecordBook.map(ev => (
              <div key={ev.id} className="py-2.5 flex items-start gap-2.5">
                <div className="min-w-0 flex-1">
                  <div className="text-sm font-semibold">{displayHoldingName(ev.holdingName)}</div>
                  <div className="text-[11px]" style={{ color: 'var(--muted)' }}>
                    {eventLabel(ev.eventType, ev.notes)} · {ev.eventDate}
                  </div>
                </div>
                <PrivacyValue
                  minor={BigInt(ev.totalAmountMinor || '0')}
                  className="text-sm font-bold"
                  includePaise={false}
                />
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

const PanOwnerBanner: React.FC<{
  check: PanImportCheck;
  selectedName: string;
  investorName?: string;
  printedPan?: string;
  onSwitch: (memberId: string) => void;
}> = ({ check, selectedName, investorName, printedPan, onSwitch }) => {
  const pan = displayPan(printedPan);
  const who = investorName ? `${investorName} · ${pan}` : pan;
  if (check.kind === 'MATCH') return null;
  if (check.kind === 'SWITCH') {
    return (
      <div className="p-4 rounded-2xl border bg-rose-950/50 border-rose-700/60 text-rose-100 space-y-3">
        <div className="font-bold">This statement is not {selectedName}'s</div>
        <p className="text-xs">
          The PDF is for {who}. That PAN belongs to {check.matched.name}.
        </p>
        <button
          type="button"
          onClick={() => onSwitch(check.matched.id)}
          className="px-4 py-2 rounded-xl text-xs font-bold bg-amber-400 text-slate-950"
        >
          Switch to {check.matched.name} and review
        </button>
      </div>
    );
  }
  return (
    <div className="p-4 rounded-2xl border bg-amber-950/40 border-amber-500/40 text-amber-100 text-sm">
      Confirm this file really belongs to {selectedName} before you update records.
    </div>
  );
};
