package com.dualshield.phone.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.shield.RecoveryMode
import com.dualshield.phone.core.shield.RecoverySettings
import com.dualshield.phone.core.shield.ShieldPause
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
    /**
     * The pause in force, or null.
     *
     * Stored rather than held only in memory so it survives process death and reboot: a
     * pause the user set for an hour must still be there if the phone restarts in between,
     * and — just as importantly — must still *lapse* on time if it does.
     */
    val shieldPause: ShieldPause? = null,
    /** Behavioural protection, per SIM slot. Absent means the default: off. */
    val recoveryBySlot: Map<Int, RecoverySettings> = emptyMap(),
) {
    fun recoveryFor(slotIndex: Int): RecoverySettings =
        recoveryBySlot[slotIndex] ?: RecoverySettings.DEFAULT
}

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
                shieldPause = readPause(prefs),
                recoveryBySlot = readRecovery(prefs),
            )
        }

    suspend fun setOnboardingComplete(complete: Boolean) = edit { it[KEY_ONBOARDING] = complete }

    suspend fun setNotifyOnBlockedCall(enabled: Boolean) = edit { it[KEY_NOTIFY_BLOCKED] = enabled }

    suspend fun setShowSimLabelsEverywhere(enabled: Boolean) = edit { it[KEY_SIM_LABELS] = enabled }

    suspend fun setVaultRetentionDays(days: Int) = edit { it[KEY_VAULT_RETENTION] = days }

    /** Starts a pause. Replaces any pause already in force. */
    suspend fun setShieldPause(pause: ShieldPause) = edit { prefs ->
        prefs[KEY_PAUSE_SCOPE] = pause.scope.name
        val expiry = pause.expiresAtMillis
        if (expiry == null) prefs.remove(KEY_PAUSE_EXPIRY) else prefs[KEY_PAUSE_EXPIRY] = expiry
    }

    suspend fun setRecoverySettings(slotIndex: Int, settings: RecoverySettings) = edit { prefs ->
        prefs[recoveryModeKey(slotIndex)] = settings.mode.name
        prefs[recoveryFlagsKey(slotIndex)] = encodeFlags(settings)
    }

    private fun readRecovery(prefs: Preferences): Map<Int, RecoverySettings> =
        SUPPORTED_SLOTS.mapNotNull { slot ->
            val mode = prefs[recoveryModeKey(slot)]
                ?.let { name -> runCatching { RecoveryMode.valueOf(name) }.getOrNull() }
                ?: return@mapNotNull null
            val flags = prefs[recoveryFlagsKey(slot)] ?: DEFAULT_FLAGS
            slot to RecoverySettings(
                mode = mode,
                repeatedCallers = flags.getOrElse(0) { '1' } == '1',
                highFrequencyCallers = flags.getOrElse(1) { '1' } == '1',
                unknownCallers = flags.getOrElse(2) { '0' } == '1',
                rapidRepeat = flags.getOrElse(3) { '1' } == '1',
            )
        }.toMap()

    /**
     * The detection toggles as four characters.
     *
     * A fixed-width string rather than four preference keys per slot: it keeps one slot's
     * settings atomic, so a half-written change cannot leave detection in a state the user
     * never chose.
     */
    private fun encodeFlags(settings: RecoverySettings): String = buildString {
        append(if (settings.repeatedCallers) '1' else '0')
        append(if (settings.highFrequencyCallers) '1' else '0')
        append(if (settings.unknownCallers) '1' else '0')
        append(if (settings.rapidRepeat) '1' else '0')
    }

    /** Lifts the pause, whether it was timed or indefinite. */
    suspend fun clearShieldPause() = edit { prefs ->
        prefs.remove(KEY_PAUSE_SCOPE)
        prefs.remove(KEY_PAUSE_EXPIRY)
    }

    /**
     * Reads the stored pause.
     *
     * An expired pause is reported as no pause at all, so a stale record left behind by a
     * reboot can never read as protection still being off.
     */
    private fun readPause(prefs: Preferences): ShieldPause? {
        val scope = prefs[KEY_PAUSE_SCOPE]?.let { name ->
            runCatching { SimScope.valueOf(name) }.getOrNull()
        } ?: return null
        val pause = ShieldPause(scope, prefs[KEY_PAUSE_EXPIRY])
        return pause.takeIf { it.isActiveAt(System.currentTimeMillis()) }
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private companion object {
        val KEY_ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val KEY_NOTIFY_BLOCKED = booleanPreferencesKey("notify_blocked_call")
        val KEY_SIM_LABELS = booleanPreferencesKey("show_sim_labels")
        val KEY_VAULT_RETENTION = intPreferencesKey("vault_retention_days")
        val KEY_PAUSE_SCOPE = stringPreferencesKey("shield_pause_scope")
        val KEY_PAUSE_EXPIRY = longPreferencesKey("shield_pause_expires_at")

        val SUPPORTED_SLOTS = listOf(0, 1)
        const val DEFAULT_FLAGS = "1101"

        fun recoveryModeKey(slotIndex: Int) = stringPreferencesKey("recovery_mode_$slotIndex")
        fun recoveryFlagsKey(slotIndex: Int) = stringPreferencesKey("recovery_flags_$slotIndex")
    }
}
