package `in`.growurdhan.database

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.junit.Assert.*
import org.junit.Test

/**
 * Test-only JVM SQLCipher fixture. NEVER the production Android vault opener.
 * Encrypted database is empty on exit. A zero-value synthetic event used for
 * trigger verification exists only inside a rolled-back transaction.
 */
class EncryptedSchemaV9Test {
    private fun statements(): List<String> {
        val text = javaClass.classLoader!!.getResourceAsStream("schema-v9-frozen.sql")!!
            .bufferedReader().use { it.readText() }
        return text.split("-- M24_END_STATEMENT").map { piece ->
            piece.lineSequence().filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n").trim()
        }.filter { it.isNotBlank() }
    }

    private fun legacySource(): String {
        var dir = File(System.getProperty("user.dir"))
        repeat(9) {
            val file = File(dir, "src/db/schema.ts")
            if (file.isFile) {
                val bytes = file.readBytes()
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                assertEquals(SchemaV9Catalog.LEGACY_SHA256, hash)
                return bytes.toString(Charsets.UTF_8)
            }
            dir = dir.parentFile ?: error("Frozen schema source unavailable")
        }
        error("Frozen schema source unavailable")
    }

    private fun legacyBlock(source: String, name: String): String {
        val start = "export const $name = " + 96.toChar()
        require(start in source) { "Missing $name" }
        return source.substringAfter(start).substringBefore(96.toChar() + ";")
    }

    private fun splitLegacy(raw: String): List<String> {
        val all = mutableListOf<String>()
        val current = StringBuilder()
        var trigger = false
        for (line in raw.lineSequence()) {
            val v = line.trim()
            if (v.isEmpty() || v.startsWith("--")) continue
            if (current.isEmpty() && v.startsWith("CREATE TRIGGER")) trigger = true
            if (current.isNotEmpty()) current.append('\n')
            current.append(v)
            if ((if (trigger) v == "END;" else v.endsWith(";"))) {
                val statement = current.toString()
                if (!statement.startsWith("PRAGMA foreign_keys")) all.add(statement)
                current.clear()
                trigger = false
            }
        }
        require(current.isEmpty()) { "Unterminated source DDL" }
        return all
    }

    private fun migration(source: String, name: String): List<String> {
        val key = "export const $name: [string, string, string][] = ["
        val raw = source.substringAfter(key, "").substringBefore("];")
        require(raw.isNotBlank())
        return Regex("""\['([a-z_]+)', '([a-z_]+)', '([^']+)'\]""")
            .findAll(raw).map {
                "ALTER TABLE " + it.groupValues[1] +
                    " ADD COLUMN " + it.groupValues[3] + ";"
            }.toList()
    }

    private fun fromOriginal(): List<String> {
        val legacy = legacySource()
        return listOf("SCHEMA_V1", "SCHEMA_V5_SQL", "SCHEMA_V6_SQL", "SCHEMA_V8_SQL")
            .flatMap { splitLegacy(legacyBlock(legacy, it)) } +
            migration(legacy, "SCHEMA_V8_COLUMNS") +
            migration(legacy, "SCHEMA_V9_COLUMNS")
    }

    private fun fixture(): Pair<File, String> {
        val file = Files.createTempFile("m24-v9-encrypted-", ".db").toFile()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        return file to key
    }

    private fun connect(file: File, key: String): Connection =
        DriverManager.getConnection(
            "jdbc:sqlite:file:" + file.absolutePath + "?cipher=sqlcipher&legacy=4&key=" + key)

