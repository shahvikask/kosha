package `in`.growurdhan.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DatabaseBoundaryTest {
    @Test
    fun boundaryDoesNotOpenADatabase() {
        assertFalse(DatabaseBoundary.OPENS_DATABASE)
        assertFalse(EncryptedSchemaBoundary.OPENS_DATABASE)
        assertFalse(EncryptedSchemaBoundary.SQLCIPHER_LINKED)
        assertFalse(EncryptedSchemaBoundary.PLAINTEXT_OPEN_ALLOWED)
        assertEquals(9, EncryptedSchemaBoundary.SCHEMA_VERSION)
    }

    @Test
    fun catalogMatchesFrozenSchemaFile() {
        val file = legacySchema()
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val hash = digest.joinToString("") { "%02x".format(it) }
        assertEquals(SchemaV9Catalog.LEGACY_SHA256, hash)
        val text = file.readText()
        assertEquals(9, Regex("""SCHEMA_VERSION\s*=\s*(\d+)""").find(text)!!.groupValues[1].toInt())
        val parsed = parseTables(text)
        assertEquals(SchemaV9Catalog.tableNames, parsed.map { it.name })
        assertEquals(SchemaV9Catalog.tables, parsed)
        assertNull(parsed.find { it.name == "snapshot" })
        assertEquals(210, parsed.sumOf { it.columns.size })
    }

    private fun legacySchema(): File {
        var dir = File(System.getProperty("user.dir"))
        repeat(8) {
            val candidate = File(dir, "src/db/schema.ts")
            if (candidate.isFile) return candidate
            dir = dir.parentFile ?: error("src/db/schema.ts was not found")
        }
        error("src/db/schema.ts was not found")
    }

    private fun parseTables(text: String): List<SchemaTable> {
        val types = setOf("TEXT", "INTEGER", "BLOB", "REAL", "NUMERIC")
        return text.split("CREATE TABLE IF NOT EXISTS ").drop(1).map { part ->
            val name = part.substringBefore("(").trim()
            val columns = part.substringAfter("(").lineSequence().takeWhile { it.trim() != ");" }
                .map { it.trim().trimEnd(',') }
                .mapNotNull { line ->
                    val tokens = line.split(Regex("\\s+"))
                    val column = tokens.getOrNull(0)
                    val type = tokens.getOrNull(1)
                    if (column != null && type in types && column.matches(Regex("[a-z_][a-z0-9_]*"))) {
                        column
                    } else {
                        null
                    }
                }
                .toList()
            SchemaTable(name, columns)
        }
    }
}
