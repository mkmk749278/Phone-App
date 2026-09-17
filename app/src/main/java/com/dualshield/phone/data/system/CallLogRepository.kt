package com.dualshield.phone.data.system

import androidx.compose.runtime.Immutable
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import com.dualshield.phone.core.number.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CallDirection { INCOMING, OUTGOING, MISSED, REJECTED, VOICEMAIL, OTHER }

/** One entry in the Phone → Recents list. Blocked calls never appear here. */
@Immutable
data class RecentCall(
    val id: Long,
    val number: String,
    /** Canonical identity, for grouping and for looking the caller up on other screens. */
    val canonicalNumber: String,
    /** The grouped spelling. Formatted once here rather than per row per frame. */
    val displayNumber: String,
    val displayName: String?,
    val contactId: Long?,
    val photoUri: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val direction: CallDirection,
    val simSlot: Int?,
    val subscriptionId: Int?,
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
        contacts: ContactIndex = ContactIndex.EMPTY,
        slotForAccountId: (String?) -> Int?,
        subscriptionIdForSlot: (Int) -> Int? = { null },
    ): List<RecentCall> = cache.getOrLoad(force) {
        queryRecents(limit, contacts, slotForAccountId, subscriptionIdForSlot)
    }

    private suspend fun queryRecents(
        limit: Int,
        contacts: ContactIndex,
        slotForAccountId: (String?) -> Int?,
        subscriptionIdForSlot: (Int) -> Int?,
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
                blockedExclusionSelection(),
                null,
                "${CallLog.Calls.DATE} DESC LIMIT $limit",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val type = cursor.getInt(5)
                        // Belt and braces: the selection above already excludes blocked rows
                        // on every platform that exposes the type, but OEM call-log providers
                        // have been known to ignore a selection they do not recognise.
                        if (type == CallLog.Calls.BLOCKED_TYPE) continue

                        val accountId = cursor.getString(6)
                        val slot = slotForAccountId(accountId)
                        val raw = cursor.getString(1)?.trim().orEmpty()
                        val contact = contacts.lookup(raw)
                        val cachedName = cursor.getString(2)?.takeIf { it.isNotBlank() }
                        add(
                            RecentCall(
                                id = cursor.getLong(0),
                                number = raw,
                                canonicalNumber = PhoneNumberFormatter.canonical(raw),
                                displayNumber = PhoneNumberFormatter.display(raw),
                                // The live address book wins over the call log's cached copy,
                                // which goes stale as soon as a contact is renamed.
                                displayName = contact?.displayName ?: cachedName,
                                contactId = contact?.id,
                                photoUri = contact?.photoUri,
                                timestamp = cursor.getLong(3),
                                durationSeconds = cursor.getLong(4),
                                direction = type.toDirection(),
                                simSlot = slot,
                                // Resolved through the SIM layer, never parsed out of the
                                // account id: the two are different numbers on most devices.
                                subscriptionId = slot?.let(subscriptionIdForSlot),
                                phoneAccountId = accountId,
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /**
     * Keeps platform-blocked calls out of ordinary Recents.
     *
     * `BLOCKED_TYPE` exists from API 24, but whether a blocked call is written to the call
     * log at all is up to the OEM — which is exactly why this is only half the story. The
     * app's own blocked history is the source of truth for what Shield rejected; this
     * selection just stops the *platform's* idea of a blocked call from leaking into the
     * normal list. See the row-level guard above for the other half.
     */
    private fun blockedExclusionSelection(): String =
        "${CallLog.Calls.TYPE} != ${CallLog.Calls.BLOCKED_TYPE}"

    private fun Int.toDirection(): CallDirection = when (this) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallDirection.MISSED
        CallLog.Calls.REJECTED_TYPE -> CallDirection.REJECTED
        CallLog.Calls.VOICEMAIL_TYPE -> CallDirection.VOICEMAIL
        else -> CallDirection.OTHER
    }
}
