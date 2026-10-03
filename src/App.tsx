import React, { useEffect, useState } from 'react';
import { KoshaProvider, useKosha } from './context/KoshaContext';
import { LicenceProvider, useLicence } from './context/LicenseContext';
import { Header } from './components/layout/Header';
import { Sidebar } from './components/layout/Sidebar';
import { StatusBar } from './components/layout/StatusBar';
import { HomeScreen } from './components/screens/HomeScreen';
import { HoldingsScreen } from './components/screens/HoldingsScreen';
import { AccountsScreen } from './components/screens/AccountsScreen';
import { DocumentsScreen } from './components/screens/DocumentsScreen';
import { FamilyScreen } from './components/screens/FamilyScreen';
import { AddHoldingModal } from './components/modals/AddHoldingModal';
import { AddRecordModal } from './components/modals/AddRecordModal';
import { HoldingDetailModal } from './components/modals/HoldingDetailModal';
import { EmergencySheetModal } from './components/modals/EmergencySheetModal';
import { AuthScreen } from './components/screens/AuthScreen';
import { OnboardingScreen } from './components/screens/OnboardingScreen';
import { LicenseScreen } from './components/screens/LicenseScreen';
import { ClosedScreen } from './components/screens/TermsGate';
import { FeedbackPrompt } from './components/screens/FeedbackPrompt';
import { markOnboardingSeen } from './lib/onboarding';
import { maybeRunScheduledBackup } from './lib/backupSchedule';
import { runGmailSync } from './lib/gmailSync';
import { setTermsDeclineHandler } from './lib/terms';
import { claimFeedbackPrompt, loadFeedbackState } from './lib/feedback';
import { AUTO_LOCK_MS, installFileInputPickerGuard, isNativePickerOpen } from './lib/autoLock';
import { bindHeldIsins } from './lib/eodPrices';
import { AlertTriangle, CheckCircle2, RefreshCw, X } from 'lucide-react';

