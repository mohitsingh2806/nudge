package com.mohit.nudge.data

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.mohit.nudge.domain.TodayState
import com.mohit.nudge.domain.buildToday
import com.mohit.nudge.reminders.Notifier
import com.mohit.nudge.reminders.ReminderScheduler
import com.mohit.nudge.widget.NudgeWidget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZonedDateTime

class NudgeRepository(
    private val context: Context,
    db: NudgeDatabase,
    val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    private val medDao = db.medicineDao()
    private val logDao = db.doseLogDao()

    val activeMedicines: Flow<List<Medicine>> = medDao.observeActive()
    val allMedicines: Flow<List<Medicine>> = medDao.observeAll()

    fun logsBetween(fromDay: Long, toDay: Long): Flow<List<DoseLog>> = logDao.observeRange(fromDay, toDay)

    /** Emits the current time every 30 s so "NOW / NEXT" labels stay fresh. */
    private fun clock(): Flow<ZonedDateTime> = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(30_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun todayState(): Flow<TodayState> =
        clock().map { it.toLocalDate() }.distinctUntilChanged().flatMapLatest { date ->
            combine(
                medDao.observeAll(),
                logDao.observeDay(date.toEpochDay()),
                settings.flow,
                clock(),
            ) { meds, logs, s, now -> buildToday(date, now, meds, logs, s) }
        }

    suspend fun todaySnapshot(): TodayState {
        val now = ZonedDateTime.now()
        val date = now.toLocalDate()
        return buildToday(date, now, medDao.getAll(), logDao.getDay(date.toEpochDay()), settings.get())
    }

    suspend fun getMedicine(id: Long): Medicine? = medDao.get(id)

    suspend fun allMedicinesNow(): List<Medicine> = medDao.getAll()

    suspend fun saveMedicine(medicine: Medicine) {
        if (medicine.id == 0L) medDao.insert(medicine) else medDao.update(medicine)
        onDataChanged()
    }

    suspend fun addMedicines(medicines: List<Medicine>) {
        if (medicines.isEmpty()) return
        medDao.insertAll(medicines)
        onDataChanged()
    }

    suspend fun archiveMedicine(id: Long) = archiveMedicines(listOf(id))

    /** "Delete": hides the medicines from today on, but keeps them so history stays correct. */
    suspend fun archiveMedicines(ids: Collection<Long>) {
        val today = LocalDate.now().toEpochDay()
        ids.forEach { id -> medDao.get(id)?.let { medDao.update(it.copy(archivedEpochDay = today)) } }
        onDataChanged()
    }

    /** Medicines due in [slot] on [day] that have no Taken/Skipped/Missed entry yet. */
    suspend fun pendingMeds(day: Long, slot: Slot): List<Medicine> {
        val logged = logDao.getDay(day).filter { it.slot == slot }.map { it.medicineId }.toSet()
        return medDao.getAll().filter { it.isDueOn(day, slot) && it.id !in logged }
    }

    /** Marks every pending medicine in the slot (the "Taken all" button). */
    suspend fun markSlot(day: Long, slot: Slot, status: DoseStatus) {
        val now = System.currentTimeMillis()
        val logs = pendingMeds(day, slot).map {
            DoseLog(epochDay = day, slot = slot, medicineId = it.id, status = status, timestamp = now)
        }
        if (logs.isNotEmpty()) logDao.upsertAll(logs)
        clearReminder(slot)
        refreshWidget()
    }

    /** Sets (or clears, when [status] is null) a single dose. */
    suspend fun setDose(day: Long, slot: Slot, medicineId: Long, status: DoseStatus?) {
        if (status == null) {
            logDao.delete(day, slot.name, medicineId)
        } else {
            logDao.upsertAll(
                listOf(
                    DoseLog(
                        epochDay = day, slot = slot, medicineId = medicineId,
                        status = status, timestamp = System.currentTimeMillis(),
                    ),
                ),
            )
        }
        if (pendingMeds(day, slot).isEmpty()) clearReminder(slot)
        refreshWidget()
    }

    /** "I just ate": after-food reminder moves to now + after-offset. */
    suspend fun justAte(meal: Meal) {
        settings.setAteAt(meal, System.currentTimeMillis())
        clearReminder(Slot.of(meal, Timing.AFTER))
        scheduler.rescheduleAll()
        refreshWidget()
    }

    suspend fun onDataChanged() {
        scheduler.rescheduleAll()
        refreshWidget()
    }

    private fun clearReminder(slot: Slot) {
        scheduler.cancelRenotify(slot)
        Notifier.cancel(context, slot)
    }

    suspend fun refreshWidget() {
        runCatching { NudgeWidget().updateAll(context) }
    }
}
