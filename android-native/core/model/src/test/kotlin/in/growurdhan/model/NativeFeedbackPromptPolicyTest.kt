package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeFeedbackPromptPolicyTest {
    private val normal = NativeFeedbackPromptPolicy.State(true, "tos-v4", "privacy-v1",
        3, null, false)

    @Test fun mustCompleteOnboardingBothCurrentConsentsAndThreeSessions() {
        assertTrue(NativeFeedbackPromptPolicy.eligible(normal, "tos-v4", "privacy-v1", 0))
        assertFalse(NativeFeedbackPromptPolicy.eligible(normal.copy(
            onboardingFinished = false), "tos-v4", "privacy-v1", 0))
        assertFalse(NativeFeedbackPromptPolicy.eligible(normal.copy(
            termsVersionAccepted = "old"), "tos-v4", "privacy-v1", 0))
        assertFalse(NativeFeedbackPromptPolicy.eligible(normal.copy(
            privacyVersionAccepted = null), "tos-v4", "privacy-v1", 0))
        assertFalse(NativeFeedbackPromptPolicy.eligible(normal.copy(
            completedSessions = 2), "tos-v4", "privacy-v1", 0))
    }

    @Test fun snoozeAndPermanentDeclineCannotBeBypassedByClockRollback() {
        val dismissed = normal.copy(lastDismissedMillis = 10_000L)
        val after = 10_000L + 30L * 86_400_000L
        assertFalse(NativeFeedbackPromptPolicy.eligible(dismissed, "tos-v4",
            "privacy-v1", after - 1))
        assertTrue(NativeFeedbackPromptPolicy.eligible(dismissed, "tos-v4",
            "privacy-v1", after))
        assertFalse(NativeFeedbackPromptPolicy.eligible(dismissed, "tos-v4",
            "privacy-v1", 1))
        assertFalse(NativeFeedbackPromptPolicy.eligible(
            normal.copy(permanentlyDeclined = true), "tos-v4", "privacy-v1", after))
    }
}
