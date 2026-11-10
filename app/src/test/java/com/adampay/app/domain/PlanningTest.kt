package com.adampay.app.domain

import com.adampay.app.core.reminders.Reminders
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.db.TxnType
import com.adampay.app.domain.plan.BudgetStatus
import com.adampay.app.domain.plan.GoalProgress
import com.adampay.app.domain.plan.Streak
import com.adampay.app.domain.plan.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PlanningTest {
    private val zone = ZoneId.of("Asia/Amman")
    private fun at(day: LocalDate, h: Int = 12) = day.atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun spend(day: LocalDate, fils: Long, type: TxnType = TxnType.EXPENSE, source: TxnSource = TxnSource.MANUAL) =
        TxnEntity(type = type, amountFils = fils, fromPocketId = 1, timestamp = at(day), createdAt = at(day), createdBy = source)

    // 2026-10-17 is a Saturday.
    private val saturday = LocalDate.of(2026, 10, 17)

    @Test fun `jordanian week runs saturday to friday`() {
        val w = Week.of(LocalDate.of(2026, 10, 21), DayOfWeek.SATURDAY) // Wednesday
        assertEquals(saturday, w.start)
        assertEquals(LocalDate.of(2026, 10, 23), w.end)
        assertEquals(saturday, Week.of(saturday, DayOfWeek.SATURDAY).start)
        assertEquals(LocalDate.of(2026, 10, 18), Week.of(LocalDate.of(2026, 10, 21), DayOfWeek.SUNDAY).start)
        assertTrue(LocalDate.of(2026, 10, 23) in w)
        assertFalse(LocalDate.of(2026, 10, 24) in w)
    }

    @Test fun `budget - left and per day for the remaining days`() {
        val wednesday = LocalDate.of(2026, 10, 21)
        val txns = listOf(
            spend(saturday, 2_000), spend(LocalDate.of(2026, 10, 20), 1_000),
            spend(LocalDate.of(2026, 10, 16), 9_000), // last week
            spend(wednesday, 500, TxnType.LOST), // lost is not spending
            TxnEntity(type = TxnType.TRANSFER, amountFils = 5_000, fromPocketId = 1, toPocketId = 2, timestamp = at(wednesday)),
        )
        val b = BudgetStatus.of(7_000, txns, wednesday, DayOfWeek.SATURDAY, zone)
        assertEquals(3_000L, b.spent)
        assertEquals(4_000L, b.left)
        assertEquals(3, b.daysLeft) // Wed, Thu, Fri
        assertEquals(1_333L, b.perDay)
        assertFalse(b.over)
        assertEquals(3f / 7f, b.fraction, 0.001f)
    }

    @Test fun `budget - over the limit`() {
        val b = BudgetStatus.of(2_000, listOf(spend(saturday, 2_500)), saturday, DayOfWeek.SATURDAY, zone)
        assertTrue(b.over)
        assertEquals(-500L, b.left)
        assertEquals(0L, b.perDay)
        assertEquals(1f, b.fraction)
        assertEquals(7, b.daysLeft)
    }

    @Test fun `goal - eta from the saving pace, needed per week for a deadline`() {
        val today = saturday
        val g = GoalProgress.of(saved = 10_000, target = 35_000, ratePerDay = 1_000.0, today = today, deadline = today.plusDays(35))
        assertEquals(25_000L, g.remaining)
        assertEquals(today.plusDays(25), g.eta)
        assertEquals(5_000L, g.perWeekNeeded) // 25.000 over 5 weeks
        assertFalse(g.reached)
        assertEquals(10f / 35f, g.fraction, 0.001f)
    }

    @Test fun `goal - reached, no pace, deadline passed`() {
        assertTrue(GoalProgress.of(40_000, 35_000, 0.0, saturday, null).reached)
        assertNull(GoalProgress.of(40_000, 35_000, 500.0, saturday, null).eta)
        assertNull(GoalProgress.of(1_000, 35_000, 0.0, saturday, null).eta)
        assertTrue(GoalProgress.of(1_000, 35_000, 0.0, saturday, saturday.minusDays(1)).deadlinePassed)
        assertNull(GoalProgress.of(1_000, 35_000, 0.0, saturday, saturday.minusDays(1)).perWeekNeeded)
    }

    @Test fun `goal - pocket pace counts net inflow over 28 days`() {
        val today = saturday
        val txns = listOf(
            TxnEntity(type = TxnType.TRANSFER, amountFils = 14_000, fromPocketId = 1, toPocketId = 3, timestamp = at(today.minusDays(3))),
            TxnEntity(type = TxnType.EXPENSE, amountFils = 2_800, fromPocketId = 3, timestamp = at(today.minusDays(1))),
            TxnEntity(type = TxnType.TRANSFER, amountFils = 99_000, fromPocketId = 1, toPocketId = 3, timestamp = at(today.minusDays(40))), // too old
        )
        assertEquals((14_000 - 2_800) / 28.0, GoalProgress.pocketRate(3, txns, today, zone), 0.001)
        assertEquals(0.0, GoalProgress.pocketRate(1, txns, today, zone), 0.0) // money left it: no pace, never negative
        assertEquals(500.0, GoalProgress.manualRate(5_000, today.minusDays(10), today), 0.001)
    }

    @Test fun `streak - consecutive logged days, today pending does not break it`() {
        val days = setOf(saturday.minusDays(1), saturday.minusDays(2), saturday.minusDays(3), saturday.minusDays(6))
        assertEquals(3, Streak.current(days, saturday)) // today not logged yet
        assertEquals(4, Streak.current(days + saturday, saturday))
        assertEquals(0, Streak.current(setOf(saturday.minusDays(2)), saturday)) // yesterday missed
        assertEquals(4, Streak.best(days + saturday))
        assertEquals(0, Streak.best(emptySet()))
    }

    @Test fun `streak - counts what the user logged, not opening cash or count adjustments`() {
        val txns = listOf(
            spend(saturday, 100),
            spend(saturday.minusDays(1), 100, TxnType.OPENING),
            spend(saturday.minusDays(2), 100, TxnType.LOST, TxnSource.COUNT),
            spend(saturday.minusDays(3), 100, source = TxnSource.NOTIFICATION),
        )
        assertEquals(setOf(saturday, saturday.minusDays(3)), Streak.activeDays(txns, zone))
    }

    @Test fun `reminders - next evening time and count due`() {
        val now = LocalDateTime.of(2026, 10, 17, 20, 0)
        assertEquals(LocalDateTime.of(2026, 10, 17, 21, 30), Reminders.next(now, LocalTime.of(21, 30)))
        assertEquals(LocalDateTime.of(2026, 10, 18, 21, 30), Reminders.next(now.withHour(22), LocalTime.of(21, 30)))
        assertEquals(LocalDateTime.of(2026, 10, 18, 21, 30), Reminders.next(LocalDateTime.of(2026, 10, 17, 21, 30), LocalTime.of(21, 30)))
        assertTrue(Reminders.countDue(saturday.minusDays(7), null, saturday, 7))
        assertFalse(Reminders.countDue(saturday.minusDays(6), null, saturday, 7))
        assertTrue(Reminders.countDue(null, saturday.minusDays(10), saturday, 7)) // never counted, using the app for 10 days
        assertFalse(Reminders.countDue(null, null, saturday, 7))
    }
}
