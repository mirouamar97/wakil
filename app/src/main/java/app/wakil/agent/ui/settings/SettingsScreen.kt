package app.wakil.agent.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.wakil.agent.R
import app.wakil.agent.data.ProviderCatalog
import app.wakil.agent.data.SettingsStore
import app.wakil.agent.ui.chat.ChatViewModel
import app.wakil.agent.ui.providerName

private fun languageLabel(tag: String): Int = when (tag) {
    "ar" -> R.string.lang_ar
    "fr" -> R.string.lang_fr
    else -> R.string.lang_en
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: ChatViewModel, onBack: () -> Unit) {
    var lang by remember { mutableStateOf(vm.settings.language()) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(stringResource(R.string.settings_language)) {
                Column(Modifier.selectableGroup()) {
                    SettingsStore.LANGUAGES.forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(selected = lang == tag, role = Role.RadioButton, onClick = {
                                    lang = tag
                                    vm.settings.setLanguage(tag)
                                })
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang == tag, onClick = null)
                            Text(stringResource(languageLabel(tag)), Modifier.padding(start = 12.dp))
                        }
                    }
                }
            }

            Text(stringResource(R.string.settings_providers), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.keys_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ProviderCatalog.entries.forEach { entry -> ProviderCard(vm, entry.id) }
        }
    }
}

@Composable
private fun ProviderCard(vm: ChatViewModel, id: String) {
    var count by remember { mutableIntStateOf(vm.keyCount(id)) }
    var newKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(vm.model(id)) }
    Card(stringResource(providerName(id))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.keys_saved, count), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text(stringResource(R.string.key_field)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = newKey.isNotBlank(),
                    onClick = {
                        vm.addKey(id, newKey)
                        newKey = ""
                        count = vm.keyCount(id)
                    },
                ) { Text(stringResource(R.string.key_add)) }
                OutlinedButton(
                    enabled = count > 0,
                    onClick = {
                        vm.clearKeys(id)
                        count = vm.keyCount(id)
                    },
                ) { Text(stringResource(R.string.key_clear)) }
            }
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    vm.setModel(id, it)
                },
                label = { Text(stringResource(R.string.model_field)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
