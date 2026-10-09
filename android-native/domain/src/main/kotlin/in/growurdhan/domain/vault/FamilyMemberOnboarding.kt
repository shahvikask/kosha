package `in`.growurdhan.domain.vault

import `in`.growurdhan.core.model.FamilyMember
import `in`.growurdhan.core.model.OwnerId

/**
 * No user names are stored in identifiers or logs. Eligibility decisions are
 * pure and re-applied inside the encrypted repository write transaction.
 */
object FamilyMemberOnboarding {
    const val MAX_MEMBERS = 30
    val additionalRelationships = listOf("Spouse", "Parent", "Child", "Sibling", "Other")

    fun create(
        existing: List<FamilyMember>,
        displayName: String,
        relationship: String,
        id: OwnerId,
    ): FamilyMember {
        val cleaned = displayName.trim().replace(Regex("\\s+"), " ")
        require(cleaned.isNotEmpty() && cleaned.length <= 100) {
            "A family member name must contain 1–100 characters."
        }
        require(cleaned.none { it.isISOControl() }) { "Control characters are not allowed." }
        require(existing.size < MAX_MEMBERS) { "The family member limit has been reached." }
        require(existing.none { it.id == id }) { "Family owner identifier is already allocated." }
        require(existing.none { it.displayName.equals(cleaned, ignoreCase = true) }) {
            "Two members cannot share the same display name in this onboarding flow."
        }
        val first = existing.isEmpty()
        if (first) {
            require(relationship == "Self") { "The first family member must be Self." }
        } else {
            require(existing.count { it.isPrimary } == 1) {
                "Existing family primary owner requires repair before adding members."
            }
            require(relationship in additionalRelationships) {
                "Select a supported family relationship."
            }
        }
        return FamilyMember(id, cleaned, relationship, isPrimary = first)
    }
}
