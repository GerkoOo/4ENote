package com.gerko.fourenote.platform

import platform.Foundation.NSRecursiveLock

actual fun newLock(): Lock = object : Lock {
    private val lock = NSRecursiveLock()
    override fun lock() = lock.lock()
    override fun unlock() = lock.unlock()
}
