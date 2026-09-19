import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { AppScreen } from '../../types';
import { Home, WalletCards, Landmark, FileText, Users, ShieldCheck, ChevronRight } from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { activeScreen, setActiveScreen } = useKosha();

  const navItems: { id: AppScreen; label: string; description: string; icon: typeof Home; tint: string }[] = [
    { id: 'HOME', label: 'Home', description: 'Your family at a glance', icon: Home, tint: 'var(--accent)' },
    { id: 'HOLDINGS', label: 'Holdings', description: 'Everything you own', icon: WalletCards, tint: 'var(--bucket-shares)' },
    { id: 'ACCOUNTS', label: 'Accounts', description: 'Where money is held', icon: Landmark, tint: 'var(--bucket-bank)' },
    { id: 'DOCUMENTS', label: 'Documents', description: 'Statements & records', icon: FileText, tint: 'var(--bucket-govt)' },
    { id: 'FAMILY', label: 'Family', description: 'People, goals & vault', icon: Users, tint: 'var(--bucket-property)' },
  ];

  return (
    <>
      <aside
        className="w-64 flex-shrink-0 flex-col justify-between hidden md:flex min-h-[calc(100vh-4rem-2.5rem)] border-r"
        style={{ background: 'color-mix(in srgb, var(--chrome) 72%, transparent)', borderColor: 'var(--border)', color: 'var(--text)' }}
      >
        <nav className="p-4 space-y-1.5" aria-label="Main navigation">
          <div className="px-3 pb-2 text-[10px] font-bold uppercase tracking-[0.16em]" style={{ color: 'var(--muted)' }}>
            Your money
          </div>
          {navItems.map(item => {
            const Icon = item.icon;
            const isActive = activeScreen === item.id;
            return (
              <button
                key={item.id}
                id={`nav-btn-${item.id.toLowerCase()}`}
                onClick={() => setActiveScreen(item.id)}
                aria-current={isActive ? 'page' : undefined}
                className="group w-full flex items-center gap-3 px-3 py-3 rounded-2xl text-left transition-opacity hover:opacity-90"
                style={
                  isActive
                    ? { background: 'var(--accent)', color: 'var(--accent-fg)', fontWeight: 700 }
                    : { color: 'var(--text)' }
                }
              >
                <span
                  className="w-9 h-9 rounded-xl flex items-center justify-center flex-shrink-0"
                  style={{
                    background: isActive ? 'color-mix(in srgb, var(--accent-fg) 14%, transparent)' : `color-mix(in srgb, ${item.tint} 18%, transparent)`,
                    color: isActive ? 'var(--accent-fg)' : item.tint,
                  }}
                >
                  <Icon className="w-[18px] h-[18px]" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="text-sm truncate">{item.label}</div>
                  <div className="text-[11px] truncate" style={{ opacity: isActive ? 0.75 : 0.55 }}>
                    {item.description}
                  </div>
                </div>
                <ChevronRight className="w-3.5 h-3.5 opacity-0 group-hover:opacity-60 transition-opacity" />
              </button>
            );
          })}
        </nav>
        <div className="p-4 border-t" style={{ borderColor: 'var(--border)' }}>
          <div className="rounded-2xl border p-3.5 flex items-start gap-2.5" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
            <ShieldCheck className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: 'var(--good)' }} />
            <div className="text-[11px] leading-relaxed" style={{ color: 'var(--muted)' }}>
              <span className="font-semibold block text-xs mb-0.5" style={{ color: 'var(--text)' }}>Private by design</span>
              Your portfolio stays on this computer.
            </div>
          </div>
        </div>
      </aside>

      <nav
        className="md:hidden fixed bottom-10 left-0 right-0 z-30 flex items-center justify-around py-2 px-1 border-t shadow-lg"
        style={{ background: 'var(--chrome)', borderColor: 'var(--border)', color: 'var(--muted)' }}
        aria-label="Mobile navigation"
      >
        {navItems.map(item => {
          const Icon = item.icon;
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveScreen(item.id)}
              aria-current={isActive ? 'page' : undefined}
              className="flex min-w-0 flex-1 flex-col items-center gap-0.5 px-1 py-1 rounded-xl text-[10px] font-medium"
              style={{
                color: isActive ? item.tint : 'var(--muted)',
                background: isActive ? `color-mix(in srgb, ${item.tint} 16%, transparent)` : 'transparent',
              }}
            >
              <Icon className="w-5 h-5" />
              <span className="truncate max-w-full">{item.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
};
