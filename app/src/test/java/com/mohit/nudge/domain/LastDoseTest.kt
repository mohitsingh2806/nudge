package com.mohit.nudge.domain

import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class LastDoseTest {
    // Sunday 8 Nov 2026
    private val start = LocalDate.of(2026, 11, 8).toEpochDay()
    private val s = NudgeSettings()

    private fun med(
        slots: List<Slot> = listOf(Slot.MORNING_AFTER),
        duration: Int? = 5,
        interval: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
        archived: Long? = null,
    ) = Medicine(
        name = "Test", dose = "", color = 0,
        slotMask = Slot.maskOf(slots),
        startEpochDay = start,
        durationDays = duration,
        intervalDays = interval,
        weekdayMask = weekdays.fold(0) { acc, d -> acc or Medicine.weekdayBit(d) },
        archivedEpochDay = archived,
    )

    @Test
    fun dailyLastDayIsLastDose() {
        val m = med(duration = 5) // days 0..4
        assertTrue(m.isLastDose(start + 4, Slot.MORNING_AFTER, s))
        assertFalse(m.isLastDose(start + 3, Slot.MORNING_AFTER, s))
        assertFalse(m.isLastDose(start + 5, Slot.MORNING_AFTER, s)) // course already over
    }

    @Test
    fun onlyTheLatestSlotOfTheLastDay() {
        val m = med(slots = listOf(Slot.MORNING_AFTER, Slot.NIGHT_AFTER), duration = 3)
        assertFalse(m.isLastDose(start + 2, Slot.MORNING_AFTER, s))
        assertTrue(m.isLastDose(start + 2, Slot.NIGHT_AFTER, s))
    }

    @Test
    fun eveningOrderedByActualTime() {
        // Evening set after the after-dinner reminder (20:15) → evening is the last slot of the day.
        val late = s.copy(eveningMin = 22 * 60)
        val m = med(slots = listOf(Slot.EVENING, Slot.NIGHT_AFTER), duration = 1)
        assertTrue(m.isLastDose(start, Slot.EVENING, late))
        assertFalse(m.isLastDose(start, Slot.NIGHT_AFTER, late))
        // Default evening (17:00) is before dinner.
        assertTrue(m.isLastDose(start, Slot.NIGHT_AFTER, s))
    }

    @Test
    fun alternateDaysEndsBeforeCourseEnd() {
        val m = med(interval = 2, duration = 6) // due on 0, 2, 4
        assertTrue(m.isLastDose(start + 4, Slot.MORNING_AFTER, s))
        assertFalse(m.isLastDose(start + 2, Slot.MORNING_AFTER, s))
    }

    @Test
    fun weekdaysLastOccurrence() {
        // Sunday start, Mon + Thu, 10 days → due on +1, +4, +8
        val m = med(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), duration = 10)
        assertTrue(m.isLastDose(start + 8, Slot.MORNING_AFTER, s))
        assertFalse(m.isLastDose(start + 4, Slot.MORNING_AFTER, s))
    }

    @Test
    fun ongoingNeverHasLastDose() {
        assertFalse(med(duration = null).isLastDose(start + 4, Slot.MORNING_AFTER, s))
    }

    @Test
    fun deletedNeverHasLastDose() {
        assertFalse(med(duration = 5, archived = start + 10).isLastDose(start + 4, Slot.MORNING_AFTER, s))
    }

    @Test
    fun notDueInThatSlot() {
        assertFalse(med(duration = 5).isLastDose(start + 4, Slot.NIGHT_AFTER, s))
    }
}
