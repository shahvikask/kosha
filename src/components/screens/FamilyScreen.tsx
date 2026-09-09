import React, { useState, useEffect } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { FamilyMember, FamilyRelation, isPrimaryFamilyMember } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { metricsOf } from '../../lib/holdingView';
import {
  decryptJson,
  isEncryptedBlob,
  maskPan,
} from '../../lib/vault';
import { LS_KEYS } from '../../db/migrate';
import {
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
  Shield,
  Download,
  Upload,
  Database,
  RotateCcw,
  Lock,
  Target,
  Coins,
} from 'lucide-react';

interface FamilyScreenProps {
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

export const FamilyScreen: React.FC<FamilyScreenProps> = ({ onOpenEmergencySheet }) => {
  const {
    familyMembers,
    addFamilyMember,
    updateFamilyMember,
    deleteFamilyMember,
    holdings,
    ledgerEvents,
    derived,
    sgbValuationMode,
    setSgbValuationMode,
    goals,
    upsertGoal,
    deleteGoal,
    exportBackupJson,
    importBackupJson,
    startOver,
    repo,
  } = useKosha();

  const [notification, setNotification] = useState<{
    type: 'success' | 'error' | 'warning';
    message: string;
  } | null>(null);

  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingMember, setEditingMember] = useState<FamilyMember | null>(null);
  const [formName, setFormName] = useState('');
  const [formRelation, setFormRelation] = useState<FamilyRelation>('Spouse');
  const [formPan, setFormPan] = useState('');
  const [formPanForStatements, setFormPanForStatements] = useState('');
  const [rememberPan, setRememberPan] = useState(false);
  const [formDob, setFormDob] = useState('');
  const [formColor, setFormColor] = useState('#0284c7');
  const [formNotes, setFormNotes] = useState('');
  const [deletingMember, setDeletingMember] = useState<FamilyMember | null>(null);

  const [goalName, setGoalName] = useState('');
  const [goalTarget, setGoalTarget] = useState('');
  const [goalDate, setGoalDate] = useState('');

  const [hasBackupCopy, setHasBackupCopy] = useState(false);
  useEffect(() => {
    const keys = Object.values(LS_KEYS);
    setHasBackupCopy(keys.some(k => localStorage.getItem(`${k}__pre_sqlite_backup`) !== null));
  }, []);

  const calculateAge = (dobString?: string): number | null => {
    if (!dobString) return null;
    const dob = new Date(dobString);
    if (isNaN(dob.getTime())) return null;
    return Math.abs(new Date(Date.now() - dob.getTime()).getUTCFullYear() - 1970);
  };

  const memberValue = (memberId: string) => {
    const hs = holdings.filter(h => h.ownerId === memberId);
    let val = 0n;
    hs.forEach(h => {
      const m = metricsOf(h, derived, sgbValuationMode);
      val += h.category === 'LIABILITY' ? -m.value : m.value;
    });
    return { count: hs.length, valuationMinor: val };
  };

  const handleOpenAddModal = () => {
    setEditingMember(null);
    setFormName('');
    setFormRelation('Spouse');
    setFormPan('');
    setFormPanForStatements('');
    setRememberPan(false);
    setFormDob('');
    setFormColor(COLOR_PALETTE[familyMembers.length % COLOR_PALETTE.length].hex);
    setFormNotes('');
    setIsFormOpen(true);
  };

  const handleOpenEditModal = (member: FamilyMember) => {
    setEditingMember(member);
    setFormName(member.name);
    setFormRelation(member.relation);
    setFormPan(member.panMasked || '');
    setFormPanForStatements(member.panForStatements || '');
    setRememberPan(Boolean(member.panForStatements));
    setFormDob(member.dateOfBirth || '');
    setFormColor(member.color || '#0284c7');
    setFormNotes(member.notes || '');
    setIsFormOpen(true);
  };

