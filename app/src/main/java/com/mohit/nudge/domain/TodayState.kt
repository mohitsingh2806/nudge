package com.mohit.nudge.domain

import com.mohit.nudge.data.DoseLog
import com.mohit.nudge.data.DoseStatus
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import java.time.LocalDate
import java.time.ZonedDateTime

data class SlotDose(val medicine: Medicine, val status: DoseStatus?)

data class SlotState(val slot: Slot, val time: ZonedDateTime, val doses: List<SlotDose>) {
    val pending: List<Medicine> get() = doses.filter { it.status == null }.map { it.medicine }
    val isDone: Boolean get() = pending.isEmpty()
}

data class TodayState(
    val date: LocalDate,
    val now: ZonedDateTime,
    val settings: NudgeSettings,
    /** Only slots that have at least one medicine due today, in slot order. */
    val slots: List<SlotState>,
    val hasMedicines: Boolean,
) {
    val epochDay: Long get() = date.toEpochDay()

    /** The slot to act on: earliest one still pending. */
    val hero: SlotState? get() = slots.firstOrNull { !it.isDone }

    val heroIsNow: Boolean get() = hero?.let { !it.time.isAfter(now) } ?: false
}

fun buildToday(
    date: LocalDate,
    now: ZonedDateTime,
    meds: List<Medicine>,
    logs: List<DoseLog>,
    settings: NudgeSettings,
): TodayState {
    val day = date.toEpochDay()
    val logMap = logs.associateBy { it.slot to it.medicineId }
    val slots = Slot.entries.mapNotNull { slot ->
        val due = meds.filter { it.isDueOn(day, slot) }
        if (due.isEmpty()) {
            null
        } else {
            SlotState(
                slot = slot,
                time = SlotTimes.scheduledTime(date, slot, settings),
                doses = due.map { SlotDose(it, logMap[slot to it.id]?.status) },
            )
        }
    }
    return TodayState(date, now, settings, slots, meds.any { it.archivedEpochDay == null })
}
