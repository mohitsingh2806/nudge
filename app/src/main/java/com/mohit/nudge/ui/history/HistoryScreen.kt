package com.mohit.nudge.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohit.nudge.container
import com.mohit.nudge.data.DoseStatus
import com.mohit.nudge.data.NudgeRepository
import com.mohit.nudge.domain.DayStatus
import com.mohit.nudge.domain.HistoryDose
import com.mohit.nudge.domain.HistoryState
import com.mohit.nudge.domain.buildHistory
import com.mohit.nudge.ui.common.ColorDot
import com.mohit.nudge.ui.common.NoInsets
import com.mohit.nudge.ui.common.statusLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val Green = Color(0xFF43A047)
private val Amber = Color(0xFFFFB300)
private val Red = Color(0xFFE53935)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(repo: NudgeRepository) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selected = MutableStateFlow(LocalDate.now())

    // Pair each batch of logs with the month it belongs to, so a month switch never
    // briefly renders the new month with the previous month's logs.
    private val monthLogs = month.flatMapLatest { ym ->
        repo.logsBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay()).map { ym to it }
    }

    val state: StateFlow<HistoryState?> =
        combine(repo.allMedicines, monthLogs, selected) { meds, (ym, logs), sel ->
            val day = if (YearMonth.from(sel) == ym) sel else ym.atDay(1)
            buildHistory(ym, day, meds, logs)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun changeMonth(delta: Long) {
        val ym = month.value.plusMonths(delta)
        val today = LocalDate.now()
        selected.value = if (YearMonth.from(today) == ym) today else ym.atDay(1)
        month.value = ym
    }

    fun select(date: LocalDate) {
        selected.value = date
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val vm: HistoryViewModel = viewModel { HistoryViewModel(context.container.repo) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("History") }) },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "calendar") {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        MonthHeader(s.month, onPrev = { vm.changeMonth(-1) }, onNext = { vm.changeMonth(1) })
                        CalendarGrid(s, vm::select)
                        Legend()
                    }
                }
            }
            item(key = "summary") {
                val text = s.adherencePercent?.let {
                    "Taken $it% of doses this month · ${s.missedCount} not taken"
                } ?: "No past doses this month yet"
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            item(key = "day_header") {
                Text(
                    s.selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (s.selectedRows.isEmpty()) {
                item(key = "empty") {
                    Text("No medicines were due.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(s.selectedRows, key = { "${it.slot.name}_${it.medicine.id}" }) { row ->
                HistoryRow(row, isPast = s.selected.isBefore(LocalDate.now()))
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun CalendarGrid(s: HistoryState, onSelect: (LocalDate) -> Unit) {
    val first = s.month.atDay(1)
    val blanks = first.dayOfWeek.value % 7 // Sunday-first
    val cells: List<LocalDate?> = List(blanks) { null } + (1..s.month.lengthOfMonth()).map { s.month.atDay(it) }
    val today = LocalDate.now()

    Column {
        Row {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { date ->
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(3.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                status = s.statuses[date] ?: DayStatus.NONE,
                                isSelected = date == s.selected,
                                isToday = date == today,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, status: DayStatus, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val (bg, fg) = when (status) {
        DayStatus.ALL_TAKEN -> Green to Color.White
        DayStatus.PARTIAL -> Amber to Color.Black
        DayStatus.MISSED -> Red to Color.White
        DayStatus.NONE, DayStatus.UPCOMING -> Color.Transparent to MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(bg)
            .then(
                if (isSelected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = fg,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        LegendItem(Green, "All taken")
        LegendItem(Amber, "Partly")
        LegendItem(Red, "Missed")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.padding(end = 6.dp).clip(CircleShape).background(color).width(10.dp).aspectRatio(1f))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HistoryRow(row: HistoryDose, isPast: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        ColorDot(row.medicine.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.medicine.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                row.slot.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val status = row.status
        val (text, color) = if (status == null) {
            (if (isPast) "Not logged" else "Pending") to MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            statusLabel(status) to (if (status == DoseStatus.TAKEN) Green else Red)
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
