import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { PrivacyValue } from '../common/PrivacyValue';
import {
  Sparkles,
  Printer,
  ShieldCheck,
  Building,
  Coins,
  TrendingUp,
  Landmark,
  FileText,
  CreditCard,
  PhoneCall,
  Home,
  CheckCircle,
} from 'lucide-react';

interface SimpleViewScreenProps {
  onOpenEmergencySheet: () => void;
}

export const SimpleViewScreen: React.FC<SimpleViewScreenProps> = ({ onOpenEmergencySheet }) => {
  const { summary, filteredHoldings, setActiveScreen, sgbValuationMode } = useKosha();

  // Compute plain buckets
  let bankCashMinor = 0n;
  let sharesMutualFundsMinor = 0n;
  let goldMinor = 0n;
  let govtSchemesMinor = 0n;
  let propertyMinor = 0n;
  let loansMinor = 0n;

  filteredHoldings.forEach(h => {
    const qty = BigInt(h.quantityScaled || '0');
    let price = BigInt(h.currentPriceMinor || '0');
    if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
      price = BigInt(h.benchmarkPriceMinor);
    }
    const val = (qty * price) / QTY_SCALE;

    if (h.category === 'CASH_BANK' || (h.category === 'FIXED_INCOME' && h.subCategory.includes('FD'))) {
      bankCashMinor += val;
    } else if (h.category === 'EQUITY' || h.category === 'MUTUAL_FUND' || h.category === 'SPECIALISED') {
      sharesMutualFundsMinor += val;
    } else if (h.category === 'GOLD_SGB') {
      goldMinor += val;
    } else if (h.category === 'FIXED_INCOME') {
      govtSchemesMinor += val;
    } else if (h.category === 'REAL_ESTATE') {
      propertyMinor += val;
    } else if (h.category === 'LIABILITY') {
      loansMinor += val;
    }
  });

  const simpleBuckets = [
    {
      title: 'Family Property & Real Estate',
      icon: Home,
      amountMinor: propertyMinor,
      description: 'Apartment and land registered in family name',
      color: '#8b5cf6',
    },
    {
      title: 'Shares & Mutual Funds',
      icon: TrendingUp,
      amountMinor: sharesMutualFundsMinor,
      description: 'Listed Indian stocks and mutual fund portfolios',
      color: '#38bdf8',
    },
    {
      title: 'Government Schemes (PPF & EPF)',
      icon: ShieldCheck,
      amountMinor: govtSchemesMinor,
      description: 'Guaranteed government provident funds & pension',
      color: '#4ade80',
    },
    {
      title: 'Gold & Sovereign Gold Bonds',
      icon: Coins,
      amountMinor: goldMinor,
      description: 'RBI gold bonds (2.5% interest) and gold in bank locker',
      color: '#fbbf24',
    },
    {
      title: 'Bank Accounts & Fixed Deposits',
      icon: Landmark,
      amountMinor: bankCashMinor,
      description: 'Emergency funds in HDFC & ICICI bank accounts',
      color: '#22d3ee',
    },
  ];

  return (
    <div className="space-y-8 max-w-4xl mx-auto pb-16">
      
      {/* Top Banner with Easy Controls */}
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-slate-800 pb-4">
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-2xl bg-amber-400/10 text-amber-400 border border-amber-400/20">
            <Sparkles className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-xl sm:text-2xl font-bold font-serif text-white">
              Simple Family Wealth View
            </h1>
            <p className="text-xs sm:text-sm text-slate-400">
              A clean, jargon-free summary designed for the whole family
            </p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={onOpenEmergencySheet}
            className="px-3.5 py-2 bg-slate-900 border border-slate-700 hover:bg-slate-800 text-slate-200 rounded-xl text-xs sm:text-sm font-semibold flex items-center gap-1.5 cursor-pointer transition-colors"
          >
            <Printer className="w-4 h-4 text-slate-400" />
            <span>Print Summary</span>
          </button>

          <button
            onClick={() => setActiveScreen('HOME')}
            className="px-4 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs sm:text-sm font-bold shadow-md shadow-amber-400/10 cursor-pointer transition-all"
          >
            Back to Detailed View
          </button>
        </div>
      </div>

      {/* Big Hero Bento Card: Total Family Net Worth */}
      <div className="bg-slate-900/90 text-white rounded-3xl p-8 sm:p-10 shadow-xl border border-slate-800 backdrop-blur-md space-y-6 text-center relative overflow-hidden">
        <div className="absolute top-0 right-0 w-72 h-72 bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />

        <div className="relative z-10 space-y-2">
          <div className="text-xs sm:text-sm uppercase tracking-widest font-bold text-amber-400">
            Total Family Net Worth Today
          </div>

          <PrivacyValue
            minor={summary.netWorthMinor}
            showCompact={true}
            className="text-4xl sm:text-6xl font-black font-serif text-white tracking-tight"
            compactClassName="text-xl sm:text-2xl font-bold text-amber-400 ml-2"
          />
          <p className="text-xs sm:text-sm text-slate-400 max-w-md mx-auto">
            This is what the family owns across all assets after subtracting outstanding bank loans.
          </p>
        </div>

        <div className="relative z-10 grid grid-cols-2 gap-4 max-w-md mx-auto pt-4 border-t border-slate-800">
          <div className="p-4 bg-slate-950/60 rounded-2xl border border-slate-800">
            <div className="text-xs text-slate-400 font-medium">Total Assets Owned</div>
            <PrivacyValue
              minor={summary.totalAssetsMinor}
              className="text-base sm:text-lg font-bold text-emerald-400"
              showCompact
            />
          </div>

          <div className="p-4 bg-slate-950/60 rounded-2xl border border-slate-800">
            <div className="text-xs text-slate-400 font-medium">Total Loans to Repay</div>
            <PrivacyValue
              minor={loansMinor}
              className="text-base sm:text-lg font-bold text-rose-400"
              showCompact
            />
          </div>
        </div>
      </div>

      {/* Where is our family money? Bento list */}
      <div className="space-y-4">
        <h2 className="text-lg font-bold text-slate-100 font-serif">
          Where is our family money located?
        </h2>

        <div className="grid grid-cols-1 gap-4">
          {simpleBuckets.map(b => {
            const Icon = b.icon;
            return (
              <div
                key={b.title}
                className="bg-slate-900/80 rounded-3xl p-5 sm:p-6 border border-slate-800 backdrop-blur-md shadow-lg flex flex-col sm:flex-row sm:items-center justify-between gap-4 hover:border-slate-700 transition-colors"
              >
                <div className="flex items-center gap-4">
                  <div
                    className="w-12 h-12 rounded-2xl flex items-center justify-center text-slate-950 flex-shrink-0 font-bold"
                    style={{ backgroundColor: b.color }}
                  >
                    <Icon className="w-6 h-6" />
                  </div>
                  <div>
                    <h3 className="font-bold text-base sm:text-lg text-slate-100">{b.title}</h3>
                    <p className="text-xs sm:text-sm text-slate-400">{b.description}</p>
                  </div>
                </div>

                <div className="text-right sm:border-l sm:border-slate-800 sm:pl-6">
                  <PrivacyValue
                    minor={b.amountMinor}
                    className="text-lg sm:text-xl font-bold text-slate-100"
                    showCompact
                  />
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Emergency Quick Notes Bento Card */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-amber-500/30 backdrop-blur-md shadow-lg space-y-3">
        <div className="flex items-center gap-2 text-amber-300 font-bold text-base">
          <PhoneCall className="w-5 h-5 text-amber-400" />
          <span>Emergency Steps & Bank Locker Access</span>
        </div>
        <p className="text-xs sm:text-sm text-slate-300 leading-relaxed">
          The physical gold is stored at <strong>HDFC Bank Locker #412 (Indiranagar branch)</strong>. Mutual fund and Demat account statements are registered under Vikas and Ananya as joint nominees. You can generate a full printed dossier from the button above.
        </p>
      </div>

    </div>
  );
};
