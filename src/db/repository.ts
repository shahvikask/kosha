/**
 * Typed data access over the SqlDriver. Platform-neutral.
 *
 * Rules enforced here:
 *  - Money and quantity cross the boundary as strings, converted to
 *    INTEGER columns. Never as JavaScript numbers.
 *  - The ledger is append-only. There is no update or delete method,
 *    by design — except a permitted cascade when a non-primary family
 *    member is removed (holdings + that member's events).
 *  - The primary (Self) entity cannot be deleted.
 */
import { SqlDriver, SqlValue } from './driver';
import {
  SCHEMA_V1,
  SCHEMA_VERSION,
  SCHEMA_V3_COLUMNS,
  LEDGER_NO_DELETE_TRIGGER,
} from './schema';
import { LedgerEvent, Holding, FamilyMember, FamilyRelation, isPrimaryFamilyMember, DematOrFolioAccount, FamilyGoal, ImportBatchInfo } from '../types';

function toBps(percent: number | undefined | null): number | null {
  if (percent === undefined || percent === null || Number.isNaN(percent)) return null;
  return Math.round(percent * 100);
}

function fromBps(v: SqlValue): number | undefined {
  if (v === null || v === undefined || v === '') return undefined;
  return Number(v) / 100;
}

export class Repository {
  constructor(private db: SqlDriver) {}

  async initialise(): Promise<void> {
    await this.db.exec(SCHEMA_V1);
    const rows = await this.db.query<{ value: string }>(
      `SELECT value FROM vault_meta WHERE key = 'schema_version'`);
    const current = rows.length ? Number(rows[0].value) : 0;

    if (current < 3) {
      await this.applyV3Columns();
    }

    if (rows.length === 0) {
      await this.db.exec(
        `INSERT INTO vault_meta (key, value, updated_at) VALUES (?, ?, ?)`,
        ['schema_version', String(SCHEMA_VERSION), new Date().toISOString()]);
    } else if (current < SCHEMA_VERSION) {
      await this.setMeta('schema_version', String(SCHEMA_VERSION));
    }
  }

  private async applyV3Columns(): Promise<void> {
    for (const [table, column, ddl] of SCHEMA_V3_COLUMNS) {
      const cols = await this.db.query<{ name: string }>(`PRAGMA table_info(${table})`);
      if (cols.some(c => c.name === column)) continue;
      await this.db.exec(`ALTER TABLE ${table} ADD COLUMN ${ddl}`);
    }
  }

  async schemaVersion(): Promise<number> {
    const r = await this.db.query<{ value: string }>(
      `SELECT value FROM vault_meta WHERE key = 'schema_version'`);
    return r.length ? Number(r[0].value) : 0;
  }

  async getMeta(key: string): Promise<string | null> {
    const r = await this.db.query<{ value: string }>(
      `SELECT value FROM vault_meta WHERE key = ?`, [key]);
    return r.length ? r[0].value : null;
  }

  async setMeta(key: string, value: string): Promise<void> {
    await this.db.exec(
      `INSERT INTO vault_meta (key, value, updated_at) VALUES (?,?,?)
       ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
      [key, value, new Date().toISOString()]);
  }

  // ---------- ledger: append only ----------

  async appendEvent(e: LedgerEvent): Promise<void> {
    await this.db.exec(
      `INSERT INTO ledger_event (
         ledger_event_id, entity_id, holding_id, holding_name, event_type,
         trade_date, settlement_date, quantity_scaled, unit_price_minor,
         total_amount_minor, charges_minor, currency, supersedes_event_id,
         import_batch_id, source_ref, notes, entered_at, entered_by)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [e.id, e.ownerId, e.holdingId, e.holdingName, e.eventType,
       e.eventDate, null,
       e.quantityScaled ?? '0', e.unitPriceMinor ?? '0',
       e.totalAmountMinor ?? '0', e.chargesMinor ?? '0',
       'INR', e.supersedesEventId ?? null,
       e.importBatchId ?? null, e.sourceRef ?? null,
       e.notes ?? null, e.createdAt, 'owner'] as SqlValue[]);
  }

  async appendEvents(events: LedgerEvent[]): Promise<void> {
    await this.db.transaction(async () => {
      for (const e of events) await this.appendEvent(e);
    });
  }

