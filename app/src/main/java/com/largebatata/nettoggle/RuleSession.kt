package com.largebatata.nettoggle

/** Process memory only. Time is monotonic; no timer or background work is scheduled. */
internal class RuleSession(private val backgroundTtl: Long = 10 * 60 * 1000L) {
    private var identity: Any? = null
    private var server: Any? = null
    private var backgroundAt: Long? = null
    private var fullRequired = true
    var generation = 0L
        private set

    fun sameServer(server: Any?) = server != null && this.server == server
    fun connected(server: Any, service: Any) {
        val identity = server to service
        if (this.identity != identity) invalidate()
        this.identity = identity
        this.server = server
    }
    fun leave(now: Long) { backgroundAt = now }
    fun enter(now: Long) {
        backgroundAt?.let { if (now < it || now - it >= backgroundTtl) invalidate() }
        backgroundAt = null
    }
    fun invalidate() { fullRequired = true; generation++ }
    fun takeFullRefresh(): Boolean = fullRequired.also { fullRequired = false }
}
