import React, { useState, useMemo } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, AssetCategory, Holding } from '../../types';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  Search,
  Filter,
  Plus,
  ArrowRight,
  TrendingUp,
  Coins,
  Shield,
  CreditCard,
  Building,
  Edit3,
  ExternalLink,
  ChevronRight,
  Clock,
  Sparkles,
  Tag,
  Target,
  User,
  X,
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
    selectedMemberId,
    sgbValuationMode,
    setSgbValuationMode,
    summary,
  } = useKosha();

  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<AssetCategory | 'ALL'>('ALL');
  const [selectedTag, setSelectedTag] = useState<string | 'ALL'>('ALL');

  // Extract all unique tags present across filtered holdings with their counts
  const availableTagsWithCounts = useMemo(() => {
    const countsMap = new Map<string, number>();

    filteredHoldings.forEach(h => {
      const itemTags = new Set<string>();
      if (h.tag) itemTags.add(h.tag);
      if (h.tags && Array.isArray(h.tags)) {
        h.tags.forEach(t => {
          if (t && t.trim()) itemTags.add(t.trim());
        });
      }

      itemTags.forEach(t => {
        countsMap.set(t, (countsMap.get(t) || 0) + 1);
      });
    });

    return Array.from(countsMap.entries())
      .sort((a, b) => b[1] - a[1]) // Sort by most frequent
      .map(([tag, count]) => ({ tag, count }));
  }, [filteredHoldings]);

  // Filtered & grouped holdings
  const visibleHoldings = useMemo(() => {
    return filteredHoldings.filter(h => {
      // Category filter
      const matchesCategory = selectedCategory === 'ALL' || h.category === selectedCategory;

      // Tag filter
      let matchesTag = true;
      if (selectedTag !== 'ALL') {
        const itemTags = [h.tag, ...(h.tags || [])].filter(Boolean) as string[];
        const owner = familyMembers.find(m => m.id === h.ownerId);
        const ownerName = owner?.name || '';
        const ownerRelation = owner?.relation || '';

        matchesTag =
          itemTags.some(t => t.toLowerCase() === selectedTag.toLowerCase()) ||
          (ownerName && selectedTag.toLowerCase().includes(ownerName.toLowerCase())) ||
          (ownerRelation && selectedTag.toLowerCase().includes(ownerRelation.toLowerCase()));
      }

      // Search filter
      const searchLower = searchQuery.toLowerCase().trim();
      const matchesSearch =
        searchLower === '' ||
        h.name.toLowerCase().includes(searchLower) ||
        h.symbolOrFolio.toLowerCase().includes(searchLower) ||
        (h.subCategory && h.subCategory.toLowerCase().includes(searchLower)) ||
        (h.tag && h.tag.toLowerCase().includes(searchLower)) ||
        (h.tags && h.tags.some(t => t.toLowerCase().includes(searchLower)));

      return matchesCategory && matchesTag && matchesSearch;
    });
  }, [filteredHoldings, selectedCategory, selectedTag, searchQuery, familyMembers]);

  // Group by category
  const groupedByCategory = useMemo(() => {
    const map = new Map<AssetCategory, Holding[]>();
    
    // Maintain category ordering
    const order: AssetCategory[] = [
      'EQUITY',
      'MUTUAL_FUND',
      'GOLD_SGB',
      'FIXED_INCOME',
      'REAL_ESTATE',
      'SPECIALISED',
      'CASH_BANK',
      'LIABILITY',
    ];

    order.forEach(cat => map.set(cat, []));

    visibleHoldings.forEach(h => {
      const list = map.get(h.category) || [];
      list.push(h);
      map.set(h.category, list);
    });

    return Array.from(map.entries()).filter(([_, list]) => list.length > 0);
  }, [visibleHoldings]);

  const categoriesWithCounts = useMemo(() => {
    const counts: Record<string, number> = { ALL: filteredHoldings.length };
    filteredHoldings.forEach(h => {
      counts[h.category] = (counts[h.category] || 0) + 1;
    });
    return counts;
  }, [filteredHoldings]);

  // Helper to determine tag style / icon
  const getTagBadgeStyle = (tag: string) => {
    const lower = tag.toLowerCase();
    if (lower.includes('retire')) return 'bg-emerald-950/80 text-emerald-300 border-emerald-700/50';
    if (lower.includes('child') || lower.includes('edu')) return 'bg-sky-950/80 text-sky-300 border-sky-700/50';
    if (lower.includes('emerg')) return 'bg-amber-950/80 text-amber-300 border-amber-700/50';
    if (lower.includes('house') || lower.includes('home')) return 'bg-purple-950/80 text-purple-300 border-purple-700/50';
    if (lower.includes('tax') || lower.includes('80c')) return 'bg-indigo-950/80 text-indigo-300 border-indigo-700/50';
    if (lower.includes('wealth') || lower.includes('compounding')) return 'bg-teal-950/80 text-teal-300 border-teal-700/50';
    return 'bg-slate-800 text-slate-300 border-slate-700';
  };

  return (
    <div className="space-y-6 pb-12">
      
      {/* Header with Title & Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold font-serif text-white tracking-tight">My Money</h1>
          <p className="text-xs sm:text-sm text-slate-400">
            Complete inventory of Indian holdings, purchase records & loans
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

      {/* Bento Overview Balance Bar */}
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
            <div className="text-xs text-slate-400 font-medium">Net Wealth</div>
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
          <span className="text-xs font-semibold text-amber-300">SGB Valuation:</span>
          <div className="flex items-center bg-slate-900 rounded-xl p-0.5 border border-slate-700 text-[11px] font-semibold">
            <button
              onClick={() => setSgbValuationMode('MARKET_TRADED')}
              className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                sgbValuationMode === 'MARKET_TRADED' ? 'bg-amber-400 text-slate-950 font-bold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              NSE Traded
            </button>
            <button
              onClick={() => setSgbValuationMode('RBI_BENCHMARK')}
              className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                sgbValuationMode === 'RBI_BENCHMARK' ? 'bg-amber-400 text-slate-950 font-bold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              RBI Gold Rate
            </button>
          </div>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="space-y-3">
        <div className="flex flex-col sm:flex-row gap-3">
          {/* Search */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              id="holdings-search-input"
              type="text"
              placeholder="Search holding name, symbol, folio, or tag..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2.5 bg-slate-900/90 border border-slate-800 rounded-2xl text-sm text-slate-100 placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-amber-400/50 focus:border-transparent transition-all"
            />
          </div>

          {/* Category Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 max-w-full">
            <button
              onClick={() => setSelectedCategory('ALL')}
              className={`px-3.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                selectedCategory === 'ALL'
                  ? 'bg-amber-400 text-slate-950 font-bold shadow-sm'
                  : 'bg-slate-900 border border-slate-800 text-slate-300 hover:bg-slate-800 hover:text-white'
              }`}
            >
              All ({categoriesWithCounts.ALL || 0})
            </button>

            {(Object.keys(ASSET_CATEGORIES) as AssetCategory[]).map(cat => {
              const info = ASSET_CATEGORIES[cat];
              const count = categoriesWithCounts[cat] || 0;
              if (count === 0 && selectedCategory !== cat) return null;
              return (
                <button
                  key={cat}
                  onClick={() => setSelectedCategory(cat)}
                  className={`px-3.5 py-2 rounded-xl text-xs font-semibold whitespace-nowrap transition-all cursor-pointer ${
                    selectedCategory === cat
                      ? 'bg-amber-400 text-slate-950 font-bold shadow-sm'
                      : 'bg-slate-900 border border-slate-800 text-slate-300 hover:bg-slate-800 hover:text-white'
                  }`}
                >
                  {info.shortName} ({count})
                </button>
              );
            })}
          </div>
        </div>

        {/* Tag & Goal Filter Pills Bar */}
        {availableTagsWithCounts.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pb-1.5 pt-1 text-xs scrollbar-none">
            <div className="flex items-center gap-1 text-slate-400 font-medium whitespace-nowrap px-1">
              <Tag className="w-3.5 h-3.5 text-amber-400" />
              <span>Tags / Goals:</span>
            </div>

            <button
              onClick={() => setSelectedTag('ALL')}
              className={`px-3 py-1.5 rounded-xl font-semibold whitespace-nowrap transition-all cursor-pointer flex items-center gap-1 ${
                selectedTag === 'ALL'
                  ? 'bg-slate-200 text-slate-950 font-bold shadow-xs'
                  : 'bg-slate-900 border border-slate-800 text-slate-400 hover:text-slate-200 hover:bg-slate-800'
              }`}
            >
              <span>All Tags</span>
            </button>

            {availableTagsWithCounts.map(({ tag, count }) => {
              const isSelected = selectedTag === tag;
              return (
                <button
                  key={tag}
                  onClick={() => setSelectedTag(isSelected ? 'ALL' : tag)}
                  className={`px-3 py-1.5 rounded-xl font-medium whitespace-nowrap transition-all cursor-pointer flex items-center gap-1.5 border ${
                    isSelected
                      ? 'bg-amber-400 text-slate-950 font-bold border-amber-400 shadow-sm'
                      : 'bg-slate-900 border-slate-800 text-slate-300 hover:bg-slate-800 hover:text-white hover:border-slate-700'
                  }`}
                >
                  <span>{tag}</span>
                  <span
                    className={`text-[10px] px-1.5 py-0.2 rounded-full font-bold ${
                      isSelected
                        ? 'bg-slate-950 text-amber-400'
                        : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    {count}
                  </span>
                </button>
              );
            })}

            {selectedTag !== 'ALL' && (
              <button
                onClick={() => setSelectedTag('ALL')}
                className="px-2 py-1 text-slate-400 hover:text-rose-400 text-xs font-semibold flex items-center gap-1 whitespace-nowrap transition-colors cursor-pointer"
                title="Clear tag filter"
              >
                <X className="w-3.5 h-3.5" />
                <span>Clear Tag</span>
              </button>
            )}
          </div>
        )}
      </div>

      {/* Empty State Check */}
      {groupedByCategory.length === 0 && (
        <div className="bg-slate-900/80 rounded-3xl p-12 text-center border border-slate-800 space-y-4 shadow-lg backdrop-blur-md">
          <div className="w-12 h-12 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center mx-auto text-slate-400">
            <Filter className="w-6 h-6" />
          </div>
          <div>
            <h3 className="text-base font-bold text-slate-100">
              {selectedTag !== 'ALL'
                ? `No holdings tagged with "${selectedTag}"`
                : searchQuery
                ? 'No matching holdings found'
                : 'You haven’t added holdings in this category yet'}
            </h3>
            <p className="text-xs text-slate-400 mt-1 max-w-md mx-auto">
              {selectedTag !== 'ALL'
                ? 'Try selecting a different goal tag or clearing the tag filter above.'
                : 'Add your shares, mutual funds, gold bonds, or bank deposits to keep all records securely on your computer.'}
            </p>
          </div>
          <div className="flex items-center justify-center gap-2">
            {selectedTag !== 'ALL' && (
              <button
                onClick={() => setSelectedTag('ALL')}
                className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-bold rounded-xl border border-slate-700 cursor-pointer"
              >
                Clear Tag Filter
              </button>
            )}
            <button
              onClick={onOpenAddHolding}
              className="px-4 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold rounded-xl shadow-md shadow-amber-400/10 cursor-pointer"
            >
              + Add Holding
            </button>
          </div>
        </div>
      )}

      {/* Grouped Bento Category Tables */}
      <div className="space-y-6">
        {groupedByCategory.map(([catKey, items]) => {
          const catInfo = ASSET_CATEGORIES[catKey];

          // Compute category total
          let catTotalMinor = 0n;
          let catInvestedMinor = 0n;
          items.forEach(h => {
            const qty = BigInt(h.quantityScaled || '0');
            const cost = BigInt(h.averageCostPerUnitMinor || '0');
            let price = BigInt(h.currentPriceMinor || '0');
            if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
              price = BigInt(h.benchmarkPriceMinor);
            }
            catTotalMinor += (qty * price) / QTY_SCALE;
            catInvestedMinor += (qty * cost) / QTY_SCALE;
          });

          const catGainMinor = catTotalMinor - catInvestedMinor;
          const catGainFormatted = MoneyEngine.formatChange(
            catGainMinor,
            Number(catInvestedMinor) > 0 ? (Number(catGainMinor) / Number(catInvestedMinor)) * 100 : 0
          );

          return (
            <div
              key={catKey}
              className="bg-slate-900/80 rounded-3xl border border-slate-800 shadow-lg overflow-hidden backdrop-blur-md"
            >
              {/* Category Header */}
              <div className="p-4 sm:px-6 bg-slate-950/60 border-b border-slate-800 flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div
                    className="w-3.5 h-3.5 rounded-full ring-2 ring-slate-800"
                    style={{ backgroundColor: catInfo.color }}
                  />
                  <div>
                    <h2 className="text-sm sm:text-base font-bold text-slate-100 flex items-center gap-2">
                      <span>{catInfo.name}</span>
                      <span className="text-xs font-normal text-slate-400">
                        ({items.length} item{items.length > 1 ? 's' : ''})
                      </span>
                    </h2>
                    <p className="text-xs text-slate-400 hidden sm:block">{catInfo.description}</p>
                  </div>
                </div>

                <div className="text-right flex items-center gap-4">
                  <div>
                    <div className="text-xs text-slate-400 font-medium">
                      {catInfo.isLiability ? 'Total Outstanding' : 'Current Worth'}
                    </div>
                    <PrivacyValue
                      minor={catTotalMinor}
                      className={`text-sm sm:text-base font-bold ${
                        catInfo.isLiability ? 'text-rose-400' : 'text-slate-100'
                      }`}
                      compactClassName="text-xs text-slate-400 ml-1 font-medium"
                      showCompact
                    />
                  </div>

                  {!catInfo.isLiability && (
                    <div className="hidden sm:block text-right">
                      <div className="text-xs text-slate-400 font-medium">Total Growth</div>
                      <div
                        className={`text-xs font-semibold ${
                          catGainFormatted.isPositive ? 'text-emerald-400' : 'text-rose-400'
                        }`}
                      >
                        {catGainFormatted.fullText}
                      </div>
                    </div>
                  )}
                </div>
              </div>

              {/* Holdings List Table */}
              <div className="divide-y divide-slate-800">
                {items.map(h => {
                  const qtyScaled = BigInt(h.quantityScaled || '0');
                  const costPerUnitMinor = BigInt(h.averageCostPerUnitMinor || '0');
                  
                  let currentPriceMinor = BigInt(h.currentPriceMinor || '0');
                  let isUsingBenchmark = false;
                  if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
                    currentPriceMinor = BigInt(h.benchmarkPriceMinor);
                    isUsingBenchmark = true;
                  }

                  const totalValueMinor = (qtyScaled * currentPriceMinor) / QTY_SCALE;
                  const totalCostMinor = (qtyScaled * costPerUnitMinor) / QTY_SCALE;
                  const gainMinor = totalValueMinor - totalCostMinor;
                  const gainPercent =
                    Number(totalCostMinor) > 0 ? (Number(gainMinor) / Number(totalCostMinor)) * 100 : 0;
                  const gainFormatted = MoneyEngine.formatChange(gainMinor, gainPercent);

                  const owner = familyMembers.find(m => m.id === h.ownerId);

                  // Extract all tags for this holding
                  const holdingTags = Array.from(
                    new Set([h.tag, ...(h.tags || [])].filter(Boolean) as string[])
                  );

                  return (
                    <div
                      key={h.id}
                      onClick={() => onOpenHoldingDetail(h.id)}
                      className="p-4 sm:px-6 hover:bg-slate-800/50 transition-colors cursor-pointer flex flex-col sm:flex-row sm:items-center justify-between gap-3 group"
                    >
                      {/* Left: Name, Symbol, Folio, Owner Tag, Goal/Tag Badges */}
                      <div className="space-y-1 sm:max-w-[48%]">
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-semibold text-sm sm:text-base text-slate-100 group-hover:text-amber-300 transition-colors">
                            {h.name}
                          </span>
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
                              2.5% p.a. Coupon
                            </span>
                          )}
                        </div>

                        {/* Holding Tags & Goals Badges */}
                        {holdingTags.length > 0 && (
                          <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                            {holdingTags.map(tagStr => (
                              <button
                                key={tagStr}
                                onClick={e => {
                                  e.stopPropagation();
                                  setSelectedTag(tagStr === selectedTag ? 'ALL' : tagStr);
                                }}
                                className={`text-[10px] font-medium px-2 py-0.5 rounded-md border transition-all flex items-center gap-1 ${getTagBadgeStyle(
                                  tagStr
                                )} ${
                                  selectedTag === tagStr ? 'ring-2 ring-amber-400 font-bold' : ''
                                }`}
                                title={`Filter by tag: ${tagStr}`}
                              >
                                <Tag className="w-2.5 h-2.5" />
                                <span>{tagStr}</span>
                              </button>
                            ))}
                          </div>
                        )}

                        <div className="flex items-center gap-2 text-xs text-slate-400 flex-wrap">
                          <span className="font-mono font-medium text-slate-300">{h.symbolOrFolio}</span>
                          <span>•</span>
                          <span>{h.subCategory}</span>
                          <span>•</span>
                          <span className="text-slate-300 font-medium">
                            {MoneyEngine.formatQty(h.quantityScaled)} {h.category === 'GOLD_SGB' ? 'grams' : 'units'}
                          </span>
                        </div>

                        {/* Price Source & Freshness tag */}
                        <div className="flex items-center gap-1.5 text-[11px] text-slate-500">
                          <Clock className="w-3 h-3 text-slate-500" />
                          <span>{h.priceSource}</span>
                          {isUsingBenchmark && (
                            <span className="text-amber-400 font-medium">(RBI Sovereign Benchmark)</span>
                          )}
                        </div>
                      </div>

                      {/* Right: Quantity, Price, Current Worth & Return */}
                      <div className="flex items-center justify-between sm:justify-end gap-6 text-right">
                        <div>
                          <div className="text-xs text-slate-400">Current Unit Price</div>
                          <div className="text-sm font-semibold text-slate-100">
                            ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(currentPriceMinor))}
                          </div>
                          <div className="text-[11px] text-slate-500">
                            Avg: ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(costPerUnitMinor))}
                          </div>
                        </div>

                        <div className="min-w-[120px]">
                          <div className="text-xs text-slate-400">
                            {catInfo.isLiability ? 'Principal Remaining' : 'Total Worth'}
                          </div>
                          <PrivacyValue
                            minor={totalValueMinor}
                            className={`text-sm sm:text-base font-bold ${
                              catInfo.isLiability ? 'text-rose-400' : 'text-slate-100'
                            }`}
                            includePaise={false}
                          />
                          {!catInfo.isLiability && (
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

            </div>
          );
        })}
      </div>

    </div>
  );
};
