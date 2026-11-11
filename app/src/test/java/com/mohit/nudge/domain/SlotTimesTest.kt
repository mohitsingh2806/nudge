package com.mohit.nudge.domain

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
    fun eveningUsesItsOwnTimeWithNoOffset() {
        assertEquals(LocalTime.of(17, 0), time(Slot.EVENING))
        assertEquals(LocalTime.of(18, 30), time(Slot.EVENING, settings.copy(eveningMin = 18 * 60 + 30)))
        // Offsets only apply to meal slots.
        val shifted = settings.copy(beforeOffsetMin = 45, afterOffsetMin = 30)
        assertEquals(LocalTime.of(17, 0), time(Slot.EVENING, shifted))
    }

    @Test
    fun existingSlotBitsNeverChange() {
        // Saved medicines store these bits; changing them would scramble everyone's data.
        assertEquals(1, Slot.MORNING_BEFORE.bit)
        assertEquals(2, Slot.MORNING_AFTER.bit)
        assertEquals(4, Slot.NOON_BEFORE.bit)
        assertEquals(8, Slot.NOON_AFTER.bit)
        assertEquals(16, Slot.NIGHT_BEFORE.bit)
        assertEquals(32, Slot.NIGHT_AFTER.bit)
        assertEquals(64, Slot.EVENING.bit)
    }

    @Test
    fun eveningShowsBetweenLunchAndDinner() {
        assertEquals(
            listOf(Slot.MORNING_BEFORE, Slot.MORNING_AFTER, Slot.NOON_BEFORE, Slot.NOON_AFTER, Slot.EVENING, Slot.NIGHT_BEFORE, Slot.NIGHT_AFTER),
            Slot.fromMask(127),
        )
        assertEquals(Slot.entries.toSet(), Slot.displayOrder.toSet())
        assertEquals("Evening", Slot.EVENING.label)
    }
}
