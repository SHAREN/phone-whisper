package com.kafkasl.phonewhisper

internal class SingleFlightGate {
    private val lock = Any()
    private var active = false

    fun tryAcquire(): Boolean = synchronized(lock) {
        if (active) {
            false
        } else {
            active = true
            true
        }
    }

    fun release() = synchronized(lock) {
        active = false
    }
}
