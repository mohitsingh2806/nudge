package com.mohit.nudge.domain

import com.mohit.nudge.data.Meal
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import com.mohit.nudge.data.Timing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object SlotTimes {

    fun mealTime(
        date: LocalDate,
        meal: Meal,
        s: NudgeSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime {
        val m = s.mealMinute(meal)
        return date.atTime(m / 60, m % 60).atZone(zone)
    }

    /**
     * When the reminder for [slot] on [date] should fire.
     * Before food = meal - beforeOffset.
     * After food  = (time user tapped "I just ate" today, else planned meal time) + afterOffset.
     */
    fun scheduledTime(
        date: LocalDate,
        slot: Slot,
        s: NudgeSettings,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ZonedDateTime {
        val meal = mealTime(date, slot.meal, s, zone)
        return when (slot.timing) {
            Timing.BEFORE -> meal.minusMinutes(s.beforeOffsetMin.toLong())
            Timing.AFTER -> {
                val ate = s.ateAt[slot.meal]?.let { Instant.ofEpochMilli(it).atZone(zone) }
                val base = if (ate != null && ate.toLocalDate() == date) ate else meal
                base.plusMinutes(s.afterOffsetMin.toLong())
            }
        }
    }
}
