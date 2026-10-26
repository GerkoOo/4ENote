package com.gerko.fourenote.data.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashLogTest {
    private lateinit var dir: File
    private lateinit var log: File
    private var previous: Thread.UncaughtExceptionHandler? = null

    private val device = object : CrashLog.DeviceInfo {
        override val appVersion = "1.0 (1)"
        override val sdkInt = 34
        override val manufacturer = "Test"
        override val model = "Device"
    }

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("crashlog").toFile()
        log = File(dir, "crash-log.txt")
        previous = Thread.getDefaultUncaughtExceptionHandler()
        CrashLog.install(log, device.appVersion, device)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previous)
        CrashLog.clear()
        dir.deleteRecursively()
    }

    @Test
    fun recordWritesAnEntry() {
        CrashLog.record("Something failed", IllegalStateException("boom"))
        val text = CrashLog.text()
        assertTrue(text.contains("ERROR Something failed"))
        assertTrue(text.contains("IllegalStateException: boom"))
        assertTrue(text.contains("Test Device"))
        assertFalse(CrashLog.isEmpty())
    }

    @Test
    fun uncaughtHandlerWritesFatalAndCallsPrevious() {
        var called = false
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> called = true }
        // Reinstall so the new previous handler is the one chained to.
        CrashLog.install(log, device.appVersion, device)
        val thread = Thread.currentThread()
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(thread, RuntimeException("fatal"))
        assertTrue(called)
        val text = CrashLog.text()
        assertTrue(text.contains("FATAL"))
        assertTrue(text.contains("RuntimeException: fatal"))
    }

    @Test
    fun capKeepsNewestEntriesAndStaysUnderLimit() {
        val big = "x".repeat(8 * 1024)
        repeat(80) { CrashLog.record("entry $it", RuntimeException(big)) }
        val text = CrashLog.text()
        assertTrue(text.toByteArray().size <= 256 * 1024)
        assertTrue(text.contains("entry 79"))
        assertFalse(text.contains("entry 0\n"))
    }

    @Test
    fun clearEmptiesTheLog() {
        CrashLog.record("Something failed", RuntimeException("boom"))
        CrashLog.clear()
        assertTrue(CrashLog.isEmpty())
        assertEquals("", CrashLog.text())
    }
}
