import React, { useEffect, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { FamilyMember, FamilyRelation, isPrimaryFamilyMember } from '../../types';
import { MoneyEngine } from '../../lib/money';
import { metricsOf } from '../../lib/holdingView';
import { maskPan } from '../../lib/vault';
import { suggestedBackupName } from '../../lib/backup';
import { saveBackupFile, pickBackupFile } from '../../lib/backupIo';
import { useLicence } from '../../context/LicenseContext';
import { formatInr, PRICE_INR } from '../../lib/license';
import { rateOnGooglePlay } from '../../lib/feedback';
import {
  readBackupSchedule,
  writeBackupSchedule,
  pickBackupFolder,
  clearBackupFolder,
  subscribeBackupSchedule,
  BACKUP_KEEP_LAST,
  BackupScheduleSettings,
} from '../../lib/backupSchedule';
import { TermsGate } from './TermsGate';
import {
  Users,
  Plus,
  Printer,
  FileCheck,
  CheckCircle2,
  AlertTriangle,
  Edit2,
  Trash2,
  X,
  Building2,
  UserCheck,
  Shield,
  Download,
  Upload,
  Database,
  Lock,
  Target,
  Coins,
  Cloud,
  FolderOpen,
  Star,
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
    createLockedBackup,
    restoreLockedBackup,
    startOver,
    account,
  } = useKosha();
  const licence = useLicence();

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

  const [backupGate, setBackupGate] = useState<'export' | 'restore' | null>(null);
  const [backupEmail, setBackupEmail] = useState('');
  const [backupPassword, setBackupPassword] = useState('');
  const [backupBusy, setBackupBusy] = useState(false);
  const [restoreNeedsTerms, setRestoreNeedsTerms] = useState(false);
  const [schedule, setSchedule] = useState<BackupScheduleSettings>(() => readBackupSchedule());
  const [scheduleBusy, setScheduleBusy] = useState(false);

  useEffect(() => subscribeBackupSchedule(() => setSchedule(readBackupSchedule())), []);

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

  const handleExportBackup = () => {
    setBackupEmail(account?.email || '');
    setBackupPassword('');
    setBackupGate('export');
  };

  const handleImportBackup = () => {
    setBackupEmail(account?.email || '');
    setBackupPassword('');
    setBackupGate('restore');
  };

  const runBackupGate = async () => {
    if (!backupEmail.trim() || !backupPassword) {
      setNotification({ type: 'error', message: 'Enter your vault email and password.' });
      return;
    }
    setBackupBusy(true);
    try {
      if (backupGate === 'export') {
        const bytes = await createLockedBackup(backupEmail, backupPassword);
        const outcome = await saveBackupFile(suggestedBackupName(), bytes);
        if (outcome === 'cancelled') return;
        setBackupGate(null);
        setBackupPassword('');
        setNotification({ type: 'success', message: 'Locked backup saved. Only your vault email and password can open it.' });
      } else {
        const bytes = await pickBackupFile();
        if (!bytes) {
          setBackupBusy(false);
          return;
        }
        const err = await restoreLockedBackup(bytes, {
          email: backupEmail,
          password: backupPassword,
          strict: false,
        });
        if (err) {
          setNotification({ type: 'error', message: err });
        } else {
          setBackupGate(null);
          setBackupPassword('');
          setNotification({ type: 'success', message: 'Vault restored from backup.' });
        }
      }
    } catch (err: any) {
      setNotification({ type: 'error', message: err?.message || 'Could not finish backup.' });
    } finally {
      setBackupBusy(false);
    }
  };

  const onRestoreSubmit = () => {
    if (!backupEmail.trim() || !backupPassword) {
      setNotification({ type: 'error', message: 'Enter your vault email and password.' });
      return;
    }
    setRestoreNeedsTerms(true);
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
    <div className="space-y-4 md:space-y-6 pb-2 md:pb-8">
      <div>
        <h1 className="page-title text-2xl font-bold">Family</h1>
        <p className="page-lead text-sm" style={{ color: 'var(--muted)' }}>
          People, goals and the vault on this phone. Self cannot be deleted.
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

      <div className="card p-4 md:p-6 space-y-4 md:space-y-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-base font-bold flex items-center gap-2">
              <Users className="w-4 h-4" /> People in this vault
            </h2>
            <p className="text-xs mt-0.5 hidden md:block" style={{ color: 'var(--muted)' }}>
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

        <div className="grid grid-cols-1 gap-3">
          {familyMembers.map(member => {
            const summaryData = memberValue(member.id);
            const age = calculateAge(member.dateOfBirth);
            const isHuf = member.relation === 'HUF';
            const isPrimary = isPrimaryFamilyMember(member);
            return (
              <div
                key={member.id}
                className="p-3.5 md:p-5 rounded-2xl border space-y-3"
                style={{ borderColor: 'var(--border)', background: 'var(--input)' }}
              >
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <div
                      className="w-10 h-10 rounded-2xl text-white font-bold text-sm flex items-center justify-center shrink-0"
                      style={{ backgroundColor: member.color }}
                    >
                      {isHuf ? <Building2 className="w-5 h-5" /> : member.name.charAt(0)}
                    </div>
                    <div className="min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-bold text-sm">{member.name}</h3>
                        <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full border" style={{ borderColor: 'var(--border)' }}>
                          {isHuf ? 'HUF' : isPrimary ? 'Self' : member.relation}
                        </span>
                      </div>
                      <p className="text-[11px] mt-0.5 truncate" style={{ color: 'var(--muted)' }}>
                        {[
                          member.panMasked || 'PAN not set',
                          member.dateOfBirth
                            ? `${new Date(member.dateOfBirth).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}${age !== null ? ` · ${age} yrs` : ''}`
                            : null,
                          member.notes,
                        ].filter(Boolean).join(' · ')}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      onClick={() => handleOpenEditModal(member)}
                      className="p-2 rounded-lg border text-xs flex items-center gap-1 min-h-9"
                      style={{ borderColor: 'var(--border)' }}
                    >
                      <Edit2 className="w-3.5 h-3.5" /> <span className="hidden sm:inline">Edit</span>
                    </button>
                    {isPrimary ? (
                      <span className="p-1.5 text-[11px] hidden sm:inline" style={{ color: 'var(--muted)' }}>
                        Locked
                      </span>
                    ) : (
                      <button
                        onClick={() => setDeletingMember(member)}
                        className="p-2 rounded-lg border text-xs flex items-center gap-1 min-h-9"
                        style={{ borderColor: 'var(--border)', color: 'var(--bad)' }}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                </div>
                <div className="flex items-center justify-between text-xs pt-2 border-t" style={{ borderColor: 'var(--border)' }}>
                  <span style={{ color: 'var(--muted)' }}>
                    {summaryData.count} holding{summaryData.count === 1 ? '' : 's'}
                  </span>
                  <span className="font-bold tabular-nums">{MoneyEngine.formatRupees(summaryData.valuationMinor, false)}</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      <div className="card p-4 md:p-6 space-y-4">
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

      <div className="card p-4 md:p-6 space-y-3">
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

      <div className="card p-4 md:p-6 space-y-4">
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

      <div className="card p-4 md:p-6 space-y-4">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Shield className="w-4 h-4" /> Vault on this phone
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
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
            <div className="font-semibold mt-1">Statements stay here unless you save a backup</div>
          </div>
          <div className="p-3 rounded-xl border" style={{ borderColor: 'var(--border)' }}>
            <div className="font-semibold">Locked with your vault email and password. We cannot open the file if you forget them.</div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-2 md:gap-3">
          <button
            onClick={handleExportBackup}
            className="p-3 md:p-4 rounded-2xl border text-left text-sm"
            style={{ borderColor: 'var(--border)' }}
          >
            <Download className="w-4 h-4 mb-2" />
            <div className="font-bold">Download backup</div>
            <div className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              Encrypted .kosha — same login password
            </div>
          </button>
          <button
            type="button"
            onClick={handleImportBackup}
            className="p-3 md:p-4 rounded-2xl border text-left text-sm"
            style={{ borderColor: 'var(--border)' }}
          >
            <Upload className="w-4 h-4 mb-2" />
            <div className="font-bold">Restore backup</div>
            <div className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              Email and password unlock it
            </div>
          </button>
          <div
            className="p-3 md:p-4 rounded-2xl border text-left text-sm col-span-2 space-y-3"
            style={{ borderColor: 'var(--border)' }}
          >
            <div className="flex items-start gap-2">
              <Cloud className="w-4 h-4 mt-0.5" />
              <div>
                <div className="font-bold">Backup schedule</div>
                <div className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
                  Encrypted copies in a folder you pick — Google Drive, OneDrive, or Downloads. Last {BACKUP_KEEP_LAST} kept. Runs after unlock, not after the app is killed (the password is never stored).
                </div>
              </div>
            </div>
            <label className="flex items-center gap-2 text-sm font-semibold">
              <input
                type="checkbox"
                checked={schedule.enabled}
                onChange={e => writeBackupSchedule({ ...schedule, enabled: e.target.checked })}
              />
              Save automatically
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={schedule.onOpen}
                  disabled={!schedule.enabled}
                  onChange={e => writeBackupSchedule({ ...schedule, onOpen: e.target.checked })}
                />
                When the app opens
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={schedule.onCasImport}
                  disabled={!schedule.enabled}
                  onChange={e => writeBackupSchedule({ ...schedule, onCasImport: e.target.checked })}
                />
                When a new CAS is imported
              </label>
            </div>
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                disabled={scheduleBusy}
                onClick={async () => {
                  setScheduleBusy(true);
                  const next = await pickBackupFolder();
                  setSchedule(next);
                  setScheduleBusy(false);
                }}
                className="px-3 py-2 rounded-xl text-xs font-bold border flex items-center gap-1.5"
                style={{ borderColor: 'var(--border)' }}
              >
                <FolderOpen className="w-3.5 h-3.5" />
                {schedule.folderLabel ? 'Change folder' : 'Choose Drive or OneDrive folder'}
              </button>
              {schedule.folderLabel && (
                <button
                  type="button"
                  onClick={async () => setSchedule(await clearBackupFolder())}
                  className="px-3 py-2 rounded-xl text-xs font-semibold"
                  style={{ color: 'var(--muted)' }}
                >
                  Stop using folder
                </button>
              )}
            </div>
            <p className="text-[11px]" style={{ color: schedule.lastError ? 'var(--bad)' : 'var(--muted)' }}>
              {schedule.folderLabel ? `Folder: ${schedule.folderLabel}. ` : 'No folder chosen yet. '}
              {schedule.lastRunAt
                ? `Last backup ${new Date(schedule.lastRunAt).toLocaleString()} · ${schedule.lastKept} kept.`
                : 'No scheduled backup yet.'}
              {schedule.lastError ? ` ${schedule.lastError}` : ''}
            </p>
          </div>
          <button
            onClick={async () => {
              if (
                window.confirm(
                  'Delete all accounts from this device? Self stays. This cannot be undone unless you have a backup.'
                )
              ) {
                await startOver();
                setNotification({ type: 'warning', message: 'Vault emptied. Self is still here.' });
              }
            }}
            className="p-3 md:p-4 rounded-2xl border text-left text-sm col-span-2"
            style={{ borderColor: 'var(--border)', color: 'var(--bad)' }}
          >
            <Trash2 className="w-4 h-4 mb-2" />
            <div className="font-bold">Start over</div>
            <div className="text-xs mt-1">Delete all accounts from this device</div>
          </button>
        </div>
        <div className="p-4 rounded-2xl border space-y-2 text-sm" style={{ borderColor: 'var(--border)' }}>
          <div className="font-bold">Licence</div>
          <p className="text-xs leading-relaxed" style={{ color: 'var(--muted)' }}>
            {licence.state.lifetime
              ? licence.state.supportActive
                ? 'Lifetime plus support. Enhancements stay on.'
                : `Lifetime licence on this Play account. Support is optional at ${formatInr(PRICE_INR.supportAnnual)} / year.`
              : `Trial: ${licence.state.daysLeft} days left. Lifetime ${formatInr(PRICE_INR.lifetime)} is required first. Optional support ${formatInr(PRICE_INR.supportAnnual)} / year after that.`}
          </p>
          <div className="flex flex-wrap gap-3 text-xs font-semibold" style={{ color: 'var(--accent)' }}>
            <button type="button" onClick={() => void rateOnGooglePlay()} className="inline-flex items-center gap-1">
              <Star className="w-3 h-3" />
              Rate on Google Play
            </button>
            <a href="https://shahvikask.github.io/kosha/privacy.html" target="_blank" rel="noreferrer">Privacy policy</a>
            <a href="https://shahvikask.github.io/kosha/terms.html" target="_blank" rel="noreferrer">Terms</a>
            <a href="https://shahvikask.github.io/kosha/delete-account.html" target="_blank" rel="noreferrer">Delete account (web)</a>
          </div>
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

      {backupGate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4" style={{ background: 'color-mix(in srgb, var(--app) 70%, transparent)' }}>
          <div className="card p-6 w-full max-w-md space-y-4">
            <h3 className="font-bold text-lg">
              {backupGate === 'export' ? 'Lock backup with your vault login' : 'Unlock backup'}
            </h3>
            <p className="text-sm" style={{ color: 'var(--muted)' }}>
              {backupGate === 'export'
                ? 'Re-enter the same email and password you use to unlock GrowUrDhan. There is no second backup password.'
                : 'Enter the vault email and password that locked this file. The records on this phone will be replaced.'}
            </p>
            <label className="block text-xs font-semibold space-y-1">
              <span>Email (username)</span>
              <input
                type="email"
                value={backupEmail}
                onChange={e => setBackupEmail(e.target.value)}
                className="w-full px-3 py-2 rounded-xl border text-sm font-normal"
                style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
              />
            </label>
            <label className="block text-xs font-semibold space-y-1">
              <span>Password</span>
              <input
                type="password"
                value={backupPassword}
                onChange={e => setBackupPassword(e.target.value)}
                className="w-full px-3 py-2 rounded-xl border text-sm font-normal"
                style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
              />
            </label>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => { setBackupGate(null); setBackupPassword(''); }}
                className="px-4 py-2 rounded-xl text-xs font-semibold border"
                style={{ borderColor: 'var(--border)' }}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={backupBusy}
                onClick={backupGate === 'restore' ? onRestoreSubmit : runBackupGate}
                className="px-4 py-2 rounded-xl text-xs font-bold"
                style={{ background: 'var(--accent)', color: 'var(--accent-fg)', opacity: backupBusy ? 0.7 : 1 }}
              >
                {backupBusy ? 'Please wait…' : backupGate === 'export' ? 'Save locked file' : 'Choose file and restore'}
              </button>
            </div>
          </div>
        </div>
      )}

      {restoreNeedsTerms && (
        <TermsGate
          reason="restore"
          onAccept={() => {
            setRestoreNeedsTerms(false);
            void runBackupGate();
          }}
        />
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