    private fun number(c: Connection, sql: String): Long = c.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            check(rs.next())
            rs.getLong(1)
        }
    }

    private fun names(c: Connection, sql: String): List<String> = c.createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add(rs.getString(1)) }
        }
    }

    private fun create(c: Connection) {
        c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        check(number(c, "PRAGMA foreign_keys") == 1L)
        check(number(c, "PRAGMA user_version") == 0L) {
            "Refuse existing encrypted schemas; full migration belongs to M24 later."
        }
        check(number(c, "SELECT count(*) FROM sqlite_master WHERE type='table' " +
            "AND name NOT LIKE 'sqlite_%'") == 0L) {
            "Only initialize an empty test file."
        }
        c.autoCommit = false
        try {
            c.createStatement().use { st ->
                statements().forEach(st::execute)
                st.execute("PRAGMA user_version=9")
            }
            c.commit()
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
        }
    }

    private fun assertEmpty(c: Connection) {
        SchemaV9Catalog.tableNames.forEach {
            assertEquals("Unexpected persisted row in $it", 0L,
                number(c, "SELECT count(*) FROM $it"))
        }
    }

    private fun assertExactSchema(c: Connection) {
        assertEquals(9L, number(c, "PRAGMA user_version"))
        assertEquals(1L, number(c, "PRAGMA foreign_keys"))
        assertEquals(listOf("ok"), names(c, "PRAGMA integrity_check"))
        assertTrue(names(c, "PRAGMA foreign_key_check").isEmpty())
        assertEquals(SchemaV9Catalog.tableNames, names(c,
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY rowid"))
        val v9extras = listOf("cas_id", "pran", "gmail_sync", "cas_id_source",
            "pran_source", "cas_sync_after", "pran_sync_after")
        var columnCount = 0
        for (table in SchemaV9Catalog.tables) {
            val expected = table.columns +
                (if (table.name == "entity") v9extras else emptyList())
            val actual = names(c,
                "SELECT name FROM pragma_table_info('" + table.name + "') ORDER BY cid")
            assertEquals("Schema mismatch in " + table.name, expected, actual)
            // Assert frozen SQL affinities, not only names. No REAL money fields.
            val ddl = statements().first {
                it.startsWith("CREATE TABLE IF NOT EXISTS " + table.name + " (")
            }
            val definition = Regex(
                """(?m)^\s*([a-z_][a-z0-9_]*)\s+(TEXT|INTEGER|BLOB|REAL|NUMERIC)\b"""
            ).findAll(ddl).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val extras = if (table.name == "entity")
                listOf("cas_id" to "TEXT", "pran" to "TEXT",
                    "gmail_sync" to "INTEGER", "cas_id_source" to "TEXT",
                    "pran_source" to "TEXT", "cas_sync_after" to "TEXT",
                    "pran_sync_after" to "TEXT")
                else emptyList()
            val expectedTypes = definition + extras
            val actualTypes = c.createStatement().use { st ->
                st.executeQuery("PRAGMA table_info('" + table.name + "')").use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(rows.getString("name") to rows.getString("type"))
                        }
                    }
                }
            }
            assertEquals("Column type mismatch in " + table.name,
                expectedTypes, actualTypes)
            columnCount += actual.size
        }
        assertEquals(210, SchemaV9Catalog.tables.sumOf { it.columns.size })
        assertEquals(217, columnCount)
        val indexes = Regex("""CREATE (?:UNIQUE )?INDEX IF NOT EXISTS ([a-z_]+)""")
            .findAll(statements().joinToString("\n")).map { it.groupValues[1] }.toSet()
        assertEquals(indexes, names(c, "SELECT name FROM sqlite_master " +
            "WHERE type='index' AND name NOT LIKE 'sqlite_%'").toSet())
        assertEquals(setOf("tg_ledger_event__no_update", "tg_ledger_event__no_delete",
            "tg_tax_rule_set__no_update"),
            names(c, "SELECT name FROM sqlite_master WHERE type='trigger'").toSet())
        assertEquals(1L, number(c,
            "SELECT count(*) FROM pragma_foreign_key_list('account') " +
                "WHERE \"table\"='entity' AND \"from\"='entity_id'"))
        assertEmpty(c)
    }

    @Test fun exactFrozenSourceSchemaAndV9MigrationColumns() {
        val s = statements()
        assertEquals(fromOriginal(), s)
        assertEquals(41, s.size)
        assertEquals(18, s.count { it.startsWith("CREATE TABLE") })
        assertEquals(13, s.count {
            it.startsWith("CREATE INDEX") || it.startsWith("CREATE UNIQUE INDEX")
        })
        assertEquals(3, s.count { it.startsWith("CREATE TRIGGER") })
        assertEquals(7, s.count { it.startsWith("ALTER TABLE") })
        assertTrue(s.any { "CHECK (current_price_minor >= 0)" in it })
    }

    @Test fun encryptedSchemaOpensAndCopiedBackupRetainsAllFields() {
        val (file, key) = fixture()
        val copy = Files.createTempFile("m24-v9-copy-", ".db").toFile()
        try {
            connect(file, key).use { create(it); assertExactSchema(it) }
            assertFalse(file.readBytes().copyOf(16).toString(Charsets.UTF_8)
                .startsWith("SQLite format 3"))
            Files.copy(file.toPath(), copy.toPath(), StandardCopyOption.REPLACE_EXISTING)
            connect(copy, key).use {
                it.createStatement().use { s -> s.execute("PRAGMA foreign_keys=ON") }
                assertExactSchema(it)
            }
            assertThrows(SQLException::class.java) {
                connect(copy, "incorrect-key").use {
                    number(it, "PRAGMA user_version")
                }
            }
        } finally {
            file.delete()
            copy.delete()
        }
    }

    @Test fun rejectsForeignKeyViolationAndRefusesNonemptyReinitialization() {
        val (file, key) = fixture()
        try {
            connect(file, key).use { c ->
                create(c)
                assertThrows(SQLException::class.java) {
                    c.createStatement().use { st ->
                        st.executeUpdate("INSERT INTO account(account_id,entity_id," +
                            "account_type,custodian,created_at) VALUES " +
                            "('synthetic','missing','test','test','2026-10-16')")
                    }
                }
                assertThrows(IllegalStateException::class.java) { create(c) }
                assertExactSchema(c)
            }
        } finally { file.delete() }
    }

    @Test fun ledgerUpdateDeleteAbortAndRollbackLeavesNoFinancialRows() {
        val (file, key) = fixture()
        try {
            connect(file, key).use { c ->
                create(c)
                c.autoCommit = false
                try {
                    c.createStatement().use { st ->
                        // Purely synthetic, zero-value, rolled-back test event.
                        st.executeUpdate("INSERT INTO ledger_event(ledger_event_id," +
                            "entity_id,holding_id,holding_name,event_type,trade_date," +
                            "entered_at) VALUES ('dummy','dummy','dummy','dummy'," +
                            "'OPENING_BALANCE','2026-10-16','2026-10-16')")
                        assertThrows(SQLException::class.java) {
                            st.executeUpdate("UPDATE ledger_event SET notes='edited' " +
                                "WHERE ledger_event_id='dummy'")
                        }
                        assertThrows(SQLException::class.java) {
                            st.executeUpdate("DELETE FROM ledger_event " +
                                "WHERE ledger_event_id='dummy'")
                        }
                    }
                } finally {
                    c.rollback()
                    c.autoCommit = true
                }
                assertExactSchema(c)
            }
        } finally { file.delete() }
    }
}
