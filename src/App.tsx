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
import { markOnboardingSeen } from './lib/onboarding';
import { AlertTriangle, CheckCircle2, RefreshCw, X } from 'lucide-react';

const KoshaMainApp: React.FC = () => {
  const { activeScreen, dbReady, dbError, migration, account, unlocked, mustRotatePassword } = useKosha();
  const licence = useLicence();

  const [isAddHoldingOpen, setIsAddHoldingOpen] = useState(false);
  const [isAddRecordOpen, setIsAddRecordOpen] = useState(false);
  const [preselectedHoldingForRecord, setPreselectedHoldingForRecord] = useState<string | undefined>(undefined);
  const [selectedHoldingIdForDetail, setSelectedHoldingIdForDetail] = useState<string | null>(null);
  const [isEmergencySheetOpen, setIsEmergencySheetOpen] = useState(false);
  const [isMigrationBannerDismissed, setIsMigrationBannerDismissed] = useState(false);

  useEffect(() => {
    if (!account) return;
    markOnboardingSeen();
    licence.dismissOnboarding();
  }, [account]);

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
