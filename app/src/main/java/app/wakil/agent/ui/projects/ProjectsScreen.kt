package app.wakil.agent.ui.projects

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.wakil.agent.R
import app.wakil.agent.domain.rules.TaskStatus

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var inputTitle by remember { mutableStateOf("") }
    var inputDesc by remember { mutableStateOf("") }
    var inputNextAction by remember { mutableStateOf("") }

    val tabTitles = listOf(
        stringResource(R.string.projects_title),
        stringResource(R.string.tasks_title),
        stringResource(R.string.goals_title),
        stringResource(R.string.notes_title)
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = {
                inputTitle = ""
                inputDesc = ""
                inputNextAction = ""
                viewModel.toggleAddDialog(true)
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = state.selectedTab) {
                tabTitles.forEachIndexed { index, titleText ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(text = titleText, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.selectedTab) {
                    0 -> { // Projects
                        items(state.projects) { project ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (project.description.isNotBlank()) {
                                        Text(text = project.description, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    if (!project.nextAction.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.next_action_label, project.nextAction),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Tasks
                        items(state.tasks) { task ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = task.status == TaskStatus.DONE,
                                        onCheckedChange = { viewModel.toggleTask(task) }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
                                        if (task.description.isNotBlank()) {
                                            Text(text = task.description, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Goals
                        items(state.goals) { goal ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (goal.description.isNotBlank()) {
                                        Text(text = goal.description, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                    3 -> { // Notes
                        items(state.notes) { note ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(text = note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = note.content, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.toggleAddDialog(false) },
                title = {
                    val addTitleRes = when (state.selectedTab) {
                        0 -> R.string.add_project
                        1 -> R.string.add_task
                        2 -> R.string.add_goal
                        else -> R.string.add_note
                    }
                    Text(text = stringResource(addTitleRes))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = { Text(text = stringResource(R.string.title_field)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = inputDesc,
                            onValueChange = { inputDesc = it },
                            label = { Text(text = stringResource(R.string.description_field)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.selectedTab == 0) {
                            OutlinedTextField(
                                value = inputNextAction,
                                onValueChange = { inputNextAction = it },
                                label = { Text(text = stringResource(R.string.next_action_field)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        when (state.selectedTab) {
                            0 -> viewModel.addProject(inputTitle, inputDesc, inputNextAction)
                            1 -> viewModel.addTask(inputTitle, inputDesc)
                            2 -> viewModel.addGoal(inputTitle, inputDesc)
                            3 -> viewModel.addNote(inputTitle, inputDesc)
                        }
                    }) {
                        Text(text = stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleAddDialog(false) }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}
