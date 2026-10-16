package `in`.growurdhan.database

import java.io.File
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * M24 test harness ONLY. This migrates synthetic SQLCipher/JDBC fixtures,
 * not user vaults or Android production storage. It refuses unknown versions,
 * inconsistent metadata, damaged databases and incompatible mandatory fields.
 *
 * The frozen source currently embeds some v3/v4/v7 columns in SCHEMA_V1.
 * The test builder can omit representative legacy columns explicitly; this
 * never misrepresents a reconstructed fixture as a captured historical vault.
 */
internal object TestOnlyV9Migration {

    fun connect(file: File, keyHex: String): Connection =
        DriverManager.getConnection(
            "jdbc:sqlite:file:" + file.absolutePath + "?cipher=sqlcipher&legacy=4&key=" + keyHex
        )

    private fun rawLegacy(): String {
        var dir = File(System.getProperty("user.dir"))
        repeat(10) {
            val src = File(dir, "src/db/schema.ts")
            if (src.isFile) {
                val bytes = src.readBytes()
                val checksum = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                require(checksum == SchemaV9Catalog.LEGACY_SHA256) {
                    "Frozen reference unexpectedly changed."
                }
                return bytes.toString(Charsets.UTF_8)
            }
            dir = dir.parentFile ?: error("Frozen schema.ts not available")
        }
        error("Frozen schema.ts not available")
    }

    private fun block(source: String, name: String): String {
        val tag = "export const " + name + " = " + 96.toChar()
        require(source.contains(tag)) { "Missing frozen DDL block: " + name }
        return source.substringAfter(tag).substringBefore(96.toChar() + ";")
    }

