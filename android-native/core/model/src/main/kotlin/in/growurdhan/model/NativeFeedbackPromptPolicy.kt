package `in`.growurdhan.model

/** P36: legacy 7-day first prompt, 2-day snooze, six total prompts.
 * Native onboarding and current Terms/Privacy consent must also be satisfied;
 * no financial fields, purchase claims or network calls are used here.
 */
object NativeFeedbackPromptPolicy {
    const val FIRST_DELAY_DAYS = 7L
    const val SNOOZE_DAYS = 2L
    const val MAX_PROMPTS = 6
    private const val DAY_MILLIS = 86_400_000L

    data class State(val onboardingFinished: Boolean,
        val termsVersionAccepted: String?, val privacyVersionAccepted: String?,
        val firstSeenMillis: Long?, val snoozeUntilMillis: Long?,
        val completedAtMillis: Long?, val promptCount: Int)

    fun eligible(state: State, requiredTerms: String, requiredPrivacy: String,
        nowMillis: Long): Boolean {
        val first = state.firstSeenMillis ?: return false
        if (!state.onboardingFinished || state.termsVersionAccepted != requiredTerms ||
            state.privacyVersionAccepted != requiredPrivacy ||
            state.completedAtMillis != null || state.promptCount !in 0 until MAX_PROMPTS ||
            first < 0 || nowMillis < first ||
            nowMillis - first < FIRST_DELAY_DAYS * DAY_MILLIS) return false
        val snooze = state.snoozeUntilMillis
        return snooze == null || (snooze >= first && nowMillis >= snooze)
    }

    fun dismiss(state: State, nowMillis: Long): State {
        require(nowMillis >= 0 && state.promptCount in 0 until MAX_PROMPTS)
        return state.copy(promptCount = state.promptCount + 1,
            snoozeUntilMillis = nowMillis + SNOOZE_DAYS * DAY_MILLIS)
    }

    fun complete(state: State, nowMillis: Long): State {
        require(nowMillis >= 0)
        return state.copy(completedAtMillis = nowMillis, snoozeUntilMillis = null)
    }
}
