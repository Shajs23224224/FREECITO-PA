import com.omega.core.meta.*

fun main() {
    val clean = Health(.06, false, 3, 39.0, 80, false, false, 20.0)
    val empty = Performance(0, null, 0.0)
    var now = 0L
    val metrics = CycleMetrics({ now }, 30_000_000_000L)
    val governor = MetaGovernor()
    check(governor.update(clean, empty).load == Load.ECONOMY)
    repeat(4) { metrics.record(1_500_000_000L, true) }
    check(governor.update(clean, metrics.snapshot()).load == Load.LIMITED)
    check(governor.decision().causes == listOf(ResourceCause.COGNITIVE_LATENCY))
    now = 29_999_999_999L
    check(metrics.snapshot().samples == 4)
    check(governor.update(clean, metrics.snapshot()).load == Load.LIMITED)
    now++
    check(metrics.snapshot() == empty)
    repeat(2) { check(governor.update(clean, metrics.snapshot()).load == Load.LIMITED) }
    check(governor.update(clean, metrics.snapshot()).load == Load.LIMITED)
    check(governor.decision().state == ResourceState.RECOVERY)
    check(governor.decision().heldCauses == listOf(ResourceCause.COGNITIVE_LATENCY))
    repeat(2) {
        val b = governor.update(clean, empty)
        check(b.load == Load.LIMITED && !b.commands && !b.learning && !b.ocr && !b.depth)
    }
    check(governor.update(clean, empty).load == Load.ECONOMY)
    check(governor.decision().state == ResourceState.ECONOMY)
    println("PASS: stale p95 expires, LIMITED -> RECOVERY -> ECONOMY at thermal=3")

    val g = MetaGovernor()
    check(g.updateUnavailable(empty).load == Load.LIMITED)
    check(g.decision().causes == listOf(ResourceCause.TELEMETRY_UNAVAILABLE))
    repeat(3) { g.update(clean, empty) }
    check(g.decision().state == ResourceState.RECOVERY)
    check(g.updateUnavailable(empty).load == Load.LIMITED)
    check(g.decision().state == ResourceState.LIMITED && g.decision().healthyReadings == 0)
    repeat(3) { g.update(clean, empty) }
    check(g.decision().state == ResourceState.RECOVERY)
    check(g.update(clean.copy(thermal=4, heapRatio=.95, batteryC=47.0), empty).load == Load.STOP)
    val critical = listOf(ResourceCause.HEAP_CRITICAL, ResourceCause.THERMAL_CRITICAL, ResourceCause.BATTERY_CRITICAL)
    check(g.decision().heldCauses == critical)
    repeat(20) { check(g.update(clean, empty).load == Load.STOP) }
    check(g.decision().heldCauses == critical)
    println("PASS: telemetry failure resets recovery; critical causes preserved and STOP latched")

    val h = MetaGovernor()
    repeat(10) { check(h.update(clean.copy(batteryC=43.0), empty).load == Load.LIMITED) }
    repeat(6) { check(h.update(clean.copy(batteryC=null), empty).load == Load.LIMITED) }
    check(h.decision().causes == listOf(ResourceCause.TELEMETRY_UNAVAILABLE))
    repeat(3) { h.update(clean, empty) }
    check(h.decision().state == ResourceState.RECOVERY)
    val failures = Performance(4, 10.0, .5)
    check(h.update(clean, failures).load == Load.LIMITED)
    check(h.decision().causes == listOf(ResourceCause.COGNITIVE_FAILURES))
    repeat(6) { h.update(clean.copy(thermal=0), empty) }
    check(h.decision().state == ResourceState.ECONOMY)
    repeat(3) { h.update(clean.copy(thermal=0), empty) }
    check(h.decision().state == ResourceState.NORMAL)
    println("PASS: persistent battery heat, relapse during recovery, gradual return to NORMAL")

    now=0
    val m = CycleMetrics({now}, 100)
    m.record(100, false)
    now=50
    m.record(200, true)
    now=100
    check(m.snapshot().samples==1 && m.snapshot().failureRatio==0.0)
    now=150
    check(m.snapshot()==empty)
    repeat(40) { m.record(200, true) }
    check(m.snapshot().samples==32)
    println("PASS: partial expiry, full expiry, and 32-sample bound")
}
