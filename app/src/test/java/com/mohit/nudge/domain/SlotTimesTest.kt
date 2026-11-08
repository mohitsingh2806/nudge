package com.mohit.nudge.domain

import com.mohit.nudge.data.Meal
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class SlotTimesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val date = LocalDate.of(2026, 11, 8)
    private val settings = NudgeSettings() // breakfast 9:00, lunch 12:30, dinner 20:00, -30 / +15

    private fun time(slot: Slot, s: NudgeSettings = settings): LocalTime =
        SlotTimes.scheduledTime(date, slot, s, zone).toLocalTime()

    @Test
    fun defaultSlotTimes() {
        assertEquals(LocalTime.of(8, 30), time(Slot.MORNING_BEFORE))
        assertEquals(LocalTime.of(9, 15), time(Slot.MORNING_AFTER))
        assertEquals(LocalTime.of(12, 0), time(Slot.NOON_BEFORE))
        assertEquals(LocalTime.of(12, 45), time(Slot.NOON_AFTER))
        assertEquals(LocalTime.of(19, 30), time(Slot.NIGHT_BEFORE))
        assertEquals(LocalTime.of(20, 15), time(Slot.NIGHT_AFTER))
    }

    @Test
    fun justAteTodayMovesAfterFoodReminder() {
        val ate = date.atTime(9, 40).atZone(zone).toInstant().toEpochMilli()
        val s = settings.copy(ateAt = mapOf(Meal.BREAKFAST to ate))
        assertEquals(LocalTime.of(9, 55), time(Slot.MORNING_AFTER, s))
        // other meals unaffected
        assertEquals(LocalTime.of(12, 45), time(Slot.NOON_AFTER, s))
    }

    @Test
    fun justAteYesterdayIsIgnored() {
        val ate = date.minusDays(1).atTime(9, 40).atZone(zone).toInstant().toEpochMilli()
        val s = settings.copy(ateAt = mapOf(Meal.BREAKFAST to ate))
        assertEquals(LocalTime.of(9, 15), time(Slot.MORNING_AFTER, s))
    }
}