  async allEvents(): Promise<LedgerEvent[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT e.* FROM ledger_event e
       WHERE EXISTS (
         SELECT 1 FROM entity x
         WHERE x.entity_id = e.entity_id AND x.is_active = 1
       )
       ORDER BY trade_date DESC, ledger_event_id DESC`);
    return rows.map(rowToEvent);
  }

  async eventsForHolding(holdingId: string): Promise<LedgerEvent[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT * FROM ledger_event WHERE holding_id = ? ORDER BY trade_date, ledger_event_id`,
      [holdingId]);
    return rows.map(rowToEvent);
  }

  /** Duplicate detection (SRS-IMP-009), done in SQL against the index. */
  async findDuplicate(
    entityId: string, holdingId: string, tradeDate: string,
    quantityScaled: string, totalAmountMinor: string
  ): Promise<boolean> {
    const r = await this.db.query<{ n: number }>(
      `SELECT COUNT(*) AS n FROM ledger_event
       WHERE entity_id = ? AND holding_id = ? AND trade_date = ?
         AND quantity_scaled = ? AND ABS(total_amount_minor - ?) <= 100`,
      [entityId, holdingId, tradeDate, quantityScaled, totalAmountMinor]);
    return Number(r[0]?.n ?? 0) > 0;
  }

  // ---------- holdings: reference data only ----------

  async upsertHolding(h: Holding): Promise<void> {
    await this.db.exec(
      `INSERT INTO instrument (
         instrument_id, name, category, sub_category, symbol_or_folio, isin,
         exposure_node, l2_node, wrapper, allocation_split,
         fmv_31jan2018_minor, amc, rta, amfi_code, advisor,
         scheme_type, ucc, exchange, dp_name, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
       ON CONFLICT(instrument_id) DO UPDATE SET
         name=excluded.name, category=excluded.category,
         sub_category=excluded.sub_category, symbol_or_folio=excluded.symbol_or_folio,
         isin=excluded.isin, exposure_node=excluded.exposure_node,
         l2_node=excluded.l2_node, wrapper=excluded.wrapper,
         allocation_split=excluded.allocation_split,
         fmv_31jan2018_minor=excluded.fmv_31jan2018_minor,
         amc=COALESCE(excluded.amc, instrument.amc),
         rta=COALESCE(excluded.rta, instrument.rta),
         amfi_code=COALESCE(excluded.amfi_code, instrument.amfi_code),
         advisor=COALESCE(excluded.advisor, instrument.advisor),
         scheme_type=COALESCE(excluded.scheme_type, instrument.scheme_type),
         ucc=COALESCE(excluded.ucc, instrument.ucc),
         exchange=COALESCE(excluded.exchange, instrument.exchange),
         dp_name=COALESCE(excluded.dp_name, instrument.dp_name)`,
      [h.id, h.name, h.category, h.subCategory ?? '', h.symbolOrFolio ?? null,
       h.isin ?? null, h.exposureNode ?? null, h.l2Node ?? null, h.wrapper ?? null,
       h.allocationSplit ? JSON.stringify(h.allocationSplit) : null,
       h.fmv31Jan2018Minor ?? null,
       h.amc ?? null, h.rta ?? null, h.amfiCode ?? null, h.advisor ?? null,
       h.schemeType ?? null, h.ucc ?? null, h.exchange ?? null, h.dpName ?? null,
       new Date().toISOString()] as SqlValue[]);

    await this.db.exec(
      `INSERT INTO holding (
         holding_id, instrument_id, entity_id, account_id, current_price_minor,
         benchmark_price_minor, issue_price_minor, sgb_valuation_mode, last_price_updated,
         price_source, needs_review, review_reasons, institution_name, account_number_masked,
         notes, location, tag, tags_json, coupon_rate_bps, interest_rate_bps,
         maturity_date, next_coupon_date, monthly_emi_minor, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
       ON CONFLICT(holding_id) DO UPDATE SET
         account_id=COALESCE(excluded.account_id, holding.account_id),
         current_price_minor=excluded.current_price_minor,
         benchmark_price_minor=excluded.benchmark_price_minor,
         issue_price_minor=COALESCE(excluded.issue_price_minor, holding.issue_price_minor),
         sgb_valuation_mode=excluded.sgb_valuation_mode,
         last_price_updated=excluded.last_price_updated,
         price_source=excluded.price_source,
         needs_review=excluded.needs_review,
         review_reasons=excluded.review_reasons,
         institution_name=COALESCE(excluded.institution_name, holding.institution_name),
         account_number_masked=COALESCE(excluded.account_number_masked, holding.account_number_masked),
         notes=COALESCE(excluded.notes, holding.notes),
         location=COALESCE(excluded.location, holding.location),
         tag=COALESCE(excluded.tag, holding.tag),
         tags_json=COALESCE(excluded.tags_json, holding.tags_json),
         coupon_rate_bps=COALESCE(excluded.coupon_rate_bps, holding.coupon_rate_bps),
         interest_rate_bps=COALESCE(excluded.interest_rate_bps, holding.interest_rate_bps),
         maturity_date=COALESCE(excluded.maturity_date, holding.maturity_date),
         next_coupon_date=COALESCE(excluded.next_coupon_date, holding.next_coupon_date),
         monthly_emi_minor=COALESCE(excluded.monthly_emi_minor, holding.monthly_emi_minor)`,
      [h.id, h.id, h.ownerId, h.accountId ?? null, h.currentPriceMinor ?? '0',
       h.benchmarkPriceMinor ?? null, h.issuePriceMinor ?? null, null,
       h.lastPriceUpdated ?? null, h.priceSource ?? null,
       h.needsReview ? 1 : 0,
       h.reviewReasons ? JSON.stringify(h.reviewReasons) : null,
       h.institutionName ?? null, h.accountNumberMasked ?? null,
       h.notes ?? null, h.location ?? null, h.tag ?? null,
       h.tags ? JSON.stringify(h.tags) : null,
       toBps(h.couponRatePercent), toBps(h.interestRatePercent),
       h.maturityDate ?? null, h.nextCouponDate ?? null,
       h.monthlyEmiMinor ?? null,
       new Date().toISOString()] as SqlValue[]);
  }

  async allHoldings(): Promise<Holding[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT h.holding_id, h.entity_id, h.account_id, h.current_price_minor,
              h.benchmark_price_minor, h.issue_price_minor, h.sgb_valuation_mode,
              h.last_price_updated, h.price_source, h.needs_review, h.review_reasons,
              h.institution_name, h.account_number_masked, h.notes AS holding_notes,
              h.location, h.tag, h.tags_json, h.coupon_rate_bps, h.interest_rate_bps,
              h.maturity_date, h.next_coupon_date, h.monthly_emi_minor,
              i.name, i.category, i.sub_category, i.symbol_or_folio, i.isin,
              i.exposure_node, i.l2_node, i.wrapper, i.allocation_split,
              i.fmv_31jan2018_minor, i.amc, i.rta, i.amfi_code, i.advisor,
              i.scheme_type, i.ucc, i.exchange, i.dp_name
       FROM holding h
       JOIN instrument i ON i.instrument_id = h.instrument_id
       JOIN entity e ON e.entity_id = h.entity_id
       WHERE e.is_active = 1`);
    return rows.map(rowToHolding);
  }

  /** Removes the holding row. Its events remain (SRS-LED-001). */
  async deleteHolding(holdingId: string): Promise<void> {
    await this.db.exec(`DELETE FROM holding WHERE holding_id = ?`, [holdingId]);
  }

  // ---------- accounts (demat / folio) ----------

  async upsertAccount(a: {
    id: string;
    entityId: string;
    accountType: string;
    custodian: string;
    identifierMasked?: string;
    dpId?: string;
    clientId?: string;
    boId?: string;
    nominee?: string;
  }): Promise<void> {
    await this.db.exec(
      `INSERT INTO account (
         account_id, entity_id, account_type, custodian, identifier_masked,
         dp_id, client_id, bo_id, nominee, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?)
       ON CONFLICT(account_id) DO UPDATE SET
         custodian=excluded.custodian,
         identifier_masked=COALESCE(excluded.identifier_masked, account.identifier_masked),
         dp_id=COALESCE(excluded.dp_id, account.dp_id),
         client_id=COALESCE(excluded.client_id, account.client_id),
         bo_id=COALESCE(excluded.bo_id, account.bo_id),
         nominee=COALESCE(excluded.nominee, account.nominee)`,
      [a.id, a.entityId, a.accountType, a.custodian,
       a.identifierMasked ?? null, a.dpId ?? null, a.clientId ?? null,
       a.boId ?? null, a.nominee ?? null, new Date().toISOString()] as SqlValue[]);
  }

  async allAccounts(): Promise<DematOrFolioAccount[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT account_id, entity_id, account_type, custodian, identifier_masked,
              dp_id, client_id, bo_id, nominee
       FROM account ORDER BY custodian, identifier_masked`);
    return rows.map(rowToAccount);
  }

  async updateAccountNominee(accountId: string, nominee: string): Promise<void> {
    await this.db.exec(`UPDATE account SET nominee = ? WHERE account_id = ?`, [nominee, accountId]);
  }

  async allGoals(): Promise<FamilyGoal[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT goal_id, entity_id, name, target_minor, target_date, priority, created_at
       FROM goal ORDER BY created_at ASC`);
    return rows.map(rowToGoal);
  }

  async upsertGoal(g: FamilyGoal): Promise<void> {
    await this.db.exec(
      `INSERT INTO goal (goal_id, entity_id, name, target_minor, target_date, priority, created_at)
       VALUES (?,?,?,?,?,?,?)
       ON CONFLICT(goal_id) DO UPDATE SET
         entity_id=excluded.entity_id,
         name=excluded.name,
         target_minor=excluded.target_minor,
         target_date=excluded.target_date,
         priority=excluded.priority`,
      [g.id, g.ownerId ?? null, g.name, g.targetMinor, g.targetDate ?? null,
       g.priority ?? null, g.createdAt]);
  }

  async deleteGoal(goalId: string): Promise<void> {
    await this.db.exec(`DELETE FROM goal WHERE goal_id = ?`, [goalId]);
  }

  async allBatches(): Promise<ImportBatchInfo[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT import_batch_id, source_filename, document_kind, statement_date,
              period_from, period_to, investor_name, statement_type,
              rows_new, rows_duplicate, committed_at
       FROM import_batch ORDER BY committed_at DESC LIMIT 50`);
    return rows.map(rowToBatch);
  }

  /**
   * Empty the vault but keep the primary Self entity. Used by "Start over".
   * Temporarily drops the append-only trigger so ledger rows can be cleared.
   */
  async wipeVaultKeepSelf(): Promise<void> {
    await this.db.transaction(async () => {
      await this.db.exec(`DROP TRIGGER IF EXISTS tg_ledger_event__no_delete`);
      await this.db.exec(`DELETE FROM statement_position`);
      await this.db.exec(`DELETE FROM holding`);
      await this.db.exec(`DELETE FROM account`);
      await this.db.exec(`DELETE FROM liability`);
      await this.db.exec(`DELETE FROM goal`);
      await this.db.exec(`DELETE FROM import_batch`);
      await this.db.exec(`DELETE FROM ledger_event`);
      try {
        await this.db.exec(`DELETE FROM price_observation`);
      } catch {
        /* table may not exist on very old vaults */
      }
      await this.db.exec(
        `DELETE FROM entity WHERE is_primary = 0 AND relation != 'Self'`
      );
      await this.db.exec(LEDGER_NO_DELETE_TRIGGER);
    });
  }

  // ---------- entities ----------

  async upsertEntity(m: FamilyMember): Promise<void> {
    const isPrimary = isPrimaryFamilyMember(m) ? 1 : 0;
    const entityType = m.relation === 'HUF' ? 'HUF' : 'INDIVIDUAL';
    await this.db.exec(
      `INSERT INTO entity (
         entity_id, entity_type, display_name, pan_masked, pan_for_statements,
         date_of_birth, relation, color, notes, is_primary, is_active, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,1,?)
       ON CONFLICT(entity_id) DO UPDATE SET
         entity_type=excluded.entity_type,
         display_name=excluded.display_name,
         pan_masked=excluded.pan_masked,
         pan_for_statements=excluded.pan_for_statements,
         date_of_birth=excluded.date_of_birth,
         relation=excluded.relation,
         color=excluded.color,
         notes=excluded.notes,
         is_primary=excluded.is_primary,
         is_active=1`,
      [m.id, entityType, m.name, m.panMasked ?? null, m.panForStatements ?? null,
       m.dateOfBirth ?? null, m.relation, m.color ?? null, m.notes ?? null,
       isPrimary, new Date().toISOString()] as SqlValue[]);
  }

  async allEntities(): Promise<FamilyMember[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT entity_id, entity_type, display_name, pan_masked, pan_for_statements,
              date_of_birth, relation, color, notes, is_primary, is_active
       FROM entity WHERE is_active = 1
       ORDER BY is_primary DESC, created_at ASC`);
    return rows.map(rowToEntity);
  }

  /**
   * Remove a non-primary family member and every associated row:
   * holdings, accounts, liabilities, goals, statement positions, and
   * that member's ledger events. The primary (Self) member is refused.
   */
  async deleteEntity(entityId: string): Promise<{ deletedHoldingsCount: number; deletedEventsCount: number }> {
    const members = await this.db.query<Record<string, SqlValue>>(
      `SELECT entity_id, relation, is_primary FROM entity WHERE entity_id = ?`, [entityId]);
    if (!members.length) {
      return { deletedHoldingsCount: 0, deletedEventsCount: 0 };
    }
    const target = rowToEntity(members[0]);
    if (isPrimaryFamilyMember(target)) {
      throw new Error(
        'The primary member (Self) cannot be deleted. You can still remove individual holdings.'
      );
    }

    const holdCount = await this.db.query<{ n: number }>(
      `SELECT COUNT(*) AS n FROM holding WHERE entity_id = ?`, [entityId]);
    const eventCount = await this.db.query<{ n: number }>(
      `SELECT COUNT(*) AS n FROM ledger_event WHERE entity_id = ?`, [entityId]);
    const deletedHoldingsCount = Number(holdCount[0]?.n ?? 0);
    const deletedEventsCount = Number(eventCount[0]?.n ?? 0);

    await this.db.transaction(async () => {
      await this.db.exec(
        `DELETE FROM statement_position WHERE holding_id IN
         (SELECT holding_id FROM holding WHERE entity_id = ?)`, [entityId]);
      await this.db.exec(`DELETE FROM holding WHERE entity_id = ?`, [entityId]);
      await this.db.exec(`DELETE FROM account WHERE entity_id = ?`, [entityId]);
      await this.db.exec(`DELETE FROM liability WHERE entity_id = ?`, [entityId]);
      await this.db.exec(`DELETE FROM goal WHERE entity_id = ?`, [entityId]);
      await this.db.exec(`DROP TRIGGER IF EXISTS tg_ledger_event__no_delete`);
      await this.db.exec(`DELETE FROM ledger_event WHERE entity_id = ?`, [entityId]);
      await this.db.exec(LEDGER_NO_DELETE_TRIGGER);
      await this.db.exec(`DELETE FROM entity WHERE entity_id = ?`, [entityId]);
    });

    return { deletedHoldingsCount, deletedEventsCount };
  }

  // ---------- prices ----------

  async addPrice(
    instrumentId: string, priceDate: string, basis: string,
    priceMinor: string, source: string
  ): Promise<void> {
    await this.db.exec(
      `INSERT OR IGNORE INTO price_observation
       (instrument_id, price_date, basis, price_minor, currency, source, ingested_at)
       VALUES (?,?,?,?, 'INR', ?, ?)`,
      [instrumentId, priceDate, basis, priceMinor, source, new Date().toISOString()]);
  }

  async latestPrice(instrumentId: string, asOf: string, basis?: string) {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT price_minor, price_date, basis, source FROM price_observation
       WHERE instrument_id = ? AND price_date <= ? ${basis ? 'AND basis = ?' : ''}
       ORDER BY price_date DESC, ingested_at DESC LIMIT 1`,
      basis ? [instrumentId, asOf, basis] : [instrumentId, asOf]);
    if (!rows.length) return null;
    return {
      priceMinor: String(rows[0].price_minor),
      priceDate: String(rows[0].price_date),
      basis: String(rows[0].basis),
      source: String(rows[0].source),
    };
  }

  // ---------- import batches & statement positions ----------

  async recordBatch(b: {
    id: string;
    filename: string;
    kind: string;
    sha256?: string;
    statementDate?: string;
    periodFrom?: string;
    periodTo?: string;
    investorName?: string;
    investorPanMasked?: string;
    investorEmail?: string;
    casId?: string;
    statementType?: string;
    rowsNew: number;
    rowsDuplicate: number;
    rowsConflict: number;
  }): Promise<void> {
    await this.db.exec(
      `INSERT INTO import_batch (import_batch_id, source_filename, document_kind,
         sha256, statement_date, period_from, period_to, investor_name,
         investor_pan_masked, investor_email, cas_id, statement_type,
         rows_new, rows_duplicate, rows_conflict, committed_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [b.id, b.filename, b.kind, b.sha256 ?? null, b.statementDate ?? null,
       b.periodFrom ?? null, b.periodTo ?? null, b.investorName ?? null,
       b.investorPanMasked ?? null, b.investorEmail ?? null, b.casId ?? null,
       b.statementType ?? null,
       b.rowsNew, b.rowsDuplicate, b.rowsConflict, new Date().toISOString()]);
  }

  async recordStatementPosition(p: {
    importBatchId: string;
    holdingId: string;
    statementDate: string;
    quantityScaled: string;
    unitPriceMinor: string;
    investedMinor?: string;
    isin?: string;
    folio?: string;
    currentValueMinor?: string;
    amc?: string;
    schemeType?: string;
    sourceSection?: string;
  }): Promise<void> {
    await this.db.exec(
      `INSERT OR REPLACE INTO statement_position
       (import_batch_id, holding_id, statement_date, quantity_scaled, unit_price_minor,
        invested_minor, isin, folio, current_value_minor, amc, scheme_type, source_section)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [p.importBatchId, p.holdingId, p.statementDate, p.quantityScaled, p.unitPriceMinor,
       p.investedMinor ?? null, p.isin ?? null, p.folio ?? null,
       p.currentValueMinor ?? null, p.amc ?? null, p.schemeType ?? null,
       p.sourceSection ?? null]);
  }

  /** Has this exact file been imported before? Prevents re-import double counting. */
  async batchBySha(sha256: string) {
    const r = await this.db.query<Record<string, SqlValue>>(
      `SELECT import_batch_id, source_filename, committed_at
       FROM import_batch WHERE sha256 = ? LIMIT 1`, [sha256]);
    return r.length ? {
      id: String(r[0].import_batch_id),
      filename: String(r[0].source_filename),
      committedAt: String(r[0].committed_at),
    } : null;
  }
}

