package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativePlaySkuCatalogTest {
    @Test fun frozenSkuTypesAreReadOnlyAndCorrect() {
        val all = NativePlaySkuCatalog.expected
        assertEquals(4, all.size)
        assertTrue(NativePlaySkuCatalog.inspect(all).complete)
        assertFalse(NativeCommercialAccessController().current(0L).canRead)
        val wrong = NativePlaySkuCatalog.inspect(all.map {
            if (it.id == NativeCommercialEntitlements.SUPPORT)
                it.copy(type = NativePlaySkuCatalog.Type.INAPP) else it
        })
        assertFalse(wrong.complete)
        assertTrue(NativeCommercialEntitlements.SUPPORT in wrong.missing)
    }
    @Test fun partialDuplicateOrUnexpectedProductCannotBecomeValidCatalog() {
        val all = NativePlaySkuCatalog.expected
        assertFalse(NativePlaySkuCatalog.inspect(all.take(2)).complete)
        assertFalse(NativePlaySkuCatalog.inspect(all + all.first()).complete)
        assertFalse(NativePlaySkuCatalog.inspect(all +
            NativePlaySkuCatalog.Item("local_free_premium",
                NativePlaySkuCatalog.Type.INAPP)).complete)
    }
}
