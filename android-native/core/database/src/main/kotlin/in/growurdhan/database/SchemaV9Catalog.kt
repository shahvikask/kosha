package `in`.growurdhan.database

data class SchemaTable(
    val name: String,
    val columns: List<String>,
)

/**
 * Column names taken from frozen src/db/schema.ts at schema version 9.
 * This catalog opens no database and stores no row.
 */
object SchemaV9Catalog {
    const val VERSION = 9
    const val LEGACY_SHA256 = "8b98666f1d4954b1f3bb09e199dd504dd23b1155cf81becfcbd6fcbdbcb7e3a1"
    val tables: List<SchemaTable> = listOf(
        SchemaTable("vault_meta", listOf("key", "value", "updated_at")),
        SchemaTable("entity", listOf("entity_id", "entity_type", "display_name", "pan_masked", "pan_for_statements", "date_of_majority", "date_of_birth", "guardian_id", "relation", "color", "notes", "is_primary", "is_active", "created_at")),
        SchemaTable("account", listOf("account_id", "entity_id", "account_type", "custodian", "identifier_masked", "dp_id", "client_id", "bo_id", "base_currency", "nominee", "opened_on", "closed_on", "created_at")),
        SchemaTable("instrument", listOf("instrument_id", "name", "category", "sub_category", "symbol_or_folio", "isin", "currency", "exposure_node", "l2_node", "wrapper", "allocation_split", "fmv_31jan2018_minor", "amc", "rta", "amfi_code", "advisor", "scheme_type", "ucc", "exchange", "dp_name", "is_user_created", "created_at")),
        SchemaTable("holding", listOf("holding_id", "instrument_id", "entity_id", "account_id", "current_price_minor", "benchmark_price_minor", "issue_price_minor", "sgb_valuation_mode", "last_price_updated", "price_source", "needs_review", "review_reasons", "institution_name", "account_number_masked", "notes", "location", "tag", "tags_json", "coupon_rate_bps", "interest_rate_bps", "maturity_date", "next_coupon_date", "monthly_emi_minor", "emi_status", "remaining_emi_count", "acquired_on", "details_json", "created_at")),
        SchemaTable("ledger_event", listOf("ledger_event_id", "entity_id", "holding_id", "holding_name", "account_id", "event_type", "trade_date", "settlement_date", "quantity_scaled", "unit_price_minor", "total_amount_minor", "charges_minor", "currency", "supersedes_event_id", "import_batch_id", "source_ref", "unit_balance_after", "description", "notes", "entered_at", "entered_by")),
        SchemaTable("price_observation", listOf("instrument_id", "price_date", "basis", "price_minor", "currency", "source", "ingested_at")),
        SchemaTable("import_batch", listOf("import_batch_id", "source_filename", "document_kind", "sha256", "statement_date", "period_from", "period_to", "investor_name", "investor_pan_masked", "investor_email", "cas_id", "statement_type", "rows_new", "rows_duplicate", "rows_conflict", "committed_at")),
        SchemaTable("source_document", listOf("source_document_id", "import_batch_id", "original_filename", "document_kind", "sha256", "byte_length", "page_count", "imported_at")),
        SchemaTable("statement_position", listOf("import_batch_id", "holding_id", "statement_date", "quantity_scaled", "unit_price_minor", "invested_minor", "isin", "folio", "current_value_minor", "amc", "scheme_type", "source_section")),
        SchemaTable("tax_rule_set", listOf("assessment_year", "rules_json", "published_at", "installed_at")),
        SchemaTable("liability", listOf("liability_id", "entity_id", "liability_type", "name", "lender", "principal_minor", "outstanding_minor", "rate_scaled", "emi_minor", "start_date", "end_date", "linked_holding_id", "created_at")),
        SchemaTable("goal", listOf("goal_id", "entity_id", "name", "target_minor", "target_date", "priority", "created_at")),
        SchemaTable("setting", listOf("key", "value")),
        SchemaTable("egress_log", listOf("egress_log_id", "occurred_at", "destination", "purpose", "request_bytes", "response_bytes", "outcome", "allow_listed")),
        SchemaTable("listed_security", listOf("isin", "name", "symbol", "nse_symbol", "bse_symbol", "amfi_code", "category", "close_minor", "price_date", "source", "updated_at")),
        SchemaTable("vault_account", listOf("id", "email", "first_name", "last_name", "password_salt", "password_hash", "password_iterations", "password_changed_at", "pan_masked", "pan_for_statements", "date_of_birth", "q1_id", "q1_hash", "q2_id", "q2_hash", "created_at")),
        SchemaTable("gmail_seen", listOf("message_id", "attachment_id", "filename", "entity_id", "seen_at")),
    )

    val tableNames: List<String> = tables.map { it.name }
}

/**
 * M24 encryption boundary. Production code does not open a database.
 * A unit test links the JVM SQLCipher driver and deletes its file.
 * Plain SQLite is not an allowed open path.
 */
object EncryptedSchemaBoundary {
    const val SQLCIPHER_LINKED = false
    const val OPENS_DATABASE = false
    const val PLAINTEXT_OPEN_ALLOWED = false
    const val SCHEMA_VERSION = SchemaV9Catalog.VERSION
    val tableNames: List<String> = SchemaV9Catalog.tableNames
}