    private fun sqlStatements(raw: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var trigger = false
        for (line in raw.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("--")) continue
            if (current.isEmpty() && trimmed.startsWith("CREATE TRIGGER")) trigger = true
            if (current.isNotEmpty()) current.append('\n')
            current.append(trimmed)
            if (if (trigger) trimmed == "END;" else trimmed.endsWith(";")) {
                val statement = current.toString()
                if (!statement.startsWith("PRAGMA foreign_keys")) statements.add(statement)
                current.clear()
                trigger = false
            }
        }
        require(current.isEmpty()) { "Unterminated original SQL" }
        return statements
    }

    private fun changes(source: String, name: String): List<ColumnAddition> {
        val tag = "export const " + name + ": [string, string, string][] = ["
        require(tag in source) { "Missing original migration array " + name }
        val body = source.substringAfter(tag).substringBefore("];")
        return Regex("""\['([a-z_]+)', '([a-z_]+)', (?:'([^']+)'|"([^"]+)")\]""")
            .findAll(body).map { result ->
                val ddl = result.groupValues[3].ifBlank { result.groupValues[4] }
                ColumnAddition(result.groupValues[1], result.groupValues[2], ddl)
            }.toList().also { require(it.isNotEmpty()) }
    }

    private data class ColumnAddition(val table: String, val name: String, val ddl: String)

    private fun count(c: Connection, sql: String): Long = c.createStatement().use { st ->
        st.executeQuery(sql).use { rs -> check(rs.next()); rs.getLong(1) }
    }

    private fun values(c: Connection, sql: String): List<String> =
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    private fun hasColumn(c: Connection, table: String, column: String): Boolean =
        values(c, "SELECT name FROM pragma_table_info('" + table + "')")
            .contains(column)

    private fun version(c: Connection): Int = count(c, "PRAGMA user_version").toInt()

    private fun metadataVersion(c: Connection): Int {
        val data = values(c, "SELECT value FROM vault_meta WHERE key='schema_version'")
        require(data.size == 1) { "Missing or conflicting schema_version metadata." }
        return data.single().toIntOrNull() ?: error("Invalid schema_version metadata")
    }

    private fun verifyFoundation(c: Connection, from: Int) {
        require(from in 1..8) { "Unsupported or untrusted schema version " + from }
        require(count(c, "PRAGMA foreign_keys") == 1L)
        require(values(c, "PRAGMA quick_check") == listOf("ok")) {
            "Database damaged; refusing migration"
        }
        require(values(c, "PRAGMA foreign_key_check").isEmpty()) {
            "Database has invalid existing ownership links"
        }
        val expected = sqlStatements(block(rawLegacy(), "SCHEMA_V1"))
            .filter { it.startsWith("CREATE TABLE") }
            .map { it.substringAfter("CREATE TABLE IF NOT EXISTS ").substringBefore(" ") }
        val actual = values(c, "SELECT name FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%'")
        require(actual.containsAll(expected)) { "Required legacy table missing" }
        require(version(c) == from && metadataVersion(c) == from) {
            "Inconsistent schema version metadata"
        }
        // A migration must never silently bless a legacy vault whose ledger
        // already lost its append-only protection.
        val ledgerTriggers = values(c,
            "SELECT name FROM sqlite_master WHERE type='trigger' " +
                "AND tbl_name='ledger_event'").toSet()
        require(ledgerTriggers.containsAll(setOf(
            "tg_ledger_event__no_update", "tg_ledger_event__no_delete"))) {
            "Legacy ledger immutability guard is missing."
        }
        // A same-named no-op trigger is not an append-only guarantee.
        for ((name, operation) in listOf(
            "tg_ledger_event__no_update" to "BEFORE UPDATE",
            "tg_ledger_event__no_delete" to "BEFORE DELETE"
        )) {
            val definition = values(c,
                "SELECT UPPER(sql) FROM sqlite_master WHERE type='trigger' " +
                    "AND name='" + name + "'").singleOrNull()
            require(definition != null &&
                definition.contains(operation) &&
                definition.contains("ON LEDGER_EVENT") &&
                definition.contains("RAISE(ABORT")) {
                "Legacy append-only trigger behavior is invalid: " + name
            }
        }
        require(count(c, "SELECT count(*) FROM pragma_foreign_key_list('account') " +
            "WHERE \"table\"='entity' AND \"from\"='entity_id'") == 1L) {
            "Legacy account ownership constraint missing."
        }
        val mandatory = mapOf(
            "entity" to mapOf("entity_id" to "TEXT", "entity_type" to "TEXT"),
            "holding" to mapOf("holding_id" to "TEXT", "entity_id" to "TEXT",
                "current_price_minor" to "INTEGER"),
            "ledger_event" to mapOf("ledger_event_id" to "TEXT",
                "quantity_scaled" to "INTEGER", "total_amount_minor" to "INTEGER"),
        )
        for ((table, expectedColumns) in mandatory) {
            val types = c.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info('" + table + "')").use { rs ->
                    buildMap {
                        while (rs.next()) put(rs.getString("name"), rs.getString("type"))
                    }
                }
            }
            for ((column, type) in expectedColumns) {
                require(types[column] == type) {
                    "Incompatible legacy mandatory field " + table + "." + column
                }
            }
        }
    }

    private fun addColumns(c: Connection, steps: List<ColumnAddition>, execute: (String) -> Unit) {
        for (step in steps) {
            if (!hasColumn(c, step.table, step.name)) {
                execute("ALTER TABLE " + step.table + " ADD COLUMN " + step.ddl)
            } else {
                val type = c.createStatement().use { st ->
                    st.executeQuery("PRAGMA table_info('" + step.table + "')").use { rs ->
                        var result: String? = null
                        while (rs.next()) {
                            if (rs.getString("name") == step.name) result = rs.getString("type")
                        }
                        result
                    }
                }
                val expectedType = step.ddl.substringAfter(' ').substringBefore(' ').uppercase()
                require(type == expectedType) {
                    "Incompatible existing migration field " + step.table + "." + step.name
                }
            }
        }
    }

    /** Runs all applicable *frozen* steps, with one transaction from original version through v9. */
    fun migrate(c: Connection, failAfterDdl: Int = -1) {
        c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        val from = version(c)
        verifyFoundation(c, from) // must fail before mutating even an existing bad vault
        val source = rawLegacy()
        c.autoCommit = false
        var executed = 0
        try {
            fun step(statement: String) {
                c.createStatement().use { it.execute(statement) }
                executed++
                if (executed == failAfterDdl) error("Injected failure after DDL")
            }
            for (phase in SchemaV9MigrationPlan.plan(from)) {
                when (phase.version) {
                    3 -> addColumns(c, changes(source, "SCHEMA_V3_COLUMNS"), ::step)
                    4 -> addColumns(c, changes(source, "SCHEMA_V4_COLUMNS"), ::step)
                    5 -> sqlStatements(block(source, "SCHEMA_V5_SQL")).forEach(::step)
                    6 -> sqlStatements(block(source, "SCHEMA_V6_SQL")).forEach(::step)
                    7 -> addColumns(c, changes(source, "SCHEMA_V7_COLUMNS"), ::step)
                    8 -> {
                        addColumns(c, changes(source, "SCHEMA_V8_COLUMNS"), ::step)
                        sqlStatements(block(source, "SCHEMA_V8_SQL")).forEach(::step)
                    }
                    9 -> addColumns(c, changes(source, "SCHEMA_V9_COLUMNS"), ::step)
                }
            }
            require(executed > 0) { "No migration DDL executed" }
            step("PRAGMA user_version=9")
            step("UPDATE vault_meta SET value='9', updated_at='2026-10-16' " +
                "WHERE key='schema_version'")
            require(version(c) == 9 && metadataVersion(c) == 9)
            require(values(c, "PRAGMA foreign_key_check").isEmpty())
            c.commit()
        } catch (failed: Exception) {
            c.rollback()
            throw failed
        } finally {
            c.autoCommit = true
        }
    }

    /**
     * Reconstruct a controlled *synthetic* legacy projection. Existing data is
     * not copied from real users. Earlier v3/4/7 column omission is exercised
     * separately because the frozen current SCHEMA_V1 already includes them.
     */
    fun seedPriorVersion(c: Connection, prior: Int, omitLegacyOptional: Boolean = false) {
        require(prior in 1..8)
        c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        val source = rawLegacy()
        c.createStatement().use { st ->
            sqlStatements(block(source, "SCHEMA_V1")).forEach(st::execute)
            if (prior >= 5) sqlStatements(block(source, "SCHEMA_V5_SQL")).forEach(st::execute)
            if (prior >= 6) sqlStatements(block(source, "SCHEMA_V6_SQL")).forEach(st::execute)
            if (prior >= 8) {
                changes(source, "SCHEMA_V8_COLUMNS").forEach {
                    st.execute("ALTER TABLE " + it.table + " ADD COLUMN " + it.ddl)
                }
                sqlStatements(block(source, "SCHEMA_V8_SQL")).forEach(st::execute)
            }
            if (omitLegacyOptional) {
                val removable = buildList {
                    if (prior < 3) {
                        add("ALTER TABLE instrument DROP COLUMN scheme_type")
                        add("ALTER TABLE holding DROP COLUMN issue_price_minor")
                    }
                    if (prior < 4) add("ALTER TABLE holding DROP COLUMN emi_status")
                    if (prior < 7) add("ALTER TABLE holding DROP COLUMN details_json")
                }
                removable.forEach(st::execute)
            }
            st.execute("PRAGMA user_version=" + prior)
            st.execute("INSERT INTO vault_meta(key,value,updated_at) VALUES " +
                "('schema_version','" + prior + "','2026-10-16')")
        }
    }

    fun seedSyntheticRecords(c: Connection) {
        c.createStatement().use { st ->
            st.executeUpdate("INSERT INTO entity(entity_id, entity_type, display_name, " +
                "created_at) VALUES ('synthetic-owner-A','INDIVIDUAL','Synthetic A','2026-10-16')")
            st.executeUpdate("INSERT INTO entity(entity_id, entity_type, display_name, " +
                "created_at) VALUES ('synthetic-owner-B','INDIVIDUAL','Synthetic B','2026-10-16')")
            st.executeUpdate("INSERT INTO instrument(instrument_id,name,category,created_at) " +
                "VALUES ('synthetic-isin','Synthetic instrument','EQUITY','2026-10-16')")
            st.executeUpdate("INSERT INTO holding(holding_id,instrument_id,entity_id," +
                "current_price_minor,tags_json,created_at) VALUES " +
                "('synthetic-holding','synthetic-isin','synthetic-owner-A',123456789," +
                "'{\"unknownOriginalKey\":{\"opaque\":\"keep-me\"}}','2026-10-16')")
            if (hasColumn(c, "holding", "details_json")) {
                st.executeUpdate("UPDATE holding SET details_json=" +
                    "'{\"unknownType\":{\"nested\":[true,123,\"preserve\"]}}' " +
                    "WHERE holding_id='synthetic-holding'")
            }
            st.executeUpdate("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
                "holding_name,event_type,trade_date,quantity_scaled,unit_price_minor," +
                "total_amount_minor,charges_minor,entered_at) VALUES " +
                "('synthetic-event','synthetic-owner-A','synthetic-holding'," +
                "'Synthetic','OPENING_BALANCE','2026-10-16',1234567890123," +
                "123456789,1234567890123,1234,'2026-10-16')")
        }
    }

    fun rowsFingerprint(c: Connection): List<String> = listOf(
        "SELECT entity_id || ':' || entity_type FROM entity ORDER BY entity_id",
        "SELECT holding_id || ':' || entity_id || ':' || current_price_minor || " +
            "':' || tags_json FROM holding ORDER BY holding_id",
        "SELECT ledger_event_id || ':' || entity_id || ':' || quantity_scaled || " +
            "':' || total_amount_minor || ':' || charges_minor FROM ledger_event",
        "SELECT value FROM vault_meta WHERE key='schema_version'",
    ).flatMap { values(c, it) }

    fun definitionFingerprint(c: Connection): List<String> =
        values(c, "SELECT type || ':' || name || ':' || sql " +
            "FROM sqlite_master WHERE sql IS NOT NULL ORDER BY type,name")
}
