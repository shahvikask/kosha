import React, { useEffect, useRef, useState } from 'react';
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
  Lock,
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
    lockVault,
  } = useKosha();

  const [isFamilyDropdownOpen, setIsFamilyDropdownOpen] = useState(false);
  const [isAddMenuOpen, setIsAddMenuOpen] = useState(false);
  const barRef = useRef<HTMLElement>(null);
  const selectedMember = familyMembers.find(m => m.id === selectedMemberId);
  const familyColor = selectedMemberId === 'ALL' ? '#38bdf8' : selectedMember?.color || '#38bdf8';
  const familyInitial = selectedMemberId === 'ALL' ? 'F' : (selectedMember?.name?.charAt(0) || 'F');

  const closeMenus = () => {
    setIsFamilyDropdownOpen(false);
    setIsAddMenuOpen(false);
  };

  useEffect(() => {
    const onPointer = (event: MouseEvent) => {
      if (!barRef.current) return;
      if (!barRef.current.contains(event.target as Node)) closeMenus();
    };
    document.addEventListener('mousedown', onPointer);
    return () => document.removeEventListener('mousedown', onPointer);
  }, []);

  const iconBtn: React.CSSProperties = {
    borderColor: 'var(--border)',
    background: 'var(--card)',
    color: 'var(--text)',
  };

  return (
    <header
      ref={barRef}
      className="sticky top-0 z-30 border-b backdrop-blur-md"
      style={{
        background: 'color-mix(in srgb, var(--chrome) 92%, transparent)',
        borderColor: 'var(--border)',
        paddingTop: 'env(safe-area-inset-top, 0px)',
      }}
    >
      <div className="max-w-7xl mx-auto px-2.5 md:px-6 lg:px-8 h-14 md:h-16 flex items-center gap-1.5 md:gap-2 min-w-0">
        <button
          type="button"
          onClick={() => {
            closeMenus();
            setActiveScreen('HOME');
          }}
          className="cursor-pointer shrink-0"
          aria-label="GrowUrDhan home"
        >
          <BrandLogo size="sm" showTagline={false} compact />
        </button>

        <div className="relative shrink-0">
          <button
            id="family-selector-btn"
            onClick={() => {
              setIsAddMenuOpen(false);
              setIsFamilyDropdownOpen(v => !v);
            }}
            className="flex items-center gap-1 h-9 pl-1 pr-1.5 md:pl-2.5 md:pr-3 md:gap-1.5 rounded-xl border"
            style={{ borderColor: 'var(--border)', background: 'var(--card)', color: 'var(--text)' }}
            aria-label={selectedMemberId === 'ALL' ? 'Whole family' : selectedMember?.name || 'Family'}
            aria-expanded={isFamilyDropdownOpen}
            title={selectedMemberId === 'ALL' ? 'Whole family' : selectedMember?.name}
          >
            <span
              className="w-7 h-7 rounded-lg text-white flex items-center justify-center text-xs font-bold shrink-0"
              style={{ backgroundColor: familyColor }}
            >
              {familyInitial}
            </span>
            <span className="hidden md:inline text-sm font-medium truncate max-w-[10rem]">
              {selectedMemberId === 'ALL' ? 'Whole family' : selectedMember?.name}
            </span>
            <ChevronDown className="w-3.5 h-3.5 opacity-60 shrink-0" />
          </button>

          {isFamilyDropdownOpen && (
            <div
              className="absolute left-0 mt-1.5 w-[min(16rem,calc(100vw-1.5rem))] rounded-2xl shadow-2xl border py-1.5 z-50"
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
                className="w-full text-left px-3.5 py-2.5 text-sm hover:opacity-80"
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
                  className="w-full text-left px-3.5 py-2.5 text-sm flex items-center gap-2.5"
                  style={{ color: 'var(--text)' }}
                >
                  <div
                    className="w-7 h-7 rounded-full text-white flex items-center justify-center text-xs font-bold"
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

        <div className="flex-1" />

        <div className="flex items-center gap-1 md:gap-2 shrink-0">
          <button
            onClick={() => refreshEodPrices()}
            disabled={pricesRefreshing}
            className="w-9 h-9 rounded-xl border flex items-center justify-center"
            style={iconBtn}
            title={pricesAsOf ? `Prices as on ${formatStatementDateShort(pricesAsOf)}` : 'Refresh last close / NAV'}
            aria-label="Refresh prices"
          >
            <RefreshCw className={`w-4 h-4 ${pricesRefreshing ? 'animate-spin' : ''}`} />
          </button>
          <button
            onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            className="w-9 h-9 rounded-xl border flex items-center justify-center"
            style={iconBtn}
            title={theme === 'light' ? 'Switch to dark' : 'Switch to light'}
            aria-label={theme === 'light' ? 'Dark theme' : 'Light theme'}
          >
            {theme === 'light' ? <Moon className="w-4 h-4" /> : <Sun className="w-4 h-4" />}
          </button>
          <button
            id="privacy-toggle-btn"
            onClick={() => setIsPrivacyMasked(prev => !prev)}
            className="w-9 h-9 rounded-xl border flex items-center justify-center"
            style={{
              borderColor: isPrivacyMasked ? 'var(--accent)' : 'var(--border)',
              background: 'var(--card)',
              color: 'var(--text)',
            }}
            title={isPrivacyMasked ? 'Show numbers' : 'Hide numbers'}
            aria-label={isPrivacyMasked ? 'Show numbers' : 'Hide numbers'}
          >
            {isPrivacyMasked ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
          <button
            onClick={() => lockVault()}
            className="w-9 h-9 rounded-xl border flex items-center justify-center"
            style={iconBtn}
            title="Lock"
            aria-label="Lock vault"
          >
            <Lock className="w-4 h-4" />
          </button>
        </div>

        <div className="relative shrink-0">
          <button
            id="header-add-btn"
            onClick={() => {
              setIsFamilyDropdownOpen(false);
              setIsAddMenuOpen(v => !v);
            }}
            className="h-9 w-9 md:w-auto text-sm font-bold px-0 md:px-3.5 rounded-xl flex items-center justify-center gap-1"
            style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
            aria-label="Add"
          >
            <Plus className="w-4 h-4 stroke-[2.5]" />
            <span className="hidden md:inline">Add</span>
            <ChevronDown className="w-3.5 h-3.5 opacity-80 hidden md:block" />
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

      {(selectedMemberId === 'ALL' && statementAsOfByMember.length > 0) ||
      lastStatementAsOf ||
      pricesAsOf ? (
        <div
          className="hidden md:flex items-center justify-between gap-4 text-xs py-1.5 border-t px-6 lg:px-8"
          style={{ borderColor: 'var(--border)', color: 'var(--muted)', background: 'var(--app)' }}
        >
          <div className="min-w-0 truncate text-left">
            {selectedMemberId === 'ALL' && statementAsOfByMember.length > 0 ? (
              <>
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
              </>
            ) : lastStatementAsOf ? (
              <>
                The data is up to date until{' '}
                <span className="font-semibold" style={{ color: 'var(--accent)' }}>
                  {formatStatementDate(lastStatementAsOf)}
                </span>
              </>
            ) : null}
          </div>
          {pricesAsOf && (
            <div className="shrink-0 text-right">
              Prices as on{' '}
              <span className="font-semibold" style={{ color: 'var(--text)' }}>
                {formatStatementDate(pricesAsOf)}
              </span>
              {' · '}NSE / BSE close and AMFI NAV — not live
            </div>
          )}
        </div>
      ) : null}
    </header>
  );
};
