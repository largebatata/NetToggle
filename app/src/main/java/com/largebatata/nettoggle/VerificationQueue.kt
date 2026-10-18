package com.largebatata.nettoggle

/** Only one batch is in flight. Tokens reject callbacks from a stopped / superseded sweep. */
internal class VerificationQueue(private val batchSize: Int = 8) {
    private val remaining = ArrayDeque<String>()
    var token: Long = 0
        private set
    private var inFlight = false
    val running get() = inFlight || remaining.isNotEmpty()

    init { require(batchSize in 1..8) }

    fun restart(packages: List<String>): Long {
        cancel()
        remaining.addAll(packages.distinct())
        return token
    }

    fun cancel() {
        token++
        remaining.clear()
        inFlight = false
    }

    fun next(expectedToken: Long): List<String> {
        if (expectedToken != token || inFlight) return emptyList()
        val batch = mutableListOf<String>()
        while (batch.size < batchSize && remaining.isNotEmpty()) batch += remaining.removeFirst()
        inFlight = batch.isNotEmpty()
        return batch
    }

    fun complete(expectedToken: Long): Boolean {
        if (expectedToken != token || !inFlight) return false
        inFlight = false
        return true
    }
}
