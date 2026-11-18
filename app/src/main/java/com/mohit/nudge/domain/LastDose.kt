package com.mohit.nudge.domain

import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import java.time.LocalDate

/**
 * True when [slot] on [day] is the final dose of this medicine's course: it is due then, no later
 * day is due, and no other slot of the medicine falls later that same day.
 *
 * Ongoing and deleted medicines never have a last dose. For non-daily medicines the last dose can
 * come a few days before the course formally ends (e.g. alternate days with an even duration).
 */
fun Medicine.isLastDose(day: Long, slot: Slot, s: NudgeSettings): Boolean {
    val duration = durationDays ?: return false
    if (archivedEpochDay != null || !isDueOn(day, slot)) return false
    val endExclusive = startEpochDay + duration
    if ((day + 1 until endExclusive).any { isDueOn(it) }) return false
    // Evening's time is user-set, so compare actual times of day rather than slot order.
    val date = LocalDate.ofEpochDay(day)
    val at = SlotTimes.scheduledTime(date, slot, s)
    return slots().none { it != slot && SlotTimes.scheduledTime(date, it, s).isAfter(at) }
}
