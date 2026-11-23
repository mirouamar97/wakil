package app.wakil.agent.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Non-secret preferences: language, provider configuration, Kill Switch, and project limits. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("wakil_settings", Context.MODE_PRIVATE)

    fun model(providerId: String): String? = prefs.getString("model.$providerId", null)

    fun setModel(providerId: String, model: String) {
        prefs.edit().putString("model.$providerId", model.trim()).apply()
    }

    fun cloudflareAccountId(): String? =
        ProviderCatalog.normalizeCloudflareAccountId(prefs.getString("cloudflare_account_id", null))

    /** Blank clears the optional account ID; invalid IDs are rejected and never placed in a URL. */
    fun setCloudflareAccountId(id: String): Boolean {
        val clean = id.trim()
        if (clean.isEmpty()) {
            prefs.edit().remove("cloudflare_account_id").apply()
            return true
        }
        val normalized = ProviderCatalog.normalizeCloudflareAccountId(clean) ?: return false
        prefs.edit().putString("cloudflare_account_id", normalized).apply()
        return true
    }

    fun providerPriority(): List<String> {
        val csv = prefs.getString("provider_priority", null)?.trim().orEmpty()
        if (csv.isEmpty()) return ProviderCatalog.defaultPriority
        return ProviderCatalog.normalizePriority(csv.split(",").map { it.trim() })
    }

    fun setProviderPriority(order: List<String>) {
        val csv = ProviderCatalog.normalizePriority(order).joinToString(",")
        prefs.edit().putString("provider_priority", csv).apply()
    }

    fun language(): String = prefs.getString("lang", null) ?: DEFAULT_LANGUAGE

    fun setLanguage(tag: String) {
        prefs.edit().putString("lang", tag).apply()
        applyLanguage(tag)
    }

    fun applyStoredLanguage() = applyLanguage(language())

    private fun applyLanguage(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    fun isKillSwitchActive(): Boolean = prefs.getBoolean("kill_switch", false)

    fun setKillSwitch(active: Boolean) {
        prefs.edit().putBoolean("kill_switch", active).apply()
    }

    fun maxActiveProjects(): Int = prefs.getInt("max_active_projects", 3)

    fun setMaxActiveProjects(max: Int) {
        prefs.edit().putInt("max_active_projects", max.coerceAtLeast(1)).apply()
    }

    fun includeLifeContext(): Boolean = prefs.getBoolean("include_life_context", true)

    fun setIncludeLifeContext(include: Boolean) {
        prefs.edit().putBoolean("include_life_context", include).apply()
    }

    companion object {
        const val DEFAULT_LANGUAGE = "ar"
        val LANGUAGES = listOf("ar", "fr", "en")
    }
}
