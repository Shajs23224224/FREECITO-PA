package com.omega.vision

/** Diagnostic time base: elapsed realtime at receipt, not completion or wall clock. */
data class TimedObservation<T>(val frameNs: Long, val receivedNs: Long, val value: T, val completedNs: Long = receivedNs)

enum class ObservationState { WAITING, PROCESSING, VALID, EXPIRED, INVALID, PAUSED, FAILED }
data class ObservationHealth(val state: ObservationState, val frameNs: Long?, val ageMs: Long?, val processingMs: Long?, val detail: String)

object ObservationTiming {
    fun <T> fresh(value: TimedObservation<T>?, nowNs: Long, ttlNs: Long): TimedObservation<T>? {
        require(ttlNs > 0)
        return value?.takeIf { it.frameNs >= 0 && it.receivedNs >= 0 && it.completedNs >= it.receivedNs && nowNs >= it.completedNs && nowNs-it.receivedNs <= ttlNs }
    }
    fun health(value: TimedObservation<*>?, nowNs: Long, ttlNs: Long, enabled: Boolean, busy: Boolean, status: String): ObservationHealth {
        val state = when {
            !enabled -> ObservationState.PAUSED
            value != null && fresh(value, nowNs, ttlNs) != null -> ObservationState.VALID
            value != null && (value.receivedNs < 0 || value.frameNs < 0 || value.completedNs < value.receivedNs || nowNs < value.completedNs) -> ObservationState.INVALID
            value != null -> ObservationState.EXPIRED
            status == "no_valid_output" || status == "invalid_frame" -> ObservationState.INVALID
            status.startsWith("unavailable:") || status == "backend_unavailable" || status == "worker_unavailable" -> ObservationState.FAILED
            busy -> ObservationState.PROCESSING
            else -> ObservationState.WAITING
        }
        return ObservationHealth(state, value?.frameNs,
            value?.receivedNs?.takeIf { it >= 0 && nowNs >= it }?.let { (nowNs-it)/1_000_000 },
            value?.takeIf { it.receivedNs >= 0 && it.completedNs >= it.receivedNs }?.let { (it.completedNs-it.receivedNs)/1_000_000 }, status)
    }
    /** Equal frame identifiers are necessary for exact pairing. Never infer geometric correspondence. */
    fun frameGroups(observations: Map<String, TimedObservation<*>?>): Map<Long,List<String>> =
        observations.entries.filter { it.value != null }.groupBy({ it.value!!.frameNs }, { it.key }).toSortedMap()
}
