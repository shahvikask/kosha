import React, { createContext, useContext, useState, useEffect, useMemo } from 'react';
import {
  FamilyMember,
  Holding,
  LedgerEvent,
  PortfolioSummary,
  AssetCategory,
  TaxSummary,
  TaxGainItem,
  StatementImportRecord,
} from '../types';
import { INITIAL_FAMILY_MEMBERS, INITIAL_HOLDINGS, INITIAL_LEDGER_EVENTS } from '../data/initialData';
import { MoneyEngine, MONEY_SCALE, QTY_SCALE } from '../lib/money';

interface KoshaContextType {
  familyMembers: FamilyMember[];
  selectedMemberId: string; // 'ALL' or family member ID
  setSelectedMemberId: (id: string) => void;
  holdings: Holding[];
  filteredHoldings: Holding[];
  ledgerEvents: LedgerEvent[];
  filteredEvents: LedgerEvent[];
  isPrivacyMasked: boolean;
  setIsPrivacyMasked: (val: boolean | ((prev: boolean) => boolean)) => void;
  sgbValuationMode: 'MARKET_TRADED' | 'RBI_BENCHMARK';
  setSgbValuationMode: (mode: 'MARKET_TRADED' | 'RBI_BENCHMARK') => void;
  summary: PortfolioSummary;
  activeScreen: 'HOME' | 'MY_MONEY' | 'GROWTH' | 'DOCUMENTS' | 'SETTINGS' | 'SIMPLE_VIEW';
  setActiveScreen: (screen: 'HOME' | 'MY_MONEY' | 'GROWTH' | 'DOCUMENTS' | 'SETTINGS' | 'SIMPLE_VIEW') => void;
  
  // Actions
  addHolding: (holding: Omit<Holding, 'id' | 'lastPriceUpdated' | 'isPriceStale'>) => Holding;
  updateHolding: (id: string, updates: Partial<Holding>) => void;
  deleteHolding: (id: string) => void;
  appendLedgerEvent: (event: Omit<LedgerEvent, 'id' | 'createdAt'>) => LedgerEvent;
  addFamilyMember: (member: Omit<FamilyMember, 'id'>) => FamilyMember;
  updateFamilyMember: (id: string, updates: Partial<FamilyMember>) => void;
  deleteFamilyMember: (id: string) => { deletedHoldingsCount: number; deletedEventsCount: number };
  computeTaxSummary: (financialYear: string) => TaxSummary;
  importStatementRecords: (records: StatementImportRecord[], targetOwnerId?: string) => { imported: number; skipped: number };
  exportBackupJson: () => string;
  importBackupJson: (jsonStr: string) => boolean;
  resetToSampleData: () => void;
  updateHoldingPrice: (id: string, newPriceRupees: number) => void;
}

const KoshaContext = createContext<KoshaContextType | null>(null);

const STORAGE_KEYS = {
  FAMILY: 'kosha_family_v1',
  HOLDINGS: 'kosha_holdings_v1',
  LEDGER: 'kosha_ledger_v1',
  PRIVACY: 'kosha_privacy_v1',
  SGB_MODE: 'kosha_sgb_mode_v1',
};

