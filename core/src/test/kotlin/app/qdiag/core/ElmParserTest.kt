package app.qdiag.core

import app.qdiag.core.elm.ElmParser
import app.qdiag.core.elm.ElmReply
import app.qdiag.core.obd.Obd
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ElmParserTest {

    @Test
    fun singleFrameWithHeader() {
        val r = ElmParser.parseData("7E8 04 41 0C 0B B8 \r\r>", "010C")
        assertIs<ElmReply.Data>(r)
        assertContentEquals(intArrayOf(0x41, 0x0C, 0x0B, 0xB8), r.byEcu["7E8"])
        assertNotNull(r.payload(0x41, 0x0C))
    }

    @Test
    fun searchingAndEchoAreDropped() {
        val r = ElmParser.parseData("0100\rSEARCHING...\r7E8 06 41 00 98 3B 80 13 \r\r>", "0100")
        assertIs<ElmReply.Data>(r)
        assertEquals(setOf(1, 4, 5, 11, 12, 13, 15, 16, 17, 28, 31, 32), Obd.supportedPids(r.payload(0x41, 0x00)!!))
    }

    @Test
    fun multiFrameVinWithHeaders() {
        val raw = """
            7E8 10 14 49 02 01 53 4A 4E
            7E8 21 46 41 41 4A 31 30 55
            7E8 22 31 32 33 34 35 36 37
        """.trimIndent()
        val r = ElmParser.parseData(raw, "0902")
        assertIs<ElmReply.Data>(r)
        val payload = r.payload(0x49, 0x02)!!
        assertEquals(20, payload.size)
        assertEquals("SJNFAAJ10U1234567", Obd.vin(payload))
    }

    @Test
    fun multiFrameWithoutHeaders() {
        val raw = "014\r0: 49 02 01 53 4A 4E\r1: 46 41 41 4A 31 30 55\r2: 31 32 33 34 35 36 37\r\r>"
        val r = ElmParser.parseData(raw, "0902")
        assertIs<ElmReply.Data>(r)
        assertEquals("SJNFAAJ10U1234567", Obd.vin(r.byEcu["?"]!!))
    }

    @Test
    fun headerlessSingleFrame() {
        val r = ElmParser.parseData("41 05 7B \r>", "0105")
        assertIs<ElmReply.Data>(r)
        assertContentEquals(intArrayOf(0x41, 0x05, 0x7B), r.byEcu["?"])
    }

    @Test
    fun spacesOff() {
        val r = ElmParser.parseData("7E8044105 7B\r>".replace(" ", ""), "0105")
        assertIs<ElmReply.Data>(r)
        assertContentEquals(intArrayOf(0x41, 0x05, 0x7B), r.byEcu["7E8"])
    }

    @Test
    fun errors() {
        assertEquals(ElmReply.Kind.NO_DATA, (ElmParser.parseData("NO DATA\r\r>", "0178") as ElmReply.Error).kind)
        assertEquals(ElmReply.Kind.UNABLE_TO_CONNECT, (ElmParser.parseData("SEARCHING...\rUNABLE TO CONNECT\r>", "0100") as ElmReply.Error).kind)
        assertEquals(ElmReply.Kind.UNKNOWN_COMMAND, (ElmParser.parseText("?\r>", "ATCRA7E8") as ElmReply.Error).kind)
        assertEquals(ElmReply.Kind.CAN_ERROR, (ElmParser.parseData("CAN ERROR\r>", "0100") as ElmReply.Error).kind)
    }

    @Test
    fun atText() {
        val r = ElmParser.parseText("ATZ\r\r\rELM327 v2.1\r\r>", "ATZ")
        assertIs<ElmReply.Text>(r)
        assertEquals("ELM327 V2.1", r.first)
    }

    @Test
    fun twoEcusAnswer() {
        val raw = "7E8 06 41 00 98 3B 80 13\r7E9 06 41 00 98 18 00 01\r>"
        val r = ElmParser.parseData(raw, "0100") as ElmReply.Data
        assertEquals(setOf("7E8", "7E9"), r.byEcu.keys)
    }
}