const KoshaMainApp: React.FC = () => {
  const {
    activeScreen,
    dbReady,
    dbError,
    migration,
    account,
    unlocked,
    mustRotatePassword,
    holdings,
    lockVault,
    pricesError,
    familyMembers,
    casLatest,
    importStatementRecords,
    updateFamilyMember,
    gmailFileSeen,
    markGmailFileSeen,
  } = useKosha();
  const licence = useLicence();

  const [isAddHoldingOpen, setIsAddHoldingOpen] = useState(false);
  const [isAddRecordOpen, setIsAddRecordOpen] = useState(false);
  const [preselectedHoldingForRecord, setPreselectedHoldingForRecord] = useState<string | undefined>(undefined);
  const [selectedHoldingIdForDetail, setSelectedHoldingIdForDetail] = useState<string | null>(null);
  const [isEmergencySheetOpen, setIsEmergencySheetOpen] = useState(false);
  const [isMigrationBannerDismissed, setIsMigrationBannerDismissed] = useState(false);
  const [termsDeclined, setTermsDeclined] = useState(false);
  const [feedbackOpen, setFeedbackOpen] = useState(false);
  const [priceBannerDismissed, setPriceBannerDismissed] = useState(false);
  const [gmailSyncing, setGmailSyncing] = useState(false);

  useEffect(() => {
    setTermsDeclineHandler(() => setTermsDeclined(true));
    return () => setTermsDeclineHandler(null);
  }, []);

  useEffect(() => {
    if (!account) return;
    markOnboardingSeen();
    licence.dismissOnboarding();
  }, [account]);

  useEffect(() => {
    if (!account || !unlocked || mustRotatePassword) return;
    void maybeRunScheduledBackup('open');
  }, [account, unlocked, mustRotatePassword]);

  useEffect(() => {
    const onSync = (event: Event) => setGmailSyncing(Boolean((event as CustomEvent).detail));
    window.addEventListener('gud-gmail-sync', onSync);
    return () => window.removeEventListener('gud-gmail-sync', onSync);
  }, []);

  useEffect(() => {
    if (!account || !unlocked || !dbReady) return;
    let once = false;
    try { once = localStorage.getItem('gud.gmailSyncedOnce') === '1'; } catch { once = false; }
    if (!once) return;
    if (!familyMembers.some(m => m.gmailSync && (m.casId || m.pran))) return;
    void runGmailSync({
      members: familyMembers,
      casLatest,
      importStatementRecords,
      updateFamilyMember,
      seen: gmailFileSeen,
      markSeen: markGmailFileSeen,
    }).catch(() => undefined);
  }, [account, unlocked, dbReady]);

  useEffect(() => {
    setPriceBannerDismissed(false);
  }, [pricesError]);

  useEffect(() => {
    bindHeldIsins(() =>
      holdings
        .map(h => (h.isin || '').trim().toUpperCase())
        .filter(s => s.length >= 12),
    );
  }, [holdings]);

  useEffect(() => installFileInputPickerGuard(), []);

  useEffect(() => {
    if (!unlocked) return;
    let lockTimer: number | null = null;
    const armLock = () => {
      if (lockTimer != null) window.clearTimeout(lockTimer);
      lockTimer = window.setTimeout(() => {
        if (isNativePickerOpen() || document.visibilityState !== 'hidden') {
          if (document.visibilityState === 'hidden') armLock();
          return;
        }
        lockVault();
      }, AUTO_LOCK_MS);
    };
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') armLock();
      else if (lockTimer != null) {
        window.clearTimeout(lockTimer);
        lockTimer = null;
      }
    };
    const onPageHide = () => armLock();
    document.addEventListener('visibilitychange', onVisibility);
    window.addEventListener('pagehide', onPageHide);
    return () => {
      if (lockTimer != null) window.clearTimeout(lockTimer);
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('pagehide', onPageHide);
    };
  }, [unlocked, lockVault]);

  useEffect(() => {
    if (!account || !unlocked || mustRotatePassword) return;
    if (isAddHoldingOpen || isAddRecordOpen || selectedHoldingIdForDetail || isEmergencySheetOpen) return;
    if (migration && !migration.verified) return;
    let cancelled = false;
    const timer = window.setTimeout(() => {
      if (cancelled) return;
      if (claimFeedbackPrompt(loadFeedbackState())) setFeedbackOpen(true);
    }, 1800);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [
    account,
    unlocked,
    mustRotatePassword,
    isAddHoldingOpen,
    isAddRecordOpen,
    selectedHoldingIdForDetail,
    isEmergencySheetOpen,
    migration?.verified,
  ]);

  const handleOpenAddRecord = (holdingId?: string) => {
    setPreselectedHoldingForRecord(holdingId);
    setIsAddRecordOpen(true);
  };

  if (!licence.ready) {
    return (
      <div className="min-h-[100dvh] flex items-center justify-center" style={{ background: 'var(--app)', color: 'var(--text)' }}>
        <div className="text-lg font-bold tracking-wide animate-pulse">Checking licence…</div>
      </div>
    );
  }

  if (termsDeclined) {
    return <ClosedScreen />;
  }

  if (dbError) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-6 text-center" style={{ background: 'var(--app)', color: 'var(--text)' }}>
        <div className="card max-w-md w-full p-8 space-y-5">
          <div className="w-12 h-12 rounded-2xl flex items-center justify-center mx-auto" style={{ color: 'var(--bad)', background: 'var(--input)' }}>
            <AlertTriangle className="w-6 h-6" />
          </div>
          <div className="space-y-2">
            <h1 className="text-xl font-bold">We could not open your records. Your data has not been changed.</h1>
            <p className="text-xs font-mono break-words p-3 rounded-xl border" style={{ borderColor: 'var(--border)', background: 'var(--input)' }}>
              {dbError}
            </p>
          </div>
          <button
            onClick={() => window.location.reload()}
            className="w-full py-3 rounded-xl text-sm font-bold flex items-center justify-center gap-2"
            style={{ background: 'var(--accent)', color: 'var(--accent-fg)' }}
          >
            <RefreshCw className="w-4 h-4" />
            Retry
          </button>
        </div>
      </div>
    );
  }

  if (!dbReady) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-6 text-center" style={{ background: 'var(--app)', color: 'var(--text)' }}>
        <div className="text-lg font-bold tracking-wide animate-pulse">Opening your records...</div>
      </div>
    );
  }

  if (!account && licence.showOnboarding) {
    return <OnboardingScreen onDone={licence.dismissOnboarding} />;
  }

  if (!licence.unlocked) {
    return <LicenseScreen state={licence.state} onChange={licence.setState} />;
  }

  if (!account || !unlocked || mustRotatePassword) {
    return <AuthScreen />;
  }

  return (
    <div className="min-h-screen flex flex-col font-sans app-shell" style={{ color: 'var(--text)' }}>
      {migration && migration.verified && !isMigrationBannerDismissed && (
        <div className="px-4 py-3 text-xs sm:text-sm flex items-center justify-between" style={{ background: 'var(--chip)', color: 'var(--text)' }}>
          <div className="flex items-center gap-2 max-w-7xl mx-auto w-full">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" style={{ color: 'var(--good)' }} />
            <span>Your records have been moved to secure storage. Your old copy has been kept as a backup.</span>
          </div>
          <button onClick={() => setIsMigrationBannerDismissed(true)} className="p-1">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {migration && !migration.verified && (
        <div className="px-4 py-3 text-xs sm:text-sm" style={{ color: 'var(--bad)' }}>
          <div className="max-w-7xl mx-auto flex items-start gap-2.5">
            <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5" />
            <div>
              <div className="font-bold">Nothing was deleted. Your original data is safe. Please contact support before making any changes.</div>
              {migration.errors && migration.errors.length > 0 && (
                <ul className="list-disc list-inside text-xs opacity-90">
                  {migration.errors.map((err, idx) => (
                    <li key={idx}>{err}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}

      {pricesError && !priceBannerDismissed && (
        <div className="px-4 py-2.5 text-xs sm:text-sm flex items-center justify-between" style={{ background: 'var(--chip)', color: 'var(--text)' }}>
          <div className="flex items-center gap-2 max-w-7xl mx-auto w-full">
            <AlertTriangle className="w-4 h-4 flex-shrink-0" style={{ color: 'var(--bad)' }} />
            <span>{pricesError}</span>
          </div>
          <button onClick={() => setPriceBannerDismissed(true)} className="p-1" aria-label="Dismiss price error">
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      <Header
        onOpenAddHolding={() => setIsAddHoldingOpen(true)}
        onOpenAddRecord={() => handleOpenAddRecord()}
      />

      <div className="flex-1 flex max-w-7xl w-full mx-auto">
        <Sidebar />
        <main className="flex-1 px-3 py-3 md:p-6 lg:p-8 min-w-0 max-w-full overflow-x-hidden app-main">
          {activeScreen === 'HOME' && (
            <HomeScreen
              onOpenAddHolding={() => setIsAddHoldingOpen(true)}
              onOpenHoldingDetail={id => setSelectedHoldingIdForDetail(id)}
            />
          )}
          {activeScreen === 'HOLDINGS' && (
            <HoldingsScreen
              onOpenAddHolding={() => setIsAddHoldingOpen(true)}
              onOpenHoldingDetail={id => setSelectedHoldingIdForDetail(id)}
            />
          )}
          {activeScreen === 'ACCOUNTS' && (
            <AccountsScreen onOpenHoldingDetail={id => setSelectedHoldingIdForDetail(id)} />
          )}
          {activeScreen === 'DOCUMENTS' && <DocumentsScreen />}
          {activeScreen === 'FAMILY' && (
            <FamilyScreen onOpenEmergencySheet={() => setIsEmergencySheetOpen(true)} />
          )}
        </main>
      </div>

      <StatusBar />
      {gmailSyncing && (
        <div className="fixed bottom-20 right-4 z-40 flex items-center gap-2 rounded-full px-3 py-2 text-xs font-semibold shadow" style={{ background: 'var(--card)', color: 'var(--text)', border: '1px solid var(--border)' }}>
          <span className="inline-block w-3.5 h-3.5 rounded-full border-2 border-current border-t-transparent animate-spin" />
          Syncing Gmail
        </div>
      )}

      <AddHoldingModal isOpen={isAddHoldingOpen} onClose={() => setIsAddHoldingOpen(false)} />
      <HoldingDetailModal
        holdingId={selectedHoldingIdForDetail}
        onClose={() => setSelectedHoldingIdForDetail(null)}
        onOpenAddRecord={id => handleOpenAddRecord(id)}
      />
      <AddRecordModal
        isOpen={isAddRecordOpen}
        onClose={() => {
          setIsAddRecordOpen(false);
          setPreselectedHoldingForRecord(undefined);
        }}
        preselectedHoldingId={preselectedHoldingForRecord}
        lockToHolding={!!preselectedHoldingForRecord}
      />
      <EmergencySheetModal isOpen={isEmergencySheetOpen} onClose={() => setIsEmergencySheetOpen(false)} />
      <FeedbackPrompt open={feedbackOpen} onClose={() => setFeedbackOpen(false)} />
    </div>
  );
};

export default function App() {
  return (
    <LicenceProvider>
      <KoshaProvider>
        <KoshaMainApp />
      </KoshaProvider>
    </LicenceProvider>
  );
}
