import React, { createContext, useContext, useState, useEffect, useMemo } from 'react';
import {
  FamilyMember,
  Holding,
  LedgerEvent,
  NewHoldingInput,
  PortfolioSummary,
  AssetCategory,
  TaxSummary,
  TaxGainItem,
  StatementImportRecord,
  isPrimaryFamilyMember,
  AppScreen,
  DematOrFolioAccount,
  FamilyGoal,
  ImportBatchInfo,
  ListedSecurity,
  StatementSnapRow,
  StatementHoldingSnap,
} from '../types';
import { INITIAL_FAMILY_MEMBERS } from '../data/initialData';
import { EMPTY_SELF, ThemeName, looksLikeSgb, metricsOf } from '../lib/holdingView';
import { applyTheme, readStoredTheme, writeStoredTheme } from '../lib/theme';
import { findMatchingHolding, holdingIdentityKey, preferSchemeName } from '../lib/matchHolding';
export { findMatchingHolding, normalizeHoldingName } from '../lib/matchHolding';
import { MoneyEngine, MONEY_SCALE, QTY_SCALE } from '../lib/money';
import { xirr, buildCashFlows, totalGainPercent } from '../lib/returns';
import { foldHolding, financialYearOf, assessmentYearOf } from '../lib/lots';
import { derivePositions, DerivedState } from '../lib/positions';
import { maskPan } from '../lib/vault';
import { packBackup, unpackBackup, verifyRestoreIdentity, VaultBackup, RestoreIdentity, BackupError, BACKUP_KIND, BACKUP_VERSION } from '../lib/backup';
import {
  VaultAccountPublic,
  toPublicAccount,
  titleCaseName,
  passwordIssues,
  isValidPan,
  isValidEmail,
  normaliseAnswer,
  needsPasswordRotation,
  hashSecret,
  verifySecret,
  packHash,
  unpackHash,
  writeRememberedEmail,
  clearRememberedEmail,
} from '../lib/auth';
import { ruleSetFor, classifyGain, grandfatheredCostMinor, applyRateMinor } from '../lib/taxRules';
import { SqlDriver } from '../db/driver';
import { openVaultDriver, errorMessage } from '../db/platform';
import { Repository } from '../db/repository';
import { resetOnboarding } from '../lib/onboarding';
import { migrateFromLocalStorage, MigrationReport } from '../db/migrate';
import { reconcileStatement, eventsToCommit, SnapshotLine, ReconciliationResult, snapshotLinesToClose } from '../lib/snapshot';
import {
  CasImportMeta,
  CasLatestState,
  EMPTY_CAS_LATEST,
  parseCasLatest,
  upsertCasLatest,
  latestAsOfForOwner,
  statementAsOfByMember,
  MemberStatementAsOf,
} from '../lib/casPython';
import { planEmiCatchUp, applyEmiPlan } from '../lib/emi';
import { planInterestCatchUp, applyInterestPlan } from '../lib/interest';
import { planInvestedCorrection, isUnknownInvested } from '../lib/costBasis';
import { findDuplicatePanMember } from '../lib/pan';
import { applyCatalogPrices, catalogToListed, isEodPriceSource, refreshEodFromSidecar } from '../lib/eodPrices';
import { casHoldingSubCategory, casImportDetails } from '../lib/holdingForm';

interface KoshaContextType {
  familyMembers: FamilyMember[];
  selectedMemberId: string; // 'ALL' or family member ID
  setSelectedMemberId: (id: string) => void;
  holdings: Holding[];
  filteredHoldings: Holding[];
  ledgerEvents: LedgerEvent[];
  filteredEvents: LedgerEvent[];
  derived: DerivedState;
  accounts: DematOrFolioAccount[];
  goals: FamilyGoal[];
  importBatches: ImportBatchInfo[];
  statementSnaps: StatementSnapRow[];
  statementHoldingSnaps: StatementHoldingSnap[];
  isPrivacyMasked: boolean;
  setIsPrivacyMasked: (val: boolean | ((prev: boolean) => boolean)) => void;
  sgbValuationMode: 'MARKET_TRADED' | 'RBI_BENCHMARK';
  setSgbValuationMode: (mode: 'MARKET_TRADED' | 'RBI_BENCHMARK') => void;
  theme: ThemeName;
  setTheme: (theme: ThemeName) => void;
  summary: PortfolioSummary;
  activeScreen: AppScreen;
  setActiveScreen: (screen: AppScreen) => void;
  
  // Database status
  repo: Repository | null;
  dbReady: boolean;
  dbError: string | null;
  migration: MigrationReport | null;

  // Actions
  addHolding: (holding: NewHoldingInput) => Promise<Holding>;
  updateHolding: (id: string, updates: Partial<Holding>) => Promise<void>;
  deleteHolding: (id: string) => Promise<void>;
  appendLedgerEvent: (event: Omit<LedgerEvent, 'id' | 'createdAt'>) => Promise<LedgerEvent>;
  addLedgerEvent?: (e: LedgerEvent) => Promise<void>;
  correctEvent: (eventId: string, corrected: Partial<LedgerEvent>) => Promise<void>;
  correctInvested: (holdingId: string, input: {
    unitPriceRupees: number;
    acquiredOn: string;
    issuePriceRupees?: number;
  }) => Promise<void>;
  addFamilyMember: (member: Omit<FamilyMember, 'id'> & { pan?: string }) => Promise<FamilyMember>;
  updateFamilyMember: (id: string, updates: Partial<FamilyMember> & { pan?: string }) => Promise<void>;
  deleteFamilyMember: (id: string) => Promise<{ deletedHoldingsCount: number; deletedEventsCount: number }>;
  computeTaxSummary: (financialYear: string) => TaxSummary;
  importStatementRecords: (
    records: StatementImportRecord[],
    targetOwnerId?: string,
    mode?: 'SYNC_UPDATE' | 'REPLACE_SNAPSHOT',
    casMeta?: CasImportMeta
  ) => Promise<{ imported: number; updated: number; skipped: number; reconciliationResult?: ReconciliationResult }>;
  lastStatementAsOf: string | null;
  statementAsOfByMember: MemberStatementAsOf[];
  casLatest: CasLatestState;
  cleanDuplicatesAndHeal: () => Promise<{ mergedCount: number; flaggedCount: number }>;
  confirmVaultPassword: (email: string, password: string) => Promise<string | null>;
  createLockedBackup: (email: string, password: string) => Promise<Uint8Array>;
  restoreLockedBackup: (bytes: Uint8Array, identity: RestoreIdentity) => Promise<string | null>;
  startOver: () => Promise<void>;
  deleteAccountAndData: () => Promise<void>;
  updateHoldingPrice: (id: string, newPriceRupees: number) => Promise<void>;
  upsertGoal: (goal: Omit<FamilyGoal, 'id' | 'createdAt'> & { id?: string }) => Promise<FamilyGoal>;
  deleteGoal: (id: string) => Promise<void>;
  updateAccountNominee: (accountId: string, nominee: string) => Promise<void>;
  listedSecurities: ListedSecurity[];
  pricesAsOf: string | null;
  pricesRefreshing: boolean;
  pricesError: string | null;
  refreshEodPrices: () => Promise<void>;
  account: VaultAccountPublic | null;
  unlocked: boolean;
  mustRotatePassword: boolean;
  registerOwner: (input: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    pan: string;
    dateOfBirth: string;
    q1Id: string;
    q1Answer: string;
    q2Id: string;
    q2Answer: string;
  }) => Promise<string | null>;
  login: (email: string, password: string) => Promise<string | null>;
  lockVault: () => void;
  changePassword: (oldPassword: string, newPassword: string) => Promise<string | null>;
  resetWithQuestions: (email: string, a1: string, a2: string, newPassword: string) => Promise<string | null>;
}

const KoshaContext = createContext<KoshaContextType | null>(null);

export function casAccountId(
  rec: Pick<StatementImportRecord, 'dematAccount' | 'folio'>
): string | undefined {
  const ident = rec.dematAccount || rec.folio;
  if (!ident) return undefined;
  const slug = ident.replace(/[^A-Za-z0-9]/g, '').slice(0, 24);
  return `acct_${slug || 'x'}`;
}