function s(v: SqlValue): string { return v === null || v === undefined ? '' : String(v); }

function rowToEvent(r: Record<string, SqlValue>): LedgerEvent {
  return {
    id: s(r.ledger_event_id),
    ownerId: s(r.entity_id),
    holdingId: s(r.holding_id),
    holdingName: s(r.holding_name),
    eventType: s(r.event_type) as LedgerEvent['eventType'],
    eventDate: s(r.trade_date),
    quantityScaled: s(r.quantity_scaled),
    unitPriceMinor: s(r.unit_price_minor),
    totalAmountMinor: s(r.total_amount_minor),
    chargesMinor: r.charges_minor ? s(r.charges_minor) : undefined,
    supersedesEventId: r.supersedes_event_id ? s(r.supersedes_event_id) : undefined,
    notes: r.notes ? s(r.notes) : undefined,
    createdAt: s(r.entered_at),
    importBatchId: r.import_batch_id ? s(r.import_batch_id) : undefined,
    sourceRef: r.source_ref ? s(r.source_ref) : undefined,
  } as LedgerEvent;
}

function rowToHolding(r: Record<string, SqlValue>): Holding {
  return {
    id: s(r.holding_id),
    name: s(r.name),
    category: s(r.category) as Holding['category'],
    subCategory: s(r.sub_category),
    symbolOrFolio: s(r.symbol_or_folio),
    isin: r.isin ? s(r.isin) : undefined,
    ownerId: s(r.entity_id),
    accountId: r.account_id ? s(r.account_id) : undefined,
    quantityScaled: '0',              // derived from the ledger, never stored
    averageCostPerUnitMinor: '0',     // derived from the ledger, never stored
    currentPriceMinor: s(r.current_price_minor),
    benchmarkPriceMinor: r.benchmark_price_minor ? s(r.benchmark_price_minor) : undefined,
    issuePriceMinor: r.issue_price_minor ? s(r.issue_price_minor) : undefined,
    lastPriceUpdated: s(r.last_price_updated),
    isPriceStale: false,
    priceSource: s(r.price_source),
    exposureNode: r.exposure_node ? s(r.exposure_node) : undefined,
    l2Node: r.l2_node ? s(r.l2_node) : undefined,
    wrapper: r.wrapper ? s(r.wrapper) : undefined,
    allocationSplit: r.allocation_split ? JSON.parse(s(r.allocation_split)) : undefined,
    fmv31Jan2018Minor: r.fmv_31jan2018_minor ? s(r.fmv_31jan2018_minor) : undefined,
    needsReview: Number(r.needs_review) === 1,
    reviewReasons: r.review_reasons ? JSON.parse(s(r.review_reasons)) : undefined,
    institutionName: r.institution_name ? s(r.institution_name) : undefined,
    accountNumberMasked: r.account_number_masked ? s(r.account_number_masked) : undefined,
    notes: r.holding_notes ? s(r.holding_notes) : undefined,
    location: r.location ? s(r.location) : undefined,
    tag: r.tag ? s(r.tag) : undefined,
    tags: r.tags_json ? JSON.parse(s(r.tags_json)) : undefined,
    couponRatePercent: fromBps(r.coupon_rate_bps),
    interestRatePercent: fromBps(r.interest_rate_bps),
    maturityDate: r.maturity_date ? s(r.maturity_date) : undefined,
    nextCouponDate: r.next_coupon_date ? s(r.next_coupon_date) : undefined,
    monthlyEmiMinor: r.monthly_emi_minor ? s(r.monthly_emi_minor) : undefined,
    amc: r.amc ? s(r.amc) : undefined,
    rta: r.rta ? s(r.rta) : undefined,
    amfiCode: r.amfi_code ? s(r.amfi_code) : undefined,
    advisor: r.advisor ? s(r.advisor) : undefined,
    schemeType: r.scheme_type ? s(r.scheme_type) : undefined,
    ucc: r.ucc ? s(r.ucc) : undefined,
    exchange: r.exchange ? s(r.exchange) : undefined,
    dpName: r.dp_name ? s(r.dp_name) : undefined,
  } as Holding;
}

