package com.mohit.nudge.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.mohit.nudge.MainActivity
import com.mohit.nudge.container
import com.mohit.nudge.data.DoseStatus
import com.mohit.nudge.data.Slot
import com.mohit.nudge.domain.TodayState
import com.mohit.nudge.ui.common.hm

private val SlotKey = ActionParameters.Key<String>("slot")
private val DayKey = ActionParameters.Key<Long>("day")

class NudgeWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.repo
        val initial = repo.todaySnapshot()
        provideContent {
            // Observe the DB so the widget refreshes while its session is alive
            // (e.g. right after tapping "Taken all" on the widget itself).
            val flow = remember { repo.todayState() }
            val state by flow.collectAsState(initial)
            WidgetContent(state)
        }
    }
}

class NudgeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NudgeWidget()
}

class TakenAllAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val slot = parameters[SlotKey]?.let { runCatching { Slot.valueOf(it) }.getOrNull() } ?: return
        val day = parameters[DayKey] ?: return
        context.container.repo.markSlot(day, slot, DoseStatus.TAKEN)
    }
}

@Composable
private fun WidgetContent(state: TodayState) {
    val white = ColorProvider(Color.White)
    val soft = ColorProvider(Color(0xCCFFFFFF))
    val hero = state.hero

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(0xFF00897B))
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (hero == null) {
            Text("Nudge", style = TextStyle(color = soft, fontSize = 12.sp))
            Text(
                if (state.slots.isEmpty()) "No medicines today" else "All done for today ✓",
                style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
        } else {
            Text(
                if (state.heroIsNow) "NOW" else "NEXT · ${hero.time.hm()}",
                style = TextStyle(color = soft, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                hero.slot.label,
                style = TextStyle(color = white, fontSize = 18.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            hero.pending.take(3).forEach { med ->
                Text(
                    "• " + if (med.dose.isBlank()) med.name else "${med.name} · ${med.dose}",
                    style = TextStyle(color = white, fontSize = 14.sp),
                    maxLines = 1,
                )
            }
            if (hero.pending.size > 3) {
                Text("+${hero.pending.size - 3} more", style = TextStyle(color = soft, fontSize = 12.sp))
            }
            Spacer(GlanceModifier.defaultWeight())
            Button(
                text = "Taken all",
                onClick = actionRunCallback<TakenAllAction>(
                    actionParametersOf(SlotKey to hero.slot.name, DayKey to state.epochDay),
                ),
                modifier = GlanceModifier.fillMaxWidth(),
            )
        }
    }
}
