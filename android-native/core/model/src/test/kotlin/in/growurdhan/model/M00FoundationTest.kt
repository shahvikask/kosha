package `in`.growurdhan.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class M00FoundationTest {
    @Test
    fun moneyAndQuantityScalesMatchTheLedgerContract() {
        assertEquals(10_000L, ScaledAmount.MONEY_SCALE)
        assertEquals(100_000_000L, ScaledAmount.QUANTITY_SCALE)
    }

    @Test
    fun scaledAdditionRejectsOverflow() {
        val left = ScaledAmount.moneyMinor(Long.MAX_VALUE)
        val right = ScaledAmount.moneyMinor(1L)
        val error = runCatching { left + right }.exceptionOrNull()
        assertTrue(error is ArithmeticException)
    }

    @Test
    fun overviewUsesIndianGroupingWithoutReplacingTheMinorAmount() {
        val minor = 12_345_678_900L
        assertEquals("\u20B912,34,567.89", InrFormat.formatOverview(minor))
        assertEquals("\u20B912,34,567.8900", InrFormat.formatExact(minor))
        assertEquals(minor, ScaledAmount.moneyMinor(minor).minor)
    }

    @Test
    fun bodyTextPairsMeetTheContrastObjective() {
        val pairs = listOf(
            BrandSourceTokens.LIGHT_TEXT to BrandSourceTokens.LIGHT_BACKGROUND,
            BrandSourceTokens.LIGHT_TEXT to BrandSourceTokens.LIGHT_SURFACE,
            BrandSourceTokens.LIGHT_MUTED to BrandSourceTokens.LIGHT_BACKGROUND,
            BrandSourceTokens.DARK_TEXT to BrandSourceTokens.DARK_BACKGROUND,
            BrandSourceTokens.DARK_TEXT to BrandSourceTokens.DARK_CARD,
            BrandSourceTokens.DARK_MUTED to BrandSourceTokens.DARK_BACKGROUND,
            SemanticTokens.LIGHT_ON_PRIMARY to SemanticTokens.LIGHT_PRIMARY_CONTAINER,
            SemanticTokens.DARK_ON_PRIMARY to SemanticTokens.DARK_PRIMARY,
        )
        pairs.forEach { (ink, surface) ->
            assertTrue(
                "$ink on $surface is below 4.5",
                Contrast.ratio(ink, surface) >= 4.5,
            )
        }
    }

    @Test
    fun amberCandidateIsNotUsedAsSmallTextOnIvory() {
        val ratio = Contrast.ratio(
            BrandSourceTokens.LIGHT_AMBER_CANDIDATE,
            BrandSourceTokens.LIGHT_BACKGROUND,
        )
        assertTrue(ratio < 4.5)
        assertTrue(SemanticTokens.LIGHT_PRIMARY_CONTAINER != BrandSourceTokens.LIGHT_AMBER_CANDIDATE)
    }

    @Test
    fun conceptRoutesExistAndDeadDestinationsAreNotMarkedImplemented() {
        assertEquals(listOf("unlock", "home", "portfolio/holding"), M00Inventory.conceptScreens)
        assertEquals(36, M00Inventory.holdingFields.map { it.key }.distinct().size)
        assertTrue(M00Inventory.routes.any { it.legacySurface == "Auth" && it.implementedInM00 })
        assertTrue(M00Inventory.routes.any { it.legacySurface == "Holdings" && !it.implementedInM00 })
    }

    @Test
    fun frozenLogoAndParserBytesStillMatchTheInventory() {
        val root = repoRoot()
        val svg = File(root, "public/brand/mark.svg").readBytes()
        assertEquals(BrandSourceTokens.MARK_SVG_SHA256, sha256(svg))
        val freeze = File(root, "docs/implementation/legacy-parser-sha256.txt")
        val rows = freeze.readLines().filter { it.isNotBlank() && !it.startsWith("#") }
        assertTrue(rows.size >= 32)
        rows.forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            val hash = parts[0]
            val path = parts.last()
            val bytes = File(root, path).readBytes()
            assertEquals(path, hash, sha256(bytes))
        }
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        repeat(6) {
            if (File(dir, "public/brand/mark.svg").isFile) return dir
            dir = dir.parentFile ?: dir
        }
        error("repository root not found from ${File("").absolutePath}")
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
