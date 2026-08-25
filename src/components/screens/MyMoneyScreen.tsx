// FILE: src/components/screens/MyMoneyScreen.tsx
import React, { useState, useMemo } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { Holding } from '../../types';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  ExposureNode,
  Wrapper,
  WRAPPERS,
  exposureLabel,
  wrapperLabel,
} from '../../lib/taxonomy';
import {
  activeWrappers,
  HoldingPlacement,
  TreeL1,
  TreeL2,
} from '../../lib/lookthrough';
import {
  Search,
  Plus,
  TrendingUp,
  ChevronRight,
  ChevronDown,
  Coins,
  ArrowUpRight,
  ArrowDownRight,
  Sparkles,
  Info,
  Clock,
  Tag,
  X,
  ChevronDown as DropdownIcon,
} from 'lucide-react';

interface MyMoneyScreenProps {
  onOpenAddHolding: () => void;
  onOpenAddRecord: () => void;
  onOpenHoldingDetail: (holdingId: string) => void;
}

export const MyMoneyScreen: React.FC<MyMoneyScreenProps> = ({
  onOpenAddHolding,
  onOpenAddRecord,
  onOpenHoldingDetail,
}) => {
  const {
    filteredHoldings,
    familyMembers,
    taxonomy,
    derived,
    sgbValuationMode,
    setSgbValuationMode,
    summary,
  } = useKosha();

  const [searchQuery, setSearchQuery] = useState('');
  
  // Accordion state: remember the single expanded L1 node
  const initialL1Id = useMemo(() => {
    const firstWithHoldings = taxonomy.tree.find(t => t.hasHoldings);
    return firstWithHoldings ? firstWithHoldings.id : taxonomy.tree[0]?.id || 'EQUITY';
  }, [taxonomy.tree]);

  const [expandedL1, setExpandedL1] = useState<ExposureNode>(initialL1Id);

  // Selected L2 node within the expanded L1
  const initialL2Id = useMemo(() => {
    const l1 = taxonomy.tree.find(t => t.id === expandedL1);
    const firstL2WithHoldings = l1?.l2.find(n => n.rows.length > 0);
    return firstL2WithHoldings ? firstL2WithHoldings.id : l1?.l2[0]?.id || 'LARGE_CAP';
  }, [taxonomy.tree, expandedL1]);

  const [selectedL2Id, setSelectedL2Id] = useState<string>(initialL2Id);

  // Wrapper tab filter: 'ALL' or specific wrapper ID
  const [selectedWrapper, setSelectedWrapper] = useState<string>('ALL');
  const [showMoreWrappersDropdown, setShowMoreWrappersDropdown] = useState(false);

  // Handle L1 accordion click
  const handleL1Click = (nodeId: ExposureNode) => {
    if (expandedL1 === nodeId) {
      // Toggle or keep active
      return;
    }
    setExpandedL1(nodeId);
    const targetL1 = taxonomy.tree.find(t => t.id === nodeId);
    const firstL2 = targetL1?.l2.find(n => n.rows.length > 0) || targetL1?.l2[0];
    if (firstL2) {
      setSelectedL2Id(firstL2.id);
    }
    setSelectedWrapper('ALL');
    setShowMoreWrappersDropdown(false);
  };

  // Handle L2 click
  const handleL2Click = (l1Id: ExposureNode, l2Id: string) => {
    setExpandedL1(l1Id);
    setSelectedL2Id(l2Id);
    setSelectedWrapper('ALL');
    setShowMoreWrappersDropdown(false);
  };

  // Calculate cost & change for an array of HoldingPlacement rows
  const getChangeStats = (rows: HoldingPlacement[], totalValueMinor: bigint) => {
    let costMinor = 0n;
    rows.forEach(r => {
      const qty = derived.positions.get(r.holding.id)?.quantityScaled ?? 0n;
      const costPerUnit = BigInt(r.holding.averageCostPerUnitMinor || '0');
      costMinor += (qty * costPerUnit) / QTY_SCALE;
    });

    const gainMinor = totalValueMinor - costMinor;
    let percent = 0;
    if (costMinor > 0n) {
      percent = (Number(gainMinor) / Number(costMinor)) * 100;
    }

    const isUp = gainMinor >= 0n;
    const absPercentStr = Math.abs(percent).toFixed(1);
    const formattedText = isUp ? `up ${absPercentStr}%` : `down ${absPercentStr}%`;

    return {
      costMinor,
      gainMinor,
      percent,
      isUp,
      formattedText,
      hasChange: costMinor > 0n,
    };
  };

  // Separate non-liability L1s from liability L1 ("What you owe")
  const standardL1s = useMemo(() => {
    return taxonomy.tree.filter(t => t.id !== 'LIABILITY');
  }, [taxonomy.tree]);

  const liabilityL1 = useMemo(() => {
    return taxonomy.tree.find(t => t.id === 'LIABILITY');
  }, [taxonomy.tree]);

  // Current active L1 and L2
  const currentL1 = useMemo(() => {
    return taxonomy.tree.find(t => t.id === expandedL1) || taxonomy.tree[0];
  }, [taxonomy.tree, expandedL1]);

  const currentL2 = useMemo(() => {
    return currentL1?.l2.find(n => n.id === selectedL2Id) || currentL1?.l2[0];
  }, [currentL1, selectedL2Id]);

  // Active wrappers for current L2
  const currentActiveWrappers = useMemo(() => {
    if (!currentL2) return [];
    return activeWrappers(currentL2.rows, WRAPPERS.map(w => w.id));
  }, [currentL2]);

  // Visible rows in the content panel (filtered by wrapper if not ALL)
  const contentRows = useMemo(() => {
    if (!currentL2) return [];
    if (selectedWrapper === 'ALL') {
      return currentL2.rows;
    }
    return currentL2.rows.filter(r => r.placement.wrapper === selectedWrapper);
  }, [currentL2, selectedWrapper]);

  // Current L2 change stats
  const currentL2Stats = useMemo(() => {
    if (!currentL2) return null;
    return getChangeStats(contentRows, currentL2.valueMinor);
  }, [currentL2, contentRows, derived]);

  // Global search matching
  const searchResults = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return null;

    return taxonomy.placements.filter(p => {
      const h = p.holding;
      const matchName = h.name.toLowerCase().includes(query);
      const matchSymbol = (h.symbolOrFolio || '').toLowerCase().includes(query);
      const matchSub = (h.subCategory || '').toLowerCase().includes(query);
      const matchTag = (h.tag || '').toLowerCase().includes(query);
      const matchTags = (h.tags || []).some(t => t.toLowerCase().includes(query));
      const owner = familyMembers.find(m => m.id === h.ownerId);
      const matchOwner = owner ? owner.name.toLowerCase().includes(query) : false;

      return matchName || matchSymbol || matchSub || matchTag || matchTags || matchOwner;
    });
  }, [searchQuery, taxonomy.placements, familyMembers]);

  return (
    <div className="space-y-6 pb-16">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-serif text-white tracking-tight">My Money</h1>
          <p className="text-xs sm:text-sm text-slate-400">
            Portfolio inventory, assets, and liabilities
          </p>
        </div>

        <div className="flex items-center gap-2">
          <button
            id="mymoney-add-record-btn"
            onClick={onOpenAddRecord}
            className="px-3.5 py-2 rounded-xl border border-slate-700 bg-slate-900 hover:bg-slate-800 text-slate-200 text-xs sm:text-sm font-semibold transition-colors flex items-center gap-1.5 cursor-pointer"
          >
            <TrendingUp className="w-4 h-4 text-emerald-400" />
            <span>Record Action</span>
          </button>

          <button
            id="mymoney-add-holding-btn"
            onClick={onOpenAddHolding}
            className="px-4 py-2 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs sm:text-sm font-bold transition-all shadow-md shadow-amber-400/10 flex items-center gap-1.5 cursor-pointer"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span>+ Add Holding</span>
          </button>
        </div>
      </div>

      {/* Summary Bento Header Bar */}
      <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 backdrop-blur-md shadow-lg flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-6 flex-wrap">
          <div>
            <div className="text-xs text-slate-400 font-medium">Total Assets</div>
            <PrivacyValue
              minor={summary.totalAssetsMinor}
              className="text-lg font-bold text-emerald-400"
              compactClassName="text-xs text-slate-400 font-medium ml-1"
              showCompact
            />
          </div>
          <div className="h-8 w-px bg-slate-800 hidden sm:block" />
          <div>
            <div className="text-xs text-slate-400 font-medium">Outstanding Loans</div>
            <PrivacyValue
              minor={summary.totalLiabilitiesMinor}
              className="text-lg font-bold text-rose-400"
              compactClassName="text-xs text-slate-400 font-medium ml-1"
              showCompact
            />
          </div>
          <div className="h-8 w-px bg-slate-800 hidden sm:block" />
          <div className="hidden sm:block">
            <div className="text-xs text-slate-400 font-medium">Net Worth</div>
            <PrivacyValue
              minor={summary.netWorthMinor}
              className="text-lg font-bold text-slate-100"
              compactClassName="text-xs text-amber-400/90 font-medium ml-1"
              showCompact
            />
          </div>
        </div>

        {/* SGB Valuation Switcher */}
        <div className="flex items-center gap-2 bg-amber-950/40 px-3.5 py-1.5 rounded-2xl border border-amber-700/50">
          <Coins className="w-4 h-4 text-amber-400" />
          <span className="text-xs font-semibold text-amber-300">Gold Valuation:</span>
          <div className="flex items-center bg-slate-900 rounded-xl p-0.5 border border-slate-700 text-[11px] font-semibold">
            <button
              onClick={() => setSgbValuationMode('MARKET_TRADED')}
              className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                sgbValuationMode === 'MARKET_TRADED' ? 'bg-amber-400 text-slate-950 font-bold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Traded
            </button>
            <button
              onClick={() => setSgbValuationMode('RBI_BENCHMARK')}
              className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                sgbValuationMode === 'RBI_BENCHMARK' ? 'bg-amber-400 text-slate-950 font-bold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              Benchmark
            </button>
          </div>
        </div>
      </div>

      {/* Global Search Bar */}
      <div className="relative">
        <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
        <input
          id="holdings-search-input"
          type="text"
          placeholder="Search all holdings, symbols, folios, or tags..."
          value={searchQuery}
          onChange={e => setSearchQuery(e.target.value)}
          className="w-full pl-9 pr-10 py-2.5 bg-slate-900/90 border border-slate-800 rounded-2xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50 focus:border-transparent transition-all"
        />
        {searchQuery && (
          <button
            onClick={() => setSearchQuery('')}
            className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-200 p-0.5"
          >
            <X className="w-4 h-4" />
          </button>
        )}
      </div>

      {/* MAIN TWO-AXIS CONTAINER */}
      {searchResults ? (
        /* SEARCH RESULTS OVERLAY VIEW */
        <div className="bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg overflow-hidden backdrop-blur-md">
          <div className="p-4 sm:px-6 bg-slate-950/60 border-b border-slate-800 flex items-center justify-between">
            <h2 className="text-sm font-bold text-slate-200">
              Search Results ({searchResults.length})
            </h2>
            <button
              onClick={() => setSearchQuery('')}
              className="text-xs text-amber-400 hover:underline font-medium"
            >
              Clear Search
            </button>
          </div>

          {searchResults.length === 0 ? (
            <div className="p-12 text-center text-slate-400 text-sm">
              No holdings matched "{searchQuery}".
            </div>
          ) : (
            <div className="divide-y divide-slate-800">
              {searchResults.map(p => {
                const h = p.holding;
                const owner = familyMembers.find(m => m.id === h.ownerId);
                const isLiability = p.dominantExposure === 'LIABILITY';

                return (
                  <div
                    key={h.id}
                    onClick={() => onOpenHoldingDetail(h.id)}
                    className="p-4 sm:px-6 hover:bg-slate-800/50 transition-colors cursor-pointer flex items-center justify-between gap-4 group"
                  >
                    <div className="space-y-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-sm sm:text-base text-slate-100 group-hover:text-amber-300 transition-colors">
                          {h.name}
                        </span>
                        {p.placement.inferred && (
                          <span
                            title="We placed this automatically. Tap to change."
                            className="w-2 h-2 rounded-full bg-slate-500 inline-block cursor-help"
                          />
                        )}
                        {owner && (
                          <span
                            className="text-[10px] font-semibold px-2 py-0.5 rounded-full text-white ring-1 ring-white/20"
                            style={{ backgroundColor: owner.color }}
                          >
                            {owner.name}
                          </span>
                        )}
                      </div>

                      {/* Hybrid marker */}
                      {p.split.slices.length > 1 && (
                        <div
                          title={p.split.estimated ? p.split.source : undefined}
                          className={`text-[11px] font-medium flex items-center gap-1 ${
                            p.split.estimated ? 'text-amber-400/90' : 'text-slate-400'
                          }`}
                        >
                          <span>
                            {p.split.slices
                              .map(s => `${exposureLabel(s.exposure)} ${s.basisPoints / 100}%`)
                              .join(' · ')}
                          </span>
                          {p.split.estimated && <span>(estimated)</span>}
                        </div>
                      )}

                      <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
                        <span className="font-mono text-slate-300">{h.symbolOrFolio}</span>
                        <span>•</span>
                        <span>{exposureLabel(p.dominantExposure)} &gt; {p.placement.l2}</span>
                        <span>•</span>
                        <span className="text-slate-300">
                          {MoneyEngine.formatQty(h.quantityScaled)} {h.category === 'GOLD_SGB' ? 'grams' : 'units'}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-4 text-right flex-shrink-0">
                      <div>
                        <PrivacyValue
                          minor={p.valueMinor}
                          className={`text-sm sm:text-base font-bold ${
                            isLiability ? 'text-rose-400' : 'text-slate-100'
                          }`}
                        />
                      </div>
                      <ChevronRight className="w-5 h-5 text-slate-600 group-hover:text-amber-400 group-hover:translate-x-0.5 transition-all" />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      ) : (
        /* TWO-AXIS SPLIT VIEW: LEFT RAIL + CONTENT */
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          
          {/* LEFT RAIL (Vertical Axis: L1 + Nested L2) */}
          <div className="lg:col-span-4 bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg p-3 sm:p-4 backdrop-blur-md space-y-1">
            
            {/* Standard L1 Exposure Nodes */}
            {standardL1s.map(l1 => {
              const isExpanded = expandedL1 === l1.id;
              const hasItems = l1.hasHoldings;
              
              // Calculate L1 change
              const allL1Rows = l1.l2.flatMap(n => n.rows);
              const l1Stats = getChangeStats(allL1Rows, l1.valueMinor);

              return (
                <div key={l1.id} className="rounded-2xl transition-all overflow-hidden">
                  {/* L1 Header Row */}
                  <button
                    onClick={() => handleL1Click(l1.id)}
                    className={`w-full px-3 py-2.5 rounded-2xl flex items-center justify-between text-left transition-all cursor-pointer ${
                      isExpanded
                        ? 'bg-slate-800/90 text-white font-bold shadow-xs'
                        : hasItems
                        ? 'hover:bg-slate-800/40 text-slate-200'
                        : 'text-slate-500 hover:bg-slate-800/20'
                    }`}
                  >
                    <div className="flex items-center gap-2 min-w-0 pr-2">
                      <span className={`text-xs sm:text-sm font-semibold truncate ${!hasItems ? 'text-slate-500' : ''}`}>
                        {l1.label}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 flex-shrink-0 text-right">
                      {hasItems ? (
                        <div>
                          <PrivacyValue
                            minor={l1.valueMinor}
                            className="text-xs sm:text-sm font-bold text-slate-100"
                            includePaise={false}
                          />
                          {l1Stats.hasChange && (
                            <div
                              className={`text-[10px] font-medium flex items-center justify-end gap-0.5 ${
                                l1Stats.isUp ? 'text-emerald-400' : 'text-rose-400'
                              }`}
                            >
                              {l1Stats.isUp ? (
                                <ArrowUpRight className="w-2.5 h-2.5 stroke-[2.5]" />
                              ) : (
                                <ArrowDownRight className="w-2.5 h-2.5 stroke-[2.5]" />
                              )}
                              <span>{l1Stats.formattedText}</span>
                            </div>
                          )}
                        </div>
                      ) : (
                        <span className="text-xs text-slate-600 font-mono">--</span>
                      )}

                      <div className="text-slate-500 pl-1">
                        {isExpanded ? (
                          <ChevronDown className="w-4 h-4 text-amber-400" />
                        ) : (
                          <ChevronRight className="w-4 h-4" />
                        )}
                      </div>
                    </div>
                  </button>

                  {/* Nested L2 Rows (Accordion child list) */}
                  {isExpanded && (
                    <div className="pl-4 pr-1 py-1 space-y-0.5 border-l-2 border-slate-800 ml-3.5 my-1">
                      {l1.l2.map(l2 => {
                        const isL2Selected = selectedL2Id === l2.id;
                        const hasL2Items = l2.rows.length > 0;
                        const l2Stats = getChangeStats(l2.rows, l2.valueMinor);

                        return (
                          <button
                            key={l2.id}
                            onClick={() => handleL2Click(l1.id, l2.id)}
                            className={`w-full px-3 py-2 rounded-xl flex items-center justify-between text-left transition-all cursor-pointer ${
                              isL2Selected
                                ? 'bg-amber-400 text-slate-950 font-bold shadow-xs'
                                : hasL2Items
                                ? 'hover:bg-slate-800/60 text-slate-300'
                                : 'text-slate-500 hover:bg-slate-800/20'
                            }`}
                          >
                            <span className="text-xs truncate pr-2">{l2.label}</span>

                            <div className="flex items-center gap-2 flex-shrink-0 text-right">
                              {hasL2Items ? (
                                <div>
                                  <PrivacyValue
                                    minor={l2.valueMinor}
                                    className={`text-xs font-bold ${
                                      isL2Selected ? 'text-slate-950' : 'text-slate-200'
                                    }`}
                                    includePaise={false}
                                  />
                                  {l2Stats.hasChange && (
                                    <div
                                      className={`text-[9px] font-semibold flex items-center justify-end gap-0.5 ${
                                        isL2Selected
                                          ? 'text-slate-900'
                                          : l2Stats.isUp
                                          ? 'text-emerald-400'
                                          : 'text-rose-400'
                                      }`}
                                    >
                                      {l2Stats.isUp ? '↑' : '↓'} {l2Stats.formattedText}
                                    </div>
                                  )}
                                </div>
                              ) : (
                                <span
                                  className={`text-xs font-mono ${
                                    isL2Selected ? 'text-slate-800' : 'text-slate-600'
                                  }`}
                                >
                                  --
                                </span>
                              )}
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}

            {/* Separator before Liability */}
            <div className="pt-2">
              <div className="border-t border-slate-800 my-2" />
            </div>

            {/* Liability L1 ("What you owe") */}
            {liabilityL1 && (() => {
              const isExpanded = expandedL1 === 'LIABILITY';
              const hasItems = liabilityL1.hasHoldings;

              return (
                <div className="rounded-2xl transition-all overflow-hidden">
                  <button
                    onClick={() => handleL1Click('LIABILITY')}
                    className={`w-full px-3 py-2.5 rounded-2xl flex items-center justify-between text-left transition-all cursor-pointer ${
                      isExpanded
                        ? 'bg-slate-800/90 text-white font-bold shadow-xs'
                        : hasItems
                        ? 'hover:bg-slate-800/40 text-slate-200'
                        : 'text-slate-500 hover:bg-slate-800/20'
                    }`}
                  >
                    <div className="flex items-center gap-2 min-w-0 pr-2">
                      <span className="text-xs sm:text-sm font-semibold truncate text-rose-300">
                        {liabilityL1.label}
                      </span>
                    </div>

                    <div className="flex items-center gap-2 flex-shrink-0 text-right">
                      {hasItems ? (
                        <div className="text-rose-400 font-bold text-xs sm:text-sm flex items-center justify-end">
                          <span>-</span>
                          <PrivacyValue
                            minor={liabilityL1.valueMinor}
                            className="text-xs sm:text-sm font-bold text-rose-400"
                            includePaise={false}
                          />
                        </div>
                      ) : (
                        <span className="text-xs text-slate-600 font-mono">--</span>
                      )}

                      <div className="text-slate-500 pl-1">
                        {isExpanded ? (
                          <ChevronDown className="w-4 h-4 text-rose-400" />
                        ) : (
                          <ChevronRight className="w-4 h-4" />
                        )}
                      </div>
                    </div>
                  </button>

                  {/* Nested Liability L2 */}
                  {isExpanded && (
                    <div className="pl-4 pr-1 py-1 space-y-0.5 border-l-2 border-rose-950/60 ml-3.5 my-1">
                      {liabilityL1.l2.map(l2 => {
                        const isL2Selected = selectedL2Id === l2.id;
                        const hasL2Items = l2.rows.length > 0;

                        return (
                          <button
                            key={l2.id}
                            onClick={() => handleL2Click('LIABILITY', l2.id)}
                            className={`w-full px-3 py-2 rounded-xl flex items-center justify-between text-left transition-all cursor-pointer ${
                              isL2Selected
                                ? 'bg-rose-500 text-white font-bold shadow-xs'
                                : hasL2Items
                                ? 'hover:bg-slate-800/60 text-slate-300'
                                : 'text-slate-500 hover:bg-slate-800/20'
                            }`}
                          >
                            <span className="text-xs truncate pr-2">{l2.label}</span>

                            <div className="flex items-center gap-1 flex-shrink-0 text-right">
                              {hasL2Items ? (
                                <div className="text-xs font-bold flex items-center">
                                  <span>-</span>
                                  <PrivacyValue
                                    minor={l2.valueMinor}
                                    className={`text-xs font-bold ${
                                      isL2Selected ? 'text-white' : 'text-rose-300'
                                    }`}
                                    includePaise={false}
                                  />
                                </div>
                              ) : (
                                <span
                                  className={`text-xs font-mono ${
                                    isL2Selected ? 'text-rose-200' : 'text-slate-600'
                                  }`}
                                >
                                  --
                                </span>
                              )}
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })()}

          </div>

          {/* RIGHT CONTENT PANEL */}
          <div className="lg:col-span-8 space-y-4">
            
            {/* Top Navigation Bar: Breadcrumb + Horizontal L3 Tabs */}
            <div className="bg-slate-900/80 rounded-3xl p-5 border border-slate-800 shadow-lg backdrop-blur-md space-y-4">
              
              {/* Breadcrumb */}
              <div className="flex items-center gap-2 text-xs sm:text-sm font-medium text-slate-400">
                <button
                  onClick={() => handleL1Click(expandedL1)}
                  className="hover:text-amber-400 transition-colors text-slate-300 font-semibold cursor-pointer"
                >
                  {currentL1?.label}
                </button>
                <span>&gt;</span>
                <span className="text-amber-400 font-bold">
                  {currentL2?.label}
                </span>
              </div>

              {/* Horizontal Tabs (Rule 6: "All" first, only active tabs, +N more if > 5) */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <button
                  onClick={() => setSelectedWrapper('ALL')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                    selectedWrapper === 'ALL'
                      ? 'bg-amber-400 text-slate-950 font-bold shadow-xs'
                      : 'bg-slate-950/60 border border-slate-800 text-slate-300 hover:bg-slate-800 hover:text-white'
                  }`}
                >
                  All ({currentL2?.rows.length || 0})
                </button>

                {currentActiveWrappers.slice(0, 5).map(wId => {
                  const isSelected = selectedWrapper === wId;
                  const count = currentL2?.rows.filter(r => r.placement.wrapper === wId).length || 0;
                  return (
                    <button
                      key={wId}
                      onClick={() => setSelectedWrapper(wId)}
                      className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                        isSelected
                          ? 'bg-amber-400 text-slate-950 font-bold shadow-xs'
                          : 'bg-slate-950/60 border border-slate-800 text-slate-300 hover:bg-slate-800 hover:text-white'
                      }`}
                    >
                      {wrapperLabel(wId)} ({count})
                    </button>
                  );
                })}

                {/* +N More Menu for > 5 active wrappers */}
                {currentActiveWrappers.length > 5 && (
                  <div className="relative">
                    <button
                      onClick={() => setShowMoreWrappersDropdown(prev => !prev)}
                      className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-950/60 border border-slate-800 text-slate-300 hover:bg-slate-800 flex items-center gap-1 cursor-pointer"
                    >
                      <span>+{currentActiveWrappers.length - 5} more</span>
                      <DropdownIcon className="w-3 h-3" />
                    </button>

                    {showMoreWrappersDropdown && (
                      <div className="absolute left-0 mt-1.5 w-48 bg-slate-900 border border-slate-700 rounded-2xl shadow-xl z-20 p-1.5 space-y-1">
                        {currentActiveWrappers.slice(5).map(wId => {
                          const isSelected = selectedWrapper === wId;
                          const count = currentL2?.rows.filter(r => r.placement.wrapper === wId).length || 0;
                          return (
                            <button
                              key={wId}
                              onClick={() => {
                                setSelectedWrapper(wId);
                                setShowMoreWrappersDropdown(false);
                              }}
                              className={`w-full text-left px-3 py-1.5 rounded-xl text-xs font-medium flex items-center justify-between cursor-pointer ${
                                isSelected
                                  ? 'bg-amber-400 text-slate-950 font-bold'
                                  : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                              }`}
                            >
                              <span>{wrapperLabel(wId)}</span>
                              <span className="text-[10px] opacity-80">({count})</span>
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                )}
              </div>

              {/* Sub-header with Total and Change */}
              <div className="pt-2 border-t border-slate-800/80 flex items-baseline justify-between flex-wrap gap-2">
                <div>
                  <div className="text-xs text-slate-400 font-medium">
                    {expandedL1 === 'LIABILITY' ? 'Total Outstanding' : 'Subtotal'}
                  </div>
                  <div className="flex items-baseline gap-3">
                    <PrivacyValue
                      minor={currentL2?.valueMinor ?? 0n}
                      className={`text-xl sm:text-2xl font-bold ${
                        expandedL1 === 'LIABILITY' ? 'text-rose-400' : 'text-slate-100'
                      }`}
                    />
                    {currentL2Stats && currentL2Stats.hasChange && expandedL1 !== 'LIABILITY' && (
                      <div
                        className={`text-xs sm:text-sm font-semibold flex items-center gap-1 ${
                          currentL2Stats.isUp ? 'text-emerald-400' : 'text-rose-400'
                        }`}
                      >
                        {currentL2Stats.isUp ? (
                          <ArrowUpRight className="w-4 h-4 stroke-[2.5]" />
                        ) : (
                          <ArrowDownRight className="w-4 h-4 stroke-[2.5]" />
                        )}
                        <span>{currentL2Stats.formattedText}</span>
                      </div>
                    )}
                  </div>
                </div>
              </div>

            </div>

            {/* Holdings List for this Node */}
            <div className="bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg overflow-hidden backdrop-blur-md">
              {contentRows.length === 0 ? (
                <div className="p-12 text-center space-y-3">
                  <div className="text-slate-400 text-sm">
                    No holdings in <span className="font-semibold text-slate-300">{currentL2?.label}</span>
                    {selectedWrapper !== 'ALL' && ` under ${wrapperLabel(selectedWrapper as Wrapper)}`}.
                  </div>
                  <button
                    onClick={onOpenAddHolding}
                    className="px-4 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold rounded-xl shadow-md shadow-amber-400/10 cursor-pointer"
                  >
                    + Add Holding
                  </button>
                </div>
              ) : (
                <div>
                  <div className="divide-y divide-slate-800">
                    {contentRows.map(p => {
                      const h = p.holding;
                      const owner = familyMembers.find(m => m.id === h.ownerId);
                      const isLiability = expandedL1 === 'LIABILITY';

                      // Holding price and stats
                      const qtyScaled = BigInt(h.quantityScaled || '0');
                      const costPerUnitMinor = BigInt(h.averageCostPerUnitMinor || '0');
                      let currentPriceMinor = BigInt(h.currentPriceMinor || '0');
                      if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
                        currentPriceMinor = BigInt(h.benchmarkPriceMinor);
                      }

                      const totalValueMinor = (qtyScaled * currentPriceMinor) / QTY_SCALE;
                      const totalCostMinor = (qtyScaled * costPerUnitMinor) / QTY_SCALE;
                      const gainMinor = totalValueMinor - totalCostMinor;
                      const gainPercent =
                        totalCostMinor > 0n ? (Number(gainMinor) / Number(totalCostMinor)) * 100 : 0;
                      const gainFormatted = MoneyEngine.formatChange(gainMinor, gainPercent);

                      return (
                        <div
                          key={h.id}
                          onClick={() => onOpenHoldingDetail(h.id)}
                          className="p-4 sm:px-6 hover:bg-slate-800/50 transition-colors cursor-pointer flex flex-col sm:flex-row sm:items-center justify-between gap-3 group"
                        >
                          {/* Left: Name, Inference marker, Hybrid marker, Tags */}
                          <div className="space-y-1 sm:max-w-[55%]">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="font-semibold text-sm sm:text-base text-slate-100 group-hover:text-amber-300 transition-colors">
                                {h.name}
                              </span>

                              {/* Rule 9: Inference Marker */}
                              {p.placement.inferred && (
                                <span
                                  title="We placed this automatically. Tap to change."
                                  className="w-2 h-2 rounded-full bg-slate-500 inline-block cursor-help"
                                />
                              )}

                              {owner && (
                                <span
                                  className="text-[10px] font-semibold px-2 py-0.5 rounded-full text-white ring-1 ring-white/20"
                                  style={{ backgroundColor: owner.color }}
                                >
                                  {owner.name}
                                </span>
                              )}

                              {h.category === 'GOLD_SGB' && (
                                <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-md bg-amber-950/80 text-amber-300 border border-amber-700/60">
                                  2.5% p.a.
                                </span>
                              )}
                            </div>

                            {/* Rule 8: Hybrid Marker */}
                            {p.split.slices.length > 1 && (
                              <div
                                title={p.split.estimated ? p.split.source : undefined}
                                className={`text-[11px] font-medium flex items-center gap-1 ${
                                  p.split.estimated ? 'text-amber-400/90' : 'text-slate-400'
                                }`}
                              >
                                <span>
                                  {p.split.slices
                                    .map(s => `${exposureLabel(s.exposure)} ${s.basisPoints / 100}%`)
                                    .join(' · ')}
                                </span>
                                {p.split.estimated && <span>(estimated)</span>}
                              </div>
                            )}

                            {/* Details: Symbol, Wrapper, Quantity */}
                            <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
                              <span className="font-mono text-slate-300">{h.symbolOrFolio}</span>
                              <span>•</span>
                              <span className="px-1.5 py-0.5 rounded-md bg-slate-800 text-slate-300 text-[10px] font-medium border border-slate-700/80">
                                {wrapperLabel(p.placement.wrapper)}
                              </span>
                              <span>•</span>
                              <span className="text-slate-300">
                                {MoneyEngine.formatQty(h.quantityScaled)} {h.category === 'GOLD_SGB' ? 'grams' : 'units'}
                              </span>
                            </div>

                            {/* Price source */}
                            <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                              <Clock className="w-3 h-3 text-slate-500" />
                              <span>{h.priceSource}</span>
                            </div>
                          </div>

                          {/* Right: Valuation and Return */}
                          <div className="flex items-center justify-between sm:justify-end gap-6 text-right">
                            <div>
                              <div className="text-xs text-slate-400">Unit Price</div>
                              <div className="text-sm font-semibold text-slate-100">
                                ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(currentPriceMinor))}
                              </div>
                              <div className="text-[11px] text-slate-500">
                                Avg: ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(costPerUnitMinor))}
                              </div>
                            </div>

                            <div className="min-w-[120px]">
                              <div className="text-xs text-slate-400">
                                {isLiability ? 'Principal Due' : 'Current Value'}
                              </div>
                              <PrivacyValue
                                minor={totalValueMinor}
                                className={`text-sm sm:text-base font-bold ${
                                  isLiability ? 'text-rose-400' : 'text-slate-100'
                                }`}
                                includePaise={false}
                              />
                              {!isLiability && totalCostMinor > 0n && (
                                <div
                                  className={`text-xs font-semibold ${
                                    gainFormatted.isPositive ? 'text-emerald-400' : 'text-rose-400'
                                  }`}
                                >
                                  {gainFormatted.fullText}
                                </div>
                              )}
                            </div>

                            <ChevronRight className="w-5 h-5 text-slate-600 group-hover:text-amber-400 group-hover:translate-x-0.5 transition-all flex-shrink-0" />
                          </div>
                        </div>
                      );
                    })}
                  </div>

                  {/* Bottom count */}
                  <div className="p-3 sm:px-6 bg-slate-950/60 border-t border-slate-800 text-xs text-slate-500 font-medium text-right">
                    {contentRows.length} holding{contentRows.length !== 1 ? 's' : ''}
                  </div>
                </div>
              )}
            </div>

          </div>

        </div>
      )}

    </div>
  );
};
