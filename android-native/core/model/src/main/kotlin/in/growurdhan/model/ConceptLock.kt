package `in`.growurdhan.model

enum class ConceptPlace {
    Locked,
    SampleHome,
    SampleHolding,
}

/**
 * M00 concept navigation. There is no unlock method.
 * [vaultOpen] stays false for every place, including the sample layout.
 */
data class ConceptLock(val place: ConceptPlace = ConceptPlace.Locked) {
    val vaultOpen: Boolean = false

    fun viewSampleLayout(): ConceptLock {
        check(place == ConceptPlace.Locked) { "sample layout starts from the locked screen" }
        return copy(place = ConceptPlace.SampleHome)
    }

    fun viewSampleHolding(): ConceptLock {
        check(place == ConceptPlace.SampleHome) { "sample holding is reached from the sample home" }
        return copy(place = ConceptPlace.SampleHolding)
    }

    fun backToSampleHome(): ConceptLock {
        check(place == ConceptPlace.SampleHolding) { "back returns to the sample home" }
        return copy(place = ConceptPlace.SampleHome)
    }

    fun returnToLocked(): ConceptLock = ConceptLock(ConceptPlace.Locked)
}
