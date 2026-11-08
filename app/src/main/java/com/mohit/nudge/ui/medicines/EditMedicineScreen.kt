package com.mohit.nudge.ui.medicines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohit.nudge.container
import com.mohit.nudge.data.Meal
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.data.NudgeRepository
import com.mohit.nudge.data.NudgeSettings
import com.mohit.nudge.data.Slot
import com.mohit.nudge.data.Timing
import com.mohit.nudge.domain.SlotTimes
import com.mohit.nudge.ui.common.NoInsets
import com.mohit.nudge.ui.common.PillColors
import com.mohit.nudge.ui.common.hm
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class EditMedicineViewModel(private val repo: NudgeRepository, private val id: Long) : ViewModel() {
    var name by mutableStateOf("")
    var dose by mutableStateOf("")
    var color by mutableIntStateOf(PillColors.first())
    var slots by mutableStateOf(setOf<Slot>())
    var startDate: LocalDate by mutableStateOf(LocalDate.now())
    var ongoing by mutableStateOf(true)
    var days by mutableStateOf("7")
    var notes by mutableStateOf("")
    var loaded by mutableStateOf(id == 0L)
        private set

    private var original: Medicine? = null

    init {
        if (id != 0L) {
            viewModelScope.launch {
                repo.getMedicine(id)?.let { m ->
                    original = m
                    name = m.name
                    dose = m.dose
                    color = m.color
                    slots = m.slots().toSet()
                    startDate = LocalDate.ofEpochDay(m.startEpochDay)
                    ongoing = m.durationDays == null
                    days = (m.durationDays ?: 7).toString()
                    notes = m.notes
                }
                loaded = true
            }
        }
    }

    val canSave: Boolean
        get() = name.isNotBlank() && slots.isNotEmpty() && (ongoing || (days.toIntOrNull() ?: 0) > 0)

    fun toggle(slot: Slot) {
        slots = if (slot in slots) slots - slot else slots + slot
    }

    fun save(onDone: () -> Unit) {
        if (!canSave) return
        viewModelScope.launch {
            repo.saveMedicine(
                Medicine(
                    id = original?.id ?: 0L,
                    name = name.trim(),
                    dose = dose.trim(),
                    color = color,
                    slotMask = Slot.maskOf(slots),
                    startEpochDay = startDate.toEpochDay(),
                    durationDays = if (ongoing) null else days.toIntOrNull(),
                    notes = notes.trim(),
                    archivedEpochDay = original?.archivedEpochDay,
                ),
            )
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.archiveMedicine(id)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMedicineScreen(id: Long, onDone: () -> Unit) {
    val c = LocalContext.current.container
    val vm: EditMedicineViewModel = viewModel { EditMedicineViewModel(c.repo, id) }
    val settings by c.settings.flow.collectAsStateWithLifecycle(initialValue = NudgeSettings())
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text(if (id == 0L) "Add medicine" else "Edit medicine") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (id != 0L) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!vm.loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("Medicine name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.dose,
                onValueChange = { vm.dose = it },
                label = { Text("Dose (e.g. 1 tablet, 5 ml)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("When do you take it?", style = MaterialTheme.typography.titleMedium)
            SlotGrid(vm.slots, settings, vm::toggle)

            Text("Colour tag", style = MaterialTheme.typography.titleMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillColors.forEach { col ->
                    val selected = col == vm.color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(col))
                            .then(
                                if (selected) {
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { vm.color = col },
                    )
                }
            }

            Text("Start date", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(vm.startDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ongoing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (vm.ongoing) "No end date" else "Stops after a number of days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = vm.ongoing, onCheckedChange = { vm.ongoing = it })
            }
            if (!vm.ongoing) {
                OutlinedTextField(
                    value = vm.days,
                    onValueChange = { v -> vm.days = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Number of days") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            OutlinedTextField(
                value = vm.notes,
                onValueChange = { vm.notes = it },
                label = { Text("Notes (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { vm.save(onDone) },
                enabled = vm.canSave,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = vm.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        vm.startDate = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${vm.name}?") },
            text = { Text("Reminders stop from today. Past history is kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(onDone)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** 3x2 grid: rows = Morning / Noon / Night, columns = Before / After food. */
@Composable
private fun SlotGrid(selected: Set<Slot>, settings: NudgeSettings, onToggle: (Slot) -> Unit) {
    val planned = settings.copy(ateAt = emptyMap())
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.width(76.dp))
            Text(
                "Before food",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                "After food",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
        Meal.entries.forEach { meal ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.width(76.dp)) {
                    Text(meal.period, style = MaterialTheme.typography.titleSmall)
                    Text(
                        meal.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Timing.entries.forEach { timing ->
                    val slot = Slot.of(meal, timing)
                    val isOn = slot in selected
                    FilterChip(
                        selected = isOn,
                        onClick = { onToggle(slot) },
                        label = { Text(SlotTimes.scheduledTime(today, slot, planned).hm()) },
                        leadingIcon = if (isOn) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
