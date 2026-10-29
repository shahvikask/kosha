package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeFeedbackPromptPolicyTest {
    private val day = 86_400_000L
    private val state = NativeFeedbackPromptPolicy.State(
        onboardingFinished = true, termsVersionAccepted = "tos-v4",
        privacyVersionAccepted = "privacy-v1", firstSeenMillis = 10 * day,
        snoozeUntilMillis = null, completedAtMillis = null, promptCount = 0)

    private fun eligible(s: NativeFeedbackPromptPolicy.State, now: Long) =
        NativeFeedbackPromptPolicy.eligible(s, "tos-v4", "privacy-v1", now)

    @Test fun sevenDayFirstDelayPreservesLegacyBoundary() {
        assertFalse(eligible(state, 17 * day - 1))
        assertTrue(eligible(state, 17 * day))
        assertFalse(eligible(state.copy(firstSeenMillis = null), 25 * day))
        assertFalse(eligible(state, 9 * day))
    }

    @Test fun twoDaySnoozeAndSixPromptLimitArePreserved() {
        val snoozed = NativeFeedbackPromptPolicy.dismiss(state, 17 * day)
        assertEquals(1, snoozed.promptCount)
        assertFalse(eligible(snoozed, 19 * day - 1))
        assertTrue(eligible(snoozed, 19 * day))
        assertFalse(eligible(snoozed.copy(promptCount = 6), 25 * day))
        assertFalse(eligible(NativeFeedbackPromptPolicy.complete(state, 20 * day), 40 * day))
    }

    @Test fun currentLegalConsentAndOnboardingAreIndependentGates() {
        assertFalse(eligible(state.copy(onboardingFinished = false), 17 * day))
        assertFalse(eligible(state.copy(termsVersionAccepted = "old"), 17 * day))
        assertFalse(eligible(state.copy(privacyVersionAccepted = null), 17 * day))
        assertFalse(eligible(state.copy(snoozeUntilMillis = 21 * day), 19 * day))
    }
}
