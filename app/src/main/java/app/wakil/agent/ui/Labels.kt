package app.wakil.agent.ui

import androidx.annotation.StringRes
import app.wakil.agent.R
import app.wakil.agent.domain.rules.ProjectStatus
import app.wakil.agent.router.Outcome

@StringRes
fun providerName(id: String): Int = when (id) {
    "groq" -> R.string.provider_groq
    "gemini" -> R.string.provider_gemini
    "cloudflare" -> R.string.provider_cloudflare
    "openrouter" -> R.string.provider_openrouter
    "mistral" -> R.string.provider_mistral
    else -> R.string.provider_unknown
}

@StringRes
fun outcomeLabel(o: Outcome): Int = when (o) {
    Outcome.OK -> R.string.outcome_ok
    Outcome.RATE_LIMITED -> R.string.outcome_rate_limited
    Outcome.AUTH_FAILED -> R.string.outcome_auth_failed
    Outcome.SERVER_ERROR -> R.string.outcome_server_error
    Outcome.NETWORK_ERROR -> R.string.outcome_network_error
    Outcome.BAD_RESPONSE -> R.string.outcome_bad_response
    Outcome.COOLING_DOWN -> R.string.outcome_cooling_down
    Outcome.NO_KEY -> R.string.outcome_no_key
    Outcome.KILL_SWITCH_ACTIVE -> R.string.outcome_kill_switch
}

@StringRes
fun actionTypeName(type: String): Int = when (type.uppercase()) {
    "CREATE" -> R.string.action_create
    "UPDATE" -> R.string.action_update
    "DELETE" -> R.string.action_delete
    "AI_SUGGESTION" -> R.string.action_suggestion
    "UNDO" -> R.string.action_undo
    else -> R.string.action_unknown
}

@StringRes
fun actionRecordTypeName(type: String): Int = when (type.uppercase()) {
    "TASK" -> R.string.record_task
    "PROJECT" -> R.string.record_project
    "GOAL" -> R.string.record_goal
    "NOTE" -> R.string.record_note
    "NOTE_LINK" -> R.string.record_note_link
    "MILESTONE" -> R.string.record_milestone
    "MEMORY" -> R.string.record_memory
    "MESSAGE" -> R.string.record_message
    else -> R.string.record_unknown
}

@StringRes
fun projectStatusName(status: ProjectStatus): Int = when (status) {
    ProjectStatus.IDEA -> R.string.project_status_idea
    ProjectStatus.PLANNED -> R.string.project_status_planned
    ProjectStatus.ACTIVE -> R.string.project_status_active
    ProjectStatus.PAUSED -> R.string.project_status_paused
    ProjectStatus.COMPLETED -> R.string.project_status_completed
    ProjectStatus.ARCHIVED -> R.string.project_status_archived
}
