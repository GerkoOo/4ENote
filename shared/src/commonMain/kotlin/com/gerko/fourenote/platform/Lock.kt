package com.gerko.fourenote.platform

/** A reentrant lock for the few places that block instead of suspending. */
interface Lock {
    fun lock()
    fun unlock()
}

expect fun newLock(): Lock

inline fun <T> Lock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}
