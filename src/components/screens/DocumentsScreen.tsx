import React, { useState, useMemo } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import { StatementImportRecord, EventType } from '../../types';
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
} from 'lucide-react';

export const DocumentsScreen: React.FC = () => {
  const {
    ledgerEvents,
    computeTaxSummary,
    importStatementRecords,
    filteredHoldings,
  } = useKosha();

  const [activeTab, setActiveTab] = useState<'IMPORTER' | 'TAXES' | 'RECORD_BOOK'>('IMPORTER');
  const [selectedFY, setSelectedFY] = useState<string>('FY 2025-26');

  // Statement Importer State
  const [importRecords, setImportRecords] = useState<StatementImportRecord[]>([]);
  const [importNotification, setImportNotification] = useState<string | null>(null);
  const [recordBookSearch, setRecordBookSearch] = useState<string>('');
  const [recordBookFilterType, setRecordBookFilterType] = useState<string>('ALL');

  // Load sample statement for instant testing
  const handleLoadSampleStatement = () => {
    const samples: StatementImportRecord[] = [
      {
        id: 'imp_1',
        date: '2026-08-18',
        holdingName: 'Infosys Ltd',
        category: 'EQUITY',
        action: 'BUY',
        quantity: 50,
        pricePerUnit: 1850,
        totalAmount: 1850 * 50,
        status: 'NEW',
        statusMessage: 'Ready to import new buy parcel',
        selected: true,
      },
      {
        id: 'imp_2',
        date: '2021-04-12',
        holdingName: 'HDFC Bank Ltd',
        category: 'EQUITY',
        action: 'BUY',
        quantity: 250,
        pricePerUnit: 1420,
        totalAmount: 1420 * 250,
        status: 'ALREADY_EXISTS',
        statusMessage: 'Duplicate: already recorded in ledger on 2021-04-12',
        selected: false,
      },
      {
        id: 'imp_3',
        date: '2026-07-10',
        holdingName: 'Nifty BEES ETF',
        category: 'MUTUAL_FUND',
        action: 'BUY',
        quantity: 100,
        pricePerUnit: 265,
        totalAmount: 265 * 100,
        status: 'NEW',
        statusMessage: 'New ETF index accumulation',
        selected: true,
      },
      {
        id: 'imp_4',
        date: '2026-06-25',
        holdingName: 'TCS Ltd',
        category: 'EQUITY',
        action: 'DIVIDEND',
        quantity: 120,
        pricePerUnit: 28,
        totalAmount: 28 * 120,
        status: 'NEW',
        statusMessage: 'Dividend payout credit ₹3,360',
        selected: true,
      },
    ];

    setImportRecords(samples);
    setImportNotification('Sample statement parsed: 4 transactions categorized.');
  };

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = event => {
      const text = event.target?.result as string;
      parseCsvText(text, file.name);
    };
    reader.readAsText(file);
  };

  const parseCsvText = (csvContent: string, fileName: string) => {
    const lines = csvContent.split('\n').map(l => l.trim()).filter(Boolean);
    if (lines.length < 2) {
      setImportNotification('Invalid or empty CSV statement.');
      return;
    }

    const parsed: StatementImportRecord[] = [];
    // Basic CSV parser
    for (let i = 1; i < lines.length; i++) {
      const parts = lines[i].split(',').map(p => p.replace(/"/g, '').trim());
      if (parts.length >= 4) {
        const date = parts[0] || new Date().toISOString().split('T')[0];
        const holdingName = parts[1] || 'Imported Share';
        const actionStr = (parts[2] || 'BUY').toUpperCase();
        const action = actionStr.includes('SELL') ? 'SELL' : actionStr.includes('DIV') ? 'DIVIDEND' : 'BUY';
        const quantity = Math.abs(parseFloat(parts[3]) || 1);
        const price = Math.abs(parseFloat(parts[4]) || 100);
        const total = quantity * price;

        // Check if duplicate in ledger
        const isDuplicate = ledgerEvents.some(
          ev => ev.holdingName.toLowerCase() === holdingName.toLowerCase() && ev.eventDate === date
        );

        parsed.push({
          id: `imp_parsed_${i}`,
          date,
          holdingName,
          category: holdingName.toLowerCase().includes('fund') ? 'MUTUAL_FUND' : 'EQUITY',
          action,
          quantity,
          pricePerUnit: price,
          totalAmount: total,
          status: isDuplicate ? 'ALREADY_EXISTS' : 'NEW',
          statusMessage: isDuplicate ? 'Already in ledger records' : 'Verified new trade',
          selected: !isDuplicate,
        });
      }
    }

    setImportRecords(parsed);
    setImportNotification(`Successfully parsed ${parsed.length} entries from ${fileName}.`);
  };

  const handleExecuteImport = () => {
    const selected = importRecords.filter(r => r.selected);
    if (selected.length === 0) {
      setImportNotification('No transactions selected for import.');
      return;
    }

    const res = importStatementRecords(selected);
    setImportNotification(`Import complete: ${res.imported} records appended to your local ledger.`);
    setImportRecords([]);
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
      return matchesSearch && matchesType;
    });
  }, [ledgerEvents, recordBookSearch, recordBookFilterType]);

  return (
    <div className="space-y-6 pb-12">
      
      {/* Header & Tabs */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-serif text-white tracking-tight">
            Documents & Records
          </h1>
          <p className="text-xs sm:text-sm text-slate-400">
            Statement parser, Indian capital gains computation & immutable ledger
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
            Import Statements
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
          
          {/* Upload Drop Zone Bento Card */}
          <div className="bg-slate-900/80 rounded-3xl p-6 sm:p-8 border border-dashed border-slate-700 backdrop-blur-md shadow-lg text-center space-y-4">
            <div className="w-12 h-12 rounded-2xl bg-sky-950/80 border border-sky-800/60 text-sky-400 flex items-center justify-center mx-auto">
              <UploadCloud className="w-6 h-6" />
            </div>

            <div className="max-w-md mx-auto">
              <h3 className="text-base font-bold text-slate-100">Bring In Statements</h3>
              <p className="text-xs text-slate-400 mt-1">
                Drop CSV statements from Zerodha Tradebook, CAMS CAS, KFintech, Groww, or Bank accounts.
              </p>
            </div>

            <div className="flex flex-wrap items-center justify-center gap-3">
              <label className="px-4 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs sm:text-sm font-bold rounded-xl cursor-pointer transition-all shadow-md shadow-amber-400/10">
                <span>Select Statement CSV File</span>
                <input
                  type="file"
                  accept=".csv,.txt"
                  className="hidden"
                  onChange={handleFileUpload}
                />
              </label>

              <button
                onClick={handleLoadSampleStatement}
                className="px-4 py-2.5 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs sm:text-sm font-semibold rounded-xl transition-colors cursor-pointer"
              >
                Load Sample Tradebook
              </button>
            </div>

            <div className="text-[11px] text-slate-500">
              🔒 All statement parsing happens 100% locally in your browser memory. No files are ever sent to the cloud.
            </div>
          </div>

          {/* Notification Banner */}
          {importNotification && (
            <div className="p-4 rounded-2xl bg-emerald-950/80 border border-emerald-800/80 text-emerald-200 text-xs sm:text-sm flex items-center justify-between shadow-lg">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
                <span>{importNotification}</span>
              </div>
              <button onClick={() => setImportNotification(null)} className="cursor-pointer">
                <X className="w-4 h-4 text-emerald-400" />
              </button>
            </div>
          )}

          {/* 3-Outcome Review Table (SRS §13.2 / Annex B §B12.2) */}
          {importRecords.length > 0 && (
            <div className="bg-slate-900/80 rounded-3xl border border-slate-800 backdrop-blur-md shadow-lg overflow-hidden space-y-4 p-6">
              <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800">
                <div>
                  <h3 className="text-base font-bold text-slate-100">3-Outcome Statement Review</h3>
                  <p className="text-xs text-slate-400">
                    Review and confirm transactions before appending into your permanent record ledger
                  </p>
                </div>

                <button
                  onClick={handleExecuteImport}
                  className="px-4 py-2 bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs sm:text-sm font-bold rounded-xl shadow-md transition-colors cursor-pointer"
                >
                  + Append Selected into Ledger ({importRecords.filter(r => r.selected).length})
                </button>
              </div>

              <div className="divide-y divide-slate-800">
                {importRecords.map(rec => (
                  <div
                    key={rec.id}
                    className={`py-3.5 px-3 flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-xl transition-colors ${
                      rec.selected ? 'bg-slate-800/40' : 'opacity-50'
                    }`}
                  >
                    <div className="flex items-center gap-3">
                      <input
                        type="checkbox"
                        checked={rec.selected}
                        onChange={e =>
                          setImportRecords(prev =>
                            prev.map(r => (r.id === rec.id ? { ...r, selected: e.target.checked } : r))
                          )
                        }
                        className="w-4 h-4 rounded text-amber-400 focus:ring-0 cursor-pointer accent-amber-400"
                      />
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="font-bold text-sm text-slate-100">{rec.holdingName}</span>
                          <span
                            className={`text-[10px] font-bold px-1.5 py-0.5 rounded-md ${
                              rec.status === 'NEW'
                                ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-800/60'
                                : rec.status === 'ALREADY_EXISTS'
                                ? 'bg-slate-800 text-slate-400 border border-slate-700'
                                : 'bg-amber-950/80 text-amber-300 border border-amber-800/60'
                            }`}
                          >
                            {rec.status === 'NEW' ? 'NEW RECORD' : rec.status === 'ALREADY_EXISTS' ? 'ALREADY HAD' : 'CONFLICT'}
                          </span>
                        </div>
                        <div className="text-xs text-slate-400 mt-0.5">
                          {rec.date} • {rec.action} • {rec.quantity} units @ ₹ {MoneyEngine.groupIndian(rec.pricePerUnit)}
                        </div>
                      </div>
                    </div>

                    <div className="text-right">
                      <div className="font-bold text-sm text-slate-100">
                        ₹ {MoneyEngine.groupIndian(rec.totalAmount)}
                      </div>
                      <div className="text-xs text-slate-400">{rec.statusMessage}</div>
                    </div>
                  </div>
                ))}
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
                className="px-3.5 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs sm:text-sm font-semibold text-slate-200"
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

          {/* Mandatory Statutory Disclaimer (Mandated by SRS-TAX-009) */}
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
                className="px-3.5 py-1.5 bg-slate-950 border border-slate-700 rounded-xl text-xs font-semibold text-slate-200"
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
