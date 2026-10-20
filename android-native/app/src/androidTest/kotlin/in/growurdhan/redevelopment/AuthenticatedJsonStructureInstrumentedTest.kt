package `in`.growurdhan.redevelopment

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic lexical fixtures only; the original encrypted JSON is never normalized. */
@RunWith(AndroidJUnit4::class)
class AuthenticatedJsonStructureInstrumentedTest {
    @Test fun validFrozenJsonStringsArraysDecimalsAndNestedObjectsRemainReadable() {
        val json = """
            {"kind":"GUDVAULT","version":2,"ledgerEvents":[
              {"id":"event","notes":"escaped\u0020value","price":-0.25e+2},
              {"id":"other","notes":"escaped \uD83D\uDE00 emoji"}],
             "holdings":[],"metadata":{"custom":"x","child":{"custom":"y"}},
             "enabled":true,"missing":null}
        """.trimIndent()
        AuthenticatedJsonStructure.requireUnambiguous(json)
        assertEquals(2, org.json.JSONObject(json)
            .getJSONArray("ledgerEvents").length())
    }

    @Test fun duplicateFinancialCollectionsAndNestedFieldsRefuseBeforeJsonObject() {
        val inputs = listOf(
            """{"ledgerEvents":[{"id":"a"}],"ledgerEvents":[]}""",
            """{"holding":{"quantityScaled":"1","quantityScaled":"0"}}""",
            """{"holding":{"quantityScaled":"1","quantity\u0053caled":"0"}}""",
            """{"account":{"panForStatements":"ABCDE1234F","panForStatements":"PQRST1234F"}}""",
            """{"goals":[],"goals":[{"targetMinor":"0"}]}"""
        )
        for (raw in inputs) {
            assertThrows(IllegalArgumentException::class.java) {
                AuthenticatedJsonStructure.requireUnambiguous(raw)
            }
            assertThrows(IllegalArgumentException::class.java) {
                FrozenJsonV9Adapter.planFromAuthenticatedPlaintext(raw)
            }
        }
    }

    @Test fun duplicateKeysInSeparateObjectsDoNotCollide() {
        AuthenticatedJsonStructure.requireUnambiguous(
            """{"owners":[{"id":"one"},{"id":"two"}],"other":{"id":"three"}}""")
    }

    @Test fun malformedExtensionsAndTrailingDataAreRefusedRatherThanCoerced() {
        val bad = listOf(
            """{'ledgerEvents':[]}""",
            """{ledgerEvents:[]}""",
            """{"holdings":[]}//comment""",
            """{"holdings":[],}""",
            """{"holdings":[1,]}""",
            """{"holdings":[01]}""",
            """{"holdings":[1.]}""",
            """{"holdings":[1e+]}""",
            """{"holdings":[+1]}""",
            """{"x":"bad\q"}""",
            """{"x":"bad\u12zz"}""",
            """{"x":"\uD800"}""",
            """{"x":"\uDC00"}""",
            """{"x":"\uD800\u0041"}""",
            """{"x":false}{"extra":true}""",
            """{"x":NaN}""",
            "[1,2]",
            "{\"x\":\"a\nb\"}",
            "{\"x\":\"embedded\u0000zero\"}"
        )
        for (input in bad) {
            assertThrows(IllegalArgumentException::class.java) {
                AuthenticatedJsonStructure.requireUnambiguous(input)
            }
        }
    }

    @Test fun excessivelyNestedAuthenticatedMetadataIsRejectedWithBoundedDepth() {
        val nested = "[".repeat(90) + "1" + "]".repeat(90)
        assertThrows(IllegalArgumentException::class.java) {
            AuthenticatedJsonStructure.requireUnambiguous("{\"metadata\":$nested}")
        }
    }
}
