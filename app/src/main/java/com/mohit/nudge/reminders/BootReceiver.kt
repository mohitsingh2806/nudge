package com.mohit.nudge.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mohit.nudge.container
import kotlinx.coroutines.launch

/** Alarms are wiped on reboot / app update / clock change, so re-arm them. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val c = context.container
        c.appScope.launch {
            try {
                c.scheduler.rescheduleAll()
                c.repo.refreshWidget()
            } finally {
                result.finish()
            }
        }
    }
}
