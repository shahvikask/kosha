package `in`.growurdhan.database

import java.io.File
import java.nio.file.Files
import java.security.SecureRandom
import java.sql.Connection
import java.sql.SQLException
import org.junit.Assert.*
import org.junit.Test

/**
 * Synthetic-only JDBC/SQLCipher versioned migrations, not production restore.
 * Source v1 has later columns already; explicitly reduced fixtures cover
 * representative originally absent v3/v4/v7 columns.
 */
class TestOnlyV9MigrationTest {
    private fun newFixture(): Pair<File, String> {
        val f = Files.createTempFile("m24-synthetic-legacy-", ".db").toFile()
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        return f to key
    }

    private fun number(c: Connection, sql: String): Long =
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> assertTrue(rs.next()); rs.getLong(1) }
        }

    private fun values(c: Connection, sql: String): List<String> =
        c.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }

    private fun assertRecords(c: Connection) {
        assertEquals(2L, number(c, "SELECT count(*) FROM entity"))
        assertEquals(listOf("synthetic-owner-A", "synthetic-owner-B"),
            values(c, "SELECT entity_id FROM entity ORDER BY entity_id"))
        assertEquals(listOf("synthetic-owner-A"),
            values(c, "SELECT entity_id FROM holding WHERE holding_id='synthetic-holding'"))
        assertEquals(123456789L, number(c,
            "SELECT current_price_minor FROM holding WHERE holding_id='synthetic-holding'"))
        assertEquals(1234567890123L, number(c,
            "SELECT quantity_scaled FROM ledger_event WHERE ledger_event_id='synthetic-event'"))
        assertEquals(1234567890123L, number(c,
            "SELECT total_amount_minor FROM ledger_event WHERE ledger_event_id='synthetic-event'"))
        assertEquals(1234L, number(c,
            "SELECT charges_minor FROM ledger_event WHERE ledger_event_id='synthetic-event'"))
        assertEquals(listOf("synthetic-owner-A"),
            values(c, "SELECT entity_id FROM ledger_event"))
        assertEquals(listOf("""{"unknownOriginalKey":{"opaque":"keep-me"}}"""),
            values(c, "SELECT tags_json FROM holding"))
        val detailColumnExists = values(c,
            "SELECT name FROM pragma_table_info('holding')").contains("details_json")
        if (detailColumnExists) {
            val detail = values(c, "SELECT COALESCE(details_json,'') FROM holding").single()
            if (detail.isNotEmpty()) {
                assertEquals("""{"unknownType":{"nested":[true,123,"preserve"]}}""", detail)
            }
        }
        assertEquals(listOf("ok"), values(c, "PRAGMA integrity_check"))
        assertTrue(values(c, "PRAGMA foreign_key_check").isEmpty())
    }

    private fun assertV9(c: Connection) {
        assertEquals(9L, number(c, "PRAGMA user_version"))
        assertEquals(listOf("9"), values(c,
            "SELECT value FROM vault_meta WHERE key='schema_version'"))
        assertEquals(18L, number(c, "SELECT count(*) FROM sqlite_master " +
            "WHERE type='table' AND name NOT LIKE 'sqlite_%'"))
        val extraEntity = listOf("cas_id","pran","gmail_sync","cas_id_source",
            "pran_source","cas_sync_after","pran_sync_after")
        val cols = SchemaV9Catalog.tables.sumOf { table ->
            val expected = table.columns +
                (if (table.name == "entity") extraEntity else emptyList())
            val actual = values(c, "SELECT name FROM pragma_table_info('" +
                table.name + "') ORDER BY cid")
            // SQLite ALTER TABLE ADD COLUMN correctly APPENDS historical fields.
            // For migrated v1–v8 vaults, cid order differs from fresh v9 DDL.
            // Verify all exact original names, count/uniqueness, and mandatory
            // financial/identity types without demanding impossible cid order.
            assertEquals("Lost or invented field in " + table.name,
                expected.toSet(), actual.toSet())
            assertEquals("Duplicate or missing field in " + table.name,
                expected.size, actual.size)
            assertEquals(0L, number(c, "SELECT count(*) FROM pragma_table_info('" +
                table.name + "') WHERE UPPER(type)='REAL'"))
            for ((field, requiredType) in listOf(
                "entity" to ("entity_id" to "TEXT"),
                "holding" to ("entity_id" to "TEXT"),
                "holding" to ("current_price_minor" to "INTEGER"),
                "ledger_event" to ("quantity_scaled" to "INTEGER"),
                "ledger_event" to ("total_amount_minor" to "INTEGER"),
                "ledger_event" to ("charges_minor" to "INTEGER"),
            )) {
                if (table.name == field) {
                    val (name, type) = requiredType
                    assertEquals("Wrong persisted affinity for " + table.name + "." + name,
                        listOf(type), values(c,
                            "SELECT UPPER(type) FROM pragma_table_info('" + table.name +
                                "') WHERE name='" + name + "'"))
                }
            }
            actual.size
        }
        assertEquals(217, cols)
        assertEquals(13L, number(c, "SELECT count(*) FROM sqlite_master " +
            "WHERE type='index' AND name NOT LIKE 'sqlite_%'"))
        assertEquals(setOf("tg_ledger_event__no_update", "tg_ledger_event__no_delete",
            "tg_tax_rule_set__no_update"),
            values(c, "SELECT name FROM sqlite_master WHERE type='trigger'").toSet())
        assertThrows(SQLException::class.java) {
            c.createStatement().use {
                it.executeUpdate("UPDATE ledger_event SET notes='edited' " +
                    "WHERE ledger_event_id='synthetic-event'")
            }
        }
        assertThrows(SQLException::class.java) {
            c.createStatement().use {
                it.executeUpdate("DELETE FROM ledger_event " +
                    "WHERE ledger_event_id='synthetic-event'")
            }
        }
        assertThrows(SQLException::class.java) {
            c.createStatement().use {
                it.executeUpdate("INSERT INTO account(account_id,entity_id,account_type," +
                    "custodian,created_at) VALUES " +
                    "('bad-account','unknown-owner','BROKER','Synthetic','2026-10-16')")
            }
        }
        assertEquals(0L, number(c, "SELECT count(*) FROM account"))
        assertEquals(listOf("gmail_seen", "listed_security", "vault_account"),
            values(c, "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name IN ('gmail_seen','listed_security','vault_account') ORDER BY name"))
        assertEquals(listOf("cas_id","pran","gmail_sync","cas_id_source",
            "pran_source","cas_sync_after","pran_sync_after"),
            values(c, "SELECT name FROM pragma_table_info('entity') WHERE cid >= 14 ORDER BY cid"))
        assertRecords(c)
    }

    @Test fun frozenHistoryProducesDeterministicNonDestructiveVersionPlans() {
        val full = SchemaV9MigrationPlan.plan(1)
        assertEquals((2..9).toList(), full.map { it.version })
        assertEquals(SchemaV9MigrationPlan.Kind.NO_DDL, full.first().kind)
        assertEquals("SCHEMA_V9_COLUMNS", full.last().sourceSymbol)
        for (v in 1..8) {
            assertEquals((v + 1..9).toList(),
                SchemaV9MigrationPlan.plan(v).map { it.version })
        }
        assertTrue(SchemaV9MigrationPlan.plan(9).isEmpty())
        for (bad in listOf(-1, 0, 10, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                SchemaV9MigrationPlan.plan(bad)
            }
        }
    }

    @Test fun allSupportedPriorVersionMarkersMigrateAndPreserveSyntheticOwnersAndMoney() {
        for (version in 1..8) {
            val (file, key) = newFixture()
            try {
                TestOnlyV9Migration.connect(file, key).use { c ->
                    TestOnlyV9Migration.seedPriorVersion(c, version,
                        omitLegacyOptional = version < 7)
                    TestOnlyV9Migration.seedSyntheticRecords(c)
                    val before = TestOnlyV9Migration.rowsFingerprint(c)
                    TestOnlyV9Migration.migrate(c)
                    assertV9(c)
                    val after = TestOnlyV9Migration.rowsFingerprint(c)
                    assertEquals("Changed source values at v" + version,
                        before.filterNot { it == version.toString() },
                        after.filterNot { it == "9" })
                }
                TestOnlyV9Migration.connect(file, key).use { c ->
                    c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                    assertV9(c)
                }
            } finally { file.delete() }
        }
    }

    @Test fun exactEncryptedBackupCopyRetainsUnknownFieldsOwnerAndScaledIntegers() {
        val (file, key) = newFixture()
        val v8Copy = Files.createTempFile("m24-pre-migration-backup-", ".db").toFile()
        val v9Copy = Files.createTempFile("m24-post-migration-backup-", ".db").toFile()
        try {
            TestOnlyV9Migration.connect(file, key).use { c ->
                TestOnlyV9Migration.seedPriorVersion(c, 8)
                TestOnlyV9Migration.seedSyntheticRecords(c)
                assertRecords(c)
            }
            file.copyTo(v8Copy, overwrite = true)
            TestOnlyV9Migration.connect(file, key).use { c ->
                TestOnlyV9Migration.migrate(c)
                assertV9(c)
            }
            file.copyTo(v9Copy, overwrite = true)
            TestOnlyV9Migration.connect(v8Copy, key).use { c ->
                c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                assertEquals(8L, number(c, "PRAGMA user_version"))
                assertRecords(c)
            }
            TestOnlyV9Migration.connect(v9Copy, key).use { c ->
                c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                assertV9(c)
                assertFalse(v9Copy.readBytes().copyOf(16).toString(Charsets.UTF_8)
                    .startsWith("SQLite format 3"))
            }
        } finally {
            file.delete()
            v8Copy.delete()
            v9Copy.delete()
        }
    }

    @Test fun injectedDdlFailureRollsBackSchemaVersionAndEverySyntheticRecord() {
        val (file, key) = newFixture()
        try {
            TestOnlyV9Migration.connect(file, key).use { c ->
                TestOnlyV9Migration.seedPriorVersion(c, 6, omitLegacyOptional = true)
                TestOnlyV9Migration.seedSyntheticRecords(c)
                val oldRows = TestOnlyV9Migration.rowsFingerprint(c)
                val oldDefinitions = TestOnlyV9Migration.definitionFingerprint(c)
                assertThrows(IllegalStateException::class.java) {
                    TestOnlyV9Migration.migrate(c, failAfterDdl = 1)
                }
                assertEquals(6L, number(c, "PRAGMA user_version"))
                assertEquals(oldRows, TestOnlyV9Migration.rowsFingerprint(c))
                assertEquals(oldDefinitions, TestOnlyV9Migration.definitionFingerprint(c))
                assertRecords(c)
                // Retrying a clean migration is safe and never duplicates columns.
                TestOnlyV9Migration.migrate(c)
                assertV9(c)
            }
        } finally { file.delete() }
    }

    @Test fun corruptMetadataUnknownVersionsAndIncompatibleSchemasFailClosed() {
        val (f1, k1) = newFixture()
        val (f2, k2) = newFixture()
        val (f3, k3) = newFixture()
        try {
            TestOnlyV9Migration.connect(f1, k1).use { c ->
                TestOnlyV9Migration.seedPriorVersion(c, 7)
                TestOnlyV9Migration.seedSyntheticRecords(c)
                val previous = TestOnlyV9Migration.rowsFingerprint(c)
                c.createStatement().use {
                    it.executeUpdate("UPDATE vault_meta SET value='999' " +
                        "WHERE key='schema_version'")
                }
                val before = TestOnlyV9Migration.definitionFingerprint(c)
                assertThrows(IllegalArgumentException::class.java) {
                    TestOnlyV9Migration.migrate(c)
                }
                assertEquals(before, TestOnlyV9Migration.definitionFingerprint(c))
                assertRecords(c)
                assertEquals(7L, number(c, "PRAGMA user_version"))
                assertEquals(999L, number(c,
                    "SELECT CAST(value AS INTEGER) FROM vault_meta " +
                        "WHERE key='schema_version'"))
                assertEquals(previous.size, TestOnlyV9Migration.rowsFingerprint(c).size)
            }
            TestOnlyV9Migration.connect(f2, k2).use { c ->
                TestOnlyV9Migration.seedPriorVersion(c, 8)
                c.createStatement().use {
                    it.execute("PRAGMA user_version=10")
                    it.executeUpdate("UPDATE vault_meta SET value='10' " +
                        "WHERE key='schema_version'")
                }
                assertThrows(IllegalArgumentException::class.java) {
                    TestOnlyV9Migration.migrate(c)
                }
                assertEquals(10L, number(c, "PRAGMA user_version"))
            }
            TestOnlyV9Migration.connect(f3, k3).use { c ->
                TestOnlyV9Migration.seedPriorVersion(c, 7)
                c.createStatement().use { it.execute("DROP TABLE instrument") }
                val before = TestOnlyV9Migration.definitionFingerprint(c)
                assertThrows(IllegalArgumentException::class.java) {
                    TestOnlyV9Migration.migrate(c)
                }
                assertEquals(before, TestOnlyV9Migration.definitionFingerprint(c))
                assertEquals(7L, number(c, "PRAGMA user_version"))
            }
            // Never silently create or reset a database on an invalid key.
            assertThrows(SQLException::class.java) {
                TestOnlyV9Migration.connect(f2, "wrong-key").use { c ->
                    c.createStatement().use { it.execute("PRAGMA user_version") }
                }
            }
        } finally {
            f1.delete(); f2.delete(); f3.delete()
        }
    }

    @Test fun corruptedCiphertextIsRejectedWithoutResettingTheSourceFile() {
        val (file, key) = newFixture()
        try {
            TestOnlyV9Migration.connect(file, key).use { c ->
                TestOnlyV9Migration.seedPriorVersion(c, 8)
                TestOnlyV9Migration.seedSyntheticRecords(c)
                assertRecords(c)
            }
            val untouchedBackup = Files.createTempFile("m24-safe-archive-", ".db").toFile()
            try {
                file.copyTo(untouchedBackup, overwrite = true)
                // Mutate only a disposable *test copy*. No real encrypted vault involved.
                file.writeBytes(ByteArray(8192) { 0x5a.toByte() })
                assertThrows(SQLException::class.java) {
                    TestOnlyV9Migration.connect(file, key).use { c ->
                        TestOnlyV9Migration.migrate(c)
                    }
                }
                TestOnlyV9Migration.connect(untouchedBackup, key).use { c ->
                    c.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
                    assertEquals(8L, number(c, "PRAGMA user_version"))
                    assertRecords(c)
                }
            } finally { untouchedBackup.delete() }
        } finally { file.delete() }
    }
}
