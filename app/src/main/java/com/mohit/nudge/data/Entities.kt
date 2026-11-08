package com.mohit.nudge.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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
) {
    fun slots(): List<Slot> = Slot.fromMask(slotMask)

    fun isDueOn(epochDay: Long): Boolean =
        epochDay >= startEpochDay &&
            (durationDays == null || epochDay < startEpochDay + durationDays) &&
            (archivedEpochDay == null || epochDay < archivedEpochDay)

    fun isDueOn(epochDay: Long, slot: Slot): Boolean =
        (slotMask and slot.bit) != 0 && isDueOn(epochDay)
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