const RELATIONS: FamilyRelation[] = ['Self', 'Spouse', 'Parent', 'Child', 'HUF', 'Joint'];

function rowToEntity(r: Record<string, SqlValue>): FamilyMember {
  const rawRel = r.relation ? s(r.relation) : '';
  const relation: FamilyRelation = (RELATIONS as string[]).includes(rawRel)
    ? (rawRel as FamilyRelation)
    : (Number(r.is_primary) === 1 ? 'Self' : 'Spouse');
  return {
    id: s(r.entity_id),
    name: s(r.display_name),
    relation,
    color: r.color ? s(r.color) : '#0F3D5C',
    panMasked: r.pan_masked ? s(r.pan_masked) : undefined,
    panForStatements: r.pan_for_statements ? s(r.pan_for_statements) : undefined,
    dateOfBirth: r.date_of_birth ? s(r.date_of_birth) : undefined,
    notes: r.notes ? s(r.notes) : undefined,
    isPrimary: Number(r.is_primary) === 1 || relation === 'Self',
  };
}

function rowToAccount(r: Record<string, SqlValue>): DematOrFolioAccount {
  return {
    id: s(r.account_id),
    ownerId: s(r.entity_id),
    accountType: s(r.account_type),
    custodian: s(r.custodian),
    identifierMasked: r.identifier_masked ? s(r.identifier_masked) : undefined,
    dpId: r.dp_id ? s(r.dp_id) : undefined,
    clientId: r.client_id ? s(r.client_id) : undefined,
    boId: r.bo_id ? s(r.bo_id) : undefined,
    nominee: r.nominee ? s(r.nominee) : undefined,
  };
}

function rowToGoal(r: Record<string, SqlValue>): FamilyGoal {
  return {
    id: s(r.goal_id),
    ownerId: r.entity_id ? s(r.entity_id) : undefined,
    name: s(r.name),
    targetMinor: s(r.target_minor || '0'),
    targetDate: r.target_date ? s(r.target_date) : undefined,
    priority: r.priority ? s(r.priority) : undefined,
    createdAt: s(r.created_at),
  };
}

function rowToBatch(r: Record<string, SqlValue>): ImportBatchInfo {
  return {
    id: s(r.import_batch_id),
    filename: s(r.source_filename),
    kind: s(r.document_kind),
    statementDate: r.statement_date ? s(r.statement_date) : undefined,
    periodFrom: r.period_from ? s(r.period_from) : undefined,
    periodTo: r.period_to ? s(r.period_to) : undefined,
    investorName: r.investor_name ? s(r.investor_name) : undefined,
    statementType: r.statement_type ? s(r.statement_type) : undefined,
    rowsNew: Number(r.rows_new || 0),
    rowsDuplicate: Number(r.rows_duplicate || 0),
    committedAt: s(r.committed_at),
  };
}

