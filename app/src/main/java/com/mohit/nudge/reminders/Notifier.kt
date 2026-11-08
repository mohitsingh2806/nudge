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
import com.mohit.nudge.data.Slot

object Notifier {
    private const val CHANNEL_ID = "reminders"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID, "Medicine reminders", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminds you to take medicines before and after meals"
            enableVibration(true)
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun notificationId(slot: Slot) = 1000 + slot.ordinal

    fun show(
        context: Context,
        slot: Slot,
        day: Long,
        slotTimeMillis: Long,
        meds: List<Medicine>,
        snoozeMin: Int,
        showAte: Boolean,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val lines = meds.map { if (it.dose.isBlank()) it.name else "${it.name} — ${it.dose}" }
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
        if (showAte) {
            builder.addAction(0, "I just ate", action(context, AlarmReceiver.ACTION_ATE, slot, day, slotTimeMillis, 400))
        }

        NotificationManagerCompat.from(context).notify(notificationId(slot), builder.build())
    }

    fun cancel(context: Context, slot: Slot) {
        NotificationManagerCompat.from(context).cancel(notificationId(slot))
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
