package app.qdiag.core

import app.qdiag.core.health.Analyzer
import app.qdiag.core.health.Level
import app.qdiag.core.health.Readiness
import app.qdiag.core.health.Sample
import app.qdiag.core.obd.MonitorStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChecksTest {

    private fun drive(build: MutableList<Sample>.() -> Unit) = Analyzer().apply { addAll(buildList(build)) }.findings().associateBy { it.id }

    @Test
    fun batteryAndCharging() {
        val f = drive {
            repeat(5) { add(Sample(it * 1000L, mapOf("rpm" to 0.0, "battV" to 12.1))) }
            repeat(10) { add(Sample(10_000L + it * 1000, mapOf("rpm" to 800.0, "battV" to 14.2))) }
        }
        assertEquals(Level.WATCH, f["battery"]!!.level)
        assertEquals(Level.GOOD, f["charging"]!!.level)
    }

    @Test
    fun thermostatStuckOpen() {
        val f = drive {
            for (i in 0..25) add(Sample(i * 60_000L, mapOf("rpm" to 1800.0, "coolant" to 30.0 + i, "speed" to 60.0)))
        }
        assertEquals(Level.WARN, f["warmup"]!!.level)
    }

    @Test
    fun underboost() {
        val f = drive {
            for (i in 0..10) add(Sample(i * 300L, mapOf("rpm" to 2500.0, "pedal" to 100.0, "map" to 150.0, "baro" to 100.0)))
        }
        assertEquals(Level.WARN, f["boost"]!!.level) // 50 kPa of boost at full load
    }

    @Test
    fun readinessForTheInspection() {
        val status = MonitorStatus.parse(intArrayOf(0x41, 0x01, 0x00, 0x0F, 0xC8, 0x00))!!
        val r = Readiness.check(status, emptyList(), emptyList(), 2500.0).associateBy { it.id }
        assertEquals(Level.GOOD, r["mil"]!!.level)
        assertEquals(Level.GOOD, r["monitors"]!!.level)
        assertEquals(Level.GOOD, r["stored"]!!.level)
    }

    @Test
    fun batteryNeedsKnownEngineState() {
        // A session that only read the adapter's voltage (no rpm): no verdict on the battery.
        val a = Analyzer()
        a.addAll((0 until 200).map { Sample(it * 1000L, mapOf("battV" to 14.2)) })
        assertTrue(a.findings().none { it.id == "battery" })
        // Engine stopped (rpm 0) at 11.9 V: discharged.
        val b = Analyzer()
        b.addAll((0 until 50).map { Sample(it * 1000L, mapOf("battV" to 11.9, "rpm" to 0.0)) })
        assertEquals(Level.WARN, b.findings().first { it.id == "battery" }.level)
    }
}
