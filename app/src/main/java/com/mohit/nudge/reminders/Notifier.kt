package com.mohit.nudge.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mohit.nudge.MainActivity
import com.mohit.nudge.R
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.RepeatType
import com.mohit.nudge.data.Slot

object Notifier {
    private const val CHANNEL_ID = "reminders"
    private const val HEADS_UP_CHANNEL_ID = "heads_up"
    private const val HEADS_UP_ID = 2000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminds you to take medicines before and after meals"
            enableVibration(true)
        }
        val headsUp = NotificationChannel(
            HEADS_UP_CHANNEL_ID, "Tomorrow's medicines", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Evening heads-up for medicines you don't take every day"
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        nm.createNotificationChannel(headsUp)
    }

    /** "Tomorrow: Vitamin D — 1 tablet · after breakfast (once a week)" */
    fun showHeadsUp(context: Context, meds: List<Medicine>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val lines = meds.map { m ->
            val dose = if (m.dose.isBlank()) "" else " — ${m.dose}"
            val slots = m.slots().joinToString(", ") { it.label.lowercase() }
            "${m.name}$dose · $slots (${m.repeatText().lowercase()})"
        }
        val title = if (meds.size == 1) "Tomorrow: ${meds.first().name}" else "Tomorrow: ${meds.size} extra medicines"
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }

        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, HEADS_UP_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setColor(0xFF00897B.toInt())
            .setContentTitle(title)
            .setContentText(lines.joinToString("; "))
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(HEADS_UP_ID, notification)
    }

    private fun notificationId(slot: Slot) = 1000 + slot.ordinal

    fun show(
        context: Context,
        slot: Slot,
        day: Long,
        slotTimeMillis: Long,
        meds: List<Medicine>,
        snoozeMin: Int,
        /** Minutes until the slot is marked missed, when this is the final reminder; else null. */
        lastReminderMin: Long? = null,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val lines = meds.map { m ->
            val base = if (m.dose.isBlank()) m.name else "${m.name} — ${m.dose}"
            if (m.repeatType() == RepeatType.DAILY) base else "$base (${m.repeatText().lowercase()})"
        }
        val title = "${slot.label} · ${meds.size} medicine" + if (meds.size == 1) "" else "s"

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine("• $it") }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setColor(0xFF00897B.toInt())
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(false)
            .setOnlyAlertOnce(false)
            .addAction(0, "Taken all", action(context, AlarmReceiver.ACTION_TAKEN_ALL, slot, day, slotTimeMillis, 200))
            .addAction(0, "Snooze $snoozeMin min", action(context, AlarmReceiver.ACTION_SNOOZE, slot, day, slotTimeMillis, 300))
            .addAction(0, "Skip", action(context, AlarmReceiver.ACTION_SKIP, slot, day, slotTimeMillis, 400))
        if (lastReminderMin != null) {
            builder.setSubText("Last reminder · marked missed in ${lastReminderMin.coerceAtLeast(1)} min")
        }

        NotificationManagerCompat.from(context).notify(notificationId(slot), builder.build())
    }

    fun cancel(context: Context, slot: Slot) {
        NotificationManagerCompat.from(context).cancel(notificationId(slot))
    }

    private fun missedId(slot: Slot) = 1100 + slot.ordinal

    /** Shown once when the app stops reminding, so a slot is never marked missed silently. */
    fun showMissed(context: Context, slot: Slot, day: Long, meds: List<Medicine>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val lines = meds.map { m -> if (m.dose.isBlank()) m.name else "${m.name} — ${m.dose}" }
        val title = "Missed · ${slot.label.lowercase()}"
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine("• $it") }
        style.setSummaryText("No more reminders for this slot")

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nudge)
            .setColor(0xFF00897B.toInt())
            .setContentTitle(title)
            .setContentText(lines.joinToString(", "))
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "I took them", action(context, AlarmReceiver.ACTION_TOOK_MISSED, slot, day, 0L, 500))
            .build()
        NotificationManagerCompat.from(context).notify(missedId(slot), notification)
    }

    fun cancelMissed(context: Context, slot: Slot) {
        NotificationManagerCompat.from(context).cancel(missedId(slot))
    }

    private fun action(
        context: Context,
        action: String,
        slot: Slot,
        day: Long,
        slotTimeMillis: Long,
        baseCode: Int,
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(action)
            .putExtra(AlarmReceiver.EXTRA_SLOT, slot.name)
            .putExtra(AlarmReceiver.EXTRA_DAY, day)
            .putExtra(AlarmReceiver.EXTRA_SLOT_TIME, slotTimeMillis)
        return PendingIntent.getBroadcast(
            context, baseCode + slot.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
