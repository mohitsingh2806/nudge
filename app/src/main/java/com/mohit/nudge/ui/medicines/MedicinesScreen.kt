package com.mohit.nudge.ui.medicines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.nudge.container
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.Slot
import com.mohit.nudge.ui.common.ColorDot
import com.mohit.nudge.ui.common.NoInsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicinesScreen(onEdit: (Long) -> Unit) {
    val repo = LocalContext.current.container.repo
    val meds by repo.activeMedicines.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = { TopAppBar(title = { Text("Medicines") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onEdit(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        if (meds.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No medicines yet.\nTap Add to create one.",
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
            Slot.entries.forEach { slot ->
                val inSlot = meds.filter { slot in it.slots() }
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
                        MedicineRow(med) { onEdit(med.id) }
                    }
                }
            }
        }
    }
}

private val DateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

private fun Medicine.scheduleText(): String {
    val today = LocalDate.now().toEpochDay()
    val start = LocalDate.ofEpochDay(startEpochDay)
    return when {
        startEpochDay > today -> "Starts ${start.format(DateFormat)}"
        durationDays == null -> "Ongoing"
        else -> "Until ${start.plusDays(durationDays - 1L).format(DateFormat)}"
    }
}

@Composable
private fun MedicineRow(med: Medicine, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { ColorDot(med.color, 20.dp) },
        headlineContent = { Text(med.name) },
        supportingContent = {
            Text(listOf(med.dose, med.scheduleText()).filter { it.isNotBlank() }.joinToString(" · "))
        },
    )
}
