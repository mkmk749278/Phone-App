package com.dualshield.phone.shield

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dualshield.phone.core.shield.CallerActivity
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The on-disk shape. Kept separate from the domain type so storage can change freely. */
@Serializable
private data class StoredActivity(val key: String, val attempts: List<Long>)

@Serializable
private data class StoredActivities(val callers: List<StoredActivity> = emptyList())

private val Context.frequencyDataStore: DataStore<Preferences> by preferencesDataStore("call_frequency")

/**
 * How often each caller has tried, held in memory and saved in the background.
 *
 * Two constraints shaped this. The screening path reads it while Android is timing the
 * response, so reads and updates have to be in-memory and allocation-light. And the counts
 * have to survive a restart, or a process death would quietly reset every caller's history
 * and the signals would never build up on a phone that restarts often.
 *
 * Stored as a small serialized blob rather than a Room table on purpose: it is only ever
 * read whole at startup and written whole in the background, never queried, and a blob needs
 * no schema migration to change shape later.
 *
 * Everything here is local. No number, count or timestamp leaves the device.
 */
class CallFrequencyStore(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The live counts. Read on the screening path, so it must never block. */
    private val activities = ConcurrentHashMap<String, CallerActivity>()

    @Volatile
    private var loaded: Boolean = false

    /** Loads saved counts. Called once at startup; failures leave the store simply empty. */
    fun start() {
        scope.launch {
            runCatching {
                val prefs = context.frequencyDataStore.data
                    .catch { cause ->
                        if (cause is IOException) emit(emptyPreferences()) else throw cause
                    }
                    .first()
                val raw = prefs[KEY_ACTIVITIES] ?: return@runCatching
                val now = System.currentTimeMillis()
                json.decodeFromString(StoredActivities.serializer(), raw).callers
                    .map { CallerActivity(it.key, it.attempts) }
                    .filterNot { it.isStaleAt(now) }
                    .forEach { activities[it.matchKey] = it }
            }.onFailure {
                Log.w(TAG, "Call frequency history could not be read; starting empty.", it)
            }
            loaded = true
        }
    }

    /**
     * The activity recorded for [matchKey], never null.
     *
     * A plain hash lookup. Safe to call from the screening callback.
     */
    fun activityFor(matchKey: String): CallerActivity =
        activities[matchKey] ?: CallerActivity(matchKey)

    /**
     * Records an attempt from [matchKey] and returns the updated activity.
     *
     * The in-memory update happens synchronously so the decision being made right now sees
     * this attempt; the save to disk is scheduled and not waited on.
     */
    fun recordAttempt(matchKey: String, now: Long): CallerActivity {
        if (matchKey.isEmpty()) return CallerActivity(matchKey)
        val updated = activityFor(matchKey).recording(now)
        activities[matchKey] = updated
        persistLater()
        return updated
    }

    /** Forgets one caller's history, for when the user clears it. */
    fun forget(matchKey: String) {
        activities.remove(matchKey)
        persistLater()
    }

    /** Forgets everything. */
    fun clear() {
        activities.clear()
        persistLater()
    }

    private fun persistLater() {
        scope.launch {
            runCatching {
                val now = System.currentTimeMillis()
                // Pruning on the way out keeps the blob from growing without bound, and
                // means a caller who stopped a month ago stops being counted.
                val stale = activities.filterValues { it.isStaleAt(now) }.keys
                stale.forEach { activities.remove(it) }

                val payload = StoredActivities(
                    activities.values.map { StoredActivity(it.matchKey, it.attemptsMillis) },
                )
                context.frequencyDataStore.edit { prefs ->
                    prefs[KEY_ACTIVITIES] =
                        json.encodeToString(StoredActivities.serializer(), payload)
                }
            }.onFailure {
                Log.w(TAG, "Call frequency history could not be saved.", it)
            }
        }
    }

    private companion object {
        const val TAG = "CallFrequency"
        val KEY_ACTIVITIES = stringPreferencesKey("caller_activities")
    }
}
