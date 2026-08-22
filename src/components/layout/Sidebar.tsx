import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import {
  Home,
  WalletCards,
  TrendingUp,
  FileText,
  Settings,
  Sparkles,
  ShieldCheck,
  Building2,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { activeScreen, setActiveScreen, summary } = useKosha();

  const navItems = [
    {
      id: 'HOME' as const,
      label: 'Home',
      icon: Home,
      description: 'Net worth & overview',
    },
    {
      id: 'MY_MONEY' as const,
      label: 'My Money',
      icon: WalletCards,
      description: 'Holdings by asset class',
    },
    {
      id: 'GROWTH' as const,
      label: 'Growth',
      icon: TrendingUp,
      description: 'Your return & wealth journey',
    },
    {
      id: 'DOCUMENTS' as const,
      label: 'Documents',
      icon: FileText,
      description: 'Statements, taxes & records',
    },
    {
      id: 'SETTINGS' as const,
      label: 'Settings',
      icon: Settings,
      description: 'Vault, recovery & backup',
    },
  ];

  return (
    <>
      {/* Desktop / Tablet Sidebar */}
      <aside className="w-64 bg-[#0F172A]/70 text-slate-100 flex-shrink-0 flex flex-col justify-between hidden md:flex min-h-[calc(100vh-4rem-2.5rem)] border-r border-slate-800 backdrop-blur-xs">
        <div className="p-4 space-y-6">
          
          {/* Main 5 Screens Navigation */}
          <nav className="space-y-1.5" aria-label="Main Navigation">
            <div className="px-3 pb-2 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
              Navigation
            </div>
            {navItems.map(item => {
              const Icon = item.icon;
              const isActive = activeScreen === item.id;
              return (
                <button
                  key={item.id}
                  id={`nav-btn-${item.id.toLowerCase()}`}
                  onClick={() => setActiveScreen(item.id)}
                  className={`w-full flex items-center gap-3 px-3.5 py-3 rounded-2xl text-left transition-all group ${
                    isActive
                      ? 'bg-amber-400 text-slate-950 font-bold shadow-lg shadow-amber-400/10'
                      : 'text-slate-300 hover:bg-slate-800/80 hover:text-white font-medium border border-transparent hover:border-slate-700/60'
                  }`}
                >
                  <Icon className={`w-5 h-5 flex-shrink-0 ${isActive ? 'text-slate-950' : 'text-slate-400 group-hover:text-slate-200'}`} />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm leading-snug truncate">{item.label}</div>
                    <div className={`text-[11px] truncate ${isActive ? 'text-slate-800 font-medium' : 'text-slate-500 group-hover:text-slate-400'}`}>
                      {item.description}
                    </div>
                  </div>
                </button>
              );
            })}
          </nav>

          {/* Simple View Promo Box */}
          <div className="bg-slate-900/90 rounded-2xl p-4 border border-slate-800/90 shadow-xs space-y-2">
            <div className="flex items-center gap-2 text-amber-400 text-xs font-semibold uppercase tracking-wider">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Family Mode</span>
            </div>
            <p className="text-xs text-slate-400 leading-relaxed">
              Simple View provides clean high-contrast summaries for family members without trading jargon.
            </p>
            <button
              id="sidebar-simple-view-btn"
              onClick={() => setActiveScreen('SIMPLE_VIEW')}
              className={`w-full py-2 px-3 rounded-xl text-xs font-semibold transition-all flex items-center justify-center gap-1.5 cursor-pointer ${
                activeScreen === 'SIMPLE_VIEW'
                  ? 'bg-amber-400 text-slate-950 font-bold'
                  : 'bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700'
              }`}
            >
              Open Simple View
            </button>
          </div>

        </div>

        {/* Bottom Local Guarantee Card */}
        <div className="p-4 border-t border-slate-800/90">
          <div className="flex items-start gap-2.5 text-xs text-slate-400">
            <ShieldCheck className="w-4 h-4 text-emerald-400 flex-shrink-0 mt-0.5" />
            <div className="leading-snug">
              <span className="text-slate-200 font-semibold block">Local Device Vault</span>
              Encrypted locally on your computer. No central servers or tracking.
            </div>
          </div>
        </div>
      </aside>

      {/* Mobile Bottom Navigation Bar */}
      <nav className="md:hidden fixed bottom-10 left-0 right-0 bg-[#0F172A] border-t border-slate-800 z-30 flex items-center justify-around py-1.5 px-2 text-slate-400 backdrop-blur-md">
        {navItems.map(item => {
          const Icon = item.icon;
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              id={`mobile-nav-${item.id.toLowerCase()}`}
              onClick={() => setActiveScreen(item.id)}
              className={`flex flex-col items-center justify-center p-2 rounded-xl text-xs transition-colors min-w-[56px] min-h-[44px] ${
                isActive ? 'text-amber-400 font-bold' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              <Icon className="w-5 h-5 mb-0.5" />
              <span className="text-[10px] truncate">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
};
