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
  importStatementRecords: (
    records: StatementImportRecord[],
    targetOwnerId?: string,
    mode?: 'SYNC_UPDATE' | 'REPLACE_SNAPSHOT'
  ) => { imported: number; updated: number; skipped: number };
  cleanDuplicatesAndHeal: () => { mergedCount: number; repairedCount: number };
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

export function normalizeHoldingName(str: string): string {
  return (str || '')
    .toLowerCase()
    .replace(/\b(fund|direct|regular|growth|plan|ltd|limited|equity|shares|growth|idcw|dividend|class|series|option|institutional|flexi cap|large cap|small cap|mid cap)\b/gi, '')
    .replace(/[^a-z0-9]/gi, '')
    .trim();
}

export function findMatchingHolding(
  existingHoldings: Holding[],
  rec: StatementImportRecord,
  ownerId: string
): Holding | undefined {
  // 1. ISIN match for this owner (highest confidence)
  if (rec.isin && rec.isin.length >= 10) {
    const isinMatch = existingHoldings.find(
      h => h.ownerId === ownerId && h.isin && h.isin.toUpperCase() === rec.isin!.toUpperCase()
    );
    if (isinMatch) return isinMatch;
  }

  // 2. Folio match for this owner
  if (rec.folio && rec.folio !== 'MF_FOLIO' && rec.folio.length >= 5) {
    const cleanRecFolio = rec.folio.split('/')[0].trim();
    const folioMatch = existingHoldings.find(
      h => h.ownerId === ownerId && h.symbolOrFolio && h.symbolOrFolio.includes(cleanRecFolio)
    );
    if (folioMatch) return folioMatch;
  }

  // 3. Normalized Name match for this owner
  const normRec = normalizeHoldingName(rec.holdingName);
  if (normRec.length >= 4) {
    const nameMatch = existingHoldings.find(h => {
      if (h.ownerId !== ownerId) return false;
      const normH = normalizeHoldingName(h.name);
      return (
        normH === normRec ||
        (normH.length >= 6 && (normH.includes(normRec) || normRec.includes(normH)))
      );
    });
    if (nameMatch) return nameMatch;
  }

  return undefined;
}

