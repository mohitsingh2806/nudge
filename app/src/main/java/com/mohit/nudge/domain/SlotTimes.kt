package com.mohit.nudge.domain

import com.mohit.nudge.data.Meal
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import com.mohit.nudge.data.Timing
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object SlotTimes {

    fun mealTime(
        date: LocalDate,
        meal: Meal,
        s: NudgeSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime = atMinute(date, s.mealMinute(meal), zone)

    private fun atMinute(date: LocalDate, minuteOfDay: Int, zone: ZoneId): ZonedDateTime =
        date.atTime(minuteOfDay / 60, minuteOfDay % 60).atZone(zone)

    /**
     * When the reminder for [slot] on [date] should fire.
     * Before food = meal - beforeOffset. After food = meal + afterOffset. Evening = its own time.
     */
    fun scheduledTime(
        date: LocalDate,
        slot: Slot,
        s: NudgeSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime {
        val meal = slot.meal ?: return atMinute(date, s.eveningMin, zone)
        val mealAt = mealTime(date, meal, s, zone)
        return when (slot.timing) {
            Timing.BEFORE -> mealAt.minusMinutes(s.beforeOffsetMin.toLong())
            else -> mealAt.plusMinutes(s.afterOffsetMin.toLong())
        }
    }
}
