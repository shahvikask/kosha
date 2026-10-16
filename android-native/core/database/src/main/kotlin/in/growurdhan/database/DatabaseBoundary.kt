package `in`.growurdhan.database

/**
 * The database module still opens nothing. Table names come from the
 * frozen schema v9 catalog. SQLCipher is not linked.
 */
object DatabaseBoundary {
    const val OPENS_DATABASE = false
    const val SQLCIPHER_LINKED = EncryptedSchemaBoundary.SQLCIPHER_LINKED
    val tableNames: List<String> = SchemaV9Catalog.tableNames
}