  const handleSaveMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) {
      setNotification({ type: 'error', message: 'Please enter a name.' });
      return;
    }
    const cleanPan = formPan.trim().toUpperCase();
    const panForStatementsVal = rememberPan
      ? formPanForStatements.trim().toUpperCase() ||
        (cleanPan && !cleanPan.includes('*') ? cleanPan : undefined)
      : undefined;
    try {
      if (editingMember) {
        await updateFamilyMember(editingMember.id, {
          name: formName.trim(),
          relation: isPrimaryFamilyMember(editingMember) ? 'Self' : formRelation,
          panMasked: cleanPan ? maskPan(cleanPan) : undefined,
          panForStatements: panForStatementsVal,
          dateOfBirth: formDob.trim() || undefined,
          color: formColor,
          notes: formNotes.trim() || undefined,
        });
        setNotification({ type: 'success', message: `Saved ${formName.trim()}.` });
      } else {
        await addFamilyMember({
          name: formName.trim(),
          relation:
            formRelation === 'Self' && familyMembers.some(m => isPrimaryFamilyMember(m))
              ? 'Spouse'
              : formRelation,
          panMasked: cleanPan ? maskPan(cleanPan) : undefined,
          panForStatements: panForStatementsVal,
          dateOfBirth: formDob.trim() || undefined,
          color: formColor,
          notes: formNotes.trim() || undefined,
        });
        setNotification({ type: 'success', message: `Added ${formName.trim()}.` });
      }
      setIsFormOpen(false);
      setEditingMember(null);
    } catch (err: any) {
      setNotification({ type: 'error', message: err?.message || 'Could not save this person.' });
    }
  };

  const handleConfirmDelete = async () => {
    if (!deletingMember) return;
    if (isPrimaryFamilyMember(deletingMember)) {
      setDeletingMember(null);
      setNotification({
        type: 'error',
        message: 'You cannot delete Self. You can still remove holdings.',
      });
      return;
    }
    const memberName = deletingMember.name;
    try {
      const { deletedHoldingsCount, deletedEventsCount } = await deleteFamilyMember(
        deletingMember.id
      );
      setDeletingMember(null);
      setNotification({
        type: 'warning',
        message: `Removed ${memberName} and ${deletedHoldingsCount} holdings / ${deletedEventsCount} records.`,
      });
    } catch (err: any) {
      setDeletingMember(null);
      setNotification({ type: 'error', message: err?.message || 'Could not delete this person.' });
    }
  };

  const handleExportBackup = async () => {
    const password = window.prompt('Choose a password to lock the backup file:');
    if (!password) return;
    try {
      const jsonStr = await exportBackupJson(password);
      const blob = new Blob([jsonStr], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `growurdhan_vault_${new Date().toISOString().split('T')[0]}.kosha`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      setNotification({ type: 'success', message: 'Backup saved to your Downloads folder.' });
    } catch (err: any) {
      setNotification({ type: 'error', message: err?.message || 'Could not export backup.' });
    }
  };

  const handleImportBackup = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = async event => {
      const content = event.target?.result as string;
      try {
        const parsed = JSON.parse(content);
        if (isEncryptedBlob(parsed)) {
          const password = window.prompt('Password for this backup:');
          if (!password) return;
          try {
            const decrypted = await decryptJson<any>(parsed, password);
            await importBackupJson(decrypted);
            setNotification({ type: 'success', message: 'Vault restored from backup.' });
          } catch {
            setNotification({
              type: 'error',
              message: 'Wrong password, or this file has been changed. Nothing was touched.',
            });
          }
        } else {
          await importBackupJson(parsed);
          setNotification({
            type: 'warning',
            message: 'This is an older unlocked backup. The next export will be locked.',
          });
        }
      } catch {
        setNotification({ type: 'error', message: 'That file is not a GrowUrDhan backup.' });
      }
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  const handleRebuildFromBackup = async () => {
    if (
      !window.confirm(
        'This will rebuild your records from the copy kept in browser storage. Nothing will be deleted.'
      )
    )
      return;
    try {
      for (const k of Object.values(LS_KEYS)) {
        const backupVal = localStorage.getItem(`${k}__pre_sqlite_backup`);
        if (backupVal !== null) localStorage.setItem(k, backupVal);
      }
      if (repo) await repo.setMeta('migrated_from_localstorage', 'false');
      await new Promise<void>((resolve, reject) => {
        const req = indexedDB.deleteDatabase('kosha-store');
        req.onsuccess = () => resolve();
        req.onerror = () => reject(req.error);
        req.onblocked = () => resolve();
      });
      window.location.reload();
    } catch (e: any) {
      setNotification({ type: 'error', message: e?.message || 'Could not rebuild from backup.' });
    }
  };

  const handleSaveGoal = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!goalName.trim()) return;
    const rupees = Number(goalTarget.replace(/,/g, ''));
    await upsertGoal({
      name: goalName.trim(),
      targetMinor: MoneyEngine.fromRupees(Number.isFinite(rupees) ? rupees : 0).toString(),
      targetDate: goalDate || undefined,
      ownerId: undefined,
    });
    setGoalName('');
    setGoalTarget('');
    setGoalDate('');
    setNotification({ type: 'success', message: `Goal “${goalName.trim()}” saved.` });
  };

  const inputStyle: React.CSSProperties = {
    background: 'var(--input)',
    borderColor: 'var(--border)',
    color: 'var(--text)',
  };

  return (
    <div className="space-y-6 pb-12">
      <div>
        <h1 className="text-2xl font-bold">Family</h1>
        <p className="text-sm" style={{ color: 'var(--muted)' }}>
          People, goals and the vault on this computer. Self cannot be deleted.
        </p>
      </div>

      {notification && (
        <div
          className="p-4 rounded-2xl border text-sm flex items-center justify-between"
          style={{
            borderColor: 'var(--border)',
            background: 'var(--card)',
            color: 'var(--text)',
          }}
        >
          <div className="flex items-center gap-2.5">
            {notification.type === 'success' ? (
              <CheckCircle2 className="w-4 h-4" style={{ color: 'var(--good)' }} />
            ) : (
              <AlertTriangle
                className="w-4 h-4"
                style={{ color: notification.type === 'warning' ? 'var(--accent)' : 'var(--bad)' }}
              />
            )}
            <span>{notification.message}</span>
          </div>
          <button onClick={() => setNotification(null)} className="text-xs font-bold">
            Dismiss
          </button>
        </div>
      )}

      <div className="card p-6 space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-bold flex items-center gap-2">
              <Users className="w-4 h-4" /> People in this vault
            </h2>
            <p className="text-xs mt-0.5" style={{ color: 'var(--muted)' }}>
              Add spouse, children, parents or an HUF. Deleting someone also removes their holdings.
            </p>
          </div>
          <button
            onClick={handleOpenAddModal}
            className="px-3.5 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5"
            style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
          >
            <Plus className="w-3.5 h-3.5" /> Add person
          </button>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {familyMembers.map(member => {
            const summaryData = memberValue(member.id);
            const age = calculateAge(member.dateOfBirth);
            const isHuf = member.relation === 'HUF';
            const isPrimary = isPrimaryFamilyMember(member);
            return (
              <div
                key={member.id}
                className="p-5 rounded-2xl border space-y-4"
                style={{ borderColor: 'var(--border)', background: 'var(--input)' }}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3">
                    <div
                      className="w-10 h-10 rounded-2xl text-white font-bold text-sm flex items-center justify-center"
                      style={{ backgroundColor: member.color }}
                    >
                      {isHuf ? <Building2 className="w-5 h-5" /> : member.name.charAt(0)}
                    </div>
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-bold text-sm">{member.name}</h3>
                        <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full border" style={{ borderColor: 'var(--border)' }}>
                          {isHuf ? 'HUF' : isPrimary ? 'Self · cannot delete' : member.relation}
                        </span>
                      </div>
                      {member.notes && (
                        <p className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>
                          {member.notes}
                        </p>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5">
                    <button
                      onClick={() => handleOpenEditModal(member)}
                      className="p-1.5 rounded-lg border text-xs flex items-center gap-1"
                      style={{ borderColor: 'var(--border)' }}
                    >
                      <Edit2 className="w-3.5 h-3.5" /> Edit
                    </button>
                    {isPrimary ? (
                      <span className="p-1.5 text-[11px]" style={{ color: 'var(--muted)' }}>
                        Locked
                      </span>
                    ) : (
                      <button
                        onClick={() => setDeletingMember(member)}
                        className="p-1.5 rounded-lg border text-xs flex items-center gap-1"
                        style={{ borderColor: 'var(--border)', color: 'var(--bad)' }}
                      >
                        <Trash2 className="w-3.5 h-3.5" /> Delete
                      </button>
                    )}
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div className="p-2.5 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
                    <div className="flex items-center gap-1.5" style={{ color: 'var(--muted)' }}>
                      <CreditCard className="w-3.5 h-3.5" /> PAN
                    </div>
                    <div className="font-mono font-semibold mt-0.5">
                      {member.panMasked || <span className="italic font-sans">Not set</span>}
                    </div>
                  </div>
                  <div className="p-2.5 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
                    <div className="flex items-center gap-1.5" style={{ color: 'var(--muted)' }}>
                      <Calendar className="w-3.5 h-3.5" /> Date of birth
                    </div>
                    <div className="font-semibold mt-0.5">
                      {member.dateOfBirth ? (
                        <span>
                          {new Date(member.dateOfBirth).toLocaleDateString('en-IN', {
                            day: 'numeric',
                            month: 'short',
                            year: 'numeric',
                          })}
                          {age !== null && (
                            <span className="font-normal" style={{ color: 'var(--muted)' }}>
                              {' '}
                              ({age} yrs)
                            </span>
                          )}
                        </span>
                      ) : (
                        <span className="italic font-sans">Not set</span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
                  <span style={{ color: 'var(--muted)' }}>
                    {summaryData.count} holding{summaryData.count === 1 ? '' : 's'}
                  </span>
                  <span className="font-bold">{MoneyEngine.formatRupees(summaryData.valuationMinor, false)}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="card p-6 space-y-4">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Target className="w-4 h-4" /> Goals
        </h2>
        <p className="text-xs" style={{ color: 'var(--muted)' }}>
          Name a goal (child’s college, house, retirement). Tag holdings with the same name when you add them manually.
        </p>
        {goals.length === 0 && (
          <p className="text-sm" style={{ color: 'var(--muted)' }}>
            No goals yet.
          </p>
        )}
        {goals.map(g => (
          <div
            key={g.id}
            className="flex items-center justify-between gap-3 py-2 border-b last:border-0"
            style={{ borderColor: 'var(--border)' }}
          >
            <div>
              <div className="font-semibold text-sm">{g.name}</div>
              <div className="text-xs" style={{ color: 'var(--muted)' }}>
                Target {MoneyEngine.formatRupees(BigInt(g.targetMinor || '0'), false)}
                {g.targetDate ? ` · by ${g.targetDate}` : ''}
              </div>
            </div>
            <button
              onClick={() => deleteGoal(g.id)}
              className="text-xs"
              style={{ color: 'var(--bad)' }}
            >
              Remove
            </button>
          </div>
        ))}
        <form onSubmit={handleSaveGoal} className="grid grid-cols-1 sm:grid-cols-4 gap-2 pt-2">
          <input
            value={goalName}
            onChange={e => setGoalName(e.target.value)}
            placeholder="e.g. Child’s college"
            className="px-3 py-2 rounded-xl border text-sm"
            style={inputStyle}
          />
          <input
            value={goalTarget}
            onChange={e => setGoalTarget(e.target.value)}
            placeholder="Target ₹"
            className="px-3 py-2 rounded-xl border text-sm"
            style={inputStyle}
          />
          <input
            type="date"
            value={goalDate}
            onChange={e => setGoalDate(e.target.value)}
            className="px-3 py-2 rounded-xl border text-sm"
            style={inputStyle}
          />
          <button
            type="submit"
            className="px-3 py-2 rounded-xl text-sm font-bold"
            style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
          >
            Save goal
          </button>
        </form>
      </div>

      <div className="card p-6 space-y-3">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Coins className="w-4 h-4" /> Gold bonds
        </h2>
        <p className="text-xs" style={{ color: 'var(--muted)' }}>
          Use the market price from your statement, or the RBI redemption rate if you have entered it.
        </p>
        <div className="flex gap-2">
          <button
            onClick={() => setSgbValuationMode('MARKET_TRADED')}
            className="px-3 py-1.5 rounded-full text-xs font-semibold border"
            style={
              sgbValuationMode === 'MARKET_TRADED'
                ? { background: 'var(--accent)', color: 'var(--accent-fg)', borderColor: 'transparent' }
                : { borderColor: 'var(--border)' }
            }
          >
            Market price
          </button>
          <button
            onClick={() => setSgbValuationMode('RBI_BENCHMARK')}
            className="px-3 py-1.5 rounded-full text-xs font-semibold border"
            style={
              sgbValuationMode === 'RBI_BENCHMARK'
                ? { background: 'var(--accent)', color: 'var(--accent-fg)', borderColor: 'transparent' }
                : { borderColor: 'var(--border)' }
            }
          >
            RBI benchmark
          </button>
        </div>
      </div>

      <div className="card p-6 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div>
            <h2 className="text-base font-bold flex items-center gap-2">
              <FileCheck className="w-5 h-5" /> Family printout
            </h2>
            <p className="text-xs mt-1 max-w-xl" style={{ color: 'var(--muted)' }}>
              A paper list of every folio and demat from this vault. No sample lockers or UAN numbers — only what the statements contain.
            </p>
          </div>
          <button
            onClick={onOpenEmergencySheet}
            className="px-4 py-2.5 rounded-xl text-sm font-bold flex items-center gap-2"
            style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
          >
            <Printer className="w-4 h-4" /> Print family sheet
          </button>
        </div>
      </div>

      <div className="card p-6 space-y-5">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Shield className="w-4 h-4" /> Vault on this computer
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
          <div className="p-3 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
            <div className="flex items-center gap-1.5" style={{ color: 'var(--muted)' }}>
              <Database className="w-3.5 h-3.5" /> Stored here
            </div>
            <div className="font-semibold mt-1">
              {familyMembers.length} people, {holdings.length} holdings, {ledgerEvents.length} records
            </div>
          </div>
          <div className="p-3 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
            <Lock className="w-3.5 h-3.5" style={{ color: 'var(--muted)' }} />
            <div className="font-semibold mt-1">Statements never leave this machine</div>
          </div>
          <div className="p-3 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
            <div className="font-semibold">If you lose the backup password, we cannot open the file.</div>
          </div>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
          <button
            onClick={handleExportBackup}
            className="p-4 rounded-2xl border text-left text-sm"
            style={{ borderColor: 'var(--border)' }}
          >
            <Download className="w-4 h-4 mb-2" />
            <div className="font-bold">Download backup</div>
            <div className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              Locked .kosha file
            </div>
          </button>
          <label className="p-4 rounded-2xl border text-left text-sm cursor-pointer" style={{ borderColor: 'var(--border)' }}>
            <Upload className="w-4 h-4 mb-2" />
            <div className="font-bold">Restore backup</div>
            <div className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              Load a previous file
            </div>
            <input type="file" accept=".kosha,.json" className="hidden" onChange={handleImportBackup} />
          </label>
          <button
            disabled={!hasBackupCopy}
            onClick={handleRebuildFromBackup}
            className="p-4 rounded-2xl border text-left text-sm disabled:opacity-50"
            style={{ borderColor: 'var(--border)' }}
          >
            <RotateCcw className="w-4 h-4 mb-2" />
            <div className="font-bold">Rebuild from old copy</div>
            <div className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              {hasBackupCopy ? 'Browser copy is available' : 'No old copy found'}
            </div>
          </button>
          <button
            onClick={async () => {
              if (
                window.confirm(
                  'Erase all holdings and records? Self stays. This cannot be undone unless you have a backup.'
                )
              ) {
                await startOver();
                setNotification({ type: 'warning', message: 'Vault emptied. Self is still here.' });
              }
            }}
            className="p-4 rounded-2xl border text-left text-sm"
            style={{ borderColor: 'var(--border)', color: 'var(--bad)' }}
          >
            <Trash2 className="w-4 h-4 mb-2" />
            <div className="font-bold">Start over</div>
            <div className="text-xs mt-1">Empty vault. No sample stocks.</div>
          </button>
        </div>
      </div>

      {isFormOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 overflow-y-auto">
          <div className="card max-w-lg w-full p-6 space-y-5 my-8">
            <div className="flex items-center justify-between">
              <h3 className="font-bold text-lg flex items-center gap-2">
                <UserCheck className="w-5 h-5" />
                {editingMember ? `Edit ${editingMember.name}` : 'Add a person'}
              </h3>
              <button onClick={() => setIsFormOpen(false)}>
                <X className="w-5 h-5" />
              </button>
            </div>
            <form onSubmit={handleSaveMember} className="space-y-4">
              <div>
                <label className="text-xs font-semibold block mb-1">Full name *</label>
                <input
                  required
                  value={formName}
                  onChange={e => setFormName(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border text-sm"
                  style={inputStyle}
                  placeholder="e.g. Ananya Shah"
                />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold block mb-1">Relation</label>
                  <select
                    value={editingMember && isPrimaryFamilyMember(editingMember) ? 'Self' : formRelation}
                    onChange={e => setFormRelation(e.target.value as FamilyRelation)}
                    disabled={Boolean(editingMember && isPrimaryFamilyMember(editingMember))}
                    className="w-full px-3.5 py-2.5 rounded-xl border text-sm"
                    style={inputStyle}
                  >
                    {(editingMember && isPrimaryFamilyMember(editingMember)) ||
                    (!editingMember && !familyMembers.some(m => isPrimaryFamilyMember(m))) ? (
                      <option value="Self">Self (cannot be deleted)</option>
                    ) : null}
                    <option value="Spouse">Spouse</option>
                    <option value="Child">Child</option>
                    <option value="Parent">Parent</option>
                    <option value="HUF">HUF</option>
                    <option value="Joint">Joint</option>
                  </select>
                </div>
                <div>
                  <label className="text-xs font-semibold block mb-1">Colour</label>
                  <div className="flex items-center gap-2 h-[42px] px-2 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
                    {COLOR_PALETTE.map(c => (
                      <button
                        key={c.hex}
                        type="button"
                        onClick={() => setFormColor(c.hex)}
                        className={`w-6 h-6 rounded-full ${formColor === c.hex ? 'ring-2 ring-offset-1' : 'opacity-70'}`}
                        style={{ backgroundColor: c.hex }}
                      />
                    ))}
                  </div>
                </div>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold block mb-1">PAN</label>
                  <input
                    maxLength={10}
                    value={rememberPan ? formPanForStatements : formPan}
                    onChange={e => {
                      const val = e.target.value.toUpperCase();
                      if (rememberPan) {
                        setFormPanForStatements(val);
                        setFormPan(val ? maskPan(val) : '');
                      } else {
                        setFormPan(val);
                      }
                    }}
                    className="w-full px-3.5 py-2.5 rounded-xl border text-sm font-mono"
                    style={inputStyle}
                    placeholder="ABCPS1234K"
                  />
                  <label className="flex items-center gap-2 mt-2 text-xs cursor-pointer">
                    <input
                      type="checkbox"
                      checked={rememberPan}
                      onChange={e => {
                        const checked = e.target.checked;
                        setRememberPan(checked);
                        if (checked && !formPanForStatements && formPan && !formPan.includes('*')) {
                          setFormPanForStatements(formPan);
                        } else if (!checked) {
                          setFormPanForStatements('');
                        }
                      }}
                    />
                    Remember PAN so statements unlock by themselves
                  </label>
                </div>
                <div>
                  <label className="text-xs font-semibold block mb-1">Date of birth</label>
                  <input
                    type="date"
                    value={formDob}
                    onChange={e => setFormDob(e.target.value)}
                    className="w-full px-3.5 py-2.5 rounded-xl border text-sm"
                    style={inputStyle}
                  />
                </div>
              </div>
              <div>
                <label className="text-xs font-semibold block mb-1">Notes</label>
                <input
                  value={formNotes}
                  onChange={e => setFormNotes(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl border text-sm"
                  style={inputStyle}
                  placeholder="Nominee on HDFC demat, etc."
                />
              </div>
              <div className="flex justify-end gap-2">
                <button type="button" onClick={() => setIsFormOpen(false)} className="px-4 py-2 rounded-xl text-xs font-semibold border" style={{ borderColor: 'var(--border)' }}>
                  Cancel
                </button>
                <button type="submit" className="px-5 py-2 rounded-xl text-xs font-bold" style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}>
                  {editingMember ? 'Save' : 'Add'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deletingMember && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="card max-w-md w-full p-6 space-y-4">
            <h3 className="font-bold text-lg">Delete {deletingMember.name}?</h3>
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              Their holdings and records will be removed. Self is never deleted.
            </p>
            <div className="text-xs space-y-1">
              <div>
                Holdings:{' '}
                <strong>{holdings.filter(h => h.ownerId === deletingMember.id).length}</strong>
              </div>
              <div>
                Records:{' '}
                <strong>{ledgerEvents.filter(e => e.ownerId === deletingMember.id).length}</strong>
              </div>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setDeletingMember(null)} className="px-4 py-2 rounded-xl text-xs font-semibold border" style={{ borderColor: 'var(--border)' }}>
                Keep
              </button>
              <button onClick={handleConfirmDelete} className="px-4 py-2 rounded-xl text-xs font-bold" style={{ background: 'var(--bad)', color: '#fff' }}>
                Delete
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
