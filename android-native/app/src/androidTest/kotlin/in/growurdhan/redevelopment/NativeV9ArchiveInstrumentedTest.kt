package `in`.growurdhan.redevelopment

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import `in`.growurdhan.database.NativeV9Archive
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test

/** Real SQLCipher/Keystore synthetic-only native archive experiments.
 * Does NOT open/publish a live user vault or approve cross-device recovery.
 */
class NativeV9ArchiveInstrumentedTest {
    private val email = "native@example.invalid"
    private val pass = "Synthetic separate archive password".toCharArray()
    private fun sandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.cacheDir, "native-v9-archive-" + UUID.randomUUID())
        root.mkdirs()
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/" + name).also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File =
                File(root, "files").also { it.mkdirs() }
        }
    }
    private fun candidate(ctx: Context, store: EncryptedVaultStore,
        body: (SQLiteDatabase) -> Unit) {
        val randomKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val path = File(ctx.getDatabasePath("growurdhan-v9.sqlcipher").parentFile,
            "growurdhan-v9.sqlcipher.restore-" + UUID.randomUUID())
        try {
            store.createNewRestoreCandidate(path, randomKey).use(body)
        } finally { randomKey.fill(0) }
    }
    private fun count(db: SQLiteDatabase, table: String): Long =
        db.rawQuery("SELECT COUNT(*) FROM " + table, null).use {
            assertTrue(it.moveToFirst())
            it.getLong(0)
        }

    private fun seed(db: SQLiteDatabase) {
        db.execSQL("INSERT INTO vault_account(id,email,first_name,last_name," +
            "password_salt,password_hash,password_iterations,password_changed_at," +
            "q1_id,q1_hash,q2_id,q2_hash,created_at) VALUES " +
            "('native','native@example.invalid','Synthetic','Owner','salt','hash'," +
            "210000,'2026-10-21','city','qhash1','pet','qhash2','2026-10-21')")
        db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name,created_at)" +
            " VALUES('owner','INDIVIDUAL','Synthetic Owner','2026-10-21')")
        db.execSQL("INSERT INTO account(account_id,entity_id,account_type,custodian,created_at)" +
            " VALUES('acc','owner','DEMAT','Synthetic Broker','2026-10-21')")
        db.execSQL("INSERT INTO instrument(instrument_id,name,category,created_at)" +
            " VALUES('asset','Synthetic Asset','EQUITY','2026-10-21')")
        db.execSQL("INSERT INTO holding(holding_id,instrument_id,entity_id,account_id," +
            "current_price_minor,created_at) VALUES('holding','asset','owner','acc'," +
            "9007199254740993,'2026-10-21')")
        db.execSQL("INSERT INTO import_batch(import_batch_id,source_filename," +
            "document_kind,committed_at) VALUES('batch','synthetic.pdf','CAS','2026-10-21')")
        db.execSQL("INSERT INTO ledger_event(ledger_event_id,entity_id,holding_id," +
            "holding_name,account_id,event_type,trade_date,quantity_scaled," +
            "unit_price_minor,total_amount_minor,charges_minor,entered_at)" +
            " VALUES('event','owner','holding','Synthetic Asset','acc'," +
            "'BUY','2026-10-20',100000000,9007199254740993,9007199254740993,0,'2026-10-21')")
        db.execSQL("INSERT INTO price_observation(instrument_id,price_date,basis," +
            "price_minor,source,ingested_at) VALUES('asset','2026-10-21'," +
            "'EOD',9007199254740993,'SYNTHETIC','2026-10-21')")
        db.execSQL("INSERT INTO source_document(source_document_id,import_batch_id," +
            "original_filename,document_kind,sha256,byte_length,imported_at) VALUES" +
            "('source','batch','synthetic.pdf','CAS','syntheticsha256',16,'2026-10-21')")
        db.execSQL("INSERT INTO statement_position(import_batch_id,holding_id," +
            "statement_date,quantity_scaled,unit_price_minor) VALUES" +
            "('batch','holding','2026-10-21',100000000,9007199254740993)")
        db.execSQL("INSERT INTO tax_rule_set(assessment_year,rules_json,published_at," +
            "installed_at) VALUES('2026','{}','2026-10-21','2026-10-21')")
        db.execSQL("INSERT INTO liability(liability_id,entity_id,liability_type,name,created_at)" +
            " VALUES('loan','owner','HOME','Synthetic liability','2026-10-21')")
        db.execSQL("INSERT INTO goal(goal_id,entity_id,name,created_at) VALUES" +
            "('goal','owner','Synthetic goal','2026-10-21')")
        db.execSQL("INSERT INTO setting(key,value) VALUES('synthetic','opaque metadata')")
        db.execSQL("INSERT INTO egress_log(egress_log_id,occurred_at,destination,purpose," +
            "outcome) VALUES('audit','2026-10-21','offline','test','BLOCKED')")
        db.execSQL("INSERT INTO listed_security(isin,name,category,price_date,source," +
            "updated_at) VALUES('SYNTHETIC','Synthetic Security','EQUITY'," +
            "'2026-10-21','MANUAL','2026-10-21')")
        db.execSQL("INSERT INTO gmail_seen(message_id,attachment_id,seen_at)" +
            " VALUES('synthetic-message','synthetic-attachment','2026-10-21')")
    }


    private val login = "Synthetic-login-password-2026!"
    private val recovery = `in`.growurdhan.database.RestoreIdentityGate.Credentials(
        email, login, "ABCDE1234F", "1985-01-01", "city", "synthetic city",
        "pet", "synthetic pet")
    private val currentConsent = `in`.growurdhan.model.NativeRegistrationProof.Consent(
        `in`.growurdhan.model.NativeRegistrationProof.TERMS_VERSION,
        `in`.growurdhan.model.NativeRegistrationProof.TERMS_TEXT_ID,
        "2026-10-21T12:00:00Z", true)

    /** Fully synthetic hash, PAN and recovery answers. Never print them in CI. */
    private fun hash(secret: String): Pair<String, String> {
        val salt = ByteArray(16) { i -> (i + 1).toByte() }
        val spec = javax.crypto.spec.PBEKeySpec(secret.toCharArray(), salt, 210_000, 256)
        val result = try {
            javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally { spec.clearPassword() }
        return java.util.Base64.getEncoder().encodeToString(salt) to
            java.util.Base64.getEncoder().encodeToString(result)
    }
    private fun packedAnswer(answer: String): String {
        val (salt, digest) = hash(answer)
        return "$salt:$digest:210000"
    }
    private fun seedOwnerProof(db: SQLiteDatabase) {
        val (salt, digest) = hash(login)
        db.execSQL("UPDATE vault_account SET password_salt=?,password_hash=?," +
            "pan_for_statements=?,date_of_birth=?,q1_hash=?,q2_hash=? WHERE id='native'",
            arrayOf(salt, digest, "ABCDE1234F", "1985-01-01",
                packedAnswer("synthetic city"), packedAnswer("synthetic pet")))
        db.execSQL("UPDATE entity SET is_primary=1 WHERE entity_id='owner'")
        db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
            arrayOf("m25.terms.version", currentConsent.version))
        db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
            arrayOf("m25.terms.text_id", currentConsent.textId))
        db.execSQL("INSERT INTO setting(key,value) VALUES(?,?)",
            arrayOf("m25.terms.accepted_at", "2026-10-21T11:00:00Z"))
    }
    /** Synthetic-only fixture shared with the real OS picker integration test. */
    internal fun syntheticAuthenticatedArchive(): ByteArray = verifiedArchive()

    private fun verifiedArchive(): ByteArray {
        val origin = sandbox()
        val snapshot = EncryptedVaultStore(origin).createFresh(false).use { db ->
            db.beginTransaction()
            try { seed(db); seedOwnerProof(db); db.setTransactionSuccessful() }
            finally { db.endTransaction() }
            NativeV9SnapshotReader.capture(db)
        }
        return NativeV9Archive.encrypt(snapshot, email, pass)
    }

    @Test fun ownerApprovedFullNativeRestorePublishesIndependentKeystoreCiphertext() {
        val manager = InstrumentationRegistry.getInstrumentation().targetContext
            .getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        assertTrue("Emulator PIN must be configured by CI", manager.isDeviceSecure)
        val destination = sandbox()
        val restore = NativeV9NewDeviceRestore(destination)
        val archive = verifiedArchive()
        val sha = restore.stageNativeOnce(archive)
        val reviewed = restore.preview(recovery, pass, currentConsent)
        assertEquals(sha, reviewed.archiveSha256)
        assertFalse(reviewed.ownerApproved)
        assertTrue(reviewed.rows > 18)
        assertThrows(IllegalArgumentException::class.java) {
            restore.publishAfterOwnerReview(recovery, pass, currentConsent,
                reviewed, explicitOwnerApproval = false, deviceAdmissionStillValid = { true })
        }
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        // Rejected foreground admission must leave the single-use claim unused
        // and may not publish either of the final vault names.
        assertThrows(IllegalStateException::class.java) {
            restore.publishAfterOwnerReview(recovery, pass, currentConsent,
                reviewed.copy(ownerApproved = true), explicitOwnerApproval = true,
                deviceAdmissionStillValid = { false })
        }
        assertFalse(File(destination.noBackupFilesDir, "restore-claims").exists())
        assertFalse(File(destination.noBackupFilesDir, "vault-key.v1").exists())
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        val inserted = restore.publishAfterOwnerReview(recovery, pass, currentConsent,
            reviewed.copy(ownerApproved = true), explicitOwnerApproval = true, deviceAdmissionStillValid = { true })
        assertEquals(reviewed.rows, inserted)
        val forensic = NativeV9RecoveryInspector(destination).inspect(
            recovery, pass, currentConsent, explicitOwnerApproval = true)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_LIVE, forensic.state)
        assertEquals(inserted, forensic.rows)
        `in`.growurdhan.redevelopment.NativeV9RestoreConsentJournal.requireExact(
            destination.noBackupFilesDir.toPath(), reviewed.snapshotSha256, currentConsent)
        val target = destination.getDatabasePath("growurdhan-v9.sqlcipher")
        val liveKey = File(destination.noBackupFilesDir, "vault-key.v1")
        assertTrue(target.isFile && target.length() > 0 && liveKey.isFile)
        assertFalse(target.readBytes().copyOfRange(0, 16).contentEquals(
            "SQLite format 3\u0000".toByteArray(Charsets.UTF_8)))
        val key = KeystoreWrappedVaultKey(destination).unwrapExisting()
        try {
            val preserve = net.zetetic.database.DatabaseErrorHandler { _, _ ->
                error("Corrupt restored ciphertext must not be deleted")
            }
            SQLiteDatabase.openDatabase(target.absolutePath, key, null,
                SQLiteDatabase.OPEN_READONLY, preserve, null).use { db ->
                val restored = NativeV9SnapshotReader.capture(db)
                assertEquals(reviewed.snapshotSha256,
                    `in`.growurdhan.database.NativeV9RecoveryProof
                        .authenticate(restored, recovery, currentConsent).snapshotSha256)
                assertEquals(1L, count(db, "liability"))
                assertEquals(1L, count(db, "ledger_event"))
                assertEquals(9007199254740993L, db.rawQuery(
                    "SELECT total_amount_minor FROM ledger_event", null).use {
                    assertTrue(it.moveToFirst()); it.getLong(0)
                })
            }
        } finally { key.fill(0) }
        assertThrows(Exception::class.java) {
            restore.publishAfterOwnerReview(recovery, pass, currentConsent,
                reviewed.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        assertArrayEquals(archive, StableStagedArchiveReader.read(
            File(destination.noBackupFilesDir, "restore-pending.v1.kosha").toPath()))
        assertTrue(File(destination.noBackupFilesDir, "restore-claims").listFiles()
            ?.singleOrNull()?.isFile == true)
    }


    @Test fun acceptedTermsReceiptSurvivesClaimOnlyCrashAndTamperingBlocksForensicReview() {
        val destination = sandbox()
        val encrypted = verifiedArchive()
        val restore = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CLAIM)
                throw IllegalStateException("Synthetic crash after durable claim and Terms")
        }
        restore.stageNativeOnce(encrypted)
        val review = restore.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            restore.publishAfterOwnerReview(recovery, pass, currentConsent,
                review.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val terms = File(destination.noBackupFilesDir,
            "restore-terms/restore-" + review.snapshotSha256 + ".terms")
        assertTrue(terms.isFile)
        val forensic = NativeV9RecoveryInspector(destination).inspect(
            recovery, pass, currentConsent, true)
        assertEquals(NativeV9RecoveryInspector.State.CLAIM_ONLY, forensic.state)
        val saved = terms.readBytes()
        assertTrue(saved.isNotEmpty())
        terms.writeBytes(saved.copyOf().also { it[it.lastIndex] = 88 })
        assertThrows(Exception::class.java) {
            NativeV9RecoveryInspector(destination).inspect(
                recovery, pass, currentConsent, true)
        }
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertTrue(File(destination.noBackupFilesDir,"restore-claims")
            .listFiles()!!.size == 1)
    }

    @Test fun restoreInterruptedAfterKeyPublicationIsForensicallyReadableWithoutRepair() {
        val destination = sandbox()
        val archive = verifiedArchive()
        val restoration = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_KEY_PUBLICATION)
                throw IllegalStateException("Synthetic crash before vault DB publication")
        }
        restoration.stageNativeOnce(archive)
        val preview = restoration.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            restoration.publishAfterOwnerReview(recovery, pass, currentConsent,
                preview.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val liveKey = File(destination.noBackupFilesDir, "vault-key.v1")
        val liveDb = destination.getDatabasePath("growurdhan-v9.sqlcipher")
        assertTrue(liveKey.isFile)
        assertFalse(liveDb.exists())
        val beforeKey = liveKey.readBytes()
        val inspection = NativeV9RecoveryInspector(destination).inspect(
            recovery, pass, currentConsent, true)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE,
            inspection.state)
        assertTrue(inspection.claimed)
        assertEquals(preview.rows, inspection.rows)
        assertArrayEquals(beforeKey, liveKey.readBytes())
        assertFalse(liveDb.exists())
        assertThrows(Exception::class.java) {
            EncryptedVaultStore(destination).createFresh(false).close()
        }
    }

    @Test fun syntheticLowStorageFailureAfterCandidateVerificationRetainsEncryptedEvidence() {
        val destination = sandbox()
        val archive = verifiedArchive()
        val restoration = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw java.io.IOException("Synthetic ENOSPC before publication")
        }
        restoration.stageNativeOnce(archive)
        val review = restoration.preview(recovery, pass, currentConsent)
        assertThrows(java.io.IOException::class.java) {
            restoration.publishAfterOwnerReview(recovery, pass, currentConsent,
                review.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val inspection = NativeV9RecoveryInspector(destination).inspect(
            recovery, pass, currentConsent, true)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE,
            inspection.state)
        assertArrayEquals(archive, StableStagedArchiveReader.read(
            File(destination.noBackupFilesDir, "restore-pending.v1.kosha").toPath()))
        assertFalse(File(destination.noBackupFilesDir, "vault-key.v1").exists())
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }

    @Test fun nativeRecoveryCrashAfterCandidateVerificationKeepsClaimAndNeverAutoRetries() {
        val destination = sandbox()
        val archive = verifiedArchive()
        var faultCount = 0
        val restore = NativeV9NewDeviceRestore(destination) { boundary ->
            if (boundary == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED) {
                faultCount++
                throw IllegalStateException("Synthetic verified-candidate interruption")
            }
        }
        restore.stageNativeOnce(archive)
        val reviewed = restore.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            restore.publishAfterOwnerReview(recovery, pass, currentConsent,
                reviewed.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        assertEquals(1, faultCount)
        val forensic = NativeV9RecoveryInspector(destination).inspect(
            recovery, pass, currentConsent, explicitOwnerApproval = true)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE,
            forensic.state)
        assertEquals(reviewed.rows, forensic.rows)
        val claims = File(destination.noBackupFilesDir, "restore-claims")
        assertEquals(1, claims.listFiles()?.size)
        assertTrue(destination.noBackupFilesDir.listFiles()!!.any {
            it.name.startsWith("vault-key.v1.restore-")
        })
        assertTrue(destination.getDatabasePath("growurdhan-v9.sqlcipher")
            .parentFile!!.listFiles()!!.any {
                it.name.startsWith("growurdhan-v9.sqlcipher.restore-")
            })
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertFalse(File(destination.noBackupFilesDir, "vault-key.v1").exists())
        assertThrows(Exception::class.java) {
            NativeV9NewDeviceRestore(destination).publishAfterOwnerReview(
                recovery, pass, currentConsent,
                reviewed.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        assertThrows(Exception::class.java) {
            EncryptedVaultStore(destination).createFresh(false).close()
        }
        assertArrayEquals(archive, StableStagedArchiveReader.read(
            File(destination.noBackupFilesDir, "restore-pending.v1.kosha").toPath()))
    }


    @Test fun manualResumeVerifiedCandidatePublishesOnlyWithSecondOwnerApproval() {
        val destination = sandbox()
        val backup = verifiedArchive()
        val first = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw IllegalStateException("Synthetic process interruption")
        }
        first.stageNativeOnce(backup)
        val initial = first.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            first.publishAfterOwnerReview(recovery, pass, currentConsent,
                initial.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val resume = NativeV9ManualResume(destination)
        val offer = resume.prepare(recovery, pass, currentConsent)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE, offer.state)
        assertEquals(initial.snapshotSha256, offer.snapshotSha256)
        // OS challenge can expire after the read-only forensic offer.
        // A revoked admission must NOT publish a key, database or claim update.
        assertThrows(IllegalStateException::class.java) {
            resume.publish(recovery, pass, currentConsent,
                offer.copy(ownerApproved = true), { false })
        }
        assertFalse(File(destination.noBackupFilesDir, "vault-key.v1").exists())
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertEquals(1, File(destination.noBackupFilesDir, "restore-claims").listFiles()!!.size)
        assertThrows(IllegalArgumentException::class.java) {
            resume.publish(recovery, pass, currentConsent, offer, { true })
        }
        assertEquals(initial.rows, resume.publish(
            recovery, pass, currentConsent, offer.copy(ownerApproved = true), { true }))
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_LIVE,
            NativeV9RecoveryInspector(destination).inspect(
                recovery, pass, currentConsent, true).state)
        assertThrows(Exception::class.java) {
            resume.publish(recovery, pass, currentConsent,
                offer.copy(ownerApproved = true), { true })
        }
        val staged = File(destination.noBackupFilesDir, "restore-pending.v1.kosha")
        assertArrayEquals(backup, staged.readBytes())
        assertEquals(1, File(destination.noBackupFilesDir,"restore-claims").listFiles()!!.size)
    }

    @Test fun manualResumeAfterKeyPublicationUsesExistingKeyWithoutReplacingIt() {
        val destination = sandbox()
        val backup = verifiedArchive()
        val first = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_KEY_PUBLICATION)
                throw IllegalStateException("Synthetic interruption after wrapped key")
        }
        first.stageNativeOnce(backup)
        val initial = first.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            first.publishAfterOwnerReview(recovery, pass, currentConsent,
                initial.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val key = File(destination.noBackupFilesDir, "vault-key.v1")
        val immutableKey = key.readBytes()
        val resume = NativeV9ManualResume(destination)
        val offer = resume.prepare(recovery, pass, currentConsent)
        assertEquals(initial.rows, resume.publish(
            recovery, pass, currentConsent, offer.copy(ownerApproved = true), { true }))
        assertArrayEquals(immutableKey, key.readBytes())
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_LIVE,
            NativeV9RecoveryInspector(destination).inspect(
                recovery, pass, currentConsent, true).state)
    }

    @Test fun tamperedNativeCandidateAndOccupiedTargetNeverResume() {
        val destination = sandbox()
        val backup = verifiedArchive()
        val first = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw IllegalStateException("Synthetic interruption")
        }
        first.stageNativeOnce(backup)
        val initial = first.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            first.publishAfterOwnerReview(recovery, pass, currentConsent,
                initial.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val resume = NativeV9ManualResume(destination)
        val offer = resume.prepare(recovery, pass, currentConsent)
        val target = destination.getDatabasePath("growurdhan-v9.sqlcipher")
        target.writeBytes(byteArrayOf()) // crash tombstone, NEVER deleted by recovery
        assertThrows(Exception::class.java) {
            resume.publish(recovery, pass, currentConsent, offer.copy(ownerApproved = true), { true })
        }
        assertEquals(0L, target.length())
        assertEquals(1, File(destination.noBackupFilesDir,"restore-claims").listFiles()!!.size)
        // Do not remove even a synthetic tombstone to force a retry.
        assertThrows(Exception::class.java) {
            resume.prepare(recovery, pass, currentConsent)
        }
    }

    @Test fun interruptedManualResumePreservesPublishedKeyForNewExplicitReview() {
        val destination = sandbox()
        val backup = verifiedArchive()
        val first = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw IllegalStateException("Synthetic interrupted first restore")
        }
        first.stageNativeOnce(backup)
        val initial = first.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            first.publishAfterOwnerReview(recovery, pass, currentConsent,
                initial.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val interrupted = NativeV9ManualResume(destination) {
            if (it == NativeV9ManualResume.Boundary.AFTER_KEY_PUBLICATION)
                throw java.io.IOException("Synthetic interrupted resume")
        }
        val offered = interrupted.prepare(recovery, pass, currentConsent)
        assertThrows(java.io.IOException::class.java) {
            interrupted.publish(recovery, pass, currentConsent,
                offered.copy(ownerApproved = true), { true })
        }
        val key = File(destination.noBackupFilesDir, "vault-key.v1")
        val saved = key.readBytes()
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE,
            NativeV9RecoveryInspector(destination).inspect(
                recovery, pass, currentConsent, true).state)
        val fresh = NativeV9ManualResume(destination)
        val reapproved = fresh.prepare(recovery, pass, currentConsent)
        assertEquals(offered.snapshotSha256, reapproved.snapshotSha256)
        assertEquals(reapproved.rows, fresh.publish(
            recovery, pass, currentConsent, reapproved.copy(ownerApproved = true), { true }))
        assertArrayEquals(saved, key.readBytes())
        assertThrows(Exception::class.java) {
            fresh.publish(recovery, pass, currentConsent,
                reapproved.copy(ownerApproved = true), { true })
        }

        // Separate synthetic foreground interruption: the Activity admission
        // expires just AFTER key publication but BEFORE database publication.
        // The next owner review must still see a claimed, unpublished candidate.
        val revokedTarget = sandbox()
        val interruptedOriginal = NativeV9NewDeviceRestore(revokedTarget) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw IllegalStateException("Synthetic first-process stop")
        }
        interruptedOriginal.stageNativeOnce(backup)
        val beforeRevocation = interruptedOriginal.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            interruptedOriginal.publishAfterOwnerReview(recovery, pass, currentConsent,
                beforeRevocation.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        var admitted = true
        val interruptedByOs = NativeV9ManualResume(revokedTarget) { point ->
            if (point == NativeV9ManualResume.Boundary.AFTER_KEY_PUBLICATION)
                admitted = false
        }
        val secondOffer = interruptedByOs.prepare(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            interruptedByOs.publish(recovery, pass, currentConsent,
                secondOffer.copy(ownerApproved = true), { admitted })
        }
        assertTrue(File(revokedTarget.noBackupFilesDir, "vault-key.v1").isFile)
        assertFalse(revokedTarget.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertEquals(1, File(revokedTarget.noBackupFilesDir, "restore-claims").listFiles()!!.size)
        val restarted = NativeV9ManualResume(revokedTarget)
        val renewed = restarted.prepare(recovery, pass, currentConsent)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE, renewed.state)
        assertEquals(renewed.rows, restarted.publish(recovery, pass,
            currentConsent, renewed.copy(ownerApproved = true), { true }))
    }

    @Test fun corruptedNativeCandidateCannotBeOfferedForManualResume() {
        val destination = sandbox()
        val backup = verifiedArchive()
        val first = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw IllegalStateException("Synthetic stop")
        }
        first.stageNativeOnce(backup)
        val original = first.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            first.publishAfterOwnerReview(recovery, pass, currentConsent,
                original.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val file = destination.getDatabasePath("growurdhan-v9.sqlcipher")
            .parentFile!!.listFiles()!!.single {
                it.name.startsWith("growurdhan-v9.sqlcipher.restore-")
            }
        java.io.RandomAccessFile(file, "rw").use {
            it.seek(128L)
            it.writeByte(42)
        }
        assertThrows(Exception::class.java) {
            NativeV9ManualResume(destination).prepare(recovery, pass, currentConsent)
        }
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
        assertFalse(File(destination.noBackupFilesDir, "vault-key.v1").exists())
        assertEquals(1, File(destination.noBackupFilesDir,"restore-claims").listFiles()!!.size)
    }


    /** Stable no-backup fixture survives a separate instrumentation process. */
    private fun processBoundarySandbox(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(base.noBackupFilesDir, "native-v9-process-boundary")
        root.mkdirs()
        return object : ContextWrapper(base) {
            override fun getDatabasePath(name: String): File =
                File(root, "databases/" + name).also { it.parentFile?.mkdirs() }
            override fun getNoBackupFilesDir(): File =
                File(root, "no_backup").also { it.mkdirs() }
            override fun getFilesDir(): File =
                File(root, "files").also { it.mkdirs() }
        }
    }

    @Test fun processBoundaryPhaseOnePreservesVerifiedCandidate() {
        val destination = processBoundarySandbox()
        val db = destination.getDatabasePath("growurdhan-v9.sqlcipher")
        assertFalse("Fresh CI-only process test fixture required", db.exists())
        val backup = verifiedArchive()
        val first = NativeV9NewDeviceRestore(destination) {
            if (it == NativeV9NewDeviceRestore.Boundary.AFTER_CANDIDATE_VERIFIED)
                throw IllegalStateException("Synthetic interruption before process shutdown")
        }
        first.stageNativeOnce(backup)
        val review = first.preview(recovery, pass, currentConsent)
        assertThrows(IllegalStateException::class.java) {
            first.publishAfterOwnerReview(recovery, pass, currentConsent,
                review.copy(ownerApproved = true), true, deviceAdmissionStillValid = { true })
        }
        val offered = NativeV9ManualResume(destination).prepare(
            recovery, pass, currentConsent)
        assertEquals(review.snapshotSha256, offered.snapshotSha256)
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_CANDIDATE,
            offered.state)
        assertFalse(db.exists())
    }

    @Test fun processBoundaryPhaseTwoFreshProcessReauthenticatesAndPublishes() {
        val destination = processBoundarySandbox()
        val db = destination.getDatabasePath("growurdhan-v9.sqlcipher")
        assertFalse("Unpublished candidate must survive Phase 1", db.exists())
        val resume = NativeV9ManualResume(destination)
        assertThrows(Exception::class.java) {
            resume.prepare(recovery.copy(password = "Wrong login password"),
                pass, currentConsent)
        }
        assertThrows(Exception::class.java) {
            resume.prepare(recovery, "Wrong archive password".toCharArray(),
                currentConsent)
        }
        val offer = resume.prepare(recovery, pass, currentConsent)
        assertThrows(IllegalArgumentException::class.java) {
            resume.publish(recovery, pass, currentConsent, offer, { true })
        }
        assertEquals(offer.rows, resume.publish(recovery, pass,
            currentConsent, offer.copy(ownerApproved = true), { true }))
        assertEquals(NativeV9RecoveryInspector.State.VERIFIED_LIVE,
            NativeV9RecoveryInspector(destination).inspect(
                recovery, pass, currentConsent, true).state)
        assertTrue(db.isFile && db.length() > 0)
    }

    @Test fun allNativeV9TableTypesRestoreIntoIndependentEmptySqlcipher() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        val original = store.createFresh(false).use { db ->
            db.beginTransaction()
            try { seed(db); db.setTransactionSuccessful() }
            finally { db.endTransaction() }
            NativeV9SnapshotReader.capture(db)
        }
        val archive = NativeV9Archive.encrypt(original, email, pass)
        candidate(ctx, store) { target ->
            val inserted = NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                target, archive, email, pass, true)
            assertEquals(original.tables.sumOf { it.rows.size }, inserted)
            assertEquals(original, NativeV9SnapshotReader.capture(target))
            assertEquals(1L, count(target, "liability"))
            assertEquals(1L, count(target, "statement_position"))
        }
    }

    @Test fun m26FamilyCustodyNomineeGoalsRoundtripIntoIndependentSqlcipher() {
        val context = sandbox()
        val store = EncryptedVaultStore(context)
        val source = store.createFresh(false).use { db ->
            seed(db)
            db.execSQL("UPDATE entity SET relation='Self',is_primary=1 WHERE entity_id='owner'")
            db.execSQL("INSERT INTO entity(entity_id,entity_type,display_name,relation," +
                "guardian_id,created_at) VALUES('minor','INDIVIDUAL','Synthetic Minor'," +
                "'Child','owner','2026-10-21')")
            db.execSQL("UPDATE account SET nominee='Synthetic Nominee'," +
                "identifier_masked='****1234',dp_id='DP1234',client_id='CL5678' WHERE account_id='acc'")
            db.execSQL("UPDATE goal SET name='Education',target_minor=500000," +
                "target_date='2032-01-01',priority='HIGH' WHERE goal_id='goal'")
            db.execSQL("UPDATE holding SET tag='goal' WHERE holding_id='holding'")
            NativeV9SnapshotReader.capture(db)
        }
        val encrypted = NativeV9Archive.encrypt(source, email, pass)
        candidate(context, store) { destination ->
            NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                destination, encrypted, email, pass, true)
            assertEquals(source, NativeV9SnapshotReader.capture(destination))
            destination.rawQuery("SELECT entity_id FROM entity WHERE guardian_id='owner'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("minor", it.getString(0))
            }
            destination.rawQuery("SELECT nominee,dp_id,client_id FROM account WHERE account_id='acc'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("Synthetic Nominee", it.getString(0))
                assertEquals("DP1234", it.getString(1))
                assertEquals("CL5678", it.getString(2))
            }
            destination.rawQuery("SELECT h.entity_id,g.entity_id FROM holding h " +
                "JOIN goal g ON h.tag=g.goal_id WHERE h.holding_id='holding'", null).use {
                assertTrue(it.moveToFirst())
                assertEquals("owner", it.getString(0))
                assertEquals(it.getString(0), it.getString(1))
            }
        }
        assertThrows(Exception::class.java) {
            NativeV9Archive.decrypt(encrypted, email, "Wrong synthetic password".toCharArray())
        }
    }

    @Test fun wrongPasswordTamperedCipherAndDuplicateRowsNeverPartiallyImport() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        val snapshot = store.createFresh(false).use { db ->
            seed(db)
            NativeV9SnapshotReader.capture(db)
        }
        val valid = NativeV9Archive.encrypt(snapshot, email, pass)
        val bad = valid.copyOf().also { it[40] = (it[40].toInt() xor 1).toByte() }
        val table = snapshot.tables.single { it.name == "vault_account" }
        val duplicate = snapshot.copy(tables = snapshot.tables.map {
            if (it.name == table.name) it.copy(rows = it.rows + listOf(it.rows.first()))
            else it
        })
        val poisoned = NativeV9Archive.encrypt(duplicate, email, pass)
        candidate(ctx, store) { target ->
            assertThrows(Exception::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, valid, email, "Wrong synthetic backup password!".toCharArray(), true)
            }
            assertThrows(Exception::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, bad, email, pass, true)
            }
            assertThrows(Exception::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, poisoned, email, pass, true)
            }
            assertEquals(0L, count(target, "entity"))
            assertEquals(0L, count(target, "vault_account"))
            assertEquals(1L, count(target, "vault_meta"))
        }
    }

    @Test fun opaqueHistoricalSchemaExtensionCannotBeSilentlyDroppedOnRestore() {
        val ctx = sandbox()
        val store = EncryptedVaultStore(ctx)
        val snapshot = store.createFresh(false).use { db ->
            db.execSQL("ALTER TABLE setting ADD COLUMN opaque_extra TEXT")
            db.execSQL("INSERT INTO setting(key,value,opaque_extra)" +
                " VALUES('historical','value','preserve')")
            NativeV9SnapshotReader.capture(db)
        }
        val archive = NativeV9Archive.encrypt(snapshot, email, pass)
        assertEquals(snapshot, NativeV9Archive.decrypt(archive, email, pass))
        candidate(ctx, store) { target ->
            assertThrows(IllegalArgumentException::class.java) {
                NativeV9CandidateRestorer.restoreIntoFreshCandidate(
                    target, archive, email, pass, true)
            }
            assertEquals(0L, count(target, "setting"))
            assertEquals(1L, count(target, "vault_meta"))
        }
    }
    @Test fun separateNewDeviceStagesNativeCiphertextOnceAndRefusesReplacement() {
        val origin = sandbox()
        val snapshot = EncryptedVaultStore(origin).createFresh(false).use { db ->
            seed(db)
            NativeV9SnapshotReader.capture(db)
        }
        val archive = NativeV9Archive.encrypt(snapshot, email, pass)
        val destination = sandbox()
        val restore = NativeV9NewDeviceRestore(destination)
        val first = restore.stageNativeOnce(archive)
        val stored = File(destination.noBackupFilesDir, "restore-pending.v1.kosha")
        assertTrue(stored.isFile)
        assertArrayEquals(archive, StableStagedArchiveReader.read(stored.toPath()))
        assertEquals(first, java.security.MessageDigest.getInstance("SHA-256")
            .digest(archive).joinToString("") { "%02x".format(it) })
        assertThrows(Exception::class.java) {
            restore.stageNativeOnce(archive)
        }
        assertArrayEquals(archive, StableStagedArchiveReader.read(stored.toPath()))
        assertFalse(File(destination.noBackupFilesDir,"vault-key.v1").exists())
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())
    }

    @Test fun nativeStagedTamperAndInterruptedAtomicWriteDoNotPublishVault() {
        val original = sandbox()
        val snapshot = EncryptedVaultStore(original).createFresh(false).use { db ->
            seed(db)
            NativeV9SnapshotReader.capture(db)
        }
        val archive = NativeV9Archive.encrypt(snapshot, email, pass)
        val destination = sandbox()
        val damaged = archive.copyOf()
        damaged[40] = (damaged[40].toInt() xor 1).toByte()
        val restore = NativeV9NewDeviceRestore(destination)
        restore.stageNativeOnce(damaged)
        val proof = `in`.growurdhan.database.RestoreIdentityGate.Credentials(
            email, "Synthetic login password", "ABCDE1234F", "1980-01-01",
            "city", "synthetic city", "pet", "synthetic pet")
        val consent = `in`.growurdhan.model.NativeRegistrationProof.Consent(
            `in`.growurdhan.model.NativeRegistrationProof.TERMS_VERSION,
            `in`.growurdhan.model.NativeRegistrationProof.TERMS_TEXT_ID,
            "2026-10-21T10:00:00Z", true)
        assertThrows(Exception::class.java) {
            restore.preview(proof, pass, consent)
        }
        assertFalse(File(destination.noBackupFilesDir,"restore-claims").exists())
        assertFalse(File(destination.noBackupFilesDir,"vault-key.v1").exists())
        assertFalse(destination.getDatabasePath("growurdhan-v9.sqlcipher").exists())

        val second = sandbox()
        val interrupted = File(second.noBackupFilesDir, "restore-pending.v1.kosha.new")
        interrupted.writeBytes(byteArrayOf(1,2,3))
        assertThrows(Exception::class.java) {
            NativeV9NewDeviceRestore(second).stageNativeOnce(archive)
        }
        assertArrayEquals(byteArrayOf(1,2,3), interrupted.readBytes())
        assertFalse(File(second.noBackupFilesDir, "vault-key.v1").exists())
    }

}
