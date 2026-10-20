package `in`.growurdhan.database

/**
 * Strict additive-column compatibility check against the immutable frozen
 * ALTER TABLE statements. A column which already existed in a reconstructed
 * v1..v8 database is skipped ONLY if type, nullability, default and PK status
 * equal the exact historical DDL. A matching name/type alone is insufficient.
 *
 * This is a pure verifier: the caller queries PRAGMA table_info inside its
 * SQLCipher migration transaction; no plaintext database is created here.
 */
object LegacyMigrationColumnContract {
    data class Addition(val table: String, val column: String,
        val type: String, val notNull: Boolean, val defaultSql: String?)
    data class Observed(val type: String, val notNull: Boolean,
        val defaultSql: String?, val primaryKeyPosition: Int)

    private val ddl = Regex(
        """^ALTER\s+TABLE\s+([a-z_]+)\s+ADD\s+COLUMN\s+([a-z_]+)\s+([A-Z]+)(.*)$""",
        RegexOption.IGNORE_CASE)
    private val default = Regex("""\bDEFAULT\s+('(?:''|[^'])*'|[-+]?[0-9]+)\s*$""",
        RegexOption.IGNORE_CASE)

    fun parse(statement: String): Addition {
        val match = ddl.matchEntire(statement.trim().removeSuffix(";"))
            ?: throw IllegalArgumentException("Unexpected frozen additive migration DDL")
        val tail = match.groupValues[4].trim()
        require(!tail.contains(Regex("\\bPRIMARY\\s+KEY\\b", RegexOption.IGNORE_CASE))) {
            "Unsupported primary-key migration"
        }
        val nullable = Regex("""^NOT\s+NULL(?:\s+|$)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(tail)
        val defaultMatch = default.find(tail)
        val leftover = (if (defaultMatch == null) tail
            else tail.removeRange(defaultMatch.range)).trim()
        require(leftover.isEmpty() ||
            leftover.matches(Regex("""NOT\s+NULL""", RegexOption.IGNORE_CASE))) {
            "Unsupported frozen ALTER TABLE column constraints"
        }
        return Addition(match.groupValues[1], match.groupValues[2],
            match.groupValues[3].uppercase(java.util.Locale.ROOT),
            nullable, defaultMatch?.groupValues?.get(1))
    }

    fun requireMatches(frozen: Addition, actual: Observed) {
        require(actual.type.uppercase(java.util.Locale.ROOT) == frozen.type &&
            actual.notNull == frozen.notNull &&
            actual.primaryKeyPosition == 0 &&
            actual.defaultSql == frozen.defaultSql) {
            "Incompatible historical field ${frozen.table}.${frozen.column}"
        }
    }
}
