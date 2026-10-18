package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeCredentialsTest {
    private fun registration() = NativeCredentials.Enrollment(" Person@Example.com ", " Ava ",
        " Shah ", "Strong!Pass123", "school", " The  First School ",
        "pet", " My  Cat ")
    private val date = "2026-10-18T00:00:00Z"

    @Test fun registrationProducesIndependentSaltedHashesAndVerifies() {
        val a = NativeCredentials.enroll(registration(), date)
        val b = NativeCredentials.enroll(registration(), date)
        assertEquals("person@example.com", a.email)
        assertEquals(NativeCredentials.ROUNDS, a.passwordIterations)
        assertNotEquals(a.passwordSalt, b.passwordSalt)
        assertNotEquals(a.q1Hash, a.q2Hash)
        assertTrue(NativeCredentials.verifyPassword(a, " PERSON@example.com", "Strong!Pass123"))
        assertFalse(NativeCredentials.verifyPassword(a, a.email, "incorrect"))
        assertFalse(NativeCredentials.verifyPassword(a, "other@example.com", "Strong!Pass123"))
    }

    @Test fun recoveryAnswerNormalisationAndTampering() {
        val a = NativeCredentials.enroll(registration(), date)
        assertTrue(NativeCredentials.verifyRecovery(a, "school", "the first school", "pet", "MY CAT"))
        assertFalse(NativeCredentials.verifyRecovery(a, "pet", "MY CAT", "school", "the first school"))
        assertFalse(NativeCredentials.verifyRecovery(a, "school", "bad", "pet", "MY CAT"))
        assertFalse(NativeCredentials.verifyRecovery(a.copy(q1Hash = "tamper"),
            "school", "the first school", "pet", "my cat"))
    }

    @Test fun weakPasswordsRepeatedQuestionsAndInvalidIdentityRejectBeforeCreation() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.enroll(registration().copy(password = "weak"), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.enroll(registration().copy(secondQuestion = "school"), date)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.enroll(registration().copy(email = "bad address"), date)
        }
    }

    @Test fun sessionLocksOnBackgroundAndIdleAndRejectsClockRollback() {
        val a = NativeCredentials.enroll(registration(), date)
        val s = NativeSession(1_000)
        assertFalse(s.allowed(a.email, 100))
        assertTrue(s.login(a, a.email, "Strong!Pass123", 10_000))
        assertTrue(s.allowed(a.email, 10_200))
        assertFalse(s.allowed(a.email, 9_999))
        assertTrue(s.login(a, a.email, "Strong!Pass123", 10_000))
        assertFalse(s.allowed(a.email, 11_000))
        assertTrue(s.login(a, a.email, "Strong!Pass123", 12_000))
        s.onBackground()
        assertFalse(s.allowed(a.email, 12_001))
    }

    @Test fun fiveFailedLoginsFailClosedWithoutAutoUnlock() {
        val a = NativeCredentials.enroll(registration(), date)
        val s = NativeSession()
        repeat(5) { assertFalse(s.login(a, a.email, "wrong", 10L)) }
        assertTrue(s.blocked())
        assertFalse(s.login(a, a.email, "Strong!Pass123", 11L))
        assertFalse(s.allowed(a.email, 12L))
    }


    @Test fun recoveryNeedsEntropyAndBothCorrectFrozenAnswers() {
        val account = NativeCredentials.enroll(registration(), date)
        val issued = NativeCredentials.issueRecoveryCapability()
        assertTrue(issued.code.startsWith("GUD-"))
        assertTrue(issued.code.length >= 40)
        val changed = "2026-10-19T00:00:00Z"
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.recoverPassword(account, account.email, "bad",
                issued.verifier, "school", "the first school", "pet", "my cat",
                "NewStrong!345", changed)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.recoverPassword(account, account.email, issued.code,
                issued.verifier, "school", "bad", "pet", "my cat", "NewStrong!345", changed)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.recoverPassword(account, "other@example.com", issued.code,
                issued.verifier, "school", "the first school", "pet", "my cat",
                "NewStrong!345", changed)
        }
        val updated = NativeCredentials.recoverPassword(account, account.email, issued.code,
            issued.verifier, "school", "the first school", "pet", "my cat",
            "NewStrong!345", changed)
        assertTrue(NativeCredentials.verifyPassword(updated, updated.email, "NewStrong!345"))
        assertFalse(NativeCredentials.verifyPassword(updated, updated.email, "Strong!Pass123"))
        assertNotEquals(account.passwordSalt, updated.passwordSalt)
    }

    @Test fun passwordExpiryIsExactly180DaysAndFailsClosedOnCorruptDate() {
        val a = NativeCredentials.enroll(registration(), date)
        val changed = java.time.Instant.parse(date)
        assertFalse(NativeCredentials.passwordExpired(a, changed.plusSeconds(179L * 86400)))
        assertTrue(NativeCredentials.passwordExpired(a, changed.plusSeconds(180L * 86400)))
        assertTrue(NativeCredentials.passwordExpired(a, changed.minusSeconds(1)))
        assertTrue(NativeCredentials.passwordExpired(a.copy(passwordChangedAt = "not-a-date"), changed))
    }

    @Test fun passwordChangeRequiresOldPasswordAndInvalidatesOldHash() {
        val a = NativeCredentials.enroll(registration(), date)
        assertThrows(IllegalArgumentException::class.java) {
            NativeCredentials.changePassword(a, "NewStrong!345", "2026-10-19T00:00:00Z", "wrong")
        }
        val b = NativeCredentials.changePassword(a, "NewStrong!345",
            "2026-10-19T00:00:00Z", "Strong!Pass123")
        assertFalse(NativeCredentials.verifyPassword(b, b.email, "Strong!Pass123"))
        assertTrue(NativeCredentials.verifyPassword(b, b.email, "NewStrong!345"))
    }
}