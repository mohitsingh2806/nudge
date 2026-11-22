package com.mohit.nudge.ui.medicines

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.nudge.container
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.RepeatType
import com.mohit.nudge.data.Slot
import com.mohit.nudge.ui.common.ColorDot
import com.mohit.nudge.ui.common.NoInsets
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinesScreen(onEdit: (Long) -> Unit, onImport: () -> Unit) {
    val repo = LocalContext.current.container.repo
    val meds by repo.activeMedicines.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Finished courses go to a collapsed section at the bottom instead of mixing with current ones.
    val today = LocalDate.now().toEpochDay()
    val (finished, current) = meds.partition { it.isFinished(today) }
    var showFinished by rememberSaveable { mutableStateOf(false) }
    var finishedChoice by remember { mutableStateOf<Medicine?>(null) }

    // Long-press selection (medicine ids).
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    // Drop ids that no longer exist (e.g. after deleting).
    LaunchedEffect(meds) {
        val ids = meds.map { it.id }.toSet()
        selected = selected.intersect(ids)
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selected.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete selected")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("Medicines") },
                    actions = { TextButton(onClick = onImport) { Text("Import list") } },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = { onEdit(0L) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add") },
                )
            }
        },
    ) { padding ->
        if (meds.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No medicines yet.\nTap Add, or Import list to add many at once.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            if (!selecting) {
                item(key = "hint") {
                    Text(
                        "Tip: long-press a medicine to select and delete several.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
            }
            Slot.displayOrder.forEach { slot ->
                val inSlot = current.filter { slot in it.slots() }
                if (inSlot.isNotEmpty()) {
                    item(key = "header_${slot.name}") {
                        Text(
                            slot.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(inSlot, key = { "${slot.name}_${it.id}" }) { med ->
                        MedicineRow(
                            med = med,
                            subtitle = listOf(med.dose, med.scheduleText()).filter { it.isNotBlank() }.joinToString(" · "),
                            isSelected = med.id in selected,
                            onClick = { if (selecting) toggle(med.id) else onEdit(med.id) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggle(med.id)
                            },
                        )
                    }
                }
            }
            if (current.isEmpty()) {
                item(key = "no_current") {
                    Text(
                        "No current medicines. Tap Add, or restart a finished course below.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (finished.isNotEmpty()) {
                item(key = "finished_header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFinished = !showFinished }
                            .padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                    ) {
                        Text(
                            "Finished (${finished.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (showFinished) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (showFinished) "Hide finished" else "Show finished",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (showFinished) {
                    items(finished.sortedByDescending { it.lastDueDay() ?: it.startEpochDay }, key = { "finished_${it.id}" }) { med ->
                        MedicineRow(
                            med = med,
                            subtitle = listOf(med.dose, med.finishedText()).filter { it.isNotBlank() }.joinToString(" · "),
                            isSelected = med.id in selected,
                            onClick = { if (selecting) toggle(med.id) else finishedChoice = med },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                toggle(med.id)
                            },
                        )
                    }
                }
            }
        }
    }

    finishedChoice?.let { med ->
        val days = med.durationDays ?: 0
        AlertDialog(
            onDismissRequest = { finishedChoice = null },
            title = { Text(med.name) },
            text = {
                Text(
                    "${med.finishedText()}.\n\n" +
                        "Restart course: take it again from today for $days day${if (days == 1) "" else "s"}, " +
                        "same dose and times.\n" +
                        "Remove: hide it from this list. History is kept either way.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    finishedChoice = null
                    scope.launch { repo.restartCourse(med.id) }
                }) { Text("Restart course") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        finishedChoice = null
                        scope.launch { repo.archiveMedicine(med.id) }
                    }) { Text("Remove") }
                    TextButton(onClick = { finishedChoice = null }) { Text("Cancel") }
                }
            },
        )
    }

    if (confirmDelete) {
        val names = meds.filter { it.id in selected }.map { it.name }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(if (names.size == 1) "Delete ${names.first()}?" else "Delete ${names.size} medicines?")
            },
            text = {
                Text(
                    (if (names.size > 1) names.joinToString(", ") + "\n\n" else "") +
                        "Reminders stop from today. Past history is kept.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selected
                    confirmDelete = false
                    selected = emptySet()
                    scope.launch { repo.archiveMedicines(ids) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
private val NextFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

private fun Medicine.scheduleText(): String {
    val today = LocalDate.now().toEpochDay()
    val start = LocalDate.ofEpochDay(startEpochDay)
    val span = when {
        startEpochDay > today -> "Starts ${start.format(DateFormat)}"
        durationDays == null -> "Ongoing"
        else -> "Until ${start.plusDays(durationDays - 1L).format(DateFormat)}"
    }
    if (repeatType() == RepeatType.DAILY) return span
    val next = when (val d = nextDueDay(today)) {
        null -> "no more doses"
        today -> "next: today"
        else -> "next: ${LocalDate.ofEpochDay(d).format(NextFormat)}"
    }
    return "${repeatText()} · $next · $span"
}

/** e.g. "Finished 12 Nov" (the day of the last dose). */
private fun Medicine.finishedText(): String {
    val last = lastDueDay() ?: (startEpochDay + (durationDays ?: 1) - 1)
    return "Finished ${LocalDate.ofEpochDay(last).format(DateFormat)}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MedicineRow(
    med: Medicine,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (isSelected) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
        leadingContent = {
            if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                ColorDot(med.color, 20.dp, Modifier.padding(2.dp))
            }
        },
        headlineContent = { Text(med.name) },
        supportingContent = { Text(subtitle) },
    )
}
