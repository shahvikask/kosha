package `in`.growurdhan.model

import org.junit.Assert.*
import org.junit.Test

class NativeEgressInventoryTest {
    @Test fun explicitDestinationsCarryNoHouseholdPayload() {
        val routes = NativeEgressInventory.entries
        assertEquals(4, routes.size)
        assertTrue(routes.none { it.personalFinancialPayload })
        assertEquals(setOf("nsearchives.nseindia.com",
            "www.bseindia.com", "www.amfiindia.com"),
            routes.mapNotNull { it.allowedHost }.toSet())
    }
}
