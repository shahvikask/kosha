package `in`.growurdhan.database

/**
 * M00 module boundary. SQLCipher, migrations, and the schema-v9 tables
 * belong to M24. This module opens nothing and defines no table.
 */
object DatabaseBoundary {
    const val OPENS_DATABASE = false
    val tableNames: List<String> = emptyList()
}
