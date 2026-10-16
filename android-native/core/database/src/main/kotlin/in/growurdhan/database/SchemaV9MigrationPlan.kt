package `in`.growurdhan.database

/**
 * Pure, read-only schema upgrade contract extracted from frozen schema.ts v9.
 * No SQL execution, storage keys, database instance or production vault opener.
 * The only supported target is schema v9. Unknown/corrupt and future versions
 * must fail closed, never trigger a destructive database reset.
 */
object SchemaV9MigrationPlan {
    const val TARGET = 9

    enum class Kind { NO_DDL, ADD_COLUMNS, CREATE_TABLES_AND_INDEXES, MIXED }

    data class Phase(val version: Int, val kind: Kind, val sourceSymbol: String)

    private val phases = listOf(
        Phase(2, Kind.NO_DDL, "SCHEMA_V1"),
        Phase(3, Kind.ADD_COLUMNS, "SCHEMA_V3_COLUMNS"),
        Phase(4, Kind.ADD_COLUMNS, "SCHEMA_V4_COLUMNS"),
        Phase(5, Kind.CREATE_TABLES_AND_INDEXES, "SCHEMA_V5_SQL"),
        Phase(6, Kind.CREATE_TABLES_AND_INDEXES, "SCHEMA_V6_SQL"),
        Phase(7, Kind.ADD_COLUMNS, "SCHEMA_V7_COLUMNS"),
        Phase(8, Kind.MIXED, "SCHEMA_V8_COLUMNS+SCHEMA_V8_SQL"),
        Phase(9, Kind.ADD_COLUMNS, "SCHEMA_V9_COLUMNS"),
    )

    fun plan(existingVersion: Int): List<Phase> {
        require(existingVersion in 1..TARGET) {
            "Unsupported schema version; do not overwrite or reset this database."
        }
        return phases.filter { it.version > existingVersion }
    }
}
