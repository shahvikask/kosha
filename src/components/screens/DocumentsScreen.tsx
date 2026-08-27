// FILE: src/components/screens/DocumentsScreen.tsx
import React, { useState, useMemo, useEffect } from 'react';
import { useKosha, findMatchingHolding } from '../../context/KoshaContext';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import { ErrorBoundary } from '../common/ErrorBoundary';
import { StatementImportRecord } from '../../types';
import { parseStatementFile, CASParseResult, getSampleCdslJune2026, getSampleCdslJuly2026, getSampleCdslSep2024 } from '../../lib/casParser';
import {
  SnapshotLine,
  ReconciliationResult,
  reconcileStatement,
  SnapshotOutcome,
} from '../../lib/snapshot';
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
  Sparkles,
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
  } = useKosha();

  const [activeTab, setActiveTab] = useState<'IMPORTER' | 'TAXES' | 'RECORD_BOOK'>('IMPORTER');
  const [selectedFY, setSelectedFY] = useState<string>('FY 2025-26');

  // Member selection for import (defaults to active filter or first member)
  const [targetMemberId, setTargetMemberId] = useState<string>(
    selectedMemberId === 'ALL' ? (familyMembers[0]?.id || 'fam_1') : selectedMemberId
  );

  // Selected member object
  const activeTargetMember = useMemo(() => {
    return familyMembers.find(m => m.id === targetMemberId) || familyMembers[0];
  }, [familyMembers, targetMemberId]);

  // File & Password state
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [pdfPassword, setPdfPassword] = useState<string>('');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [isParsing, setIsParsing] = useState<boolean>(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  // Statement Importer Results State
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
    CONFLICT: true,
  });

  const [importNotification, setImportNotification] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);

  // Record Book filter & search
  const [recordBookSearch, setRecordBookSearch] = useState<string>('');
  const [recordBookFilterType, setRecordBookFilterType] = useState<string>('ALL');

  // Clear staged file & results when changing member or clearing manually
  const handleTargetMemberChange = (newMemberId: string) => {
    setTargetMemberId(newMemberId);
    setSelectedFile(null);
    setParseResult(null);
    setRawRecords([]);
    setReconciliation(null);
    setAcceptedHoldingIds(new Set());
    setParseError(null);
    setImportNotification(null);
    const m = familyMembers.find(fm => fm.id === newMemberId);
    setPdfPassword(m?.panForStatements || '');
  };

  useEffect(() => {
    if (activeTargetMember?.panForStatements) {
      setPdfPassword(activeTargetMember.panForStatements);
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

  // When selectedMemberId changes in header filter, sync if not 'ALL'
  useEffect(() => {
    if (selectedMemberId !== 'ALL' && selectedMemberId !== targetMemberId) {
      handleTargetMemberChange(selectedMemberId);
    }
  }, [selectedMemberId]);

  // Build reconciliation review from parsed records
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
      let holdingId = matched?.id;
      const priceMinor = MoneyEngine.fromRupees(rec.pricePerUnit).toString();
      const priceMinorBig = BigInt(priceMinor);

      if (!matched) {
        holdingId = `tmp_${rec.id}`;
      }

      snapshotLines.push({
        holdingId: holdingId!,
        holdingName: rec.holdingName,
        category: rec.category,
        statementDate: rec.date || statementDate,
        quantityScaled: BigInt(Math.round(rec.quantity * 1e8)),
        unitPriceMinor: priceMinorBig,
        cumulativeInvestedMinor: rec.cumulativeInvested
          ? BigInt(Math.round(rec.cumulativeInvested * 1e4))
          : undefined,
        isin: rec.isin,
        folio: rec.folio,
      });
    }

    const recon = reconcileStatement(snapshotLines, ledgerEvents, holdings, ownerId);
    setReconciliation(recon);

    // Default checkbox selection rule:
    // Ticked by default EXCEPT ADJUST_DOWN and CLOSED, which are UNTICKED.
    const initialAccepted = new Set<string>();
    for (const line of recon.lines) {
      if (line.outcome === 'ADJUST_DOWN' || line.outcome === 'CLOSED') {
        // Unticked by default (opt-in)
      } else {
        // MATCH, NEW_POSITION, ADJUST_UP ticked by default
        initialAccepted.add(line.line.holdingId);
      }
    }
    setAcceptedHoldingIds(initialAccepted);
  };

  // Handle file picker selection
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
      processFile(file, '', false);
    } else {
      const initialPass = (pdfPassword || activeTargetMember?.panForStatements || '').trim();
      if (initialPass) {
        processFile(file, initialPass, true);
      } else {
        const memberName = activeTargetMember?.name || 'the primary holder';
        setImportNotification({
          type: 'info',
          message: `PDF file selected. If password-protected, enter the 10-character PAN of ${memberName} in capitals below and click "Read Statement".`,
        });
      }
    }
  };

  // Process statement file (PDF or CSV)
  const processFile = async (file: File, passwordToUse: string, isAutoAttempt = false) => {
    setIsParsing(true);
    setParseError(null);
    setImportNotification(null);

    try {
      const memberLedger = ledgerEvents
        .filter(ev => ev.ownerId === targetMemberId)
        .map(ev => ({
          holdingName: ev.holdingName,
          eventDate: ev.eventDate,
          totalAmountMinor: ev.totalAmountMinor,
        }));

      const result = await parseStatementFile(file, passwordToUse, memberLedger, targetMemberId);

      if (result.importRecords.length === 0) {
        setParseError(
          'No portfolio holdings or transactions found in the statement. Please check if the statement format is supported.'
        );
        return;
      }

      setParseResult(result);
      setRawRecords(result.importRecords);
      buildReconciliation(result, result.importRecords, targetMemberId);
    } catch (err: any) {
      console.error('Statement Parse Attempt:', err);

      const errString = err?.message || String(err);
      const isPasswordIssue =
        errString.includes('Password') ||
        errString.includes('password') ||
        errString.includes('Incorrect') ||
        err?.name === 'PasswordException';

      if (isPasswordIssue) {
        if (isAutoAttempt) {
          const memberName = activeTargetMember?.name || 'the primary holder';
          setImportNotification({
            type: 'info',
            message: `"${file.name}" is password-protected. Enter the 10-character PAN of ${memberName} in capitals below and click "Read Statement".`,
          });
        } else {
          setParseError(
            "That password did not open the file. CAS statements use the primary holder's PAN in capital letters."
          );
        }
      } else {
        setParseError(`Failed to parse statement: ${errString || 'Unsupported file format or corrupted stream.'}`);
      }
    } finally {
      setIsParsing(false);
    }
  };

  // Trigger manual decrypt button
  const handleDecryptAndParse = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;
    processFile(selectedFile, pdfPassword.trim(), false);
  };

  // Load sample statements
  const handleLoadCdslJune2026 = () => {
    const result = getSampleCdslJune2026();
    setSelectedFile(new File([''], 'JUNE2026_CDSL_CAS_Consolidated.pdf', { type: 'application/pdf' }));
    if (result.investorPan) setPdfPassword(result.investorPan);
    setParseError(null);
    setParseResult(result);
    setRawRecords(result.importRecords);
    buildReconciliation(result, result.importRecords, targetMemberId);
  };

  const handleLoadCdslJuly2026 = () => {
    const result = getSampleCdslJuly2026();
    setSelectedFile(new File([''], 'JUL2026_CDSL_CAS_Consolidated.pdf', { type: 'application/pdf' }));
    if (result.investorPan) setPdfPassword(result.investorPan);
    setParseError(null);
    setParseResult(result);
    setRawRecords(result.importRecords);
    buildReconciliation(result, result.importRecords, targetMemberId);
  };

  const handleLoadCdslSep2024 = () => {
    const result = getSampleCdslSep2024();
    setSelectedFile(new File([''], 'SEP2024_CDSL_CAS_Consolidated.pdf', { type: 'application/pdf' }));
    if (result.investorPan) setPdfPassword(result.investorPan);
    setParseError(null);
    setParseResult(result);
    setRawRecords(result.importRecords);
    buildReconciliation(result, result.importRecords, targetMemberId);
  };

  // Toggle individual line checkbox
  const handleToggleLine = (holdingId: string) => {
    setAcceptedHoldingIds(prev => {
      const next = new Set(prev);
      if (next.has(holdingId)) {
        next.delete(holdingId);
      } else {
        next.add(holdingId);
      }
      return next;
    });
  };

  // Calculate Net Change based on accepted items
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

  // Execute Commit
  const handleCommitUpdates = async () => {
    if (!reconciliation) return;

    const recordsToImport = rawRecords.filter(r => {
      if (r.recordType === 'TRANSACTION') return true;
      const matched = findMatchingHolding(holdings, r, targetMemberId);
      const hId = matched?.id || `tmp_${r.id}`;
      return acceptedHoldingIds.has(hId);
    });

    const res = await importStatementRecords(recordsToImport, targetMemberId, 'SYNC_UPDATE');

    setImportNotification({
      type: 'success',
      message: `Your records have been updated for ${activeTargetMember?.name}. ${res.imported} item(s) recorded.`,
    });

    setReconciliation(null);
    setRawRecords([]);
    setParseResult(null);
    setSelectedFile(null);
    setAcceptedHoldingIds(new Set());
  };

  // Clean Duplicates and Heal State
  const handleCleanDuplicates = async () => {
    const { mergedCount, flaggedCount } = await cleanDuplicatesAndHeal();
    setImportNotification({
      type: 'success',
      message: `Health Check: Re-merged ${mergedCount} duplicate items and flagged ${flaggedCount} items for review across all accounts.`,
    });
  };

  // Tax computation data
  const taxSummary = useMemo(() => {
    return computeTaxSummary(selectedFY);
  }, [selectedFY, computeTaxSummary]);

  // Derived or Tax computation unmatched / conflict errors
  const matchErrors = useMemo(() => {
    const list: string[] = [];
    if (derived?.errors && derived.errors.length > 0) {
      list.push(...derived.errors);
    }
    if (taxSummary?.errors && taxSummary.errors.length > 0) {
      list.push(...taxSummary.errors);
    }
    return Array.from(new Set(list));
  }, [derived?.errors, taxSummary?.errors]);

  // Record book filter
  const filteredRecordBook = useMemo(() => {
    return ledgerEvents.filter(ev => {
      const matchesSearch =
        recordBookSearch === '' ||
        ev.holdingName.toLowerCase().includes(recordBookSearch.toLowerCase()) ||
        (ev.notes && ev.notes.toLowerCase().includes(recordBookSearch.toLowerCase()));
      const matchesType = recordBookFilterType === 'ALL' || ev.eventType === recordBookFilterType;
      const matchesMember = selectedMemberId === 'ALL' || ev.ownerId === selectedMemberId;
      return matchesSearch && matchesType && matchesMember;
    });
  }, [ledgerEvents, recordBookSearch, recordBookFilterType, selectedMemberId]);

  // Format date readable
  const formattedStatementDate = useMemo(() => {
    if (!reconciliation?.statementDate) return '';
    try {
      const d = new Date(reconciliation.statementDate);
      if (isNaN(d.getTime())) return reconciliation.statementDate;
      return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' });
    } catch {
      return reconciliation.statementDate;
    }
  }, [reconciliation?.statementDate]);

  return (
    <div className="space-y-6 pb-12">
      {/* Header & Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-serif text-white tracking-tight">
            Documents & Records
          </h1>
          <p className="text-xs sm:text-sm text-slate-400">
            Encrypted statement reader, Indian capital gains computation & immutable records
          </p>
        </div>

        {/* Bento Tab Switcher */}
        <div className="flex items-center bg-slate-900 p-1 rounded-2xl border border-slate-800 text-xs sm:text-sm font-semibold">
          <button
            onClick={() => setActiveTab('IMPORTER')}
            className={`px-4 py-2 rounded-xl transition-all cursor-pointer ${
              activeTab === 'IMPORTER'
                ? 'bg-amber-400 text-slate-950 font-bold shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Statement Reader
          </button>
          <button
            onClick={() => setActiveTab('TAXES')}
            className={`px-4 py-2 rounded-xl transition-all cursor-pointer ${
              activeTab === 'TAXES'
                ? 'bg-amber-400 text-slate-950 font-bold shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Tax Summary (CA)
          </button>
          <button
            onClick={() => setActiveTab('RECORD_BOOK')}
            className={`px-4 py-2 rounded-xl transition-all cursor-pointer ${
              activeTab === 'RECORD_BOOK'
                ? 'bg-amber-400 text-slate-950 font-bold shadow-xs'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Record Book
          </button>
        </div>
      </div>

      {/* Amber Panel for Unmatched Records / Errors */}
      {matchErrors.length > 0 && (
        <div className="p-4 sm:p-5 rounded-2xl bg-amber-950/40 border border-amber-500/40 text-amber-200 space-y-2">
          <div className="flex items-center gap-2 font-bold text-sm text-amber-300">
            <AlertTriangle className="w-5 h-5 text-amber-400 shrink-0" />
            <span>Some records could not be matched</span>
          </div>
          <ul className="list-disc list-inside text-xs text-amber-200/90 space-y-1 pl-1 font-mono">
            {matchErrors.map((errMsg, idx) => (
              <li key={idx} className="leading-relaxed">
                {errMsg}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* TAB 1: STATEMENT IMPORTER & RECONCILIATION */}
      {activeTab === 'IMPORTER' && (
        <ErrorBoundary>
          <div className="space-y-6">
          {/* STEP 1: ENTITY SELECTION & FILE UPLOAD BENTO CARD */}
          <div className="bg-slate-900/80 rounded-3xl p-6 sm:p-7 border border-slate-800 backdrop-blur-md shadow-lg space-y-6">
            {/* Top Bar: Family Member Target Selector */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800">
              <div>
                <label className="text-xs font-bold text-slate-300 uppercase tracking-wider block mb-1 flex items-center gap-1.5">
                  <Users className="w-4 h-4 text-sky-400" />
                  <span>1. Select Family Member or Entity</span>
                </label>
                <p className="text-xs text-slate-400">
                  Holdings and differences will be verified against this member profile
                </p>
              </div>

              <div className="flex items-center gap-3">
                <select
                  id="target-family-member-select"
                  value={targetMemberId}
                  onChange={e => handleTargetMemberChange(e.target.value)}
                  className="px-4 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-bold text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50 cursor-pointer min-w-[220px]"
                >
                  {familyMembers.map(member => (
                    <option key={member.id} value={member.id}>
                      {member.name} ({member.relation}){' '}
                      {member.panMasked ? `• PAN: ${member.panMasked}` : ''}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Upload Drop Zone */}
            <div
              onDragOver={e => {
                e.preventDefault();
                setIsDragging(true);
              }}
              onDragLeave={e => {
                e.preventDefault();
                setIsDragging(false);
              }}
              onDrop={e => {
                e.preventDefault();
                setIsDragging(false);
                if (e.dataTransfer.files && e.dataTransfer.files[0]) {
                  const fakeEvent = {
                    target: { files: e.dataTransfer.files },
                  } as unknown as React.ChangeEvent<HTMLInputElement>;
                  handleFileChange(fakeEvent);
                }
              }}
              className={`border border-dashed rounded-2xl p-6 sm:p-8 text-center space-y-4 transition-colors ${
                isDragging
                  ? 'border-amber-400 bg-amber-400/10'
                  : 'border-slate-700 hover:border-amber-400/60 bg-slate-950/50'
              }`}
            >
              <div className="w-12 h-12 rounded-2xl bg-amber-400/10 border border-amber-400/30 text-amber-400 flex items-center justify-center mx-auto shadow-inner">
                <UploadCloud className="w-6 h-6" />
              </div>

              <div className="max-w-lg mx-auto space-y-1">
                <h3 className="text-base font-bold text-slate-100">
                  Drop Encrypted CAS PDF or Statement CSV
                </h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Supports password-protected <strong>CAMS & KFintech CAS</strong>,{' '}
                  <strong>CDSL & NSDL Demat Statements</strong>, and broker CSV exports.
                </p>
              </div>

              <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
                <label className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs sm:text-sm font-bold rounded-xl cursor-pointer transition-all shadow-md shadow-amber-400/10 flex items-center gap-2">
                  <FileText className="w-4 h-4 stroke-[2.5]" />
                  <span>Choose Statement File</span>
                  <input
                    id="cas-file-input"
                    type="file"
                    accept=".pdf,.csv,.txt"
                    className="hidden"
                    onClick={e => {
                      (e.target as HTMLInputElement).value = '';
                    }}
                    onChange={handleFileChange}
                  />
                </label>

                {import.meta.env.DEV && (
                  <div className="flex flex-col items-center gap-2 pt-2 w-full">
                    <p className="text-xs opacity-60 text-slate-400">
                      Development only — synthetic sample statements
                    </p>
                    <div className="flex flex-wrap items-center justify-center gap-3">
                      <button
                        onClick={handleLoadCdslJune2026}
                        className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
                      >
                        <Sparkles className="w-4 h-4 text-emerald-400" />
                        <span>Test June 2026 Statement</span>
                      </button>

                      <button
                        onClick={handleLoadCdslJuly2026}
                        className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
                      >
                        <Sparkles className="w-4 h-4 text-amber-400" />
                        <span>Test July 2026 Statement</span>
                      </button>

                      <button
                        onClick={handleLoadCdslSep2024}
                        className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
                      >
                        <Sparkles className="w-4 h-4 text-sky-400" />
                        <span>Test Sep 2024 Statement</span>
                      </button>
                    </div>
                  </div>
                )}
              </div>

              {selectedFile && (
                <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-slate-800/90 border border-slate-700 text-xs text-slate-200 font-mono shadow-sm">
                  <FileCheck2 className="w-3.5 h-3.5 text-emerald-400" />
                  <span>{selectedFile.name}</span>
                  {selectedFile.size > 0 && (
                    <span className="text-slate-400 font-sans">
                      ({(selectedFile.size / 1024).toFixed(1)} KB)
                    </span>
                  )}
                  <button
                    type="button"
                    onClick={handleClearFile}
                    className="ml-1.5 p-1 rounded-md bg-slate-700/60 hover:bg-rose-950/80 hover:text-rose-300 text-slate-400 cursor-pointer transition-colors"
                    title="Remove / Clear file"
                  >
                    <X className="w-3.5 h-3.5" />
                  </button>
                </div>
              )}
            </div>

            {/* PASSWORD / PAN PROMPT (For Encrypted PDFs) */}
            {selectedFile && selectedFile.name.toLowerCase().endsWith('.pdf') && (
              <form
                onSubmit={handleDecryptAndParse}
                className="p-4 sm:p-5 rounded-2xl bg-slate-950 border border-amber-500/40 space-y-3 animate-in fade-in"
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Lock className="w-4 h-4 text-amber-400 flex-shrink-0" />
                    <span className="text-xs sm:text-sm font-bold text-slate-200">
                      PDF Password (PAN / Statement Password)
                    </span>
                  </div>
                  <span className="text-[11px] text-slate-400">
                    {activeTargetMember?.panMasked
                      ? `Active: ${activeTargetMember.name} (${activeTargetMember.panMasked})`
                      : 'Enter 10-digit PAN or custom password'}
                  </span>
                </div>

                <div className="flex flex-col sm:flex-row gap-3">
                  <div className="relative flex-1">
                    <input
                      id="cas-pdf-password-input"
                      type={showPassword ? 'text' : 'password'}
                      placeholder={
                        activeTargetMember?.name
                          ? `Enter the 10-character PAN of ${activeTargetMember.name} in capitals`
                          : 'Enter the 10-character PAN in capitals'
                      }
                      value={pdfPassword}
                      onChange={e => setPdfPassword(e.target.value)}
                      className="w-full px-3.5 py-2.5 pr-10 bg-slate-900 border border-slate-700 rounded-xl text-sm font-mono text-slate-100 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 cursor-pointer"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>

                  <button
                    type="submit"
                    disabled={isParsing || !pdfPassword}
                    className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 disabled:opacity-50 text-slate-950 text-xs sm:text-sm font-bold rounded-xl shadow-md transition-all cursor-pointer flex items-center justify-center gap-2 whitespace-nowrap"
                  >
                    {isParsing ? (
                      <>
                        <RefreshCw className="w-4 h-4 animate-spin" />
                        <span>Reading in Browser...</span>
                      </>
                    ) : (
                      <>
                        <FileCheck2 className="w-4 h-4" />
                        <span>Read Statement</span>
                      </>
                    )}
                  </button>
                </div>

                <p className="text-[11px] text-slate-400">
                  🔒 {activeTargetMember?.name
                    ? `Enter the 10-character PAN of ${activeTargetMember.name} in capitals.`
                    : 'Enter the 10-character PAN of the primary holder in capitals.'}{' '}
                  Reading runs 100% locally in your browser memory.
                </p>
              </form>
            )}

            {/* Parsing Error Box */}
            {parseError && (
              <div className="p-4 rounded-2xl bg-rose-950/90 border border-rose-800 text-rose-200 text-xs sm:text-sm flex items-start gap-3 shadow-lg">
                <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
                <div className="space-y-1 flex-1">
                  <div className="font-bold">Reading Error</div>
                  <div className="text-xs text-rose-300 leading-relaxed">{parseError}</div>
                </div>
              </div>
            )}

            {/* Notification Banner */}
            {importNotification && (
              <div
                className={`p-4 rounded-2xl border text-xs sm:text-sm flex items-center justify-between shadow-lg animate-in fade-in ${
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
                <button
                  onClick={() => setImportNotification(null)}
                  className="cursor-pointer opacity-70 hover:opacity-100"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
            )}
          </div>

          {/* TASK 6: RECONCILIATION REVIEW (SRS-UX-033) */}
          {reconciliation && (
            <div className="bg-slate-900/80 rounded-3xl border border-slate-800 backdrop-blur-md shadow-lg overflow-hidden space-y-6 p-6 sm:p-7 animate-in fade-in">
              {/* TOP — Header with Date */}
              <div className="space-y-1 pb-4 border-b border-slate-800">
                <h3 className="text-lg font-bold font-serif text-slate-100">
                  We read your statement dated {formattedStatementDate}
                </h3>
                <p className="text-xs text-slate-400">
                  Review the differences below before updating your records. Only the difference is recorded.
                </p>
              </div>

              {/* TOP COUNTS — Counts first, detail second */}
              <div className="space-y-2.5 font-sans">
                {/* 1. MATCH */}
                {reconciliation.counts.MATCH > 0 && (
                  <div className="rounded-2xl bg-slate-950/70 border border-slate-800/80 overflow-hidden transition-all">
                    <div className="p-3.5 flex items-center justify-between gap-3 text-xs sm:text-sm">
                      <div className="flex items-center gap-3">
                        <span className="px-2.5 py-0.5 rounded-md font-mono font-bold bg-slate-800 text-slate-300 border border-slate-700">
                          OK
                        </span>
                        <span className="font-semibold text-slate-200">
                          {reconciliation.counts.MATCH} already up to date — nothing to change
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          setShowOutcomeGroup(prev => ({ ...prev, MATCH: !prev.MATCH }))
                        }
                        className="px-3 py-1 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 font-semibold text-xs transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <span>{showOutcomeGroup.MATCH ? 'Hide' : 'Show'}</span>
                        {showOutcomeGroup.MATCH ? (
                          <ChevronUp className="w-3.5 h-3.5" />
                        ) : (
                          <ChevronDown className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    {showOutcomeGroup.MATCH && (
                      <div className="border-t border-slate-800/80 divide-y divide-slate-900 bg-slate-950/40 px-3.5 py-1">
                        {reconciliation.lines
                          .filter(l => l.outcome === 'MATCH')
                          .map(l => (
                            <div
                              key={l.line.holdingId}
                              className="py-2.5 flex items-center justify-between gap-3 text-xs"
                            >
                              <div className="flex items-center gap-2.5">
                                <span className="text-emerald-400 font-bold">✓</span>
                                <div>
                                  <span className="font-semibold text-slate-200">
                                    {l.line.holdingName}
                                  </span>
                                  <span className="text-slate-500 ml-2">({l.message})</span>
                                </div>
                              </div>
                              <span className="text-slate-400 font-mono">
                                {Number(l.statementQtyScaled) / Number(QTY_SCALE)} units
                              </span>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 2. NEW_POSITION */}
                {reconciliation.counts.NEW_POSITION > 0 && (
                  <div className="rounded-2xl bg-emerald-950/30 border border-emerald-800/40 overflow-hidden transition-all">
                    <div className="p-3.5 flex items-center justify-between gap-3 text-xs sm:text-sm">
                      <div className="flex items-center gap-3">
                        <span className="px-2.5 py-0.5 rounded-md font-mono font-bold bg-emerald-900/60 text-emerald-300 border border-emerald-700/60">
                          NEW
                        </span>
                        <span className="font-semibold text-emerald-200">
                          {reconciliation.counts.NEW_POSITION} not in your records yet
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          setShowOutcomeGroup(prev => ({
                            ...prev,
                            NEW_POSITION: !prev.NEW_POSITION,
                          }))
                        }
                        className="px-3 py-1 rounded-lg bg-emerald-900/40 hover:bg-emerald-900/60 text-emerald-200 font-semibold text-xs transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <span>{showOutcomeGroup.NEW_POSITION ? 'Hide' : 'Show'}</span>
                        {showOutcomeGroup.NEW_POSITION ? (
                          <ChevronUp className="w-3.5 h-3.5" />
                        ) : (
                          <ChevronDown className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    {showOutcomeGroup.NEW_POSITION && (
                      <div className="border-t border-emerald-900/40 divide-y divide-emerald-950/40 bg-slate-950/40 px-3.5 py-2 space-y-2">
                        {reconciliation.lines
                          .filter(l => l.outcome === 'NEW_POSITION')
                          .map(l => (
                            <div
                              key={l.line.holdingId}
                              className="py-2 flex items-start justify-between gap-3 text-xs"
                            >
                              <div className="flex items-start gap-3">
                                <input
                                  type="checkbox"
                                  checked={acceptedHoldingIds.has(l.line.holdingId)}
                                  onChange={() => handleToggleLine(l.line.holdingId)}
                                  className="w-4 h-4 mt-0.5 rounded text-amber-400 focus:ring-0 cursor-pointer accent-amber-400"
                                />
                                <div>
                                  <div className="font-bold text-slate-100">
                                    {l.line.holdingName}
                                  </div>
                                  <div className="text-slate-300 text-[11px] mt-0.5 leading-relaxed">
                                    {l.message}
                                  </div>
                                </div>
                              </div>
                              <div className="text-right font-mono font-bold text-emerald-400 shrink-0">
                                +₹{' '}
                                {MoneyEngine.groupIndian(
                                  MoneyEngine.toRupees(
                                    (BigInt(l.deltaQtyScaled) * BigInt(l.priceMinor)) / 100_000_000n
                                  )
                                )}
                              </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 3. ADJUST_UP */}
                {reconciliation.counts.ADJUST_UP > 0 && (
                  <div className="rounded-2xl bg-sky-950/30 border border-sky-800/40 overflow-hidden transition-all">
                    <div className="p-3.5 flex items-center justify-between gap-3 text-xs sm:text-sm">
                      <div className="flex items-center gap-3">
                        <span className="px-2.5 py-0.5 rounded-md font-mono font-bold bg-sky-900/60 text-sky-300 border border-sky-700/60">
                          UP
                        </span>
                        <span className="font-semibold text-sky-200">
                          {reconciliation.counts.ADJUST_UP} show more units than we have
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          setShowOutcomeGroup(prev => ({
                            ...prev,
                            ADJUST_UP: !prev.ADJUST_UP,
                          }))
                        }
                        className="px-3 py-1 rounded-lg bg-sky-900/40 hover:bg-sky-900/60 text-sky-200 font-semibold text-xs transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <span>{showOutcomeGroup.ADJUST_UP ? 'Hide' : 'Review'}</span>
                        {showOutcomeGroup.ADJUST_UP ? (
                          <ChevronUp className="w-3.5 h-3.5" />
                        ) : (
                          <ChevronDown className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    {showOutcomeGroup.ADJUST_UP && (
                      <div className="border-t border-sky-900/40 divide-y divide-sky-950/40 bg-slate-950/40 px-3.5 py-2 space-y-2">
                        {reconciliation.lines
                          .filter(l => l.outcome === 'ADJUST_UP')
                          .map(l => (
                            <div
                              key={l.line.holdingId}
                              className="py-2 flex items-start justify-between gap-3 text-xs"
                            >
                              <div className="flex items-start gap-3">
                                <input
                                  type="checkbox"
                                  checked={acceptedHoldingIds.has(l.line.holdingId)}
                                  onChange={() => handleToggleLine(l.line.holdingId)}
                                  className="w-4 h-4 mt-0.5 rounded text-amber-400 focus:ring-0 cursor-pointer accent-amber-400"
                                />
                                <div>
                                  <div className="font-bold text-slate-100">
                                    {l.line.holdingName}
                                  </div>
                                  <div className="text-slate-300 text-[11px] mt-0.5 leading-relaxed">
                                    {l.message}
                                  </div>
                                </div>
                              </div>
                              <div className="text-right font-mono font-bold text-sky-400 shrink-0">
                                +₹{' '}
                                {MoneyEngine.groupIndian(
                                  MoneyEngine.toRupees(
                                    (BigInt(l.deltaQtyScaled) * BigInt(l.priceMinor)) / 100_000_000n
                                  )
                                )}
                              </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 4. ADJUST_DOWN */}
                {reconciliation.counts.ADJUST_DOWN > 0 && (
                  <div className="rounded-2xl bg-amber-950/30 border border-amber-800/40 overflow-hidden transition-all">
                    <div className="p-3.5 flex items-center justify-between gap-3 text-xs sm:text-sm">
                      <div className="flex items-center gap-3">
                        <span className="px-2.5 py-0.5 rounded-md font-mono font-bold bg-amber-900/60 text-amber-300 border border-amber-700/60">
                          DOWN
                        </span>
                        <span className="font-semibold text-amber-200">
                          {reconciliation.counts.ADJUST_DOWN} shows fewer units
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          setShowOutcomeGroup(prev => ({
                            ...prev,
                            ADJUST_DOWN: !prev.ADJUST_DOWN,
                          }))
                        }
                        className="px-3 py-1 rounded-lg bg-amber-900/40 hover:bg-amber-900/60 text-amber-200 font-semibold text-xs transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <span>{showOutcomeGroup.ADJUST_DOWN ? 'Hide' : 'Review'}</span>
                        {showOutcomeGroup.ADJUST_DOWN ? (
                          <ChevronUp className="w-3.5 h-3.5" />
                        ) : (
                          <ChevronDown className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    {showOutcomeGroup.ADJUST_DOWN && (
                      <div className="border-t border-amber-900/40 divide-y divide-amber-950/40 bg-slate-950/40 px-3.5 py-2 space-y-2">
                        {reconciliation.lines
                          .filter(l => l.outcome === 'ADJUST_DOWN')
                          .map(l => (
                            <div
                              key={l.line.holdingId}
                              className="py-2 flex items-start justify-between gap-3 text-xs"
                            >
                              <div className="flex items-start gap-3">
                                <input
                                  type="checkbox"
                                  checked={acceptedHoldingIds.has(l.line.holdingId)}
                                  onChange={() => handleToggleLine(l.line.holdingId)}
                                  className="w-4 h-4 mt-0.5 rounded text-amber-400 focus:ring-0 cursor-pointer accent-amber-400"
                                />
                                <div>
                                  <div className="font-bold text-slate-100">
                                    {l.line.holdingName}
                                  </div>
                                  <div className="text-slate-300 text-[11px] mt-0.5 leading-relaxed">
                                    {l.message}
                                  </div>
                                </div>
                              </div>
                              <div className="text-right font-mono font-bold text-amber-400 shrink-0">
                                -₹{' '}
                                {MoneyEngine.groupIndian(
                                  MoneyEngine.toRupees(
                                    (BigInt(-l.deltaQtyScaled) * BigInt(l.priceMinor)) / 100_000_000n
                                  )
                                )}
                              </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}

                {/* 5. CLOSED */}
                {reconciliation.counts.CLOSED > 0 && (
                  <div className="rounded-2xl bg-rose-950/30 border border-rose-800/40 overflow-hidden transition-all">
                    <div className="p-3.5 flex items-center justify-between gap-3 text-xs sm:text-sm">
                      <div className="flex items-center gap-3">
                        <span className="px-2.5 py-0.5 rounded-md font-mono font-bold bg-rose-900/60 text-rose-300 border border-rose-700/60">
                          GONE
                        </span>
                        <span className="font-semibold text-rose-200">
                          {reconciliation.counts.CLOSED} no longer held
                        </span>
                      </div>
                      <button
                        type="button"
                        onClick={() =>
                          setShowOutcomeGroup(prev => ({
                            ...prev,
                            CLOSED: !prev.CLOSED,
                          }))
                        }
                        className="px-3 py-1 rounded-lg bg-rose-900/40 hover:bg-rose-900/60 text-rose-200 font-semibold text-xs transition-colors cursor-pointer flex items-center gap-1"
                      >
                        <span>{showOutcomeGroup.CLOSED ? 'Hide' : 'Review'}</span>
                        {showOutcomeGroup.CLOSED ? (
                          <ChevronUp className="w-3.5 h-3.5" />
                        ) : (
                          <ChevronDown className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>

                    {showOutcomeGroup.CLOSED && (
                      <div className="border-t border-rose-900/40 divide-y divide-rose-950/40 bg-slate-950/40 px-3.5 py-2 space-y-2">
                        {reconciliation.lines
                          .filter(l => l.outcome === 'CLOSED')
                          .map(l => (
                            <div
                              key={l.line.holdingId}
                              className="py-2 flex items-start justify-between gap-3 text-xs"
                            >
                              <div className="flex items-start gap-3">
                                <input
                                  type="checkbox"
                                  checked={acceptedHoldingIds.has(l.line.holdingId)}
                                  onChange={() => handleToggleLine(l.line.holdingId)}
                                  className="w-4 h-4 mt-0.5 rounded text-amber-400 focus:ring-0 cursor-pointer accent-amber-400"
                                />
                                <div>
                                  <div className="font-bold text-slate-100">
                                    {l.line.holdingName}
                                  </div>
                                  <div className="text-slate-300 text-[11px] mt-0.5 leading-relaxed">
                                    {l.message}
                                  </div>
                                </div>
                              </div>
                              <div className="text-right font-mono font-bold text-rose-400 shrink-0">
                                -₹{' '}
                                {MoneyEngine.groupIndian(
                                  MoneyEngine.toRupees(
                                    (BigInt(l.expectedQtyScaled) * BigInt(l.priceMinor)) / 100_000_000n
                                  )
                                )}
                              </div>
                            </div>
                          ))}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* RULE 6: Amber warnings panel */}
              {reconciliation.warnings && reconciliation.warnings.length > 0 && (
                <div className="p-4 rounded-2xl bg-amber-950/40 border border-amber-500/40 text-amber-200 text-xs space-y-1.5">
                  <div className="flex items-center gap-2 font-bold text-amber-300">
                    <AlertTriangle className="w-4 h-4 text-amber-400 shrink-0" />
                    <span>Important notice regarding this statement</span>
                  </div>
                  <ul className="list-disc list-inside space-y-1 text-amber-200/90 pl-1 leading-relaxed">
                    {reconciliation.warnings.map((w, idx) => (
                      <li key={idx}>{w}</li>
                    ))}
                  </ul>
                </div>
              )}

              {/* RULE 7: If every line is MATCH */}
              {areAllMatches ? (
                <div className="p-5 rounded-2xl bg-slate-950 border border-slate-800 text-center space-y-4">
                  <div className="text-slate-200 text-sm font-semibold">
                    Your records already match this statement. Nothing to do.
                  </div>
                  <button
                    onClick={handleCommitUpdates}
                    className="px-6 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-100 text-xs sm:text-sm font-bold rounded-xl border border-slate-700 transition-colors cursor-pointer"
                  >
                    Refresh Prices Only
                  </button>
                </div>
              ) : (
                /* RULE 4 & 5: Net change statement & Update my records button */
                <div className="pt-4 border-t border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-4">
                  <div className="text-xs sm:text-sm text-slate-300 font-medium">
                    If you accept these, your total will change by{' '}
                    <strong
                      className={
                        netAcceptedChangeMinor >= 0n
                          ? 'text-emerald-400 font-bold'
                          : 'text-rose-400 font-bold'
                      }
                    >
                      {netAcceptedChangeMinor >= 0n ? '+' : '-'}Rs{' '}
                      {MoneyEngine.groupIndian(
                        MoneyEngine.toRupees(
                          netAcceptedChangeMinor < 0n
                            ? -netAcceptedChangeMinor
                            : netAcceptedChangeMinor
                        )
                      )}
                    </strong>
                    .
                  </div>

                  <div className="flex items-center gap-3 w-full sm:w-auto">
                    <button
                      type="button"
                      onClick={handleCleanDuplicates}
                      className="px-3.5 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold rounded-xl border border-slate-700 transition-colors cursor-pointer flex items-center gap-1.5"
                      title="Merge folios and check integrity"
                    >
                      <RefreshCw className="w-3.5 h-3.5 text-amber-400" />
                      <span>Health Check</span>
                    </button>

                    <button
                      id="update-my-records-btn"
                      onClick={handleCommitUpdates}
                      className="flex-1 sm:flex-initial px-6 py-2.5 bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs sm:text-sm font-bold rounded-xl shadow-md transition-colors cursor-pointer flex items-center justify-center gap-2"
                    >
                      <span>Update my records</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
        </ErrorBoundary>
      )}

      {/* TAB 2: TAX SUMMARY (LTCG / STCG / SGB EXEMPTION) */}
      {activeTab === 'TAXES' && (
        <div className="space-y-6">
          {/* FY Selector & Print Header Bento Bar */}
          <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-900/80 p-4 rounded-3xl border border-slate-800 backdrop-blur-md shadow-lg">
            <div className="flex items-center gap-3">
              <span className="text-xs sm:text-sm font-bold text-slate-300">Financial Year:</span>
              <select
                value={selectedFY}
                onChange={e => setSelectedFY(e.target.value)}
                className="px-3.5 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-semibold text-slate-200 cursor-pointer"
              >
                <option value="FY 2025-26">FY 2025-26 (AY 2026-27)</option>
                <option value="FY 2024-25">FY 2024-25 (AY 2025-26)</option>
              </select>
            </div>

            <button
              onClick={() => window.print()}
              className="px-3.5 py-1.5 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs font-bold flex items-center gap-1.5 shadow-md shadow-amber-400/10 cursor-pointer transition-all"
            >
              <Printer className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>Print Tax Dossier</span>
            </button>
          </div>

          {/* Tax Summary Bento Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {/* Section 112A LTCG */}
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 shadow-lg backdrop-blur-md space-y-2">
              <div className="text-xs text-slate-400 font-semibold">LTCG (Section 112A)</div>
              <PrivacyValue
                minor={taxSummary.ltcgEquityTotalMinor}
                className="text-xl font-bold text-slate-100"
                showCompact
              />
              <div className="text-xs text-emerald-400 font-medium">
                ₹1.25L annual exemption applied. Tax payable: ₹0.00
              </div>
            </div>

            {/* Section 111A STCG */}
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 shadow-lg backdrop-blur-md space-y-2">
              <div className="text-xs text-slate-400 font-semibold">STCG (Section 111A @ 20%)</div>
              <PrivacyValue
                minor={taxSummary.stcgEquityTotalMinor}
                className="text-xl font-bold text-slate-100"
                showCompact
              />
              <div className="text-xs text-rose-400 font-medium">
                Estimated Tax: ₹{' '}
                {MoneyEngine.groupIndian(MoneyEngine.toRupees(taxSummary.stcgEquityTaxMinor))}
              </div>
            </div>

            {/* SGB 100% Tax Exemption */}
            <div className="bg-amber-950/30 rounded-3xl p-5 border border-amber-700/40 shadow-lg backdrop-blur-md space-y-2">
              <div className="text-xs text-amber-300 font-semibold">SGB Sovereign Redemption</div>
              <PrivacyValue
                minor={taxSummary.sgbExemptGainsMinor}
                className="text-xl font-bold text-amber-200"
                showCompact
              />
              <div className="text-xs text-emerald-400 font-bold">
                ✓ 100% Tax Free under Sec 47(viic)
              </div>
            </div>

            {/* Dividend & Interest Income */}
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 shadow-lg backdrop-blur-md space-y-2">
              <div className="text-xs text-slate-400 font-semibold">Dividends & Interest Income</div>
              <PrivacyValue
                minor={taxSummary.dividendIncomeMinor}
                className="text-xl font-bold text-slate-100"
                showCompact
              />
              <div className="text-xs text-slate-400">Taxable at normal slab rates</div>
            </div>
          </div>

          {/* Realised Sales Capital Gains Table Bento Card */}
          <div className="bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg overflow-hidden p-6 space-y-4 backdrop-blur-md">
            <h3 className="text-base font-bold text-slate-100">Capital Gains Schedule</h3>

            <div className="divide-y divide-slate-800">
              {taxSummary.records.map(rec => (
                <div key={rec.id} className="py-3 flex items-center justify-between">
                  <div>
                    <div className="font-semibold text-sm text-slate-100">{rec.holdingName}</div>
                    <div className="text-xs text-slate-400">
                      Bought: {rec.purchaseDate} • Sold: {rec.saleDate} ({rec.holdingDays} days) •{' '}
                      {rec.taxType}
                    </div>
                  </div>

                  <div className="text-right">
                    <div className="text-sm font-bold text-emerald-400">
                      +₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(rec.gainMinor))}
                    </div>
                    <div className="text-xs text-slate-400">{rec.applicableRate}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Mandatory Statutory Disclaimer */}
          <div className="p-4 rounded-2xl bg-slate-950/80 border border-slate-800 text-xs text-slate-400 leading-relaxed">
            <span className="font-bold text-slate-200">Notice for your Chartered Accountant: </span>
            This document is a computational record of asset transactions and holdings compiled from your local records. It does not constitute formal tax advice or a filed Income Tax Return (ITR). Tax rules are subject to the Finance Act provisions for the relevant assessment year.
          </div>
        </div>
      )}

      {/* TAB 3: APPEND-ONLY RECORD BOOK */}
      {activeTab === 'RECORD_BOOK' && (
        <div className="bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg overflow-hidden space-y-4 p-6 backdrop-blur-md">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-800">
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-slate-100">Record Book</h3>
                <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-emerald-950/80 text-emerald-300 border border-emerald-800/60">
                  Immutable
                </span>
              </div>
              <p className="text-xs text-slate-400">
                Chronological timeline of all purchases, sales, bonus issues, splits, dividends & interest
              </p>
            </div>

            {/* Filter */}
            <div className="flex items-center gap-2">
              <select
                value={recordBookFilterType}
                onChange={e => setRecordBookFilterType(e.target.value)}
                className="px-3.5 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs font-semibold text-slate-200 cursor-pointer"
              >
                <option value="ALL">All Event Types</option>
                <option value="BUY">BUY</option>
                <option value="SELL">SELL</option>
                <option value="DIVIDEND">DIVIDEND</option>
                <option value="INTEREST">INTEREST</option>
                <option value="REPAYMENT">REPAYMENT</option>
              </select>
            </div>
          </div>

          <div className="divide-y divide-slate-800">
            {filteredRecordBook.map(ev => {
              const isIncome = ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST';
              return (
                <div
                  key={ev.id}
                  className="py-3 flex items-center justify-between px-2 hover:bg-slate-800/40 rounded-xl transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-9 h-9 rounded-xl flex items-center justify-center text-xs font-bold border ${
                        ev.eventType === 'BUY'
                          ? 'bg-sky-950/80 text-sky-300 border-sky-800/60'
                          : ev.eventType === 'SELL'
                          ? 'bg-rose-950/80 text-rose-300 border-rose-800/60'
                          : isIncome
                          ? 'bg-emerald-950/80 text-emerald-300 border-emerald-800/60'
                          : 'bg-slate-800 text-slate-300 border-slate-700'
                      }`}
                    >
                      {ev.eventType}
                    </div>

                    <div>
                      <div className="font-semibold text-sm text-slate-100">{ev.holdingName}</div>
                      <div className="text-xs text-slate-400">
                        {ev.eventDate} • {ev.notes || 'Recorded trade'}
                      </div>
                    </div>
                  </div>

                  <div className="text-right">
                    <PrivacyValue
                      minor={BigInt(ev.totalAmountMinor || '0')}
                      className={`text-sm font-bold ${isIncome ? 'text-emerald-400' : 'text-slate-100'}`}
                      prefix={isIncome ? '+' : ''}
                      includePaise={false}
                    />
                    <div className="text-[11px] text-slate-500 font-mono">ID: {ev.id}</div>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
