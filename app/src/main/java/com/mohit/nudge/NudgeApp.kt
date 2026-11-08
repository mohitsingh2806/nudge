package com.mohit.nudge

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.mohit.nudge.data.NudgeDatabase
import com.mohit.nudge.data.NudgeRepository
import com.mohit.nudge.data.SettingsRepository
import com.mohit.nudge.reminders.Notifier
import com.mohit.nudge.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NudgeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannel(this)
        container.appScope.launch { container.scheduler.rescheduleAll() }
    }
}

/** Simple manual dependency container (no DI framework needed for an app this size). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db: NudgeDatabase = Room.databaseBuilder(context, NudgeDatabase::class.java, "nudge.db").build()
    val settings = SettingsRepository(context)
    val scheduler = ReminderScheduler(context, db, settings)
    val repo = NudgeRepository(context, db, settings, scheduler)
}

val Context.container: AppContainer
    get() = (applicationContext as NudgeApp).container
