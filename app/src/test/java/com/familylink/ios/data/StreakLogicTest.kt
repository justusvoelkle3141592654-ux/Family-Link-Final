package com.familylink.ios.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the streak rules.
 *
 * Plain JVM tests: [StreakLogic] takes its inputs as arguments and returns its result, so none of
 * this needs an emulator, a Context or a clock. Day numbers are the same markers Prefs uses
 * (year * 1000 + day of year); only their order matters here.
 */
class StreakLogicTest {

    private val day1 = 2026_100
    private val day2 = 2026_101
    private val day3 = 2026_102

    private fun keep(state: StreakState, day: Int, penalty: Int = 5) =
        StreakLogic.evaluate(state, DayOutcome.KEPT, penalty, day)

    private fun miss(state: StreakState, day: Int, penalty: Int = 5) =
        StreakLogic.evaluate(state, DayOutcome.EXCEEDED, penalty, day)

    // ---- counting -------------------------------------------------------

    @Test
    fun `a kept day raises the streak by one`() {
        val after = keep(StreakState(), day1)
        assertEquals(1, after.current)
        assertEquals(1, after.longest)
        assertEquals(day1, after.evaluatedDay)
    }

    @Test
    fun `an exceeded day resets the streak to zero`() {
        val after = miss(StreakState(current = 7, longest = 7), day1)
        assertEquals(0, after.current)
    }

    @Test
    fun `the longest streak is remembered after a reset`() {
        var s = StreakState()
        s = keep(s, day1)
        s = keep(s, day2)
        s = miss(s, day3)
        assertEquals(0, s.current)
        assertEquals(2, s.longest)
    }

    @Test
    fun `a neutral day leaves the streak untouched`() {
        val before = StreakState(current = 4, longest = 9, penaltyMinutesToday = 5)
        val after = StreakLogic.evaluate(before, DayOutcome.NEUTRAL, 5, day1)
        assertEquals(4, after.current)
        assertEquals(9, after.longest)
        // Yesterday's penalty is spent and must not be charged a second time.
        assertEquals(0, after.penaltyMinutesToday)
    }

    // ---- milestones -----------------------------------------------------

    @Test
    fun `the first milestone pays on day three`() {
        var s = StreakState()
        s = keep(s, day1)
        assertEquals(0, s.bonusMinutesToday)
        s = keep(s, day2)
        assertEquals(0, s.bonusMinutesToday)
        s = keep(s, day3)
        assertEquals(3, s.current)
        assertEquals(5, s.bonusMinutesToday)
        assertEquals(3, s.milestoneReached)
    }

    @Test
    fun `a milestone pays once and not again on the following day`() {
        var s = StreakState(current = 2)
        s = keep(s, day1)              // reaches 3 -> +5
        assertEquals(5, s.bonusMinutesToday)
        s = keep(s, day2)              // reaches 4 -> nothing
        assertEquals(4, s.current)
        assertEquals(0, s.bonusMinutesToday)
        assertEquals(0, s.milestoneReached)
    }

    @Test
    fun `evaluating the same day twice awards the bonus only once`() {
        val first = keep(StreakState(current = 2), day1)
        assertEquals(5, first.bonusMinutesToday)
        assertEquals(3, first.current)

        // A second rollover on the same day must change nothing at all.
        val second = keep(first, day1)
        assertEquals(first, second)
        assertEquals(3, second.current)
        assertEquals(5, second.bonusMinutesToday)
    }

    @Test
    fun `every configured milestone is reachable and its reward grows`() {
        var s = StreakState()
        var day = 2026_000
        val paid = LinkedHashMap<Int, Int>()
        repeat(50) {
            day += 1
            s = keep(s, day)
            if (s.bonusMinutesToday > 0) paid[s.current] = s.bonusMinutesToday
        }
        assertEquals(StreakLogic.MILESTONES.toMap(), paid)
        val rewards = paid.values.toList()
        assertTrue("rewards must not shrink", rewards == rewards.sorted())
    }

    @Test
    fun `bonus is only paid for exactly a milestone day`() {
        assertEquals(0, StreakLogic.bonusForStreak(2))
        assertEquals(5, StreakLogic.bonusForStreak(3))
        assertEquals(0, StreakLogic.bonusForStreak(4))
        assertEquals(10, StreakLogic.bonusForStreak(5))
    }

