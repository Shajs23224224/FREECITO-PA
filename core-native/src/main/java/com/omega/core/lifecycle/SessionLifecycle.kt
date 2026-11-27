package com.omega.core.lifecycle

/** Process-local admission fence. Failed teardown blocks restart. */
class SessionLifecycle {
    enum class State { IDLE, STARTING, RUNNING, STOPPING, BLOCKED }
    data class Snapshot(val state: State, val token: String?)
    @Volatile private var current=Snapshot(State.IDLE,null)
    fun snapshot()=current
    fun canStart()=current.state==State.IDLE
    fun token()=current.takeIf{it.state==State.RUNNING}?.token
    fun accepts(token:String?)=token!=null&&token==token()
    @Synchronized fun begin(token:String):Boolean {
        require(token.isNotBlank())
        if(!canStart())return false
        current=Snapshot(State.STARTING,token);return true
    }
    @Synchronized fun activate(token:String):Boolean {
        if(current!=Snapshot(State.STARTING,token))return false
        current=Snapshot(State.RUNNING,token);return true
    }
    @Synchronized fun retire(token:String):Boolean {
        if(current.token!=token || current.state !in listOf(State.STARTING,State.RUNNING))return false
        current=Snapshot(State.STOPPING,token);return true
    }
    @Synchronized fun complete(token:String, success:Boolean):Boolean {
        if(current!=Snapshot(State.STOPPING,token))return false
        current=Snapshot(if(success)State.IDLE else State.BLOCKED,null);return true
    }
}
