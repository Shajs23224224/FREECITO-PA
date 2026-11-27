import com.omega.vision.*

fun main(){
    val sample=TimedObservation(10L,100L,"synthetic",150L)
    val ttl=100L
    check(ObservationTiming.fresh(sample,150,ttl)==sample)
    check(ObservationTiming.fresh(sample,200,ttl)==sample)
    check(ObservationTiming.fresh(sample,201,ttl)==null)
    check(ObservationTiming.fresh(sample,149,ttl)==null)
    check(ObservationTiming.fresh(sample.copy(completedNs=99),200,ttl)==null)
    check(ObservationTiming.fresh(sample.copy(frameNs=-1),200,ttl)==null)
    fun state(v:TimedObservation<*>?,now:Long=200,enabled:Boolean=true,busy:Boolean=false,status:String="inference_ok")=ObservationTiming.health(v,now,ttl,enabled,busy,status).state
    check(state(sample)==ObservationState.VALID)
    check(state(sample,201)==ObservationState.EXPIRED)
    check(state(sample,149)==ObservationState.INVALID)
    check(state(sample,enabled=false)==ObservationState.PAUSED)
    check(state(null,busy=true,status="waiting_for_authorized_frame")==ObservationState.PROCESSING)
    check(state(null,status="waiting_for_authorized_frame")==ObservationState.WAITING)
    check(state(null,status="no_valid_output")==ObservationState.INVALID)
    check(state(null,status="invalid_frame")==ObservationState.INVALID)
    check(state(null,status="unavailable:TimeoutException")==ObservationState.FAILED)
    check(state(null,status="backend_unavailable")==ObservationState.FAILED)
    check(state(null,status="worker_unavailable")==ObservationState.FAILED)
    val groups=ObservationTiming.frameGroups(mapOf("a" to sample,"b" to sample.copy(frameNs=11),"c" to sample,"d" to null))
    check(groups==mapOf(10L to listOf("a","c"),11L to listOf("b")))
    check(ObservationTiming.frameGroups(mapOf("a" to null)).isEmpty())
    val h=ObservationTiming.health(TimedObservation(1L,0L,emptyList<Int>(),20_000_000L),35_000_000L,100_000_000L,true,false,"inference_ok")
    check(h.ageMs==35L&&h.processingMs==20L&&h.state==ObservationState.VALID)
    println("PASS: freshness boundary, invalid clocks, all seven states, empty valid output, same-frame grouping, age and processing time")
}
