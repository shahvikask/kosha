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
import { xirr, buildCashFlows, totalGainPercent } from '../lib/returns';
import { foldHolding, financialYearOf, assessmentYearOf } from '../lib/lots';
import { derivePositions, marketValueMinor, DerivedState } from '../lib/positions';
import { encryptJson, decryptJson, isEncryptedBlob, generateRecoveryKey, maskPan, WrongPassphraseError } from '../lib/vault';
import { ruleSetFor, classifyGain, grandfatheredCostMinor, applyRateMinor } from '../lib/taxRules';
import { buildTree, TreeL1, HoldingPlacement } from '../lib/lookthrough';
import { ExposureNode } from '../lib/taxonomy';

interface KoshaContextType {
  familyMembers: FamilyMember[];
  selectedMemberId: string; // 'ALL' or family member ID
  setSelectedMemberId: (id: string) => void;
  holdings: Holding[];
  filteredHoldings: Holding[];
  ledgerEvents: LedgerEvent[];
  filteredEvents: LedgerEvent[];
  derived: DerivedState;
  taxonomy: { tree: TreeL1[]; exposureTotals: Map<ExposureNode, bigint>; placements: HoldingPlacement[] };
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
  correctEvent: (eventId: string, corrected: Partial<LedgerEvent>) => void;
  addFamilyMember: (member: Omit<FamilyMember, 'id'> & { pan?: string }) => FamilyMember;
  updateFamilyMember: (id: string, updates: Partial<FamilyMember> & { pan?: string }) => void;
  deleteFamilyMember: (id: string) => { deletedHoldingsCount: number; deletedEventsCount: number };
  computeTaxSummary: (financialYear: string) => TaxSummary;
  importStatementRecords: (
    records: StatementImportRecord[],
    targetOwnerId?: string,
    mode?: 'SYNC_UPDATE' | 'REPLACE_SNAPSHOT'
  ) => { imported: number; updated: number; skipped: number };
  cleanDuplicatesAndHeal: () => { mergedCount: number; flaggedCount: number };
  exportBackupJson: (passphrase?: string) => Promise<string>;
  importBackupJson: (dataOrJsonStr: string | any) => boolean;
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

export function sanitizeAndDeduplicateHoldings(rawHoldings: Holding[]): { holdings: Holding[]; mergedCount: number; flaggedCount: number } {
  const result: Holding[] = [];
  const seenMap = new Map<string, number>();
  let mergedCount = 0;
  let flaggedCount = 0;

  rawHoldings.forEach(h => {
    let qtyScaledNum = BigInt(h.quantityScaled || '0');
    let priceNum = BigInt(h.currentPriceMinor || '0');
    let costNum = BigInt(h.averageCostPerUnitMinor || h.currentPriceMinor || '0');
    const reviewReasons: string[] = [];

    // 1. Guard against corrupted quantities (e.g. folio numbers stored as unit quantities > 50,000,000 units)
    if (h.category === 'MUTUAL_FUND' && qtyScaledNum > 50_000_000n * QTY_SCALE) {
      reviewReasons.push('Quantity looks wrong - may be a folio number read as units');
    } else if (h.category === 'EQUITY' && qtyScaledNum > 10_000_000n * QTY_SCALE) {
      reviewReasons.push('Quantity looks wrong - unusually high share quantity');
    }

    // 2. Guard against corrupted prices / NAVs (e.g. ₹0 or > ₹50,000 for regular mutual funds)
    if (h.category === 'MUTUAL_FUND') {
      const priceRupees = MoneyEngine.toRupees(priceNum);
      if (priceRupees <= 0 || priceRupees > 50000) {
        reviewReasons.push('NAV/Price looks wrong - zero or unusually high NAV for mutual fund');
      }
    }

    // 3. Guard against corrupted cost numbers
    if (costNum <= 0n || (priceNum > 0n && costNum > priceNum * 10n)) {
      reviewReasons.push('Average cost looks wrong - zero or unusually high average cost per unit');
    }

    // 4. Holding Total Value Sanity check: individual retail holding must not exceed ₹100 Crore
    const holdingValMinor = (qtyScaledNum * priceNum) / QTY_SCALE;
    if (h.category !== 'REAL_ESTATE' && holdingValMinor > MoneyEngine.fromRupees(100_00_00_000)) {
      reviewReasons.push('Holding valuation exceeds sanity limit of ₹100 Crore');
    }

    if (reviewReasons.length > 0) {
      flaggedCount++;
    }

    const sanitized: Holding = {
      ...h,
      quantityScaled: qtyScaledNum.toString(),
      currentPriceMinor: priceNum.toString(),
      averageCostPerUnitMinor: costNum.toString(),
      ...(reviewReasons.length > 0
        ? { needsReview: true, reviewReasons }
        : { needsReview: h.needsReview, reviewReasons: h.reviewReasons }),
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

  return { holdings: result, mergedCount, flaggedCount };
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

  const derived = useMemo(
    () => derivePositions(filteredEvents, filteredHoldings),
    [filteredEvents, filteredHoldings]
  );

  const taxonomy = useMemo(() => {
    const valueOf = (h: Holding): bigint => {
      const qty = derived.positions.get(h.id)?.quantityScaled ?? 0n;
      const price = BigInt(h.currentPriceMinor || '0');
      const val = (qty * price) / QTY_SCALE;
      return val < 0n ? -val : val;
    };
    return buildTree(filteredHoldings, valueOf);
  }, [filteredHoldings, derived]);

  // Computed Portfolio Summary using scaled integer arithmetic
  const summary = useMemo<PortfolioSummary>(() => {
    let totalAssetsMinor = 0n;
    let totalLiabilitiesMinor = 0n;
    let totalInvestedMinor = 0n;
    let dayChangeMinor = 0n;

    filteredHoldings.forEach(h => {
      const qtyScaled = derived.positions.get(h.id)?.quantityScaled ?? 0n;
      
      // Effective price (check SGB benchmark mode)
      let priceMinor = BigInt(h.currentPriceMinor || '0');
      if (h.category === 'GOLD_SGB' && sgbValuationMode === 'RBI_BENCHMARK' && h.benchmarkPriceMinor) {
        priceMinor = BigInt(h.benchmarkPriceMinor);
      }

      const holdingCostMinor = derived.positions.get(h.id)?.costMinor ?? 0n;
      const holdingValueMinor = marketValueMinor(qtyScaled, priceMinor);

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

    // Calculate total dividends and interest from ledger
    let totalDividendsInterestMinor = 0n;
    filteredEvents.forEach(ev => {
      if (ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST') {
        totalDividendsInterestMinor += BigInt(ev.totalAmountMinor || '0');
      }
    });

    let totalRealisedGainsMinor = 0n;
    derived.positions.forEach(p => { totalRealisedGainsMinor += p.realisedGainMinor; });
    const realisedGainErrors = derived.errors;

    const currentValueRupees = Number(totalAssetsMinor) / 10000;
    const flows = buildCashFlows(
      filteredEvents,
      currentValueRupees,
      new Date().toISOString().split('T')[0]
    );
    const xirrPercent = xirr(flows);
    const totalGainPct = totalGainPercent(
      Number(totalInvestedMinor) / 10000,
      Number(totalAssetsMinor - totalInvestedMinor) / 10000
    );

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
      realisedGainErrors,
      xirrPercent,
      totalGainPercent: totalGainPct,
    };
  }, [filteredHoldings, filteredEvents, holdings, sgbValuationMode, derived]);

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
    // quantityScaled and averageCostPerUnitMinor are derived from the ledger and cannot be updated directly here.
    const { quantityScaled, averageCostPerUnitMinor, ...allowedUpdates } = updates;
    setHoldings(prev =>
      prev.map(h => {
        if (h.id === id) {
          return {
            ...h,
            ...allowedUpdates,
            lastPriceUpdated: allowedUpdates.currentPriceMinor ? new Date().toISOString() : h.lastPriceUpdated,
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
    // Confirmation / log notice: The records of what you bought and sold will be kept. Only the holding is removed.
    console.info('The records of what you bought and sold will be kept. Only the holding is removed.');
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

  const correctEvent = (eventId: string, corrected: Partial<LedgerEvent>) => {
    const original = ledgerEvents.find(e => e.id === eventId);
    if (!original) return;
    const correction: LedgerEvent = {
      ...original,
      ...corrected,
      id: `ev_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      eventType: 'CORRECTION',
      supersedesEventId: eventId,
      createdAt: new Date().toISOString(),
    };
    setLedgerEvents(prev => [correction, ...prev]);
  };

  const addFamilyMember = (memberData: Omit<FamilyMember, 'id'> & { pan?: string; panCard?: string }): FamilyMember => {
    const newId = `fam_${Date.now()}`;
    const rawPan = (memberData as any).pan || (memberData as any).panCard || memberData.panMasked || '';
    const masked = rawPan ? maskPan(rawPan) : undefined;
    const newMember: FamilyMember = {
      name: memberData.name,
      relation: memberData.relation,
      id: newId,
      panMasked: masked,
      dateOfBirth: memberData.dateOfBirth?.trim() || undefined,
      color: memberData.color,
      notes: memberData.notes?.trim() || undefined,
    };
    setFamilyMembers(prev => [...prev, newMember]);
    return newMember;
  };

  const updateFamilyMember = (id: string, updates: Partial<FamilyMember> & { pan?: string; panCard?: string }) => {
    setFamilyMembers(prev =>
      prev.map(m => {
        if (m.id === id) {
          const rawPan = updates.panMasked ?? (updates as any).panCard ?? (updates as any).pan;
          const updated: FamilyMember = { ...m, ...updates };
          if (rawPan !== undefined) {
            updated.panMasked = rawPan ? maskPan(rawPan) : undefined;
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

    // 2. Cascade delete holdings
    setHoldings(prev => prev.filter(h => h.ownerId !== id));

    // 3. NEVER delete ledger events. Events must be kept append-only.
    // Orphaned events will be reported by derivePositions.
    const deletedEventsCount = 0;

    // 4. Remove family member entity
    setFamilyMembers(prev => prev.filter(m => m.id !== id));

    // 5. Reset selectedMemberId if user had filtered by this deleted member
    if (selectedMemberId === id) {
      setSelectedMemberId('ALL');
    }

    return { deletedHoldingsCount, deletedEventsCount };
  };

  const computeTaxSummary = (financialYear: string): TaxSummary => {
    const sampleDate = `${financialYear.split('-')[0]}-06-01`;
    const assessmentYear = assessmentYearOf(sampleDate);
    const rules = ruleSetFor(assessmentYear);
    if (!rules) {
      return {
        financialYear,
        ltcgEquityTotalMinor: 0n,
        ltcgEquityExemptionMinor: 0n,
        ltcgEquityTaxableMinor: 0n,
        ltcgEquityTaxMinor: 0n,
        stcgEquityTotalMinor: 0n,
        stcgEquityTaxMinor: 0n,
        debtSlabGainsMinor: 0n,
        sgbExemptGainsMinor: 0n,
        dividendIncomeMinor: 0n,
        interestIncomeMinor: 0n,
        totalEstimatedTaxMinor: 0n,
        records: [],
        errors: [`Tax rules for ${assessmentYear} are not available. This year cannot be computed.`],
      };
    }

    // 1. Group filteredEvents by holdingId
    const eventsByHolding = new Map<string, LedgerEvent[]>();
    for (const ev of filteredEvents) {
      const list = eventsByHolding.get(ev.holdingId) || [];
      list.push(ev);
      eventsByHolding.set(ev.holdingId, list);
    }

    // Map of holdings for category lookup
    const holdingMap = new Map<string, Holding>();
    for (const h of holdings) {
      holdingMap.set(h.id, h);
    }

    const allDisposals: ReturnType<typeof foldHolding>['disposals'] = [];
    const collectedErrors: string[] = [];

    // 2 & 3. For each group, look up holding category, then call foldHolding
    eventsByHolding.forEach((events, holdingId) => {
      const holding = holdingMap.get(holdingId);
      const category: AssetCategory = holding ? holding.category : 'EQUITY';
      const foldRes = foldHolding(events, category);
      if (foldRes.errors && foldRes.errors.length > 0) {
        collectedErrors.push(...foldRes.errors);
      }
      for (const d of foldRes.disposals) {
        if (financialYearOf(d.saleDate) === financialYear) {
          allDisposals.push(d);
        }
      }
    });

    // 4 & 5. Map disposals to TaxGainItem
    const records: TaxGainItem[] = allDisposals.map((disposal, idx) => {
      const holding = holdingMap.get(disposal.holdingId);
      const category: AssetCategory = holding ? holding.category : 'EQUITY';

      const taxType = classifyGain(category, holding?.subCategory, disposal.isLongTerm);

      let applicableRate = 'Your slab rate — we cannot compute this';
      if (taxType === 'LTCG_EQUITY') {
        applicableRate = '12.5% (above ₹1.25L exemption)';
      } else if (taxType === 'STCG_EQUITY') {
        applicableRate = '20% under Sec 111A';
      } else if (taxType === 'SGB_EXEMPT') {
        applicableRate = 'Exempt Sec 47(ix)';
      } else if (taxType === 'REAL_ESTATE_LTCG') {
        applicableRate = '12.5% / 20%';
      } else {
        applicableRate = 'Your slab rate — we cannot compute this';
      }

      const fmv = holding?.fmv31Jan2018Minor ? BigInt(holding.fmv31Jan2018Minor) : null;
      const effectiveCost = grandfatheredCostMinor(
        rules, disposal.purchaseDate, disposal.costMinor, disposal.proceedsMinor, fmv);
      const gainMinor = disposal.proceedsMinor - effectiveCost;

      const estimatedTaxMinor = taxType === 'STCG_EQUITY'
        ? applyRateMinor(gainMinor, rules.stcgEquityRateBasisPoints) : 0n;

      return {
        id: `${disposal.saleEventId}_${disposal.purchaseEventId}_${idx}`,
        holdingId: disposal.holdingId,
        holdingName: disposal.holdingName,
        category,
        taxType,
        saleDate: disposal.saleDate,
        purchaseDate: disposal.purchaseDate,
        holdingDays: disposal.holdingDays,
        saleQuantity: Number(disposal.qtyScaled) / 100000000,
        saleAmountMinor: disposal.proceedsMinor,
        buyCostMinor: effectiveCost,
        gainMinor,
        estimatedTaxMinor,
        applicableRate,
      };
    });

    // 6. Totals in BigInt
    let ltcgEquityTotalMinor = 0n;
    let stcgEquityTotalMinor = 0n;
    let sgbExemptGainsMinor = 0n;
    let debtSlabGainsMinor = 0n;

    for (const r of records) {
      if (r.taxType === 'LTCG_EQUITY') {
        ltcgEquityTotalMinor += r.gainMinor;
      } else if (r.taxType === 'STCG_EQUITY') {
        stcgEquityTotalMinor += r.gainMinor;
      } else if (r.taxType === 'SGB_EXEMPT') {
        sgbExemptGainsMinor += r.gainMinor;
      } else if (r.taxType === 'DEBT_SLAB') {
        debtSlabGainsMinor += r.gainMinor;
      }
    }

    const ltcgEquityExemptionMinor = BigInt(rules.ltcgEquityExemptionRupees) * 10000n;
    const ltcgEquityTaxableMinor = ltcgEquityTotalMinor > ltcgEquityExemptionMinor
      ? ltcgEquityTotalMinor - ltcgEquityExemptionMinor
      : 0n;
    const ltcgEquityTaxMinor = applyRateMinor(ltcgEquityTaxableMinor, rules.ltcgEquityRateBasisPoints);

    const stcgEquityTaxableBase = stcgEquityTotalMinor > 0n ? stcgEquityTotalMinor : 0n;
    const stcgEquityTaxMinor = applyRateMinor(stcgEquityTaxableBase, rules.stcgEquityRateBasisPoints);

    // Sum actual DIVIDEND and INTEREST events in this financial year
    let dividendIncomeMinor = 0n;
    let interestIncomeMinor = 0n;

    for (const ev of filteredEvents) {
      if (financialYearOf(ev.eventDate) === financialYear) {
        let amt = BigInt(ev.totalAmountMinor || '0');
        if (amt === 0n) {
          const qty = BigInt(ev.quantityScaled || '0');
          const unit = BigInt(ev.unitPriceMinor || '0');
          amt = (qty * unit) / QTY_SCALE;
        }
        if (ev.eventType === 'DIVIDEND') {
          dividendIncomeMinor += amt;
        } else if (ev.eventType === 'INTEREST') {
          interestIncomeMinor += amt;
        }
      }
    }

    const totalEstimatedTaxMinor = ltcgEquityTaxMinor + stcgEquityTaxMinor;

    return {
      financialYear,
      ltcgEquityTotalMinor,
      ltcgEquityExemptionMinor,
      ltcgEquityTaxableMinor,
      ltcgEquityTaxMinor,
      stcgEquityTotalMinor,
      stcgEquityTaxMinor,
      debtSlabGainsMinor,
      sgbExemptGainsMinor,
      dividendIncomeMinor,
      interestIncomeMinor,
      totalEstimatedTaxMinor,
      records,
      errors: collectedErrors.length > 0 ? collectedErrors : undefined,
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

  const cleanDuplicatesAndHeal = (): { mergedCount: number; flaggedCount: number } => {
    const result = sanitizeAndDeduplicateHoldings(holdings);
    setHoldings(result.holdings);
    return { mergedCount: result.mergedCount, flaggedCount: result.flaggedCount };
  };

  const exportBackupJson = async (passphrase?: string): Promise<string> => {
    const backupData = {
      koshaVersion: '1.0.0',
      exportedAt: new Date().toISOString(),
      familyMembers,
      holdings,
      ledgerEvents,
    };
    if (passphrase) {
      const blob = await encryptJson(backupData, passphrase);
      return JSON.stringify(blob, null, 2);
    }
    return JSON.stringify(backupData, null, 2);
  };

  const importBackupJson = (dataOrJsonStr: string | any): boolean => {
    try {
      const data = typeof dataOrJsonStr === 'string' ? JSON.parse(dataOrJsonStr) : dataOrJsonStr;
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
        derived,
        taxonomy,
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
        correctEvent,
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
