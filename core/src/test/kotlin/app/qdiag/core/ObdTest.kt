package app.qdiag.core

import app.qdiag.core.elm.ObdClient
import app.qdiag.core.elm.Transport
import app.qdiag.core.kb.Dtcs
import app.qdiag.core.kb.Parts
import app.qdiag.core.obd.MonitorStatus
import app.qdiag.core.obd.Obd
import app.qdiag.core.obd.Pids
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObdTest {

    @Test
    fun dtcDecoding() {
        assertEquals("P0087", Obd.dtc(0x00, 0x87))
        assertEquals("P244B", Obd.dtc(0x24, 0x4B))
        assertEquals("C0035", Obd.dtc(0x40, 0x35))
        assertEquals("U0100", Obd.dtc(0xC1, 0x00))
        assertEquals(null, Obd.dtc(0, 0))
    }

    @Test
    fun dtcListOnCanHasCountByte() {
        assertEquals(listOf("P0087", "P0401"), Obd.dtcs(intArrayOf(0x43, 0x02, 0x00, 0x87, 0x04, 0x01)))
        assertEquals(listOf("P0087"), Obd.dtcs(intArrayOf(0x43, 0x00, 0x87, 0x00, 0x00, 0x00, 0x00)))
        assertEquals(emptyList(), Obd.dtcs(intArrayOf(0x43, 0x00)))
    }

    @Test
    fun pidScaling() {
        val rpm = Pids.decode(intArrayOf(0x41, 0x0C, 0x0B, 0xB8))!!.single()
        assertEquals(750.0, rpm.value)
        val rail = Pids.decode(intArrayOf(0x41, 0x23, 0x0B, 0xB8))!!.single()
        assertEquals(300.0, rail.value, 0.01) // 3000 × 0.1 bar
        val coolant = Pids.decode(intArrayOf(0x41, 0x05, 0x7B))!!.single()
        assertEquals(83.0, coolant.value)
        val boost = Pids.decode(intArrayOf(0x41, 0x70, 0x03, 0x1F, 0x40, 0x1E, 0x00, 0, 0, 0, 0, 0))!!
        assertEquals(250.0, boost.first { it.key == "boostCmd" }.value, 0.01)
        assertEquals(240.0, boost.first { it.key == "boost" }.value, 0.01)
    }

    @Test
    fun dieselMonitors() {
        // MIL on, 2 codes; compression ignition; DPF (bit 6) supported and incomplete, EGR complete.
        val s = MonitorStatus.parse(intArrayOf(0x41, 0x01, 0x82, 0x0F, 0xC8, 0x40))!!
        assertTrue(s.milOn)
        assertEquals(2, s.dtcCount)
        assertTrue(s.diesel)
        assertEquals(listOf("Filtre à particules (FAP)"), s.incomplete.map { it.name.fr })
    }

    @Test
    fun knowledgeBase() {
        val info = Dtcs.info("p0087")
        assertTrue(info.known)
        assertTrue("fuelFilter" in info.parts)
        assertEquals("P0272", Dtcs.info("P0272").code)
        assertTrue(Dtcs.info("P0272").known)
        assertFalse(Dtcs.info("P1234").known)
        for (code in listOf("P0087", "P0299", "P0401", "P2463", "P0380")) {
            for (key in Dtcs.info(code).parts) assertNotNull(Parts[key], "$code → $key")
        }
        assertTrue(Dtcs.count > 100)
    }

    /** A pretend Qashqai: replies by command. */
    private class FakeCar : Transport {
        val sent = ArrayList<String>()
        override fun exchange(command: String, timeoutMs: Long): String {
            sent += command
            return when (command) {
                "ATZ" -> "\r\rELM327 v2.1\r\r>"
                "ATRV" -> "12.6V\r\r>"
                "ATDPN" -> "A6\r\r>"
                "0100" -> "SEARCHING...\r7E8 06 41 00 98 3B 80 13\r\r>"
                "0120" -> "7E8 06 41 20 80 01 80 01\r\r>"
                "0140" -> "7E8 06 41 40 40 00 00 00\r\r>"
                "010C" -> "7E8 04 41 0C 0B B8\r\r>"
                "03" -> "7E8 06 43 02 00 87 04 01\r\r>"
                "07" -> "NO DATA\r\r>"
                "0902" -> "7E8 10 14 49 02 01 53 4A 4E\r7E8 21 46 41 41 4A 31 30 55\r7E8 22 31 32 33 34 35 36 37\r\r>"
                else -> if (command.startsWith("AT")) "OK\r\r>" else "NO DATA\r\r>"
            }
        }
    }

    @Test
    fun clientAgainstAFakeCar() {
        val car = FakeCar()
        val obd = ObdClient(car)
        val init = obd.init()
        assertTrue(init.connected)
        assertEquals("6", init.protocol)
        assertEquals(12.6, init.voltage)
        assertEquals("ELM327 V2.1", init.adapter)
        val supported = obd.supportedPids()
        assertTrue(0x0C in supported && 0x21 in supported && 0x42 in supported)
        assertEquals(750.0, obd.read(0x0C)!!.single().value)
        assertEquals(mapOf("7E8" to listOf("P0087", "P0401")), obd.dtcs(0x03))
        assertEquals(emptyMap(), obd.dtcs(0x07))
        assertEquals("SJNFAAJ10U1234567", obd.vin())
    }
}
