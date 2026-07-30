package com.familylink.ios.data

/**
 * The streak rules for days the child stayed inside the daily budget.
 *
 * Deliberately free of Android: no Context, no SharedPreferences, no clock of its own. Everything
 * it needs is passed in and everything it decides is returned, which is what makes this the one
 * part of the limit machinery with real unit tests. [Prefs] only stores the result, [LimitEngine]
 * only applies it.
 */

/** How a finished day turned out. */
enum class DayOutcome {
    /** Stayed inside the limit that was really in force. The streak grows. */
    KEPT,

    /** Went past it. The streak falls back to zero and the next day pays a small price. */
    EXCEEDED,

    /**
     * No fair test — the limits were switched off that day, or nothing was measured at all.
     * The streak neither grows nor breaks; the day simply does not count.
     */
    NEUTRAL
}

/**
 * The whole streak state. Immutable: an evaluation returns the next state instead of mutating
 * this one, so a day rollover can be replayed in a test with no hidden state anywhere.
 *
 * @param current             days in a row inside the limit
 * @param longest             the best run ever reached, never reduced
 * @param bonusMinutesToday   milestone reward unlocked for today only
 * @param penaltyMinutesToday reduction today, charged for breaking the streak yesterday
 * @param evaluatedDay        day marker this state was computed for — the double-award guard
 * @param milestoneReached    the milestone that produced [bonusMinutesToday], 0 when none
 */
data class StreakState(
    val current: Int = 0,
    val longest: Int = 0,
    val bonusMinutesToday: Int = 0,
    val penaltyMinutesToday: Int = 0,
    val evaluatedDay: Int = -1,
    val milestoneReached: Int = 0
)

object StreakLogic {

    /**
     * Milestone -> minutes unlocked, ascending. Reaching day three is worth five minutes and it
     * grows from there.
     */
    val MILESTONES: List<Pair<Int, Int>> = listOf(
        3 to 5,
        5 to 10,
        10 to 15,
        20 to 20,
        30 to 25,
        50 to 30
    )

    /** Default reduction for the day after a broken streak. */
    const val DEFAULT_PENALTY_MIN = 5
    const val MAX_PENALTY_MIN = 30

    /**
     * The bonus for reaching exactly [streak] days, or 0 when [streak] is not a milestone.
     *
     * Exactly: the reward is for arriving, not for staying. A streak of four gets nothing — day
     * three was already paid for on day three.
     */
    fun bonusForStreak(streak: Int): Int =
        MILESTONES.firstOrNull { it.first == streak }?.second ?: 0

    /** The next milestone above [streak], or null once every milestone is behind it. */
    fun nextMilestone(streak: Int): Int? =
        MILESTONES.firstOrNull { it.first > streak }?.first

    /** Days still to go until the next milestone, or null when there is none left. */
    fun daysToNextMilestone(streak: Int): Int? =
        nextMilestone(streak)?.let { it - streak }

    /** What waits at the next milestone, or 0 when there is none left. */
    fun nextMilestoneBonus(streak: Int): Int =
        nextMilestone(streak)?.let { bonusForStreak(it) } ?: 0

    /**
     * Fold a finished day into the streak.
     *
     * @param previous       the state before this evaluation
     * @param outcome        how the finished day went
     * @param penaltyMinutes what a broken streak costs the following day
     * @param newDay         day marker of the day now starting
     * @return the state for the day now starting
     */
    fun evaluate(
        previous: StreakState,
        outcome: DayOutcome,
        penaltyMinutes: Int,
        newDay: Int
    ): StreakState {
        // Already evaluated for this day: hand back exactly what is stored. Without this a second
        // rollover on the same day would award the same milestone twice.
        if (previous.evaluatedDay == newDay) return previous

        return when (outcome) {
            DayOutcome.NEUTRAL -> previous.copy(
                bonusMinutesToday = 0,
                penaltyMinutesToday = 0,
                milestoneReached = 0,
                evaluatedDay = newDay
            )

            DayOutcome.EXCEEDED -> previous.copy(
                current = 0,
                bonusMinutesToday = 0,
                penaltyMinutesToday = penaltyMinutes.coerceIn(0, MAX_PENALTY_MIN),
                milestoneReached = 0,
                evaluatedDay = newDay
            )

            DayOutcome.KEPT -> {
                val next = previous.current + 1
                val bonus = bonusForStreak(next)
                StreakState(
                    current = next,
                    longest = maxOf(previous.longest, next),
                    bonusMinutesToday = bonus,
                    penaltyMinutesToday = 0,
                    evaluatedDay = newDay,
                    milestoneReached = if (bonus > 0) next else 0
                )
            }
        }
    }

    /**
     * How a finished day turned out, given what was used and the limit that was really enforced.
     *
     * [limitSeconds] must be the limit as it applied on that day — the base budget, plus anything
     * granted, plus that day's own milestone bonus, minus that day's own penalty. Judging a day
     * against a limit the child never had would either break streaks for time they were allowed
     * to spend or reward them for going over.
     *
     * A day with nothing measured, or one where the limits were switched off, is
     * [DayOutcome.NEUTRAL]: it neither counts nor breaks.
     */
    fun outcomeFor(usedSeconds: Int, limitSeconds: Int, limitsWereOff: Boolean): DayOutcome = when {
        limitsWereOff -> DayOutcome.NEUTRAL
        limitSeconds <= 0 -> DayOutcome.NEUTRAL
        usedSeconds <= 0 -> DayOutcome.NEUTRAL
        usedSeconds <= limitSeconds -> DayOutcome.KEPT
        else -> DayOutcome.EXCEEDED
    }
}
