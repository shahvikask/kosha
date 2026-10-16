package `in`.growurdhan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConceptLockTest {
    @Test
    fun sampleLayoutNeverOpensAVault() {
        val locked = ConceptLock()
        assertFalse(locked.vaultOpen)
        val home = locked.viewSampleLayout()
        assertEquals(ConceptPlace.SampleHome, home.place)
        assertFalse(home.vaultOpen)
        val holding = home.viewSampleHolding()
        assertEquals(ConceptPlace.SampleHolding, holding.place)
        assertFalse(holding.vaultOpen)
        val again = holding.returnToLocked()
        assertEquals(ConceptPlace.Locked, again.place)
        assertFalse(again.vaultOpen)
    }

    @Test
    fun sampleHoldingCannotBeOpenedWhileLocked() {
        val error = runCatching { ConceptLock().viewSampleHolding() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun composeMarkConstantsStillMatchTheFrozenSvg() {
        var dir = File("").absoluteFile
        val svg = generateSequence(dir) { it.parentFile }
            .map { File(it, "public/brand/mark.svg") }
            .first { it.isFile }
            .readText()
        MarkGeometry.svgSnippets.forEach { snippet ->
            assertTrue("missing $snippet", svg.contains(snippet))
        }
    }
}
