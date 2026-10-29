package `in`.growurdhan.model

/**
 * P36: explicit opt-in feedback/rating suggestion state, not an automatic
 * Play-review launch. Terms/Privacy consent and onboarding are separate gates.
 * The legacy prompt cadence still needs a source-parity review before signoff.
 */
object NativeFeedbackPromptPolicy {
    const val SNOOZE_DAYS = 30L
    private const val DAY_MILLIS = 86_400_000L
    data class State(val onboardingFinished: Boolean, val termsVersionAccepted: String?,
        val privacyVersionAccepted: String?, val completedSessions: Int,
        val lastDismissedMillis: Long?, val permanentlyDeclined: Boolean)
    fun eligible(state: State, requiredTerms: String, requiredPrivacy: String,
        nowMillis: Long): Boolean {
        if (!state.onboardingFinished || state.permanentlyDeclined ||
            state.termsVersionAccepted != requiredTerms ||
            state.privacyVersionAccepted != requiredPrivacy ||
            state.completedSessions < 3 || nowMillis < 0) return false
        val last = state.lastDismissedMillis ?: return true
        return last >= 0L && nowMillis >= last &&
            nowMillis - last >= SNOOZE_DAYS * DAY_MILLIS
    }
}
