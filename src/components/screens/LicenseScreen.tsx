import React, { useState } from 'react';
import { Check, Lock, RefreshCw, Shield } from 'lucide-react';
import {
  LicenceState,
  PRICE_INR,
  canPurchaseSupport,
  canPurchaseLifetime,
  canPurchaseGmailDirect,
  canPurchaseGmailUpgrade,
  formatInr,
  purchaseLifetime,
  purchaseGmailPlan,
  purchaseSupport,
  restorePurchases,
} from '../../lib/license';

interface LicenseScreenProps {
  state: LicenceState;
  onChange: (next: LicenceState) => void;
}

export const LicenseScreen: React.FC<LicenseScreenProps> = ({ state, onChange }) => {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState('');

  const run = async (id: string, fn: () => Promise<LicenceState>) => {
    setBusy(id);
    setError('');
    try {
      onChange(await fn());
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Google Play could not complete that purchase.');
    } finally {
      setBusy(null);
    }
  };

  const expired = state.plan === 'expired';

  return (
    <div className="min-h-[100dvh] flex items-center justify-center p-5" style={{ background: 'var(--app)', color: 'var(--text)' }}>
      <div className="w-full max-w-md space-y-6">
        <div className="space-y-2 text-center">
          <div
            className="w-12 h-12 rounded-2xl mx-auto flex items-center justify-center"
            style={{ background: 'color-mix(in srgb, var(--accent) 18%, transparent)', color: 'var(--accent)' }}
          >
            <Lock className="w-5 h-5" />
          </div>
          <h1 className="text-2xl font-semibold tracking-tight">
            {state.sideloadBlocked ? 'This copy cannot be licensed' : expired ? 'Your 30-day trial has ended' : 'Choose a licence'}
          </h1>
          <p className="text-sm leading-relaxed" style={{ color: 'var(--muted)' }}>
            {state.sideloadBlocked
              ? 'This copy was not installed from Google Play. A copied APK cannot carry a licence — it lives on the Play account that bought it.'
              : 'The trial included every feature, including Gmail sync. A ₹249 licence keeps the vault and turns Gmail off. ₹499 keeps Gmail. After ₹249, only the ₹250 upgrade turns Gmail back on. The ₹199 yearly service is what unlocks new features. The app update itself still installs if that year ends.'}
          </p>
        </div>

        {!state.sideloadBlocked && (
        <div className="space-y-3">
          {canPurchaseLifetime(state) && (
            <PlanCard
              featured
              eyebrow="Manual"
              title={`Lifetime licence · ${formatInr(PRICE_INR.lifetime)}`}
              body="Pay once. The vault stays open. Gmail sync turns off, even if you used it during the trial. The ₹499 Gmail licence is then closed. You can add Gmail later for ₹250."
              cta={busy === 'a' ? 'Opening Play…' : `Buy ${formatInr(PRICE_INR.lifetime)}`}
              disabled={Boolean(busy)}
              onClick={() => run('a', purchaseLifetime)}
            />
          )}
          {canPurchaseGmailDirect(state) && (
            <PlanCard
              featured
              eyebrow="Gmail sync"
              title={`Gmail sync licence · ${formatInr(PRICE_INR.gmail)}`}
              body="Pay once. Includes the perpetual licence and Gmail sync. The ₹249 licence and the ₹250 upgrade are not offered after this."
              cta={busy === 'g' ? 'Opening Play…' : `Buy ${formatInr(PRICE_INR.gmail)}`}
              disabled={Boolean(busy)}
              onClick={() => run('g', purchaseGmailPlan)}
            />
          )}
          {canPurchaseGmailUpgrade(state) && (
            <PlanCard
              featured
              eyebrow="Upgrade"
              title={`Add Gmail sync · ${formatInr(PRICE_INR.gmailUpgrade)}`}
              body="The ₹249 licence is already on this Play account, so the ₹499 product stays closed. ₹250 turns Gmail sync back on."
              cta={busy === 'g' ? 'Opening Play…' : `Pay difference ${formatInr(PRICE_INR.gmailUpgrade)}`}
              disabled={Boolean(busy)}
              onClick={() => run('g', purchaseGmailPlan)}
            />
          )}
          {state.lifetime && !state.gmail && (
            <div className="p-4 rounded-3xl border space-y-1" style={{ borderColor: 'var(--border)', background: 'var(--card)' }}>
              <div className="text-[10px] font-bold uppercase tracking-[0.16em]" style={{ color: 'var(--good)' }}>Manual licence owned</div>
              <p className="text-sm font-semibold">₹249 is on this Play account. Gmail sync is off.</p>
            </div>
          )}
          {state.gmail && (
            <div className="p-4 rounded-3xl border space-y-1" style={{ borderColor: 'var(--border)', background: 'var(--card)' }}>
              <div className="text-[10px] font-bold uppercase tracking-[0.16em]" style={{ color: 'var(--good)' }}>Gmail sync owned</div>
              <p className="text-sm font-semibold">Perpetual licence with Gmail sync is on. The ₹249 and ₹250 products stay closed.</p>
            </div>
          )}
          {state.supportActive ? (
            <div className="p-4 rounded-3xl border space-y-1" style={{ borderColor: 'var(--border)', background: 'var(--card)' }}>
              <div className="text-[10px] font-bold uppercase tracking-[0.16em]" style={{ color: 'var(--good)' }}>Support active</div>
              <p className="text-sm font-semibold">Annual service is on. New features stay available until this paid year ends, including after you cancel. The perpetual licence stays either way.</p>
            </div>
          ) : (
            <PlanCard
              featured={state.lifetime}
              eyebrow="2 · Optional"
              title={`Annual service · ${formatInr(PRICE_INR.supportAnnual)} / year`}
              body={
                state.lifetime
                  ? 'While this year is paid, new features and improvements stay on. If you cancel, they stay until the paid period ends, then they lock. The app update still installs, and the perpetual licence stays.'
                  : 'Buy a perpetual licence first. The annual service cannot be purchased on its own. Without it, app updates still install, but new features stay off.'
              }
              cta={
                !canPurchaseSupport(state)
                  ? 'Lifetime licence required first'
                  : busy === 'c' ? 'Opening Play…' : `Subscribe ${formatInr(PRICE_INR.supportAnnual)} / year`
              }
              disabled={Boolean(busy) || !canPurchaseSupport(state)}
              onClick={() => run('c', purchaseSupport)}
            />
          )}
        </div>
        )}

        <button
          type="button"
          disabled={Boolean(busy)}
          onClick={() => run('restore', restorePurchases)}
          className="w-full py-3 rounded-2xl text-sm font-semibold flex items-center justify-center gap-2 border"
          style={{ borderColor: 'var(--border)', color: 'var(--text)' }}
        >
          <RefreshCw className="w-4 h-4" />
          Restore purchases on this Play account
        </button>

        {error && (
          <p className="text-xs text-center" style={{ color: 'var(--bad)' }}>{error}</p>
        )}

        <p className="text-[11px] leading-relaxed text-center" style={{ color: 'var(--subtle)' }}>
          <Shield className="w-3 h-3 inline mr-1" />
          Payments are processed by Google Play. GrowUrDhan never sees your card. A copied APK cannot carry this licence — it lives on the Play account.
        </p>
      </div>
    </div>
  );
};

