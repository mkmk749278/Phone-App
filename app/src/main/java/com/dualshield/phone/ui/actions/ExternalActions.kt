package com.dualshield.phone.ui.actions

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import com.dualshield.phone.core.number.PhoneNumberFormatter

/**
 * Hand-offs to apps that are not us.
 *
 * DualShieldPhone does not reimplement WhatsApp or the contact editor. It hands the number
 * to the real app and gets out of the way, and when the hand-off fails it says so instead of
 * leaving the user staring at a button that did nothing.
 *
 * Every launch returns a boolean rather than throwing: a missing app is an ordinary outcome
 * on someone else's device, not an error condition.
 */
object ExternalActions {

    /** WhatsApp's documented click-to-chat host. */
    private const val WA_HOST = "https://wa.me/"

    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /**
     * True when this number is one WhatsApp could address.
     *
     * A service code or short code is not, so the action is hidden rather than offered and
     * then failed. Whether the person actually *has* WhatsApp is not knowable offline — the
     * app is not queried for account existence, and no such check is invented.
     */
    fun canOpenWhatsApp(context: Context, rawNumber: String?): Boolean =
        PhoneNumberFormatter.internationalDigits(rawNumber) != null &&
            isWhatsAppInstalled(context)

    /**
     * Whether either WhatsApp app is on the device.
     *
     * Asks about the two packages by name rather than listing everything installed: from
     * API 30 a general package listing needs QUERY_ALL_PACKAGES, a permission this app has
     * no business holding. The two names are declared in `<queries>` in the manifest, which
     * is the narrow, intended way to ask.
     */
    fun isWhatsAppInstalled(context: Context): Boolean =
        isInstalled(context, WHATSAPP_PACKAGE) || isInstalled(context, WHATSAPP_BUSINESS_PACKAGE)

    private fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) != null }
            .getOrDefault(false)

    /**
     * Opens a WhatsApp chat with [rawNumber].
     *
     * The link carries the number in international form with no `+`, spaces, brackets or
     * dashes, which is the format WhatsApp documents.
     */
    fun openWhatsApp(context: Context, rawNumber: String?): Boolean {
        val digits = PhoneNumberFormatter.internationalDigits(rawNumber) ?: return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$WA_HOST$digits"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, intent)
    }

    /** Opens the system's new-contact editor pre-filled with [rawNumber]. */
    fun createContact(context: Context, rawNumber: String?, name: String? = null): Boolean {
        val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, rawNumber.orEmpty())
            if (!name.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.NAME, name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(context, intent)
    }

    /**
     * Opens the picker that adds [rawNumber] to a contact the user already has.
     *
     * Same insert intent, but without a contact type, which is what tells the platform
     * editor to offer the existing-contact list first.
     */
    fun addToExistingContact(context: Context, rawNumber: String?): Boolean {
        val intent = Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, rawNumber.orEmpty())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return launch(context, intent)
    }

    /** Opens a saved contact in the system contacts app. */
    fun viewContact(context: Context, contactId: Long): Boolean {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, intent)
    }

    private fun launch(context: Context, intent: Intent): Boolean =
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            // Some OEM contact editors guard their exported activities more tightly than
            // AOSP does. Treated the same as "not there": the caller shows a message.
            false
        }
}
