package com.mohit.nudge.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.NudgeDatabase
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.SettingsRepository
import com.mohit.nudge.data.Slot
import com.mohit.nudge.domain.SlotTimes
import java.time.ZonedDateTime

/**
 * Two alarms per slot:
 *  - main: next scheduled time the slot has medicines due (request code 0..5)
 *  - re-notify / snooze: follow-up while the slot is still pending (request code 100..105)
 */
class ReminderScheduler(
    private val context: Context,
    private val db: NudgeDatabase,
    private val settings: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    suspend fun rescheduleAll() {
        val s = settings.get()
        val meds = db.medicineDao().getAll()
        val now = ZonedDateTime.now()
        Slot.entries.forEach { scheduleNext(it, s, meds, now) }
    }

    private fun scheduleNext(slot: Slot, s: NudgeSettings, meds: List<Medicine>, now: ZonedDateTime) {
        val today = now.toLocalDate()
        for (offset in 0L..60L) {
            val date = today.plusDays(offset)
            val day = date.toEpochDay()
            if (meds.none { it.isDueOn(day, slot) }) continue
            val at = SlotTimes.scheduledTime(date, slot, s)
            if (!at.isAfter(now)) continue
            val millis = at.toInstant().toEpochMilli()
            setAlarm(millis, remindIntent(slot, day, millis, REQ_MAIN + slot.ordinal))
            return
        }
        alarmManager.cancel(remindIntent(slot, 0, 0, REQ_MAIN + slot.ordinal))
    }

    fun scheduleRenotify(slot: Slot, day: Long, slotTimeMillis: Long, delayMin: Int) {
        val at = System.currentTimeMillis() + delayMin * 60_000L
        setAlarm(at, remindIntent(slot, day, slotTimeMillis, REQ_RENOTIFY + slot.ordinal))
    }

    fun cancelRenotify(slot: Slot) {
        alarmManager.cancel(remindIntent(slot, 0, 0, REQ_RENOTIFY + slot.ordinal))
    }

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun remindIntent(slot: Slot, day: Long, slotTimeMillis: Long, requestCode: Int): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_REMIND)
            .putExtra(AlarmReceiver.EXTRA_SLOT, slot.name)
            .putExtra(AlarmReceiver.EXTRA_DAY, day)
            .putExtra(AlarmReceiver.EXTRA_SLOT_TIME, slotTimeMillis)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun setAlarm(atMillis: Long, pi: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
    }

    private companion object {
        const val REQ_MAIN = 0
        const val REQ_RENOTIFY = 100
    }
}