const PlanCard: React.FC<{
  eyebrow: string;
  title: string;
  body: string;
  cta: string;
  featured?: boolean;
  disabled: boolean;
  onClick: () => void;
}> = ({ eyebrow, title, body, cta, featured, disabled, onClick }) => (
  <div
    className="p-4 rounded-3xl border space-y-3"
    style={{
      borderColor: featured ? 'var(--accent)' : 'var(--border)',
      background: featured ? 'color-mix(in srgb, var(--accent) 8%, var(--card))' : 'var(--card)',
    }}
  >
    <div className="text-[10px] font-bold uppercase tracking-[0.16em]" style={{ color: featured ? 'var(--accent)' : 'var(--muted)' }}>
      {eyebrow}
    </div>
    <h2 className="text-base font-semibold">{title}</h2>
    <p className="text-xs leading-relaxed" style={{ color: 'var(--muted)' }}>{body}</p>
    <button
      type="button"
      disabled={disabled}
      onClick={onClick}
      className="w-full py-3 rounded-2xl text-sm font-bold flex items-center justify-center gap-2"
      style={{ background: 'var(--accent)', color: 'var(--accent-fg)', opacity: disabled ? 0.6 : 1 }}
    >
      <Check className="w-4 h-4" />
      {cta}
    </button>
  </div>
);
