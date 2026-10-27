package com.gerko.fourenote.platform

import java.util.concurrent.locks.ReentrantLock

actual fun newLock(): Lock = object : Lock {
    private val lock = ReentrantLock()
    override fun lock() = lock.lock()
    override fun unlock() = lock.unlock()
}
