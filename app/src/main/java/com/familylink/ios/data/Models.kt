package com.familylink.ios.data

/**
 * App category:
 *  - PLUS     -> always allowed, never counts against any limit
 *  - LIMIT    -> has its own dedicated per-app daily limit
 *  - STANDARD -> shares the global daily time budget
 *  - BLOCKED  -> always locked (generally blocked, independent of time)
 */
enum class AppCategory { PLUS, LIMIT, STANDARD, BLOCKED }

/**
 * How the daily budget is measured.
 *
 *  SYSTEM_TOTAL — take the phone's whole foreground time for the day and simply subtract the
 *                 allowed (PLUS) apps. Everything else the child does counts, including apps
 *                 that were never classified. Strict and hard to game.
 *  CATEGORIES   — count only apps explicitly put into STANDARD or LIMIT. An unclassified app
 *                 costs nothing until a parent sorts it. Predictable and forgiving.
 */
enum class UsageMode { SYSTEM_TOTAL, CATEGORIES }

/**
 * Over which stretch a limit is measured.
 *
 *  DAY  — a fresh budget every morning, nothing carries over.
 *  WEEK — one pot for the whole week. The child may spend it all on Monday, or ration it.
 *  BOTH — a weekly pot *and* a daily ceiling inside it, so a single day cannot eat the week.
 */
enum class LimitScope { DAY, WEEK, BOTH } 

data class ManagedApp(
    val packageName: String,
    val label: String,
    val category: AppCategory,
    /** Only used for LIMIT apps; minutes per day. */
    val limitMinutes: Int = 30
)
