package `in`.growurdhan.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBoundaryTest {
    @Test
    fun moduleOpensNoDatabaseAndDefinesNoTable() {
        assertFalse(DatabaseBoundary.OPENS_DATABASE)
        assertTrue(DatabaseBoundary.tableNames.isEmpty())
    }
}
