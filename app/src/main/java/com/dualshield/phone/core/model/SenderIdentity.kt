package com.dualshield.phone.core.model

import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.core.number.PhoneNumberNormalizer

/** What kind of party a message or call came from. */
enum class SenderType {
    /** Someone in the user's address book. */
    KNOWN_CONTACT,

    /** A DLT/alphanumeric sender ID: `AXISBK`, `JIO`, `HDFCBK`. */
    BUSINESS_SENDER,

    /** A short numeric code, the kind operators and services send from. */
    SHORT_CODE,

    /** An ordinary phone number nobody has saved. */
    PHONE_NUMBER,

    /** The network genuinely withheld the originating number. */
    WITHHELD,

    /** Digits that fit no shape we recognise. */
    UNKNOWN,
}

/**
 * What [SenderIdentity.resolve] needs to know about the user's address book.
 *
 * A single object rather than two lambdas on purpose: two same-shaped `(String) -> String?`
 * parameters are trivially swapped at a call site — a trailing lambda silently binds to the
 * last one — and the symptom is a contact's name quietly going missing.
 */
interface ContactLookup {
    fun nameFor(key: String): String?
    fun photoFor(key: String): String? = null

    /** Used where no address book is available; every sender resolves without a contact. */
    object None : ContactLookup {
        override fun nameFor(key: String): String? = null
    }
}

/**
 * Who a message is from, resolved once and reused by every screen.
 *
 * This exists because the app used to answer that question with `Private number` for
 * anything without a parseable phone number, which swept up every bank and operator the
 * user hears from. `Private number` now means one thing only: the network withheld the
 * caller's identity.
 */
data class SenderIdentity(
    val raw: String,
    val type: SenderType,
    /** What the user sees: a contact's name, a sender ID, or a formatted number. */
    val displayName: String,
    /** The formatted number, when there is one to show under the name. Null otherwise. */
    val displayNumber: String?,
    /** Stable identity for cross-screen lookup; empty when there is none. */
    val matchKey: String,
    /** The contact photo, when the sender is someone the user has saved. */
    val photoUri: String? = null,
) {
    val isContact: Boolean get() = type == SenderType.KNOWN_CONTACT

    companion object {

        /** An unsaved-contact short code is anything this short. */
        private const val SHORT_CODE_MAX_DIGITS = 6

        /** The label shown for a genuinely withheld caller, and for nothing else. */
        const val WITHHELD_LABEL = PhoneNumberFormatter.PRIVATE_LABEL

        /**
         * Resolves [rawAddress] against an already-built contact index.
         *
         * [contacts] is a pure in-memory lookup keyed by [PhoneNumberFormatter.matchKey];
         * nothing here touches a content provider, so this is safe to call while building a
         * list.
         */
        fun resolve(
            rawAddress: String?,
            contacts: ContactLookup = ContactLookup.None,
        ): SenderIdentity {
            val raw = rawAddress?.trim().orEmpty()
            val info = PhoneNumberNormalizer.normalize(raw)
            val key = PhoneNumberFormatter.matchKeyOf(info)
            val contactName = key.takeIf { it.isNotEmpty() }
                ?.let(contacts::nameFor)
                ?.takeIf { it.isNotBlank() }
            val formatted = PhoneNumberFormatter.displayOf(info)

            if (contactName != null) {
                return SenderIdentity(
                    raw = raw,
                    type = SenderType.KNOWN_CONTACT,
                    displayName = contactName,
                    displayNumber = formatted,
                    matchKey = key,
                    photoUri = contacts.photoFor(key),
                )
            }

            return when (info.kind) {
                NumberKind.ALPHANUMERIC_SENDER -> SenderIdentity(
                    raw = raw,
                    type = SenderType.BUSINESS_SENDER,
                    displayName = info.senderId.orEmpty().ifBlank { raw },
                    displayNumber = null,
                    matchKey = key,
                )

                NumberKind.PRIVATE -> SenderIdentity(
                    raw = raw,
                    type = SenderType.WITHHELD,
                    displayName = WITHHELD_LABEL,
                    displayNumber = null,
                    matchKey = "",
                )

                NumberKind.MALFORMED -> SenderIdentity(
                    raw = raw,
                    type = SenderType.UNKNOWN,
                    displayName = raw.ifBlank { "Unknown sender" },
                    displayNumber = null,
                    matchKey = key,
                )

                NumberKind.INDIAN_SERVICE_CODE -> {
                    val digits = info.normalized
                    val type = if (digits.length <= SHORT_CODE_MAX_DIGITS) {
                        SenderType.SHORT_CODE
                    } else {
                        SenderType.PHONE_NUMBER
                    }
                    SenderIdentity(
                        raw = raw,
                        type = type,
                        displayName = formatted,
                        displayNumber = null,
                        matchKey = key,
                    )
                }

                NumberKind.INDIAN_SUBSCRIBER, NumberKind.INTERNATIONAL -> SenderIdentity(
                    raw = raw,
                    type = SenderType.PHONE_NUMBER,
                    displayName = formatted,
                    displayNumber = null,
                    matchKey = key,
                )
            }
        }
    }
}
