import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES, AssetCategory } from '../../types';
import { MoneyEngine, MONEY_SCALE, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  TrendingUp,
  TrendingDown,
  ShieldCheck,
  Plus,
  ArrowRight,
  Sparkles,
  PieChart,
  Calendar,
  Layers,
  ArrowUpRight,
  ArrowDownRight,
  Coins,
  Building,
  CreditCard,
  FileSpreadsheet,
  AlertCircle,
} from 'lucide-react';

interface HomeScreenProps {
  onOpenAddHolding: () => void;
  onOpenAddRecord: () => void;
  onOpenHoldingDetail: (holdingId: string) => void;
}

export const HomeScreen: React.FC<HomeScreenProps> = ({
  onOpenAddHolding,
  onOpenAddRecord,
  onOpenHoldingDetail,
}) => {
  const {
    summary,
    filteredHoldings,
    filteredEvents,
    familyMembers,
    selectedMemberId,
    setActiveScreen,
    sgbValuationMode,
  } = useKosha();

  const dayChangeFormatted = MoneyEngine.formatChange(
    summary.dayChangeMinor,
    summary.dayChangePercent
  );

  const gainFormatted = MoneyEngine.formatChange(
    summary.unrealisedGainMinor,
    summary.unrealisedGainPercent
  );

  // Group holdings by asset category to calculate allocation
  const categoryAllocations = React.useMemo(() => {
    const map = new Map<AssetCategory, { category: AssetCategory; totalValueMinor: bigint; count: number }>();

    filteredHoldings.forEach(h => {
      if (h.category === 'LIABILITY') return; // Exclude loans from positive asset pie
      const qtyScaled = BigInt(h.quantityScaled || '0');
      let priceMinor = BigInt(h.currentPriceMinor || '0');
      if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
        priceMinor = BigInt(h.benchmarkPriceMinor);
      }
      const valMinor = (qtyScaled * priceMinor) / QTY_SCALE;

      const existing = map.get(h.category) || { category: h.category, totalValueMinor: 0n, count: 0 };
      existing.totalValueMinor += valMinor;
      existing.count += 1;
      map.set(h.category, existing);
    });

    const totalAssetsNum = Number(summary.totalAssetsMinor);
    const list = Array.from(map.values()).map(item => {
      const valNum = Number(item.totalValueMinor);
      const percentage = totalAssetsNum > 0 ? (valNum / totalAssetsNum) * 100 : 0;
      return {
        ...item,
        percentage,
        info: ASSET_CATEGORIES[item.category],
      };
    });

    return list.sort((a, b) => Number(b.totalValueMinor - a.totalValueMinor));
  }, [filteredHoldings, summary.totalAssetsMinor, sgbValuationMode]);

  const selectedMemberName =
    selectedMemberId === 'ALL'
      ? 'All Family'
      : familyMembers.find(m => m.id === selectedMemberId)?.name || 'Family';

  return (
    <div className="space-y-6 pb-12">
      
      {/* Top Bento Hero Net Worth Card */}
      <div className="bg-slate-900/90 text-white rounded-3xl p-6 sm:p-8 shadow-xl border border-slate-800 backdrop-blur-md relative overflow-hidden">
        
        {/* Subtle decorative Bento background glows */}
        <div className="absolute -top-12 -right-12 w-96 h-96 bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute -bottom-12 -left-12 w-72 h-72 bg-sky-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase tracking-wider font-bold text-slate-400">
                {selectedMemberName} • Net Worth
              </span>
              <span className="inline-flex items-center gap-1 text-[11px] font-medium px-2.5 py-0.5 rounded-full bg-slate-800/90 text-slate-300 border border-slate-700/80">
                <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                Assets minus Loans
              </span>
            </div>

            <div className="text-xs text-slate-400 bg-slate-800/80 px-3 py-1 rounded-xl border border-slate-700/60">
              Annualized Return: <span className="font-bold text-amber-400">+{summary.yourReturnAnnualizedPercent}%</span>
            </div>
          </div>

          {/* Primary Net Worth Figure */}
          <div>
            <div className="flex items-baseline flex-wrap gap-x-3 gap-y-1">
              <PrivacyValue
                minor={summary.netWorthMinor}
                showCompact={true}
                className="text-3xl sm:text-5xl font-extrabold font-serif tracking-tight text-white"
                compactClassName="text-base sm:text-xl font-medium text-amber-400/90 ml-2"
              />
            </div>
            
            {/* Day Movement Indicator */}
            <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
              <div
                className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-xl font-semibold text-xs sm:text-sm ${
                  dayChangeFormatted.isPositive
                    ? 'bg-emerald-950/80 text-emerald-300 border border-emerald-800/60'
                    : dayChangeFormatted.isNegative
                    ? 'bg-rose-950/80 text-rose-300 border border-rose-800/60'
                    : 'bg-slate-800 text-slate-300 border border-slate-700'
                }`}
              >
                <span>{dayChangeFormatted.fullText} today</span>
              </div>

              <div className="text-slate-400 text-xs sm:text-sm">
                Overall Growth:{' '}
                <span className="font-semibold text-slate-200">
                  {gainFormatted.fullText}
                </span>
              </div>
            </div>
          </div>

          {/* Balance Sheet Bento Mini-Tiles */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-4 border-t border-slate-800/90">
            <div className="bg-slate-800/60 backdrop-blur-xs rounded-2xl p-4 border border-slate-700/50 hover:border-slate-600/70 transition-all">
              <div className="text-xs text-slate-400 mb-1 font-medium">Total Assets Owned</div>
              <PrivacyValue
                minor={summary.totalAssetsMinor}
                className="text-lg sm:text-xl font-bold text-emerald-400"
                compactClassName="text-xs text-slate-400 ml-1"
                showCompact
              />
            </div>

            <div className="bg-slate-800/60 backdrop-blur-xs rounded-2xl p-4 border border-slate-700/50 hover:border-slate-600/70 transition-all">
              <div className="text-xs text-slate-400 mb-1 font-medium">Total Outstanding Loans</div>
              <PrivacyValue
                minor={summary.totalLiabilitiesMinor}
                className="text-lg sm:text-xl font-bold text-rose-400"
                compactClassName="text-xs text-slate-400 ml-1"
                showCompact
              />
            </div>

            <div className="bg-slate-800/60 backdrop-blur-xs rounded-2xl p-4 border border-slate-700/50 hover:border-slate-600/70 transition-all">
              <div className="text-xs text-slate-400 mb-1 font-medium">Total Invested Cost</div>
              <PrivacyValue
                minor={summary.totalInvestedMinor}
                className="text-lg sm:text-xl font-bold text-slate-200"
                compactClassName="text-xs text-slate-400 ml-1"
                showCompact
              />
            </div>
          </div>

        </div>
      </div>

      {/* Bento Quick Action Shortcuts */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <button
          id="quick-add-investment"
          onClick={onOpenAddHolding}
          className="flex items-center gap-3 p-4 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-sky-500/50 hover:bg-slate-800/90 text-left transition-all group cursor-pointer shadow-sm"
        >
          <div className="w-10 h-10 rounded-xl bg-sky-950/80 border border-sky-800/60 text-sky-400 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
            <Plus className="w-5 h-5 stroke-[2.5]" />
          </div>
          <div>
            <div className="font-bold text-sm text-slate-100">Add Investment</div>
            <div className="text-xs text-slate-400">Stock, MF, SGB, FD</div>
          </div>
        </button>

        <button
          id="quick-record-transaction"
          onClick={onOpenAddRecord}
          className="flex items-center gap-3 p-4 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-emerald-500/50 hover:bg-slate-800/90 text-left transition-all group cursor-pointer shadow-sm"
        >
          <div className="w-10 h-10 rounded-xl bg-emerald-950/80 border border-emerald-800/60 text-emerald-400 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
            <TrendingUp className="w-5 h-5 stroke-[2.5]" />
          </div>
          <div>
            <div className="font-bold text-sm text-slate-100">Record Buy/Sell</div>
            <div className="text-xs text-slate-400">Dividend, EMI, Bonus</div>
          </div>
        </button>

        <button
          id="quick-import-statements"
          onClick={() => setActiveScreen('DOCUMENTS')}
          className="flex items-center gap-3 p-4 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-amber-500/50 hover:bg-slate-800/90 text-left transition-all group cursor-pointer shadow-sm"
        >
          <div className="w-10 h-10 rounded-xl bg-amber-950/80 border border-amber-800/60 text-amber-400 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
            <FileSpreadsheet className="w-5 h-5 stroke-[2.5]" />
          </div>
          <div>
            <div className="font-bold text-sm text-slate-100">Import Statement</div>
            <div className="text-xs text-slate-400">Zerodha, CAS, Groww</div>
          </div>
        </button>

        <button
          id="quick-growth-analytics"
          onClick={() => setActiveScreen('GROWTH')}
          className="flex items-center gap-3 p-4 rounded-2xl bg-slate-900/80 border border-slate-800 hover:border-purple-500/50 hover:bg-slate-800/90 text-left transition-all group cursor-pointer shadow-sm"
        >
          <div className="w-10 h-10 rounded-xl bg-purple-950/80 border border-purple-800/60 text-purple-400 flex items-center justify-center flex-shrink-0 group-hover:scale-105 transition-transform">
            <PieChart className="w-5 h-5 stroke-[2.5]" />
          </div>
          <div>
            <div className="font-bold text-sm text-slate-100">Your Returns</div>
            <div className="text-xs text-slate-400">IRR & Wealth Journey</div>
          </div>
        </button>
      </div>

      {/* Bento Grid: Asset Allocation Breakdown & Sovereign Gold Bond Highlight */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Left 2 Cols: Asset Allocation Bar & Cards */}
        <div className="lg:col-span-2 bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-5">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-bold text-slate-100">Asset Class Allocation</h2>
              <p className="text-xs text-slate-400">Diversification across Indian investment classes</p>
            </div>
            <button
              onClick={() => setActiveScreen('MY_MONEY')}
              className="text-xs font-semibold text-sky-400 hover:text-sky-300 flex items-center gap-1 cursor-pointer transition-colors"
            >
              <span>View All Holdings</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          {/* Allocation Multi-segmented Bar */}
          <div className="space-y-2">
            <div className="h-4 w-full rounded-full overflow-hidden flex bg-slate-800 p-0.5 gap-0.5 border border-slate-700/60">
              {categoryAllocations.map(cat => (
                <div
                  key={cat.category}
                  className="h-full rounded-xs transition-all hover:opacity-80 cursor-pointer"
                  style={{
                    width: `${Math.max(cat.percentage, 1)}%`,
                    backgroundColor: cat.info.color,
                  }}
                  title={`${cat.info.name}: ${cat.percentage.toFixed(1)}%`}
                />
              ))}
            </div>
          </div>

          {/* Category Allocation Bento Mini-Tiles */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
            {categoryAllocations.map(cat => (
              <div
                key={cat.category}
                onClick={() => setActiveScreen('MY_MONEY')}
                className="p-3.5 rounded-2xl bg-slate-900 border border-slate-800 hover:border-slate-700 hover:bg-slate-800/60 transition-all cursor-pointer flex items-center justify-between"
              >
                <div className="flex items-center gap-2.5">
                  <div
                    className="w-3 h-3 rounded-full flex-shrink-0 shadow-xs"
                    style={{ backgroundColor: cat.info.color }}
                  />
                  <div>
                    <div className="text-sm font-semibold text-slate-100">{cat.info.name}</div>
                    <div className="text-xs text-slate-400">{cat.count} holding{cat.count > 1 ? 's' : ''}</div>
                  </div>
                </div>

                <div className="text-right">
                  <PrivacyValue
                    minor={cat.totalValueMinor}
                    className="text-sm font-bold text-slate-100"
                    includePaise={false}
                  />
                  <div className="text-xs font-semibold text-slate-400">
                    {cat.percentage.toFixed(1)}%
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Right 1 Col: SGB & Sovereign Gold Bento Tile (SRS §6.3) */}
        <div className="bg-gradient-to-b from-amber-950/30 to-amber-950/10 rounded-3xl p-6 border border-amber-700/40 backdrop-blur-md shadow-lg flex flex-col justify-between space-y-4">
          <div>
            <div className="flex items-center gap-2 text-amber-300 font-bold text-sm mb-1">
              <Coins className="w-4 h-4 text-amber-400" />
              <span>Sovereign Gold (SGB) Tracker</span>
            </div>
            <p className="text-xs text-amber-200/80 leading-relaxed">
              SGBs carry two distinct values under Indian regulations: secondary market price and RBI Sovereign Gold benchmark.
            </p>

            <div className="mt-4 space-y-3 bg-slate-900/80 rounded-2xl p-4 border border-amber-700/30">
              <div className="flex justify-between text-xs">
                <span className="text-slate-400">Current Valuation Mode:</span>
                <span className="font-bold text-amber-300">
                  {sgbValuationMode === 'RBI_BENCHMARK' ? 'RBI Sovereign Rate' : 'NSE Traded Rate'}
                </span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-slate-400">Next Semi-Annual Coupon:</span>
                <span className="font-bold text-emerald-400">15 Nov 2026 (2.5% p.a.)</span>
              </div>
              <div className="flex justify-between text-xs">
                <span className="text-slate-400">Tax Exemption:</span>
                <span className="font-bold text-amber-300">100% Tax-Free on RBI Maturity</span>
              </div>
            </div>
          </div>

          <div className="pt-2">
            <button
              onClick={() => setActiveScreen('MY_MONEY')}
              className="w-full py-2.5 px-3 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold transition-all shadow-md shadow-amber-400/10 flex items-center justify-center gap-1.5 cursor-pointer"
            >
              <span>View Gold & SGB Holdings</span>
              <ArrowRight className="w-3.5 h-3.5 stroke-[2.5]" />
            </button>
          </div>
        </div>

      </div>

      {/* Bento Grid: Top Holdings & Recent Records Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        
        {/* Top Assets Bento Tile */}
        <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-4">
          <div className="flex items-center justify-between">
            <h2 className="text-base font-bold text-slate-100">Largest Family Assets</h2>
            <button
              onClick={() => setActiveScreen('MY_MONEY')}
              className="text-xs font-semibold text-sky-400 hover:text-sky-300 cursor-pointer transition-colors"
            >
              All Holdings →
            </button>
          </div>

          <div className="divide-y divide-slate-800">
            {filteredHoldings
              .filter(h => h.category !== 'LIABILITY')
              .slice(0, 5)
              .map(h => {
                const qtyScaled = BigInt(h.quantityScaled || '0');
                const priceMinor = BigInt(h.currentPriceMinor || '0');
                const valMinor = (qtyScaled * priceMinor) / QTY_SCALE;
                const catInfo = ASSET_CATEGORIES[h.category];

                return (
                  <div
                    key={h.id}
                    onClick={() => onOpenHoldingDetail(h.id)}
                    className="py-3 flex items-center justify-between hover:bg-slate-800/60 px-2 rounded-xl transition-colors cursor-pointer"
                  >
                    <div>
                      <div className="font-semibold text-sm text-slate-100">{h.name}</div>
                      <div className="text-xs text-slate-400 flex items-center gap-2 mt-0.5">
                        <span className="font-medium text-slate-300">{catInfo.shortName}</span>
                        <span>•</span>
                        <span>{MoneyEngine.formatQty(h.quantityScaled)} units</span>
                      </div>
                    </div>

                    <div className="text-right">
                      <PrivacyValue
                        minor={valMinor}
                        className="text-sm font-bold text-slate-100"
                        includePaise={false}
                      />
                      <div className="text-xs text-slate-400 font-medium mt-0.5">
                        ₹ {MoneyEngine.groupIndian(MoneyEngine.toRupees(BigInt(h.currentPriceMinor)))} / unit
                      </div>
                    </div>
                  </div>
                );
              })}
          </div>
        </div>

        {/* Recent Ledger Events Bento Tile */}
        <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <h2 className="text-base font-bold text-slate-100">Recent Records</h2>
              <span className="text-[10px] font-semibold bg-emerald-950/80 text-emerald-300 border border-emerald-800/60 px-2 py-0.5 rounded-md">
                Append-only
              </span>
            </div>
            <button
              onClick={() => setActiveScreen('DOCUMENTS')}
              className="text-xs font-semibold text-sky-400 hover:text-sky-300 cursor-pointer transition-colors"
            >
              Record Book →
            </button>
          </div>

          <div className="divide-y divide-slate-800">
            {filteredEvents.slice(0, 5).map(ev => {
              const isIncome = ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST';
              return (
                <div key={ev.id} className="py-3 flex items-center justify-between px-2 rounded-xl hover:bg-slate-800/40 transition-colors">
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-8 h-8 rounded-xl flex items-center justify-center text-xs font-bold border ${
                        ev.eventType === 'BUY'
                          ? 'bg-sky-950/80 text-sky-400 border-sky-800/60'
                          : ev.eventType === 'SELL'
                          ? 'bg-rose-950/80 text-rose-400 border-rose-800/60'
                          : isIncome
                          ? 'bg-emerald-950/80 text-emerald-400 border-emerald-800/60'
                          : 'bg-slate-800 text-slate-300 border-slate-700'
                      }`}
                    >
                      {ev.eventType === 'BUY' ? 'BUY' : ev.eventType === 'SELL' ? 'SELL' : isIncome ? 'INC' : 'REC'}
                    </div>
                    <div>
                      <div className="font-semibold text-sm text-slate-100">{ev.holdingName}</div>
                      <div className="text-xs text-slate-400">
                        {ev.eventDate} • {ev.notes || ev.eventType}
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
                  </div>
                </div>
              );
            })}
          </div>
        </div>

      </div>

    </div>
  );
};
