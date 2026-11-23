package app.wakil.agent.ui.settings

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import app.wakil.agent.R
import app.wakil.agent.data.ProviderCatalog
import app.wakil.agent.domain.rules.ApprovalStatus
import app.wakil.agent.ui.actionRecordTypeName
import app.wakil.agent.ui.actionTypeName
import app.wakil.agent.ui.providerName
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedProviderForNewKey by remember { mutableStateOf<String?>(null) }
    var newKeyInput by remember { mutableStateOf("") }
    var memoryDraft by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val json = viewModel.getExportJson()
                    val output = context.contentResolver.openOutputStream(it)
                        ?: throw java.io.IOException("Could not open selected backup file")
                    output.use { stream -> stream.write(json.toByteArray(Charsets.UTF_8)) }
                    viewModel.backupExported()
                } catch (_: Exception) {
                    viewModel.setStatusMessage(context.getString(R.string.backup_export_failed))
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader ->
                        reader.readText()
                    }
                    if (!json.isNullOrBlank()) {
                        viewModel.inspectImportJson(json)
                    } else {
                        viewModel.setStatusMessage(context.getString(R.string.backup_import_failed))
                    }
                } catch (_: Exception) {
                    viewModel.setStatusMessage(context.getString(R.string.backup_import_failed))
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
            Text(
                text = stringResource(R.string.provider_key_usage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = stringResource(R.string.provider_cost_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.provider_priority_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.provider_priority_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        itemsIndexed(state.providerPriority, key = { _, providerId -> providerId }) { index, providerId ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        R.string.provider_priority_item,
                        index + 1,
                        stringResource(providerName(providerId))
                    ),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(
                    onClick = { viewModel.moveProvider(providerId, -1) },
                    enabled = index > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.move_provider_up)
                    )
                }
                IconButton(
                    onClick = { viewModel.moveProvider(providerId, 1) },
                    enabled = index < state.providerPriority.lastIndex
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.move_provider_down)
                    )
                }
            }
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
                            text = stringResource(providerName(entry.id)),
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
                    if (entry.id == "cloudflare") {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.cloudflareAccountId,
                            onValueChange = viewModel::updateCloudflareAccountId,
                            label = { Text(text = stringResource(R.string.cloudflare_account_id)) },
                            supportingText = {
                                if (state.cloudflareAccountIdInvalid) {
                                    Text(text = stringResource(R.string.cloudflare_account_id_invalid))
                                } else {
                                    Text(text = stringResource(R.string.cloudflare_account_id_hint))
                                }
                            },
                            isError = state.cloudflareAccountIdInvalid,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = viewModel::saveCloudflareAccountId,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.save))
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
                    Text(
                        text = stringResource(R.string.backup_plaintext_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    state.statusMessage?.let { message ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = message, style = MaterialTheme.typography.bodySmall)
                    }
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.memories_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.memories_privacy_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = memoryDraft,
                        onValueChange = { memoryDraft = it },
                        label = { Text(stringResource(R.string.memory_text)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.proposeMemory(memoryDraft)
                            memoryDraft = ""
                        },
                        enabled = memoryDraft.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.memory_save_for_review))
                    }
                }
            }
        }
        if (state.memories.isEmpty()) {
            item { Text(stringResource(R.string.no_memories), color = MaterialTheme.colorScheme.outline) }
        } else {
            items(state.memories, key = { it.id }) { memory ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(memory.factText, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(
                                if (memory.approvalStatus == ApprovalStatus.APPROVED) R.string.memory_approved else R.string.memory_pending
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (memory.approvalStatus != ApprovalStatus.APPROVED) {
                                Button(onClick = { viewModel.approveMemory(memory) }) {
                                    Text(stringResource(R.string.memory_approve))
                                }
                            }
                            TextButton(onClick = { viewModel.deleteMemory(memory.id) }) {
                                Text(stringResource(R.string.delete))
                            }
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
                            text = stringResource(
                                R.string.action_log_summary,
                                stringResource(actionTypeName(log.actionType)),
                                stringResource(actionRecordTypeName(log.recordType)),
                                log.result
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (log.reason == "USER_ACTION") {
                                stringResource(R.string.reason_user_action)
                            } else {
                                log.reason
                            },
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
                    Text(
                        text = stringResource(
                            R.string.backup_preview_counts,
                            preview.counts.goals,
                            preview.counts.projects,
                            preview.counts.milestones,
                            preview.counts.tasks,
                            preview.counts.notes,
                            preview.counts.noteLinks,
                            preview.counts.conversations,
                            preview.counts.messages,
                            preview.counts.actionLogs,
                            preview.counts.memories
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = stringResource(R.string.backup_conflicts, preview.conflictCount))
                    if (!preview.isValid) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.backup_import_invalid),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::confirmImport, enabled = preview.isValid) {
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