function emiEventsFromPlan(holdingId: string, plan: NonNullable<ReturnType<typeof planEmiCatchUp>>): LedgerEvent[] {
  const now = new Date().toISOString();
  return plan.events.map(e => ({
    ...e,
    id: `ev_emi_${holdingId}_${e.eventDate}`,
    createdAt: now,
  }));
}

function interestEventsFromPlan(
  holdingId: string,
  plan: NonNullable<ReturnType<typeof planInterestCatchUp>>
): LedgerEvent[] {
  const now = new Date().toISOString();
  return plan.events.map(e => ({
    ...e,
    id: `ev_int_${holdingId}_${e.eventDate}`,
    createdAt: now,
  }));
}

async function catchUpHoldings(
  r: Repository,
  holdings: Holding[],
  events: LedgerEvent[],
  asOf: string
): Promise<{ holdings: Holding[]; events: LedgerEvent[] }> {
  const extra: LedgerEvent[] = [];
  const next: Holding[] = [];
  for (const h of holdings) {
    let current = h;
    const postedEmi = [...events, ...extra]
      .filter(e => e.holdingId === h.id && e.eventType === 'REPAYMENT')
      .map(e => e.eventDate);
    const emiPlan = planEmiCatchUp(current, asOf, postedEmi);
    if (emiPlan) {
      const rows = emiEventsFromPlan(h.id, emiPlan);
      current = applyEmiPlan(current, emiPlan);
      await r.commitEmiCatchUp(current, rows);
      extra.push(...rows);
    }
    const postedInt = [...events, ...extra]
      .filter(e => e.holdingId === h.id && e.eventType === 'INTEREST')
      .map(e => e.eventDate);
    const qty = BigInt(current.quantityScaled || '0');
    const intPlan = planInterestCatchUp(current, asOf, postedInt, qty > 0n ? qty : undefined);
    if (intPlan) {
      const rows = interestEventsFromPlan(h.id, intPlan);
      current = applyInterestPlan(current, intPlan);
      await r.commitEmiCatchUp(current, rows);
      extra.push(...rows);
    }
    next.push(current);
  }
  return { holdings: next, events: extra.length ? [...extra, ...events] : events };
}

