import React, { useState } from 'react';
import { KoshaProvider, useKosha } from './context/KoshaContext';
import { Header } from './components/layout/Header';
import { Sidebar } from './components/layout/Sidebar';
import { StatusBar } from './components/layout/StatusBar';
import { HomeScreen } from './components/screens/HomeScreen';
import { MyMoneyScreen } from './components/screens/MyMoneyScreen';
import { GrowthScreen } from './components/screens/GrowthScreen';
import { DocumentsScreen } from './components/screens/DocumentsScreen';
import { SettingsScreen } from './components/screens/SettingsScreen';
import { SimpleViewScreen } from './components/screens/SimpleViewScreen';
import { AddHoldingModal } from './components/modals/AddHoldingModal';
import { AddRecordModal } from './components/modals/AddRecordModal';
import { HoldingDetailModal } from './components/modals/HoldingDetailModal';
import { EmergencySheetModal } from './components/modals/EmergencySheetModal';
import { AlertTriangle, CheckCircle2, RefreshCw, X } from 'lucide-react';

const KoshaMainApp: React.FC = () => {
  const { activeScreen, dbReady, dbError, migration } = useKosha();

  // Modals state
  const [isAddHoldingOpen, setIsAddHoldingOpen] = useState(false);
  const [isAddRecordOpen, setIsAddRecordOpen] = useState(false);
  const [preselectedHoldingForRecord, setPreselectedHoldingForRecord] = useState<string | undefined>(undefined);
  const [selectedHoldingIdForDetail, setSelectedHoldingIdForDetail] = useState<string | null>(null);
  const [isEmergencySheetOpen, setIsEmergencySheetOpen] = useState(false);
  const [isMigrationBannerDismissed, setIsMigrationBannerDismissed] = useState(false);

  const handleOpenAddRecord = (holdingId?: string) => {
    setPreselectedHoldingForRecord(holdingId);
    setIsAddRecordOpen(true);
  };

  // 1. If dbError is set, show a full-screen message (do not render app with empty data)
  if (dbError) {
    return (
      <div className="min-h-screen bg-[#0B1120] text-slate-100 flex flex-col items-center justify-center p-6 text-center">
        <div className="max-w-md w-full bg-slate-900 border border-slate-800 rounded-3xl p-8 shadow-2xl space-y-5">
          <div className="w-12 h-12 rounded-2xl bg-rose-950/80 border border-rose-800/80 text-rose-400 flex items-center justify-center mx-auto">
            <AlertTriangle className="w-6 h-6" />
          </div>
          <div className="space-y-2">
            <h1 className="text-xl font-bold font-serif text-white">
              We could not open your records. Your data has not been changed.
            </h1>
            <p className="text-xs text-slate-400 font-mono break-words bg-slate-950/80 p-3 rounded-xl border border-slate-800/80">
              {dbError}
            </p>
          </div>
          <button
            onClick={() => window.location.reload()}
            className="w-full py-3 bg-amber-400 hover:bg-amber-300 text-slate-950 rounded-xl text-sm font-bold flex items-center justify-center gap-2 transition-all shadow-md shadow-amber-400/10 cursor-pointer"
          >
            <RefreshCw className="w-4 h-4" />
            <span>Retry</span>
          </button>
        </div>
      </div>
    );
  }

  // 2. If dbReady is false, show a simple centred message: "Opening your records..."
  if (!dbReady) {
    return (
      <div className="min-h-screen bg-[#0B1120] text-slate-100 flex flex-col items-center justify-center p-6 text-center space-y-4">
        <div className="text-lg font-serif font-bold tracking-wide text-slate-200 animate-pulse">
          Opening your records...
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#0B1120] text-slate-100 flex flex-col font-sans selection:bg-amber-400 selection:text-slate-950">
      
      {/* 3 & 4. Migration Banners */}
      {migration && migration.verified && !isMigrationBannerDismissed && (
        <div className="bg-emerald-950/90 border-b border-emerald-800/80 text-emerald-200 px-4 py-3 text-xs sm:text-sm flex items-center justify-between shadow-lg">
          <div className="flex items-center gap-2 max-w-7xl mx-auto w-full">
            <CheckCircle2 className="w-4 h-4 text-emerald-400 flex-shrink-0" />
            <span>
              Your records have been moved to secure storage. Your old copy has been kept as a backup.
            </span>
          </div>
          <button
            onClick={() => setIsMigrationBannerDismissed(true)}
            className="p-1 text-emerald-400 hover:text-emerald-200 transition-colors cursor-pointer"
            title="Dismiss"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {migration && !migration.verified && (
        <div className="bg-rose-950 border-b border-rose-800 text-rose-200 px-4 py-3 text-xs sm:text-sm shadow-lg">
          <div className="max-w-7xl mx-auto flex items-start gap-2.5">
            <AlertTriangle className="w-5 h-5 text-rose-400 flex-shrink-0 mt-0.5" />
            <div className="space-y-1">
              <div className="font-bold">
                Nothing was deleted. Your original data is safe. Please contact support before making any changes.
              </div>
              {migration.errors && migration.errors.length > 0 && (
                <ul className="list-disc list-inside text-xs text-rose-300 opacity-90">
                  {migration.errors.map((err, idx) => (
                    <li key={idx}>{err}</li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Top Header */}
      <Header
        onOpenAddHolding={() => setIsAddHoldingOpen(true)}
        onOpenAddRecord={() => handleOpenAddRecord()}
      />

      {/* Main Layout Container */}
      <div className="flex-1 flex max-w-7xl w-full mx-auto">
        
        {/* Left Navigation Sidebar (5 Screens) */}
        <Sidebar />

        {/* Dynamic Screen Content Area */}
        <main className="flex-1 p-4 sm:p-6 lg:p-8 min-w-0 max-w-full overflow-x-hidden mb-12">
          {activeScreen === 'HOME' && (
            <HomeScreen
              onOpenAddHolding={() => setIsAddHoldingOpen(true)}
              onOpenAddRecord={() => handleOpenAddRecord()}
              onOpenHoldingDetail={id => setSelectedHoldingIdForDetail(id)}
            />
          )}

          {activeScreen === 'MY_MONEY' && (
            <MyMoneyScreen
              onOpenAddHolding={() => setIsAddHoldingOpen(true)}
              onOpenAddRecord={() => handleOpenAddRecord()}
              onOpenHoldingDetail={id => setSelectedHoldingIdForDetail(id)}
            />
          )}

          {activeScreen === 'GROWTH' && <GrowthScreen />}

          {activeScreen === 'DOCUMENTS' && <DocumentsScreen />}

          {activeScreen === 'SETTINGS' && (
            <SettingsScreen
              onOpenEmergencySheet={() => setIsEmergencySheetOpen(true)}
            />
          )}

          {activeScreen === 'SIMPLE_VIEW' && (
            <SimpleViewScreen
              onOpenEmergencySheet={() => setIsEmergencySheetOpen(true)}
            />
          )}
        </main>
      </div>

      {/* Fixed Status Bar (Annex B §B6.1) */}
      <StatusBar />

      {/* Interactive Modals */}
      <AddHoldingModal
        isOpen={isAddHoldingOpen}
        onClose={() => setIsAddHoldingOpen(false)}
      />

      <AddRecordModal
        isOpen={isAddRecordOpen}
        onClose={() => {
          setIsAddRecordOpen(false);
          setPreselectedHoldingForRecord(undefined);
        }}
        preselectedHoldingId={preselectedHoldingForRecord}
      />

      <HoldingDetailModal
        holdingId={selectedHoldingIdForDetail}
        onClose={() => setSelectedHoldingIdForDetail(null)}
        onOpenAddRecord={id => handleOpenAddRecord(id)}
      />

      <EmergencySheetModal
        isOpen={isEmergencySheetOpen}
        onClose={() => setIsEmergencySheetOpen(false)}
      />

    </div>
  );
};

export default function App() {
  return (
    <KoshaProvider>
      <KoshaMainApp />
    </KoshaProvider>
  );
}
