package com.gerko.fourenote.data

/** How long a note stays in Deleted notes before it is deleted for good. */
object Trash {
    const val DAYS = 10

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** When a note trashed at [trashedAt] is deleted for good. */
    fun expiresAt(trashedAt: Long): Long = trashedAt + DAYS * DAY_MS

    /** True once a note trashed at [trashedAt] has stayed in Deleted notes for [DAYS] days. */
    fun isExpired(trashedAt: Long, now: Long): Boolean = now >= expiresAt(trashedAt)

    /**
     * Whole days left before a note trashed at [trashedAt] is deleted for good, counting today
     * as one: 10 right after trashing, 1 on its last day, 0 once it is due.
     */
    fun daysLeft(trashedAt: Long, now: Long): Int {
        val left = expiresAt(trashedAt) - now
        if (left <= 0) return 0
        return ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    /** The line under a trashed note's title. */
    fun daysLeftText(trashedAt: Long, now: Long): String {
        val days = daysLeft(trashedAt, now)
        return when (days) {
            0 -> "Deleted for good today"
            1 -> "Deleted for good in 1 day"
            else -> "Deleted for good in $days days"
        }
    }
}
