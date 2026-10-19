package `in`.growurdhan.model

import java.time.LocalDate

/** Frozen AuthScreen field policy. Never log PAN or DOB. */
object NativeRegistrationProof {
    const val TERMS_VERSION = "2026-10-06"
    const val TERMS_TEXT_ID = "GUD-TOS-2026-10-06-v4"

    data class Identity(val fullPan: String, val dob: String)
    data class Consent(val version: String, val textId: String,
                       val acceptedAt: String, val scrolledToEnd: Boolean)

    fun requireValid(identity: Identity, consent: Consent) {
        require(Regex("[A-Z]{5}[0-9]{4}[A-Z]").matches(identity.fullPan.trim().uppercase())) {
            "Registration identity invalid"
        }
        val birth = runCatching { LocalDate.parse(identity.dob) }.getOrNull()
        require(birth != null && !birth.isAfter(LocalDate.now())) {
            "Registration identity invalid"
        }
        require(consent.scrolledToEnd && consent.version == TERMS_VERSION &&
            consent.textId == TERMS_TEXT_ID &&
            runCatching { java.time.Instant.parse(consent.acceptedAt) }.isSuccess) {
            "Current terms acceptance required"
        }
    }
}
