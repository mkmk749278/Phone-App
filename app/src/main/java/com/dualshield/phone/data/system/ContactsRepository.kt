package com.dualshield.phone.data.system

import androidx.compose.runtime.Immutable
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import com.dualshield.phone.core.model.ContactLookup
import com.dualshield.phone.core.number.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One phone number belonging to a contact.
 *
 * A contact row in ContactsContract is a *number*, not a person, and the same line is often
 * stored several times in different spellings. Keeping the raw spelling alongside the
 * canonical identity is what lets the app dedupe without ever rewriting what the user typed
 * into their address book.
 */
@Immutable
data class ContactNumber(
    val raw: String,
    val display: String,
    val matchKey: String,
    val typeLabel: String,
)

/** A device contact, as shown in the Contacts tab. */
@Immutable
data class Contact(
    val id: Long,
    val lookupKey: String?,
    val displayName: String,
    val phoneNumbers: List<ContactNumber>,
    val photoUri: String?,
    val starred: Boolean,
) {
    val primaryNumber: String? get() = phoneNumbers.firstOrNull()?.raw
}

/** The identity bits every surface needs when it has a number and wants a person. */
@Immutable
data class ContactRef(
    val id: Long,
    val lookupKey: String?,
    val displayName: String,
    val photoUri: String?,
)

/**
 * Number -> contact lookup, built once per contacts load.
 *
 * Recents, Messages, the dialer, Call Details and the in-call screen all resolve through
 * this one index, which is what makes a contact's name and photo identical everywhere
 * instead of each screen inventing its own answer.
 */
@Immutable
class ContactIndex(private val byKey: Map<String, ContactRef>) : ContactLookup {

    val size: Int get() = byKey.size

    fun lookup(rawNumber: String?): ContactRef? {
        val key = PhoneNumberFormatter.matchKey(rawNumber)
        if (key.isEmpty()) return null
        return byKey[key]
    }

    fun nameForNumber(rawNumber: String?): String? = lookup(rawNumber)?.displayName

    /**
     * Direct lookup for a caller already reduced to its [PhoneNumberFormatter.matchKey].
     * Avoids normalizing a key a second time.
     */
    override fun nameFor(key: String): String? = byKey[key]?.displayName

    override fun photoFor(key: String): String? = byKey[key]?.photoUri

    /** The whole contact behind an already-computed key. */
    fun refForKey(key: String): ContactRef? = byKey[key]

    fun photoUriFor(rawNumber: String?): String? = lookup(rawNumber)?.photoUri

    companion object {
        val EMPTY = ContactIndex(emptyMap())
    }
}

/**
 * Reads the device's own contacts. There is no remote lookup anywhere in this class, and
 * there never will be — that is the whole point of the app.
 */
class ContactsRepository(private val context: Context) {

    private val contactsCache = SystemDataCache<List<Contact>>()
    private val contactIndexCache = SystemDataCache<ContactIndex>()

    /**
     * The last loaded contacts, available without suspending.
     *
     * Screens seed their first frame from this so a tab switch paints immediately instead of
     * flashing an empty state while the provider query runs.
     */
    val cachedContacts: List<Contact> get() = contactsCache.value.orEmpty()

    val isContactCacheFresh: Boolean get() = contactsCache.isFresh

    /** The last built index, readable without suspending. Empty until contacts load once. */
    val cachedContactIndex: ContactIndex get() = contactIndexCache.value ?: ContactIndex.EMPTY

    /** Call after anything that could change the address book. */
    fun invalidateCache() {
        contactsCache.invalidate()
        contactIndexCache.invalidate()
    }

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadContacts(force: Boolean = false): List<Contact> =
        contactsCache.getOrLoad(force) { queryContacts() }

    /** The number -> contact index, built once per contacts load rather than per caller. */
    suspend fun contactIndex(force: Boolean = false): ContactIndex =
        contactIndexCache.getOrLoad(force) { buildIndex(loadContacts(force)) }

    private suspend fun queryContacts(): List<Contact> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
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
                val typeIdx = cursor.getColumnIndexOrThrow(projection[6])
                val labelIdx = cursor.getColumnIndexOrThrow(projection[7])

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val raw = cursor.getString(numberIdx)?.trim().orEmpty()
                    if (raw.isEmpty()) continue

                    val number = ContactNumber(
                        raw = raw,
                        display = PhoneNumberFormatter.display(raw),
                        matchKey = PhoneNumberFormatter.matchKey(raw),
                        typeLabel = numberTypeLabel(cursor.getInt(typeIdx), cursor.getString(labelIdx)),
                    )

                    val existing = byId[id]
                    if (existing == null) {
                        byId[id] = Contact(
                            id = id,
                            lookupKey = cursor.getString(lookupIdx),
                            displayName = cursor.getString(nameIdx)?.trim().orEmpty()
                                .ifEmpty { number.display },
                            phoneNumbers = listOf(number),
                            photoUri = cursor.getString(photoIdx),
                            starred = cursor.getInt(starredIdx) == 1,
                        )
                    } else if (existing.phoneNumbers.none { it.sameNumberAs(number) }) {
                        // Same person, genuinely different line. A second *spelling* of a line
                        // already held is dropped here rather than in the UI, so no screen has
                        // to remember to dedupe.
                        byId[id] = existing.copy(phoneNumbers = existing.phoneNumbers + number)
                    }
                }
            }
        }
        byId.values.toList()
    }

    /**
     * Two numbers are the same line when their canonical keys agree.
     *
     * Falls back to the raw spelling for anything with no canonical identity at all, so two
     * unparseable entries are still not merged into one.
     */
    private fun ContactNumber.sameNumberAs(other: ContactNumber): Boolean =
        if (matchKey.isNotEmpty() && other.matchKey.isNotEmpty()) {
            matchKey == other.matchKey
        } else {
            raw == other.raw
        }

    private fun numberTypeLabel(type: Int, label: String?): String {
        if (!label.isNullOrBlank()) return label
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Main"
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Home fax"
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Work fax"
            else -> "Phone"
        }
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
     * Builds the number -> contact index from an already-loaded contact list.
     *
     * The Messages list previously called [displayNameFor] once per conversation, which is
     * one content-provider round trip per row. One pass over the contacts already in memory
     * replaces all of them, and carries the photo along so no screen has to look it up
     * separately.
     *
     * Keyed on [PhoneNumberFormatter.matchKey] so "+91 98765 43210" and "09876543210" both
     * hit the same person.
     */
    fun buildIndex(contacts: List<Contact>): ContactIndex {
        val index = HashMap<String, ContactRef>(contacts.size * 2)
        for (contact in contacts) {
            val ref = ContactRef(
                id = contact.id,
                lookupKey = contact.lookupKey,
                displayName = contact.displayName,
                photoUri = contact.photoUri,
            )
            for (number in contact.phoneNumbers) {
                if (number.matchKey.isNotEmpty()) index.putIfAbsent(number.matchKey, ref)
            }
        }
        return ContactIndex(index)
    }

    /** The comparison key used by [buildIndex]; also handy for matching a single number. */
    fun matchKey(number: String?): String = PhoneNumberFormatter.matchKey(number)

    /**
     * Local search over name and number.
     *
     * Deliberately simple: substring on the name, digit-substring on the number. The real
     * T9 index, result ranking and dedup land with the dialer rebuild; this keeps the
     * Contacts tab working against the new number model in the meantime.
     */
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
                            it.raw.filter(Char::isDigit).contains(digits)
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