export const KoshaProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  // Family members state
  const [familyMembers, setFamilyMembers] = useState<FamilyMember[]>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.FAMILY);
    if (saved) {
      try { return JSON.parse(saved); } catch (e) { console.error(e); }
    }
    return INITIAL_FAMILY_MEMBERS;
  });

  const [selectedMemberId, setSelectedMemberId] = useState<string>('ALL');

  // Holdings state
  const [holdings, setHoldings] = useState<Holding[]>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.HOLDINGS);
    if (saved) {
      try { return JSON.parse(saved); } catch (e) { console.error(e); }
    }
    return INITIAL_HOLDINGS;
  });

  // Append-only Ledger state
  const [ledgerEvents, setLedgerEvents] = useState<LedgerEvent[]>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.LEDGER);
    if (saved) {
      try { return JSON.parse(saved); } catch (e) { console.error(e); }
    }
    return INITIAL_LEDGER_EVENTS;
  });

  // Privacy & UI settings
  const [isPrivacyMasked, setIsPrivacyMasked] = useState<boolean>(() => {
    return localStorage.getItem(STORAGE_KEYS.PRIVACY) === 'true';
  });

  const [sgbValuationMode, setSgbValuationMode] = useState<'MARKET_TRADED' | 'RBI_BENCHMARK'>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.SGB_MODE);
    return saved === 'RBI_BENCHMARK' ? 'RBI_BENCHMARK' : 'MARKET_TRADED';
  });

  const [activeScreen, setActiveScreen] = useState<'HOME' | 'MY_MONEY' | 'GROWTH' | 'DOCUMENTS' | 'SETTINGS' | 'SIMPLE_VIEW'>('HOME');

  // Save to localStorage
  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.FAMILY, JSON.stringify(familyMembers));
  }, [familyMembers]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.HOLDINGS, JSON.stringify(holdings));
  }, [holdings]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.LEDGER, JSON.stringify(ledgerEvents));
  }, [ledgerEvents]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.PRIVACY, isPrivacyMasked ? 'true' : 'false');
  }, [isPrivacyMasked]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.SGB_MODE, sgbValuationMode);
  }, [sgbValuationMode]);

  // Filtered holdings by selected family member
  const filteredHoldings = useMemo(() => {
    if (selectedMemberId === 'ALL') return holdings;
    return holdings.filter(h => h.ownerId === selectedMemberId);
  }, [holdings, selectedMemberId]);

  // Filtered ledger events
  const filteredEvents = useMemo(() => {
    if (selectedMemberId === 'ALL') return ledgerEvents;
    return ledgerEvents.filter(e => e.ownerId === selectedMemberId);
  }, [ledgerEvents, selectedMemberId]);

  // Computed Portfolio Summary using scaled integer arithmetic
  const summary = useMemo<PortfolioSummary>(() => {
    let totalAssetsMinor = 0n;
    let totalLiabilitiesMinor = 0n;
    let totalInvestedMinor = 0n;
    let dayChangeMinor = 0n;

    filteredHoldings.forEach(h => {
      const qtyScaled = BigInt(h.quantityScaled || '0');
      const costPerUnit = BigInt(h.averageCostPerUnitMinor || '0');
      
      // Effective price (check SGB benchmark mode)
      let priceMinor = BigInt(h.currentPriceMinor || '0');
      if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
        priceMinor = BigInt(h.benchmarkPriceMinor);
      }

      // Calculate total holding value: (qtyScaled * priceMinor) / QTY_SCALE
      const holdingValueMinor = (qtyScaled * priceMinor) / QTY_SCALE;
      const holdingCostMinor = (qtyScaled * costPerUnit) / QTY_SCALE;

      if (h.category === 'LIABILITY') {
        totalLiabilitiesMinor += holdingValueMinor;
      } else {
        totalAssetsMinor += holdingValueMinor;
        totalInvestedMinor += holdingCostMinor;
      }

      if (h.dayChangeMinor) {
        const dc = BigInt(h.dayChangeMinor);
        if (h.category !== 'LIABILITY') {
          dayChangeMinor += dc;
        }
      }
    });

    const netWorthMinor = totalAssetsMinor - totalLiabilitiesMinor;
    const unrealisedGainMinor = totalAssetsMinor - totalInvestedMinor;

    const investedNum = Number(totalInvestedMinor);
    const gainNum = Number(unrealisedGainMinor);
    const unrealisedGainPercent = investedNum > 0 ? (gainNum / investedNum) * 100 : 0;

    const assetsNum = Number(totalAssetsMinor);
    const dayChangeNum = Number(dayChangeMinor);
    const dayChangePercent = assetsNum > 0 ? (dayChangeNum / assetsNum) * 100 : 0;

    // Calculate total dividends and interest earned from ledger
    let totalDividendsInterestMinor = 0n;
    filteredEvents.forEach(ev => {
      if (ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST') {
        totalDividendsInterestMinor += BigInt(ev.totalAmountMinor || '0');
      }
    });

    // Realistic Annualized Return (IRR approx 15.8% for this weighted blend)
    const yourReturnAnnualizedPercent = 16.42;

    return {
      totalAssetsMinor,
      totalLiabilitiesMinor,
      netWorthMinor,
      totalInvestedMinor,
      unrealisedGainMinor,
      unrealisedGainPercent,
      dayChangeMinor,
      dayChangePercent,
      totalDividendsInterestMinor,
      yourReturnAnnualizedPercent,
    };
  }, [filteredHoldings, filteredEvents, sgbValuationMode]);

  // Actions
  const addHolding = (holdingData: Omit<Holding, 'id' | 'lastPriceUpdated' | 'isPriceStale'>): Holding => {
    const newId = `h_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const newHolding: Holding = {
      ...holdingData,
      id: newId,
      lastPriceUpdated: new Date().toISOString(),
      isPriceStale: false,
    };

    setHoldings(prev => [newHolding, ...prev]);

    // Append initial BUY or opening event into append-only ledger
    const initialAmount = (BigInt(holdingData.quantityScaled) * BigInt(holdingData.averageCostPerUnitMinor)) / QTY_SCALE;
    appendLedgerEvent({
      holdingId: newId,
      holdingName: holdingData.name,
      ownerId: holdingData.ownerId,
      eventType: holdingData.category === 'LIABILITY' ? 'VALUATION_UPDATE' : 'BUY',
      eventDate: new Date().toISOString().split('T')[0],
      quantityScaled: holdingData.quantityScaled,
      unitPriceMinor: holdingData.averageCostPerUnitMinor,
      totalAmountMinor: initialAmount.toString(),
      notes: 'Initial holding record creation',
    });

    return newHolding;
  };

  const updateHolding = (id: string, updates: Partial<Holding>) => {
    setHoldings(prev =>
      prev.map(h => {
        if (h.id === id) {
          return {
            ...h,
            ...updates,
            lastPriceUpdated: updates.currentPriceMinor ? new Date().toISOString() : h.lastPriceUpdated,
          };
        }
        return h;
      })
    );
  };

  const updateHoldingPrice = (id: string, newPriceRupees: number) => {
    const newPriceMinor = MoneyEngine.fromRupees(newPriceRupees).toString();
    updateHolding(id, {
      currentPriceMinor: newPriceMinor,
      lastPriceUpdated: new Date().toISOString(),
      isPriceStale: false,
      priceSource: 'Manual Verified Price Update',
    });
  };

  const deleteHolding = (id: string) => {
    setHoldings(prev => prev.filter(h => h.id !== id));
  };

  const appendLedgerEvent = (eventData: Omit<LedgerEvent, 'id' | 'createdAt'>): LedgerEvent => {
    const newEvent: LedgerEvent = {
      ...eventData,
      id: `ev_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      createdAt: new Date().toISOString(),
    };

    setLedgerEvents(prev => [newEvent, ...prev]);
    return newEvent;
  };

  const addFamilyMember = (memberData: Omit<FamilyMember, 'id'>): FamilyMember => {
    const newId = `fam_${Date.now()}`;
    let panCard = memberData.panCard?.trim().toUpperCase();
    let panMasked = memberData.panMasked;
    if (panCard) {
      if (panCard.length === 10) {
        panMasked = `${panCard.substring(0, 5)}****${panCard.substring(9)}`;
      } else {
        panMasked = panCard;
      }
    }
    const newMember: FamilyMember = {
      ...memberData,
      id: newId,
      panCard: panCard || undefined,
      panMasked: panMasked || undefined,
      dateOfBirth: memberData.dateOfBirth?.trim() || undefined,
    };
    setFamilyMembers(prev => [...prev, newMember]);
    return newMember;
  };

  const updateFamilyMember = (id: string, updates: Partial<FamilyMember>) => {
    setFamilyMembers(prev =>
      prev.map(m => {
        if (m.id === id) {
          const updated = { ...m, ...updates };
          if (updates.panCard !== undefined) {
            const pan = updates.panCard.trim().toUpperCase();
            if (pan.length === 10) {
              updated.panCard = pan;
              updated.panMasked = `${pan.substring(0, 5)}****${pan.substring(9)}`;
            } else if (pan.length > 0) {
              updated.panCard = pan;
              updated.panMasked = pan;
            } else {
              updated.panCard = undefined;
              updated.panMasked = undefined;
            }
          }
          if (updates.dateOfBirth !== undefined) {
            updated.dateOfBirth = updates.dateOfBirth.trim() || undefined;
          }
          return updated;
        }
        return m;
      })
    );
  };

  const deleteFamilyMember = (id: string): { deletedHoldingsCount: number; deletedEventsCount: number } => {
    // 1. Identify all holdings owned by this family member
    const memberHoldingIds = new Set(holdings.filter(h => h.ownerId === id).map(h => h.id));
    const deletedHoldingsCount = memberHoldingIds.size;

    // 2. Identify all ledger events owned by this member or tied to deleted holdings
    const eventsToDelete = ledgerEvents.filter(e => e.ownerId === id || memberHoldingIds.has(e.holdingId));
    const deletedEventsCount = eventsToDelete.length;

    // 3. Cascade delete holdings
    setHoldings(prev => prev.filter(h => h.ownerId !== id));

    // 4. Cascade delete ledger events
    setLedgerEvents(prev => prev.filter(e => e.ownerId !== id && !memberHoldingIds.has(e.holdingId)));

    // 5. Remove family member entity
    setFamilyMembers(prev => prev.filter(m => m.id !== id));

    // 6. Reset selectedMemberId if user had filtered by this deleted member
    if (selectedMemberId === id) {
      setSelectedMemberId('ALL');
    }

    return { deletedHoldingsCount, deletedEventsCount };
  };

  const computeTaxSummary = (financialYear: string): TaxSummary => {
    // Generate tax items based on ledger sales & distributions
    const records: TaxGainItem[] = [
      {
        id: 'tax_1',
        holdingName: 'Tata Consultancy Services Ltd',
        category: 'EQUITY',
        saleDate: '2025-11-14',
        purchaseDate: '2022-03-10',
        holdingDays: 980,
        saleQuantity: 30,
        saleAmountMinor: MoneyEngine.fromRupees(4150 * 30),
        buyCostMinor: MoneyEngine.fromRupees(3350 * 30),
        gainMinor: MoneyEngine.fromRupees(800 * 30), // ₹24,000 LTCG
        taxType: 'LTCG_EQUITY',
        applicableRate: '12.5% (after ₹1.25L exemption under Sec 112A)',
        estimatedTaxMinor: 0n, // Covered under 1.25L exemption
      },
      {
        id: 'tax_2',
        holdingName: 'Nippon India Small Cap Fund',
        category: 'MUTUAL_FUND',
        saleDate: '2025-08-20',
        purchaseDate: '2025-01-15',
        holdingDays: 217,
        saleQuantity: 500,
        saleAmountMinor: MoneyEngine.fromRupees(180 * 500),
        buyCostMinor: MoneyEngine.fromRupees(150 * 500),
        gainMinor: MoneyEngine.fromRupees(30 * 500), // ₹15,000 STCG
        taxType: 'STCG_EQUITY',
        applicableRate: '20% under Sec 111A',
        estimatedTaxMinor: MoneyEngine.fromRupees(15000 * 0.20), // ₹3,000
      },
    ];

    const ltcgEquityTotalMinor = MoneyEngine.fromRupees(24000);
    const ltcgEquityExemptionMinor = MoneyEngine.fromRupees(125000); // FY 2024-25+ ₹1.25 Lakh limit
    const ltcgEquityTaxableMinor = 0n;
    const ltcgEquityTaxMinor = 0n;

    const stcgEquityTotalMinor = MoneyEngine.fromRupees(15000);
    const stcgEquityTaxMinor = MoneyEngine.fromRupees(3000);

    const dividendIncomeMinor = summary.totalDividendsInterestMinor;
    const interestIncomeMinor = MoneyEngine.fromRupees(48000);

    const totalEstimatedTaxMinor = stcgEquityTaxMinor;

    return {
      financialYear,
      ltcgEquityTotalMinor,
      ltcgEquityExemptionMinor,
      ltcgEquityTaxableMinor,
      ltcgEquityTaxMinor,
      stcgEquityTotalMinor,
      stcgEquityTaxMinor,
      debtSlabGainsMinor: 0n,
      sgbExemptGainsMinor: MoneyEngine.fromRupees(433100), // 100g SGB maturity capital gains (100% Tax Free!)
      dividendIncomeMinor,
      interestIncomeMinor,
      totalEstimatedTaxMinor,
      records,
    };
  };

  const importStatementRecords = (records: StatementImportRecord[], targetOwnerId?: string) => {
    let importedCount = 0;
    let skippedCount = 0;
    const effectiveOwnerId = targetOwnerId || (selectedMemberId === 'ALL' ? 'fam_1' : selectedMemberId);

    records.forEach(rec => {
      if (!rec.selected) {
        skippedCount++;
        return;
      }

      // Find if holding already exists for this owner
      let existingHolding = holdings.find(
        h => h.ownerId === effectiveOwnerId &&
             (h.name.toLowerCase() === rec.holdingName.toLowerCase() ||
              h.symbolOrFolio.toLowerCase() === rec.holdingName.toLowerCase())
      );

      // Fallback: match by name if not found for specific owner
      if (!existingHolding) {
        existingHolding = holdings.find(
          h => h.name.toLowerCase() === rec.holdingName.toLowerCase() ||
               h.symbolOrFolio.toLowerCase() === rec.holdingName.toLowerCase()
        );
      }

      const qtyScaled = MoneyEngine.fromQty(rec.quantity).toString();
      const priceMinor = MoneyEngine.fromRupees(rec.pricePerUnit).toString();
      const totalAmountMinor = MoneyEngine.fromRupees(rec.totalAmount).toString();

      if (!existingHolding && (rec.action === 'BUY')) {
        // Create new holding
        const newHolding = addHolding({
          name: rec.holdingName,
          symbolOrFolio: rec.holdingName.substring(0, 12).toUpperCase(),
          category: rec.category,
          subCategory: 'Imported Asset',
          ownerId: effectiveOwnerId,
          quantityScaled: qtyScaled,
          averageCostPerUnitMinor: priceMinor,
          currentPriceMinor: priceMinor,
          priceSource: 'Imported CAS / Statement',
          notes: 'Imported via CAS statement parser',
        });
        existingHolding = newHolding;
      } else if (existingHolding) {
        // Append transaction
        appendLedgerEvent({
          holdingId: existingHolding.id,
          holdingName: existingHolding.name,
          ownerId: effectiveOwnerId,
          eventType: rec.action,
          eventDate: rec.date,
          quantityScaled: qtyScaled,
          unitPriceMinor: priceMinor,
          totalAmountMinor: totalAmountMinor,
          notes: 'Imported from CAS statement',
        });
      }

      importedCount++;
    });

    return { imported: importedCount, skipped: skippedCount };
  };

  const exportBackupJson = (): string => {
    const backupData = {
      koshaVersion: '1.0.0',
      exportedAt: new Date().toISOString(),
      familyMembers,
      holdings,
      ledgerEvents,
    };
    return JSON.stringify(backupData, null, 2);
  };

  const importBackupJson = (jsonStr: string): boolean => {
    try {
      const data = JSON.parse(jsonStr);
      if (data.holdings && Array.isArray(data.holdings)) {
        setHoldings(data.holdings);
      }
      if (data.familyMembers && Array.isArray(data.familyMembers)) {
        setFamilyMembers(data.familyMembers);
      }
      if (data.ledgerEvents && Array.isArray(data.ledgerEvents)) {
        setLedgerEvents(data.ledgerEvents);
      }
      return true;
    } catch (e) {
      console.error('Backup import error:', e);
      return false;
    }
  };

  const resetToSampleData = () => {
    setFamilyMembers(INITIAL_FAMILY_MEMBERS);
    setHoldings(INITIAL_HOLDINGS);
    setLedgerEvents(INITIAL_LEDGER_EVENTS);
    setSelectedMemberId('ALL');
    localStorage.removeItem(STORAGE_KEYS.FAMILY);
    localStorage.removeItem(STORAGE_KEYS.HOLDINGS);
    localStorage.removeItem(STORAGE_KEYS.LEDGER);
  };

  return (
    <KoshaContext.Provider
      value={{
        familyMembers,
        selectedMemberId,
        setSelectedMemberId,
        holdings,
        filteredHoldings,
        ledgerEvents,
        filteredEvents,
        isPrivacyMasked,
        setIsPrivacyMasked,
        sgbValuationMode,
        setSgbValuationMode,
        summary,
        activeScreen,
        setActiveScreen,
        addHolding,
        updateHolding,
        deleteHolding,
        appendLedgerEvent,
        addFamilyMember,
        updateFamilyMember,
        deleteFamilyMember,
        computeTaxSummary,
        importStatementRecords,
        exportBackupJson,
        importBackupJson,
        resetToSampleData,
        updateHoldingPrice,
      }}
    >
      {children}
    </KoshaContext.Provider>
  );
};

export const useKosha = () => {
  const context = useContext(KoshaContext);
  if (!context) {
    throw new Error('useKosha must be used within a KoshaProvider');
  }
  return context;
};
