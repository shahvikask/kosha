import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { BrandLogo } from '../common/BrandLogo';
import { formatStatementDate, formatStatementDateShort } from '../../lib/casPython';
import {
  Eye,
  EyeOff,
  Plus,
  ChevronDown,
  Sun,
  Moon,
  ArrowRightLeft,
  RefreshCw,
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
    theme,
    setTheme,
    lastStatementAsOf,
    statementAsOfByMember,
    setActiveScreen,
    pricesAsOf,
    pricesRefreshing,
    refreshEodPrices,
  } = useKosha();

  const [isFamilyDropdownOpen, setIsFamilyDropdownOpen] = useState(false);
  const [isAddMenuOpen, setIsAddMenuOpen] = useState(false);
  const selectedMember = familyMembers.find(m => m.id === selectedMemberId);

  return (
    <header
      className="sticky top-0 z-30 border-b backdrop-blur-md"
      style={{ background: 'color-mix(in srgb, var(--chrome) 92%, transparent)', borderColor: 'var(--border)' }}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <button type="button" onClick={() => setActiveScreen('HOME')} className="cursor-pointer">
            <BrandLogo />
          </button>
        </div>

        <div className="relative">
          <button
            id="family-selector-btn"
            onClick={() => setIsFamilyDropdownOpen(!isFamilyDropdownOpen)}
            className="flex items-center gap-2 px-3.5 py-1.5 rounded-xl border text-sm font-medium"
            style={{ borderColor: 'var(--border)', background: 'var(--card)', color: 'var(--text)' }}
          >
            <div
              className="w-2.5 h-2.5 rounded-full"
              style={{ backgroundColor: selectedMemberId === 'ALL' ? '#38bdf8' : selectedMember?.color || '#38bdf8' }}
            />
            <span className="truncate max-w-[140px] sm:max-w-[200px]">
              {selectedMemberId === 'ALL' ? 'Whole family' : selectedMember?.name}
            </span>
            <ChevronDown className="w-4 h-4 opacity-60" />
          </button>

          {isFamilyDropdownOpen && (
            <div
              className="absolute left-0 mt-1.5 w-64 rounded-2xl shadow-2xl border py-1.5 z-50"
              style={{ background: 'var(--card)', borderColor: 'var(--border)' }}
            >
              <div className="px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: 'var(--muted)' }}>
                Show money for
              </div>
              <button
                onClick={() => {
                  setSelectedMemberId('ALL');
                  setIsFamilyDropdownOpen(false);
                }}
                className="w-full text-left px-3.5 py-2 text-sm hover:opacity-80"
                style={{ color: 'var(--text)' }}
              >
                Whole family
              </button>
              <div className="h-px my-1" style={{ background: 'var(--border)' }} />
              {familyMembers.map(member => (
                <button
                  key={member.id}
                  onClick={() => {
                    setSelectedMemberId(member.id);
                    setIsFamilyDropdownOpen(false);
                  }}
                  className="w-full text-left px-3.5 py-2 text-sm flex items-center gap-2.5"
                  style={{ color: 'var(--text)' }}
                >
                  <div
                    className="w-6 h-6 rounded-full text-white flex items-center justify-center text-xs font-bold"
                    style={{ backgroundColor: member.color }}
                  >
                    {member.name.charAt(0)}
                  </div>
                  <div>
                    <div className="font-medium leading-tight">{member.name}</div>
                    <div className="text-[11px]" style={{ color: 'var(--muted)' }}>{member.relation}</div>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => refreshEodPrices()}
            disabled={pricesRefreshing}
            className="p-2 rounded-xl border"
            style={{ borderColor: 'var(--border)', background: 'var(--card)', color: 'var(--text)' }}
            title={pricesAsOf ? `Prices as on ${formatStatementDateShort(pricesAsOf)}` : 'Refresh last close / NAV'}
          >
            <RefreshCw className={`w-4 h-4 ${pricesRefreshing ? 'animate-spin' : ''}`} />
          </button>

          <button
            onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            className="p-2 rounded-xl border"
            style={{ borderColor: 'var(--border)', background: 'var(--card)', color: 'var(--text)' }}
            title={theme === 'light' ? 'Switch to dark' : 'Switch to light'}
          >
            {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
          </button>

          <button
            id="privacy-toggle-btn"
            onClick={() => setIsPrivacyMasked(prev => !prev)}
            className="p-2 rounded-xl border flex items-center gap-1.5 text-sm"
            style={{
              borderColor: isPrivacyMasked ? 'var(--accent)' : 'var(--border)',
              background: 'var(--card)',
              color: 'var(--text)',
            }}
            title={isPrivacyMasked ? 'Show numbers' : 'Hide numbers'}
          >
            {isPrivacyMasked ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
            <span className="text-xs font-medium hidden sm:inline">{isPrivacyMasked ? 'Hidden' : 'Visible'}</span>
          </button>

          <div className="relative">
            <button
              id="header-add-btn"
              onClick={() => setIsAddMenuOpen(!isAddMenuOpen)}
              className="text-sm font-bold px-3.5 py-1.5 rounded-xl flex items-center gap-1.5"
              style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
            >
              <Plus className="w-4 h-4 stroke-[2.5]" />
              <span className="hidden sm:inline">Add</span>
              <ChevronDown className="w-3.5 h-3.5 opacity-80" />
            </button>
            {isAddMenuOpen && (
              <div
                className="absolute right-0 mt-1.5 w-60 rounded-2xl shadow-2xl border py-1.5 z-50"
                style={{ background: 'var(--card)', borderColor: 'var(--border)' }}
              >
                <button
                  onClick={() => {
                    setIsAddMenuOpen(false);
                    onOpenAddHolding();
                  }}
                  className="w-full text-left px-3.5 py-2.5 text-sm flex items-center gap-2.5"
                  style={{ color: 'var(--text)' }}
                >
                  <Plus className="w-4 h-4" />
                  <div>
                    <div className="font-semibold">Add manually</div>
                    <div className="text-xs" style={{ color: 'var(--muted)' }}>PPF, property, loan, gold</div>
                  </div>
                </button>
                <button
                  onClick={() => {
                    setIsAddMenuOpen(false);
                    onOpenAddRecord();
                  }}
                  className="w-full text-left px-3.5 py-2.5 text-sm flex items-center gap-2.5"
                  style={{ color: 'var(--text)' }}
                >
                  <ArrowRightLeft className="w-4 h-4" />
                  <div>
                    <div className="font-semibold">Record a buy or sell</div>
                    <div className="text-xs" style={{ color: 'var(--muted)' }}>Or dividend, EMI, bonus</div>
                  </div>
                </button>
              </div>
            )}
          </div>
        </div>
      </div>

      {selectedMemberId === 'ALL' && statementAsOfByMember.length > 0 ? (
        <div
          className="text-center text-xs py-1.5 border-t px-3"
          style={{ borderColor: 'var(--border)', color: 'var(--muted)', background: 'var(--app)' }}
        >
          The data is up to date until{' '}
          {statementAsOfByMember.map((m, i) => (
            <span key={m.id}>
              {i > 0 ? <span> · </span> : null}
              <span className="font-medium" style={{ color: 'var(--text)' }}>{m.name}</span>
              {' '}
              <span className="font-semibold" style={{ color: 'var(--accent)' }}>
                {formatStatementDateShort(m.asOf)}
              </span>
            </span>
          ))}
        </div>
      ) : lastStatementAsOf ? (
        <div
          className="text-center text-xs py-1.5 border-t"
          style={{ borderColor: 'var(--border)', color: 'var(--muted)', background: 'var(--app)' }}
        >
          The data is up to date until{' '}
          <span className="font-semibold" style={{ color: 'var(--accent)' }}>
            {formatStatementDate(lastStatementAsOf)}
          </span>
        </div>
      ) : null}
      {pricesAsOf && (
        <div
          className="text-center text-[11px] py-1 border-t"
          style={{ borderColor: 'var(--border)', color: 'var(--muted)', background: 'var(--app)' }}
        >
          Prices as on{' '}
          <span className="font-semibold" style={{ color: 'var(--text)' }}>
            {formatStatementDate(pricesAsOf)}
          </span>
          {' · '}NSE / BSE close and AMFI NAV — not live
        </div>
      )}
    </header>
  );
};
