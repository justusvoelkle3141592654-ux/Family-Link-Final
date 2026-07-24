package com.familylink.ios.data

/**
 * App category as defined in the spec:
 *  - PLUS     -> always allowed, never counts against any limit
 *  - LIMIT    -> has its own dedicated per-app daily limit
 *  - STANDARD -> shares the global daily time budget
 */
enum class AppCategory { PLUS, LIMIT, STANDARD }

data class ManagedApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    /** Only used for LIMIT apps; minutes per day. */
    val limitMinutes: Int = 30
)

/** Live snapshot the UI and lock logic read from. */
data class UsageSnapshot(
    val globalUsedSeconds: Int,
    val globalLimitSeconds: Int,
    val bedtimeActive: Boolean,
    val limitsDisabled: Boolean,
    val perAppUsedSeconds: Map<String, Int>
) {
    val globalRemainingSeconds: Int get() = (globalLimitSeconds - globalUsedSeconds).coerceAtLeast(0)
}
