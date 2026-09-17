package com.dualshield.phone.data.system

import androidx.compose.runtime.Immutable
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import com.dualshield.phone.core.model.ContactLookup
import com.dualshield.phone.core.model.SenderIdentity
import com.dualshield.phone.core.model.SenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A row in the Messages list. */
@Immutable
data class SmsThread(
    val threadId: Long,
    val address: String,
    val displayName: String?,
    val snippet: String,
    val timestamp: Long,
    val unreadCount: Int,
    val simSlot: Int?,
    /**
     * Who the thread is with, resolved once when the list is built.
     *
     * Carrying the identity on the row is what stopped every bank and operator showing up as
     * "Private number": the Messages list no longer has to guess from the address alone.
     */
    val sender: SenderIdentity,
) {
    val senderType: SenderType get() = sender.type
}

/** One message inside a conversation. */
@Immutable
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val timestamp: Long,
    val outgoing: Boolean,
    val read: Boolean,
    val subscriptionId: Int,
)

/**
 * Reads and sends SMS through the platform provider.
 *
 * Sending is always tied to an explicit subscription id so a reply never silently leaves
 * from the wrong SIM — the same dual-SIM guarantee the call side makes.
 */
class SmsRepository(private val context: Context) {

    private val threadCache = SystemDataCache<List<SmsThread>>()

    /** The last loaded conversation list, readable without suspending. */
    val cachedThreads: List<SmsThread> get() = threadCache.value.orEmpty()

    val isThreadCacheFresh: Boolean get() = threadCache.isFresh

    /** Call after sending or receiving, so the list does not wait out the TTL. */
    fun invalidateThreadCache() = threadCache.invalidate()

    fun hasReadPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun hasSendPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun threads(
        force: Boolean = false,
        contacts: ContactLookup = ContactLookup.None,
        slotForSubscriptionId: (Int) -> Int?,
    ): List<SmsThread> =
        threadCache.getOrLoad(force) { queryThreads(contacts, slotForSubscriptionId) }

    private suspend fun queryThreads(
        contacts: ContactLookup,
        slotForSubscriptionId: (Int) -> Int?,
    ): List<SmsThread> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID,
        )

        val byThread = LinkedHashMap<Long, SmsThread>()
        val unread = HashMap<Long, Int>()

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC LIMIT 1000",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(1)
                    val address = cursor.getString(2)?.trim().orEmpty()
                    if (address.isEmpty()) continue
                    val isUnread = cursor.getInt(5) == 0
                    if (isUnread) unread[threadId] = (unread[threadId] ?: 0) + 1
                    if (byThread.containsKey(threadId)) continue
                    val subId = cursor.getInt(6)
                    val sender = SenderIdentity.resolve(address, contacts)
                    byThread[threadId] = SmsThread(
                        threadId = threadId,
                        address = address,
                        displayName = sender.displayName,
                        snippet = cursor.getString(3)?.trim().orEmpty(),
                        timestamp = cursor.getLong(4),
                        unreadCount = 0,
                        simSlot = slotForSubscriptionId(subId),
                        sender = sender,
                    )
                }
            }
        }

        byThread.values
            .map { it.copy(unreadCount = unread[it.threadId] ?: 0) }
            .sortedByDescending { it.timestamp }
    }

    suspend fun messages(threadId: Long): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
            Telephony.Sms.SUBSCRIPTION_ID,
        )

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC LIMIT 500",
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val type = cursor.getInt(5)
                        add(
                            SmsMessage(
                                id = cursor.getLong(0),
                                threadId = cursor.getLong(1),
                                address = cursor.getString(2)?.trim().orEmpty(),
                                body = cursor.getString(3).orEmpty(),
                                timestamp = cursor.getLong(4),
                                outgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT ||
                                    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX ||
                                    type == Telephony.Sms.MESSAGE_TYPE_QUEUED,
                                read = cursor.getInt(6) == 1,
                                subscriptionId = cursor.getInt(7),
                            ),
                        )
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /**
     * Sends [body] to [address] from a specific subscription.
     *
     * @return null on success, or a user-facing message describing why it did not send.
     */
    @SuppressLint("MissingPermission")
    suspend fun send(
        address: String,
        body: String,
        subscriptionId: Int,
    ): String? = withContext(Dispatchers.IO) {
        if (!hasSendPermission()) return@withContext "DualShieldPhone needs SMS permission to send this message."
        if (address.isBlank()) return@withContext "Enter a number to send to."
        if (body.isBlank()) return@withContext null

        val manager = smsManagerFor(subscriptionId)
            ?: return@withContext "This SIM can't send messages right now."

        try {
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(address, null, parts, null, null)
            } else {
                manager.sendTextMessage(address, null, body, null, null)
            }
        } catch (t: Throwable) {
            return@withContext "The message couldn't be sent. Check signal and try again."
        }

        // Only the default SMS app may write to the provider; when we are not, the platform
        // app records the message itself, so a failure here is expected and harmless.
        runCatching {
            context.contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                },
            )
        }
        null
    }

    /**
     * Stores an inbound message when we hold the default-SMS role.
     *
     * [markRead] is set for a message Shield filtered: the message is still saved and still
     * readable, it just does not announce itself.
     */
    fun persistIncoming(
        address: String,
        body: String,
        timestamp: Long,
        subscriptionId: Int,
        markRead: Boolean = false,
    ): Boolean = runCatching {
        context.contentResolver.insert(
            Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.DATE_SENT, timestamp)
                put(Telephony.Sms.READ, if (markRead) 1 else 0)
                put(Telephony.Sms.SEEN, if (markRead) 1 else 0)
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            },
        ) != null
    }.getOrDefault(false)

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                },
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        }
        Unit
    }

    private fun smsManagerFor(subscriptionId: Int): SmsManager? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subscriptionId >= 0) base?.createForSubscriptionId(subscriptionId) else base
        } else {
            @Suppress("DEPRECATION")
            if (subscriptionId >= 0) {
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                SmsManager.getDefault()
            }
        }
    }.getOrNull()
}
