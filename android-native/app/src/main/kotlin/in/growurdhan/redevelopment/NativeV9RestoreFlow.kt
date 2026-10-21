package `in`.growurdhan.redevelopment

import android.content.Context
import android.net.Uri
import `in`.growurdhan.database.NativeV9Archive
import `in`.growurdhan.database.RestoreIdentityGate
import `in`.growurdhan.model.NativeRegistrationProof
import java.io.ByteArrayOutputStream

/**
 * Explicit native-only SAF inbound reader. No auto-backup, provider grants,
 * filesystem plaintext or compatibility downgrade to GUDVLT01/KOSHA1.
 */
internal class NativeV9RestoreFlow(private val context: Context) {
    private val recovery get() = NativeV9NewDeviceRestore(context)

    fun stage(uri: Uri): String {
        // Staging writer rechecks vault emptiness under the exclusive lock.
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                require(out.size().toLong() + n <= NativeV9Archive.LIMIT.toLong()) {
                    "Native encrypted archive exceeds the bounded limit"
                }
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } ?: error("Unable to read encrypted native backup")
        try { return recovery.stageNativeOnce(bytes) }
        finally { bytes.fill(0) }
    }

    fun preview(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent): NativeV9NewDeviceRestore.Review =
        recovery.preview(proof, password, consent)

    fun publish(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent,
        review: NativeV9NewDeviceRestore.Review): Int =
        recovery.publishAfterOwnerReview(proof, password, consent,
            review, explicitOwnerApproval = review.ownerApproved)

    /** The forensic inspector and authenticated source are revalidated under one lock. */
    fun prepareManualResume(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent): NativeV9ManualResume.Offer =
        NativeV9ManualResume(context).prepare(proof, password, consent)

    /** New explicit action; not an automatic retry of an earlier claim. */
    fun publishManualResume(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent, offer: NativeV9ManualResume.Offer): Int =
        NativeV9ManualResume(context).publish(proof, password, consent, offer)

    /** Safe to call after a process restart. Never moves or deletes any file. */
    fun forensic(proof: RestoreIdentityGate.Credentials, password: CharArray,
        consent: NativeRegistrationProof.Consent): NativeV9RecoveryInspector.Inspection =
        NativeV9RecoveryInspector(context).inspect(proof, password, consent,
            explicitOwnerApproval = true)
}
