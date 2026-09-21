import React, { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { EMPTY_LICENCE, LicenceState, appUnlocked, enhancementsEnabled, loadLicence } from '../lib/license';
import { hasSeenOnboarding } from '../lib/onboarding';
import { seedFeedbackClock } from '../lib/feedback';

interface LicenceContextValue {
  ready: boolean;
  state: LicenceState;
  setState: (s: LicenceState) => void;
  unlocked: boolean;
  supportUnlocked: boolean;
  showOnboarding: boolean;
  dismissOnboarding: () => void;
}

const Ctx = createContext<LicenceContextValue | null>(null);

export const LicenceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [ready, setReady] = useState(false);
  const [state, setState] = useState<LicenceState>(EMPTY_LICENCE);
  const [showOnboarding, setShowOnboarding] = useState(() => !hasSeenOnboarding());

  useEffect(() => {
    let alive = true;
    (async () => {
      const next = await loadLicence();
      if (!alive) return;
      seedFeedbackClock(next.trialStartedAt);
      setState(next);
      setReady(true);
    })();
    return () => {
      alive = false;
    };
  }, []);

  const dismissOnboarding = useCallback(() => setShowOnboarding(false), []);

  const value = useMemo<LicenceContextValue>(
    () => ({
      ready,
      state,
      setState,
      unlocked: appUnlocked(state),
      supportUnlocked: enhancementsEnabled(state),
      showOnboarding,
      dismissOnboarding,
    }),
    [ready, state, showOnboarding, dismissOnboarding],
  );

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
};

export function useLicence(): LicenceContextValue {
  const v = useContext(Ctx);
  if (!v) throw new Error('LicenceProvider missing');
  return v;
}
