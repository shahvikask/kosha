/**
 * Typed data access over the SqlDriver. Platform-neutral.
 *
 * Rules enforced here:
 *  - Money and quantity cross the boundary as strings, converted to
 *    INTEGER columns. Never as JavaScript numbers.
 *  - The ledger is append-only. There is no update or delete method,
 *    by design. Corrections are new events.
 */
import { SqlDriver, SqlValue } from './driver';
import { SCHEMA_V1, SCHEMA_VERSION } from './schema';
import { LedgerEvent, Holding, FamilyMember } from '../types';

export class Repository {
  constructor(private db: SqlDriver) {}

  async initialise(): Promise<void> {
    await this.db.exec(SCHEMA_V1);
    const rows = await this.db.query<{ value: string }>(
      `SELECT value FROM vault_meta WHERE key = 'schema_version'`);
    if (rows.length === 0) {
      await this.db.exec(
        `INSERT INTO vault_meta (key, value, updated_at) VALUES (?, ?, ?)`,
        ['schema_version', String(SCHEMA_VERSION), new Date().toISOString()]);
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
       null, null,
       e.notes ?? null, e.createdAt, 'owner'] as SqlValue[]);
  }

  async appendEvents(events: LedgerEvent[]): Promise<void> {
    await this.db.transaction(async () => {
      for (const e of events) await this.appendEvent(e);
    });
  }

  async allEvents(): Promise<LedgerEvent[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT * FROM ledger_event ORDER BY trade_date DESC, ledger_event_id DESC`);
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
         fmv_31jan2018_minor, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
       ON CONFLICT(instrument_id) DO UPDATE SET
         name=excluded.name, category=excluded.category,
         sub_category=excluded.sub_category, symbol_or_folio=excluded.symbol_or_folio,
         isin=excluded.isin, exposure_node=excluded.exposure_node,
         l2_node=excluded.l2_node, wrapper=excluded.wrapper,
         allocation_split=excluded.allocation_split,
         fmv_31jan2018_minor=excluded.fmv_31jan2018_minor`,
      [h.id, h.name, h.category, h.subCategory ?? '', h.symbolOrFolio ?? null,
       h.isin ?? null, h.exposureNode ?? null, h.l2Node ?? null, h.wrapper ?? null,
       h.allocationSplit ? JSON.stringify(h.allocationSplit) : null,
       h.fmv31Jan2018Minor ?? null, new Date().toISOString()] as SqlValue[]);

    await this.db.exec(
      `INSERT INTO holding (
         holding_id, instrument_id, entity_id, current_price_minor,
         benchmark_price_minor, sgb_valuation_mode, last_price_updated,
         price_source, needs_review, review_reasons, created_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?)
       ON CONFLICT(holding_id) DO UPDATE SET
         current_price_minor=excluded.current_price_minor,
         benchmark_price_minor=excluded.benchmark_price_minor,
         sgb_valuation_mode=excluded.sgb_valuation_mode,
         last_price_updated=excluded.last_price_updated,
         price_source=excluded.price_source,
         needs_review=excluded.needs_review,
         review_reasons=excluded.review_reasons`,
      [h.id, h.id, h.ownerId, h.currentPriceMinor ?? '0',
       h.benchmarkPriceMinor ?? null, null,
       h.lastPriceUpdated ?? null, h.priceSource ?? null,
       h.needsReview ? 1 : 0,
       h.reviewReasons ? JSON.stringify(h.reviewReasons) : null,
       new Date().toISOString()] as SqlValue[]);
  }

  async allHoldings(): Promise<Holding[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT h.holding_id, h.entity_id, h.current_price_minor,
              h.benchmark_price_minor, h.sgb_valuation_mode,
              h.last_price_updated, h.price_source, h.needs_review, h.review_reasons,
              i.name, i.category, i.sub_category, i.symbol_or_folio, i.isin,
              i.exposure_node, i.l2_node, i.wrapper, i.allocation_split,
              i.fmv_31jan2018_minor
       FROM holding h JOIN instrument i ON i.instrument_id = h.instrument_id`);
    return rows.map(rowToHolding);
  }

  /** Removes the holding row. Its events remain (SRS-LED-001). */
  async deleteHolding(holdingId: string): Promise<void> {
    await this.db.exec(`DELETE FROM holding WHERE holding_id = ?`, [holdingId]);
  }

  // ---------- entities ----------

  async upsertEntity(m: FamilyMember): Promise<void> {
    await this.db.exec(
      `INSERT INTO entity (entity_id, entity_type, display_name, pan_masked, is_active, created_at)
       VALUES (?,?,?,?,1,?)
       ON CONFLICT(entity_id) DO UPDATE SET
         entity_type=excluded.entity_type, display_name=excluded.display_name,
         pan_masked=excluded.pan_masked`,
      [m.id, 'INDIVIDUAL', m.name, m.panMasked ?? null,
       new Date().toISOString()] as SqlValue[]);
  }

  async allEntities(): Promise<FamilyMember[]> {
    const rows = await this.db.query<Record<string, SqlValue>>(
      `SELECT entity_id, entity_type, display_name, pan_masked, is_active FROM entity`);
    return rows.map(r => ({
      id: String(r.entity_id),
      name: String(r.display_name),
      relation: 'SELF',
      color: '#0F3D5C',
      panMasked: r.pan_masked ? String(r.pan_masked) : undefined,
    })) as unknown as FamilyMember[];
  }

  async deleteEntity(entityId: string): Promise<void> {
    await this.db.transaction(async () => {
      await this.db.exec(
        `DELETE FROM holding WHERE entity_id = ?`, [entityId]);
      await this.db.exec(`DELETE FROM entity WHERE entity_id = ?`, [entityId]);
    });
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
    statementType?: string;
    rowsNew: number;
    rowsDuplicate: number;
    rowsConflict: number;
  }): Promise<void> {
    await this.db.exec(
      `INSERT INTO import_batch (import_batch_id, source_filename, document_kind,
         sha256, statement_date, period_from, period_to, investor_name, statement_type,
         rows_new, rows_duplicate, rows_conflict, committed_at)
       VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)`,
      [b.id, b.filename, b.kind, b.sha256 ?? null, b.statementDate ?? null,
       b.periodFrom ?? null, b.periodTo ?? null, b.investorName ?? null, b.statementType ?? null,
       b.rowsNew, b.rowsDuplicate, b.rowsConflict, new Date().toISOString()]);
  }

  async recordStatementPosition(p: {
    importBatchId: string;
    holdingId: string;
    statementDate: string;
    quantityScaled: string;
    unitPriceMinor: string;
    investedMinor?: string;
  }): Promise<void> {
    await this.db.exec(
      `INSERT OR REPLACE INTO statement_position
       (import_batch_id, holding_id, statement_date, quantity_scaled, unit_price_minor, invested_minor)
       VALUES (?, ?, ?, ?, ?, ?)`,
      [p.importBatchId, p.holdingId, p.statementDate, p.quantityScaled, p.unitPriceMinor, p.investedMinor ?? null]);
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
    chargesMinor: s(r.charges_minor),
    supersedesEventId: r.supersedes_event_id ? s(r.supersedes_event_id) : undefined,
    notes: r.notes ? s(r.notes) : undefined,
    createdAt: s(r.entered_at),
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
    quantityScaled: '0',              // derived from the ledger, never stored
    averageCostPerUnitMinor: '0',     // derived from the ledger, never stored
    currentPriceMinor: s(r.current_price_minor),
    benchmarkPriceMinor: r.benchmark_price_minor ? s(r.benchmark_price_minor) : undefined,
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
  } as Holding;
}
