package `in`.growurdhan.redevelopment

import `in`.growurdhan.model.NativeFeedbackPromptPolicy
import `in`.growurdhan.model.NativeRegistrationProof
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * P36 durable, vault-only feedback timing. Neither local timestamps nor a
 * feedback dismissal prove Play identity, Privacy consent or a store review.
 * A locked/invalid vault never yields an eligibility decision.
 */
internal class NativeFeedbackStateRepository(private val accounts: NativeAccountRepository) {
    private val key = "native.feedback.state.v1"
    private val privacyKey = "native.privacy.consent.version"

    data class Record(
        val firstSeenMillis: Long,
        val lastObservedMillis: Long,
        val snoozeUntilMillis: Long?,
        val completedAtMillis: Long?,
        val promptCount: Int,
        val onboardingFinished: Boolean
    )

    private fun readSetting(db: SQLiteDatabase, name: String): String? =
        db.rawQuery("SELECT value FROM setting WHERE key=?", arrayOf(name)).use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    private fun read(db: SQLiteDatabase): Record? {
        val raw = readSetting(db, key) ?: return null
        val parts = raw.split(":")
        require(parts.size == 7 && parts[0] == "1") { "Invalid feedback state version" }
        val first = parts[1].toLong()
        val last = parts[2].toLong()
        val snooze = parts[3].takeUnless { it == "-" }?.toLong()
        val completed = parts[4].takeUnless { it == "-" }?.toLong()
        val count = parts[5].toInt()
        val onboarding = when(parts[6]) {
            "1" -> true
            "0" -> false
            else -> error("Invalid feedback onboarding marker")
        }
        require(first >= 0 && last >= first &&
            (snooze == null || snooze >= first) &&
            (completed == null || completed >= first) &&
            count in 0..NativeFeedbackPromptPolicy.MAX_PROMPTS) {
            "Invalid encrypted feedback state"
        }
        return Record(first, last, snooze, completed, count, onboarding)
    }

    private fun store(db: SQLiteDatabase, state: Record) {
        val encoded = listOf("1", state.firstSeenMillis.toString(),
            state.lastObservedMillis.toString(),
            state.snoozeUntilMillis?.toString() ?: "-",
            state.completedAtMillis?.toString() ?: "-",
            state.promptCount.toString(),
            if (state.onboardingFinished) "1" else "0").joinToString(":")
        db.execSQL("INSERT OR REPLACE INTO setting(key,value) VALUES(?,?)",
            arrayOf<Any>(key, encoded))
    }

    /** Seed from an authenticated open only; never backdate the first-open clock. */
    fun recordAuthenticatedOpen(email: String, nowMillis: Long): Record =
        accounts.withUnlockedVault(email) { db ->
            require(nowMillis >= 0)
            val prior = read(db)
            require(prior == null || nowMillis >= prior.lastObservedMillis) {
                "Feedback clock moved backwards"
            }
            val updated = if (prior == null) Record(nowMillis, nowMillis,
                null, null, 0, false)
                else prior.copy(lastObservedMillis = nowMillis)
            store(db, updated)
            updated
        }

    /** Called only after completing the actual onboarding journey. */
    fun finishOnboarding(email: String, nowMillis: Long): Record =
        accounts.withUnlockedVault(email) { db ->
            val previous = requireNotNull(read(db)) { "First-open evidence required" }
            require(nowMillis >= previous.lastObservedMillis)
            previous.copy(lastObservedMillis = nowMillis, onboardingFinished = true)
                .also { store(db, it) }
        }

    fun dismiss(email: String, nowMillis: Long): Record =
        accounts.withUnlockedVault(email) { db ->
            val previous = requireNotNull(read(db))
            require(previous.onboardingFinished && previous.completedAtMillis == null)
            require(nowMillis >= previous.lastObservedMillis &&
                previous.promptCount < NativeFeedbackPromptPolicy.MAX_PROMPTS &&
                nowMillis <= Long.MAX_VALUE - NativeFeedbackPromptPolicy.SNOOZE_DAYS * 86_400_000L)
            previous.copy(
                lastObservedMillis = nowMillis,
                promptCount = previous.promptCount + 1,
                snoozeUntilMillis = nowMillis + NativeFeedbackPromptPolicy.SNOOZE_DAYS * 86_400_000L
            ).also { store(db, it) }
        }

    fun eligible(email: String, nowMillis: Long, currentPrivacyVersion: String): Boolean =
        accounts.withUnlockedVault(email) { db ->
            val state = read(db) ?: return@withUnlockedVault false
            if (nowMillis < state.lastObservedMillis || currentPrivacyVersion.isBlank()) {
                return@withUnlockedVault false
            }
            val terms = readSetting(db, "m25.terms.version")
            val termsId = readSetting(db, "m25.terms.text_id")
            val privacy = readSetting(db, privacyKey)
            NativeFeedbackPromptPolicy.eligible(
                NativeFeedbackPromptPolicy.State(
                    state.onboardingFinished,
                    terms?.takeIf { termsId == NativeRegistrationProof.TERMS_TEXT_ID },
                    privacy,
                    state.firstSeenMillis, state.snoozeUntilMillis,
                    state.completedAtMillis, state.promptCount),
                NativeRegistrationProof.TERMS_VERSION, currentPrivacyVersion, nowMillis)
        }

    fun readRecorded(email: String): Record? =
        accounts.withUnlockedVault(email) { read(it) }
}
