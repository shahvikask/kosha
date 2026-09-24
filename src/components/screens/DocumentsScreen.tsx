// FILE: src/components/screens/DocumentsScreen.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { useKosha, findMatchingHolding } from '../../context/KoshaContext';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import { ErrorBoundary } from '../common/ErrorBoundary';
import { StatementImportRecord } from '../../types';
import { parseStatementFile, CASParseResult } from '../../lib/casParser';
import {
  SnapshotLine,
  ReconciliationResult,
  reconcileStatement,
  SnapshotOutcome,
  snapshotLinesToClose,
} from '../../lib/snapshot';
import { eventLabel, displayHoldingName } from '../../lib/holdingView';
import {
  CasFileType,
  CasImportMeta,
  formatStatementDate,
  readCasHealth,
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
  RefreshCw,
  FileCheck2,
  ChevronDown,
  ChevronUp,
} from 'lucide-react';

export const DocumentsScreen: React.FC = () => {
  const {
    familyMembers,
    selectedMemberId,
    holdings,
    ledgerEvents,
    computeTaxSummary,
    importStatementRecords,
    cleanDuplicatesAndHeal,
    derived,
    casLatest,
    importBatches,
  } = useKosha();

  const [activeTab, setActiveTab] = useState<'IMPORTER' | 'TAXES' | 'RECORD_BOOK'>('IMPORTER');
  const [selectedFY, setSelectedFY] = useState<string>('2025-26');
  const [targetMemberId, setTargetMemberId] = useState<string>(
    selectedMemberId === 'ALL' ? (familyMembers[0]?.id || 'fam_1') : selectedMemberId
  );
  const activeTargetMember = useMemo(() => {
    return familyMembers.find(m => m.id === targetMemberId) || familyMembers[0];
  }, [familyMembers, targetMemberId]);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [pdfPassword, setPdfPassword] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [isParsing, setIsParsing] = useState<boolean>(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const [parseResult, setParseResult] = useState<CASParseResult | null>(null);
  const [rawRecords, setRawRecords] = useState<StatementImportRecord[]>([]);
  const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
  const [acceptedHoldingIds, setAcceptedHoldingIds] = useState<Set<string>>(new Set());
  const [showOutcomeGroup, setShowOutcomeGroup] = useState<Record<SnapshotOutcome, boolean>>({
    MATCH: false,
    NEW_POSITION: true,
    ADJUST_UP: true,
    ADJUST_DOWN: true,
    CLOSED: true,
    COST_UPDATE: true,
    CONFLICT: true,
  });
  const [importNotification, setImportNotification] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);
  const [pythonReady, setPythonReady] = useState<boolean | null>(null);
  const [engineDetail, setEngineDetail] = useState<string>('');
  const [isCommitting, setIsCommitting] = useState(false);
  const [recordBookSearch, setRecordBookSearch] = useState<string>('');
  const [recordBookFilterType, setRecordBookFilterType] = useState<string>('ALL');
  const [showOlderStatements, setShowOlderStatements] = useState(false);

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
    let stop = false;
    let n = 0;
    const tick = () => {
      if (stop) return;
      readCasHealth().then(h => {
        if (stop) return;
        setPythonReady(h.ok);
        const err = [h.error, h.logError ? `log write: ${h.logError}` : '']
          .filter(Boolean)
          .join(' — ')
          .replace(/\s+/g, ' ')
          .slice(0, 400);
        setEngineDetail(h.ok ? '' : err);
        n += 1;
        if (!h.ok && n < 6) setTimeout(tick, 900);
      });
    };
    tick();
    return () => { stop = true; };
  }, []);

  useEffect(() => {
    if (activeTargetMember?.panForStatements) {
      setPdfPassword(activeTargetMember.panForStatements);
    } else {
      const nps = npsStatementPassword(activeTargetMember?.name, activeTargetMember?.dateOfBirth);
      if (nps) setPdfPassword(nps);
    }
  }, [targetMemberId]);

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

  useEffect(() => {
    if (selectedMemberId !== 'ALL' && selectedMemberId !== targetMemberId) {
      handleTargetMemberChange(selectedMemberId);
    }
  }, [selectedMemberId]);

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
      snapshotLines.push(...snapshotLinesToClose(prevIds, snapshotLines, holdings, statementDate, ledgerEvents));
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
      .map(ev => ({ holdingName: ev.holdingName, eventDate: ev.eventDate, totalAmountMinor: ev.totalAmountMinor }));
    const uniquePasswords = passwordsToUse.length ? passwordsToUse : [''];
    try {
      for (let i = 0; i < uniquePasswords.length; i += 1) {
        try {
          const result = await parseStatementFile(file, uniquePasswords[i], memberLedger, targetMemberId);
          if (result.importRecords.length === 0) {
            setParseError('Python casparser did not find any valued holdings in this statement. Closed / nil lines are skipped. Check that the PDF is a CDSL, NSDL, CAMS, KFintech or NPS statement.');
            return;
          }
          if (uniquePasswords[i] && uniquePasswords[i] !== pdfPassword) setPdfPassword(uniquePasswords[i]);
          setParseResult(result);
          setRawRecords(result.importRecords);
          buildReconciliation(result, result.importRecords, targetMemberId);
          return;
        } catch (err: any) {
          const errString = err?.message || String(err);
          const isPasswordIssue = /password|incorrect/i.test(errString) || err?.name === 'PasswordException';
          if (!isPasswordIssue || i === uniquePasswords.length - 1) throw err;
        }
      }
    } catch (err: any) {
      const errString = err?.message || String(err);
      const isPasswordIssue = /password|incorrect/i.test(errString) || err?.name === 'PasswordException';
      if (isPasswordIssue) {
        if (isAutoAttempt) {
          setImportNotification({ type: 'info', message: `"${file.name}" is password-protected. CAS uses the PAN in capitals.` });
        } else {
          setParseError("That password did not open the file. CAS statements use the holder's PAN in capitals.");
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
    processFile(selectedFile, statementPasswordsToTry({
      typed: pdfPassword.trim(),
      pan: activeTargetMember?.panForStatements,
      name: activeTargetMember?.name,
      dateOfBirth: activeTargetMember?.dateOfBirth,
    }), false);
  };

  const handleToggleLine = (holdingId: string) => {
    setAcceptedHoldingIds(prev => {
      const next = new Set(prev);
      if (next.has(holdingId)) next.delete(holdingId);
      else next.add(holdingId);
      return next;
    });
  };

  const netAcceptedChangeMinor = useMemo(() => {
    if (!reconciliation) return 0n;
    let net = 0n;
    for (const line of reconciliation.lines) {
      if (line.suggestedEvent && acceptedHoldingIds.has(line.line.holdingId)) {
        net += (BigInt(line.deltaQtyScaled) * BigInt(line.priceMinor)) / 100_000_000n;
      }
    }
    return net;
  }, [reconciliation, acceptedHoldingIds]);

  const areAllMatches = useMemo(() => {
    if (!reconciliation || reconciliation.lines.length === 0) return false;
    return reconciliation.lines.every(l => l.outcome === 'MATCH');
  }, [reconciliation]);

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
        message: panCheck.kind === 'SWITCH'
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
        setImportNotification({ type: 'error', message: 'Nothing was ticked to import. Tick the holdings you want, then try again.' });
        return;
      }
      const isPython = parseResult?.engine === 'PYTHON_CASPARSER';
      const fileType = parseResult?.fileType as CasFileType | undefined;
      const prevIds = fileType ? casLatest.byOwner[targetMemberId]?.[fileType]?.holdingIds || [] : [];
      const closeHoldingIds = reconciliation.lines
        .filter(l => l.outcome === 'CLOSED' && acceptedHoldingIds.has(l.line.holdingId) && prevIds.includes(l.line.holdingId))
        .map(l => l.line.holdingId);
      const casMeta: CasImportMeta | undefined = isPython && fileType ? {
        fileType,
        filename: parseResult?.filename || selectedFile?.name || 'statement.pdf',
        periodFrom: parseResult?.periodFrom,
        periodTo: parseResult?.periodTo,
        investorName: parseResult?.investorName,
        investorPan: parseResult?.investorPan,
        pdfGrandTotal: parseResult?.pdfTotals?.grand ?? parseResult?.summary.totalValuation ?? 0,
        parsedGrandTotal: parseResult?.parsedTotals?.holdingsValue ?? parseResult?.summary.totalValuation ?? 0,
        complete: parseResult?.parseComplete === true,
        closeHoldingIds,
      } : undefined;
      const res = await importStatementRecords(recordsToImport, targetMemberId, isPython ? 'REPLACE_SNAPSHOT' : 'SYNC_UPDATE', casMeta);
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
      setImportNotification({ type: 'error', message: err?.message || 'Could not save this statement. Nothing new was recorded.' });
    } finally {
      setIsCommitting(false);
    }
  };

  const handleCleanDuplicates = async () => {
    const { mergedCount, flaggedCount } = await cleanDuplicatesAndHeal();
    setImportNotification({ type: 'success', message: `Health Check: Re-merged ${mergedCount} duplicate items and flagged ${flaggedCount} items for review across all accounts.` });
  };

  const taxSummary = useMemo(() => computeTaxSummary(selectedFY), [selectedFY, computeTaxSummary]);
  const matchErrors = useMemo(() => {
    const noisy = /Units were removed on .+ to match a statement|NOT counted in your capital gains/i;
    const list: string[] = [];
    if (derived?.errors) list.push(...derived.errors.filter(e => !noisy.test(e)));
    if (taxSummary?.errors) list.push(...taxSummary.errors.filter(e => !noisy.test(e)));
    return Array.from(new Set(list));
  }, [derived?.errors, taxSummary?.errors]);

  const filteredRecordBook = useMemo(() => ledgerEvents.filter(ev => {
    const matchesSearch = recordBookSearch === '' || ev.holdingName.toLowerCase().includes(recordBookSearch.toLowerCase()) || (ev.notes && ev.notes.toLowerCase().includes(recordBookSearch.toLowerCase()));
    const matchesType = recordBookFilterType === 'ALL' || ev.eventType === recordBookFilterType;
    const matchesMember = selectedMemberId === 'ALL' || ev.ownerId === selectedMemberId;
    return matchesSearch && matchesType && matchesMember;
  }), [ledgerEvents, recordBookSearch, recordBookFilterType, selectedMemberId]);

  const visibleBatches = showOlderStatements || importBatches.length <= 2 ? importBatches : importBatches.slice(0, 2);
  const hiddenBatchCount = importBatches.length - visibleBatches.length;
  const formattedStatementDate = useMemo(() => {
    const iso = parseResult?.periodTo || reconciliation?.statementDate;
    return iso ? formatStatementDate(iso) || iso : '';
  }, [parseResult?.periodTo, reconciliation?.statementDate]);

  const tabBtn = (id: typeof activeTab, label: string) => (
    <button key={id} onClick={() => setActiveTab(id)} className={`px-3 md:px-4 py-2 rounded-xl transition-all cursor-pointer whitespace-nowrap ${activeTab === id ? 'bg-amber-400 text-slate-950 font-bold shadow-xs' : 'text-slate-400 hover:text-slate-200'}`}>{label}</button>
  );

  return (
    <div className="space-y-4 md:space-y-6 pb-2 md:pb-8">
      <div className="flex flex-col gap-3">
        <div>
          <h1 className="page-title text-2xl font-bold font-serif tracking-tight">Documents</h1>
          <p className="page-lead text-xs sm:text-sm" style={{ color: 'var(--muted)' }}>Encrypted statement reader, Indian capital gains computation & immutable records</p>
        </div>
        <div className="flex items-center overflow-x-auto p-1 rounded-2xl border text-xs sm:text-sm font-semibold" style={{ background: 'var(--input)', borderColor: 'var(--border)' }}>
          {tabBtn('IMPORTER', 'Statement')}
          {tabBtn('TAXES', 'Tax')}
          {tabBtn('RECORD_BOOK', 'Records')}
        </div>
      </div>

      {matchErrors.length > 0 && (
        <div className="p-4 sm:p-5 rounded-2xl bg-amber-950/40 border border-amber-500/40 text-amber-200 space-y-2">
          <div className="flex items-center gap-2 font-bold text-sm text-amber-300">
            <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" />
            <span>Some records could not be matched</span>
          </div>
          <ul className="list-disc list-inside text-xs text-amber-200/90 space-y-1 pl-1 font-mono">
            {matchErrors.map((errMsg, idx) => (<li key={idx} className="leading-relaxed">{errMsg}</li>))}
          </ul>
        </div>
      )}

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
                <select value={targetMemberId} onChange={e => handleTargetMemberChange(e.target.value)} className="px-3 md:px-4 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-bold text-slate-100 cursor-pointer w-full sm:min-w-[220px]" id="target-family-member-select">
                  {familyMembers.map(member => (
                    <option key={member.id} value={member.id}>{member.name} ({member.relation})</option>
                  ))}
                </select>
              </div>

              <div
                onDragOver={e => { e.preventDefault(); setIsDragging(true); }}
                onDragLeave={e => { e.preventDefault(); setIsDragging(false); }}
                onDrop={e => {
                  e.preventDefault();
                  setIsDragging(false);
                  if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                    handleFileChange({ target: { files: e.dataTransfer.files } } as unknown as React.ChangeEvent<HTMLInputElement>);
                  }
                }}
                className={`border border-dashed rounded-2xl p-4 md:p-8 text-center space-y-3 ${isDragging ? 'border-amber-400 bg-amber-400/10' : 'border-slate-700 bg-slate-950/50'}`}
              >
                <div className="w-12 h-12 rounded-2xl bg-amber-400/10 border border-amber-400/30 text-amber-400 flex items-center justify-center mx-auto">
                  <UploadCloud className="w-6 h-6" />
                </div>
                <div className="max-w-lg mx-auto space-y-1">
                  <h3 className="text-base font-bold text-slate-100">Drop Encrypted CAS or NPS PDF, or Statement CSV</h3>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    Supports password-protected <strong>CDSL, NSDL, CAMS, KFintech and NPS</strong> PDFs via Python casparser. Same issuer overwrites the previous file.
                  </p>
                  {pythonReady === false && (
                    <div className="pt-2 space-y-1">
                      <p className="text-xs text-amber-300">Engine is not running.</p>
                      {engineDetail && <p className="text-[11px] text-amber-200/80 break-words">{engineDetail}</p>}
                      <p className="text-[11px] text-slate-400">Diagnostics: Download/GrowUrDhan-diagnostics.txt</p>
                    </div>
                  )}
                  {pythonReady === true && (
                    <div className="pt-1 space-y-1">
                      <p className="text-[11px] text-emerald-400/90">Engine is running.</p>
                      <p className="text-[11px] text-slate-500">Diagnostics: Download/GrowUrDhan-diagnostics.txt</p>
                    </div>
                  )}
                </div>
                <label className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs sm:text-sm font-bold rounded-xl cursor-pointer inline-flex items-center gap-2">
                  <FileText className="w-4 h-4 stroke-[2.5]" />
                  <span>Choose Statement File</span>
                  <input id="cas-file-input" type="file" accept=".pdf,.csv,.txt" className="hidden" onClick={e => { (e.target as HTMLInputElement).value = ''; }} onChange={handleFileChange} />
                </label>
                {selectedFile && (
                  <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-slate-800/90 border border-slate-700 text-xs text-slate-200 font-mono">
                    <FileCheck2 className="w-3.5 h-3.5 text-emerald-400" />
                    <span>{selectedFile.name}</span>
                    <button type="button" onClick={handleClearFile} className="ml-1.5 p-1 rounded-md text-slate-400"><X className="w-3.5 h-3.5" /></button>
                  </div>
                )}
              </div>

              {selectedFile && selectedFile.name.toLowerCase().endsWith('.pdf') && (
                <form onSubmit={handleDecryptAndParse} className="p-4 sm:p-5 rounded-2xl bg-slate-950 border border-amber-500/40 space-y-3">
                  <div className="flex items-center gap-2">
                    <Lock className="w-4 h-4 text-amber-400" />
                    <span className="text-xs sm:text-sm font-bold text-slate-200">PDF Password (PAN or NPS)</span>
                  </div>
                  <div className="flex flex-col sm:flex-row gap-3">
                    <div className="relative flex-1">
                      <input id="cas-pdf-password-input" type={showPassword ? 'text' : 'password'} value={pdfPassword} onChange={e => setPdfPassword(e.target.value)} className="w-full px-3.5 py-2.5 pr-10 bg-slate-900 border border-slate-700 rounded-xl text-sm font-mono text-slate-100" placeholder="PAN in capitals, or NPS first4+DDMM" />
                      <button type="button" onClick={() => setShowPassword(!showPassword)} className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400">{showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}</button>
                    </div>
                    <button type="submit" disabled={isParsing} className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 disabled:opacity-50 text-slate-950 text-xs sm:text-sm font-bold rounded-xl flex items-center justify-center gap-2">
                      {isParsing ? (<><RefreshCw className="w-4 h-4 animate-spin" /><span>Reading with Python casparser...</span></>) : (<><FileCheck2 className="w-4 h-4" /><span>Read Statement</span></>)}
                    </button>
                  </div>
                  <p className="text-[11px] text-slate-400">The file stays on this device.</p>
                </form>
              )}

              {parseError && (
                <div className="p-4 rounded-2xl bg-rose-950/90 border border-rose-800 text-rose-200 text-xs sm:text-sm flex items-start gap-3">
                  <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
                  <div><div className="font-bold">Reading Error</div><div className="text-xs text-rose-300">{parseError}</div></div>
                </div>
              )}
              {importNotification && (
                <div className={`p-4 rounded-2xl border text-xs sm:text-sm flex items-center justify-between ${importNotification.type === 'success' ? 'bg-emerald-950/80 border-emerald-800 text-emerald-200' : importNotification.type === 'error' ? 'bg-rose-950/80 border-rose-800 text-rose-200' : 'bg-sky-950/80 border-sky-800 text-sky-200'}`}>
                  <div className="flex items-center gap-2"><CheckCircle2 className="w-4 h-4" /><span>{importNotification.message}</span></div>
                  <button onClick={() => setImportNotification(null)} className="opacity-70"><X className="w-4 h-4" /></button>
                </div>
              )}
            </div>

            {parseResult && (
              <PanOwnerBanner check={panCheck} selectedName={activeTargetMember?.name || 'this member'} investorName={parseResult.investorName} printedPan={parseResult.investorPan} onSwitch={id => handleTargetMemberChange(id)} />
            )}
            {parseResult?.engine === 'PYTHON_CASPARSER' && (
              <CasConfirmPanel result={parseResult} previous={parseResult.fileType ? casLatest.byOwner[targetMemberId]?.[parseResult.fileType as CasFileType] || null : null} />
            )}

            {reconciliation && (
              <div className="card overflow-hidden space-y-4 p-4 md:p-7">
                <div className="space-y-1 pb-4 border-b border-slate-800">
                  <h3 className="text-lg font-bold font-serif text-slate-100">We read your statement dated {formattedStatementDate}</h3>
                  <p className="text-xs text-slate-400">This snapshot replaces the previous file of the same issuer. Only the difference is recorded.</p>
                </div>
                {(['MATCH','COST_UPDATE','NEW_POSITION','ADJUST_UP','ADJUST_DOWN','CLOSED'] as SnapshotOutcome[]).map(outcome => {
                  const count = reconciliation.counts[outcome];
                  if (!count) return null;
                  const label = outcome === 'MATCH' ? `${count} already up to date` : outcome === 'COST_UPDATE' ? `${count} invested amount(s) will be updated` : outcome === 'NEW_POSITION' ? `${count} not in your records yet` : outcome === 'ADJUST_UP' ? `${count} show more units than we have` : outcome === 'ADJUST_DOWN' ? `${count} shows fewer units` : `${count} no longer held`;
                  return (
                    <div key={outcome} className="rounded-2xl bg-slate-950/70 border border-slate-800/80 overflow-hidden">
                      <div className="p-3.5 flex items-center justify-between gap-3 text-xs sm:text-sm">
                        <span className="font-semibold text-slate-200">{label}</span>
                        <button type="button" onClick={() => setShowOutcomeGroup(prev => ({ ...prev, [outcome]: !prev[outcome] }))} className="px-3 py-1 rounded-lg bg-slate-800 text-slate-300 font-semibold text-xs">{showOutcomeGroup[outcome] ? 'Hide' : 'Show'}</button>
                      </div>
                      {showOutcomeGroup[outcome] && (
                        <div className="border-t border-slate-800/80 px-3.5 py-1">
                          {reconciliation.lines.filter(l => l.outcome === outcome).map(l => (
                            <div key={l.line.holdingId} className="py-2.5 flex items-center justify-between gap-3 text-xs">
                              <div className="flex items-center gap-2.5 min-w-0">
                                {outcome !== 'MATCH' && (
                                  <input type="checkbox" checked={acceptedHoldingIds.has(l.line.holdingId)} onChange={() => handleToggleLine(l.line.holdingId)} className="rounded border-slate-600" />
                                )}
                                <div className="min-w-0">
                                  <div className="font-semibold text-slate-200">{displayHoldingName(l.line.holdingName)}</div>
                                  <div className="text-slate-500 text-[11px]">{l.message}</div>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
                {reconciliation.warnings.length > 0 && (
                  <div className="p-4 rounded-2xl bg-amber-950/40 border border-amber-500/40 text-amber-200 text-xs space-y-1.5">
                    {reconciliation.warnings.map((w, idx) => (<div key={idx}>{w}</div>))}
                  </div>
                )}
                {areAllMatches ? (
                  <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 text-center space-y-4">
                    <div className="text-slate-200 text-sm font-semibold">Your records already match this statement. Nothing to do.</div>
                    <button onClick={handleCommitUpdates} disabled={isCommitting} className="px-6 py-2.5 bg-slate-800 text-slate-100 text-xs font-bold rounded-xl">{isCommitting ? 'Saving…' : 'Refresh Prices Only'}</button>
                  </div>
                ) : (
                  <div className="pt-4 border-t border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-4">
                    <div className="text-xs sm:text-sm text-slate-300">If you accept these, your total will change by <strong>{netAcceptedChangeMinor >= 0n ? '+' : '-'}Rs {MoneyEngine.groupIndian(MoneyEngine.toRupees(netAcceptedChangeMinor < 0n ? -netAcceptedChangeMinor : netAcceptedChangeMinor))}</strong>.</div>
                    <div className="flex items-center gap-3 w-full sm:w-auto">
                      <button type="button" onClick={handleCleanDuplicates} className="px-3.5 py-2.5 bg-slate-800 text-slate-300 text-xs font-semibold rounded-xl border border-slate-700 flex items-center gap-1.5"><RefreshCw className="w-3.5 h-3.5 text-amber-400" /><span>Health Check</span></button>
                      <button id="update-my-records-btn" onClick={handleCommitUpdates} disabled={isCommitting || commitBlockedByPan} className="flex-1 sm:flex-initial px-6 py-2.5 bg-emerald-500 disabled:opacity-50 text-slate-950 text-xs sm:text-sm font-bold rounded-xl">{isCommitting ? 'Saving…' : 'Update my records'}</button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {importBatches.length > 0 && (
              <div className="card p-6 space-y-3">
                <h3 className="text-base font-bold">Statements you have confirmed</h3>
                <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
                  {visibleBatches.map(b => (
                    <div key={b.id} className="py-3 flex items-start justify-between gap-3 text-sm">
                      <div>
                        <div className="font-semibold">{b.filename}</div>
                        <div className="text-xs mt-0.5" style={{ color: 'var(--muted)' }}>{b.statementType || b.kind}{b.periodTo ? ` · until ${formatStatementDate(b.periodTo)}` : ''}</div>
                      </div>
                    </div>
                  ))}
                </div>
                {importBatches.length > 2 && (
                  <button type="button" onClick={() => setShowOlderStatements(v => !v)} className="w-full text-xs font-semibold" style={{ color: 'var(--accent)' }}>
                    {showOlderStatements ? 'Hide earlier statements' : `${hiddenBatchCount} earlier statements`}
                  </button>
                )}
              </div>
            )}
          </div>
        </ErrorBoundary>
      )}

      {activeTab === 'TAXES' && (
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-900/80 p-4 rounded-3xl border border-slate-800">
            <div className="flex items-center gap-3">
              <span className="text-xs sm:text-sm font-bold text-slate-300">Financial Year:</span>
              <select value={selectedFY} onChange={e => setSelectedFY(e.target.value)} className="px-3.5 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-semibold text-slate-200">
                <option value="2025-26">April 2025 – March 2026</option>
                <option value="2024-25">April 2024 – March 2025</option>
              </select>
            </div>
            <button onClick={() => window.print()} className="px-3.5 py-1.5 bg-amber-400 text-slate-950 rounded-xl text-xs font-bold flex items-center gap-1.5"><Printer className="w-3.5 h-3.5" /><span>Print Tax Dossier</span></button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 space-y-2">
              <div className="text-xs text-slate-400 font-semibold">Long-term share gains</div>
              <PrivacyValue minor={taxSummary.ltcgEquityTotalMinor} className="text-xl font-bold text-slate-100" showCompact />
            </div>
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 space-y-2">
              <div className="text-xs text-slate-400 font-semibold">Short-term share gains</div>
              <PrivacyValue minor={taxSummary.stcgEquityTotalMinor} className="text-xl font-bold text-slate-100" showCompact />
            </div>
            <div className="bg-amber-950/30 rounded-3xl p-5 border border-amber-700/40 space-y-2">
              <div className="text-xs text-amber-300 font-semibold">SGB Sovereign Redemption</div>
              <PrivacyValue minor={taxSummary.sgbExemptGainsMinor} className="text-xl font-bold text-amber-200" showCompact />
            </div>
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 space-y-2">
              <div className="text-xs text-slate-400 font-semibold">Dividends & Interest Income</div>
              <PrivacyValue minor={taxSummary.dividendIncomeMinor} className="text-xl font-bold text-slate-100" showCompact />
            </div>
          </div>
          <div className="bg-slate-900/80 rounded-3xl border border-slate-800 p-6 space-y-4">
            <h3 className="text-base font-bold text-slate-100">Sales this year</h3>
            {taxSummary.records.length === 0 ? (
              <p className="text-sm py-6 text-center" style={{ color: 'var(--muted)' }}>No sales recorded in this year.</p>
            ) : taxSummary.records.map(rec => (
              <div key={rec.id} className="py-3 flex items-center justify-between">
                <div>
                  <div className="font-semibold text-sm text-slate-100">{displayHoldingName(rec.holdingName)}</div>
                  <div className="text-xs text-slate-400">{rec.taxType}</div>
                </div>
                <div className="text-sm font-bold text-emerald-400">+₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(rec.gainMinor))}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {activeTab === 'RECORD_BOOK' && (
        <div className="card overflow-hidden space-y-3 p-4 md:p-6">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b" style={{ borderColor: 'var(--border)' }}>
            <h3 className="text-base font-bold">Record Book</h3>
            <select value={recordBookFilterType} onChange={e => setRecordBookFilterType(e.target.value)} className="px-3 py-2 rounded-xl text-xs font-semibold border" style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}>
              <option value="ALL">All rows</option>
              <option value="BUY">Bought</option>
              <option value="SELL">Sold</option>
              <option value="SNAPSHOT_ADJUSTMENT">Updated to match statement</option>
              <option value="OPENING_BALANCE">Opening from statement</option>
              <option value="DIVIDEND">Dividend</option>
              <option value="INTEREST">Interest</option>
            </select>
          </div>
          <div className="divide-y" style={{ borderColor: 'var(--border)' }}>
            {filteredRecordBook.map(ev => (
              <div key={ev.id} className="py-2.5 flex items-start gap-2.5 min-w-0">
                <div className="min-w-0 flex-1">
                  <div className="flex items-start justify-between gap-2">
                    <div className="text-sm font-semibold leading-snug line-clamp-2">{displayHoldingName(ev.holdingName)}</div>
                    <PrivacyValue minor={BigInt(ev.totalAmountMinor || '0')} className="text-sm font-bold tabular-nums whitespace-nowrap shrink-0" includePaise={false} />
                  </div>
                  <div className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>{eventLabel(ev.eventType, ev.notes)} · {ev.eventDate}</div>
                </div>
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
  if (check.kind === 'NO_PDF_PAN') {
    return (
      <div className="p-4 rounded-2xl border bg-amber-950/40 border-amber-500/40 text-amber-100 text-sm">
        Confirm this file really belongs to {selectedName} before you update records.
      </div>
    );
  }
  if (check.kind === 'SWITCH') {
    return (
      <div className="p-4 rounded-2xl border bg-rose-950/50 border-rose-700/60 text-rose-100 space-y-3">
        <div className="font-bold">This statement is not {selectedName}'s</div>
        <p className="text-xs">The PDF is for {who}. That PAN belongs to <strong>{check.matched.name}</strong>.</p>
        <button type="button" onClick={() => onSwitch(check.matched.id)} className="px-4 py-2 rounded-xl text-xs font-bold bg-amber-400 text-slate-950">Switch to {check.matched.name} and review</button>
      </div>
    );
  }
  return (
    <div className="p-4 rounded-2xl border bg-rose-950/50 border-rose-700/60 text-rose-100 space-y-1">
      <div className="font-bold">No family member has this PAN</div>
      <p className="text-xs mt-1">This PDF is {who}. Add that PAN on the right person in Family, then select them here.</p>
    </div>
  );
};
