package com.mohit.nudge.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

enum class RepeatType { DAILY, EVERY_N_DAYS, WEEKDAYS }

@Entity(tableName = "medicines")
data class Medicine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    /** ARGB colour tag so the pill is easy to recognise. */
    val color: Int,
    /** Bitmask of [Slot.bit]. */
    val slotMask: Int,
    val startEpochDay: Long,
    /** Number of days to take it; null = ongoing. */
    val durationDays: Int?,
    val notes: String = "",
    /** Set when the medicine is deleted; kept so history stays correct. */
    val archivedEpochDay: Long? = null,
    /** Repeat every N days counted from the start date (1 = daily, 2 = alternate days). Ignored when [weekdayMask] != 0. */
    @ColumnInfo(defaultValue = "1") val intervalDays: Int = 1,
    /** Specific weekdays: bit (dayOfWeek.value - 1), Monday = bit 0 … Sunday = bit 6. 0 = not used. */
    @ColumnInfo(defaultValue = "0") val weekdayMask: Int = 0,
) {
    fun slots(): List<Slot> = Slot.fromMask(slotMask)

    fun repeatType(): RepeatType = when {
        weekdayMask != 0 -> RepeatType.WEEKDAYS
        intervalDays > 1 -> RepeatType.EVERY_N_DAYS
        else -> RepeatType.DAILY
    }

    /** Fixed calendar: due days are anchored to the start date; a missed dose doesn't shift later ones. */
    private fun matchesRepeat(epochDay: Long): Boolean = when (repeatType()) {
        RepeatType.DAILY -> true
        RepeatType.EVERY_N_DAYS -> Math.floorMod(epochDay - startEpochDay, intervalDays.toLong()) == 0L
        RepeatType.WEEKDAYS -> weekdayMask and weekdayBit(LocalDate.ofEpochDay(epochDay).dayOfWeek) != 0
    }

    fun isDueOn(epochDay: Long): Boolean =
        epochDay >= startEpochDay &&
            (durationDays == null || epochDay < startEpochDay + durationDays) &&
            (archivedEpochDay == null || epochDay < archivedEpochDay) &&
            matchesRepeat(epochDay)

    fun isDueOn(epochDay: Long, slot: Slot): Boolean =
        (slotMask and slot.bit) != 0 && isDueOn(epochDay)

    /** First day on or after [fromDay] this medicine is due, or null if it has ended. */
    fun nextDueDay(fromDay: Long): Long? = (fromDay..fromDay + 400).firstOrNull { isDueOn(it) }

    /** e.g. "Every day", "Alternate days", "Mon, Thu". */
    fun repeatText(): String = when (repeatType()) {
        RepeatType.DAILY -> "Every day"
        RepeatType.EVERY_N_DAYS -> when (intervalDays) {
            2 -> "Alternate days"
            7 -> "Once a week"
            14 -> "Every 2 weeks"
            else -> "Every $intervalDays days"
        }
        RepeatType.WEEKDAYS -> DayOfWeek.values()
            .filter { weekdayMask and weekdayBit(it) != 0 }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }

    companion object {
        fun weekdayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

enum class DoseStatus { TAKEN, SKIPPED, MISSED }

@Entity(
    tableName = "dose_logs",
    indices = [Index(value = ["epochDay", "slot", "medicineId"], unique = true)],
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val slot: Slot,
    val medicineId: Long,
    val status: DoseStatus,
    val timestamp: Long,
)
