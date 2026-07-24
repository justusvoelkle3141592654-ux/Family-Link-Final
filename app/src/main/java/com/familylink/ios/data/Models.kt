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
