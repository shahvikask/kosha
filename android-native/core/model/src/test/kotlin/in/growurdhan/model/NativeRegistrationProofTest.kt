package `in`.growurdhan.model

import org.junit.Assert.assertThrows
import org.junit.Test

class NativeRegistrationProofTest {
    private val identity = NativeRegistrationProof.Identity("ABCDE1234F", "1990-06-12")
    private val approved = NativeRegistrationProof.Consent(
        NativeRegistrationProof.TERMS_VERSION, NativeRegistrationProof.TERMS_TEXT_ID,
        "2026-10-19T12:00:00Z", true)

    @Test fun acceptsValidSyntheticIdentityAndVersionedAgreement() {
        NativeRegistrationProof.requireValid(identity, approved)
    }

    @Test fun rejectsWrongPanDateAndInvalidOrUnreviewedAgreement() {
        for (wrong in listOf("ABCD1234F", "ABCDE12345", "ABCDE1234!")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeRegistrationProof.requireValid(identity.copy(fullPan = wrong), approved)
            }
        }
        for (dob in listOf("", "invalid", "3000-01-01")) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeRegistrationProof.requireValid(identity.copy(dob = dob), approved)
            }
        }
        for (consent in listOf(approved.copy(scrolledToEnd = false),
            approved.copy(version = "obsolete"),
            approved.copy(textId = "wrong"),
            approved.copy(acceptedAt = "invalid"))) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeRegistrationProof.requireValid(identity, consent)
            }
        }
    }
}
