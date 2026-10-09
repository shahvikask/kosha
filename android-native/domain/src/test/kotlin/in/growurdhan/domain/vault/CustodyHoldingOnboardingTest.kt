package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic name, account and ISIN; never a real investor or real CAS. */
class CustodyHoldingOnboardingTest {
    private val owner = OwnerId("self")
    private val spouse = OwnerId("spouse")
    private val id = HoldingId("synthetic-holding")
    private val isin = "INF000000001"

    private fun account(ownerId: OwnerId = owner, label: String = "Primary MF") =
        CustodyHoldingOnboarding.account(ownerId, "CAMS", label, "account-1", emptyList())

    @Test fun registrarAccountIsNicknameOnlyAndOwnerScoped() {
        val a = account(label = "  Primary   MF  ")
        assertEquals("Primary MF", a.identifierMasked)
        assertEquals(owner, a.ownerId)
        assertEquals("CAMS", a.accountType)
    }

    @Test fun registrarAccountRejectsUnsupportedTypesAndEmptyNames() {
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.account(owner, "Unknown", "Main", "a", emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.account(owner, "CAMS", "  ", "a", emptyList())
        }
    }

    @Test fun accountRefusesPanShapedLabels() {
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.account(owner, "CAMS", "ABCDE1234F", "a", emptyList())
        }
    }

    @Test fun namesWithinOneOwnerMustBeUniqueByRegistrar() {
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.account(owner, "CAMS", "primary mf", "second", listOf(account()))
        }
        assertEquals(
            "Primary MF",
            CustodyHoldingOnboarding.account(spouse, "CAMS", "Primary MF", "spouse-account", listOf(account())).identifierMasked,
        )
    }

    @Test fun holdingStartsWithZeroUnitsAndUnknownCost() {
        val (holding, identity) = CustodyHoldingOnboarding.holding(
            owner, account(), "  Synthetic  Index  ", isin, "12345", id, emptyList(),
        )
        assertEquals("Synthetic Index", holding.name)
        assertEquals("MUTUAL_FUND", holding.assetCategory)
        assertEquals(0L, holding.quantity.raw)
        assertEquals(0L, holding.currentUnitPrice.raw)
        assertEquals("account-1", holding.accountId)
        assertEquals(isin, identity.isin)
        assertEquals("12345", identity.folio)
    }

    @Test fun casingAndWhitespaceOnIsinAreCanonicalized() {
        val (_, mapping) = CustodyHoldingOnboarding.holding(
            owner, account(), "Synthetic", "  inf000000001 ", " F123 ",
            id, emptyList(),
        )
        assertEquals(isin, mapping.isin)
        assertEquals("F123", mapping.folio)
    }

    @Test fun crossOwnerHoldingEnrolmentFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.holding(
                spouse, account(), "Synthetic", isin, "123", id, emptyList(),
            )
        }
    }

    @Test fun invalidIsinFolioOrNameCannotBeEnrolled() {
        for (badIsin in listOf("INF", "abcdefghijkl", "INF00000000;", "")) {
            assertThrows(IllegalArgumentException::class.java) {
                CustodyHoldingOnboarding.holding(owner, account(), "Synthetic", badIsin, "123", id, emptyList())
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.holding(owner, account(), "Synthetic", isin, "12 3", id, emptyList())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.holding(owner, account(), " ", isin, "123", id, emptyList())
        }
    }

    @Test fun sameOwnerIsinFolioCannotBeClaimedTwice() {
        val mapping = CustodyHoldingOnboarding.holding(
            owner, account(), "Synthetic", isin, "123", id, emptyList(),
        ).second
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.holding(
                owner, account(), "Same under another name", isin, "123",
                HoldingId("next"), listOf(mapping),
            )
        }
    }

    @Test fun registrarAccountAndHoldingLimitAreBounded() {
        val accountList = List(CustodyHoldingOnboarding.MAX_ACCOUNTS_PER_OWNER) {
            CustodyAccount("a$it", owner, "CAMS", "Account $it")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.account(owner, "CAMS", "More", "new", accountList)
        }
        val mappedList = List(CustodyHoldingOnboarding.MAX_CAS_HOLDINGS_PER_OWNER) {
            CasHoldingIdentity(HoldingId("h$it"), owner, "account-1", isin, "folio-$it", "CAMS")
        }
        assertThrows(IllegalArgumentException::class.java) {
            CustodyHoldingOnboarding.holding(
                owner, account(), "Extra", isin, "another", HoldingId("new"), mappedList,
            )
        }
    }
}
