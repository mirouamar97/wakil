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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import app.wakil.agent.data.local.entities.MilestoneEntity
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.domain.rules.TaskPriority
import app.wakil.agent.domain.rules.TaskStatus
import app.wakil.agent.ui.projectStatusName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private data class EditableLifeRecord(
    val type: String,
    val id: String,
    val title: String,
    val description: String,
    val nextAction: String = "",
    val goalId: String? = null,
    val projectId: String? = null,
    val dueDate: Long? = null,
    val priority: TaskPriority = TaskPriority.MEDIUM,
)

private fun parseLocalDate(value: String): Long? = runCatching {
    LocalDate.parse(value.trim()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

private fun localDateText(value: Long?): String = value?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}.orEmpty()

@Composable
private fun RecordSelector(
    label: String,
    noneLabel: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    allowNone: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selectedId }?.second ?: noneLabel
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }) {
            Text("$label: $selectedLabel")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowNone) {
                DropdownMenuItem(
                    text = { Text(noneLabel) },
                    onClick = { onSelect(""); expanded = false }
                )
            }
            options.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = { onSelect(id); expanded = false }
                )
            }
        }
    }
}

@Composable
fun ProjectsScreen(viewModel: ProjectsViewModel) {
    val state by viewModel.uiState.collectAsState()
    var inputTitle by remember { mutableStateOf("") }
    var inputDesc by remember { mutableStateOf("") }
    var inputNextAction by remember { mutableStateOf("") }
    var inputGoalId by remember { mutableStateOf("") }
    var inputProjectId by remember { mutableStateOf("") }
    var inputTaskDueDate by remember { mutableStateOf("") }
    var inputTaskPriority by remember { mutableStateOf(TaskPriority.MEDIUM.name) }
    var recordBeingEdited by remember { mutableStateOf<EditableLifeRecord?>(null) }
    var recordPendingDelete by remember { mutableStateOf<EditableLifeRecord?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDescription by remember { mutableStateOf("") }
    var editNextAction by remember { mutableStateOf("") }
    var editGoalId by remember { mutableStateOf("") }
    var editProjectId by remember { mutableStateOf("") }
    var editTaskDueDate by remember { mutableStateOf("") }
    var editTaskPriority by remember { mutableStateOf(TaskPriority.MEDIUM.name) }
    var statusMenuProjectId by remember { mutableStateOf<String?>(null) }
    var milestoneProjectId by remember { mutableStateOf<String?>(null) }
    var milestoneTitle by remember { mutableStateOf("") }
    var milestoneBeingEdited by remember { mutableStateOf<MilestoneEntity?>(null) }
    var milestonePendingDelete by remember { mutableStateOf<MilestoneEntity?>(null) }
    var editedMilestoneTitle by remember { mutableStateOf("") }
    var noteToLinkId by remember { mutableStateOf<String?>(null) }
    var linkTargetType by remember { mutableStateOf("PROJECT") }
    var linkTargetId by remember { mutableStateOf("") }
    var showLinkTypeMenu by remember { mutableStateOf(false) }
    var showLinkTargetMenu by remember { mutableStateOf(false) }

    fun edit(record: EditableLifeRecord) {
        recordBeingEdited = record
        editTitle = record.title
        editDescription = record.description
        editNextAction = record.nextAction
        editGoalId = record.goalId.orEmpty()
        editProjectId = record.projectId.orEmpty()
        editTaskDueDate = localDateText(record.dueDate)
        editTaskPriority = record.priority.name
    }

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
                inputGoalId = ""
                inputProjectId = ""
                inputTaskDueDate = ""
                inputTaskPriority = TaskPriority.MEDIUM.name
                viewModel.toggleAddDialog(true)
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_item))
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
                    0 -> items(state.projects, key = { it.id }) { project ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(project.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        TextButton(onClick = { statusMenuProjectId = project.id }) {
                                            Text(stringResource(projectStatusName(project.status)))
                                        }
                                        DropdownMenu(
                                            expanded = statusMenuProjectId == project.id,
                                            onDismissRequest = { statusMenuProjectId = null }
                                        ) {
                                            ProjectStatus.entries.forEach { status ->
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(projectStatusName(status))) },
                                                    onClick = {
                                                        statusMenuProjectId = null
                                                        viewModel.changeProjectStatus(project.id, status)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    IconButton(onClick = {
                                        edit(EditableLifeRecord(
                                            "PROJECT", project.id, project.title, project.description,
                                            nextAction = project.nextAction.orEmpty(), goalId = project.goalId
                                        ))
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                    }
                                    IconButton(onClick = {
                                        recordPendingDelete = EditableLifeRecord("PROJECT", project.id, project.title, project.description)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                    }
                                }
                                if (project.description.isNotBlank()) {
                                    Text(project.description, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (!project.nextAction.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.next_action_label, project.nextAction),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val projectMilestones = state.milestones.filter { it.projectId == project.id }
                                Text(
                                    text = stringResource(R.string.milestones_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (projectMilestones.isEmpty()) {
                                    Text(
                                        stringResource(R.string.no_milestones),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    projectMilestones.forEach { milestone ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("• ${milestone.title}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                            IconButton(onClick = {
                                                milestoneBeingEdited = milestone
                                                editedMilestoneTitle = milestone.title
                                            }) {
                                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                            }
                                            IconButton(onClick = { milestonePendingDelete = milestone }) {
                                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick = {
                                    milestoneTitle = ""
                                    milestoneProjectId = project.id
                                }) {
                                    Text(stringResource(R.string.add_milestone))
                                }
                            }
                        }
                    }
                    1 -> items(state.tasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.status == TaskStatus.DONE,
                                    onCheckedChange = { viewModel.toggleTask(task) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(task.title, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        stringResource(
                                            when (task.priority) {
                                                TaskPriority.LOW -> R.string.priority_low
                                                TaskPriority.MEDIUM -> R.string.priority_medium
                                                TaskPriority.HIGH -> R.string.priority_high
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (task.description.isNotBlank()) {
                                        Text(task.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    task.projectId?.let { projectId ->
                                        state.projects.firstOrNull { it.id == projectId }?.let { project ->
                                            Text(stringResource(R.string.linked_project, project.title), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    task.goalId?.let { goalId ->
                                        state.goals.firstOrNull { it.id == goalId }?.let { goal ->
                                            Text(stringResource(R.string.linked_goal, goal.title), style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    task.dueDate?.let { dueDate ->
                                        Text(stringResource(R.string.due_date_display, localDateText(dueDate)), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                IconButton(onClick = {
                                    edit(EditableLifeRecord(
                                        "TASK", task.id, task.title, task.description,
                                        projectId = task.projectId, goalId = task.goalId, dueDate = task.dueDate,
                                        priority = task.priority
                                    ))
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                }
                                IconButton(onClick = {
                                    recordPendingDelete = EditableLifeRecord("TASK", task.id, task.title, task.description)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                    2 -> items(state.goals, key = { it.id }) { goal ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (goal.description.isNotBlank()) {
                                        Text(goal.description, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                                IconButton(onClick = {
                                    edit(EditableLifeRecord("GOAL", goal.id, goal.title, goal.description))
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                }
                                IconButton(onClick = {
                                    recordPendingDelete = EditableLifeRecord("GOAL", goal.id, goal.title, goal.description)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                    3 -> items(state.notes, key = { it.id }) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(note.content, style = MaterialTheme.typography.bodyMedium)
                                    val linkedRecords = state.noteLinks.filter { it.noteId == note.id }
                                    if (linkedRecords.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            stringResource(R.string.linked_records),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        linkedRecords.forEach { link ->
                                            val linkedName = when (link.targetType) {
                                                "PROJECT" -> state.projects.firstOrNull { it.id == link.targetId }?.title
                                                "GOAL" -> state.goals.firstOrNull { it.id == link.targetId }?.title
                                                "TASK" -> state.tasks.firstOrNull { it.id == link.targetId }?.title
                                                else -> null
                                            } ?: stringResource(R.string.link_target_missing)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(linkedName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                                IconButton(onClick = { viewModel.deleteNoteLink(link.id) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                                }
                                            }
                                        }
                                    }
                                    TextButton(onClick = {
                                        noteToLinkId = note.id
                                        linkTargetType = "PROJECT"
                                        linkTargetId = state.projects.firstOrNull()?.id.orEmpty()
                                    }) {
                                        Text(stringResource(R.string.link_record))
                                    }
                                }
                                IconButton(onClick = {
                                    edit(EditableLifeRecord("NOTE", note.id, note.title, note.content))
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                                }
                                IconButton(onClick = {
                                    recordPendingDelete = EditableLifeRecord("NOTE", note.id, note.title, note.content)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
                            RecordSelector(
                                label = stringResource(R.string.link_target_goal),
                                noneLabel = stringResource(R.string.no_linked_record),
                                options = state.goals.map { it.id to it.title },
                                selectedId = inputGoalId,
                                onSelect = { inputGoalId = it }
                            )
                        }
                        if (state.selectedTab == 1) {
                            RecordSelector(
                                label = stringResource(R.string.priority_field),
                                noneLabel = stringResource(R.string.priority_medium),
                                options = listOf(
                                    TaskPriority.LOW.name to stringResource(R.string.priority_low),
                                    TaskPriority.MEDIUM.name to stringResource(R.string.priority_medium),
                                    TaskPriority.HIGH.name to stringResource(R.string.priority_high),
                                ),
                                selectedId = inputTaskPriority,
                                onSelect = { inputTaskPriority = it },
                                allowNone = false
                            )
                            RecordSelector(
                                label = stringResource(R.string.link_target_project),
                                noneLabel = stringResource(R.string.no_linked_record),
                                options = state.projects.map { it.id to it.title },
                                selectedId = inputProjectId,
                                onSelect = { inputProjectId = it }
                            )
                            RecordSelector(
                                label = stringResource(R.string.link_target_goal),
                                noneLabel = stringResource(R.string.no_linked_record),
                                options = state.goals.map { it.id to it.title },
                                selectedId = inputGoalId,
                                onSelect = { inputGoalId = it }
                            )
                            OutlinedTextField(
                                value = inputTaskDueDate,
                                onValueChange = { inputTaskDueDate = it },
                                label = { Text(stringResource(R.string.due_date_field)) },
                                supportingText = { Text(stringResource(R.string.due_date_hint)) },
                                isError = inputTaskDueDate.isNotBlank() && parseLocalDate(inputTaskDueDate) == null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (state.selectedTab) {
                                0 -> viewModel.addProject(
                                    inputTitle, inputDesc, inputNextAction,
                                    goalId = inputGoalId.takeIf { it.isNotBlank() }
                                )
                                1 -> viewModel.addTask(
                                    inputTitle,
                                    inputDesc,
                                    priority = runCatching { TaskPriority.valueOf(inputTaskPriority) }.getOrDefault(TaskPriority.MEDIUM),
                                    projectId = inputProjectId.takeIf { it.isNotBlank() },
                                    goalId = inputGoalId.takeIf { it.isNotBlank() },
                                    dueDate = parseLocalDate(inputTaskDueDate)
                                )
                                2 -> viewModel.addGoal(inputTitle, inputDesc)
                                3 -> viewModel.addNote(inputTitle, inputDesc)
                            }
                        },
                        enabled = inputTitle.isNotBlank() &&
                            (state.selectedTab != 1 || inputTaskDueDate.isBlank() || parseLocalDate(inputTaskDueDate) != null)
                    ) {
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

        recordBeingEdited?.let { record ->
            AlertDialog(
                onDismissRequest = { recordBeingEdited = null },
                title = { Text(text = stringResource(R.string.edit)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text(stringResource(R.string.title_field)) },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = editDescription,
                            onValueChange = { editDescription = it },
                            label = { Text(stringResource(R.string.description_field)) }
                        )
                        if (record.type == "PROJECT") {
                            OutlinedTextField(
                                value = editNextAction,
                                onValueChange = { editNextAction = it },
                                label = { Text(stringResource(R.string.next_action_field)) },
                                singleLine = true
                            )
                            RecordSelector(
                                label = stringResource(R.string.link_target_goal),
                                noneLabel = stringResource(R.string.no_linked_record),
                                options = state.goals.map { it.id to it.title },
                                selectedId = editGoalId,
                                onSelect = { editGoalId = it }
                            )
                        }
                        if (record.type == "TASK") {
                            RecordSelector(
                                label = stringResource(R.string.priority_field),
                                noneLabel = stringResource(R.string.priority_medium),
                                options = listOf(
                                    TaskPriority.LOW.name to stringResource(R.string.priority_low),
                                    TaskPriority.MEDIUM.name to stringResource(R.string.priority_medium),
                                    TaskPriority.HIGH.name to stringResource(R.string.priority_high),
                                ),
                                selectedId = editTaskPriority,
                                onSelect = { editTaskPriority = it },
                                allowNone = false
                            )
                            RecordSelector(
                                label = stringResource(R.string.link_target_project),
                                noneLabel = stringResource(R.string.no_linked_record),
                                options = state.projects.map { it.id to it.title },
                                selectedId = editProjectId,
                                onSelect = { editProjectId = it }
                            )
                            RecordSelector(
                                label = stringResource(R.string.link_target_goal),
                                noneLabel = stringResource(R.string.no_linked_record),
                                options = state.goals.map { it.id to it.title },
                                selectedId = editGoalId,
                                onSelect = { editGoalId = it }
                            )
                            OutlinedTextField(
                                value = editTaskDueDate,
                                onValueChange = { editTaskDueDate = it },
                                label = { Text(stringResource(R.string.due_date_field)) },
                                supportingText = { Text(stringResource(R.string.due_date_hint)) },
                                isError = editTaskDueDate.isNotBlank() && parseLocalDate(editTaskDueDate) == null,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            when (record.type) {
                                "PROJECT" -> viewModel.updateProject(
                                    record.id, editTitle, editDescription, editNextAction,
                                    editGoalId.takeIf { it.isNotBlank() }
                                )
                                "TASK" -> viewModel.updateTask(
                                    record.id,
                                    editTitle,
                                    editDescription,
                                    editProjectId.takeIf { it.isNotBlank() },
                                    editGoalId.takeIf { it.isNotBlank() },
                                    parseLocalDate(editTaskDueDate),
                                    runCatching { TaskPriority.valueOf(editTaskPriority) }.getOrDefault(TaskPriority.MEDIUM)
                                )
                                "GOAL" -> viewModel.updateGoal(record.id, editTitle, editDescription)
                                "NOTE" -> viewModel.updateNote(record.id, editTitle, editDescription)
                            }
                            recordBeingEdited = null
                        },
                        enabled = editTitle.isNotBlank() &&
                            (record.type != "TASK" || editTaskDueDate.isBlank() || parseLocalDate(editTaskDueDate) != null)
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordBeingEdited = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        recordPendingDelete?.let { record ->
            AlertDialog(
                onDismissRequest = { recordPendingDelete = null },
                title = { Text(stringResource(R.string.delete_confirmation_title)) },
                text = { Text(stringResource(R.string.delete_confirmation_message, record.title)) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteRecord(record.type, record.id)
                        recordPendingDelete = null
                    }) {
                        Text(stringResource(R.string.delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { recordPendingDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        milestoneProjectId?.let { projectId ->
            AlertDialog(
                onDismissRequest = { milestoneProjectId = null },
                title = { Text(stringResource(R.string.add_milestone)) },
                text = {
                    OutlinedTextField(
                        value = milestoneTitle,
                        onValueChange = { milestoneTitle = it },
                        label = { Text(stringResource(R.string.milestone_title)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addMilestone(projectId, milestoneTitle)
                            milestoneProjectId = null
                        },
                        enabled = milestoneTitle.isNotBlank()
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { milestoneProjectId = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        noteToLinkId?.let { noteId ->
            val targets = when (linkTargetType) {
                "GOAL" -> state.goals.map { it.id to it.title }
                "TASK" -> state.tasks.map { it.id to it.title }
                else -> state.projects.map { it.id to it.title }
            }
            val selectedTargetId = linkTargetId.takeIf { id -> targets.any { it.first == id } }
                ?: targets.firstOrNull()?.first.orEmpty()
            val targetTypeLabel = when (linkTargetType) {
                "GOAL" -> R.string.link_target_goal
                "TASK" -> R.string.link_target_task
                else -> R.string.link_target_project
            }
            AlertDialog(
                onDismissRequest = { noteToLinkId = null },
                title = { Text(stringResource(R.string.link_record)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.select_target_type))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showLinkTypeMenu = true }) {
                                Text(stringResource(targetTypeLabel))
                            }
                            DropdownMenu(
                                expanded = showLinkTypeMenu,
                                onDismissRequest = { showLinkTypeMenu = false }
                            ) {
                                listOf("PROJECT", "GOAL", "TASK").forEach { type ->
                                    val label = when (type) {
                                        "GOAL" -> R.string.link_target_goal
                                        "TASK" -> R.string.link_target_task
                                        else -> R.string.link_target_project
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(label)) },
                                        onClick = {
                                            linkTargetType = type
                                            linkTargetId = ""
                                            showLinkTypeMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.select_record))
                        TextButton(onClick = { showLinkTargetMenu = true }, enabled = targets.isNotEmpty()) {
                            Text(targets.firstOrNull { it.first == selectedTargetId }?.second ?: stringResource(R.string.select_record))
                        }
                        DropdownMenu(
                            expanded = showLinkTargetMenu,
                            onDismissRequest = { showLinkTargetMenu = false }
                        ) {
                            targets.forEach { (id, title) ->
                                DropdownMenuItem(
                                    text = { Text(title) },
                                    onClick = {
                                        linkTargetId = id
                                        showLinkTargetMenu = false
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addNoteLink(noteId, linkTargetType, selectedTargetId)
                            noteToLinkId = null
                        },
                        enabled = selectedTargetId.isNotBlank()
                    ) { Text(stringResource(R.string.link_record)) }
                },
                dismissButton = {
                    TextButton(onClick = { noteToLinkId = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        milestoneBeingEdited?.let { milestone ->
            AlertDialog(
                onDismissRequest = { milestoneBeingEdited = null },
                title = { Text(stringResource(R.string.edit_milestone)) },
                text = {
                    OutlinedTextField(
                        value = editedMilestoneTitle,
                        onValueChange = { editedMilestoneTitle = it },
                        label = { Text(stringResource(R.string.milestone_title)) },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.updateMilestone(milestone.id, editedMilestoneTitle)
                            milestoneBeingEdited = null
                        },
                        enabled = editedMilestoneTitle.isNotBlank()
                    ) { Text(stringResource(R.string.save)) }
                },
                dismissButton = {
                    TextButton(onClick = { milestoneBeingEdited = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        milestonePendingDelete?.let { milestone ->
            AlertDialog(
                onDismissRequest = { milestonePendingDelete = null },
                title = { Text(stringResource(R.string.delete_confirmation_title)) },
                text = { Text(stringResource(R.string.delete_confirmation_message, milestone.title)) },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deleteMilestone(milestone.id)
                        milestonePendingDelete = null
                    }) { Text(stringResource(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(onClick = { milestonePendingDelete = null }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}
