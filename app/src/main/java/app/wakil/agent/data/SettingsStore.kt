package app.wakil.agent.data

import android.content.Context
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate

/** Non-secret preferences: language and per-provider model names. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("wakil_settings", Context.MODE_PRIVATE)

    fun model(providerId: String): String? = prefs.getString("model.$providerId", null)

    fun setModel(providerId: String, model: String) {
        prefs.edit().putString("model.$providerId", model.trim()).apply()
    }

    /** "ar", "fr" or "en". Arabic is the default on first launch. */
    fun language(): String = prefs.getString("lang", null) ?: DEFAULT_LANGUAGE

    fun setLanguage(tag: String) {
        prefs.edit().putString("lang", tag).apply()
        applyLanguage(tag)
    }

    fun applyStoredLanguage() = applyLanguage(language())

    private fun applyLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    companion object {
        const val DEFAULT_LANGUAGE = "ar"
        val LANGUAGES = listOf("ar", "fr", "en")
    }
}
