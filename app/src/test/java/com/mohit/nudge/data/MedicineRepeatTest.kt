package com.mohit.nudge.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class MedicineRepeatTest {
    // Sunday 8 Nov 2026
    private val start = LocalDate.of(2026, 11, 8)

    private fun med(interval: Int = 1, weekdays: Set<DayOfWeek> = emptySet(), duration: Int? = null) = Medicine(
        name = "Test", dose = "", color = 0,
        slotMask = Slot.MORNING_AFTER.bit,
        startEpochDay = start.toEpochDay(),
        durationDays = duration,
        intervalDays = interval,
        weekdayMask = weekdays.fold(0) { acc, d -> acc or Medicine.weekdayBit(d) },
    )

    private fun dueDays(m: Medicine, days: Int = 15): List<Int> =
        (0 until days).filter { m.isDueOn(start.plusDays(it.toLong()).toEpochDay()) }

    @Test
    fun daily() {
        assertEquals(RepeatType.DAILY, med().repeatType())
        assertEquals((0 until 15).toList(), dueDays(med()))
    }

    @Test
    fun alternateDaysAnchoredToStart() {
        val m = med(interval = 2)
        assertEquals(RepeatType.EVERY_N_DAYS, m.repeatType())
        assertEquals(listOf(0, 2, 4, 6, 8, 10, 12, 14), dueDays(m))
        assertFalse(m.isDueOn(start.minusDays(2).toEpochDay())) // nothing before start
    }

    @Test
    fun everyTwoWeeks() {
        assertEquals(listOf(0, 14, 28), dueDays(med(interval = 14), days = 30))
    }

    @Test
    fun twiceAWeekOnMonAndThu() {
        val m = med(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        assertEquals(RepeatType.WEEKDAYS, m.repeatType())
        // start is Sunday: Mon = +1, Thu = +4, Mon = +8, Thu = +11
        assertEquals(listOf(1, 4, 8, 11), dueDays(m))
        assertTrue(m.isDueOn(start.plusDays(1).toEpochDay(), Slot.MORNING_AFTER))
        assertFalse(m.isDueOn(start.plusDays(1).toEpochDay(), Slot.NIGHT_AFTER)) // wrong slot
    }

    @Test
    fun oncePerWeekRespectsDuration() {
        val m = med(weekdays = setOf(DayOfWeek.SUNDAY), duration = 10)
        assertEquals(listOf(0, 7), dueDays(m))
    }

    @Test
    fun nextDueDay() {
        val m = med(interval = 2)
        assertEquals(start.plusDays(2).toEpochDay(), m.nextDueDay(start.plusDays(1).toEpochDay()))
        assertEquals(null, med(interval = 2, duration = 3).nextDueDay(start.plusDays(3).toEpochDay()))
    }

    private fun day(offset: Int) = start.plusDays(offset.toLong()).toEpochDay()

    @Test
    fun finishedOnlyAfterTheLastDoseDay() {
        val m = med(duration = 5) // due days 0..4
        assertEquals(day(4), m.lastDueDay())
        assertFalse(m.isFinished(day(0)))
        assertFalse(m.isFinished(day(4))) // last dose is today: still current
        assertTrue(m.isFinished(day(5)))
        assertTrue(m.isFinished(day(30)))
    }

    @Test
    fun alternateDaysFinishAfterLastActualDose() {
        val m = med(interval = 2, duration = 6) // due days 0, 2, 4
        assertEquals(day(4), m.lastDueDay())
        assertFalse(m.isFinished(day(4)))
        assertTrue(m.isFinished(day(5)))
    }

    @Test
    fun ongoingFutureAndDeletedAreNeverFinished() {
        assertFalse(med().isFinished(day(100)))
        assertEquals(null, med().lastDueDay())
        assertFalse(med(duration = 3).isFinished(day(-1))) // not started yet
        assertFalse(med(duration = 3).copy(archivedEpochDay = day(10)).isFinished(day(20)))
    }
}
