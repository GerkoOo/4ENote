package com.gerko.fourenote.data.diagnostics

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A small log of crashes and errors, kept on this device only. It holds messages and stack traces,
 * never note content, titles or file contents. The user can export or clear it from Support.
 */
object CrashLog {
    /** What the log says about the device; injectable so the JVM tests run without Android. */
    interface DeviceInfo {
        val appVersion: String
        val sdkInt: Int
        val manufacturer: String
        val model: String
    }

    private const val MAX_BYTES = 256 * 1024
    private const val SEPARATOR = "----"

    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC)

    @Volatile
    private var file: File? = null

    @Volatile
    private var device: DeviceInfo? = null

    /** Starts logging; [info] defaults to the real device and [appVersion]. */
    fun install(file: File, appVersion: String, info: DeviceInfo? = null) {
        this.file = file
        this.device = info ?: AndroidDeviceInfo(appVersion)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Never let logging get in the way of the crash itself.
            runCatching { append("FATAL", "Uncaught exception in ${thread.name}", error, thread.name) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Adds a non-fatal error; does nothing before [install]. */
    fun record(what: String, error: Throwable) {
        runCatching { append("ERROR", what, error, Thread.currentThread().name) }
    }

    /** The whole log, oldest entry first; empty when nothing was recorded. */
    fun text(): String = runCatching { file?.takeIf { it.isFile }?.readText().orEmpty() }.getOrDefault("")

    fun isEmpty(): Boolean = text().isBlank()

    fun clear() {
        runCatching { file?.delete() }
    }

    @Synchronized
    private fun append(level: String, what: String, error: Throwable, threadName: String) {
        val target = file ?: return
        val info = device ?: return
        val entry = buildString {
            append(timeFormat.format(Instant.now()))
            append(' ').append(level)
            append(' ').append(what)
            append('\n')
            append("app ").append(info.appVersion)
            append(" · Android ").append(info.sdkInt)
            append(" · ").append(info.manufacturer).append(' ').append(info.model)
            append(" · thread ").append(threadName)
            append('\n')
            append(error.stackTraceToString())
            if (!endsWith("\n")) append('\n')
        }
        val existing = target.takeIf { it.isFile }?.readText().orEmpty()
        val combined = if (existing.isBlank()) entry else existing + SEPARATOR + "\n" + entry
        target.parentFile?.mkdirs()
        target.writeText(cap(combined))
    }

    /** Drops the oldest entries until the text fits [MAX_BYTES]. */
    private fun cap(text: String): String {
        if (text.toByteArray().size <= MAX_BYTES) return text
        val entries = text.split(SEPARATOR + "\n")
        // Keep the newest entries that fit, dropping from the front.
        var start = entries.size
        var size = 0
        while (start > 0) {
            val candidate = entries[start - 1]
            val added = candidate.toByteArray().size + if (start < entries.size) SEPARATOR.length + 1 else 0
            if (size + added > MAX_BYTES) break
            size += added
            start--
        }
        return entries.subList(start, entries.size).joinToString(SEPARATOR + "\n")
    }

    private class AndroidDeviceInfo(override val appVersion: String) : DeviceInfo {
        override val sdkInt: Int get() = android.os.Build.VERSION.SDK_INT
        override val manufacturer: String get() = android.os.Build.MANUFACTURER
        override val model: String get() = android.os.Build.MODEL
    }
}
