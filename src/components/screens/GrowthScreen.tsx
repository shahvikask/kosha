import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
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
  const { summary, filteredHoldings, sgbValuationMode } = useKosha();
  const [isExplainerOpen, setIsExplainerOpen] = useState(false);
  const [selectedTimeline, setSelectedTimeline] = useState<'3Y' | '5Y' | 'ALL'>('ALL');

  // Realistic historical wealth journey points for Indian investor compounding
  const chartData = [
    { year: '2019', invested: 4200000, value: 4520000, formattedValue: '₹ 45.2 Lakh' },
    { year: '2020', invested: 5400000, value: 5890000, formattedValue: '₹ 58.9 Lakh' },
    { year: '2021', invested: 7200000, value: 8900000, formattedValue: '₹ 89.0 Lakh' },
    { year: '2022', invested: 9100000, value: 11200000, formattedValue: '₹ 1.12 Crore' },
    { year: '2023', invested: 10800000, value: 13900000, formattedValue: '₹ 1.39 Crore' },
    { year: '2024', invested: 12200000, value: 16800000, formattedValue: '₹ 1.68 Crore' },
    { year: '2025', invested: 13400000, value: 19500000, formattedValue: '₹ 1.95 Crore' },
    { year: '2026', invested: 14600000, value: 22682950, formattedValue: '₹ 2.27 Crore' },
  ];

  // Asset class performance benchmarks
  const assetPerformance = [
    {
      name: 'Indian Equities (Direct)',
      annualizedReturn: 18.9,
      riskNote: 'High Growth · Volatile',
      color: '#38bdf8',
      comment: 'Top contributors: HDFC Bank, Reliance & TCS bonus',
    },
    {
      name: 'Mutual Funds & ETFs',
      annualizedReturn: 17.2,
      riskNote: 'Core Systematic Compounding',
      color: '#2dd4bf',
      comment: 'Parag Parikh Flexi Cap & Nifty 50 Index',
    },
    {
      name: 'Gold & Sovereign Gold Bonds (SGB)',
      annualizedReturn: 15.6,
      riskNote: 'Inflation Hedge + 2.5% Tax-free Coupon',
      color: '#fbbf24',
      comment: 'Gold rally + semi-annual sovereign interest',
    },
    {
      name: 'Govt Schemes & Fixed Income (PPF/EPF/NPS)',
      annualizedReturn: 8.1,
      riskNote: 'Guaranteed Sovereign Safety',
      color: '#4ade80',
      comment: 'EPF 8.25%, PPF 7.1% (EEE Tax Free)',
    },
  ];

  const totalDividendsMinor = summary.totalDividendsInterestMinor;

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

        <div className="relative z-10 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <span className="text-xs uppercase tracking-wider font-bold text-amber-400">
                Your Annualized Return
              </span>
              <span className="text-[11px] px-2.5 py-0.5 rounded-full bg-slate-800/90 text-slate-300 border border-slate-700">
                Personal IRR / Money-Weighted Return
              </span>
            </div>
            
            <button
              id="explainer-toggle-btn"
              onClick={() => setIsExplainerOpen(!isExplainerOpen)}
              className="text-xs font-semibold text-slate-300 hover:text-white flex items-center gap-1.5 bg-slate-800/80 hover:bg-slate-700/80 px-3 py-1.5 rounded-xl border border-slate-700 transition-all cursor-pointer"
            >
              <HelpCircle className="w-3.5 h-3.5 text-amber-400" />
              <span>Why is this different from fund company numbers?</span>
              {isExplainerOpen ? <ChevronUp className="w-3.5 h-3.5" /> : <ChevronDown className="w-3.5 h-3.5" />}
            </button>
          </div>

          <div className="flex items-baseline gap-3 flex-wrap">
            <div className="text-4xl sm:text-6xl font-extrabold font-serif text-white tracking-tight">
              +{summary.yourReturnAnnualizedPercent}%
            </div>
            <div className="text-sm sm:text-base text-slate-300 font-medium">
              per annum across your entire portfolio
            </div>
          </div>

          <p className="text-xs sm:text-sm text-slate-400 max-w-2xl leading-relaxed">
            This accounts for the exact dates when money entered and left your accounts, all dividends and interest received in your bank, and bonus shares.
          </p>

          {/* Expandable Explanation Panel (Mandated by Annex B §B7.3) */}
          {isExplainerOpen && (
            <div className="mt-4 p-5 rounded-2xl bg-slate-950/80 border border-slate-800 text-xs sm:text-sm text-slate-200 space-y-3 animate-in fade-in">
              <div className="font-bold text-amber-300 flex items-center gap-2">
                <Info className="w-4 h-4 text-amber-400" />
                <span>The Difference Between Fund Returns and Your Return</span>
              </div>
              <p className="leading-relaxed text-slate-300">
                A mutual fund company or broker shows <strong>Scheme Point-to-Point CAGR</strong> or <strong>Time-Weighted Return (TWR)</strong>. That calculation assumes someone invested all their money on day one and never added or withdrew a single rupee.
              </p>
              <p className="leading-relaxed text-slate-300">
                <strong>Your Return (Internal Rate of Return / IRR)</strong> is your true individual financial experience. If you added more money via monthly SIPs when the market was lower in 2022, your actual personal return is higher than the fund’s advertised benchmark.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2 text-xs">
                <div className="p-3.5 bg-slate-900 rounded-xl border border-slate-800">
                  <div className="font-semibold text-slate-300">Fund Adverts (TWR/CAGR)</div>
                  <div className="text-slate-400 mt-1">Measures fund manager performance ignoring cashflow timing.</div>
                </div>
                <div className="p-3.5 bg-slate-900 rounded-xl border border-emerald-900/50">
                  <div className="font-semibold text-emerald-400">Kosha "Your Return" (IRR)</div>
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
                minor={MoneyEngine.fromRupees(39000)}
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
                tickFormatter={val => `₹ ${(val / 10000000).toFixed(1)} Cr`}
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

      {/* Asset Class Performance Matrix Bento Tile */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-4">
        <div>
          <h2 className="text-base font-bold text-slate-100">Asset Class Returns</h2>
          <p className="text-xs text-slate-400">Annualized return contribution by each investment pillar</p>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {assetPerformance.map(item => (
            <div
              key={item.name}
              className="p-4 rounded-2xl bg-slate-900 border border-slate-800 hover:border-slate-700 transition-all space-y-2"
            >
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <div
                    className="w-3 h-3 rounded-full flex-shrink-0 ring-2 ring-slate-800"
                    style={{ backgroundColor: item.color }}
                  />
                  <span className="font-bold text-sm text-slate-100">{item.name}</span>
                </div>
                <span className="font-bold text-base text-emerald-400">
                  +{item.annualizedReturn}% p.a.
                </span>
              </div>

              <div className="flex items-center justify-between text-xs text-slate-400">
                <span>{item.riskNote}</span>
              </div>

              <div className="text-xs text-slate-300 bg-slate-950/70 p-2.5 rounded-xl border border-slate-800/80">
                {item.comment}
              </div>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};
