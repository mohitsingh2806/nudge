package com.mohit.nudge.ui.medicines

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mohit.nudge.container
import com.mohit.nudge.data.Medicine
import com.mohit.nudge.domain.ImportLine
import com.mohit.nudge.domain.ImportParser
import com.mohit.nudge.ui.common.ColorDot
import com.mohit.nudge.ui.common.NoInsets
import com.mohit.nudge.ui.common.PillColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val repo = context.container.repo
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val existing by repo.activeMedicines.collectAsStateWithLifecycle(initialValue = emptyList())
    val existingNames = remember(existing) { existing.map { it.name.trim().lowercase() }.toSet() }
    val parsed = remember(text) { ImportParser.parse(text, LocalDate.now().toEpochDay(), PillColors) }
    val toAdd = parsed.mapNotNull { it.medicine }.filter { it.name.trim().lowercase() !in existingNames }

    Scaffold(
        contentWindowInsets = NoInsets,
        topBar = {
            TopAppBar(
                title = { Text("Import medicines") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "step1") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Ask an AI to convert your prescription", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Copy the prompt, paste it into the Claude app (or any AI chat), attach a photo " +
                            "of your prescription and send.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        clipboard.setText(AnnotatedString(ImportParser.AI_PROMPT))
                        Toast.makeText(context, "Prompt copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy AI prompt") }
                }
            }

            item(key = "step2") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Paste the AI's reply", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Pantocid 40 | 1 tablet | BB | daily | ongoing") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        OutlinedButton(onClick = { clipboard.getText()?.text?.let { text = it } }) {
                            Text("Paste")
                        }
                        if (text.isNotEmpty()) {
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { text = "" }) { Text("Clear") }
                        }
                    }
                }
            }

            if (text.isNotBlank()) {
                item(key = "step3") {
                    Text("3. Check the list", style = MaterialTheme.typography.titleMedium)
                }
                if (parsed.isEmpty()) {
                    item(key = "nothing") {
                        Text(
                            "No medicine lines found. Each line should look like:\nName | Dose | When | How often | Days",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                items(parsed, key = { it.lineNo }) { line ->
                    PreviewRow(line, isDuplicate = line.medicine?.name?.trim()?.lowercase() in existingNames)
                }
                item(key = "add") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = toAdd.isNotEmpty() && !adding,
                            onClick = {
                                adding = true
                                scope.launch {
                                    repo.addMedicines(toAdd)
                                    Toast.makeText(context, "Added ${toAdd.size} medicines", Toast.LENGTH_SHORT).show()
                                    onDone()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        ) {
                            Text(
                                when (toAdd.size) {
                                    0 -> "Nothing to add"
                                    1 -> "Add 1 medicine"
                                    else -> "Add ${toAdd.size} medicines"
                                },
                            )
                        }
                        val errors = parsed.count { it.error != null }
                        Text(
                            (if (errors > 0) "$errors line(s) in red will be skipped — fix them or add those by hand. " else "") +
                                "All start today; you can edit any medicine afterwards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun Medicine.summary(): String = listOfNotNull(
    dose.ifBlank { null },
    slots().joinToString(", ") { it.label.lowercase() },
    repeatText(),
    durationDays?.let { "$it days" } ?: "ongoing",
).joinToString(" · ")

@Composable
private fun PreviewRow(line: ImportLine, isDuplicate: Boolean) {
    val med = line.medicine
    if (med == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Line ${line.lineNo}: ${line.raw}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    line.error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(med.color, 18.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(med.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    med.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isDuplicate) {
                    Text(
                        "Already in your list — will be skipped",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
