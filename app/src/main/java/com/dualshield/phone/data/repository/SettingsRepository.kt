package com.dualshield.phone.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** App preferences that are not rules. Stored locally, never synced. */
data class AppSettings(
    /**
     * False until the first value has actually been read from disk. The UI waits for this
     * so a returning user never sees the onboarding flow flash past on launch.
     */
    val isLoaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val notifyOnBlockedCall: Boolean = false,
    val showSimLabelsEverywhere: Boolean = true,
    val vaultRetentionDays: Int = 0,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { cause ->
            // A corrupt preferences file must not stop the phone app from starting.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            AppSettings(
                isLoaded = true,
                onboardingComplete = prefs[KEY_ONBOARDING] ?: false,
                notifyOnBlockedCall = prefs[KEY_NOTIFY_BLOCKED] ?: false,
                showSimLabelsEverywhere = prefs[KEY_SIM_LABELS] ?: true,
                vaultRetentionDays = prefs[KEY_VAULT_RETENTION] ?: 0,
            )
        }

    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[KEY_ONBOARDING] = complete }

    suspend fun setNotifyOnBlockedCall(enabled: Boolean) = edit { it[KEY_NOTIFY_BLOCKED] = enabled }

    suspend fun setShowSimLabelsEverywhere(enabled: Boolean) = edit { it[KEY_SIM_LABELS] = enabled }

    suspend fun setVaultRetentionDays(days: Int) = edit { it[KEY_VAULT_RETENTION] = days }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private companion object {
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_NOTIFY_BLOCKED = booleanPreferencesKey("notify_blocked_call")
        val KEY_SIM_LABELS = booleanPreferencesKey("show_sim_labels")
        val KEY_VAULT_RETENTION = intPreferencesKey("vault_retention_days")
    }
}
