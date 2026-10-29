package `in`.growurdhan.redevelopment

import org.junit.Assert.*
import org.junit.Test

class NativeEmergencyDossierInstrumentedTest {
    private fun sample(review: Boolean, date: String?) =
        NativeBooksRepository.Snapshot(
            people = listOf(NativeBooksRepository.Person("p", "Synthetic person",
                "Self", "INDIVIDUAL", true, true, "ABCDE****F")),
            custody = listOf(NativeBooksRepository.Custody(
                "a", "p", "DEMAT", "Synthetic custodian", "****7423",
                "Self-declared nominee")),
            goals = emptyList(),
            holdings = listOf(NativeBooksRepository.Holding(
                "h", "p", "Synthetic person", "a", "instrument", "Synthetic shares",
                "EQUITY", "Large cap", 100_000_000L, 11_000L, date, "USER_OVERRIDE",
                10_000L, false, review, false, null)),
            imports = emptyList(), documents = emptyList()
        )

    @Test fun decorativeAsterisksCannotExposeRawIdentifiersOrNomineePan() {
        val original = sample(false, "2025-01-01")
        val unsafe = original.copy(
            people = original.people.map {
                it.copy(maskedPan = "ABCDE1234F*")
            },
            custody = original.custody.map {
                it.copy(maskedReference = "****1234567890",
                    nominee = "Nominee PAN ABCDE1234F")
            })
        val dossier = NativeEmergencyDossier.prepare(unsafe, true,
            java.time.LocalDate.parse("2025-02-01"))
        assertNull(dossier.members.single().maskedPan)
        assertNull(dossier.accounts.single().maskedReference)
        assertNull(dossier.accounts.single().nominee)
        assertFalse(dossier.toString().contains("ABCDE1234F"))
        assertFalse(dossier.toString().contains("1234567890"))
    }

    @Test fun staleHistoricalValueCarriesWarningEvenIfNonzero() {
        val dossier = NativeEmergencyDossier.prepare(
            sample(false, "2025-01-01"), true,
            java.time.LocalDate.parse("2025-06-01"))
        assertEquals(11_000L, dossier.holdings.single().valueMinor)
        assertTrue(dossier.holdings.single().valueWarning!!.contains("stale"))
    }

    @Test fun cannotPrepareUnacknowledgedFamilySheet() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeEmergencyDossier.prepare(sample(false, "2025-01-01"), false)
        }
    }

    @Test fun showsOnlyMaskedAccountAndMaskedPanWithExplicitCaveats() {
        val document = NativeEmergencyDossier.prepare(
            sample(false, "2025-01-01"), true,
            java.time.LocalDate.parse("2025-02-01"))
        assertEquals("ABCDE****F", document.members.single().maskedPan)
        assertEquals("****7423", document.accounts.single().maskedReference)
        assertEquals("Self-declared nominee", document.accounts.single().nominee)
        assertEquals(11_000L, document.holdings.single().valueMinor)
        assertEquals(0, document.unresolvedValues)
    }

    @Test fun missingOrUnreconciledPriceNeverBecomesInventedEstateValue() {
        val today = java.time.LocalDate.parse("2025-02-01")
        for (input in listOf(sample(true, "2025-01-01"),
            sample(false, null), sample(false, "2026-01-01"))) {
            val document = NativeEmergencyDossier.prepare(input, true, today)
            assertNull(document.holdings.single().valueMinor)
            assertEquals(1, document.unresolvedValues)
            assertNotNull(document.holdings.single().valueWarning)
        }
    }
}
