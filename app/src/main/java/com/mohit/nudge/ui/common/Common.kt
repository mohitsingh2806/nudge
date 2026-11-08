package com.mohit.nudge.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mohit.nudge.data.DoseStatus
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Inner screens let the outer scaffold / top app bar handle system insets. */
val NoInsets = WindowInsets(0, 0, 0, 0)

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

fun ZonedDateTime.hm(): String = format(TimeFormat)

fun minuteOfDayText(minute: Int): String {
    val m = Math.floorMod(minute, 24 * 60)
    return LocalTime.of(m / 60, m % 60).format(TimeFormat)
}

val PillColors: List<Int> = listOf(
    0xFFFF7043, // coral
    0xFF26A69A, // teal
    0xFF42A5F5, // blue
    0xFFAB47BC, // purple
    0xFFFFCA28, // yellow
    0xFFEC407A, // pink
    0xFF66BB6A, // green
    0xFF78909C, // grey
).map { it.toInt() }

fun statusLabel(status: DoseStatus): String = when (status) {
    DoseStatus.TAKEN -> "Taken"
    DoseStatus.SKIPPED -> "Skipped"
    DoseStatus.MISSED -> "Missed"
}

@Composable
fun ColorDot(color: Int, size: Dp = 14.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color(color)))
}
