package app.wakil.agent.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wakil.agent.R
import app.wakil.agent.data.ProviderCatalog
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProviderForNewKey by remember { mutableStateOf<String?>(null) }
    var newKeyInput by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val json = viewModel.getExportJson()
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                    reader.readText()
                }
                if (!json.isNullOrBlank()) {
                    viewModel.inspectImportJson(json)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        // Language Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { viewModel.setLanguage("ar") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentLanguage == "ar") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(text = stringResource(R.string.lang_ar))
                        }
                        Button(
                            onClick = { viewModel.setLanguage("fr") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentLanguage == "fr") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(text = stringResource(R.string.lang_fr))
                        }
                        Button(
                            onClick = { viewModel.setLanguage("en") },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentLanguage == "en") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(text = stringResource(R.string.lang_en))
                        }
                    }
                }
            }
        }

        // Kill Switch Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.kill_switch_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.kill_switch_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(
                        checked = state.isKillSwitchActive,
                        onCheckedChange = viewModel::toggleKillSwitch
                    )
                }
            }
        }

        // Active Projects Limit
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.max_projects_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${state.maxActiveProjects}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = state.maxActiveProjects.toFloat(),
                        onValueChange = { viewModel.setMaxActiveProjects(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }
        }

        // API Keys Management
        item {
            Text(
                text = stringResource(R.string.settings_providers),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        items(ProviderCatalog.entries) { entry ->
            val count = state.providerKeyCounts[entry.id] ?: 0
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = entry.id.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.keys_saved, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { selectedProviderForNewKey = entry.id },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.key_add))
                        }
                        if (count > 0) {
                            Button(
                                onClick = { viewModel.clearApiKeys(entry.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(text = stringResource(R.string.key_clear))
                            }
                        }
                    }
                }
            }
        }

        // Backup & Restore
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.export_backup),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { exportLauncher.launch("wakil_backup.json") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.export_backup))
                        }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = stringResource(R.string.import_backup))
                        }
                    }
                }
            }
        }

        // Action Log & History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.action_logs_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = viewModel::undoLastAction) {
                    Text(text = stringResource(R.string.undo))
                }
            }
        }

        if (state.actionLogs.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.no_logs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            items(state.actionLogs) { log ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = log.result,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = log.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    // Dialog to add key
    selectedProviderForNewKey?.let { providerId ->
        AlertDialog(
            onDismissRequest = {
                selectedProviderForNewKey = null
                newKeyInput = ""
            },
            title = { Text(text = stringResource(R.string.key_field)) },
            text = {
                OutlinedTextField(
                    value = newKeyInput,
                    onValueChange = { newKeyInput = it },
                    label = { Text(text = stringResource(R.string.key_field)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addApiKey(providerId, newKeyInput)
                    selectedProviderForNewKey = null
                    newKeyInput = ""
                }) {
                    Text(text = stringResource(R.string.key_add))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedProviderForNewKey = null
                    newKeyInput = ""
                }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }

    // Import Preview Dialog
    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(text = stringResource(R.string.import_preview_title)) },
            text = {
                Column {
                    Text(text = "${preview.goalsCount} · ${preview.projectsCount} · ${preview.tasksCount} · ${preview.notesCount}")
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmImport) {
                    Text(text = stringResource(R.string.import_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
