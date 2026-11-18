package app.wakil.agent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** API keys live only here: EncryptedSharedPreferences (AES-256), never in plain prefs, files, logs or the repo. */
class SecureKeyStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "wakil_keys",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun keys(providerId: String): List<String> =
        (prefs.getString(name(providerId), "") ?: "").split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun add(providerId: String, key: String) {
        val clean = key.trim()
        if (clean.isEmpty() || '\n' in clean) return
        val all = keys(providerId)
        if (clean in all) return
        prefs.edit().putString(name(providerId), (all + clean).joinToString("\n")).apply()
    }

    fun clear(providerId: String) {
        prefs.edit().remove(name(providerId)).apply()
    }

    private fun name(providerId: String) = "keys.$providerId"
}
