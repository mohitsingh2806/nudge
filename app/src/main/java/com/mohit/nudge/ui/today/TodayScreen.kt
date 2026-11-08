package com.mohit.nudge.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohit.nudge.container
import com.mohit.nudge.data.DoseStatus
import com.mohit.nudge.data.Meal
import com.mohit.nudge.data.NudgeRepository
import com.mohit.nudge.data.Slot
import com.mohit.nudge.domain.SlotDose
import com.mohit.nudge.domain.SlotState
import com.mohit.nudge.domain.TodayState
import com.mohit.nudge.ui.common.ColorDot
import com.mohit.nudge.ui.common.NoInsets
import com.mohit.nudge.ui.common.hm
import com.mohit.nudge.ui.common.statusLabel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TodayViewModel(private val repo: NudgeRepository) : ViewModel() {
    val state: StateFlow<TodayState?> =
        repo.todayState().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markSlot(slot: Slot, status: DoseStatus) {
        val day = state.value?.epochDay ?: return
        viewModelScope.launch { repo.markSlot(day, slot, status) }
    }

    fun setDose(slot: Slot, medicineId: Long, status: DoseStatus?) {
        val day = state.value?.epochDay ?: return
        viewModelScope.launch { repo.setDose(day, slot, medicineId, status) }
    }

    fun justAte(meal: Meal) {
        viewModelScope.launch { repo.justAte(meal) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onAddMedicine: () -> Unit) {
    val context = LocalContext.current
    val vm: TodayViewModel = viewModel { TodayViewModel(context.container.repo) }
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Nudge", fontWeight = FontWeight.Bold)
                        Text(
                            (state?.date ?: LocalDate.now()).format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val s = state
            when {
                s == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                !s.hasMedicines -> EmptyToday(onAddMedicine, Modifier.align(Alignment.Center))
                else -> TodayContent(s, vm)
            }
        }
    }
}

@Composable
private fun EmptyToday(onAddMedicine: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No medicines yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Add your medicines and pick when you take them — before or after breakfast, lunch or dinner.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAddMedicine) { Text("Add medicine") }
    }
}

@Composable
private fun TodayContent(s: TodayState, vm: TodayViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "hero") {
            val hero = s.hero
            if (hero != null) {
                HeroCard(
                    st = hero,
                    isNow = s.heroIsNow,
                    onTakenAll = { vm.markSlot(hero.slot, DoseStatus.TAKEN) },
                    onSkip = { vm.markSlot(hero.slot, DoseStatus.SKIPPED) },
                )
            } else {
                AllDoneCard(hasSlots = s.slots.isNotEmpty())
            }
        }
        s.ateCandidate?.let { meal ->
            item(key = "ate") { AteCard(meal, s.settings.afterOffsetMin) { vm.justAte(meal) } }
        }
        if (s.slots.isNotEmpty()) {
            item(key = "header") {
                Text(
                    "Today's schedule",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(s.slots, key = { it.slot.name }) { st ->
            SlotCard(st) { medId, status -> vm.setDose(st.slot, medId, status) }
        }
    }
}

@Composable
private fun HeroCard(st: SlotState, isNow: Boolean, onTakenAll: () -> Unit, onSkip: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (isNow) "NOW" else "NEXT · ${st.time.hm()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            Text(st.slot.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            st.pending.forEach { med ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(med.color, 18.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(med.name, style = MaterialTheme.typography.titleMedium)
                        if (med.dose.isNotBlank()) {
                            Text(med.dose, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onTakenAll, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Taken all", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.End)) { Text("Skip these") }
        }
    }
}

@Composable
private fun AllDoneCard(hasSlots: Boolean) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(24.dp)) {
            Text(
                if (hasSlots) "All done for today 🎉" else "No medicines scheduled today",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (hasSlots) Text("Nice work. See you tomorrow.")
        }
    }
}

@Composable
private fun AteCard(meal: Meal, afterOffsetMin: Int, onAte: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Eating ${meal.label.lowercase()}?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap when you finish — after-food reminder comes $afterOffsetMin min later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = onAte) { Text("I just ate") }
        }
    }
}

@Composable
private fun SlotCard(st: SlotState, onSetDose: (Long, DoseStatus?) -> Unit) {
    Card(Modifier.fillMaxWidth().alpha(if (st.isDone) 0.7f else 1f)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    st.time.hm(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(76.dp),
                )
                Text(st.slot.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    if (st.isDone) "Done ✓" else "${st.pending.size} left",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            st.doses.forEach { DoseLine(it, onSetDose) }
        }
    }
}

@Composable
private fun DoseLine(d: SlotDose, onSetDose: (Long, DoseStatus?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorDot(d.medicine.color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.medicine.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (d.status == DoseStatus.TAKEN) TextDecoration.LineThrough else null,
            )
            if (d.medicine.dose.isNotBlank()) {
                Text(
                    d.medicine.dose,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val status = d.status
        if (status == null) {
            IconButton(onClick = { onSetDose(d.medicine.id, DoseStatus.SKIPPED) }) {
                Icon(Icons.Filled.Close, contentDescription = "Skip")
            }
            FilledTonalIconButton(onClick = { onSetDose(d.medicine.id, DoseStatus.TAKEN) }) {
                Icon(Icons.Filled.Check, contentDescription = "Taken")
            }
        } else {
            // Tap the chip to undo.
            AssistChip(onClick = { onSetDose(d.medicine.id, null) }, label = { Text(statusLabel(status)) })
        }
    }
}
