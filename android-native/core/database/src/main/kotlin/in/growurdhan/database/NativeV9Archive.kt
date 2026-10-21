package `in`.growurdhan.database

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Native-only v9 format. NOT GUDVLT01/KOSHA1 and NOT a legacy financial projection.
 * Captures all 18 canonical tables, additional table identities, complete SQLite
 * schema declarations, and every cell's SQLite STORAGE CLASS in encrypted memory.
 * A decoder never executes snapshot SQL: restore must check its trusted schema.
 */
object NativeV9Archive {
    const val LIMIT = 64 * 1024 * 1024
    private const val MAX_TABLES = 128
    private const val MAX_COLUMNS = 512
    private const val MAX_ROWS = 200_000
    private const val MAX_CELLS = 2_000_000L
    private const val ROUNDS = 600_000
    private val payloadMagic = "GV9SNAP1".toByteArray(Charsets.US_ASCII)
    private val envelopeMagic = "GUDNAT01".toByteArray(Charsets.US_ASCII)
    private val safeName = Regex("[A-Za-z_][A-Za-z_0-9]{0,127}")

    data class Schema(val type: String, val name: String, val table: String,
        val sql: String?)
    data class Column(val name: String, val type: String, val notNull: Boolean,
        val primaryKey: Int, val defaultSql: String?)

    /** tag: 0=NULL, 1=INTEGER, 2=TEXT, 3=BLOB, 4=IEEE754 REAL raw bits. */
    class Cell private constructor(val tag: Int, val payload: ByteArray) {
        init {
            require(tag in 0..4 && payload.size <= LIMIT &&
                (tag !in setOf(0, 1, 4) || payload.size == (if (tag == 0) 0 else 8)))
        }
        override fun equals(other: Any?): Boolean = other is Cell &&
            tag == other.tag && payload.contentEquals(other.payload)
        override fun hashCode(): Int = 31 * tag + payload.contentHashCode()
        companion object {
            fun nullValue() = Cell(0, byteArrayOf())
            fun integer(value: Long) = Cell(1, ByteBuffer.allocate(8).putLong(value).array())
            fun text(value: String) = Cell(2, value.toByteArray(Charsets.UTF_8))
            fun blob(value: ByteArray) = Cell(3, value.copyOf())
            fun real(value: Double) = Cell(4, ByteBuffer.allocate(8)
                .putLong(java.lang.Double.doubleToRawLongBits(value)).array())
            internal fun decoded(tag: Int, bytes: ByteArray) = Cell(tag, bytes)
        }
        fun boundValue(): Any? = when (tag) {
            0 -> null
            1 -> ByteBuffer.wrap(payload).long
            2 -> strictUtf8(payload)
            3 -> payload.copyOf()
            4 -> java.lang.Double.longBitsToDouble(ByteBuffer.wrap(payload).long)
            else -> error("Unrecognized SQLite storage class")
        }
    }
    data class Table(val name: String, val columns: List<Column>,
        val rows: List<List<Cell>>)
    data class Snapshot(val objects: List<Schema>, val tables: List<Table>)

    private fun strictUtf8(bytes: ByteArray): String =
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()

    private fun checkSnapshot(s: Snapshot) {
        require(s.tables.size in SchemaV9Catalog.tables.size..MAX_TABLES)
        require(s.tables.map { it.name }.toSet().containsAll(SchemaV9Catalog.tableNames))
        require(s.tables.map { it.name }.distinct().size == s.tables.size)
        require(s.objects.size in s.tables.size..512)
        require(s.objects.map { it.type + ":" + it.name }.distinct().size == s.objects.size)
        for (obj in s.objects) {
            require(obj.type in setOf("table", "index", "trigger", "view") &&
                safeName.matches(obj.name) && safeName.matches(obj.table) &&
                (obj.sql == null || obj.sql.length <= 128_000))
        }
        require(s.objects.filter { it.type == "table" }.map { it.name }.toSet() ==
            s.tables.map { it.name }.toSet())
        var cells = 0L
        for (table in s.tables) {
            require(safeName.matches(table.name) &&
                table.columns.size in 1..MAX_COLUMNS &&
                table.columns.map { it.name }.distinct().size == table.columns.size &&
                table.rows.size <= MAX_ROWS)
            for (col in table.columns) require(safeName.matches(col.name) &&
                col.type.length <= 128 && col.primaryKey in 0..MAX_COLUMNS &&
                (col.defaultSql == null || col.defaultSql.length <= 128_000))
            for (row in table.rows) {
                require(row.size == table.columns.size)
                cells += row.size
                require(cells <= MAX_CELLS) { "Oversized snapshot cell inventory" }
                for (value in row) {
                    require(value.tag in 0..4 && value.payload.size <= LIMIT)
                    if (value.tag == 2) strictUtf8(value.payload)
                }
            }
        }
    }

