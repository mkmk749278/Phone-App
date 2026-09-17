package com.dualshield.phone.data.system

import androidx.compose.runtime.Immutable
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallDirection { INCOMING, OUTGOING, MISSED, REJECTED, VOICEMAIL, OTHER }

/** One entry in the Phone → Recents list. Blocked calls never appear here. */
@Immutable
data class RecentCall(
    val id: Long,
    val number: String,
    val displayName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val direction: CallDirection,
    val simSlot: Int?,
    val phoneAccountId: String?,
)

/**
 * Reads the system call log for the Recents list.
 *
 * DualShieldPhone never writes to or deletes from the platform call log. Blocked calls live
 * only in the Shield Vault, which is why Recents stays conceptually clean without the app
 * having to tamper with a database it does not own.
 */
class CallLogRepository(private val context: Context) {

    private val cache = SystemDataCache<List<RecentCall>>()

    /** The last loaded recents, readable without suspending, for an instant first frame. */
    val cachedRecents: List<RecentCall> get() = cache.value.orEmpty()

    val isCacheFresh: Boolean get() = cache.isFresh

    /** Call after placing a call, so Recents reflects it rather than waiting out the TTL. */
    fun invalidateCache() = cache.invalidate()

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun recentCalls(
        limit: Int = 200,
        force: Boolean = false,
        slotForAccountId: (String?) -> Int?,
    ): List<RecentCall> = cache.getOrLoad(force) { queryRecents(limit, slotForAccountId) }

    private suspend fun queryRecents(
        limit: Int,
        slotForAccountId: (String?) -> Int?,
    ): List<RecentCall> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_ID,
        )

        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val accountId = cursor.getString(6)
                        add(
                            RecentCall(
                                id = cursor.getLong(0),
                                number = cursor.getString(1)?.trim().orEmpty(),
                                displayName = cursor.getString(2)?.takeIf { it.isNotBlank() },
                                timestamp = cursor.getLong(3),
                                durationSeconds = cursor.getLong(4),
                                direction = cursor.getInt(5).toDirection(),
                                simSlot = slotForAccountId(accountId),
                                phoneAccountId = accountId,
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    private fun Int.toDirection(): CallDirection = when (this) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
        CallLog.Calls.VOICEMAIL_TYPE -> CallDirection.VOICEMAIL
        else -> CallDirection.OTHER
    }
}