    @Test
    fun `the next milestone is reported until every one is behind us`() {
        assertEquals(3, StreakLogic.nextMilestone(0))
        assertEquals(3, StreakLogic.daysToNextMilestone(0))
        assertEquals(5, StreakLogic.nextMilestone(3))
        assertEquals(2, StreakLogic.daysToNextMilestone(3))
        // At a streak of five the next milestone is day ten, which is worth fifteen minutes —
        // the reward of the milestone ahead, not of the one just passed.
        assertEquals(15, StreakLogic.nextMilestoneBonus(5))
        assertEquals(5, StreakLogic.nextMilestoneBonus(0))
        assertNull(StreakLogic.nextMilestone(50))
        assertNull(StreakLogic.daysToNextMilestone(99))
        assertEquals(0, StreakLogic.nextMilestoneBonus(99))
    }

    // ---- the reset penalty ----------------------------------------------

    @Test
    fun `breaking the streak charges the next day`() {
        val after = miss(StreakState(current = 4, longest = 4), day1, penalty = 10)
        assertEquals(10, after.penaltyMinutesToday)
        assertEquals(0, after.bonusMinutesToday)
    }

    @Test
    fun `the penalty applies to one day only`() {
        val broke = miss(StreakState(current = 4), day1, penalty = 10)
        assertEquals(10, broke.penaltyMinutesToday)
        // Next day kept -> the charge is gone, it is not carried forward.
        val next = keep(broke, day2, penalty = 10)
        assertEquals(0, next.penaltyMinutesToday)
        assertEquals(1, next.current)
    }

    @Test
    fun `the penalty is clamped to the allowed range`() {
        assertEquals(
            StreakLogic.MAX_PENALTY_MIN,
            miss(StreakState(), day1, penalty = 500).penaltyMinutesToday
        )
        assertEquals(0, miss(StreakState(), day1, penalty = -20).penaltyMinutesToday)
    }

    @Test
    fun `a bonus and a penalty are never both in force`() {
        val kept = keep(StreakState(current = 2, penaltyMinutesToday = 5), day1)
        assertEquals(5, kept.bonusMinutesToday)
        assertEquals(0, kept.penaltyMinutesToday)

        val missed = miss(StreakState(current = 2, bonusMinutesToday = 15), day1)
        assertEquals(0, missed.bonusMinutesToday)
        assertEquals(5, missed.penaltyMinutesToday)
    }

    // ---- reading the outcome of a day -----------------------------------

    @Test
    fun `staying inside the limit counts, exactly reaching it still counts`() {
        assertEquals(DayOutcome.KEPT, StreakLogic.outcomeFor(1800, 3600, false))
        assertEquals(DayOutcome.KEPT, StreakLogic.outcomeFor(3600, 3600, false))
        assertEquals(DayOutcome.EXCEEDED, StreakLogic.outcomeFor(3601, 3600, false))
    }

    @Test
    fun `a day with the limits switched off is neutral`() {
        assertEquals(DayOutcome.NEUTRAL, StreakLogic.outcomeFor(99_999, 3600, true))
    }

    @Test
    fun `a day with nothing measured is neutral`() {
        assertEquals(DayOutcome.NEUTRAL, StreakLogic.outcomeFor(0, 3600, false))
        assertEquals(DayOutcome.NEUTRAL, StreakLogic.outcomeFor(600, 0, false))
    }

    @Test
    fun `an unused phone neither builds nor breaks a streak`() {
        val before = StreakState(current = 6, longest = 6)
        val outcome = StreakLogic.outcomeFor(usedSeconds = 0, limitSeconds = 3600, limitsWereOff = false)
        val after = StreakLogic.evaluate(before, outcome, 5, day1)
        assertEquals(6, after.current)
        assertEquals(0, after.penaltyMinutesToday)
    }

    // ---- a run across several days --------------------------------------

    @Test
    fun `a week of kept days then one miss, day by day`() {
        var s = StreakState()
        var day = 2026_200
        // Three kept days: the third pays five minutes.
        repeat(3) { day += 1; s = keep(s, day) }
        assertEquals(3, s.current)
        assertEquals(5, s.bonusMinutesToday)

        // Two more kept days: the fifth pays ten.
        repeat(2) { day += 1; s = keep(s, day) }
        assertEquals(5, s.current)
        assertEquals(10, s.bonusMinutesToday)

        // One day over the limit: back to zero, and tomorrow costs five minutes.
        day += 1
        s = miss(s, day)
        assertEquals(0, s.current)
        assertEquals(5, s.longest)
        assertEquals(5, s.penaltyMinutesToday)
        assertEquals(0, s.bonusMinutesToday)

        // Starting again counts from one, and the charge is settled.
        day += 1
        s = keep(s, day)
        assertEquals(1, s.current)
        assertEquals(5, s.longest)
        assertEquals(0, s.penaltyMinutesToday)
    }
}