    /** Deterministic typed binary encoding. Plain bytes must NEVER be persisted. */
    fun encodeInMemory(snapshot: Snapshot): ByteArray {
        checkSnapshot(snapshot)
        val memory = ByteArrayOutputStream()
        val out = DataOutputStream(memory)
        fun bytes(value: ByteArray) {
            require(value.size <= LIMIT && memory.size() + value.size.toLong() + 4 <= LIMIT)
            out.writeInt(value.size)
            out.write(value)
        }
        fun word(value: String) = bytes(value.toByteArray(Charsets.UTF_8))
        out.write(payloadMagic)
        out.writeInt(9)
        out.writeInt(snapshot.objects.size)
        snapshot.objects.forEach { s ->
            word(s.type); word(s.name); word(s.table)
            out.writeBoolean(s.sql != null)
            if (s.sql != null) word(s.sql)
        }
        out.writeInt(snapshot.tables.size)
        snapshot.tables.forEach { t ->
            word(t.name); out.writeInt(t.columns.size)
            t.columns.forEach { c ->
                word(c.name); word(c.type); out.writeBoolean(c.notNull)
                out.writeInt(c.primaryKey); out.writeBoolean(c.defaultSql != null)
                if (c.defaultSql != null) word(c.defaultSql)
            }
            out.writeInt(t.rows.size)
            t.rows.forEach { row -> row.forEach { cell ->
                out.writeByte(cell.tag); bytes(cell.payload)
            } }
        }
        out.flush()
        require(memory.size() <= LIMIT)
        return memory.toByteArray()
    }

    /** Strict, independently parsed inverse; no SQL statements are executed. */
    fun decodeInMemory(data: ByteArray): Snapshot {
        require(data.size in 24..LIMIT) { "Invalid native snapshot size" }
        val stream = ByteArrayInputStream(data)
        val input = DataInputStream(stream)
        fun bytes(): ByteArray {
            val n = input.readInt()
            require(n in 0..LIMIT && n <= stream.available()) {
                "Truncated or oversized native archive field"
            }
            return ByteArray(n).also { input.readFully(it) }
        }
        fun word(): String = strictUtf8(bytes())
        fun count(max: Int): Int = input.readInt().also { require(it in 0..max) }
        require(ByteArray(8).also { input.readFully(it) }.contentEquals(payloadMagic))
        require(input.readInt() == 9) { "Unsupported native snapshot schema" }
        val objects = (0 until count(512)).map {
            val type = word(); val name = word(); val table = word()
            Schema(type, name, table, if (input.readBoolean()) word() else null)
        }
        var cells = 0L
        val tables = (0 until count(MAX_TABLES)).map {
            val name = word()
            val columns = (0 until count(MAX_COLUMNS)).map {
                Column(word(), word(), input.readBoolean(), count(MAX_COLUMNS),
                    if (input.readBoolean()) word() else null)
            }
            val rows = (0 until count(MAX_ROWS)).map {
                (0 until columns.size).map {
                    cells++
                    require(cells <= MAX_CELLS)
                    Cell.decoded(input.readUnsignedByte(), bytes())
                }
            }
            Table(name, columns, rows)
        }
        require(stream.available() == 0) { "Trailing unauthenticated snapshot content" }
        return Snapshot(objects, tables).also(::checkSnapshot)
    }

    private fun material(email: String, password: CharArray): CharArray {
        require(email.trim().length in 3..320 && email.contains('@') &&
            password.size in 12..1024)
        val prefix = ("GrowUrDhan-Native-v1\n" +
            email.trim().lowercase(Locale.ROOT) + "\n").toCharArray()
        return (prefix + password).also { prefix.fill('\u0000') }
    }

    private fun key(email: String, password: CharArray, salt: ByteArray): ByteArray {
        val chars = material(email, password)
        val spec = PBEKeySpec(chars, salt, ROUNDS, 256)
        chars.fill('\u0000')
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
    }

    /** Backup password is separate from login. Caller owns and clears CharArray. */
    fun encrypt(snapshot: Snapshot, email: String, backupPassword: CharArray): ByteArray {
        val plaintext = encodeInMemory(snapshot)
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val header = ByteBuffer.allocate(40).put(envelopeMagic).putInt(ROUNDS)
            .put(salt).put(nonce).array()
        val secret = key(email, backupPassword, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(secret, "AES"),
                GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val encrypted = cipher.doFinal(plaintext)
            require(encrypted.size.toLong() + header.size <= LIMIT + 56L)
            return header + encrypted
        } finally { secret.fill(0); plaintext.fill(0) }
    }

    fun decrypt(envelope: ByteArray, email: String, backupPassword: CharArray): Snapshot {
        require(envelope.size in 56..(LIMIT + 56))
        val header = envelope.copyOfRange(0, 40)
        val bb = ByteBuffer.wrap(header)
        require(ByteArray(8).also { bb.get(it) }.contentEquals(envelopeMagic))
        require(bb.int == ROUNDS) { "Unsupported native KDF parameters" }
        val salt = ByteArray(16).also { bb.get(it) }
        val nonce = ByteArray(12).also { bb.get(it) }
        val secret = key(email, backupPassword, salt)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(secret, "AES"),
                GCMParameterSpec(128, nonce))
            cipher.updateAAD(header)
            val plain = cipher.doFinal(envelope, 40, envelope.size - 40)
            return try { decodeInMemory(plain) }
            finally { plain.fill(0) }
        } finally { secret.fill(0) }
    }
}
