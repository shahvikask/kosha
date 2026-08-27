import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { BrandLogo } from '../common/BrandLogo';
import {
  Users,
  Eye,
  EyeOff,
  Plus,
  ShieldCheck,
  ChevronDown,
  Sparkles,
  Layers,
  ArrowRightLeft,
} from 'lucide-react';

interface HeaderProps {
  onOpenAddHolding: () => void;
  onOpenAddRecord: () => void;
}

export const Header: React.FC<HeaderProps> = ({ onOpenAddHolding, onOpenAddRecord }) => {
  const {
    familyMembers,
    selectedMemberId,
    setSelectedMemberId,
    isPrivacyMasked,
    setIsPrivacyMasked,
    sgbValuationMode,
    setSgbValuationMode,
    activeScreen,
    setActiveScreen,
  } = useKosha();

  const [isFamilyDropdownOpen, setIsFamilyDropdownOpen] = useState(false);
  const [isAddMenuOpen, setIsAddMenuOpen] = useState(false);

  const selectedMember = familyMembers.find(m => m.id === selectedMemberId);

  return (
    <header className="bg-[#0F172A]/90 border-b border-slate-800 sticky top-0 z-30 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        
        {/* Left: Brand Logo & Tagline */}
        <div className="flex items-center gap-3">
          <div
            id="brand-logo"
            onClick={() => setActiveScreen('HOME')}
          >
            <BrandLogo />
          </div>
        </div>

        {/* Center: Family Member Selector */}
        <div className="relative">
          <button
            id="family-selector-btn"
            onClick={() => setIsFamilyDropdownOpen(!isFamilyDropdownOpen)}
            className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl border border-slate-700 bg-slate-900/90 hover:bg-slate-800 hover:border-slate-600 text-slate-200 text-sm font-medium transition-all shadow-xs"
            aria-label="Select family member view"
          >
            <div
              className="w-2.5 h-2.5 rounded-full ring-2 ring-slate-800"
              style={{
                backgroundColor:
                  selectedMemberId === 'ALL' ? '#38bdf8' : selectedMember?.color || '#38bdf8',
              }}
            />
            <span className="truncate max-w-[120px] sm:max-w-[180px]">
              {selectedMemberId === 'ALL' ? 'All Family Wealth' : selectedMember?.name}
            </span>
            <ChevronDown className="w-4 h-4 text-slate-400 ml-0.5" />
          </button>

          {isFamilyDropdownOpen && (
            <div
              className="absolute left-0 mt-1.5 w-60 bg-slate-900/95 backdrop-blur-md rounded-2xl shadow-2xl border border-slate-700 py-1.5 z-50 animate-in fade-in slide-in-from-top-1"
              onMouseLeave={() => setIsFamilyDropdownOpen(false)}
            >
              <div className="px-3 py-1.5 text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Viewing Portfolio For
              </div>
              <button
                id="select-all-family"
                onClick={() => {
                  setSelectedMemberId('ALL');
                  setIsFamilyDropdownOpen(false);
                }}
                className={`w-full text-left px-3.5 py-2 text-sm flex items-center justify-between hover:bg-slate-800/80 transition-colors ${
                  selectedMemberId === 'ALL' ? 'bg-sky-950/70 text-sky-300 font-semibold' : 'text-slate-300'
                }`}
              >
                <div className="flex items-center gap-2.5">
                  <div className="w-6 h-6 rounded-full bg-slate-800 text-white flex items-center justify-center text-xs font-medium border border-slate-700">
                    🏠
                  </div>
                  <span>Entire Family Combined</span>
                </div>
                {selectedMemberId === 'ALL' && <span className="w-1.5 h-1.5 rounded-full bg-sky-400" />}
              </button>

              <div className="h-px bg-slate-800 my-1" />

              {familyMembers.map(member => (
                <button
                  key={member.id}
                  id={`select-family-${member.id}`}
                  onClick={() => {
                    setSelectedMemberId(member.id);
                    setIsFamilyDropdownOpen(false);
                  }}
                  className={`w-full text-left px-3.5 py-2 text-sm flex items-center justify-between hover:bg-slate-800/80 transition-colors ${
                    selectedMemberId === member.id ? 'bg-sky-950/70 text-sky-300 font-semibold' : 'text-slate-300'
                  }`}
                >
                  <div className="flex items-center gap-2.5">
                    <div
                      className="w-6 h-6 rounded-full text-white flex items-center justify-center text-xs font-bold ring-1 ring-white/20"
                      style={{ backgroundColor: member.color }}
                    >
                      {member.name.charAt(0)}
                    </div>
                    <div>
                      <div className="text-slate-100 font-medium leading-tight">{member.name}</div>
                      <div className="text-[11px] text-slate-400">{member.relation}</div>
                    </div>
                  </div>
                  {selectedMemberId === member.id && <span className="w-1.5 h-1.5 rounded-full bg-sky-400" />}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Right Controls: SGB Toggle, Privacy Mask, Add Button */}
        <div className="flex items-center gap-2">
          
          {/* SGB Valuation Mode Badge (SRS §6.3) */}
          <button
            id="sgb-mode-toggle"
            title="Toggle Sovereign Gold Bond valuation between secondary market traded price and RBI Sovereign Gold benchmark rate"
            onClick={() => setSgbValuationMode(sgbValuationMode === 'MARKET_TRADED' ? 'RBI_BENCHMARK' : 'MARKET_TRADED')}
            className={`hidden md:flex items-center gap-1.5 px-2.5 py-1 rounded-xl border text-xs font-medium transition-colors ${
              sgbValuationMode === 'RBI_BENCHMARK'
                ? 'bg-amber-950/70 text-amber-300 border-amber-700/60'
                : 'bg-slate-900 border-slate-700 text-slate-300 hover:bg-slate-800'
            }`}
          >
            <span className="w-2 h-2 rounded-full bg-amber-400" />
            <span>SGB: {sgbValuationMode === 'RBI_BENCHMARK' ? 'RBI Sovereign Rate' : 'NSE Traded Rate'}</span>
          </button>

          {/* Privacy Eye Button */}
          <button
            id="privacy-toggle-btn"
            onClick={() => setIsPrivacyMasked(prev => !prev)}
            className={`p-2 rounded-xl border text-sm transition-colors flex items-center gap-1.5 ${
              isPrivacyMasked
                ? 'bg-amber-950/70 border-amber-700 text-amber-300'
                : 'bg-slate-900 border-slate-700 text-slate-300 hover:bg-slate-800 hover:text-white'
            }`}
            title={isPrivacyMasked ? 'Show numbers' : 'Hide numbers for privacy'}
            aria-label="Toggle amount privacy mask"
          >
            {isPrivacyMasked ? (
              <>
                <EyeOff className="w-4 h-4 text-amber-400" />
                <span className="text-xs font-medium hidden sm:inline">Hidden</span>
              </>
            ) : (
              <>
                <Eye className="w-4 h-4 text-slate-400" />
                <span className="text-xs font-medium hidden sm:inline">Visible</span>
              </>
            )}
          </button>

          {/* Simple View Switcher */}
          <button
            id="simple-view-toggle"
            onClick={() => setActiveScreen(activeScreen === 'SIMPLE_VIEW' ? 'HOME' : 'SIMPLE_VIEW')}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold border transition-colors hidden sm:flex items-center gap-1.5 ${
              activeScreen === 'SIMPLE_VIEW'
                ? 'bg-indigo-600 text-white border-indigo-500 shadow-md shadow-indigo-600/20'
                : 'bg-slate-900 text-slate-300 border-slate-700 hover:bg-slate-800 hover:text-white'
            }`}
          >
            <Sparkles className="w-3.5 h-3.5 text-amber-400" />
            <span>{activeScreen === 'SIMPLE_VIEW' ? 'Exit Simple View' : 'Simple View'}</span>
          </button>

          {/* + Add Investment Dropdown Button */}
          <div className="relative">
            <button
              id="header-add-btn"
              onClick={() => setIsAddMenuOpen(!isAddMenuOpen)}
              className="bg-amber-400 hover:bg-amber-300 text-slate-950 text-sm font-bold px-3.5 py-1.5 rounded-xl flex items-center gap-1.5 shadow-md shadow-amber-400/10 transition-all cursor-pointer"
            >
              <Plus className="w-4 h-4 text-slate-950 stroke-[2.5]" />
              <span className="hidden sm:inline">Add</span>
              <ChevronDown className="w-3.5 h-3.5 opacity-80" />
            </button>

            {isAddMenuOpen && (
              <div
                className="absolute right-0 mt-1.5 w-60 bg-slate-900/95 backdrop-blur-md rounded-2xl shadow-2xl border border-slate-700 py-1.5 z-50 animate-in fade-in"
                onMouseLeave={() => setIsAddMenuOpen(false)}
              >
                <button
                  id="add-holding-menu-item"
                  onClick={() => {
                    setIsAddMenuOpen(false);
                    onOpenAddHolding();
                  }}
                  className="w-full text-left px-3.5 py-2.5 text-sm text-slate-200 hover:bg-slate-800/90 flex items-center gap-2.5 transition-colors"
                >
                  <div className="p-1.5 rounded-lg bg-sky-950/80 text-sky-400 border border-sky-800/60">
                    <Plus className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="font-semibold text-white">Add New Holding</div>
                    <div className="text-xs text-slate-400">Stock, MF, SGB, PPF, Property, Loan</div>
                  </div>
                </button>

                <div className="h-px bg-slate-800 my-1" />

                <button
                  id="add-record-menu-item"
                  onClick={() => {
                    setIsAddMenuOpen(false);
                    onOpenAddRecord();
                  }}
                  className="w-full text-left px-3.5 py-2.5 text-sm text-slate-200 hover:bg-slate-800/90 flex items-center gap-2.5 transition-colors"
                >
                  <div className="p-1.5 rounded-lg bg-emerald-950/80 text-emerald-400 border border-emerald-800/60">
                    <ArrowRightLeft className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="font-semibold text-white">Record Transaction</div>
                    <div className="text-xs text-slate-400">Buy, Sell, Dividend, EMI, Bonus</div>
                  </div>
                </button>
              </div>
            )}
          </div>
        </div>

      </div>
    </header>
  );
};
