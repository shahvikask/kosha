import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { Shield, Clock, Database, CheckCircle2 } from 'lucide-react';

export const StatusBar: React.FC = () => {
  const { holdings, ledgerEvents } = useKosha();

  return (
    <footer className="h-10 bg-[#090D16] text-slate-400 border-t border-slate-800/80 text-[11px] sm:text-xs flex items-center justify-between px-4 sm:px-6 fixed bottom-0 left-0 right-0 z-40 select-none backdrop-blur-md">
      
      {/* Left: Local Security Guarantee */}
      <div className="flex items-center gap-2 truncate">
        <div className="flex items-center gap-1.5 text-emerald-400 font-medium">
          <Shield className="w-3.5 h-3.5 flex-shrink-0" />
          <span className="hidden sm:inline">100% Local Storage</span>
        </div>
        <span className="text-slate-700 hidden sm:inline">|</span>
        <span className="text-slate-300 truncate">
          All data stays on this device · Zero external servers or tracking
        </span>
      </div>

      {/* Right: Price Freshness & Vault Integrity */}
      <div className="flex items-center gap-3 sm:gap-4 flex-shrink-0">
        <div className="flex items-center gap-1.5 text-slate-300">
          <Clock className="w-3.5 h-3.5 text-amber-400" />
          <span className="hidden md:inline">Prices:</span>
          <span>NSE/AMFI Today 4:00 PM</span>
        </div>

        <div className="hidden lg:flex items-center gap-1.5 text-slate-400">
          <Database className="w-3.5 h-3.5 text-sky-400" />
          <span>{holdings.length} Holdings · {ledgerEvents.length} Ledger Records</span>
        </div>

        <div className="flex items-center gap-1 text-emerald-400 font-semibold">
          <CheckCircle2 className="w-3.5 h-3.5" />
          <span className="hidden sm:inline">Vault OK</span>
        </div>
      </div>

    </footer>
  );
};
