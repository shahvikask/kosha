import React from 'react';
import { useKosha } from '../../context/KoshaContext';
import { ASSET_CATEGORIES } from '../../types';
import { MoneyEngine } from '../../lib/money';
import {
  X,
  Printer,
  ShieldCheck,
  Building,
  KeyRound,
  FileText,
  Coins,
  PhoneCall,
  CheckCircle2,
  AlertCircle,
} from 'lucide-react';

interface EmergencySheetModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const EmergencySheetModal: React.FC<EmergencySheetModalProps> = ({ isOpen, onClose }) => {
  const { holdings, familyMembers, summary } = useKosha();

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-md overflow-y-auto animate-in fade-in">
      <div
        className="bg-slate-900 rounded-3xl max-w-4xl w-full p-8 shadow-2xl border border-slate-800 max-h-[92vh] overflow-y-auto space-y-8 print:p-0 print:border-none print:shadow-none print:bg-white text-slate-100 print:text-black"
        onClick={e => e.stopPropagation()}
      >
        {/* Modal Controls (Hidden in Print) */}
        <div className="flex items-center justify-between border-b border-slate-800 pb-4 print:hidden">
          <div className="flex items-center gap-2 text-amber-400 font-bold text-lg font-serif">
            <ShieldCheck className="w-6 h-6 text-amber-400" />
            <span>Family Emergency Wealth Dossier & Recovery Guide</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => window.print()}
              className="px-4 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs sm:text-sm font-bold flex items-center gap-2 shadow-md shadow-amber-400/10 transition-all cursor-pointer"
            >
              <Printer className="w-4 h-4 stroke-[2.5]" />
              <span>Print to Paper / PDF</span>
            </button>
            <button
              onClick={onClose}
              className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Printable Document Body */}
        <div className="space-y-8 print:space-y-6">
          
          {/* Document Header */}
          <div className="border-b-2 border-slate-700 print:border-black pb-4">
            <div className="flex items-baseline justify-between">
              <div>
                <h1 className="text-2xl sm:text-3xl font-black font-serif text-white print:text-black tracking-tight">
                  CONFIDENTIAL FAMILY WEALTH & NOMINEE DOSSIER
                </h1>
                <p className="text-xs text-slate-400 print:text-slate-600 mt-1">
                  GrowUrDhan · Prepared on {new Date().toLocaleDateString('en-IN', { dateStyle: 'full' })}
                </p>
              </div>
              <div className="text-right">
                <span className="text-xs font-bold uppercase tracking-wider bg-slate-800 print:bg-slate-100 text-amber-400 print:text-black px-3 py-1 rounded-md border border-slate-700 print:border-slate-300">
                  Strictly Private
                </span>
              </div>
            </div>

            <div className="mt-4 p-4 bg-amber-950/40 print:bg-amber-50 rounded-2xl border border-amber-700/50 print:border-amber-200 text-xs text-amber-200 print:text-amber-950 leading-relaxed">
              <strong>Notice for Family Nominees & Legal Heirs: </strong>
              This document lists all active banking, demat, mutual fund, gold, and government investment holdings registered under the family name, along with their official institution identifiers. No online banking passwords are stored here.
            </div>
          </div>

          {/* Family Snapshot Summary */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-950/60 print:bg-slate-50 p-4 rounded-2xl border border-slate-800 print:border-slate-200">
            <div>
              <div className="text-[11px] text-slate-400 print:text-slate-500 font-semibold uppercase">Total Family Wealth</div>
              <div className="text-lg font-bold text-slate-100 print:text-slate-950">
                {MoneyEngine.formatRupees(summary.netWorthMinor, false)}
              </div>
              <div className="text-xs text-amber-400/90 print:text-slate-500 font-medium">
                ({MoneyEngine.formatCompact(summary.netWorthMinor)})
              </div>
            </div>

            <div>
              <div className="text-[11px] text-slate-400 print:text-slate-500 font-semibold uppercase">Total Assets</div>
              <div className="text-lg font-bold text-emerald-400 print:text-emerald-700">
                {MoneyEngine.formatRupees(summary.totalAssetsMinor, false)}
              </div>
            </div>

            <div>
              <div className="text-[11px] text-slate-400 print:text-slate-500 font-semibold uppercase">Outstanding Loans</div>
              <div className="text-lg font-bold text-rose-400 print:text-rose-600">
                {MoneyEngine.formatRupees(summary.totalLiabilitiesMinor, false)}
              </div>
            </div>

            <div>
              <div className="text-[11px] text-slate-400 print:text-slate-500 font-semibold uppercase">Family Members & Entities</div>
              <div className="text-xs font-bold text-slate-200 print:text-slate-900 mt-1 space-y-0.5">
                {familyMembers.map(m => (
                  <div key={m.id} className="truncate">
                    {m.name} ({m.relation}){m.panMasked ? ` · PAN: ${m.panMasked}` : ''}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Directory of Institutions & Assets */}
          <div className="space-y-4">
            <h2 className="text-base font-bold text-slate-100 print:text-black font-serif border-b border-slate-800 print:border-slate-200 pb-2">
              1. Institutional Directory & Registered Folios
            </h2>

            <div className="divide-y divide-slate-800 print:divide-slate-200 border border-slate-800 print:border-slate-200 rounded-2xl overflow-hidden text-xs">
              <div className="bg-slate-950 print:bg-slate-100 font-bold text-slate-300 print:text-slate-700 grid grid-cols-12 p-3">
                <div className="col-span-4">Asset / Holding Name</div>
                <div className="col-span-3">Institution / Broker</div>
                <div className="col-span-3">Folio / Demat BOID / Ref</div>
                <div className="col-span-2 text-right">Owner</div>
              </div>

              {holdings.map(h => {
                const owner = familyMembers.find(m => m.id === h.ownerId);
                return (
                  <div key={h.id} className="grid grid-cols-12 p-3 items-center hover:bg-slate-800/40 print:hover:bg-slate-50">
                    <div className="col-span-4 font-semibold text-slate-100 print:text-slate-900">
                      {h.name}
                      <div className="text-[11px] text-slate-400 print:text-slate-500 font-normal">{h.subCategory}</div>
                    </div>
                    <div className="col-span-3 text-slate-300 print:text-slate-700">
                      {h.institutionName || 'Direct / Bank'}
                    </div>
                    <div className="col-span-3 font-mono font-medium text-amber-400/90 print:text-slate-800">
                      {h.accountNumberMasked || h.symbolOrFolio}
                    </div>
                    <div className="col-span-2 text-right font-medium text-slate-200 print:text-slate-900">
                      {owner?.name || 'Self'}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Physical Assets & Bank Lockers */}
          <div className="space-y-4">
            <h2 className="text-base font-bold text-slate-100 print:text-black font-serif border-b border-slate-800 print:border-slate-200 pb-2">
              2. Physical Gold, Property Deeds & Bank Locker Access
            </h2>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-xs">
              <div className="p-4 bg-slate-950/60 print:bg-slate-50 rounded-2xl border border-slate-800 print:border-slate-200 space-y-2">
                <div className="font-bold text-slate-100 print:text-slate-900 flex items-center gap-1.5">
                  <Coins className="w-4 h-4 text-amber-400 print:text-amber-600" />
                  <span>Bank Safe Deposit Locker</span>
                </div>
                <p className="text-slate-300 print:text-slate-600 leading-relaxed">
                  <strong>Location:</strong> HDFC Bank, Indiranagar 100ft Road Branch, Bengaluru.<br />
                  <strong>Locker Number:</strong> #412 (Jointly operated by Vikas and Ananya).<br />
                  <strong>Key Location:</strong> Master bedroom safe drawer (Key #2). Contains 40 grams 24K MMTC-PAMP gold bullion and original property registration deeds.
                </p>
              </div>

              <div className="p-4 bg-slate-950/60 print:bg-slate-50 rounded-2xl border border-slate-800 print:border-slate-200 space-y-2">
                <div className="font-bold text-slate-100 print:text-slate-900 flex items-center gap-1.5">
                  <Building className="w-4 h-4 text-purple-400 print:text-purple-600" />
                  <span>Real Estate Property Documents</span>
                </div>
                <p className="text-slate-300 print:text-slate-600 leading-relaxed">
                  <strong>Property:</strong> 3BHK Apartment, Prestige Shantiniketan, Whitefield.<br />
                  <strong>Title Deeds:</strong> Original Sale Deed deposited with State Bank of India (Home Loan lien Doc #5892/2019). Khata Certificate and OC copy in home filing cabinet.
                </p>
              </div>
            </div>
          </div>

          {/* Nominee Transmission Roadmap */}
          <div className="space-y-4">
            <h2 className="text-base font-bold text-slate-100 print:text-black font-serif border-b border-slate-800 print:border-slate-200 pb-2">
              3. Step-by-Step Claim & Transmission Roadmap for Nominees
            </h2>

            <div className="space-y-3 text-xs text-slate-300 print:text-slate-700 leading-relaxed">
              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-amber-400 text-slate-950 print:bg-slate-900 print:text-white flex items-center justify-center font-bold text-[11px] flex-shrink-0 mt-0.5">
                  1
                </span>
                <div>
                  <strong className="text-white print:text-black">Mutual Funds & Demat Accounts (Zerodha / Groww / CAMS):</strong><br />
                  Submit <em>Form ISR-1</em> along with an attested copy of the Death Certificate to CAMS (for PPFAS/HDFC) and KFintech (for Mirae/UTI). Nominees are pre-registered across all folios.
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-amber-400 text-slate-950 print:bg-slate-900 print:text-white flex items-center justify-center font-bold text-[11px] flex-shrink-0 mt-0.5">
                  2
                </span>
                <div>
                  <strong className="text-white print:text-black">Sovereign Gold Bonds (RBI SGB):</strong><br />
                  Held in demat format through Zerodha. Nominee transmission occurs automatically via the Demat Depository Participant (CDSL).
                </div>
              </div>

              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-amber-400 text-slate-950 print:bg-slate-900 print:text-white flex items-center justify-center font-bold text-[11px] flex-shrink-0 mt-0.5">
                  3
                </span>
                <div>
                  <strong className="text-white print:text-black">PPF, EPF & NPS Accounts:</strong><br />
                  Submit the SBI PPF Form G and EPFO Nominee Claim form through the employer HR / EPFO portal using UAN 100829104829.
                </div>
              </div>
            </div>
          </div>

        </div>

      </div>
    </div>
  );
};
