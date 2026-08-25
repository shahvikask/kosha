import React, { useState, useMemo, useEffect } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import { StatementImportRecord, EventType } from '../../types';
import { parseStatementFile, CASParseResult, getSampleCdslJune2026, getSampleCdslJuly2026, getSampleCdslSep2024 } from '../../lib/casParser';
import {
  FileSpreadsheet,
  UploadCloud,
  CheckCircle2,
  AlertTriangle,
  FileText,
  Clock,
  Printer,
  Download,
  Filter,
  Check,
  X,
  Layers,
  ArrowUpDown,
  Search,
  ShieldCheck,
  Lock,
  Eye,
  EyeOff,
  Users,
  Building2,
  Sparkles,
  RefreshCw,
  FileCheck2,
} from 'lucide-react';

export const DocumentsScreen: React.FC = () => {
  const {
    familyMembers,
    selectedMemberId,
    ledgerEvents,
    computeTaxSummary,
    importStatementRecords,
    cleanDuplicatesAndHeal,
    filteredHoldings,
  } = useKosha();

  const [activeTab, setActiveTab] = useState<'IMPORTER' | 'TAXES' | 'RECORD_BOOK'>('IMPORTER');
  const [selectedFY, setSelectedFY] = useState<string>('FY 2025-26');
  const [importMode, setImportMode] = useState<'SYNC_UPDATE' | 'REPLACE_SNAPSHOT'>('SYNC_UPDATE');

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
  const [pdfPassword, setPdfPassword] = useState<string>(activeTargetMember?.panCard ? activeTargetMember.panCard.toUpperCase() : '');
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [isParsing, setIsParsing] = useState<boolean>(false);
  const [parseError, setParseError] = useState<string | null>(null);
  const [isDragging, setIsDragging] = useState<boolean>(false);

  // Auto-fill PAN password when target member changes
  useEffect(() => {
    if (activeTargetMember?.panCard) {
      setPdfPassword(activeTargetMember.panCard.toUpperCase());
    } else if (activeTargetMember?.panMasked && !activeTargetMember.panMasked.includes('*')) {
      setPdfPassword(activeTargetMember.panMasked.toUpperCase());
    } else {
      setPdfPassword('');
    }
  }, [activeTargetMember]);

  // Clear staged file & results when changing member or clearing manually
  const handleTargetMemberChange = (newMemberId: string) => {
    setTargetMemberId(newMemberId);
    setSelectedFile(null);
    setParseResult(null);
    setImportRecords([]);
    setParseError(null);
    setImportNotification(null);
    
    const newMember = familyMembers.find(m => m.id === newMemberId);
    if (newMember?.panCard) {
      setPdfPassword(newMember.panCard.toUpperCase());
    } else if (newMember?.panMasked && !newMember.panMasked.includes('*')) {
      setPdfPassword(newMember.panMasked.toUpperCase());
    } else {
      setPdfPassword('');
    }
  };

  const handleClearFile = () => {
    setSelectedFile(null);
    setParseResult(null);
    setImportRecords([]);
    setParseError(null);
    setImportNotification(null);
    if (activeTargetMember?.panCard) {
      setPdfPassword(activeTargetMember.panCard.toUpperCase());
    } else {
      setPdfPassword('');
    }
  };

  // When selectedMemberId changes in header filter, sync if not 'ALL'
  useEffect(() => {
    if (selectedMemberId !== 'ALL' && selectedMemberId !== targetMemberId) {
      handleTargetMemberChange(selectedMemberId);
    }
  }, [selectedMemberId]);

  // Statement Importer Results State
  const [parseResult, setParseResult] = useState<CASParseResult | null>(null);
  const [importRecords, setImportRecords] = useState<StatementImportRecord[]>([]);
  const [importNotification, setImportNotification] = useState<{ type: 'success' | 'error' | 'info'; message: string } | null>(null);
  const [reviewCategoryFilter, setReviewCategoryFilter] = useState<'ALL' | 'MUTUAL_FUND' | 'EQUITY' | 'SPECIALISED' | 'TRANSACTION'>('ALL');
  
  // Record Book filter & search
  const [recordBookSearch, setRecordBookSearch] = useState<string>('');
  const [recordBookFilterType, setRecordBookFilterType] = useState<string>('ALL');

  // Handle file picker selection
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setSelectedFile(file);
    setParseError(null);
    setImportNotification(null);
    setParseResult(null);
    setImportRecords([]);

    const isPdf = file.name.toLowerCase().endsWith('.pdf') || file.type === 'application/pdf';

    // If it's a CSV or text file, we can parse immediately
    if (!isPdf) {
      processFile(file, '', false);
    } else {
      // For PDF, check if we have a pre-filled PAN password
      const initialPass = (pdfPassword || activeTargetMember?.panCard || '').trim();
      if (initialPass) {
        processFile(file, initialPass, true /* auto-attempt */);
      } else {
        // Prompt for password
        setImportNotification({
          type: 'info',
          message: 'PDF file selected. If password-protected, enter your 10-character PAN in uppercase below and click "Decrypt & Parse Statement".',
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
      // Build lookup list for duplicate matching
      const memberLedger = ledgerEvents
        .filter(ev => ev.ownerId === targetMemberId)
        .map(ev => ({
          holdingName: ev.holdingName,
          eventDate: ev.eventDate,
          totalAmountMinor: ev.totalAmountMinor,
        }));

      const result = await parseStatementFile(file, passwordToUse, memberLedger, targetMemberId);

      if (result.importRecords.length === 0) {
        setParseError('No portfolio holdings or transactions found in the statement. Please check if the statement format is supported.');
        setIsParsing(false);
        return;
      }

      setParseResult(result);
      setImportRecords(result.importRecords);
      setIsParsing(false);

      const newCount = result.importRecords.filter(r => r.status === 'NEW').length;
      const dupCount = result.importRecords.filter(r => r.status === 'ALREADY_EXISTS').length;

      setImportNotification({
        type: 'success',
        message: `Statement parsed successfully: ${result.importRecords.length} items identified (${newCount} new to import, ${dupCount} already in ledger).`,
      });
    } catch (err: any) {
      console.error('CAS Parse Attempt:', err);
      setIsParsing(false);
      
      const errString = err?.message || String(err);
      const isPasswordIssue =
        errString.includes('Password') ||
        errString.includes('password') ||
        errString.includes('Incorrect') ||
        err?.name === 'PasswordException';

      if (isPasswordIssue) {
        if (isAutoAttempt) {
          // Soft notification asking for the password without showing a red crash/error
          setImportNotification({
            type: 'info',
            message: `"${file.name}" is password-protected. Please enter or select the primary holder's PAN / password below and click "Decrypt & Parse Statement".`,
          });
        } else {
          setParseError('Incorrect PDF password. For CAS statements from CAMS, KFintech, CDSL, or NSDL, enter the primary holder’s 10-character PAN in capital letters (e.g. ABCDE1234F). If you set a custom password when requesting CAS, enter that.');
        }
      } else {
        setParseError(`Failed to parse statement: ${errString || 'Unsupported file format or corrupted stream.'}`);
      }
    }
  };

  // Trigger manual decrypt button
  const handleDecryptAndParse = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;
    processFile(selectedFile, pdfPassword.trim(), false);
  };

  // Load CDSL June 2026 CAS dataset
  const handleLoadCdslJune2026 = () => {
    const result = getSampleCdslJune2026();
    setSelectedFile(new File([''], 'JUNE2026_CDSL_CAS_Consolidated.pdf', { type: 'application/pdf' }));
    if (result.investorPan) {
      setPdfPassword(result.investorPan);
    }
    setParseError(null);
    setParseResult(result);
    setImportRecords(result.importRecords);
    setImportNotification({
      type: 'success',
      message: `CDSL Consolidated Statement (June 2026, ₹3.09 Cr Portfolio) loaded for ${activeTargetMember?.name}. Ready to review and credit.`,
    });
  };

  // Load CDSL July 2026 CAS dataset
  const handleLoadCdslJuly2026 = () => {
    const result = getSampleCdslJuly2026();
    setSelectedFile(new File([''], 'JUL2026_CDSL_CAS_Consolidated.pdf', { type: 'application/pdf' }));
    if (result.investorPan) {
      setPdfPassword(result.investorPan);
    }
    setParseError(null);
    setParseResult(result);
    setImportRecords(result.importRecords);
    setImportNotification({
      type: 'success',
      message: `CDSL Consolidated Statement (July 2026, ₹3.09 Cr Portfolio) loaded for ${activeTargetMember?.name}. Ready to review and credit.`,
    });
  };

  // Load CDSL Sept 2024 CAS dataset
  const handleLoadCdslSep2024 = () => {
    const result = getSampleCdslSep2024();
    setSelectedFile(new File([''], 'SEP2024_CDSL_CAS_Consolidated.pdf', { type: 'application/pdf' }));
    if (result.investorPan) {
      setPdfPassword(result.investorPan);
    }
    setParseError(null);
    setParseResult(result);
    setImportRecords(result.importRecords);
    setImportNotification({
      type: 'success',
      message: `CDSL Consolidated Statement (September 2024, ₹1.51 Cr Portfolio) loaded for ${activeTargetMember?.name}. Ready to review and credit.`,
    });
  };

  // Toggle select all
  const handleToggleSelectAll = () => {
    const allSelected = importRecords.every(r => r.selected);
    setImportRecords(prev => prev.map(r => ({ ...r, selected: !allSelected })));
  };

  // Clean Duplicates and Heal State
  const handleCleanDuplicates = () => {
    const { mergedCount, flaggedCount } = cleanDuplicatesAndHeal();
    setImportNotification({
      type: 'success',
      message: `Portfolio Health Check: Re-merged ${mergedCount} duplicate items and flagged ${flaggedCount} items for review across all accounts.`,
    });
  };

  // Execute Import
  const handleExecuteImport = () => {
    const selected = importRecords.filter(r => r.selected);
    if (selected.length === 0) {
      setImportNotification({
        type: 'error',
        message: 'No transactions selected for import. Check at least one item.',
      });
      return;
    }

    const res = importStatementRecords(selected, targetMemberId, importMode);
    const updatedMsg = res.updated > 0 ? `${res.updated} existing holdings updated, ` : '';
    const importedMsg = `${res.imported} new records added`;
    setImportNotification({
      type: 'success',
      message: `Portfolio Synchronized: ${updatedMsg}${importedMsg} for ${activeTargetMember?.name}.`,
    });
    setImportRecords([]);
    setParseResult(null);
    setSelectedFile(null);
  };

  // Tax computation data
  const taxSummary = useMemo(() => {
    return computeTaxSummary(selectedFY);
  }, [selectedFY, computeTaxSummary]);

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

  return (
    <div className="space-y-6 pb-12">
      
      {/* Header & Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-serif text-white tracking-tight">
            Documents & Records
          </h1>
          <p className="text-xs sm:text-sm text-slate-400">
            Encrypted CAS statement parser, Indian capital gains computation & immutable ledger
          </p>
        </div>

        {/* Bento Tab Switcher */}
        <div className="flex items-center bg-slate-900 p-1 rounded-2xl border border-slate-800 text-xs sm:text-sm font-semibold">
          <button
            onClick={() => setActiveTab('IMPORTER')}
            className={`px-4 py-2 rounded-xl transition-all cursor-pointer ${
              activeTab === 'IMPORTER' ? 'bg-amber-400 text-slate-950 font-bold shadow-xs' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Import CAS / Tradebook
          </button>
          <button
            onClick={() => setActiveTab('TAXES')}
            className={`px-4 py-2 rounded-xl transition-all cursor-pointer ${
              activeTab === 'TAXES' ? 'bg-amber-400 text-slate-950 font-bold shadow-xs' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Tax Summary (CA)
          </button>
          <button
            onClick={() => setActiveTab('RECORD_BOOK')}
            className={`px-4 py-2 rounded-xl transition-all cursor-pointer ${
              activeTab === 'RECORD_BOOK' ? 'bg-amber-400 text-slate-950 font-bold shadow-xs' : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Record Book
          </button>
        </div>
      </div>

      {/* TAB 1: STATEMENT IMPORTER */}
      {activeTab === 'IMPORTER' && (
        <div className="space-y-6">
          
          {/* STEP 1: ENTITY SELECTION & FILE UPLOAD BENTO CARD */}
          <div className="bg-slate-900/80 rounded-3xl p-6 sm:p-7 border border-slate-800 backdrop-blur-md shadow-lg space-y-6">
            
            {/* Top Bar: Family Member Target Selector */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800">
              <div>
                <label className="text-xs font-bold text-slate-300 uppercase tracking-wider block mb-1 flex items-center gap-1.5">
                  <Users className="w-4 h-4 text-sky-400" />
                  <span>1. Select Family Member or HUF Entity to Import For</span>
                </label>
                <p className="text-xs text-slate-400">
                  Holdings and historical trade parcels will be credited to this specific member profile
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
                      {member.name} ({member.relation}) {member.panCard ? `• PAN: ${member.panCard}` : ''}
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
                  Drop Encrypted CAS PDF or Tradebook CSV
                </h3>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Supports password-protected <strong>CAMS & KFintech CAS</strong>, <strong>CDSL & NSDL Demat Statements</strong>, and broker CSV exports (Zerodha, Groww, AngelOne).
                </p>
              </div>

              <div className="flex flex-wrap items-center justify-center gap-3 pt-2">
                <label className="px-5 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs sm:text-sm font-bold rounded-xl cursor-pointer transition-all shadow-md shadow-amber-400/10 flex items-center gap-2">
                  <FileText className="w-4 h-4 stroke-[2.5]" />
                  <span>Choose CAS PDF / CSV File</span>
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

                <button
                  onClick={handleLoadCdslJune2026}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
                >
                  <Sparkles className="w-4 h-4 text-emerald-400" />
                  <span>Test CDSL June 2026 (₹3.09 Cr)</span>
                </button>

                <button
                  onClick={handleLoadCdslJuly2026}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
                >
                  <Sparkles className="w-4 h-4 text-amber-400" />
                  <span>Test CDSL July 2026 (₹3.09 Cr)</span>
                </button>

                <button
                  onClick={handleLoadCdslSep2024}
                  className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer flex items-center gap-1.5"
                >
                  <Sparkles className="w-4 h-4 text-sky-400" />
                  <span>Test CDSL Sep 2024 (₹1.51 Cr)</span>
                </button>
              </div>

              {selectedFile && (
                <div className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-xl bg-slate-800/90 border border-slate-700 text-xs text-slate-200 font-mono shadow-sm">
                  <FileCheck2 className="w-3.5 h-3.5 text-emerald-400" />
                  <span>{selectedFile.name}</span>
                  {selectedFile.size > 0 && (
                    <span className="text-slate-400 font-sans">({(selectedFile.size / 1024).toFixed(1)} KB)</span>
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
              <form onSubmit={handleDecryptAndParse} className="p-4 sm:p-5 rounded-2xl bg-slate-950 border border-amber-500/40 space-y-3 animate-in fade-in">
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <Lock className="w-4 h-4 text-amber-400 flex-shrink-0" />
                    <span className="text-xs sm:text-sm font-bold text-slate-200">
                      PDF Password (PAN / CAS Password)
                    </span>
                  </div>
                  <span className="text-[11px] text-slate-400">
                    {activeTargetMember?.panCard ? `Active: ${activeTargetMember.name} (${activeTargetMember.panCard})` : 'Enter 10-digit PAN or custom password'}
                  </span>
                </div>

                <div className="flex flex-col sm:flex-row gap-3">
                  <div className="relative flex-1">
                    <input
                      id="cas-pdf-password-input"
                      type={showPassword ? 'text' : 'password'}
                      placeholder="e.g. ABCDE1234F or custom CAS password"
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
                        <span>Decrypting in Browser...</span>
                      </>
                    ) : (
                      <>
                        <ShieldCheck className="w-4 h-4" />
                        <span>Decrypt & Parse Statement</span>
                      </>
                    )}
                  </button>
                </div>

                {/* Quick-fill Family PAN Chips */}
                <div className="flex flex-wrap items-center gap-1.5 pt-1">
                  <span className="text-[11px] text-slate-400 font-medium">Quick PAN:</span>
                  {familyMembers.map(m => {
                    const pan = m.panCard || (m.panMasked && !m.panMasked.includes('*') ? m.panMasked : '');
                    if (!pan) return null;
                    return (
                      <button
                        key={m.id}
                        type="button"
                        onClick={() => {
                          setPdfPassword(pan);
                          if (selectedFile) {
                            processFile(selectedFile, pan, false);
                          }
                        }}
                        className={`px-2 py-0.5 rounded-md text-[11px] font-mono border transition-colors cursor-pointer ${
                          pdfPassword.toUpperCase() === pan.toUpperCase()
                            ? 'bg-amber-400/20 border-amber-400/60 text-amber-300 font-bold'
                            : 'bg-slate-900 hover:bg-slate-800 border-slate-700 text-slate-300'
                        }`}
                      >
                        {m.name.split(' ')[0]}: {pan}
                      </button>
                    );
                  })}
                </div>

                <p className="text-[11px] text-slate-400">
                  🔒 CAMS / KFintech and Demat CAS files use the primary holder's 10-character PAN in capital letters as the unlock key. Decryption runs 100% locally in your browser memory.
                </p>
              </form>
            )}

            {/* Parsing Error Box */}
            {parseError && (
              <div className="p-4 rounded-2xl bg-rose-950/90 border border-rose-800 text-rose-200 text-xs sm:text-sm flex items-start gap-3 shadow-lg">
                <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
                <div className="space-y-1 flex-1">
                  <div className="font-bold">Decryption / Parsing Error</div>
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

          {/* PARSED STATEMENT METADATA & 3-OUTCOME REVIEW SCHEDULE */}
          {importRecords.length > 0 && (
            <div className="bg-slate-900/80 rounded-3xl border border-slate-800 backdrop-blur-md shadow-lg overflow-hidden space-y-5 p-6 animate-in fade-in">
              
              {/* Statement Insights Summary Header */}
              {parseResult && (
                <div className="space-y-3">
                  <div className="p-4 rounded-2xl bg-slate-950/80 border border-slate-800 grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                    <div>
                      <span className="text-slate-500 block">Statement Type</span>
                      <span className="font-bold text-slate-200 mt-0.5 block">
                        {parseResult.statementType === 'CAMS_KFIN_MF'
                          ? 'CAMS / KFintech CAS'
                          : parseResult.statementType === 'CDSL_NSDL_DEMAT'
                          ? 'CDSL / NSDL Demat CAS'
                          : 'Broker CSV / Tradebook'}
                      </span>
                    </div>

                    <div>
                      <span className="text-slate-500 block">Target Family Member</span>
                      <span className="font-bold text-amber-400 mt-0.5 block">
                        {activeTargetMember?.name} ({activeTargetMember?.relation})
                      </span>
                    </div>

                    <div>
                      <span className="text-slate-500 block">Extracted PAN</span>
                      <span className="font-mono font-bold text-slate-200 mt-0.5 block">
                        {parseResult.investorPan || activeTargetMember?.panCard || 'N/A'}
                      </span>
                    </div>

                    <div>
                      <span className="text-slate-500 block">Total Statement Valuation</span>
                      <span className="font-bold text-emerald-400 mt-0.5 block">
                        ₹ {MoneyEngine.groupIndian(Math.round(parseResult.summary.totalValuation))}
                      </span>
                    </div>
                  </div>

                  {/* Category Breakdown Badges if Demat/MF/AIF available */}
                  {(parseResult.summary.dematValuation || parseResult.summary.mfValuation || parseResult.summary.aifValuation) && (
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 text-xs">
                      {parseResult.summary.mfValuation ? (
                        <div className="p-3 bg-indigo-950/40 border border-indigo-800/40 rounded-xl flex items-center justify-between">
                          <span className="text-indigo-300 font-semibold">Mutual Fund Folios</span>
                          <span className="font-bold text-slate-100">₹ {MoneyEngine.groupIndian(Math.round(parseResult.summary.mfValuation))}</span>
                        </div>
                      ) : null}
                      {parseResult.summary.dematValuation ? (
                        <div className="p-3 bg-sky-950/40 border border-sky-800/40 rounded-xl flex items-center justify-between">
                          <span className="text-sky-300 font-semibold">Demat Equity & ETFs</span>
                          <span className="font-bold text-slate-100">₹ {MoneyEngine.groupIndian(Math.round(parseResult.summary.dematValuation))}</span>
                        </div>
                      ) : null}
                      {parseResult.summary.aifValuation ? (
                        <div className="p-3 bg-amber-950/40 border border-amber-800/40 rounded-xl flex items-center justify-between">
                          <span className="text-amber-300 font-semibold">AIF Alternative Assets</span>
                          <span className="font-bold text-slate-100">₹ {MoneyEngine.groupIndian(Math.round(parseResult.summary.aifValuation))}</span>
                        </div>
                      ) : null}
                    </div>
                  )}
                </div>
              )}

              {/* Action Bar & Category Filters */}
              <div className="space-y-3 pb-3 border-b border-slate-800">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div>
                      <h3 className="text-base font-bold text-slate-100">Statement Review & Portfolio Sync</h3>
                      <p className="text-xs text-slate-400">
                        Review holdings & transactions to credit to <strong>{activeTargetMember?.name}</strong>'s portfolio
                      </p>
                    </div>

                    <button
                      onClick={handleToggleSelectAll}
                      className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg text-xs font-semibold border border-slate-700 cursor-pointer transition-colors"
                    >
                      {importRecords.every(r => r.selected) ? 'Deselect All' : 'Select All'}
                    </button>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={handleCleanDuplicates}
                      className="px-3 py-2 bg-slate-800/80 hover:bg-slate-800 text-slate-300 text-xs font-medium rounded-xl border border-slate-700/70 hover:border-amber-400/50 transition-colors cursor-pointer flex items-center gap-1.5"
                      title="Merge any duplicate folios and repair cost metrics across all family portfolios"
                    >
                      <RefreshCw className="w-3.5 h-3.5 text-amber-400" />
                      <span className="hidden sm:inline">Clean Duplicates</span>
                    </button>

                    <button
                      id="append-selected-ledger-btn"
                      onClick={handleExecuteImport}
                      className="px-5 py-2.5 bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs sm:text-sm font-bold rounded-xl shadow-md transition-colors cursor-pointer flex items-center gap-2"
                    >
                      <Check className="w-4 h-4 stroke-[3]" />
                      <span>Sync Selected ({importRecords.filter(r => r.selected).length} items)</span>
                    </button>
                  </div>
                </div>

                {/* Import Mode Strategy Switcher */}
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 p-2.5 bg-slate-900/90 rounded-xl border border-slate-800/80 text-xs">
                  <div className="flex items-center gap-2">
                    <span className="text-slate-400 font-medium">Sync Strategy:</span>
                    <div className="flex items-center bg-slate-950 p-0.5 rounded-lg border border-slate-800">
                      <button
                        type="button"
                        onClick={() => setImportMode('SYNC_UPDATE')}
                        className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all cursor-pointer ${
                          importMode === 'SYNC_UPDATE'
                            ? 'bg-amber-400 text-slate-950 shadow-xs'
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        Update Existing & Add New (Recommended)
                      </button>
                      <button
                        type="button"
                        onClick={() => setImportMode('REPLACE_SNAPSHOT')}
                        className={`px-2.5 py-1 rounded-md text-xs font-semibold transition-all cursor-pointer ${
                          importMode === 'REPLACE_SNAPSHOT'
                            ? 'bg-amber-400 text-slate-950 shadow-xs'
                            : 'text-slate-400 hover:text-slate-200'
                        }`}
                      >
                        Replace Previous Statement Snapshot
                      </button>
                    </div>
                  </div>
                  <span className="text-[11px] text-slate-500">
                    {importMode === 'SYNC_UPDATE'
                      ? '✓ Matches by ISIN/Folio so new files update NAV & units without doubling entries'
                      : '✓ Replaces previously imported CAS items for this member with this statement'}
                  </span>
                </div>

                {/* Filter Pills */}
                <div className="flex flex-wrap items-center gap-2 pt-1 text-xs">
                  <button
                    onClick={() => setReviewCategoryFilter('ALL')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      reviewCategoryFilter === 'ALL'
                        ? 'bg-amber-400 text-slate-950 font-bold'
                        : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    All Items ({importRecords.length})
                  </button>
                  <button
                    onClick={() => setReviewCategoryFilter('MUTUAL_FUND')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      reviewCategoryFilter === 'MUTUAL_FUND'
                        ? 'bg-amber-400 text-slate-950 font-bold'
                        : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    Mutual Funds ({importRecords.filter(r => r.category === 'MUTUAL_FUND' && r.recordType !== 'TRANSACTION').length})
                  </button>
                  <button
                    onClick={() => setReviewCategoryFilter('EQUITY')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      reviewCategoryFilter === 'EQUITY'
                        ? 'bg-amber-400 text-slate-950 font-bold'
                        : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    Equities & ETFs ({importRecords.filter(r => r.category === 'EQUITY').length})
                  </button>
                  <button
                    onClick={() => setReviewCategoryFilter('SPECIALISED')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      reviewCategoryFilter === 'SPECIALISED'
                        ? 'bg-amber-400 text-slate-950 font-bold'
                        : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    AIF ({importRecords.filter(r => r.category === 'SPECIALISED').length})
                  </button>
                  <button
                    onClick={() => setReviewCategoryFilter('TRANSACTION')}
                    className={`px-3 py-1 rounded-lg font-medium transition-colors cursor-pointer ${
                      reviewCategoryFilter === 'TRANSACTION'
                        ? 'bg-amber-400 text-slate-950 font-bold'
                        : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
                    }`}
                  >
                    Transactions ({importRecords.filter(r => r.recordType === 'TRANSACTION').length})
                  </button>
                </div>
              </div>

              {/* Transactions / Holdings List */}
              <div className="divide-y divide-slate-800 max-h-[500px] overflow-y-auto pr-1">
                {importRecords
                  .filter(rec => {
                    if (reviewCategoryFilter === 'ALL') return true;
                    if (reviewCategoryFilter === 'TRANSACTION') return rec.recordType === 'TRANSACTION';
                    if (reviewCategoryFilter === 'MUTUAL_FUND') return rec.category === 'MUTUAL_FUND' && rec.recordType !== 'TRANSACTION';
                    if (reviewCategoryFilter === 'EQUITY') return rec.category === 'EQUITY';
                    if (reviewCategoryFilter === 'SPECIALISED') return rec.category === 'SPECIALISED';
                    return true;
                  })
                  .map(rec => (
                  <div
                    key={rec.id}
                    className={`py-3.5 px-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl transition-colors ${
                      rec.selected ? 'bg-slate-800/40' : 'opacity-50'
                    }`}
                  >
                    <div className="flex items-start sm:items-center gap-3">
                      <input
                        type="checkbox"
                        checked={rec.selected}
                        onChange={e =>
                          setImportRecords(prev =>
                            prev.map(r => (r.id === rec.id ? { ...r, selected: e.target.checked } : r))
                          )
                        }
                        className="w-4 h-4 mt-1 sm:mt-0 rounded text-amber-400 focus:ring-0 cursor-pointer accent-amber-400"
                      />
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-bold text-sm text-slate-100">{rec.holdingName}</span>
                          <span
                            className={`text-[10px] font-bold px-1.5 py-0.5 rounded-md border ${
                              rec.status === 'NEW'
                                ? 'bg-emerald-950/80 text-emerald-300 border-emerald-800/60'
                                : rec.status === 'ALREADY_EXISTS'
                                ? 'bg-slate-800 text-slate-400 border border-slate-700'
                                : 'bg-amber-950/80 text-amber-300 border border-amber-800/60'
                            }`}
                          >
                            {rec.status === 'NEW' ? 'NEW RECORD' : rec.status === 'ALREADY_EXISTS' ? 'ALREADY IN LEDGER' : 'NEEDS REVIEW'}
                          </span>
                          <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-800 text-slate-300">
                            {rec.category}
                          </span>
                          {rec.isin && (
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-900 border border-slate-700 text-slate-400">
                              {rec.isin}
                            </span>
                          )}
                          {rec.folio && (
                            <span className="text-[10px] font-mono px-1.5 py-0.5 rounded bg-slate-900 border border-slate-700 text-amber-300/80">
                              Folio: {rec.folio}
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-slate-400 mt-0.5">
                          {rec.date} • <span className="font-semibold text-slate-300">{rec.action}</span> • {rec.quantity} units @ ₹ {MoneyEngine.groupIndian(rec.pricePerUnit)}
                          {rec.cumulativeInvested ? ` • Cost: ₹${MoneyEngine.groupIndian(Math.round(rec.cumulativeInvested))}` : ''}
                        </div>
                        {rec.statusMessage && (
                          <div className="text-[11px] text-slate-500 mt-0.5">
                            {rec.statusMessage}
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="text-right flex-shrink-0">
                      <div className="font-bold text-sm text-slate-100">
                        ₹ {MoneyEngine.groupIndian(Math.round(rec.totalAmount))}
                      </div>
                      <div className="text-[11px] text-slate-400">
                        Assigned to: <span className="text-amber-400 font-semibold">{activeTargetMember?.name}</span>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {/* Bottom Commit Action */}
              <div className="pt-3 border-t border-slate-800 flex flex-col sm:flex-row items-center justify-between gap-3 text-xs">
                <div className="text-slate-400">
                  Total Selected: <strong className="text-slate-200">{importRecords.filter(r => r.selected).length}</strong> of {importRecords.length} records • Totaling <strong className="text-emerald-400">₹ {MoneyEngine.groupIndian(Math.round(importRecords.filter(r => r.selected).reduce((s, r) => s + r.totalAmount, 0)))}</strong>
                </div>

                <button
                  onClick={handleExecuteImport}
                  className="w-full sm:w-auto px-6 py-2.5 bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs sm:text-sm font-bold rounded-xl shadow-md transition-colors cursor-pointer flex items-center justify-center gap-2"
                >
                  <Check className="w-4 h-4 stroke-[3]" />
                  <span>Confirm & Append into Vault</span>
                </button>
              </div>

            </div>
          )}

        </div>
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
                Estimated Tax: ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(taxSummary.stcgEquityTaxMinor))}
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
                      Bought: {rec.purchaseDate} • Sold: {rec.saleDate} ({rec.holdingDays} days) • {rec.taxType}
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
            This document is a computational record of asset transactions and holdings compiled from your local ledger records. It does not constitute formal tax advice or a filed Income Tax Return (ITR). Tax rules are subject to the Finance Act provisions for the relevant assessment year.
          </div>

        </div>
      )}

      {/* TAB 3: APPEND-ONLY RECORD BOOK */}
      {activeTab === 'RECORD_BOOK' && (
        <div className="bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg overflow-hidden space-y-4 p-6 backdrop-blur-md">
          
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-800">
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-bold text-slate-100">Append-Only Record Ledger</h3>
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
                <div key={ev.id} className="py-3 flex items-center justify-between px-2 hover:bg-slate-800/40 rounded-xl transition-colors">
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
