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

const KoshaMainApp: React.FC = () => {
  const { activeScreen } = useKosha();

  // Modals state
  const [isAddHoldingOpen, setIsAddHoldingOpen] = useState(false);
  const [isAddRecordOpen, setIsAddRecordOpen] = useState(false);
  const [preselectedHoldingForRecord, setPreselectedHoldingForRecord] = useState<string | undefined>(undefined);
  const [selectedHoldingIdForDetail, setSelectedHoldingIdForDetail] = useState<string | null>(null);
  const [isEmergencySheetOpen, setIsEmergencySheetOpen] = useState(false);

  const handleOpenAddRecord = (holdingId?: string) => {
    setPreselectedHoldingForRecord(holdingId);
    setIsAddRecordOpen(true);
  };

  return (
    <div className="min-h-screen bg-[#0B1120] text-slate-100 flex flex-col font-sans selection:bg-amber-400 selection:text-slate-950">
      
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
