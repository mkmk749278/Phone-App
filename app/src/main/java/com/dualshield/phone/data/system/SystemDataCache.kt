package com.dualshield.phone.data.system

import android.os.SystemClock

/**
 * A tiny stale-while-revalidate cache for data that lives in a content provider.
 *
 * Contacts, the call log and the SMS provider are all cross-process queries. Reading them is
 * fast enough once and far too slow to do on every tab switch, every recomposition, or every
 * time a ViewModel is recreated by a rotation.
 *
 * The contract callers rely on:
 *
 *  - [value] is readable synchronously and without blocking, so a screen can paint the last
 *    known data on its very first frame instead of showing an empty state and then popping.
 *  - [isFresh] says whether a refresh is worth doing at all.
 *
 * Deliberately process-lifetime only. Persisting a copy of the user's contacts and call log
 * to our own storage would mean holding a second copy of sensitive data that the system
 * already stores, for a saving of a few hundred milliseconds once per cold start. Not a
 * trade this app should make.
 */
class SystemDataCache<T>(private val ttlMs: Long = DEFAULT_TTL_MS) {

    @Volatile
    private var cached: T? = null

    @Volatile
    private var loadedAtElapsed: Long = 0L

    /** The last loaded value, or null if nothing has been loaded yet this process. */
    val value: T? get() = cached

    /** True when a load happened recently enough that another one would be wasted work. */
    val isFresh: Boolean
        get() = cached != null && SystemClock.elapsedRealtime() - loadedAtElapsed < ttlMs

    fun put(value: T) {
        cached = value
        loadedAtElapsed = SystemClock.elapsedRealtime()
    }

    /** Drops the value so the next read reloads. Used when the underlying data changed. */
    fun invalidate() {
        cached = null
        loadedAtElapsed = 0L
    }

    /**
     * Returns the cached value when fresh, otherwise runs [load] and caches the result.
     *
     * [force] skips the freshness check, for an explicit pull-to-refresh.
     */
    suspend fun getOrLoad(force: Boolean = false, load: suspend () -> T): T {
        if (!force) {
            cached?.let { if (isFresh) return it }
        }
        return load().also { put(it) }
    }

    companion object {
        /**
         * Long enough to cover a session of moving between tabs, short enough that a call
         * taken a minute ago shows up when the user goes looking for it.
         */
        const val DEFAULT_TTL_MS = 30_000L
    }
}
