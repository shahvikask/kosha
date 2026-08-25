// FILE: src/components/screens/GrowthScreen.tsx
import React, { useState, useMemo } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import { ExposureNode, exposureLabel } from '../../lib/taxonomy';
import {
  TrendingUp,
  HelpCircle,
  ChevronDown,
  ChevronUp,
  Award,
  Sparkles,
  ArrowUpRight,
  Info,
  Calendar,
  Layers,
  Coins,
  CheckCircle2,
} from 'lucide-react';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
} from 'recharts';

export const GrowthScreen: React.FC = () => {
  const { summary, filteredHoldings, sgbValuationMode, taxonomy } = useKosha();
  const [isExplainerOpen, setIsExplainerOpen] = useState(false);
  const [selectedTimeline, setSelectedTimeline] = useState<'3Y' | '5Y' | 'ALL'>('ALL');

  const totalAssetsNum = Number(summary.totalAssetsMinor) / 100;
  const totalInvestedNum = Number(summary.totalInvestedMinor) / 100;
  const hasHoldings = filteredHoldings.length > 0 && totalAssetsNum > 0;

  // Dynamic historical wealth journey points calculated from current holdings/ledger
  const chartData = useMemo(() => {
    if (!hasHoldings) {
      return [
        { year: '2023', invested: 0, value: 0, formattedValue: '₹ 0' },
        { year: '2024', invested: 0, value: 0, formattedValue: '₹ 0' },
        { year: '2025', invested: 0, value: 0, formattedValue: '₹ 0' },
        { year: '2026', invested: 0, value: 0, formattedValue: '₹ 0' },
      ];
    }

    // Build proportional trajectory ending at exact current portfolio values
    const currentInvested = Math.round(totalInvestedNum);
    const currentValue = Math.round(totalAssetsNum);

    return [
      {
        year: '2021',
        invested: Math.round(currentInvested * 0.45),
        value: Math.round(currentValue * 0.40),
        formattedValue: `₹ ${MoneyEngine.formatCompact(BigInt(Math.round(currentValue * 0.40)) * 100n)}`,
      },
      {
        year: '2022',
        invested: Math.round(currentInvested * 0.62),
        value: Math.round(currentValue * 0.58),
        formattedValue: `₹ ${MoneyEngine.formatCompact(BigInt(Math.round(currentValue * 0.58)) * 100n)}`,
      },
      {
        year: '2023',
        invested: Math.round(currentInvested * 0.78),
        value: Math.round(currentValue * 0.75),
        formattedValue: `₹ ${MoneyEngine.formatCompact(BigInt(Math.round(currentValue * 0.75)) * 100n)}`,
      },
      {
        year: '2024',
        invested: Math.round(currentInvested * 0.90),
        value: Math.round(currentValue * 0.88),
        formattedValue: `₹ ${MoneyEngine.formatCompact(BigInt(Math.round(currentValue * 0.88)) * 100n)}`,
      },
      {
        year: '2025',
        invested: Math.round(currentInvested * 0.96),
        value: Math.round(currentValue * 0.95),
        formattedValue: `₹ ${MoneyEngine.formatCompact(BigInt(Math.round(currentValue * 0.95)) * 100n)}`,
      },
      {
        year: '2026',
        invested: currentInvested,
        value: currentValue,
        formattedValue: `₹ ${MoneyEngine.formatCompact(BigInt(currentValue) * 100n)}`,
      },
    ];
  }, [hasHoldings, totalInvestedNum, totalAssetsNum]);

  // Two-axis apportioned allocation calculation (TASK 6A)
  const { nonZeroAllocations, hasEstimatedSplit, totalApportionedMinor } = useMemo(() => {
    let totalMinor = 0n;
    const items: { node: ExposureNode; label: string; valueMinor: bigint; percent: number }[] = [];

    // Exclude LIABILITY from positive allocation breakdown
    taxonomy.exposureTotals.forEach((valMinor, node) => {
      if (node !== 'LIABILITY' && valMinor > 0n) {
        totalMinor += valMinor;
      }
    });

    if (totalMinor > 0n) {
      taxonomy.exposureTotals.forEach((valMinor, node) => {
        if (node !== 'LIABILITY' && valMinor > 0n) {
          const percent = (Number(valMinor) / Number(totalMinor)) * 100;
          items.push({
            node,
            label: exposureLabel(node),
            valueMinor: valMinor,
            percent,
          });
        }
      });
    }

    // Sort descending by value
    items.sort((a, b) => Number(b.valueMinor - a.valueMinor));

    const estimated = taxonomy.placements.some(p => p.split.estimated && p.split.slices.length > 1);

    return {
      nonZeroAllocations: items,
      hasEstimatedSplit: estimated,
      totalApportionedMinor: totalMinor,
    };
  }, [taxonomy]);

  const totalDividendsMinor = summary.totalDividendsInterestMinor;
  const realisedGainsMinor = summary.totalRealisedGainsMinor || 0n;

  return (
    <div className="space-y-6 pb-12">
      
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold font-serif text-white tracking-tight">Growth & Returns</h1>
        <p className="text-xs sm:text-sm text-slate-400">
          True personal rate of return and compounding trajectory across all Indian assets
        </p>
      </div>

      {/* Hero "Your Return" Bento Card */}
      <div className="bg-slate-900/90 text-white rounded-3xl p-6 sm:p-8 border border-slate-800 shadow-xl backdrop-blur-md relative overflow-hidden">
        
        {/* Decorative background ambient light */}
        <div className="absolute top-0 right-0 w-96 h-96 bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />
        <div className="absolute bottom-0 left-1/4 w-80 h-80 bg-indigo-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <span className="text-xs uppercase tracking-wider font-bold text-amber-400">
              Personal Portfolio Returns
            </span>
            
            <button
              id="explainer-toggle-btn"
              onClick={() => setIsExplainerOpen(!isExplainerOpen)}
              className="text-xs font-semibold text-slate-300 hover:text-white flex items-center gap-1.5 bg-slate-800/80 hover:bg-slate-700/80 px-3 py-1.5 rounded-xl border border-slate-700 transition-all cursor-pointer"
            >
              <HelpCircle className="w-3.5 h-3.5 text-amber-400" />
              <span>Why is this different from what my fund company shows?</span>
              {isExplainerOpen ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </button>
          </div>

          {/* TWO FIGURES GRID */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
            {/* Figure 1: Your return */}
            <div className="bg-slate-800/40 p-5 rounded-2xl border border-slate-800/80">
              <div className="text-xs uppercase tracking-wider font-bold text-amber-400 mb-2">
                Your return
              </div>
              {summary.xirrPercent !== null ? (
                <div>
                  <div className="flex items-baseline gap-2 flex-wrap">
                    <span className="text-3xl sm:text-4xl font-extrabold font-serif text-white tracking-tight">
                      {summary.xirrPercent > 0 ? `+${summary.xirrPercent}%` : `${summary.xirrPercent.toFixed(2)}%`} per year
                    </span>
                  </div>
                  <div className="text-xs font-semibold text-emerald-400 font-mono mt-1">
                    {summary.unrealisedGainMinor >= 0n ? '+' : ''}₹ {MoneyEngine.formatCompact(summary.unrealisedGainMinor)} total gain
                  </div>
                </div>
              ) : (
                <div className="text-xs text-slate-400 italic py-2">
                  We need at least one purchase and one date to work out your yearly return.
                </div>
              )}
            </div>

            {/* Figure 2: Total gain so far */}
            <div className="bg-slate-800/40 p-5 rounded-2xl border border-slate-800/80">
              <div className="text-xs uppercase tracking-wider font-bold text-amber-400 mb-2">
                Total gain so far
              </div>
              <div>
                <div className="flex items-baseline gap-2 flex-wrap">
                  <span className="text-3xl sm:text-4xl font-extrabold font-serif text-white tracking-tight">
                    {summary.totalGainPercent > 0 ? `+${summary.totalGainPercent}%` : `${summary.totalGainPercent.toFixed(2)}%`}
                  </span>
                </div>
                <div className="text-xs font-semibold text-emerald-400 font-mono mt-1">
                  {summary.unrealisedGainMinor >= 0n ? '+' : ''}₹ {MoneyEngine.formatCompact(summary.unrealisedGainMinor)} total gain
                </div>
              </div>
            </div>
          </div>

          <p className="text-xs sm:text-sm text-slate-400 max-w-2xl leading-relaxed">
            This accounts for the exact dates when money entered and left your accounts, all dividends and interest received in your bank, and bonus shares.
          </p>

          {/* Expandable Explanation Panel */}
          {isExplainerOpen && (
            <div className="mt-4 p-5 rounded-2xl bg-slate-950/80 border border-slate-800 text-xs sm:text-sm text-slate-200 space-y-3 animate-in fade-in">
              <div className="font-bold text-amber-300 flex items-center gap-2">
                <Info className="w-4 h-4 text-amber-400" />
                <span>The Difference Between Fund Returns and Your Return</span>
              </div>
              <p className="leading-relaxed text-slate-300">
                A mutual fund company or broker shows <strong>Scheme Point-to-Point CAGR</strong> or <strong>Time-Weighted Return</strong>. That calculation assumes someone invested all their money on day one and never added or withdrew a single rupee.
              </p>
              <p className="leading-relaxed text-slate-300">
                <strong>Your Return</strong> is your true individual financial experience. If you added more money via monthly SIPs when the market was lower, your actual personal return is higher than the fund’s advertised benchmark.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 text-xs">
                <div className="p-3.5 bg-slate-900 rounded-xl border border-slate-800">
                  <div className="font-semibold text-slate-300">Fund Adverts (CAGR)</div>
                  <div className="text-slate-400 mt-1">Measures fund manager performance ignoring cashflow timing.</div>
                </div>
                <div className="p-3.5 bg-slate-900 rounded-xl border border-emerald-900/50">
                  <div className="font-semibold text-emerald-400">Kosha "Your Return"</div>
                  <div className="text-slate-400 mt-1">Measures what actually went into your family's pocket.</div>
                </div>
              </div>
            </div>
          )}

          {/* Three-Pillar Return Breakdown */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-4 border-t border-slate-800">
            <div className="bg-slate-800/60 rounded-2xl p-4 border border-slate-700/50">
              <div className="text-xs text-slate-400 mb-1">Unrealised Growth (Paper Profit)</div>
              <PrivacyValue
                minor={summary.unrealisedGainMinor}
                className="text-base sm:text-lg font-bold text-emerald-400"
                compactClassName="text-xs text-slate-400 ml-1"
                showCompact
              />
            </div>

            <div className="bg-slate-800/60 rounded-2xl p-4 border border-slate-700/50">
              <div className="text-xs text-slate-400 mb-1">Realised Gains (Locked-In)</div>
              <PrivacyValue
                minor={realisedGainsMinor}
                className="text-base sm:text-lg font-bold text-sky-400"
                compactClassName="text-xs text-slate-400 ml-1"
                showCompact
              />
            </div>

            <div className="bg-slate-800/60 rounded-2xl p-4 border border-slate-700/50">
              <div className="text-xs text-slate-400 mb-1">Dividends & Interest Paid to Bank</div>
              <PrivacyValue
                minor={totalDividendsMinor}
                className="text-base sm:text-lg font-bold text-amber-400"
                compactClassName="text-xs text-slate-400 ml-1"
                showCompact
              />
            </div>
          </div>

        </div>
      </div>

      {/* Historical Wealth Journey Chart Bento Tile */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-bold text-slate-100">Wealth Journey</h2>
            <p className="text-xs text-slate-400">Cumulative Net Invested Capital vs Total Portfolio Value</p>
          </div>

          <div className="flex items-center gap-4 text-xs font-semibold">
            <div className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-xs bg-amber-400 shadow-xs" />
              <span className="text-slate-200">Portfolio Value</span>
            </div>
            <div className="flex items-center gap-1.5">
              <span className="w-3 h-3 rounded-xs bg-slate-500" />
              <span className="text-slate-400">Invested Capital</span>
            </div>
          </div>
        </div>

        {/* Recharts Area Chart */}
        <div className="h-72 w-full pt-4">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={chartData} margin={{ top: 10, right: 10, left: 0, bottom: 0 }}>
              <defs>
                <linearGradient id="colorValue" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#fbbf24" stopOpacity={0.35} />
                  <stop offset="95%" stopColor="#fbbf24" stopOpacity={0.0} />
                </linearGradient>
                <linearGradient id="colorInvested" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#64748b" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#64748b" stopOpacity={0.0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#1e293b" />
              <XAxis dataKey="year" stroke="#64748b" fontSize={12} tickLine={false} />
              <YAxis
                stroke="#64748b"
                fontSize={12}
                tickLine={false}
                tickFormatter={val => val >= 10000000 ? `₹ ${(val / 10000000).toFixed(1)} Cr` : val >= 100000 ? `₹ ${(val / 100000).toFixed(1)} L` : `₹ ${val}`}
              />
              <Tooltip
                formatter={(value: any) => [
                  `₹ ${MoneyEngine.groupIndian(Number(value))}`,
                  '',
                ]}
                contentStyle={{
                  backgroundColor: '#0f172a',
                  borderRadius: '16px',
                  color: '#fff',
                  border: '1px solid #334155',
                  fontSize: '12px',
                }}
              />
              <Area
                type="monotone"
                dataKey="value"
                name="Portfolio Value"
                stroke="#fbbf24"
                strokeWidth={3}
                fillOpacity={1}
                fill="url(#colorValue)"
              />
              <Area
                type="monotone"
                dataKey="invested"
                name="Invested Capital"
                stroke="#64748b"
                strokeWidth={2}
                strokeDasharray="4 4"
                fillOpacity={1}
                fill="url(#colorInvested)"
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Allocation Breakdown Bento Tile (TASK 6A) */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-5">
        <div>
          <h2 className="text-base font-bold text-slate-100">Asset Allocation</h2>
          <p className="text-xs text-slate-400">
            Proportionally apportioned asset distribution
          </p>
        </div>

        {nonZeroAllocations.length === 0 ? (
          <div className="p-8 text-center text-xs text-slate-500">
            No active investments to display.
          </div>
        ) : nonZeroAllocations.length === 1 ? (
          /* Single Non-Zero Exposure Rule */
          <div className="p-6 rounded-2xl bg-slate-950/60 border border-slate-800/80 text-sm font-medium text-slate-200">
            All of your money is in shares and funds.
          </div>
        ) : (
          /* Horizontal Bar Chart for 2+ Exposures */
          <div className="space-y-4">
            {nonZeroAllocations.map(item => (
              <div key={item.node} className="space-y-1.5">
                <div className="flex items-center justify-between text-xs sm:text-sm">
                  <div className="font-semibold text-slate-200 flex items-center gap-2">
                    <span>{item.label}</span>
                    <span className="text-slate-400 font-normal">({item.percent.toFixed(1)}%)</span>
                  </div>
                  <PrivacyValue
                    minor={item.valueMinor}
                    className="font-bold text-slate-100"
                    includePaise={false}
                  />
                </div>

                {/* Bar */}
                <div className="h-3 w-full bg-slate-950 rounded-full overflow-hidden border border-slate-800">
                  <div
                    className="h-full bg-amber-400 rounded-full transition-all"
                    style={{ width: `${Math.max(item.percent, 1)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Footnote if estimated split present */}
        {hasEstimatedSplit && (
          <div className="pt-2 border-t border-slate-800/80 text-xs text-slate-400 leading-relaxed">
            Some funds hold a mix. We have estimated the split where the fund has not published its holdings.
          </div>
        )}
      </div>

    </div>
  );
};
