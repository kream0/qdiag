package app.qdiag.core

import app.qdiag.core.kb.Dtcs
import app.qdiag.core.kb.Parts
import app.qdiag.core.obd.Pids
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KbTest {
    @Test
    fun lambdaAndPedalCodesAreDescribed() {
        for (code in listOf("P0130", "P0134", "P0135", "P2A00", "P0225", "P2120", "P0564", "P0560", "P0606")) {
            val d = Dtcs.info(code)
            assertTrue(d.known, "$code should be in the table")
        }
        assertTrue("lambdaSensor" in Dtcs.info("P0135").parts)
    }

    @Test
    fun everyPartReferencedByACodeExists() {
        val codes = listOf("P0030", "P0130", "P0135", "P2237", "P0225", "P2138", "P0087", "P0101", "P0670")
        for (c in codes) for (p in Dtcs.info(c).parts) assertNotNull(Parts[p], "$c → $p")
    }

    @Test
    fun partsWithoutNumberSearchByWords() {
        val lambda = Parts["lambdaSensor"]!!
        assertEquals("https://www.auto-doc.fr/search?keyword=sonde+lambda+qashqai+2.0+dci", lambda.searchUrl)
        assertEquals("https://www.auto-doc.fr/search?keyword=18002JD900", Parts["pedal"]!!.searchUrl)
    }

    @Test
    fun widebandLambdaPid() {
        // λ = 1.000 (0x8000), current 0 mA (0x8000)
        val s = Pids.decode(intArrayOf(0x41, 0x34, 0x80, 0x00, 0x80, 0x00))!!
        assertEquals(1.0, s.first { it.key == "lambda1" }.value, 1e-6)
        assertEquals(0.0, s.first { it.key == "lambdaI1" }.value, 1e-6)
    }
}
