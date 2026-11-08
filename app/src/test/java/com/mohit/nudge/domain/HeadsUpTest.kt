package com.mohit.nudge.domain

import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.Slot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HeadsUpTest {
    // Sunday 8 Nov 2026
    private val start = LocalDate.of(2026, 11, 8).toEpochDay()

    private fun med(name: String, interval: Int = 1, weekdays: Set<DayOfWeek> = emptySet()) = Medicine(
        id = name.hashCode().toLong(), name = name, dose = "", color = 0,
        slotMask = Slot.MORNING_AFTER.bit, startEpochDay = start, durationDays = null,
        intervalDays = interval,
        weekdayMask = weekdays.fold(0) { acc, d -> acc or Medicine.weekdayBit(d) },
    )

    private val daily = med("Daily")
    private val alternate = med("Alternate", interval = 2) // due 8, 10, 12 …
    private val sunday = med("Sunday", weekdays = setOf(DayOfWeek.SUNDAY))
    private val monTue = med("MonTue", weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY))
    private val all = listOf(daily, alternate, sunday, monTue)

    private fun names(evening: Long) = headsUpItems(evening, all).map { it.name }

    @Test
    fun dailyNeverIncluded() {
        (0L..14L).forEach { assert("Daily" !in names(start + it)) }
    }

    @Test
    fun alternateDayRemindedOnOffDays() {
        assertEquals(true, "Alternate" in names(start + 1)) // Mon evening -> due Tue 10th
        assertEquals(false, "Alternate" in names(start + 2)) // due 10th itself, not 11th
    }

    @Test
    fun weeklyRemindedSaturdayEvening() {
        assertEquals(listOf("Sunday"), names(start + 6).filter { it == "Sunday" }) // Sat 14th
    }

    @Test
    fun consecutiveDaysOnlyRemindedBeforeFirst() {
        assertEquals(true, "MonTue" in names(start)) // Sun evening -> Mon
        assertEquals(false, "MonTue" in names(start + 1)) // Mon evening: taken today already
    }
}
