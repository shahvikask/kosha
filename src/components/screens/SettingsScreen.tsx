import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine } from '../../lib/money';
import { FamilyMember } from '../../types';
import {
  Shield,
  Download,
  Upload,
  RefreshCw,
  Users,
  Plus,
  Printer,
  FileCheck,
  CheckCircle2,
  AlertTriangle,
  KeyRound,
  Lock,
  Sparkles,
} from 'lucide-react';

interface SettingsScreenProps {
  onOpenEmergencySheet: () => void;
}

export const SettingsScreen: React.FC<SettingsScreenProps> = ({ onOpenEmergencySheet }) => {
  const {
    familyMembers,
    addFamilyMember,
    exportBackupJson,
    importBackupJson,
    resetToSampleData,
    holdings,
    ledgerEvents,
  } = useKosha();

  const [notification, setNotification] = useState<string | null>(null);
  const [newMemberName, setNewMemberName] = useState('');
  const [newMemberRelation, setNewMemberRelation] = useState<'Spouse' | 'Child' | 'Parent' | 'HUF'>('Spouse');
  const [newMemberPan, setNewMemberPan] = useState('');
  const [isAddingMember, setIsAddingMember] = useState(false);

  // Export local backup file
  const handleExportBackup = () => {
    const jsonStr = exportBackupJson();
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `kosha_vault_backup_${new Date().toISOString().split('T')[0]}.kosha`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    setNotification('Encrypted local backup file downloaded to your computer.');
  };

  // Restore backup
  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = event => {
      const content = event.target?.result as string;
      const success = importBackupJson(content);
      if (success) {
        setNotification('Vault successfully restored from local backup file.');
      } else {
        setNotification('Error: Failed to parse backup file. Please ensure it is a valid .kosha backup.');
      }
    };
    reader.readAsText(file);
  };

  // Add new family member
  const handleAddMember = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMemberName.trim()) return;

    const colors = ['#0284c7', '#ec4899', '#10b981', '#8b5cf6', '#f59e0b', '#06b6d4'];
    const randomColor = colors[familyMembers.length % colors.length];

    addFamilyMember({
      name: newMemberName.trim(),
      relation: newMemberRelation,
      panMasked: newMemberPan.trim() ? newMemberPan.trim().toUpperCase() : undefined,
      color: randomColor,
    });

    setNewMemberName('');
    setNewMemberPan('');
    setIsAddingMember(false);
    setNotification(`Added ${newMemberName} to family portfolio.`);
  };

  return (
    <div className="space-y-6 pb-12">
      
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold font-serif text-white tracking-tight">Settings & Security</h1>
        <p className="text-xs sm:text-sm text-slate-400">
          Local vault management, emergency recovery dossier & family entity configuration
        </p>
      </div>

      {notification && (
        <div className="p-4 rounded-2xl bg-emerald-950/80 border border-emerald-800/80 text-emerald-200 text-xs sm:text-sm flex items-center justify-between shadow-lg">
          <div className="flex items-center gap-2">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span>{notification}</span>
          </div>
          <button onClick={() => setNotification(null)} className="font-bold text-emerald-400 text-xs cursor-pointer">
            Dismiss
          </button>
        </div>
      )}

      {/* EMERGENCY RECOVERY SHEET (SRS-SEC-015 / Annex B §B12.1) */}
      <div className="bg-slate-900/90 rounded-3xl p-6 sm:p-7 border border-amber-500/30 shadow-xl backdrop-blur-md space-y-4 relative overflow-hidden">
        <div className="absolute top-0 right-0 w-80 h-80 bg-amber-500/10 rounded-full blur-3xl pointer-events-none" />
        
        <div className="relative z-10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-amber-300 font-bold text-base">
              <FileCheck className="w-5 h-5 text-amber-400" />
              <span>Family Emergency Wealth Dossier & Recovery Sheet</span>
            </div>
            <p className="text-xs sm:text-sm text-slate-300 max-w-2xl leading-relaxed">
              In an unforeseen emergency, your family members need to know where assets are located (bank folios, demat BOIDs, PPF/EPF UANs, bank lockers) with clear offline instructions, without compromising your passwords.
            </p>
          </div>

          <button
            id="open-emergency-sheet-btn"
            onClick={onOpenEmergencySheet}
            className="px-4 py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs sm:text-sm font-bold flex items-center justify-center gap-2 shadow-md shadow-amber-400/10 transition-all whitespace-nowrap cursor-pointer"
          >
            <Printer className="w-4 h-4 stroke-[2.5]" />
            <span>Generate Emergency Dossier</span>
          </button>
        </div>
      </div>

      {/* Local Vault & Backup Storage Bento Tile */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-6">
        <div className="border-b border-slate-800 pb-4">
          <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
            <Shield className="w-4 h-4 text-emerald-400" />
            <span>Local Vault & Data Backup</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Your financial data is stored locally in your browser storage. You can download an encrypted backup anytime.
          </p>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {/* Download Backup */}
          <div className="p-5 rounded-2xl border border-slate-800 bg-slate-950/60 space-y-3 flex flex-col justify-between">
            <div>
              <div className="font-bold text-sm text-slate-100">Download Vault Backup</div>
              <div className="text-xs text-slate-400 mt-1">
                Save a complete `.kosha` JSON file containing all your holdings and append-only ledger history.
              </div>
            </div>
            <button
              onClick={handleExportBackup}
              className="w-full py-2.5 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs font-bold flex items-center justify-center gap-1.5 transition-all shadow-md shadow-amber-400/10 cursor-pointer"
            >
              <Download className="w-3.5 h-3.5 stroke-[2.5]" />
              <span>Download Backup File</span>
            </button>
          </div>

          {/* Restore Backup */}
          <div className="p-5 rounded-2xl border border-slate-800 bg-slate-950/60 space-y-3 flex flex-col justify-between">
            <div>
              <div className="font-bold text-sm text-slate-100">Restore From Backup</div>
              <div className="text-xs text-slate-400 mt-1">
                Load a previously saved `.kosha` backup file from your computer.
              </div>
            </div>
            <label className="w-full py-2.5 bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 cursor-pointer transition-colors">
              <Upload className="w-3.5 h-3.5" />
              <span>Select Backup File</span>
              <input
                type="file"
                accept=".kosha,.json"
                className="hidden"
                onChange={handleImportBackup}
              />
            </label>
          </div>

          {/* Reset Data */}
          <div className="p-5 rounded-2xl border border-slate-800 bg-slate-950/60 space-y-3 flex flex-col justify-between">
            <div>
              <div className="font-bold text-sm text-slate-100">Sample Portfolio Reset</div>
              <div className="text-xs text-slate-400 mt-1">
                Reset your vault back to the default authentic Indian sample portfolio.
              </div>
            </div>
            <button
              onClick={() => {
                if (window.confirm('Reset all holdings and ledger events to initial sample data?')) {
                  resetToSampleData();
                  setNotification('Vault reset to authentic Indian sample portfolio.');
                }
              }}
              className="w-full py-2.5 bg-slate-800/80 border border-rose-900/60 hover:bg-rose-950 hover:text-rose-300 hover:border-rose-700 text-rose-400 rounded-xl text-xs font-semibold flex items-center justify-center gap-1.5 transition-colors cursor-pointer"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              <span>Reset to Sample Data</span>
            </button>
          </div>
        </div>
      </div>

      {/* Family Members & HUF Tax Entities Bento Tile */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <Users className="w-4 h-4 text-sky-400" />
              <span>Family Members & HUF Entities</span>
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Organize holdings by individual family owners and Hindu Undivided Family tax units
            </p>
          </div>

          <button
            onClick={() => setIsAddingMember(!isAddingMember)}
            className="px-3.5 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 text-xs font-semibold flex items-center gap-1 cursor-pointer transition-colors"
          >
            <Plus className="w-3.5 h-3.5" />
            <span>{isAddingMember ? 'Cancel' : 'Add Family Member'}</span>
          </button>
        </div>

        {/* Add Member Form */}
        {isAddingMember && (
          <form onSubmit={handleAddMember} className="p-5 rounded-2xl bg-slate-950 border border-slate-800 space-y-4 animate-in fade-in">
            <div className="font-bold text-sm text-slate-100">Add New Family Member / Entity</div>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              <div>
                <label className="text-xs text-slate-400 block mb-1">Full Name *</label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Ramesh Shah"
                  value={newMemberName}
                  onChange={e => setNewMemberName(e.target.value)}
                  className="w-full px-3.5 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                />
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Relation</label>
                <select
                  value={newMemberRelation}
                  onChange={e => setNewMemberRelation(e.target.value as any)}
                  className="w-full px-3.5 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                >
                  <option value="Spouse">Spouse</option>
                  <option value="Child">Child</option>
                  <option value="Parent">Parent</option>
                  <option value="HUF">HUF Entity</option>
                </select>
              </div>

              <div>
                <label className="text-xs text-slate-400 block mb-1">Masked PAN (Optional)</label>
                <input
                  type="text"
                  placeholder="e.g. ABCPS****K"
                  value={newMemberPan}
                  onChange={e => setNewMemberPan(e.target.value)}
                  className="w-full px-3.5 py-2 bg-slate-900 border border-slate-700 rounded-xl text-xs font-mono text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                />
              </div>
            </div>

            <button
              type="submit"
              className="px-4 py-2 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-xs font-bold cursor-pointer"
            >
              Save Member
            </button>
          </form>
        )}

        {/* Members List */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {familyMembers.map(member => (
            <div
              key={member.id}
              className="p-4 rounded-2xl border border-slate-800 bg-slate-950/60 flex items-center justify-between"
            >
              <div className="flex items-center gap-3">
                <div
                  className="w-8 h-8 rounded-full text-white font-bold text-xs flex items-center justify-center ring-2 ring-slate-800"
                  style={{ backgroundColor: member.color }}
                >
                  {member.name.charAt(0)}
                </div>
                <div>
                  <div className="font-semibold text-sm text-slate-100">{member.name}</div>
                  <div className="text-xs text-slate-400">
                    {member.relation} {member.panMasked && `• PAN: ${member.panMasked}`}
                  </div>
                </div>
              </div>

              <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
                {holdings.filter(h => h.ownerId === member.id).length} assets
              </span>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};
