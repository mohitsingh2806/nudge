package com.mohit.nudge.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.nudge.container
import com.mohit.nudge.data.Meal
import com.mohit.nudge.ui.common.NoInsets
import com.mohit.nudge.ui.common.minuteOfDayText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val c = context.container
    val settings by c.settings.flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var editingMeal by remember { mutableStateOf<Meal?>(null) }
    var editingHeadsUp by remember { mutableStateOf(false) }
    var editingEvening by remember { mutableStateOf(false) }

    // Re-check permission status whenever the user comes back from system settings.
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeTick++
        onPauseOrDispose { }
    }

    fun change(block: suspend () -> Unit) {
        scope.launch {
            block()
            c.repo.onDataChanged()
        }
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        val s = settings
        if (s == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("Reminder times")
            Meal.entries.forEach { meal ->
                if (meal == Meal.DINNER) {
                    ListItem(
                        modifier = Modifier.clickable { editingEvening = true },
                        headlineContent = { Text("Evening") },
                        supportingContent = { Text("Reminder at this time, food doesn't matter") },
                        trailingContent = {
                            Text(minuteOfDayText(s.eveningMin), style = MaterialTheme.typography.titleMedium)
                        },
                    )
                }
                val mealMin = s.mealMinute(meal)
                ListItem(
                    modifier = Modifier.clickable { editingMeal = meal },
                    headlineContent = { Text(meal.label) },
                    supportingContent = {
                        Text(
                            "Before-food reminder ${minuteOfDayText(mealMin - s.beforeOffsetMin)} · " +
                                "after-food ${minuteOfDayText(mealMin + s.afterOffsetMin)}",
                        )
                    },
                    trailingContent = {
                        Text(minuteOfDayText(mealMin), style = MaterialTheme.typography.titleMedium)
                    },
                )
            }

            HorizontalDivider()
            SectionTitle("Reminder timing")
            StepperRow(
                title = "Before-food medicines",
                subtitle = "${s.beforeOffsetMin} min before the meal",
                onMinus = { change { c.settings.setBeforeOffset((s.beforeOffsetMin - 5).coerceAtLeast(5)) } },
                onPlus = { change { c.settings.setBeforeOffset((s.beforeOffsetMin + 5).coerceAtMost(120)) } },
            )
            StepperRow(
                title = "After-food medicines",
                subtitle = "${s.afterOffsetMin} min after the meal",
                onMinus = { change { c.settings.setAfterOffset((s.afterOffsetMin - 5).coerceAtLeast(0)) } },
                onPlus = { change { c.settings.setAfterOffset((s.afterOffsetMin + 5).coerceAtMost(120)) } },
            )
            StepperRow(
                title = "Remind again every",
                subtitle = "${s.renotifyMin} min until you tap Taken or Skip",
                onMinus = { change { c.settings.setRenotify((s.renotifyMin - 5).coerceAtLeast(5)) } },
                onPlus = { change { c.settings.setRenotify((s.renotifyMin + 5).coerceAtMost(30)) } },
            )
            StepperRow(
                title = "Stop reminding after",
                subtitle = "${s.giveUpMin} min, then mark as missed",
                onMinus = { change { c.settings.setGiveUp((s.giveUpMin - 15).coerceAtLeast(15)) } },
                onPlus = { change { c.settings.setGiveUp((s.giveUpMin + 15).coerceAtMost(240)) } },
            )

            HorizontalDivider()
            SectionTitle("Night-before reminder")
            ListItem(
                headlineContent = { Text("Remind me the evening before") },
                supportingContent = { Text("For medicines you don't take every day (alternate days, weekly…)") },
                trailingContent = {
                    Switch(
                        checked = s.headsUpEnabled,
                        onCheckedChange = { on -> change { c.settings.setHeadsUpEnabled(on) } },
                    )
                },
            )
            if (s.headsUpEnabled) {
                ListItem(
                    modifier = Modifier.clickable { editingHeadsUp = true },
                    headlineContent = { Text("Time") },
                    trailingContent = {
                        Text(minuteOfDayText(s.headsUpMin), style = MaterialTheme.typography.titleMedium)
                    },
                )
            }

            HorizontalDivider()
            SectionTitle("Make reminders reliable")
            ReliabilitySection(context, resumeTick, c.scheduler.canScheduleExact())

            Text(
                "Nudge 1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally),
            )
        }

        editingMeal?.let { meal ->
            TimeDialog(
                title = "${meal.label} time",
                initialMinute = s.mealMinute(meal),
                onDismiss = { editingMeal = null },
                onConfirm = { minute ->
                    change { c.settings.setMealTime(meal, minute) }
                    editingMeal = null
                },
            )
        }
        if (editingEvening) {
            TimeDialog(
                title = "Evening time",
                initialMinute = s.eveningMin,
                onDismiss = { editingEvening = false },
                onConfirm = { minute ->
                    change { c.settings.setEveningTime(minute) }
                    editingEvening = false
                },
            )
        }
        if (editingHeadsUp) {
            TimeDialog(
                title = "Night-before reminder time",
                initialMinute = s.headsUpMin,
                onDismiss = { editingHeadsUp = false },
                onConfirm = { minute ->
                    change { c.settings.setHeadsUpTime(minute) }
                    editingHeadsUp = false
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun StepperRow(title: String, subtitle: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onMinus) { Text("−", style = MaterialTheme.typography.titleLarge) }
                Box(Modifier.width(4.dp))
                FilledTonalIconButton(onClick = onPlus) { Text("+", style = MaterialTheme.typography.titleLarge) }
            }
        },
    )
}

@Composable
private fun ReliabilitySection(context: Context, resumeTick: Int, exactAlarmsOk: Boolean) {
    val notificationsOk = remember(resumeTick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val batteryOk = remember(resumeTick) {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(context.packageName)
    }
    val pkgUri = Uri.parse("package:${context.packageName}")

    PermissionRow(
        title = "Notifications",
        okText = "Allowed",
        badText = "Turned off — you won't see reminders",
        ok = notificationsOk,
    ) {
        context.safeStart(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PermissionRow(
            title = "Exact alarms",
            okText = "Allowed — reminders fire on time",
            badText = "Not allowed — reminders may be late",
            ok = exactAlarmsOk,
        ) {
            context.safeStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri))
        }
    }
    PermissionRow(
        title = "Battery optimisation",
        okText = "Unrestricted — Nudge can run in background",
        badText = "Restricted — some phones block reminders. Tap Fix.",
        ok = batteryOk,
    ) {
        context.safeStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri))
    }
}

@Composable
private fun PermissionRow(title: String, okText: String, badText: String, ok: Boolean, onFix: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                if (ok) okText else badText,
                color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        trailingContent = {
            if (ok) {
                Icon(Icons.Filled.Check, contentDescription = "OK", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onFix) { Text("Fix") }
            }
        },
    )
}

private fun Context.safeStart(intent: Intent) {
    runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
