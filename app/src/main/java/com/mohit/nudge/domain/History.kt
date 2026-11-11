package com.mohit.nudge.domain

import com.mohit.nudge.data.DoseLog
import com.mohit.nudge.data.DoseStatus
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.Slot
import java.time.LocalDate
import java.time.YearMonth

enum class DayStatus { NONE, UPCOMING, ALL_TAKEN, PARTIAL, MISSED }

data class HistoryDose(val slot: Slot, val medicine: Medicine, val status: DoseStatus?)

data class HistoryState(
    val month: YearMonth,
    val selected: LocalDate,
    val statuses: Map<LocalDate, DayStatus>,
    val selectedRows: List<HistoryDose>,
    /** Taken / expected over past days of the month; null if nothing was due. */
    val adherencePercent: Int?,
    /** Doses not taken (skipped, missed or never logged) over past days of the month. */
    val missedCount: Int,
)

fun dayRows(day: Long, meds: List<Medicine>, logs: List<DoseLog>): List<HistoryDose> {
    val map = logs.associateBy { it.slot to it.medicineId }
    return Slot.displayOrder.flatMap { slot ->
        meds.filter { it.isDueOn(day, slot) }.map { HistoryDose(slot, it, map[slot to it.id]?.status) }
    }
}

fun dayStatus(rows: List<HistoryDose>, date: LocalDate, today: LocalDate): DayStatus = when {
    rows.isEmpty() -> DayStatus.NONE
    date.isAfter(today) -> DayStatus.UPCOMING
    rows.all { it.status == DoseStatus.TAKEN } -> DayStatus.ALL_TAKEN
    date == today && rows.all { it.status == null } -> DayStatus.NONE
    date.isBefore(today) && rows.none { it.status == DoseStatus.TAKEN } -> DayStatus.MISSED
    else -> DayStatus.PARTIAL
}

fun buildHistory(
    month: YearMonth,
    selected: LocalDate,
    meds: List<Medicine>,
    logs: List<DoseLog>,
    today: LocalDate = LocalDate.now(),
): HistoryState {
    val logsByDay = logs.groupBy { it.epochDay }
    var expected = 0
    var taken = 0
    val statuses = (1..month.lengthOfMonth()).associate { d ->
        val date = month.atDay(d)
        val rows = dayRows(date.toEpochDay(), meds, logsByDay[date.toEpochDay()].orEmpty())
        if (date.isBefore(today)) {
            expected += rows.size
            taken += rows.count { it.status == DoseStatus.TAKEN }
        }
        date to dayStatus(rows, date, today)
    }
    return HistoryState(
        month = month,
        selected = selected,
        statuses = statuses,
        selectedRows = dayRows(selected.toEpochDay(), meds, logsByDay[selected.toEpochDay()].orEmpty()),
        adherencePercent = if (expected > 0) taken * 100 / expected else null,
        missedCount = expected - taken,
    )
}
