import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { AppScreen } from '../../types';
import { Home, WalletCards, Landmark, FileText, Users, ShieldCheck } from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { activeScreen, setActiveScreen } = useKosha();

  const navItems: { id: AppScreen; label: string; description: string; icon: typeof Home }[] = [
    { id: 'HOME', label: 'Home', description: 'Family money at a glance', icon: Home },
    { id: 'HOLDINGS', label: 'Holdings', description: 'Everything you own', icon: WalletCards },
    { id: 'ACCOUNTS', label: 'Accounts', description: 'Demat and folios', icon: Landmark },
    { id: 'DOCUMENTS', label: 'Documents', description: 'Statements and tax', icon: FileText },
    { id: 'FAMILY', label: 'Family', description: 'People, goals, vault', icon: Users },
  ];

  return (
    <>
      <aside
        className="w-64 flex-shrink-0 flex-col justify-between hidden md:flex min-h-[calc(100vh-4rem-2.5rem)] border-r"
        style={{ background: 'color-mix(in srgb, var(--chrome) 70%, transparent)', borderColor: 'var(--border)', color: 'var(--text)' }}
      >
        <nav className="p-4 space-y-1.5" aria-label="Main">
          <div className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider" style={{ color: 'var(--muted)' }}>
            Menu
          </div>
          {navItems.map(item => {
            const Icon = item.icon;
            const isActive = activeScreen === item.id;
            return (
              <button
                key={item.id}
                id={`nav-btn-${item.id.toLowerCase()}`}
                onClick={() => setActiveScreen(item.id)}
                className="w-full flex items-center gap-3 px-3.5 py-3 rounded-2xl text-left"
                style={
                  isActive
                    ? { background: 'var(--accent)', color: 'var(--accent-fg)', fontWeight: 700 }
                    : { color: 'var(--text)' }
                }
              >
                <Icon className="w-5 h-5 flex-shrink-0" />
                <div className="min-w-0">
                  <div className="text-sm truncate">{item.label}</div>
                  <div className="text-[11px] truncate" style={{ opacity: isActive ? 0.75 : 0.55 }}>
                    {item.description}
                  </div>
                </div>
              </button>
            );
          })}
        </nav>
        <div className="p-4 border-t" style={{ borderColor: 'var(--border)' }}>
          <div className="flex items-start gap-2.5 text-xs" style={{ color: 'var(--muted)' }}>
            <ShieldCheck className="w-4 h-4 flex-shrink-0 mt-0.5" style={{ color: 'var(--good)' }} />
            <div>
              <span className="font-semibold block" style={{ color: 'var(--text)' }}>Stays on this computer</span>
              Statements never leave your machine.
            </div>
          </div>
        </div>
      </aside>

      <nav
        className="md:hidden fixed bottom-10 left-0 right-0 z-30 flex items-center justify-around py-1.5 px-1 border-t"
        style={{ background: 'var(--chrome)', borderColor: 'var(--border)', color: 'var(--muted)' }}
      >
        {navItems.map(item => {
          const Icon = item.icon;
          const isActive = activeScreen === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveScreen(item.id)}
              className="flex flex-col items-center gap-0.5 px-2 py-1 text-[10px]"
              style={{ color: isActive ? 'var(--accent)' : 'var(--muted)' }}
            >
              <Icon className="w-5 h-5" />
              <span>{item.label}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
};
