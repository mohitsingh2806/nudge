package com.mohit.nudge.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohit.nudge.AppContainer
import com.mohit.nudge.container
import com.mohit.nudge.data.DoseStatus
import com.mohit.nudge.data.Slot
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                handle(context, c, intent)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun handle(context: Context, c: AppContainer, intent: Intent) {
        val slot = intent.getStringExtra(EXTRA_SLOT)
            ?.let { runCatching { Slot.valueOf(it) }.getOrNull() } ?: return
        val day = intent.getLongExtra(EXTRA_DAY, LocalDate.now().toEpochDay())
        val slotTime = intent.getLongExtra(EXTRA_SLOT_TIME, System.currentTimeMillis())

        when (intent.action) {
            ACTION_REMIND -> remind(context, c, slot, day, slotTime)
            ACTION_TAKEN_ALL -> c.repo.markSlot(day, slot, DoseStatus.TAKEN)
            ACTION_SNOOZE -> {
                Notifier.cancel(context, slot)
                c.scheduler.scheduleRenotify(slot, day, slotTime, c.settings.get().renotifyMin)
            }
            ACTION_ATE -> {
                Notifier.cancel(context, slot)
                c.repo.justAte(slot.meal)
            }
        }
    }

    private suspend fun remind(context: Context, c: AppContainer, slot: Slot, day: Long, slotTime: Long) {
        val s = c.settings.get()
        val pending = c.repo.pendingMeds(day, slot)
        val elapsedMin = (System.currentTimeMillis() - slotTime) / 60_000
        when {
            pending.isEmpty() -> Notifier.cancel(context, slot)
            elapsedMin >= s.giveUpMin -> c.repo.markSlot(day, slot, DoseStatus.MISSED)
            else -> {
                val showAte = !s.ateOn(slot.meal, LocalDate.ofEpochDay(day))
                Notifier.show(context, slot, day, slotTime, pending, s.renotifyMin, showAte)
                c.scheduler.scheduleRenotify(slot, day, slotTime, s.renotifyMin)
            }
        }
        // Make sure tomorrow's (or the next) main alarm is armed.
        c.scheduler.rescheduleAll()
        c.repo.refreshWidget()
    }

    companion object {
        const val ACTION_REMIND = "com.mohit.nudge.REMIND"
        const val ACTION_TAKEN_ALL = "com.mohit.nudge.TAKEN_ALL"
        const val ACTION_SNOOZE = "com.mohit.nudge.SNOOZE"
        const val ACTION_ATE = "com.mohit.nudge.ATE"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_DAY = "day"
        const val EXTRA_SLOT_TIME = "slot_time"
    }
}
