package `in`.growurdhan.database

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class LegacyMigrationColumnContractTest {
    @Test fun frozenV3NotNullDefaultMustMatchOriginalDeclaration() {
        val frozen = LegacyMigrationColumnContract.parse(
            "ALTER TABLE entity ADD COLUMN relation TEXT NOT NULL DEFAULT 'Self';")
        assertEquals("entity", frozen.table)
        assertEquals("relation", frozen.column)
        assertEquals("TEXT", frozen.type)
        assertTrue(frozen.notNull)
        assertEquals("'Self'", frozen.defaultSql)
        LegacyMigrationColumnContract.requireMatches(frozen,
            LegacyMigrationColumnContract.Observed("TEXT", true, "'Self'", 0))
        for (invalid in listOf(
            LegacyMigrationColumnContract.Observed("TEXT", false, "'Self'", 0),
            LegacyMigrationColumnContract.Observed("TEXT", true, null, 0),
            LegacyMigrationColumnContract.Observed("TEXT", true, "'Spouse'", 0),
            LegacyMigrationColumnContract.Observed("INTEGER", true, "'Self'", 0),
            LegacyMigrationColumnContract.Observed("TEXT", true, "'Self'", 1)
        )) assertThrows(IllegalArgumentException::class.java) {
            LegacyMigrationColumnContract.requireMatches(frozen, invalid)
        }
    }

    @Test fun frozenV8IntegerDefaultMustNotBeSilentlyAcceptedAsNullable() {
        val frozen = LegacyMigrationColumnContract.parse(
            "ALTER TABLE entity ADD COLUMN gmail_sync INTEGER NOT NULL DEFAULT 0;")
        LegacyMigrationColumnContract.requireMatches(frozen,
            LegacyMigrationColumnContract.Observed("INTEGER", true, "0", 0))
        assertThrows(IllegalArgumentException::class.java) {
            LegacyMigrationColumnContract.requireMatches(frozen,
                LegacyMigrationColumnContract.Observed("INTEGER", false, "0", 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LegacyMigrationColumnContract.requireMatches(frozen,
                LegacyMigrationColumnContract.Observed("INTEGER", true, null, 0))
        }
    }

    @Test fun nullableOriginalColumnMustStayNullableWithNoIntroducedDefault() {
        val frozen = LegacyMigrationColumnContract.parse(
            "ALTER TABLE holding ADD COLUMN acquired_on TEXT;")
        LegacyMigrationColumnContract.requireMatches(frozen,
            LegacyMigrationColumnContract.Observed("text", false, null, 0))
        for (invalid in listOf(
            LegacyMigrationColumnContract.Observed("TEXT", true, null, 0),
            LegacyMigrationColumnContract.Observed("TEXT", false, "'1970-01-01'", 0)
        )) assertThrows(IllegalArgumentException::class.java) {
            LegacyMigrationColumnContract.requireMatches(frozen, invalid)
        }
    }

    @Test fun everyCheckedInFrozenAlterStatementUsesSupportedExactContract() {
        var location = File(System.getProperty("user.dir"))
        var asset: File? = null
        repeat(10) {
            val candidate = File(location,
                "android-native/app/src/main/assets/m24-frozen-upgrades.sql")
            if (candidate.isFile) asset = candidate
            location = location.parentFile ?: location
        }
        val frozen = requireNotNull(asset) {
            "Frozen migration asset unavailable to required migration test"
        }.readLines().filter { it.trim().startsWith("ALTER TABLE ") }
        assertEquals(45, frozen.size)
        val parsed = frozen.map(LegacyMigrationColumnContract::parse)
        assertEquals(45, parsed.map { it.table to it.column }.distinct().size)
        assertTrue(parsed.any { it.column == "relation" &&
            it.notNull && it.defaultSql == "'Self'" })
        assertTrue(parsed.any { it.column == "gmail_sync" &&
            it.notNull && it.defaultSql == "0" })
    }

    @Test fun unknownConstraintsOrDangerousStatementsFailBeforeAccessToDatabase() {
        for (sql in listOf(
            "DROP TABLE ledger_event;",
            "ALTER TABLE entity DROP COLUMN pan_masked;",
            "ALTER TABLE entity ADD COLUMN shadow TEXT CHECK(1=1);",
            "ALTER TABLE entity ADD COLUMN shadow TEXT PRIMARY KEY;",
            "ALTER TABLE entity ADD COLUMN shadow TEXT DEFAULT (hex(randomblob(8)));"
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                LegacyMigrationColumnContract.parse(sql)
            }
        }
    }
}
