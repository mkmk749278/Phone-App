package com.dualshield.phone.data.system

import androidx.compose.runtime.Immutable
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A device contact, as shown in the Contacts tab. */
@Immutable
data class Contact(
    val id: Long,
    val lookupKey: String?,
    val displayName: String,
    val phoneNumbers: List<String>,
    val photoUri: String?,
    val starred: Boolean,
) {
    val primaryNumber: String? get() = phoneNumbers.firstOrNull()
}

/**
 * Reads the device's own contacts. There is no remote lookup anywhere in this class, and
 * there never will be — that is the whole point of the app.
 */
class ContactsRepository(private val context: Context) {

    private val contactsCache = SystemDataCache<List<Contact>>()
    private val nameIndexCache = SystemDataCache<Map<String, String>>()

    /**
     * The last loaded contacts, available without suspending.
     *
     * Screens seed their first frame from this so a tab switch paints immediately instead of
     * flashing an empty state while the provider query runs.
     */
    val cachedContacts: List<Contact> get() = contactsCache.value.orEmpty()

    val isContactCacheFresh: Boolean get() = contactsCache.isFresh

    /** Call after anything that could change the address book. */
    fun invalidateCache() {
        contactsCache.invalidate()
        nameIndexCache.invalidate()
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadContacts(force: Boolean = false): List<Contact> =
        contactsCache.getOrLoad(force) { queryContacts() }

    /** The number -> name map, built once per contacts load rather than per caller. */
    suspend fun cachedNameIndex(): Map<String, String> =
        nameIndexCache.getOrLoad { nameIndex(loadContacts()) }

    private suspend fun queryContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
        )

        val byId = LinkedHashMap<Long, Contact>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(projection[0])
                val lookupIdx = cursor.getColumnIndexOrThrow(projection[1])
                val nameIdx = cursor.getColumnIndexOrThrow(projection[2])
                val numberIdx = cursor.getColumnIndexOrThrow(projection[3])
                val photoIdx = cursor.getColumnIndexOrThrow(projection[4])
                val starredIdx = cursor.getColumnIndexOrThrow(projection[5])

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val number = cursor.getString(numberIdx)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val existing = byId[id]
                    if (existing == null) {
                        byId[id] = Contact(
                            id = id,
                            lookupKey = cursor.getString(lookupIdx),
                            displayName = cursor.getString(nameIdx)?.trim().orEmpty()
                                .ifEmpty { number },
                            phoneNumbers = listOf(number),
                            photoUri = cursor.getString(photoIdx),
                            starred = cursor.getInt(starredIdx) == 1,
                        )
                    } else if (number !in existing.phoneNumbers) {
                        byId[id] = existing.copy(phoneNumbers = existing.phoneNumbers + number)
                    }
                }
            }
        }
        byId.values.toList()
    }

    /**
     * Resolves a caller ID to a contact name.
     *
     * Used on the screening path, so it is a single indexed lookup and swallows every
     * failure: a missing name must never delay or change a blocking decision.
     */
    fun displayNameFor(number: String?): String? {
        if (number.isNullOrBlank() || !hasPermission()) return null
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number),
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    /**
     * Builds a number -> name index from an already-loaded contact list.
     *
     * The Messages list previously called [displayNameFor] once per conversation, which is
     * one content-provider round trip per row. One pass over the contacts already in memory
     * replaces all of them.
     *
     * Keyed on the last 10 digits so "+91 98765 43210" and "09876543210" both hit.
     */
    fun nameIndex(contacts: List<Contact>): Map<String, String> {
        val index = HashMap<String, String>(contacts.size * 2)
        for (contact in contacts) {
            for (number in contact.phoneNumbers) {
                val key = matchKey(number)
                if (key.isNotEmpty()) index.putIfAbsent(key, contact.displayName)
            }
        }
        return index
    }

    /** The comparison key used by [nameIndex]; also handy for matching a single number. */
    fun matchKey(number: String?): String =
        number?.filter { it.isDigit() }?.takeLast(10).orEmpty()

    /** T9-ish local search over name and number. Substring, not fuzzy — predictable wins. */
    fun search(contacts: List<Contact>, query: String): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return contacts
        val digits = trimmed.filter { it.isDigit() }
        val lowered = trimmed.lowercase()
        return contacts.filter { contact ->
            contact.displayName.lowercase().contains(lowered) ||
                (
                    digits.isNotEmpty() &&
                        contact.phoneNumbers.any {
                            PhoneNumberNormalizer.normalizedOrEmpty(it).contains(digits)
                        }
                    ) ||
                (digits.isNotEmpty() && t9Matches(contact.displayName, digits))
        }
    }

    /** Maps each letter to its keypad digit so "726" finds "Ravi". */
    private fun t9Matches(name: String, digits: String): Boolean {
        val encoded = buildString {
            for (ch in name.lowercase()) {
                when (ch) {
                    in 'a'..'c' -> append('2')
                    in 'd'..'f' -> append('3')
                    in 'g'..'i' -> append('4')
                    in 'j'..'l' -> append('5')
                    in 'm'..'o' -> append('6')
                    in 'p'..'s' -> append('7')
                    in 't'..'v' -> append('8')
                    in 'w'..'z' -> append('9')
                    else -> append(' ')
                }
            }
        }
        return encoded.split(' ').any { it.isNotEmpty() && it.startsWith(digits) } ||
            encoded.replace(" ", "").contains(digits)
    }
}
