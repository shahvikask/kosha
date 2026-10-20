package `in`.growurdhan.database

import org.junit.Assert.*
import org.junit.Test

/**
 * Synthetic, non-customer records. Frozen ledger economic corrections may
 * chain, but can never fork, cycle or supersede another family member's
 * position when projected into a new SQLCipher vault.
 *
 * Pure source tests: do not claim an original-device encrypted file passed.
 */
class RestoreCorrectionGraphTest {
    private fun event(id: String, owner: String = "member-a",
        holding: String = "holding-a", kind: String = "BUY",
        supersedes: String? = null) = RestoreLedgerPreflight.Event(
        id = id, holdingId = holding, ownerId = owner,
        eventType = kind, eventDate = "2025-03-01",
        quantityScaled = "100000000", unitPriceMinor = "120000",
        totalAmountMinor = "120000", chargesMinor = "0",
        supersedesEventId = supersedes)

    private fun fixture(vararg events: RestoreLedgerPreflight.Event) =
        RestoreLedgerPreflight.Snapshot(
            owners = listOf(RestoreLedgerPreflight.Owner("member-a"),
                RestoreLedgerPreflight.Owner("member-b")),
            holdings = listOf(
                RestoreLedgerPreflight.Holding(
                    "holding-a", "member-a", "custody-a", "100000000", "125000"),
                RestoreLedgerPreflight.Holding(
                    "holding-b", "member-b", "custody-b", "100000000", "125000"),
                RestoreLedgerPreflight.Holding(
                    "holding-a-other", "member-a", null, "0", "10000")),
            accounts = listOf(
                RestoreLedgerPreflight.Account("custody-a", "member-a"),
                RestoreLedgerPreflight.Account("custody-b", "member-b")),
            goals = emptyList(),
            events = events.toList())

    @Test fun validImmutableCorrectionChainPreservesAllSourceRows() {
        val snapshot = fixture(
            event("original-buy"),
            event("revision-one", kind = "CORRECTION", supersedes = "original-buy"),
            event("revision-two", kind = "CORRECTION", supersedes = "revision-one"),
            event("owner-b-purchase", owner = "member-b", holding = "holding-b")
        )
        val counts = RestoreLedgerPreflight.validate(snapshot)
        assertEquals(4, counts.events)
        assertEquals(2, counts.owners)
        assertEquals(3, counts.holdings)
    }

    @Test fun correctionMayNotSupersedeAnotherFamilyMemberPurchase() {
        val crossed = fixture(
            event("original-buy", owner = "member-b", holding = "holding-b"),
            event("own-buy"),
            event("forgery", kind = "CORRECTION", supersedes = "original-buy")
        )
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(crossed)
        }
    }

    @Test fun correctionMayNotJumpBetweenHoldingsOfSameOwner() {
        val crossed = fixture(
            event("other-buy", holding = "holding-a-other"),
            event("own-buy"),
            event("forgery", kind = "CORRECTION", supersedes = "other-buy"),
            event("owner-b-buy", owner = "member-b", holding = "holding-b")
        )
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(crossed)
        }
    }

    @Test fun twoCorrectionsOfOneEventCannotDoubleCountReplacement() {
        val fork = fixture(
            event("original-buy"),
            event("revision-one", kind = "CORRECTION", supersedes = "original-buy"),
            event("revision-two", kind = "CORRECTION", supersedes = "original-buy"),
            event("owner-b-buy", owner = "member-b", holding = "holding-b")
        )
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(fork)
        }
    }

    @Test fun twoNodeOrThreeNodeCyclesCannotBecomeCostLots() {
        val two = fixture(
            event("cycle-a", kind = "CORRECTION", supersedes = "cycle-b"),
            event("cycle-b", kind = "CORRECTION", supersedes = "cycle-a"),
            event("owner-b-buy", owner = "member-b", holding = "holding-b")
        )
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(two)
        }
        val three = fixture(
            event("cycle-a", kind = "CORRECTION", supersedes = "cycle-b"),
            event("cycle-b", kind = "CORRECTION", supersedes = "cycle-c"),
            event("cycle-c", kind = "CORRECTION", supersedes = "cycle-a"),
            event("owner-b-buy", owner = "member-b", holding = "holding-b")
        )
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(three)
        }
    }

    @Test fun longCorrectionChainsAreCheckedIterativelyWithoutStackOverflow() {
        val count = 2000
        val history = ArrayList<RestoreLedgerPreflight.Event>()
        history += event("base")
        for (i in 1..count) {
            history += event("change-$i", kind = "CORRECTION",
                supersedes = if (i == 1) "base" else "change-${i - 1}")
        }
        history += event("owner-b-buy", owner = "member-b", holding = "holding-b")
        assertEquals(count + 2,
            RestoreLedgerPreflight.validate(fixture(*history.toTypedArray())).events)
    }
    @Test fun dividendOrValuationOnlyHistoryMustNotEraseStoredPositivePosition() {
        val otherOwner = event("owner-b-buy", owner = "member-b", holding = "holding-b")
        for (kind in listOf("DIVIDEND", "INTEREST", "VALUATION_UPDATE",
                "REPAYMENT", "SELL")) {
            val misleading = fixture(
                event("not-an-acquisition", kind = kind), otherOwner)
            assertThrows(IllegalArgumentException::class.java) {
                RestoreLedgerPreflight.validate(misleading)
            }
        }
    }

    @Test fun onlySupersededAcquisitionWithZeroActiveQuantityFailsClosed() {
        val original = event("buy")
        val zeroed = event("corrected-to-no-units", kind = "CORRECTION",
            supersedes = "buy").copy(quantityScaled = "0")
        assertThrows(IllegalArgumentException::class.java) {
            RestoreLedgerPreflight.validate(fixture(
                original, zeroed,
                event("owner-b-buy", owner = "member-b", holding = "holding-b")))
        }
    }

}
