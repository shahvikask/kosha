import React, { useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { MoneyEngine, QTY_SCALE } from '../../lib/money';
import { FamilyMember, FamilyRelation } from '../../types';
import {
  encryptJson,
  decryptJson,
  isEncryptedBlob,
  generateRecoveryKey,
  maskPan,
  WrongPassphraseError,
} from '../../lib/vault';
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
  Edit2,
  Trash2,
  Calendar,
  CreditCard,
  X,
  Building2,
  UserCheck,
  Info,
  Lock,
} from 'lucide-react';

interface SettingsScreenProps {
  onOpenEmergencySheet: () => void;
}

const COLOR_PALETTE = [
  { label: 'Sky Blue', hex: '#0284c7' },
  { label: 'Pink', hex: '#ec4899' },
  { label: 'Emerald', hex: '#10b981' },
  { label: 'Purple', hex: '#8b5cf6' },
  { label: 'Amber', hex: '#f59e0b' },
  { label: 'Cyan', hex: '#06b6d4' },
  { label: 'Rose', hex: '#f43f5e' },
];

export const SettingsScreen: React.FC<SettingsScreenProps> = ({ onOpenEmergencySheet }) => {
  const {
    familyMembers,
    addFamilyMember,
    updateFamilyMember,
    deleteFamilyMember,
    exportBackupJson,
    importBackupJson,
    resetToSampleData,
    holdings,
    ledgerEvents,
  } = useKosha();

  const [notification, setNotification] = useState<{ type: 'success' | 'error' | 'warning'; message: string } | null>(null);
  
  // Member modal / form state
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingMember, setEditingMember] = useState<FamilyMember | null>(null);
  
  // Form fields
  const [formName, setFormName] = useState('');
  const [formRelation, setFormRelation] = useState<FamilyRelation>('Spouse');
  const [formPan, setFormPan] = useState('');
  const [formDob, setFormDob] = useState('');
  const [formColor, setFormColor] = useState('#0284c7');
  const [formNotes, setFormNotes] = useState('');

  // Delete confirmation modal state
  const [deletingMember, setDeletingMember] = useState<FamilyMember | null>(null);

  // Helper to calculate age from DOB
  const calculateAge = (dobString?: string): number | null => {
    if (!dobString) return null;
    const dob = new Date(dobString);
    if (isNaN(dob.getTime())) return null;
    const diffMs = Date.now() - dob.getTime();
    const ageDt = new Date(diffMs);
    return Math.abs(ageDt.getUTCFullYear() - 1970);
  };

  // Open add member modal
  const handleOpenAddModal = () => {
    setEditingMember(null);
    setFormName('');
    setFormRelation('Spouse');
    setFormPan('');
    setFormDob('');
    setFormColor(COLOR_PALETTE[familyMembers.length % COLOR_PALETTE.length].hex);
    setFormNotes('');
    setIsFormOpen(true);
  };

  // Open edit member modal
  const handleOpenEditModal = (member: FamilyMember) => {
    setEditingMember(member);
    setFormName(member.name);
    setFormRelation(member.relation);
    setFormPan(member.panMasked || '');
    setFormDob(member.dateOfBirth || '');
    setFormColor(member.color || '#0284c7');
    setFormNotes(member.notes || '');
    setIsFormOpen(true);
  };

  // Save Add or Edit Member
  const handleSaveMember = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) {
      setNotification({ type: 'error', message: 'Member name is required.' });
      return;
    }

    const cleanPan = formPan.trim().toUpperCase();

    if (editingMember) {
      // Update existing member
      updateFamilyMember(editingMember.id, {
        name: formName.trim(),
        relation: formRelation,
        panMasked: cleanPan ? maskPan(cleanPan) : undefined,
        dateOfBirth: formDob.trim() || undefined,
        color: formColor,
        notes: formNotes.trim() || undefined,
      });
      setNotification({
        type: 'success',
        message: `Updated profile details for ${formName.trim()} (PAN & DOB updated).`,
      });
    } else {
      // Add new member
      addFamilyMember({
        name: formName.trim(),
        relation: formRelation,
        panMasked: cleanPan ? maskPan(cleanPan) : undefined,
        dateOfBirth: formDob.trim() || undefined,
        color: formColor,
        notes: formNotes.trim() || undefined,
      });
      setNotification({
        type: 'success',
        message: `Added ${formName.trim()} to family portfolio.`,
      });
    }

    setIsFormOpen(false);
    setEditingMember(null);
  };

  // Confirm and execute cascade deletion
  const handleConfirmDelete = () => {
    if (!deletingMember) return;
    const memberName = deletingMember.name;
    const { deletedHoldingsCount, deletedEventsCount } = deleteFamilyMember(deletingMember.id);
    setDeletingMember(null);
    setNotification({
      type: 'warning',
      message: `Deleted "${memberName}" and permanently removed ${deletedHoldingsCount} associated asset holdings and ${deletedEventsCount} ledger records.`,
    });
  };

  // Export local backup file (Encrypted)
  const handleExportBackup = async () => {
    const password = window.prompt('Enter a password to encrypt your .kosha backup file:');
    if (!password) return;

    try {
      const backupData = {
        koshaVersion: '1.0.0',
        exportedAt: new Date().toISOString(),
        familyMembers,
        holdings,
        ledgerEvents,
      };
      const encryptedBlob = await encryptJson(backupData, password);
      const jsonStr = JSON.stringify(encryptedBlob, null, 2);
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `growurdhan_vault_backup_${new Date().toISOString().split('T')[0]}.kosha`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      setNotification({
        type: 'success',
        message: 'Encrypted backup exported successfully.',
      });
    } catch (err: any) {
      setNotification({
        type: 'error',
        message: err?.message || 'Failed to export backup.',
      });
    }
  };

  // Restore backup (Encrypted or Legacy Unencrypted)
  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = async event => {
      const content = event.target?.result as string;
      try {
        const parsed = JSON.parse(content);
        if (isEncryptedBlob(parsed)) {
          const password = window.prompt('Enter password to decrypt this .kosha backup:');
          if (!password) return;

          try {
            const decrypted = await decryptJson<any>(parsed, password);
            importBackupJson(decrypted);
            setNotification({
              type: 'success',
              message: 'Vault successfully restored from encrypted backup.',
            });
          } catch (err) {
            setNotification({
              type: 'error',
              message: 'Wrong password, or this file has been altered. Nothing was changed.',
            });
          }
        } else {
          importBackupJson(parsed);
          setNotification({
            type: 'warning',
            message: 'This is an older unencrypted backup. Your next export will be encrypted.',
          });
        }
      } catch (err) {
        setNotification({
          type: 'error',
          message: 'Error: Failed to parse backup file. Please ensure it is a valid backup file.',
        });
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  // Calculate member holdings summary
  const getMemberAssetSummary = (memberId: string) => {
    const memberHoldings = holdings.filter(h => h.ownerId === memberId);
    let totalValuationMinor = 0n;
    memberHoldings.forEach(h => {
      const qtyScaled = BigInt(h.quantityScaled || '0');
      const priceMinor = BigInt(h.currentPriceMinor || '0');
      const val = (qtyScaled * priceMinor) / QTY_SCALE;
      if (h.category === 'LIABILITY') {
        totalValuationMinor -= val;
      } else {
        totalValuationMinor += val;
      }
    });
    return {
      count: memberHoldings.length,
      valuationMinor: totalValuationMinor,
    };
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

      {/* Notification Toast */}
      {notification && (
        <div
          className={`p-4 rounded-2xl border text-xs sm:text-sm flex items-center justify-between shadow-lg animate-in fade-in slide-in-from-top-2 ${
            notification.type === 'success'
              ? 'bg-emerald-950/80 border-emerald-800/80 text-emerald-200'
              : notification.type === 'warning'
              ? 'bg-amber-950/80 border-amber-800/80 text-amber-200'
              : 'bg-rose-950/80 border-rose-800/80 text-rose-200'
          }`}
        >
          <div className="flex items-center gap-2.5">
            {notification.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            ) : notification.type === 'warning' ? (
              <AlertTriangle className="w-4 h-4 text-amber-400 flex-shrink-0" />
            ) : (
              <AlertTriangle className="w-4 h-4 text-rose-400 flex-shrink-0" />
            )}
            <span>{notification.message}</span>
          </div>
          <button
            onClick={() => setNotification(null)}
            className="font-bold text-xs cursor-pointer ml-3 opacity-80 hover:opacity-100"
          >
            Dismiss
          </button>
        </div>
      )}

      {/* FAMILY MEMBERS & HUF ENTITIES BENTO CARD */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 pb-4">
          <div>
            <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
              <Users className="w-4 h-4 text-sky-400" />
              <span>Family Members & HUF Entities</span>
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Manage family member profiles, PAN cards, Dates of Birth, and HUF tax entities
            </p>
          </div>

          <button
            id="add-family-member-btn"
            onClick={handleOpenAddModal}
            className="px-3.5 py-2 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold flex items-center gap-1.5 cursor-pointer transition-all shadow-md shadow-amber-400/10"
          >
            <Plus className="w-3.5 h-3.5 stroke-[2.5]" />
            <span>Add Member / HUF</span>
          </button>
        </div>

        {/* Member Cards Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {familyMembers.map(member => {
            const summaryData = getMemberAssetSummary(member.id);
            const age = calculateAge(member.dateOfBirth);
            const isHuf = member.relation === 'HUF';

            return (
              <div
                key={member.id}
                id={`member-card-${member.id}`}
                className="p-5 rounded-2xl border border-slate-800/90 bg-slate-950/70 hover:border-slate-700 transition-all flex flex-col justify-between space-y-4 relative group"
              >
                <div>
                  {/* Top Bar: Avatar, Name, Relation Badge, Actions */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-3">
                      <div
                        className="w-10 h-10 rounded-2xl text-white font-bold text-sm flex items-center justify-center ring-2 ring-slate-800 shadow-inner flex-shrink-0"
                        style={{ backgroundColor: member.color }}
                      >
                        {isHuf ? <Building2 className="w-5 h-5" /> : member.name.charAt(0)}
                      </div>
                      <div>
                        <div className="flex items-center gap-2">
                          <h3 className="font-bold text-sm text-slate-100">{member.name}</h3>
                          <span
                            className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${
                              isHuf
                                ? 'bg-purple-950/80 text-purple-300 border-purple-800/80'
                                : member.relation === 'Self'
                                ? 'bg-sky-950/80 text-sky-300 border-sky-800/80'
                                : 'bg-slate-800 text-slate-300 border-slate-700'
                            }`}
                          >
                            {member.relation === 'HUF' ? 'HUF Entity' : member.relation}
                          </span>
                        </div>
                        {member.notes && (
                          <p className="text-[11px] text-slate-400 mt-0.5 line-clamp-1">{member.notes}</p>
                        )}
                      </div>
                    </div>

                    {/* Action buttons */}
                    <div className="flex items-center gap-1.5 flex-shrink-0">
                      <button
                        onClick={() => handleOpenEditModal(member)}
                        className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700/80 transition-colors cursor-pointer text-xs flex items-center gap-1"
                        title="Edit Member, PAN & DOB"
                      >
                        <Edit2 className="w-3.5 h-3.5" />
                        <span className="hidden sm:inline text-[11px] font-medium">Edit</span>
                      </button>

                      <button
                        onClick={() => setDeletingMember(member)}
                        className="p-1.5 rounded-lg bg-slate-800/80 hover:bg-rose-950/80 text-slate-400 hover:text-rose-300 border border-slate-700/80 hover:border-rose-800/80 transition-colors cursor-pointer text-xs flex items-center gap-1"
                        title="Delete Member and associated investments"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                        <span className="hidden sm:inline text-[11px] font-medium">Delete</span>
                      </button>
                    </div>
                  </div>

                  {/* Attributes Grid (PAN, DOB, Age) */}
                  <div className="mt-4 grid grid-cols-2 gap-2 pt-3 border-t border-slate-900 text-xs">
                    <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800/70">
                      <div className="flex items-center gap-1.5 text-slate-400 text-[11px]">
                        <CreditCard className="w-3.5 h-3.5 text-amber-400" />
                        <span>PAN Card</span>
                      </div>
                      <div className="font-mono font-semibold text-slate-200 mt-0.5">
                        {member.panMasked || (
                          <span className="text-slate-500 font-sans text-xs italic">Not set</span>
                        )}
                      </div>
                    </div>

                    <div className="p-2.5 rounded-xl bg-slate-900/80 border border-slate-800/70">
                      <div className="flex items-center gap-1.5 text-slate-400 text-[11px]">
                        <Calendar className="w-3.5 h-3.5 text-sky-400" />
                        <span>Date of Birth</span>
                      </div>
                      <div className="font-semibold text-slate-200 mt-0.5">
                        {member.dateOfBirth ? (
                          <span>
                            {new Date(member.dateOfBirth).toLocaleDateString('en-IN', {
                              day: 'numeric',
                              month: 'short',
                              year: 'numeric',
                            })}{' '}
                            {age !== null && (
                              <span className="text-[11px] text-slate-400 font-normal">({age} yrs)</span>
                            )}
                          </span>
                        ) : (
                          <span className="text-slate-500 font-sans text-xs italic">Not set</span>
                        )}
                      </div>
                    </div>
                  </div>
                </div>

                {/* Bottom Footer: Holdings count & Net Worth Owned */}
                <div className="flex items-center justify-between pt-3 border-t border-slate-900 text-xs">
                  <span className="text-slate-400">
                    Portfolio Assets:{' '}
                    <strong className="text-slate-200">{summaryData.count}</strong>
                  </span>
                  <span className="font-bold text-emerald-400">
                    {MoneyEngine.formatRupees(summaryData.valuationMinor, false)}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* EMERGENCY RECOVERY SHEET BANNER */}
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
                  setNotification({
                    type: 'success',
                    message: 'Vault reset to authentic Indian sample portfolio.',
                  });
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

      {/* Privacy Section */}
      <div className="bg-slate-900/80 rounded-3xl p-6 border border-slate-800 backdrop-blur-md shadow-lg space-y-4">
        <div className="border-b border-slate-800 pb-3">
          <h2 className="text-base font-bold text-slate-100 flex items-center gap-2">
            <Lock className="w-4 h-4 text-emerald-400" />
            <span>Privacy</span>
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 text-xs">
          <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800 space-y-1.5">
            <div className="font-bold text-slate-200">Local Device Storage</div>
            <div className="text-slate-400 leading-relaxed">Your data is stored on this computer only</div>
          </div>

          <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800 space-y-1.5">
            <div className="font-bold text-slate-200">Passphrase Protection</div>
            <div className="text-slate-400 leading-relaxed">Backups you export are encrypted with your password</div>
          </div>

          <div className="p-4 rounded-2xl bg-slate-950/70 border border-slate-800 space-y-1.5">
            <div className="font-bold text-rose-400">Zero-Knowledge Key</div>
            <div className="text-slate-400 leading-relaxed">We cannot recover your password. If you lose it and lose your recovery sheet, your backups cannot be opened.</div>
          </div>
        </div>
      </div>

      {/* ADD / EDIT FAMILY MEMBER MODAL */}
      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 overflow-y-auto animate-in fade-in">
          <div className="bg-slate-900 border border-slate-700 rounded-3xl max-w-lg w-full p-6 shadow-2xl space-y-5 my-8">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3.5">
              <div className="flex items-center gap-2">
                <UserCheck className="w-5 h-5 text-amber-400" />
                <h3 className="font-serif font-bold text-lg text-white">
                  {editingMember ? `Edit ${editingMember.name}` : 'Add Family Member / HUF'}
                </h3>
              </div>
              <button
                onClick={() => {
                  setIsFormOpen(false);
                  setEditingMember(null);
                }}
                className="p-1 rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveMember} className="space-y-4">
              {/* Full Legal Name */}
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  Full Name / Legal Entity Name *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. Vikas Shah or Vikas Shah (HUF)"
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                />
              </div>

              {/* Relation / Entity Type */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">
                    Relationship / Entity Type *
                  </label>
                  <select
                    value={formRelation}
                    onChange={e => setFormRelation(e.target.value as FamilyRelation)}
                    className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50 cursor-pointer"
                  >
                    <option value="Self">Self (Primary)</option>
                    <option value="Spouse">Spouse</option>
                    <option value="Child">Child</option>
                    <option value="Parent">Parent</option>
                    <option value="HUF">HUF (Hindu Undivided Family)</option>
                    <option value="Joint">Joint Holding Unit</option>
                  </select>
                </div>

                {/* Avatar Color */}
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1">
                    Theme Color
                  </label>
                  <div className="flex items-center gap-2 h-[42px] px-2 bg-slate-950 border border-slate-700 rounded-xl">
                    {COLOR_PALETTE.map(c => (
                      <button
                        key={c.hex}
                        type="button"
                        onClick={() => setFormColor(c.hex)}
                        className={`w-6 h-6 rounded-full transition-transform cursor-pointer ${
                          formColor === c.hex ? 'scale-125 ring-2 ring-white' : 'opacity-70 hover:opacity-100'
                        }`}
                        style={{ backgroundColor: c.hex }}
                        title={c.label}
                      />
                    ))}
                  </div>
                </div>
              </div>

              {/* PAN Card & Date of Birth */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1 flex items-center justify-between">
                    <span>PAN Card Number</span>
                    <span className="text-[10px] text-slate-500 font-normal">10-digits</span>
                  </label>
                  <input
                    type="text"
                    maxLength={10}
                    placeholder="e.g. ABCPS1234K"
                    value={formPan}
                    onChange={e => setFormPan(e.target.value.toUpperCase())}
                    className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm font-mono uppercase text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                  />
                  <p className="text-[10px] text-slate-500 mt-1">
                    Used to decrypt CAS statements & compute tax limits
                  </p>
                </div>

                <div>
                  <label className="text-xs font-semibold text-slate-300 block mb-1 flex items-center justify-between">
                    <span>Date of Birth / Incorp</span>
                    {formDob && calculateAge(formDob) !== null && (
                      <span className="text-[10px] text-amber-400 font-semibold">
                        Age: {calculateAge(formDob)} yrs
                      </span>
                    )}
                  </label>
                  <input
                    type="date"
                    value={formDob}
                    onChange={e => setFormDob(e.target.value)}
                    className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                  />
                  <p className="text-[10px] text-slate-500 mt-1">
                    Senior citizen slab rules applied if 60+
                  </p>
                </div>
              </div>

              {/* Notes */}
              <div>
                <label className="text-xs font-semibold text-slate-300 block mb-1">
                  Private Notes & Demat/Bank Remarks
                </label>
                <input
                  type="text"
                  placeholder="e.g. Primary earner, Demat with Zerodha, Nominee on HDFC"
                  value={formNotes}
                  onChange={e => setFormNotes(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-700 rounded-xl text-sm text-slate-100 focus:outline-none focus:ring-2 focus:ring-amber-400/50"
                />
              </div>

              {/* Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => {
                    setIsFormOpen(false);
                    setEditingMember(null);
                  }}
                  className="px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold cursor-pointer transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2.5 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 text-xs font-bold cursor-pointer transition-all shadow-md shadow-amber-400/10"
                >
                  {editingMember ? 'Save Changes' : 'Add Family Member'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CASCADE DELETE CONFIRMATION MODAL */}
      {deletingMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4 animate-in fade-in">
          <div className="bg-slate-900 border border-rose-800/80 rounded-3xl max-w-md w-full p-6 shadow-2xl space-y-4">
            <div className="flex items-start gap-3">
              <div className="p-3 rounded-2xl bg-rose-950 text-rose-400 border border-rose-800/60 flex-shrink-0">
                <AlertTriangle className="w-6 h-6" />
              </div>
              <div className="space-y-1">
                <h3 className="font-serif font-bold text-lg text-white">
                  Delete "{deletingMember.name}"?
                </h3>
                <p className="text-xs text-rose-300 font-semibold">
                  Cascade Deletion: All investments will be permanently removed!
                </p>
              </div>
            </div>

            {/* Impact summary */}
            <div className="p-4 rounded-2xl bg-slate-950 border border-slate-800 space-y-2 text-xs text-slate-300">
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Associated Asset Holdings:</span>
                <strong className="text-rose-400">
                  {holdings.filter(h => h.ownerId === deletingMember.id).length} holdings
                </strong>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Associated Ledger Records:</span>
                <strong className="text-rose-400">
                  {ledgerEvents.filter(e => e.ownerId === deletingMember.id).length} transactions
                </strong>
              </div>
              <div className="flex items-center justify-between pt-2 border-t border-slate-900">
                <span className="text-slate-400">Total Valuation Removed:</span>
                <strong className="text-white">
                  {MoneyEngine.formatRupees(getMemberAssetSummary(deletingMember.id).valuationMinor, false)}
                </strong>
              </div>
            </div>

            <p className="text-xs text-slate-400 leading-relaxed">
              This action cannot be undone. All holdings, folios, and historical trades belonging to{' '}
              <strong className="text-slate-200">{deletingMember.name}</strong> will be erased from your local vault.
            </p>

            <div className="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                onClick={() => setDeletingMember(null)}
                className="px-4 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold cursor-pointer transition-colors"
              >
                Keep Member
              </button>
              <button
                type="button"
                onClick={handleConfirmDelete}
                className="px-4 py-2.5 rounded-xl bg-rose-600 hover:bg-rose-500 text-white text-xs font-bold cursor-pointer transition-all shadow-md shadow-rose-600/20"
              >
                Delete Member & All Investments
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};
