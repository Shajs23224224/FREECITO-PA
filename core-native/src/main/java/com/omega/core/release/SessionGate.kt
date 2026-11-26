package com.omega.core.release

enum class Connection { UNCONFIGURED, INVALID, READ_ONLY, COMMAND_READY }

object SessionGate {
    fun connection(scope:String?,portScope:String?):Connection = when {
        scope==null -> Connection.UNCONFIGURED
        scope.isBlank() || scope.length>200 -> Connection.INVALID
        portScope==null -> Connection.READ_ONLY
        portScope!=scope -> Connection.INVALID
        else -> Connection.COMMAND_READY
    }
    fun captureAllowed(connection:Connection,attested:Boolean):Boolean = attested && connection in setOf(Connection.READ_ONLY,Connection.COMMAND_READY)
    fun fresh(observedNs:Long?,nowNs:Long,maxAgeNs:Long=1_500_000_000L):Boolean = observedNs!=null && observedNs>=0 && nowNs>=observedNs && maxAgeNs>0 && nowNs-observedNs<=maxAgeNs
    fun commandAllowed(connection:Connection,attested:Boolean,enabled:Boolean,visualNs:Long?,nowNs:Long):Boolean = connection==Connection.COMMAND_READY && attested && enabled && fresh(visualNs,nowNs)
}
