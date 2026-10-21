package `in`.growurdhan.redevelopment

import `in`.growurdhan.database.NativeV9ExportCoverage
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Read-only SQLCipher export-coverage audit. Only available behind the
 * current device-admitted and password-bound NativeAccountRepository session.
 * This does NOT generate a backup, authorize SAF, or expose finance contents.
 */
internal class NativeV9ExportReadiness(private val accounts: NativeAccountRepository) {
    fun audit(email: String): NativeV9ExportCoverage.Receipt =
        accounts.withUnlockedVault(email) { db ->
            requireCipherAndIntegrity(db)
            val names = mutableListOf<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' ORDER BY name", null).use { c ->
                while (c.moveToNext()) names += c.getString(0)
            }
            val valid = Regex("[a-z][a-z0-9_]*")
            val tables = names.map { table ->
                require(valid.matches(table)) {
                    "Unexpected native financial schema identifier"
                }
                val columns = mutableListOf<String>()
                db.rawQuery("PRAGMA table_info('$table')", null).use { c ->
                    while (c.moveToNext()) columns += c.getString(1)
                }
                val count = db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                    require(c.moveToFirst() && !c.isNull(0))
                    val count = c.getLong(0)
                    require(!c.moveToNext())
                    count
                }
                NativeV9ExportCoverage.Table(table, columns, count)
            }
            fun keys(table: String): List<String> {
                val data = mutableListOf<String>()
                db.rawQuery("SELECT key FROM $table ORDER BY key", null).use { c ->
                    while (c.moveToNext()) data += c.getString(0)
                }
                return data
            }
            NativeV9ExportCoverage.requireLosslessCandidate(
                tables, keys("setting"), keys("vault_meta"))
        }

    private fun requireCipherAndIntegrity(db: SQLiteDatabase) {
        db.rawQuery("PRAGMA cipher_version", null).use { c ->
            require(c.moveToFirst() && !c.getString(0).isNullOrBlank()) {
                "Native export requires SQLCipher"
            }
        }
        db.rawQuery("PRAGMA user_version", null).use { c ->
            require(c.moveToFirst() && c.getLong(0) == 9L) {
                "Native export requires canonical schema v9"
            }
        }
        db.rawQuery("SELECT value FROM vault_meta WHERE key='schema_version'",
            null).use { c ->
            require(c.moveToFirst() && c.getString(0) == "9" &&
                !c.moveToNext()) {
                "Native schema marker is incompatible"
            }
        }
        db.rawQuery("PRAGMA quick_check", null).use { c ->
            require(c.moveToFirst() && c.getString(0) == "ok" &&
                !c.moveToNext()) {
                "Native encrypted vault integrity failed"
            }
        }
        db.rawQuery("PRAGMA foreign_key_check", null).use { c ->
            require(!c.moveToFirst()) { "Dangling native vault owners" }
        }
    }
}