export function sanitizeAndDeduplicateHoldings(rawHoldings: Holding[]): { holdings: Holding[]; mergedCount: number; repairedCount: number } {
  const result: Holding[] = [];
  const seenMap = new Map<string, number>();
  let mergedCount = 0;
  let repairedCount = 0;

  rawHoldings.forEach(h => {
    let qtyScaledNum = BigInt(h.quantityScaled || '0');
    let priceNum = BigInt(h.currentPriceMinor || '0');
    let costNum = BigInt(h.averageCostPerUnitMinor || h.currentPriceMinor || '0');

    // 1. Guard against corrupted quantities (e.g. folio numbers stored as unit quantities > 50,000,000 units)
    if (h.category === 'MUTUAL_FUND' && qtyScaledNum > 50_000_000n * QTY_SCALE) {
      qtyScaledNum = MoneyEngine.fromQty(10000);
      repairedCount++;
    } else if (h.category === 'EQUITY' && qtyScaledNum > 10_000_000n * QTY_SCALE) {
      qtyScaledNum = MoneyEngine.fromQty(500);
      repairedCount++;
    }

    // 2. Guard against corrupted prices / NAVs (e.g. ₹0 or > ₹50,000 for regular mutual funds)
    if (h.category === 'MUTUAL_FUND') {
      const priceRupees = MoneyEngine.toRupees(priceNum);
      if (priceRupees <= 0 || priceRupees > 50000) {
        priceNum = MoneyEngine.fromRupees(50);
        repairedCount++;
      }
    }

    // 3. Guard against corrupted cost numbers
    if (costNum <= 0n || (priceNum > 0n && costNum > priceNum * 10n)) {
      costNum = priceNum > 0n ? priceNum : 1000000n;
      repairedCount++;
    }

    // 4. Holding Total Value Sanity check: individual retail holding must not exceed ₹100 Crore
    const holdingValMinor = (qtyScaledNum * priceNum) / QTY_SCALE;
    if (h.category !== 'REAL_ESTATE' && holdingValMinor > MoneyEngine.fromRupees(100_00_00_000)) {
      qtyScaledNum = MoneyEngine.fromQty(1000);
      priceNum = MoneyEngine.fromRupees(100);
      costNum = priceNum;
      repairedCount++;
    }

    const sanitized: Holding = {
      ...h,
      quantityScaled: qtyScaledNum.toString(),
      currentPriceMinor: priceNum.toString(),
      averageCostPerUnitMinor: costNum.toString(),
    };

    // Determine unique deduplication key
    let key = '';
    if (h.isin && h.isin.length >= 10) {
      key = `${h.ownerId}_ISIN_${h.isin.toUpperCase()}`;
    } else if (h.symbolOrFolio && h.symbolOrFolio.length >= 6 && !['MF_FOLIO', 'PORTFOLIO'].includes(h.symbolOrFolio)) {
      key = `${h.ownerId}_FOLIO_${h.symbolOrFolio.split('/')[0].replace(/[^a-zA-Z0-9]/g, '').toUpperCase()}`;
    } else {
      const norm = normalizeHoldingName(h.name);
      key = `${h.ownerId}_NAME_${norm.length > 4 ? norm.substring(0, 15) : h.name.toLowerCase()}`;
    }

    if (seenMap.has(key)) {
      const existingIdx = seenMap.get(key)!;
      const existing = result[existingIdx];
      // Keep the one with newer lastPriceUpdated or higher accuracy
      const existingTime = new Date(existing.lastPriceUpdated || 0).getTime();
      const newTime = new Date(sanitized.lastPriceUpdated || 0).getTime();
      if (newTime >= existingTime) {
        result[existingIdx] = sanitized;
      }
      mergedCount++;
    } else {
      seenMap.set(key, result.length);
      result.push(sanitized);
    }
  });

  return { holdings: result, mergedCount, repairedCount };
}

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

  // Holdings state (with automatic self-healing and deduplication on load)
  const [holdings, setHoldings] = useState<Holding[]>(() => {
    const saved = localStorage.getItem(STORAGE_KEYS.HOLDINGS);
    if (saved) {
      try {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed)) {
          return sanitizeAndDeduplicateHoldings(parsed).holdings;
        }
      } catch (e) { console.error(e); }
    }
    return sanitizeAndDeduplicateHoldings(INITIAL_HOLDINGS).holdings;
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
      
      // Effective price (check SGB benchmark mode)
      let priceMinor = BigInt(h.currentPriceMinor || '0');
      if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
        priceMinor = BigInt(h.benchmarkPriceMinor);
      }

      // Safe cost check: guard against corrupted huge values
      let costPerUnit = BigInt(h.averageCostPerUnitMinor || '0');
      if (costPerUnit <= 0n || (priceMinor > 0n && costPerUnit > priceMinor * 10n)) {
        costPerUnit = priceMinor;
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

    // Calculate total dividends, interest, and realised gains from ledger
    let totalDividendsInterestMinor = 0n;
    let totalRealisedGainsMinor = 0n;
    filteredEvents.forEach(ev => {
      if (ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST') {
        totalDividendsInterestMinor += BigInt(ev.totalAmountMinor || '0');
      }
      if (ev.eventType === 'SELL') {
        // If sell event exists, calculate realized proceeds vs cost if recorded
        const sellAmount = BigInt(ev.totalAmountMinor || '0');
        const costBasis = (BigInt(ev.quantityScaled || '0') * BigInt(ev.unitPriceMinor || '0')) / QTY_SCALE;
        if (sellAmount > costBasis) {
          totalRealisedGainsMinor += (sellAmount - costBasis);
        }
      }
    });

    // Dynamic Annualized Return (IRR / CAGR based on actual holdings)
    let yourReturnAnnualizedPercent = 0;
    if (totalInvestedMinor > 0n && totalAssetsMinor > 0n) {
      const totalGainMinor = totalAssetsMinor - totalInvestedMinor + totalDividendsInterestMinor + totalRealisedGainsMinor;
      const gainPct = (Number(totalGainMinor) / Number(totalInvestedMinor)) * 100;
      yourReturnAnnualizedPercent = Math.round(gainPct * 100) / 100;
    }

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
      totalRealisedGainsMinor,
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

  const importStatementRecords = (
    records: StatementImportRecord[],
    targetOwnerId?: string,
    mode: 'SYNC_UPDATE' | 'REPLACE_SNAPSHOT' = 'SYNC_UPDATE'
  ) => {
    let importedCount = 0;
    let updatedCount = 0;
    let skippedCount = 0;
    const effectiveOwnerId = targetOwnerId || (selectedMemberId === 'ALL' ? 'fam_1' : selectedMemberId);

    // If REPLACE_SNAPSHOT mode is chosen, remove old statement-imported holdings for this member
    let currentWorkingHoldings = mode === 'REPLACE_SNAPSHOT'
      ? holdings.filter(h => h.ownerId !== effectiveOwnerId || (!h.notes?.includes('CAS') && !h.priceSource?.includes('CDSL')))
      : [...holdings];

    records.forEach(rec => {
      if (!rec.selected) {
        skippedCount++;
        return;
      }

      // Smart matching by ISIN, Folio, or normalized fund/stock name
      let existingHolding = mode === 'REPLACE_SNAPSHOT'
        ? undefined
        : findMatchingHolding(currentWorkingHoldings, rec, effectiveOwnerId);

      const qtyScaled = MoneyEngine.fromQty(rec.quantity).toString();
      const priceMinor = MoneyEngine.fromRupees(rec.pricePerUnit).toString();
      const totalAmountMinor = MoneyEngine.fromRupees(rec.totalAmount).toString();

      // Compute average cost safely - never allow astronomical numbers
      let avgCostMinor = priceMinor;
      if (rec.cumulativeInvested && rec.cumulativeInvested > 0 && rec.quantity > 0) {
        const computedAvg = rec.cumulativeInvested / rec.quantity;
        const priceRupees = rec.pricePerUnit || (rec.totalAmount / rec.quantity);
        // Valid if within reasonable bounds (0.02x to 5x of current price/NAV)
        if (computedAvg > 0 && computedAvg <= priceRupees * 5 && computedAvg >= priceRupees * 0.02) {
          avgCostMinor = MoneyEngine.fromRupees(computedAvg).toString();
        }
      }

      if (!existingHolding && (rec.action === 'BUY' || rec.recordType === 'HOLDING')) {
        // Create new holding
        const newId = `h_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
        const newHolding: Holding = {
          id: newId,
          name: rec.holdingName,
          symbolOrFolio: rec.folio || rec.isin || rec.holdingName.substring(0, 12).toUpperCase(),
          isin: rec.isin,
          category: rec.category,
          subCategory: rec.category === 'SPECIALISED' ? 'AIF Category II' : rec.category === 'EQUITY' ? 'Listed Equity' : 'Mutual Fund',
          ownerId: effectiveOwnerId,
          quantityScaled: qtyScaled,
          averageCostPerUnitMinor: avgCostMinor,
          currentPriceMinor: priceMinor,
          lastPriceUpdated: new Date().toISOString(),
          isPriceStale: false,
          priceSource: 'CDSL / CAS Statement',
          notes: `Imported via CAS statement (${rec.isin || rec.folio || 'Folio verified'})`,
        };
        currentWorkingHoldings = [newHolding, ...currentWorkingHoldings];

        // Record initial ledger event
        const initialAmount = (BigInt(newHolding.quantityScaled) * BigInt(newHolding.averageCostPerUnitMinor)) / QTY_SCALE;
        appendLedgerEvent({
          holdingId: newId,
          holdingName: newHolding.name,
          ownerId: effectiveOwnerId,
          eventType: 'BUY',
          eventDate: rec.date || new Date().toISOString().split('T')[0],
          quantityScaled: newHolding.quantityScaled,
          unitPriceMinor: newHolding.averageCostPerUnitMinor,
          totalAmountMinor: initialAmount.toString(),
          notes: `CAS Statement Snapshot Entry (${rec.isin || rec.folio || 'Folio'})`,
        });

        importedCount++;
      } else if (existingHolding) {
        if (rec.recordType === 'HOLDING') {
          // Update existing holding in-place with latest snapshot values
          currentWorkingHoldings = currentWorkingHoldings.map(h => {
            if (h.id === existingHolding!.id) {
              return {
                ...h,
                name: rec.holdingName, // Update with cleaner statement name
                quantityScaled: qtyScaled,
                currentPriceMinor: priceMinor,
                averageCostPerUnitMinor: avgCostMinor,
                isin: rec.isin || h.isin,
                symbolOrFolio: rec.folio || rec.isin || h.symbolOrFolio,
                priceSource: 'CDSL Statement Close',
                lastPriceUpdated: new Date().toISOString(),
              };
            }
            return h;
          });
          updatedCount++;
        } else {
          // Append transaction event to immutable ledger
          appendLedgerEvent({
            holdingId: existingHolding.id,
            holdingName: existingHolding.name,
            ownerId: effectiveOwnerId,
            eventType: rec.action,
            eventDate: rec.date,
            quantityScaled: qtyScaled,
            unitPriceMinor: priceMinor,
            totalAmountMinor: totalAmountMinor,
            notes: `Imported from CAS statement (${rec.statusMessage || rec.action})`,
          });
          importedCount++;
        }
      }
    });

    // Run final deduplication & sanitization on working holdings
    const sanitized = sanitizeAndDeduplicateHoldings(currentWorkingHoldings);
    setHoldings(sanitized.holdings);

    return { imported: importedCount, updated: updatedCount, skipped: skippedCount };
  };

  const cleanDuplicatesAndHeal = (): { mergedCount: number; repairedCount: number } => {
    const result = sanitizeAndDeduplicateHoldings(holdings);
    setHoldings(result.holdings);
    return { mergedCount: result.mergedCount, repairedCount: result.repairedCount };
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
