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

    @Test fun memberLensNeverIncludesForeignOrArchivedDossierRows() {
        val first = sample(false, "2025-01-01")
        val secondPerson = NativeBooksRepository.Person("foreign", "Person B",
            "Parent", "INDIVIDUAL", false, true, null)
        val other = first.copy(
            people = first.people + secondPerson,
            custody = first.custody + first.custody.single().copy(
                id = "acct-b", personId = "foreign", custodian = "Foreign custodian"),
            holdings = first.holdings + first.holdings.single().copy(
                id = "foreign-holding", personId = "foreign", personName = "Person B",
                accountId = "acct-b", name = "Foreign position") +
                first.holdings.single().copy(id = "archived", archived = true)
        )
        val asOf = java.time.LocalDate.parse("2025-02-01")
        val own = NativeEmergencyDossier.prepare(other, true, asOf, "p")
        assertEquals(listOf("p"), own.members.map { it.id })
        assertEquals(1, own.accounts.size)
        assertEquals(listOf("Synthetic shares"), own.holdings.map { it.name })
        assertFalse(own.toString().contains("Foreign position"))
        assertFalse(own.toString().contains("Foreign custodian"))
        val foreign = NativeEmergencyDossier.prepare(other, true, asOf, "foreign")
        assertEquals(listOf("foreign"), foreign.members.map { it.id })
        assertEquals(listOf("Foreign position"), foreign.holdings.map { it.name })
        assertThrows(IllegalArgumentException::class.java) {
            NativeEmergencyDossier.prepare(other, true, asOf, "missing")
        }
    }

    @Test fun crossOwnerCustodyAndTextIdentifiersCannotLeakIntoSheet() {
        val initial = sample(false, "2025-01-01")
        val contaminated = initial.copy(
            people = initial.people.map { it.copy(name = "Owner ABCDE1234F") },
            custody = initial.custody.map {
                it.copy(custodian = "Bank 12345678901234")
            },
            holdings = initial.holdings.map {
                it.copy(name = "12345678901234 position")
            }
        )
        val asOf = java.time.LocalDate.parse("2025-02-01")
        val sheet = NativeEmergencyDossier.prepare(contaminated, true, asOf, "p")
        assertFalse(sheet.toString().contains("ABCDE1234F"))
        assertFalse(sheet.toString().contains("12345678901234"))
        val foreignAccount = contaminated.copy(
            custody = contaminated.custody.map { it.copy(personId = "other") }
        )
        assertThrows(IllegalArgumentException::class.java) {
            NativeEmergencyDossier.prepare(foreignAccount, true, asOf, "p")
        }
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