function holdingIdsForImportedRecords(
  records: StatementImportRecord[],
  working: Holding[],
  ownerId: string
): string[] {
  const ids: string[] = [];
  for (const rec of records) {
    const matched = findMatchingHolding(working, rec, ownerId);
    if (matched) ids.push(matched.id);
  }
  return Array.from(new Set(ids));
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

    const key = holdingIdentityKey(h);

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
  const [repo, setRepo] = useState<Repository | null>(null);
  const [dbDriver, setDbDriver] = useState<SqlDriver | null>(null);
  const [dbReady, setDbReady] = useState(false);
  const [dbError, setDbError] = useState<string | null>(null);
  const [migration, setMigration] = useState<MigrationReport | null>(null);

  // State loaded from SQLite
  const [familyMembers, setFamilyMembers] = useState<FamilyMember[]>([]);
  const [holdings, setHoldings] = useState<Holding[]>([]);
  const [ledgerEvents, setLedgerEvents] = useState<LedgerEvent[]>([]);

  const [selectedMemberId, setSelectedMemberId] = useState<string>('ALL');
  const [isPrivacyMasked, setIsPrivacyMaskedState] = useState<boolean>(false);
  const [sgbValuationMode, setSgbValuationModeState] = useState<'MARKET_TRADED' | 'RBI_BENCHMARK'>('MARKET_TRADED');
  const [theme, setThemeState] = useState<ThemeName>(() => readStoredTheme() || 'light');
  const [activeScreen, setActiveScreen] = useState<AppScreen>('HOME');
  const [casLatest, setCasLatest] = useState<CasLatestState>(EMPTY_CAS_LATEST);
  const [accounts, setAccounts] = useState<DematOrFolioAccount[]>([]);
  const [goals, setGoals] = useState<FamilyGoal[]>([]);
  const [importBatches, setImportBatches] = useState<ImportBatchInfo[]>([]);
  const [statementSnaps, setStatementSnaps] = useState<StatementSnapRow[]>([]);
  const [statementHoldingSnaps, setStatementHoldingSnaps] = useState<StatementHoldingSnap[]>([]);
  const [listedSecurities, setListedSecurities] = useState<ListedSecurity[]>([]);
  const [pricesAsOf, setPricesAsOf] = useState<string | null>(null);
  const [pricesRefreshing, setPricesRefreshing] = useState(false);
  const [pricesError, setPricesError] = useState<string | null>(null);
  const [account, setAccount] = useState<VaultAccountPublic | null>(null);
  const [unlocked, setUnlocked] = useState(false);
  const [mustRotatePassword, setMustRotatePassword] = useState(false);

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);


  useEffect(() => {
    (async () => {
      try {
        const driver = await openVaultDriver();
        const r = new Repository(driver);
        await r.initialise();
        const report = await migrateFromLocalStorage(r, window.localStorage);
        setMigration(report.ran ? report : null);
        
        let entities = await r.allEntities();
        let currentHoldings = await r.allHoldings();
        let currentEvents = await r.allEvents();

        // One-time: restore relation/colour for sample entity ids after v3
        // columns were added (those fields were previously dropped on write).
        const backfilled = await r.getMeta('family_profile_v3_backfill');
        if (backfilled !== 'true' && entities.length > 0) {
          const byId = new Map(entities.map(e => [e.id, e]));
          for (const seed of INITIAL_FAMILY_MEMBERS) {
            const cur = byId.get(seed.id);
            if (!cur) continue;
            await r.upsertEntity({
              ...cur,
              relation: seed.relation,
              color: seed.color || cur.color,
              notes: cur.notes || seed.notes,
              dateOfBirth: cur.dateOfBirth || seed.dateOfBirth,
              isPrimary: seed.relation === 'Self',
            });
          }
          const after = await r.allEntities();
          if (after.length && !after.some(e => isPrimaryFamilyMember(e))) {
            await r.upsertEntity({ ...after[0], relation: 'Self', isPrimary: true });
          }
          await r.setMeta('family_profile_v3_backfill', 'true');
          entities = await r.allEntities();
        }

        // Fresh install: one Self, no sample stocks
        if (entities.length === 0 && currentHoldings.length === 0 && currentEvents.length === 0) {
          await r.upsertEntity(EMPTY_SELF);
          entities = await r.allEntities();
        }

        const prefsRaw = await r.getMeta('ui_prefs');
        if (prefsRaw) {
          try {
            const prefs = JSON.parse(prefsRaw);
            if (prefs.privacy === true) setIsPrivacyMaskedState(true);
            if (prefs.sgb === 'RBI_BENCHMARK' || prefs.sgb === 'MARKET_TRADED') {
              setSgbValuationModeState(prefs.sgb);
            }
            if (prefs.theme === 'light' || prefs.theme === 'dark') {
              setThemeState(prefs.theme);
              writeStoredTheme(prefs.theme);
              applyTheme(prefs.theme);
            }
          } catch {
            /* ignore */
          }
        } else {
          const stored = readStoredTheme();
          if (stored) {
            setThemeState(stored);
            applyTheme(stored);
          }
        }

        setFamilyMembers(entities);
        const asOf = new Date().toISOString().slice(0, 10);
        const sgbFixed: Holding[] = [];
        for (const h of currentHoldings) {
          if (looksLikeSgb(h.name) && h.category !== 'GOLD_SGB') {
            const fixed = { ...h, category: 'GOLD_SGB' as const, subCategory: h.subCategory || 'SGB' };
            if (r) await r.upsertHolding(fixed);
            sgbFixed.push(fixed);
          } else {
            sgbFixed.push(h);
          }
        }
        currentHoldings = sgbFixed;
        const caught = await catchUpHoldings(r, currentHoldings, currentEvents, asOf);
        setHoldings(caught.holdings);
        setLedgerEvents(caught.events);
        setCasLatest(parseCasLatest(await r.getMeta('cas_latest')));
        setAccounts(await r.allAccounts());
        setGoals(await r.allGoals());
        setImportBatches(await r.allBatches());
        try {
          setStatementSnaps(await r.statementSnapshotTotals());
          setStatementHoldingSnaps(await r.latestHoldingStatementValues());
        } catch {
          setStatementSnaps([]);
          setStatementHoldingSnaps([]);
        }
        try {
          const listed = await r.allListedSecurities();
          setListedSecurities(listed);
          setPricesAsOf(await r.getMeta('prices_as_of'));
        } catch {
          setListedSecurities([]);
        }
        try {
          const acc = await r.getVaultAccount();
          if (acc) {
            setAccount(toPublicAccount(acc));
            setMustRotatePassword(needsPasswordRotation(acc.passwordChangedAt));
          }
        } catch {
          setAccount(null);
        }
        setRepo(r);
        setDbDriver(driver);
        setDbReady(true);
      } catch (err: any) {
        console.error('Failed to initialize SQLite Database:', err);
        setDbError(errorMessage(err) || 'Database initialization error');
        setFamilyMembers([EMPTY_SELF]);
        setHoldings([]);
        setLedgerEvents([]);
        setDbReady(true);
      }
    })();
  }, []);

  const filteredHoldings = useMemo(() => {
    if (selectedMemberId === 'ALL') return holdings;
    return holdings.filter(h => h.ownerId === selectedMemberId);
  }, [holdings, selectedMemberId]);

  const filteredEvents = useMemo(() => {
    if (selectedMemberId === 'ALL') return ledgerEvents;
    return ledgerEvents.filter(e => e.ownerId === selectedMemberId);
  }, [ledgerEvents, selectedMemberId]);

  const derived = useMemo(() => {
    return derivePositions(filteredEvents, filteredHoldings);
  }, [filteredEvents, filteredHoldings]);

  const persistUiPrefs = async (next: {
    privacy?: boolean;
    sgb?: 'MARKET_TRADED' | 'RBI_BENCHMARK';
    theme?: ThemeName;
  }) => {
    const payload = {
      privacy: next.privacy ?? isPrivacyMasked,
      sgb: next.sgb ?? sgbValuationMode,
      theme: next.theme ?? theme,
    };
    if (repo) await repo.setMeta('ui_prefs', JSON.stringify(payload));
  };

  const setIsPrivacyMasked = (val: boolean | ((prev: boolean) => boolean)) => {
    setIsPrivacyMaskedState(prev => {
      const next = typeof val === 'function' ? val(prev) : val;
      persistUiPrefs({ privacy: next });
      return next;
    });
  };

  const setSgbValuationMode = (mode: 'MARKET_TRADED' | 'RBI_BENCHMARK') => {
    setSgbValuationModeState(mode);
    persistUiPrefs({ sgb: mode });
  };

  const setTheme = (next: ThemeName) => {
    setThemeState(next);
    writeStoredTheme(next);
    applyTheme(next);
    persistUiPrefs({ theme: next });
  };

  const summary = useMemo((): PortfolioSummary => {
    let totalAssetsMinor = 0n;
    let totalLiabilitiesMinor = 0n;
    let totalInvestedMinor = 0n;
    let totalDividendsInterestMinor = 0n;
    let totalRealisedGainsMinor = 0n;

    filteredHoldings.forEach(h => {
      const m = metricsOf(h, derived, sgbValuationMode);
      if (h.category === 'LIABILITY') {
        totalLiabilitiesMinor += m.value;
      } else {
        totalAssetsMinor += m.value;
        if (!isUnknownInvested(h, filteredEvents, m.cost, m.value)) {
          totalInvestedMinor += m.cost;
        }
      }
    });

    const netWorthMinor = totalAssetsMinor - totalLiabilitiesMinor;
    const unrealisedGainMinor = totalAssetsMinor - totalInvestedMinor;
    const unrealisedGainPercent = totalInvestedMinor > 0n
      ? (Number(unrealisedGainMinor) / Number(totalInvestedMinor)) * 100
      : 0;

    const dayChangeMinor = 0n;
    const dayChangePercent = 0;

    filteredEvents.forEach(ev => {
      if (ev.eventType === 'DIVIDEND' || ev.eventType === 'INTEREST') {
        totalDividendsInterestMinor += BigInt(ev.totalAmountMinor || '0');
      }
    });

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

  // Actions writing through SQLite Repository first
  const addLedgerEvent = async (e: LedgerEvent) => {
    if (!repo) return;
    await repo.appendEvent(e);
    setLedgerEvents(prev => [e, ...prev]);
  };

  const appendLedgerEvent = async (eventData: Omit<LedgerEvent, 'id' | 'createdAt'>): Promise<LedgerEvent> => {
    const newEvent: LedgerEvent = {
      ...eventData,
      id: `ev_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
      createdAt: new Date().toISOString(),
    };

    if (repo) {
      await repo.appendEvent(newEvent);
    }
    setLedgerEvents(prev => [newEvent, ...prev]);
    return newEvent;
  };

  const addHolding = async (holdingData: NewHoldingInput): Promise<Holding> => {
    const { chargesMinor: buyChargesRaw, ...rest } = holdingData;
    const buyCharges = buyChargesRaw && buyChargesRaw !== '0' ? buyChargesRaw : undefined;
    const isin = (rest.isin || '').trim().toUpperCase();
    if (isin.length >= 12 && rest.category !== 'LIABILITY') {
      const existing = holdings.find(
        h => h.ownerId === rest.ownerId && (h.isin || '').toUpperCase() === isin
      );
      if (existing) {
        await appendLedgerEvent({
          holdingId: existing.id,
          holdingName: existing.name,
          ownerId: existing.ownerId,
          eventType: 'BUY',
          eventDate: rest.acquiredOn || new Date().toISOString().split('T')[0],
          quantityScaled: rest.quantityScaled,
          unitPriceMinor: rest.averageCostPerUnitMinor,
          totalAmountMinor: (
            (BigInt(rest.quantityScaled) * BigInt(rest.averageCostPerUnitMinor)) / QTY_SCALE
          ).toString(),
          chargesMinor: buyCharges,
          notes: 'Buy on existing holding (same ISIN)',
        });
        if (rest.currentPriceMinor && rest.currentPriceMinor !== existing.currentPriceMinor) {
          const priced: Holding = {
            ...existing,
            currentPriceMinor: rest.currentPriceMinor,
            lastPriceUpdated: new Date().toISOString(),
            priceSource: rest.priceSource || existing.priceSource,
            isin: existing.isin || isin,
          };
          if (repo) await repo.upsertHolding(priced);
          setHoldings(prev => prev.map(h => (h.id === existing.id ? priced : h)));
          return priced;
        }
        return existing;
      }
    }

    const newId = `h_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
    const newHolding: Holding = {
      ...rest,
      id: newId,
      lastPriceUpdated: new Date().toISOString(),
      isPriceStale: false,
    };

    if (repo) {
      await repo.upsertHolding(newHolding);
    }

    const isLoan = rest.category === 'LIABILITY';
    const initialAmount = isLoan
      ? (rest.currentPriceMinor || '0')
      : (BigInt(rest.quantityScaled) * BigInt(rest.averageCostPerUnitMinor)) / QTY_SCALE;
    const eventDate = rest.acquiredOn || new Date().toISOString().split('T')[0];
    await appendLedgerEvent({
      holdingId: newId,
      holdingName: rest.name,
      ownerId: rest.ownerId,
      eventType: isLoan ? 'VALUATION_UPDATE' : 'BUY',
      eventDate,
      quantityScaled: isLoan ? '0' : rest.quantityScaled,
      unitPriceMinor: isLoan
        ? (rest.currentPriceMinor || rest.averageCostPerUnitMinor)
        : rest.averageCostPerUnitMinor,
      totalAmountMinor: initialAmount.toString(),
      chargesMinor: isLoan ? undefined : buyCharges,
      notes: isLoan ? 'Loan recorded — outstanding at start' : 'Initial holding record creation',
    });

    let saved = newHolding;
    if (rest.category === 'LIABILITY' && repo) {
      const plan = planEmiCatchUp(newHolding, new Date().toISOString().slice(0, 10));
      if (plan) {
        const rows = emiEventsFromPlan(newId, plan);
        saved = applyEmiPlan(newHolding, plan);
        await repo.commitEmiCatchUp(saved, rows);
        setLedgerEvents(prev => [...rows, ...prev]);
      }
    }

    setHoldings(prev => [saved, ...prev.filter(h => h.id !== newId)]);
    return saved;
  };

  const persistInterestCatchUp = async (
    holding: Holding,
    events: LedgerEvent[]
  ): Promise<{ holding: Holding; extra: LedgerEvent[] }> => {
    const asOf = new Date().toISOString().slice(0, 10);
    const posted = events
      .filter(e => e.holdingId === holding.id && e.eventType === 'INTEREST')
      .map(e => e.eventDate);
    const qty = BigInt(holding.quantityScaled || '0');
    const plan = planInterestCatchUp(holding, asOf, posted, qty > 0n ? qty : undefined);
    if (!plan) return { holding, extra: [] };
    const rows = interestEventsFromPlan(holding.id, plan);
    const next = applyInterestPlan(holding, plan);
    if (repo) await repo.commitEmiCatchUp(next, rows);
    return { holding: next, extra: rows };
  };

  const updateHolding = async (id: string, updates: Partial<Holding>): Promise<void> => {
    const { quantityScaled, averageCostPerUnitMinor, ...allowedUpdates } = updates;
    const existing = holdings.find(h => h.id === id);
    if (!existing) return;

    let updatedHolding: Holding = {
      ...existing,
      ...allowedUpdates,
      lastPriceUpdated: allowedUpdates.currentPriceMinor ? new Date().toISOString() : existing.lastPriceUpdated,
    };

    const interestTouched =
      allowedUpdates.couponRatePercent !== undefined ||
      allowedUpdates.interestRatePercent !== undefined ||
      allowedUpdates.issuePriceMinor !== undefined ||
      allowedUpdates.acquiredOn !== undefined;

    if (repo) {
      await repo.upsertHolding(updatedHolding);
    }

    let extra: LedgerEvent[] = [];
    if (interestTouched) {
      const caught = await persistInterestCatchUp(
        { ...updatedHolding, lastPriceUpdated: existing.lastPriceUpdated },
        ledgerEvents
      );
      updatedHolding = {
        ...caught.holding,
        lastPriceUpdated: caught.extra.length ? caught.holding.lastPriceUpdated : updatedHolding.lastPriceUpdated,
      };
      extra = caught.extra;
    }

    setHoldings(prev => prev.map(h => (h.id === id ? updatedHolding : h)));
    if (extra.length) setLedgerEvents(prev => [...extra, ...prev]);
  };

  const updateHoldingPrice = async (id: string, newPriceRupees: number): Promise<void> => {
    const newPriceMinor = MoneyEngine.fromRupees(newPriceRupees).toString();
    await updateHolding(id, {
      currentPriceMinor: newPriceMinor,
      lastPriceUpdated: new Date().toISOString(),
      isPriceStale: false,
      priceSource: 'Manual Verified Price Update',
    });
  };

  const deleteHolding = async (id: string): Promise<void> => {
    console.info('The records of what you bought and sold will be kept. Only the holding is removed.');
    if (repo) {
      await repo.deleteHolding(id);
    }
    setHoldings(prev => prev.filter(h => h.id !== id));
  };

  const correctEvent = async (eventId: string, corrected: Partial<LedgerEvent>): Promise<void> => {
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
    if (repo) {
      await repo.appendEvent(correction);
    }
    setLedgerEvents(prev => [correction, ...prev]);
  };

  const correctInvested = async (
    holdingId: string,
    input: { unitPriceRupees: number; acquiredOn: string; issuePriceRupees?: number }
  ): Promise<void> => {
    const existing = holdings.find(h => h.id === holdingId);
    if (!existing) return;
    const unitPriceMinor = MoneyEngine.fromRupees(input.unitPriceRupees);
    if (unitPriceMinor <= 0n) return;
    const planned = planInvestedCorrection({
      holding: existing,
      events: ledgerEvents,
      unitPriceMinor,
      acquiredOn: input.acquiredOn,
    });
    let extra: LedgerEvent[] = [];
    if (planned) {
      const correction: LedgerEvent = {
        ...planned,
        id: `ev_cost_${holdingId}_${Date.now().toString(36)}`,
        createdAt: new Date().toISOString(),
      };
      if (repo) await repo.appendEvent(correction);
      extra.push(correction);
    }
    const issue =
      input.issuePriceRupees != null && input.issuePriceRupees > 0
        ? MoneyEngine.fromRupees(input.issuePriceRupees).toString()
        : existing.issuePriceMinor;
    const acquiredOn = /^\d{4}-\d{2}-\d{2}$/.test(input.acquiredOn)
      ? input.acquiredOn
      : (planned?.eventDate || existing.acquiredOn);
    let next: Holding = {
      ...existing,
      acquiredOn: acquiredOn || existing.acquiredOn,
      issuePriceMinor: issue,
    };
    if (repo) await repo.upsertHolding(next);
    const eventsNow = extra.length ? [...extra, ...ledgerEvents] : ledgerEvents;
    const caught = await persistInterestCatchUp(next, eventsNow);
    next = caught.holding;
    extra = [...extra, ...caught.extra];
    setHoldings(prev => prev.map(h => (h.id === holdingId ? next : h)));
    if (extra.length) setLedgerEvents(prev => [...extra, ...prev]);
  };

  const addFamilyMember = async (memberData: Omit<FamilyMember, 'id'> & { pan?: string; panCard?: string; panForStatements?: string }): Promise<FamilyMember> => {
    const wantsPrimary = isPrimaryFamilyMember({ relation: memberData.relation, isPrimary: memberData.isPrimary });
    if (wantsPrimary && familyMembers.some(m => isPrimaryFamilyMember(m))) {
      throw new Error('There is already a primary member (Self). Additional members must be Spouse, Parent, Child, HUF or Joint.');
    }
    const newId = `fam_${Date.now()}`;
    const rawPan = (memberData as any).pan || (memberData as any).panCard || memberData.panMasked || '';
    const dup = findDuplicatePanMember(
      familyMembers,
      memberData.panForStatements || rawPan || ''
    );
    if (dup) {
      throw new Error(`PAN already used by ${dup.name} (${dup.relation}). Each person needs their own PAN.`);
    }
    const masked = rawPan ? maskPan(rawPan) : undefined;
    const newMember: FamilyMember = {
      name: memberData.name,
      relation: wantsPrimary ? 'Self' : memberData.relation,
      id: newId,
      panMasked: masked,
      panForStatements: memberData.panForStatements?.trim().toUpperCase() || undefined,
      dateOfBirth: memberData.dateOfBirth?.trim() || undefined,
      color: memberData.color,
      notes: memberData.notes?.trim() || undefined,
      isPrimary: wantsPrimary,
    };
    if (repo) {
      await repo.upsertEntity(newMember);
    }
    setFamilyMembers(prev => [...prev, newMember]);
    return newMember;
  };

  const updateFamilyMember = async (id: string, updates: Partial<FamilyMember> & { pan?: string; panCard?: string; panForStatements?: string }): Promise<void> => {
    const existing = familyMembers.find(m => m.id === id);
    if (!existing) return;

    const nextRelation = updates.relation ?? existing.relation;
    if (isPrimaryFamilyMember(existing) && nextRelation !== 'Self') {
      throw new Error('The primary member must remain Self. You can edit name, PAN and other details.');
    }
    if (!isPrimaryFamilyMember(existing) && nextRelation === 'Self' && familyMembers.some(m => m.id !== id && isPrimaryFamilyMember(m))) {
      throw new Error('There is already a primary member (Self).');
    }

    const rawPan = updates.panMasked ?? (updates as any).panCard ?? (updates as any).pan;
    const updated: FamilyMember = { ...existing, ...updates, relation: nextRelation };
    if (rawPan !== undefined) {
      updated.panMasked = rawPan ? maskPan(rawPan) : undefined;
    }
    if ('panForStatements' in updates) {
      updated.panForStatements = updates.panForStatements ? updates.panForStatements.trim().toUpperCase() : undefined;
    }
    if (updates.dateOfBirth !== undefined) {
      updated.dateOfBirth = updates.dateOfBirth.trim() || undefined;
    }
    updated.isPrimary = isPrimaryFamilyMember(updated);

    const panToCheck = updated.panForStatements || updated.panMasked || '';
    const dup = findDuplicatePanMember(familyMembers, panToCheck, id);
    if (dup) {
      throw new Error(`PAN already used by ${dup.name} (${dup.relation}). Each person needs their own PAN.`);
    }

    if (repo) {
      await repo.upsertEntity(updated);
    }
    setFamilyMembers(prev => prev.map(m => (m.id === id ? updated : m)));
  };

  const deleteFamilyMember = async (id: string): Promise<{ deletedHoldingsCount: number; deletedEventsCount: number }> => {
    const member = familyMembers.find(m => m.id === id);
    if (member && isPrimaryFamilyMember(member)) {
      throw new Error('The primary member (Self) cannot be deleted. You can still remove individual holdings.');
    }

    let deletedHoldingsCount = holdings.filter(h => h.ownerId === id).length;
    let deletedEventsCount = ledgerEvents.filter(e => e.ownerId === id).length;

    if (repo) {
      const result = await repo.deleteEntity(id);
      deletedHoldingsCount = result.deletedHoldingsCount;
      deletedEventsCount = result.deletedEventsCount;
    }

    setHoldings(prev => prev.filter(h => h.ownerId !== id));
    setLedgerEvents(prev => prev.filter(e => e.ownerId !== id));
    setFamilyMembers(prev => prev.filter(m => m.id !== id));

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

    const eventsByHolding = new Map<string, LedgerEvent[]>();
    for (const ev of filteredEvents) {
      const list = eventsByHolding.get(ev.holdingId) || [];
      list.push(ev);
      eventsByHolding.set(ev.holdingId, list);
    }

    const holdingMap = new Map<string, Holding>();
    for (const h of holdings) {
      holdingMap.set(h.id, h);
    }

    const allDisposals: ReturnType<typeof foldHolding>['disposals'] = [];
    const collectedErrors: string[] = [];

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

  const importStatementRecords = async (
    records: StatementImportRecord[],
    targetOwnerId?: string,
    mode: 'SYNC_UPDATE' | 'REPLACE_SNAPSHOT' = 'SYNC_UPDATE',
    casMeta?: CasImportMeta
  ): Promise<{ imported: number; updated: number; skipped: number; reconciliationResult?: ReconciliationResult }> => {
    let importedCount = 0;
    let updatedCount = 0;
    let skippedCount = 0;
    const effectiveOwnerId = targetOwnerId || (selectedMemberId === 'ALL' ? 'fam_1' : selectedMemberId);

    const selectedRecords = records.filter(r => r.selected);
    const skippedRecords = records.filter(r => !r.selected);
    skippedCount += skippedRecords.length;

    const importBatchId = `batch_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;

    // Python CAS is a holdings snapshot. Never treat rows as buys/sells.
    const isPythonCas = mode === 'REPLACE_SNAPSHOT' || Boolean(casMeta);
    const transactionRecords = isPythonCas
      ? []
      : selectedRecords.filter(r => r.recordType === 'TRANSACTION');
    const holdingRecords = selectedRecords.filter(r => r.recordType !== 'TRANSACTION');

    let currentWorkingHoldings = [...holdings];
    const newEventsToCommit: LedgerEvent[] = [];

    // --- Path A: TRANSACTION records (real events) ---
    for (const rec of transactionRecords) {
      let existingHolding = findMatchingHolding(currentWorkingHoldings, rec, effectiveOwnerId);
      const qtyScaled = MoneyEngine.fromQty(rec.quantity).toString();
      const priceMinor = MoneyEngine.fromRupees(rec.pricePerUnit).toString();
      const totalAmountMinor = MoneyEngine.fromRupees(rec.totalAmount).toString();

      let targetHoldingId = existingHolding?.id;
      let targetHoldingName = existingHolding?.name || rec.holdingName;

      if (!existingHolding) {
        const newId = `h_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
        const newHolding: Holding = {
          id: newId,
          name: rec.holdingName,
          symbolOrFolio: rec.folio || rec.isin || rec.holdingName.substring(0, 12).toUpperCase(),
          isin: rec.isin,
          category: rec.category,
          subCategory: casHoldingSubCategory(rec.category, rec.holdingName, rec.schemeType),
          ownerId: effectiveOwnerId,
          quantityScaled: '0',
          averageCostPerUnitMinor: priceMinor,
          currentPriceMinor: priceMinor,
          lastPriceUpdated: rec.date || new Date().toISOString(),
          isPriceStale: false,
          priceSource: 'Tradebook / Transaction Record',
          amc: rec.amc,
          rta: rec.rta,
          amfiCode: rec.amfiCode,
          advisor: rec.advisor,
          schemeType: rec.schemeType,
          ucc: rec.ucc,
          exchange: rec.exchange,
          dpName: rec.dpName,
          institutionName: rec.dpName,
          accountNumberMasked: rec.folio || rec.dematAccount,
          details: casImportDetails({ category: rec.category, casFileType: rec.casFileType }),
        };
        currentWorkingHoldings = [newHolding, ...currentWorkingHoldings];
        if (repo) await repo.upsertHolding(newHolding);
        targetHoldingId = newId;
        targetHoldingName = newHolding.name;
      }

      // Check duplicate
      const isDup = repo
        ? await repo.findDuplicate(effectiveOwnerId, targetHoldingId!, rec.date, qtyScaled, totalAmountMinor)
        : ledgerEvents.some(e => e.holdingId === targetHoldingId && e.eventDate === rec.date && e.quantityScaled === qtyScaled && e.eventType === rec.action);

      if (!isDup) {
        const ev: LedgerEvent = {
          id: `ev_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
          holdingId: targetHoldingId!,
          holdingName: targetHoldingName,
          ownerId: effectiveOwnerId,
          eventType: rec.action,
          eventDate: rec.date,
          quantityScaled: qtyScaled,
          unitPriceMinor: priceMinor,
          totalAmountMinor: totalAmountMinor,
          notes: `Imported from CAS statement (${rec.statusMessage || rec.action})`,
          createdAt: new Date().toISOString(),
          importBatchId,
          sourceRef: rec.isin || rec.folio,
        };
        newEventsToCommit.push(ev);
        importedCount++;
      } else {
        skippedCount++;
      }
    }

    // --- Path B: HOLDING records (snapshots -> reconcile) ---
    let reconciliationResult: ReconciliationResult | undefined;
    const statementDate = holdingRecords[0]?.date || new Date().toISOString().split('T')[0];

    if (holdingRecords.length > 0) {
      const snapshotLines: SnapshotLine[] = [];

      // Account rows MUST exist before holdings: holding.account_id is an FK.
      if (repo) {
        const seenAccounts = new Set<string>();
        for (const rec of holdingRecords) {
          const ident = rec.dematAccount || rec.folio;
          const acctId = casAccountId(rec);
          if (!ident || !acctId || seenAccounts.has(ident)) continue;
          seenAccounts.add(ident);
          await repo.upsertAccount({
            id: acctId,
            entityId: effectiveOwnerId,
            accountType: rec.folio && !rec.dematAccount ? 'MF_FOLIO' : 'DEMAT',
            custodian: rec.dpName || rec.amc || 'CAS',
            identifierMasked: ident,
            dpId: rec.dpId,
            clientId: rec.clientId,
            boId: rec.dematAccount,
          });
        }
        setAccounts(await repo.allAccounts());
      }

      for (const rec of holdingRecords) {
        let matched = findMatchingHolding(currentWorkingHoldings, rec, effectiveOwnerId);
        let holdingId = matched?.id;

        const priceMinor = MoneyEngine.fromRupees(rec.pricePerUnit).toString();
        const priceMinorBig = BigInt(priceMinor);
        const acctId = casAccountId(rec);

        if (!matched) {
          holdingId = `h_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
          const avgCostMinor = rec.cumulativeInvested && rec.quantity > 0
            ? String(Math.round((rec.cumulativeInvested / rec.quantity) * 1e4))
            : priceMinor;
          const newH: Holding = {
            id: holdingId,
            name: rec.holdingName,
            symbolOrFolio: rec.folio || rec.isin || rec.holdingName.substring(0, 12).toUpperCase(),
            isin: rec.isin,
            category: rec.category,
            subCategory: casHoldingSubCategory(rec.category, rec.holdingName, rec.schemeType),
            ownerId: effectiveOwnerId,
            quantityScaled: '0',
            averageCostPerUnitMinor: avgCostMinor,
            currentPriceMinor: priceMinor,
            lastPriceUpdated: rec.date || new Date().toISOString(),
            isPriceStale: false,
            priceSource: 'CAS Statement Snapshot',
            amc: rec.amc,
            rta: rec.rta,
            amfiCode: rec.amfiCode,
            advisor: rec.advisor,
            schemeType: rec.schemeType,
            ucc: rec.ucc,
            exchange: rec.exchange,
            dpName: rec.dpName,
            institutionName: rec.dpName,
            accountNumberMasked: rec.folio || rec.dematAccount,
            accountId: acctId,
            details: casImportDetails({ category: rec.category, casFileType: rec.casFileType }),
          };
          currentWorkingHoldings = [newH, ...currentWorkingHoldings];
          if (repo) await repo.upsertHolding(newH);
        } else {
          // Rule 6: Record price observation, but only update holding.currentPriceMinor if statement date is newer.
          // Name/ISIN upgrades are independent — a later CDSL row with a real scheme
          // name must replace a "MUTUAL FUND UNITS" placeholder even if NAV is older.
          const isEod = isEodPriceSource(matched.priceSource);
          const isNewer =
            !isEod &&
            (!matched.lastPriceUpdated || rec.date >= matched.lastPriceUpdated.split('T')[0]);
          const betterName = preferSchemeName(matched.name, rec.holdingName);
          const nameUpgrade = betterName !== matched.name;
          const isinUpgrade = Boolean(!matched.isin && rec.isin);
          const amfiUpgrade = Boolean(!matched.amfiCode && rec.amfiCode);
          const importedDetails = casImportDetails({ category: rec.category, casFileType: rec.casFileType });
          const categoryUpgrade =
            (rec.category === 'REAL_ESTATE' || rec.category === 'SPECIALISED') &&
            matched.category !== rec.category;
          const detailsUpgrade = Boolean(importedDetails && !matched.details?.altHoldMode);
          if (isNewer || nameUpgrade || isinUpgrade || amfiUpgrade || categoryUpgrade || detailsUpgrade) {
            const updatedH: Holding = {
              ...matched,
              name: betterName,
              category: categoryUpgrade ? rec.category : matched.category,
              subCategory: categoryUpgrade
                ? casHoldingSubCategory(rec.category, rec.holdingName, rec.schemeType)
                : matched.subCategory,
              currentPriceMinor: isNewer ? priceMinor : matched.currentPriceMinor,
              lastPriceUpdated: isNewer
                ? (rec.date ? `${rec.date}T00:00:00.000Z` : new Date().toISOString())
                : matched.lastPriceUpdated,
              priceSource: isNewer ? 'CAS Statement Snapshot' : matched.priceSource,
              accountId: matched.accountId || acctId,
              accountNumberMasked: matched.accountNumberMasked || rec.folio || rec.dematAccount,
              amc: matched.amc || rec.amc,
              rta: matched.rta || rec.rta,
              amfiCode: matched.amfiCode || rec.amfiCode,
              dpName: matched.dpName || rec.dpName,
              isin: matched.isin || rec.isin,
              details: importedDetails ? { ...matched.details, ...importedDetails } : matched.details,
            };
            if (repo) await repo.upsertHolding(updatedH);
            currentWorkingHoldings = currentWorkingHoldings.map(h => h.id === matched!.id ? updatedH : h);
          }
        }

        // Price observation in repository
        if (repo) {
          await repo.addPrice(holdingId!, rec.date || statementDate, 'STATEMENT_NAV', priceMinor, 'CAS Statement');
        }

        snapshotLines.push({
          holdingId: holdingId!,
          holdingName: rec.holdingName,
          category: rec.category,
          statementDate: rec.date || statementDate,
          quantityScaled: BigInt(Math.round(rec.quantity * 1e8)),
          unitPriceMinor: priceMinorBig,
          cumulativeInvestedMinor: rec.cumulativeInvested ? BigInt(Math.round(rec.cumulativeInvested * 1e4)) : undefined,
          isin: rec.isin,
          folio: rec.folio,
        });
      }

      // Same-issuer overwrite: close holdings that were on the previous
      // CDSL/NSDL/CAMS/KFin file but are missing from this one.
      if (isPythonCas && casMeta?.fileType) {
        const prevIds = Array.isArray(casMeta.closeHoldingIds)
            ? casMeta.closeHoldingIds
            : casLatest.byOwner[effectiveOwnerId]?.[casMeta.fileType]?.holdingIds || [];
        snapshotLines.push(
          ...snapshotLinesToClose(prevIds, snapshotLines, currentWorkingHoldings, statementDate, ledgerEvents)
        );
      }

      // Step 4: Call reconciliation
      reconciliationResult = reconcileStatement(snapshotLines, ledgerEvents, currentWorkingHoldings, effectiveOwnerId);

      // Step 5: Events to commit
      const acceptedIds = new Set(snapshotLines.map(l => l.holdingId));
      const reconciledEvents = eventsToCommit(reconciliationResult, acceptedIds)
        .map(e => ({ ...e, importBatchId, sourceRef: e.holdingId }));
      newEventsToCommit.push(...reconciledEvents);

      importedCount += reconciliationResult.counts.NEW_POSITION + reconciliationResult.counts.ADJUST_UP + reconciliationResult.counts.ADJUST_DOWN + reconciliationResult.counts.CLOSED + reconciliationResult.counts.COST_UPDATE;
      updatedCount += reconciliationResult.counts.MATCH;

      // Step 7: Record the batch and statement_position audit rows
      if (repo) {
        await repo.recordBatch({
          id: importBatchId,
          filename: casMeta?.filename || (holdingRecords[0]?.holdingName ? 'CAS_Statement_Import.pdf' : 'Statement.pdf'),
          kind: 'CAS_STATEMENT',
          statementDate: casMeta?.periodTo || statementDate,
          periodFrom: casMeta?.periodFrom || statementDate,
          periodTo: casMeta?.periodTo || statementDate,
          investorName: casMeta?.investorName,
          statementType: casMeta?.fileType || 'CAS_HOLDINGS_SNAPSHOT',
          rowsNew: reconciliationResult.counts.NEW_POSITION,
          rowsDuplicate: reconciliationResult.counts.MATCH,
          rowsConflict: reconciliationResult.counts.CONFLICT,
        });

        for (const line of snapshotLines) {
          const rec = holdingRecords.find(r => r.holdingName === line.holdingName && (r.isin === line.isin || r.folio === line.folio))
            || holdingRecords.find(r => r.holdingName === line.holdingName);
          await repo.recordStatementPosition({
            importBatchId,
            holdingId: line.holdingId,
            statementDate: line.statementDate,
            quantityScaled: line.quantityScaled.toString(),
            unitPriceMinor: line.unitPriceMinor.toString(),
            investedMinor: line.cumulativeInvestedMinor ? line.cumulativeInvestedMinor.toString() : undefined,
            isin: line.isin,
            folio: line.folio,
            currentValueMinor: rec?.currentValuation != null
              ? String(Math.round(rec.currentValuation * 1e4))
              : undefined,
            amc: rec?.amc,
            schemeType: rec?.schemeType,
            sourceSection: rec?.category,
          });
        }

        setImportBatches(await repo.allBatches());
        try {
          setStatementSnaps(await repo.statementSnapshotTotals());
          setStatementHoldingSnaps(await repo.latestHoldingStatementValues());
        } catch {
          setStatementSnaps([]);
          setStatementHoldingSnaps([]);
        }
      }
    }

    // Append newly generated events
    if (newEventsToCommit.length > 0) {
      if (repo) {
        await repo.appendEvents(newEventsToCommit);
      }
      setLedgerEvents(prev => [...newEventsToCommit, ...prev]);
    }

    const sanitized = sanitizeAndDeduplicateHoldings(currentWorkingHoldings);
    setHoldings(sanitized.holdings);

    if (casMeta?.fileType) {
      const persistedIds = holdingIdsForImportedRecords(holdingRecords, currentWorkingHoldings, effectiveOwnerId);
      const next = upsertCasLatest(casLatest, effectiveOwnerId, {
        ...casMeta,
        closeHoldingIds: undefined,
        holdingIds: persistedIds,
        importedAt: new Date().toISOString(),
      });
      setCasLatest(next);
      if (repo) {
        await repo.setMeta('cas_latest', JSON.stringify(next));
      }
    }

    return { imported: importedCount, updated: updatedCount, skipped: skippedCount, reconciliationResult };
  };

  const cleanDuplicatesAndHeal = async (): Promise<{ mergedCount: number; flaggedCount: number }> => {
    const result = sanitizeAndDeduplicateHoldings(holdings);
    if (repo) {
      for (const h of result.holdings) {
        await repo.upsertHolding(h);
      }
    }
    setHoldings(result.holdings);
    return { mergedCount: result.mergedCount, flaggedCount: result.flaggedCount };
  };

  const confirmVaultPassword = async (email: string, password: string): Promise<string | null> => {
    if (!repo) return 'Vault is not ready.';
    const stored = await repo.getVaultAccount();
    if (!stored) return 'No account on this device.';
    if (email.trim().toLowerCase() !== stored.email.toLowerCase()) {
      return 'Email or password is wrong.';
    }
    const ok = await verifySecret(password, stored.passwordSalt, stored.passwordHash, stored.passwordIterations);
    if (!ok) return 'Email or password is wrong.';
    return null;
  };

  const createLockedBackup = async (email: string, password: string): Promise<Uint8Array> => {
    if (!repo) throw new BackupError('Vault is not ready.');
    const stored = await repo.getVaultAccount();
    if (!stored) throw new BackupError('Create your vault before making a backup.');
    const mismatch = await confirmVaultPassword(email, password);
    if (mismatch) throw new BackupError(mismatch);
    const payload: VaultBackup = {
      kind: BACKUP_KIND,
      version: BACKUP_VERSION,
      exportedAt: new Date().toISOString(),
      account: stored,
      familyMembers,
      holdings,
      ledgerEvents,
      accounts,
      goals,
      casLatest,
    };
    return packBackup(payload, stored.email, password);
  };

  const hydrateFromRepo = async (r: Repository) => {
    setFamilyMembers(await r.allEntities());
    setHoldings(await r.allHoldings());
    setLedgerEvents(await r.allEvents());
    setAccounts(await r.allAccounts());
    setGoals(await r.allGoals());
    setImportBatches(await r.allBatches());
    try {
      const raw = await r.getMeta('cas_latest');
      setCasLatest(parseCasLatest(raw));
    } catch {
      setCasLatest(EMPTY_CAS_LATEST);
    }
    try {
      setStatementSnaps(await r.statementSnapshotTotals());
      setStatementHoldingSnaps(await r.latestHoldingStatementValues());
    } catch {
      setStatementSnaps([]);
      setStatementHoldingSnaps([]);
    }
  };

  const restoreLockedBackup = async (bytes: Uint8Array, identity: RestoreIdentity): Promise<string | null> => {
    if (!repo) return 'Vault is not ready.';
    let backup: VaultBackup;
    try {
      backup = await unpackBackup(bytes, identity.email, identity.password);
    } catch (e) {
      return e instanceof Error ? e.message : 'Could not open that backup.';
    }
    const idErr = await verifyRestoreIdentity(backup, identity);
    if (idErr) return idErr;
    try {
      await repo.wipeVaultCompletely();
      await repo.saveVaultAccount(backup.account);
      for (const m of backup.familyMembers) await repo.upsertEntity(m);
      for (const a of backup.accounts || []) {
        await repo.upsertAccount({
          id: a.id,
          entityId: a.ownerId,
          accountType: a.accountType,
          custodian: a.custodian,
          identifierMasked: a.identifierMasked,
          dpId: a.dpId,
          clientId: a.clientId,
          boId: a.boId,
          nominee: a.nominee,
        });
      }
      for (const h of backup.holdings) await repo.upsertHolding(h);
      if (backup.ledgerEvents.length) await repo.appendEvents(backup.ledgerEvents);
      for (const g of backup.goals || []) {
        await repo.upsertGoal(g);
      }
      if (backup.casLatest) {
        await repo.setMeta('cas_latest', JSON.stringify(backup.casLatest));
      }
      await hydrateFromRepo(repo);
      setAccount(toPublicAccount(backup.account));
      writeRememberedEmail(backup.account.email);
      setUnlocked(true);
      setMustRotatePassword(needsPasswordRotation(backup.account.passwordChangedAt));
      return null;
    } catch (e) {
      return e instanceof Error ? e.message : 'Could not restore that backup.';
    }
  };

  const registerOwner = async (input: {
    firstName: string;
    lastName: string;
    email: string;
    password: string;
    pan: string;
    dateOfBirth: string;
    q1Id: string;
    q1Answer: string;
    q2Id: string;
    q2Answer: string;
  }): Promise<string | null> => {
    if (!repo) return 'Vault is not ready.';
    const firstName = titleCaseName(input.firstName);
    const lastName = titleCaseName(input.lastName);
    const email = input.email.trim().toLowerCase();
    const pan = input.pan.trim().toUpperCase();
    if (!firstName || !lastName) return 'Enter first and last name.';
    if (!isValidEmail(email)) return 'Enter a valid email.';
    const issues = passwordIssues(input.password);
    if (issues.length) return `Password: ${issues.join(', ')}.`;
    if (!isValidPan(pan)) return 'PAN must look like ABCDE1234F.';
    if (!input.dateOfBirth) return 'Enter date of birth.';
    if (input.q1Id === input.q2Id) return 'Pick two different questions.';
    const pw = await hashSecret(input.password);
    const a1 = await hashSecret(normaliseAnswer(input.q1Answer));
    const a2 = await hashSecret(normaliseAnswer(input.q2Answer));
    const now = new Date().toISOString();
    const stored = {
      email,
      firstName,
      lastName,
      passwordSalt: pw.salt,
      passwordHash: pw.hash,
      passwordIterations: pw.iterations,
      passwordChangedAt: now,
      panMasked: maskPan(pan),
      panForStatements: pan,
      dateOfBirth: input.dateOfBirth,
      q1Id: input.q1Id,
      q1Hash: packHash(a1),
      q2Id: input.q2Id,
      q2Hash: packHash(a2),
      createdAt: now,
    };
    await repo.saveVaultAccount(stored);
    const entities = await repo.allEntities();
    const self = entities.find(e => isPrimaryFamilyMember(e)) || { ...EMPTY_SELF };
    await repo.upsertEntity({
      ...self,
      name: `${firstName} ${lastName}`.trim(),
      relation: 'Self',
      isPrimary: true,
      panMasked: maskPan(pan),
      panForStatements: pan,
      dateOfBirth: input.dateOfBirth,
    });
    setFamilyMembers(await repo.allEntities());
    setAccount(toPublicAccount(stored));
    writeRememberedEmail(email);
    setUnlocked(true);
    setMustRotatePassword(false);
    return null;
  };

  const login = async (email: string, password: string): Promise<string | null> => {
    if (!repo) return 'Vault is not ready.';
    const stored = await repo.getVaultAccount();
    if (!stored) return 'No account on this device. Create one first.';
    if (email.trim().toLowerCase() !== stored.email.toLowerCase()) {
      return 'Email or password is wrong.';
    }
    const ok = await verifySecret(password, stored.passwordSalt, stored.passwordHash, stored.passwordIterations);
    if (!ok) return 'Email or password is wrong.';
    writeRememberedEmail(stored.email);
    setAccount(toPublicAccount(stored));
    setUnlocked(true);
    setMustRotatePassword(needsPasswordRotation(stored.passwordChangedAt));
    return null;
  };

  const lockVault = () => {
    setUnlocked(false);
  };

  const changePassword = async (oldPassword: string, newPassword: string): Promise<string | null> => {
    if (!repo) return 'Vault is not ready.';
    const stored = await repo.getVaultAccount();
    if (!stored) return 'No account.';
    const ok = await verifySecret(oldPassword, stored.passwordSalt, stored.passwordHash, stored.passwordIterations);
    if (!ok) return 'Current password is wrong.';
    const issues = passwordIssues(newPassword);
    if (issues.length) return `Password: ${issues.join(', ')}.`;
    if (oldPassword === newPassword) return 'Pick a different new password.';
    const pw = await hashSecret(newPassword);
    const next = {
      ...stored,
      passwordSalt: pw.salt,
      passwordHash: pw.hash,
      passwordIterations: pw.iterations,
      passwordChangedAt: new Date().toISOString(),
    };
    await repo.saveVaultAccount(next);
    setAccount(toPublicAccount(next));
    setMustRotatePassword(false);
    setUnlocked(true);
    return null;
  };

  const resetWithQuestions = async (
    email: string,
    a1: string,
    a2: string,
    newPassword: string,
  ): Promise<string | null> => {
    if (!repo) return 'Vault is not ready.';
    const stored = await repo.getVaultAccount();
    if (!stored) return 'No account on this device.';
    if (email.trim().toLowerCase() !== stored.email.toLowerCase()) {
      return 'Those answers did not match.';
    }
    const p1 = unpackHash(stored.q1Hash);
    const p2 = unpackHash(stored.q2Hash);
    if (!p1 || !p2) return 'Security questions are missing. Start over from Family.';
    const ok1 = await verifySecret(normaliseAnswer(a1), p1.salt, p1.hash, p1.iterations);
    const ok2 = await verifySecret(normaliseAnswer(a2), p2.salt, p2.hash, p2.iterations);
    if (!ok1 || !ok2) return 'Those answers did not match.';
    const issues = passwordIssues(newPassword);
    if (issues.length) return `Password: ${issues.join(', ')}.`;
    const pw = await hashSecret(newPassword);
    const next = {
      ...stored,
      passwordSalt: pw.salt,
      passwordHash: pw.hash,
      passwordIterations: pw.iterations,
      passwordChangedAt: new Date().toISOString(),
    };
    await repo.saveVaultAccount(next);
    setAccount(toPublicAccount(next));
    setMustRotatePassword(false);
    setUnlocked(true);
    writeRememberedEmail(stored.email);
    return null;
  };

  const startOver = async (): Promise<void> => {
    if (repo) {
      await repo.wipeVaultKeepSelf();
      await repo.deleteVaultAccount();
      const remaining = await repo.allEntities();
      if (!remaining.some(e => isPrimaryFamilyMember(e))) {
        await repo.upsertEntity(EMPTY_SELF);
      } else {
        const self = remaining.find(e => isPrimaryFamilyMember(e))!;
        await repo.upsertEntity({ ...self, name: 'You', panMasked: undefined, panForStatements: undefined, dateOfBirth: undefined });
      }
      await repo.setMeta('cas_latest', JSON.stringify(EMPTY_CAS_LATEST));
      setFamilyMembers(await repo.allEntities());
      setHoldings(await repo.allHoldings());
      setLedgerEvents(await repo.allEvents());
      setAccounts(await repo.allAccounts());
      setGoals(await repo.allGoals());
      setImportBatches(await repo.allBatches());
      try {
        setStatementSnaps(await repo.statementSnapshotTotals());
        setStatementHoldingSnaps(await repo.latestHoldingStatementValues());
      } catch {
        setStatementSnaps([]);
        setStatementHoldingSnaps([]);
      }
    } else {
      setFamilyMembers([EMPTY_SELF]);
      setHoldings([]);
      setLedgerEvents([]);
      setAccounts([]);
      setGoals([]);
      setImportBatches([]);
      setStatementSnaps([]);
      setStatementHoldingSnaps([]);
    }
    setCasLatest(EMPTY_CAS_LATEST);
    setSelectedMemberId('ALL');
    setAccount(null);
    setUnlocked(false);
    setMustRotatePassword(false);
    clearRememberedEmail();
  };

  const deleteAccountAndData = async (): Promise<void> => {
    if (repo) {
      await repo.wipeVaultCompletely();
    }
    resetOnboarding();
    setFamilyMembers([]);
    setHoldings([]);
    setLedgerEvents([]);
    setAccounts([]);
    setGoals([]);
    setImportBatches([]);
    setStatementSnaps([]);
    setStatementHoldingSnaps([]);
    setCasLatest(EMPTY_CAS_LATEST);
    setAccount(null);
    setUnlocked(false);
    setMustRotatePassword(false);
    clearRememberedEmail();
    window.location.reload();
  };


  const upsertGoal = async (
    goal: Omit<FamilyGoal, 'id' | 'createdAt'> & { id?: string }
  ): Promise<FamilyGoal> => {
    const row: FamilyGoal = {
      id: goal.id || `goal_${Date.now().toString(36)}`,
      ownerId: goal.ownerId,
      name: goal.name.trim(),
      targetMinor: goal.targetMinor || '0',
      targetDate: goal.targetDate,
      priority: goal.priority,
      createdAt: new Date().toISOString(),
    };
    if (repo) await repo.upsertGoal(row);
    setGoals(prev => {
      const i = prev.findIndex(g => g.id === row.id);
      if (i >= 0) {
        const next = [...prev];
        next[i] = row;
        return next;
      }
      return [...prev, row];
    });
    return row;
  };

  const deleteGoal = async (id: string): Promise<void> => {
    if (repo) await repo.deleteGoal(id);
    setGoals(prev => prev.filter(g => g.id !== id));
  };

  const updateAccountNominee = async (accountId: string, nominee: string): Promise<void> => {
    if (repo) await repo.updateAccountNominee(accountId, nominee);
    setAccounts(prev => prev.map(a => (a.id === accountId ? { ...a, nominee } : a)));
  };

  const refreshEodPrices = async (): Promise<void> => {
    setPricesRefreshing(true);
    setPricesError(null);
    try {
      const result = await refreshEodFromSidecar();
      if (!result.ok) {
        setPricesError(result.error || 'Could not refresh prices.');
        return;
      }
      const listed = (result.catalog || [])
        .map(catalogToListed)
        .filter((row): row is ListedSecurity => !!row);
      if (repo) {
        await repo.replaceListedSecurities(listed);
        if (result.asOf) await repo.setMeta('prices_as_of', result.asOf);
      }
      setListedSecurities(listed);
      setPricesAsOf(result.asOf || null);
      const applied = applyCatalogPrices(holdings, listed);
      if (applied.updated > 0) {
        if (repo) {
          for (const h of applied.holdings) {
            if (h.currentPriceMinor !== holdings.find(x => x.id === h.id)?.currentPriceMinor
              || h.priceSource !== holdings.find(x => x.id === h.id)?.priceSource) {
              await repo.upsertHolding(h);
              if (h.isin) {
                await repo.addPrice(h.id, h.lastPriceUpdated.slice(0, 10), 'EOD_CLOSE', h.currentPriceMinor, h.priceSource);
              }
            }
          }
        }
        setHoldings(applied.holdings);
      }
      if (result.warnings && result.warnings.length) {
        setPricesError(result.warnings.join(' '));
      }
    } catch (err: any) {
      setPricesError(err?.message || 'Could not refresh prices.');
    } finally {
      setPricesRefreshing(false);
    }
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
        accounts,
        goals,
        importBatches,
        statementSnaps,
        statementHoldingSnaps,
        isPrivacyMasked,
        setIsPrivacyMasked,
        sgbValuationMode,
        setSgbValuationMode,
        theme,
        setTheme,
        summary,
        activeScreen,
        setActiveScreen,
        repo,
        dbReady,
        dbError,
        migration,
        addHolding,
        updateHolding,
        deleteHolding,
        appendLedgerEvent,
        addLedgerEvent,
        correctEvent,
        correctInvested,
        addFamilyMember,
        updateFamilyMember,
        deleteFamilyMember,
        computeTaxSummary,
        importStatementRecords,
        lastStatementAsOf:
          selectedMemberId === 'ALL'
            ? casLatest.asOf
            : latestAsOfForOwner(casLatest, selectedMemberId),
        statementAsOfByMember: statementAsOfByMember(casLatest, familyMembers),
        casLatest,
        cleanDuplicatesAndHeal,
        confirmVaultPassword,
        createLockedBackup,
        restoreLockedBackup,
        startOver,
        deleteAccountAndData,
        updateHoldingPrice,
        upsertGoal,
        deleteGoal,
        updateAccountNominee,
        listedSecurities,
        pricesAsOf,
        pricesRefreshing,
        pricesError,
        refreshEodPrices,
        account,
        unlocked,
        mustRotatePassword,
        registerOwner,
        login,
        lockVault,
        changePassword,
        resetWithQuestions,
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
