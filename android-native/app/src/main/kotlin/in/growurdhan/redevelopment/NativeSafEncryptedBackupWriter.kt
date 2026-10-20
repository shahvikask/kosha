package `in`.growurdhan.redevelopment

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import `in`.growurdhan.database.LegacyBackupEnvelopeReader
import java.io.FileOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.json.JSONObject

/**
 * Genuine Android SAF encrypted ARCHIVE WRITER, not a native financial
 * snapshot exporter or background scheduler. The incoming GUDVLT01 archive
 * must authenticate with the user's ephemeral password and be valid frozen
 * GUDVAULT-v2 JSON before anything is created on external storage.
 *
 * Never stores vault password/PAN/source JSON in the provider, preferences
 * or logs; published bytes are AES-GCM ciphertext. Providers lacking atomic
 * rename/known length refuse completed publication; pending artefact is
 * retained for owner review rather than silently deleting forensic evidence.
 */
internal class NativeSafEncryptedBackupWriter(private val context: Context) {
    data class Receipt(val uri: Uri, val bytesWritten: Int,
        val retained: Int, val pruneComplete: Boolean)
    internal data class Entry(val id: String, val name: String,
        val lastModified: Long)

    companion object {
        const val KEEP_LAST = 5
        private val ownFile = Regex(
            "GrowUrDhan-Native-[0-9]{8}T[0-9]{6}Z-[0-9a-f]{8}\\.kosha")
        private val timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)

        fun newName(now: Instant = Instant.now()): String {
            val random = ByteArray(4).also { SecureRandom().nextBytes(it) }
                .joinToString("") { "%02x".format(it) }
            return "GrowUrDhan-Native-" + timestamp.format(now) +
                "-" + random + ".kosha"
        }

        /** Never prune another application/user's file or unverified names. */
        fun obsolete(entries: List<Entry>, keep: Int = KEEP_LAST): List<Entry> {
            require(keep in 1..100)
            return entries.filter { ownFile.matches(it.name) }
                .sortedWith(compareByDescending<Entry> { it.lastModified }
                    .thenByDescending { it.name })
                .drop(keep)
        }
    }

    private fun validateEncryptedArchive(bytes: ByteArray, email: String,
        password: String) {
        require(bytes.size in 56..(64 * 1024 * 1024))
        val json = LegacyBackupEnvelopeReader.decodeGUDVLT01(
            bytes, email, password)
        val document = JSONObject(json)
        require(document.getString("kind") == "GUDVAULT" &&
            document.getInt("version") == 2 &&
            document.has("holdings") && document.has("ledgerEvents") &&
            document.has("familyMembers") && document.has("account")) {
            "Not an authenticated frozen full-financial backup"
        }
    }

    private fun selectedTree(tree: Uri): Uri {
        require(tree.scheme == "content")
        val grant = context.contentResolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && it.isWritePermission
        }
        require(grant) { "SAF folder grant requires renewed owner permission" }
        return DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree))
    }

    private fun exactSize(resolver: ContentResolver, uri: Uri): Long =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE),
            null, null, null)?.use { cur ->
            if (cur.moveToFirst() && !cur.isNull(0)) cur.getLong(0) else -1L
        } ?: -1L

    private fun childEntries(tree: Uri): List<Entry> {
        val document = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree))
        val fields = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val records = mutableListOf<Entry>()
        context.contentResolver.query(document, fields, null, null, null).use { cur ->
            requireNotNull(cur) { "Backup folder cannot be enumerated safely" }
            while (cur.moveToNext()) {
                records += Entry(cur.getString(0), cur.getString(1),
                    if (cur.isNull(2)) 0L else cur.getLong(2))
            }
        }
        return records
    }

    /**
     * Call only after foreground vault reauthentication and owner-selected
     * OpenDocumentTree grant. Password not retained across Activity lifecycles.
     * Automatic on-open/CAS dispatch remains unsupported until the financial
     * serializer and scheduling consent are production-complete.
     */
    fun publish(email: String, password: String, tree: Uri,
        encryptedArchive: ByteArray, explicitOwnerApproval: Boolean,
        approvePruning: Boolean): Receipt {
        require(explicitOwnerApproval) { "Owner must approve encrypted SAF export" }
        validateEncryptedArchive(encryptedArchive, email, password)
        val root = selectedTree(tree)
        val resolver = context.contentResolver
        val name = newName()
        val pending = requireNotNull(DocumentsContract.createDocument(
            resolver, root, "application/octet-stream", name + ".pending")) {
            "Cannot create encrypted pending backup"
        }
        resolver.openFileDescriptor(pending, "w")?.use { fd ->
            FileOutputStream(fd.fileDescriptor).use { out ->
                out.write(encryptedArchive)
                out.flush()
                out.fd.sync()
            }
        } ?: error("Cannot open encrypted backup destination")
        require(exactSize(resolver, pending) == encryptedArchive.size.toLong()) {
            "Encrypted pending backup length mismatch"
        }
        val final = requireNotNull(
            DocumentsContract.renameDocument(resolver, pending, name)) {
            "Provider does not support verified backup rename"
        }
        require(exactSize(resolver, final) == encryptedArchive.size.toLong()) {
            "Final encrypted backup length mismatch"
        }
        var completed = true
        var retained = 0
        if (approvePruning) {
            val entries = childEntries(tree)
            for (extra in obsolete(entries)) {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, extra.id)
                if (!runCatching {
                    DocumentsContract.deleteDocument(resolver, uri)
                }.getOrDefault(false)) completed = false
            }
            retained = childEntries(tree).count { ownFile.matches(it.name) }
        } else {
            retained = childEntries(tree).count { ownFile.matches(it.name) }
        }
        return Receipt(final, encryptedArchive.size, retained, completed)
    }
}
