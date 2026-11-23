package app.wakil.agent.ui

import androidx.annotation.StringRes
import app.wakil.agent.R
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
}
