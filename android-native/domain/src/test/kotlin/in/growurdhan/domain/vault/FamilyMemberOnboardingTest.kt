package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.OwnerId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FamilyMemberOnboardingTest {
    private val firstId = OwnerId("primary")
    private val secondId = OwnerId("dependent")
    private val first = FamilyMember(firstId, "Synthetic Primary", "Self", true)

    @Test fun firstMemberIsPrimaryAndNormalizesWhitespace() {
        val member = FamilyMemberOnboarding.create(
            emptyList(), "  Synthetic   Primary ", "Self", firstId,
        )
        assertEquals(first, member)
        assertTrue(member.isPrimary)
        assertEquals("Self", member.relationship)
    }

    @Test fun firstMemberCannotBeSpouseOrParent() {
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(emptyList(), "Synthetic", "Spouse", firstId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(emptyList(), "Synthetic", "Parent", firstId)
        }
    }

    @Test fun additionalMemberMustChooseSupportedNonPrimaryRelationship() {
        val member = FamilyMemberOnboarding.create(
            listOf(first), "Synthetic Spouse", "Spouse", secondId,
        )
        assertFalse(member.isPrimary)
        assertEquals("Spouse", member.relationship)
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(listOf(first), "Other", "Self", secondId)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(listOf(first), "Other", "Unknown", secondId)
        }
    }

    @Test fun rejectsBlankControlCharactersAndNamesOverLimit() {
        for (name in listOf("", "   ", "Invalid\u0000Control", "N".repeat(101))) {
            assertThrows(IllegalArgumentException::class.java) {
                FamilyMemberOnboarding.create(emptyList(), name, "Self", firstId)
            }
        }
    }

    @Test fun sameDisplayNameAndOwnerIdCannotBeReintroduced() {
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(
                listOf(first), " synthetic primary ", "Child", secondId,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(
                listOf(first), "Synthetic Dependent", "Child", firstId,
            )
        }
    }

    @Test fun refusesMissingOrMultiplePrimaryOwnerAndTooManyMembers() {
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(
                listOf(first.copy(isPrimary = false)), "Dependent", "Child", secondId,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(
                listOf(first, first.copy(id = OwnerId("second"), displayName = "Second")),
                "Dependent", "Child", secondId,
            )
        }
        val full = listOf(first) + (1 until FamilyMemberOnboarding.MAX_MEMBERS).map {
            FamilyMember(OwnerId("id-$it"), "Synthetic $it", "Child", false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FamilyMemberOnboarding.create(full, "Extra", "Child", secondId)
        }
    }
}
