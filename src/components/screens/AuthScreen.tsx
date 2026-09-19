import React, { useEffect, useMemo, useState } from 'react';
import { useKosha } from '../../context/KoshaContext';
import { BrandLogo } from '../common/BrandLogo';
import {
  SECURITY_QUESTIONS,
  passwordIssues,
  isValidPan,
  isValidEmail,
  titleCaseName,
  readRememberedEmail,
} from '../../lib/auth';
import { pickBackupFile } from '../../lib/backupIo';

const FLOATS = [
  { t: 'Data will remain on your device.', top: '8%', left: '6%', rot: '-6deg' },
  { t: 'Tired of sharing your mobile number?', top: '18%', right: '5%', rot: '5deg' },
  { t: 'Scared your data will leak?', bottom: '22%', left: '4%', rot: '4deg' },
  { t: 'Service providers calling for no reason?', top: '42%', right: '3%', rot: '-4deg' },
  { t: 'Can you track family wealth anywhere else?', bottom: '10%', right: '8%', rot: '6deg' },
  { t: 'Do you want to see everything in one place?', bottom: '32%', left: '18%', rot: '-3deg' },
];

type Mode = 'register' | 'login' | 'forgot' | 'change' | 'restore';

export const AuthScreen: React.FC = () => {
  const {
    account,
    mustRotatePassword,
    registerOwner,
    login,
    changePassword,
    resetWithQuestions,
    restoreLockedBackup,
  } = useKosha();

  const [mode, setMode] = useState<Mode>(
    !account ? 'register' : mustRotatePassword ? 'change' : 'login',
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [email, setEmail] = useState(() => readRememberedEmail());
  const [password, setPassword] = useState('');
  const [pan, setPan] = useState('');
  const [dob, setDob] = useState('');
  const [q1Id, setQ1Id] = useState(SECURITY_QUESTIONS[0].id);
  const [q2Id, setQ2Id] = useState(SECURITY_QUESTIONS[1].id);
  const [q1Ans, setQ1Ans] = useState('');
  const [q2Ans, setQ2Ans] = useState('');
  const [oldPw, setOldPw] = useState('');
  const [restoreBytes, setRestoreBytes] = useState<Uint8Array | null>(null);
  const [newPw, setNewPw] = useState('');
  const [newPw2, setNewPw2] = useState('');

  /** New device: full identity. Already-has-vault (login page): email + password only. */
  const newDeviceRestore = !account;

  useEffect(() => {
    if (!account) setMode(m => (m === 'restore' ? m : 'register'));
    else if (mustRotatePassword) setMode('change');
    else setMode(m => (m === 'register' ? 'login' : m));
  }, [account, mustRotatePassword]);

  const greeting = useMemo(() => {
    if (!account) return null;
    if (email.trim().toLowerCase() === account.email.toLowerCase()) {
      return `${account.firstName} ${account.lastName}`.trim();
    }
    return null;
  }, [account, email]);

  const pwHints = passwordIssues(mode === 'register' ? password : newPw);

  const run = async (fn: () => Promise<string | null>) => {
    setBusy(true);
    setError('');
    try {
      const err = await fn();
      if (err) setError(err);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Something went wrong.');
    } finally {
      setBusy(false);
    }
  };

  const onRegister = () =>
    run(async () => {
      if (!titleCaseName(firstName) || !titleCaseName(lastName)) return 'Enter first and last name.';
      if (!isValidEmail(email)) return 'Enter a valid email. That is also your username.';
      if (pwHints.length) return `Password: ${pwHints.join(', ')}.`;
      if (!isValidPan(pan)) return 'PAN must be 10 characters, like ABCDE1234F.';
      if (!dob) return 'Enter date of birth.';
      if (q1Id === q2Id) return 'Pick two different questions.';
      if (!q1Ans.trim() || !q2Ans.trim()) return 'Answer both security questions.';
      return registerOwner({
        firstName,
        lastName,
        email,
        password,
        pan,
        dateOfBirth: dob,
        q1Id,
        q1Answer: q1Ans,
        q2Id,
        q2Answer: q2Ans,
      });
    });

  const onRestore = () =>
    run(async () => {
      if (!restoreBytes) return 'Choose a .kosha backup file first.';
      if (!isValidEmail(email)) return 'Enter the vault email.';
      if (!password) return 'Enter the vault password.';
      if (newDeviceRestore) {
        if (!isValidPan(pan)) return 'PAN must be 10 characters, like ABCDE1234F.';
        if (!dob) return 'Enter date of birth.';
        if (q1Id === q2Id) return 'Pick two different questions.';
        if (!q1Ans.trim() || !q2Ans.trim()) return 'Answer both security questions.';
        return restoreLockedBackup(restoreBytes, {
          email,
          password,
          pan,
          dateOfBirth: dob,
          q1Id,
          q1Answer: q1Ans,
          q2Id,
          q2Answer: q2Ans,
          strict: true,
        });
      }
      return restoreLockedBackup(restoreBytes, {
        email,
        password,
        strict: false,
      });
    });

  const chooseRestoreFile = () =>
    run(async () => {
      const bytes = await pickBackupFile();
      if (!bytes) return null;
      setRestoreBytes(bytes);
      return null;
    });

  const onLogin = () =>
    run(async () => {
      if (!email.trim() || !password) return 'Enter email and password.';
      return login(email, password);
    });

  const onForgot = () =>
    run(async () => {
      if (pwHints.length) return `New password: ${pwHints.join(', ')}.`;
      if (newPw !== newPw2) return 'New password and confirmation do not match.';
      return resetWithQuestions(email, q1Ans, q2Ans, newPw);
    });

  const onChange = () =>
    run(async () => {
      if (pwHints.length) return `New password: ${pwHints.join(', ')}.`;
      if (newPw !== newPw2) return 'New password and confirmation do not match.';
      return changePassword(oldPw, newPw);
    });

  const heading =
    mode === 'register' ? 'Create your vault'
    : mode === 'restore' ? (newDeviceRestore ? 'Restore on this device' : 'Restore this vault')
    : mode === 'forgot' ? 'Reset password'
    : mode === 'change' ? 'Password must be renewed'
    : 'Unlock your vault';

  return (
    <div className="min-h-screen relative overflow-hidden flex items-center justify-center p-4" style={{ background: 'var(--app)', color: 'var(--text)' }}>
      {FLOATS.map(f => (
        <div
          key={f.t}
          className="hidden md:block absolute max-w-[14rem] text-xs leading-snug pointer-events-none select-none"
          style={{
            top: f.top,
            left: f.left,
            right: f.right,
            bottom: f.bottom,
            transform: `rotate(${f.rot})`,
            color: 'var(--muted)',
            opacity: 0.35,
          }}
        >
          {f.t}
        </div>
      ))}

      <div className="relative z-10 w-full max-w-md">
        <div className="flex justify-center mb-6">
          <BrandLogo size="lg" />
        </div>
        <div className="card p-6 sm:p-8 space-y-4">
          <div>
            <h1 className="text-xl font-bold">{heading}</h1>
            <p className="text-xs mt-1" style={{ color: 'var(--muted)' }}>
              {mode === 'register'
                ? 'This becomes Self — it cannot be deleted. Money stays on this computer.'
                : mode === 'restore' && newDeviceRestore
                  ? 'Same email, password, PAN, date of birth and security answers you used at signup.'
                : mode === 'restore'
                  ? 'Email and password unlock the file. Records on this computer will be replaced.'
                : mode === 'change'
                  ? 'Passwords are renewed every six months.'
                  : 'Password every time. Your email is remembered on this computer.'}
            </p>
          </div>

          {mode === 'register' && (
            <>
              <div className="grid grid-cols-2 gap-3">
                <Field label="First name" value={firstName} onChange={v => setFirstName(titleCaseName(v))} autoComplete="given-name" />
                <Field label="Last name" value={lastName} onChange={v => setLastName(titleCaseName(v))} autoComplete="family-name" />
              </div>
              <Field label="Email (username)" value={email} onChange={setEmail} type="email" autoComplete="username" />
              <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete="new-password" />
              {password.length > 0 && pwHints.length > 0 && (
                <p className="text-[11px]" style={{ color: 'var(--bad)' }}>{pwHints.join(' · ')}</p>
              )}
              <Field label="PAN" value={pan} onChange={v => setPan(v.toUpperCase())} autoComplete="off" />
              <Field label="Date of birth" value={dob} onChange={setDob} type="date" />
              <Select label="Security question 1" value={q1Id} onChange={setQ1Id} options={SECURITY_QUESTIONS} />
              <Field label="Answer 1" value={q1Ans} onChange={setQ1Ans} />
              <Select label="Security question 2" value={q2Id} onChange={setQ2Id} options={SECURITY_QUESTIONS} />
              <Field label="Answer 2" value={q2Ans} onChange={setQ2Ans} />
              <p className="text-[11px]" style={{ color: 'var(--muted)' }}>
                Answers are not case sensitive. If you forget the password, these two answers reset it.
              </p>
              <Btn busy={busy} onClick={onRegister}>Create account</Btn>
              <button type="button" className="text-xs font-semibold w-full" style={{ color: 'var(--accent)' }} onClick={() => { setMode('restore'); setError(''); setRestoreBytes(null); }}>
                I changed device — restore a backup
              </button>
            </>
          )}

          {mode === 'restore' && (
            <>
              <button
                type="button"
                onClick={chooseRestoreFile}
                className="w-full px-3 py-3 rounded-xl border text-sm text-left"
                style={{ background: 'var(--input)', borderColor: restoreBytes ? 'var(--accent)' : 'var(--border)', color: 'var(--text)' }}
              >
                <div className="font-bold">{restoreBytes ? 'Backup file selected' : 'Choose .kosha backup'}</div>
                <div className="text-[11px] mt-0.5" style={{ color: 'var(--muted)' }}>
                  {restoreBytes
                    ? (newDeviceRestore ? 'Locked file ready. Fill the same details as signup.' : 'Locked file ready. Email and password decrypt it.')
                    : 'The file is encrypted. Opening it in Notepad shows noise, not your holdings.'}
                </div>
              </button>
              <Field label="Email (username)" value={email} onChange={setEmail} type="email" autoComplete="username" />
              <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete="current-password" />
              {newDeviceRestore && (
                <>
                  <Field label="PAN" value={pan} onChange={v => setPan(v.toUpperCase())} autoComplete="off" />
                  <Field label="Date of birth" value={dob} onChange={setDob} type="date" />
                  <Select label="Security question 1" value={q1Id} onChange={setQ1Id} options={SECURITY_QUESTIONS} />
                  <Field label="Answer 1" value={q1Ans} onChange={setQ1Ans} />
                  <Select label="Security question 2" value={q2Id} onChange={setQ2Id} options={SECURITY_QUESTIONS} />
                  <Field label="Answer 2" value={q2Ans} onChange={setQ2Ans} />
                </>
              )}
              <Btn busy={busy} onClick={onRestore}>Restore vault</Btn>
              <button
                type="button"
                className="text-xs font-semibold w-full"
                style={{ color: 'var(--accent)' }}
                onClick={() => {
                  setMode(newDeviceRestore ? 'register' : 'login');
                  setError('');
                  setRestoreBytes(null);
                }}
              >
                {newDeviceRestore ? 'Back to create account' : 'Back to unlock'}
              </button>
            </>
          )}

          {mode === 'login' && (
            <>
              <Field label="Email (username)" value={email} onChange={setEmail} type="email" autoComplete="username" />
              <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete="current-password" />
              {greeting && (
                <p className="text-sm font-medium text-center" style={{ color: 'var(--accent)' }}>
                  Welcome back, {greeting}
                </p>
              )}
              <Btn busy={busy} onClick={onLogin}>Unlock</Btn>
              <button type="button" className="text-xs font-semibold w-full" style={{ color: 'var(--accent)' }} onClick={() => { setMode('forgot'); setError(''); }}>
                Forgot password
              </button>
              <button type="button" className="text-xs font-semibold w-full" style={{ color: 'var(--accent)' }} onClick={() => { setMode('restore'); setError(''); setRestoreBytes(null); }}>
                Restore from a backup
              </button>
            </>
          )}

          {mode === 'forgot' && account && (
            <>
              <Field label="Email (username)" value={email} onChange={setEmail} type="email" />
              <Select label={SECURITY_QUESTIONS.find(q => q.id === account.q1Id)?.text || 'Question 1'} value={account.q1Id} onChange={() => {}} options={SECURITY_QUESTIONS.filter(q => q.id === account.q1Id)} />
              <Field label="Answer" value={q1Ans} onChange={setQ1Ans} />
              <Select label={SECURITY_QUESTIONS.find(q => q.id === account.q2Id)?.text || 'Question 2'} value={account.q2Id} onChange={() => {}} options={SECURITY_QUESTIONS.filter(q => q.id === account.q2Id)} />
              <Field label="Answer" value={q2Ans} onChange={setQ2Ans} />
              <Field label="New password" value={newPw} onChange={setNewPw} type="password" />
              <Field label="Confirm new password" value={newPw2} onChange={setNewPw2} type="password" />
              <Btn busy={busy} onClick={onForgot}>Reset password</Btn>
              <button type="button" className="text-xs font-semibold w-full" style={{ color: 'var(--accent)' }} onClick={() => { setMode('login'); setError(''); }}>
                Back to unlock
              </button>
            </>
          )}

          {mode === 'change' && (
            <>
              <Field label="Current password" value={oldPw} onChange={setOldPw} type="password" />
              <Field label="New password" value={newPw} onChange={setNewPw} type="password" />
              <Field label="Confirm new password" value={newPw2} onChange={setNewPw2} type="password" />
              {newPw.length > 0 && pwHints.length > 0 && (
                <p className="text-[11px]" style={{ color: 'var(--bad)' }}>{pwHints.join(' · ')}</p>
              )}
              <Btn busy={busy} onClick={onChange}>Save new password</Btn>
            </>
          )}

          {error && (
            <p className="text-xs font-medium" style={{ color: 'var(--bad)' }}>{error}</p>
          )}
        </div>
        <p className="text-[11px] text-center mt-4" style={{ color: 'var(--muted)' }}>
          No mobile number. No cloud login. The vault never leaves this computer.
        </p>
      </div>
    </div>
  );
};

const Field: React.FC<{
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: string;
  autoComplete?: string;
}> = ({ label, value, onChange, type = 'text', autoComplete }) => (
  <label className="block text-xs font-semibold space-y-1">
    <span>{label}</span>
    <input
      type={type}
      value={value}
      autoComplete={autoComplete}
      onChange={e => onChange(e.target.value)}
      className="w-full px-3 py-2 rounded-xl border text-sm font-normal"
      style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
    />
  </label>
);

const Select: React.FC<{
  label: string;
  value: string;
  onChange: (v: string) => void;
  options: { id: string; text: string }[];
}> = ({ label, value, onChange, options }) => (
  <label className="block text-xs font-semibold space-y-1">
    <span>{label}</span>
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className="w-full px-3 py-2 rounded-xl border text-sm font-normal"
      style={{ background: 'var(--input)', borderColor: 'var(--border)', color: 'var(--text)' }}
    >
      {options.map(o => (
        <option key={o.id} value={o.id}>{o.text}</option>
      ))}
    </select>
  </label>
);

const Btn: React.FC<{ busy: boolean; onClick: () => void; children: React.ReactNode }> = ({ busy, onClick, children }) => (
  <button
    type="button"
    disabled={busy}
    onClick={onClick}
    className="w-full py-2.5 rounded-xl text-sm font-bold"
    style={{ background: 'var(--accent)', color: 'var(--accent-fg)', opacity: busy ? 0.7 : 1 }}
  >
    {busy ? 'Please wait…' : children}
  </button>
);
