package com.dualshield.phone.ui.navigation

import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.People
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every destination in the app.
 *
 * There are three top-level routes — Phone, Messages, Contacts — and Shield is not one of
 * them. Shield is configuration, not a place you visit, so it lives under Settings with the
 * rest of the settings. The app should feel like a phone that happens to be protected, not
 * a security console that happens to make calls.
 */
object Routes {
    const val ONBOARDING = "onboarding"
    const val SETUP = "setup?first={first}"

    const val PHONE = "phone"
    const val DIALPAD = "phone/dialpad"
    const val CALL_DETAILS = "phone/details/{number}"

    const val MESSAGES = "messages"
    const val CONVERSATION = "messages/thread/{threadId}?address={address}"
    const val NEW_MESSAGE = "messages/new"

    const val CONTACTS = "contacts"

    const val SHIELD = "shield"
    const val SHIELD_SIM = "shield/sim/{slot}"
    const val SHIELD_RULE_NEW = "shield/rule/new?scope={scope}&type={type}&pattern={pattern}"
    const val SHIELD_RULE = "shield/rule/{ruleId}"
    const val SHIELD_TEST = "shield/test"
    const val BLOCKED_NUMBERS = "shield/blocked"
    const val INDIA_PROTECTION = "shield/india"
    const val VAULT = "shield/vault"
    const val VAULT_DETAIL = "shield/vault/{id}"
    const val ALLOWLIST = "shield/allowlist?slot={slot}"

    const val SETTINGS = "settings"
    const val PRIVACY = "settings/privacy"
    const val RULE_PACKS = "settings/rulepacks"

    fun callDetails(number: String): String = "phone/details/${Uri.encode(number)}"

    fun conversation(threadId: Long, address: String): String =
        "messages/thread/$threadId?address=${Uri.encode(address)}"

    fun shieldSim(slot: Int): String = "shield/sim/$slot"

    fun setup(first: Boolean): String = "setup?first=$first"

    fun newRule(scope: String, type: String, pattern: String): String =
        "shield/rule/new?scope=$scope&type=$type&pattern=${Uri.encode(pattern)}"

    fun rule(ruleId: Long): String = "shield/rule/$ruleId"

    fun vaultDetail(id: Long): String = "shield/vault/$id"

    fun allowlist(slot: Int?): String =
        if (slot == null) "shield/allowlist?slot=-1" else "shield/allowlist?slot=$slot"
}

/** A bottom-navigation destination. */
data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val TOP_LEVEL_DESTINATIONS = listOf(
    TopLevelDestination(Routes.PHONE, "Phone", Icons.Filled.Call),
    TopLevelDestination(Routes.MESSAGES, "Messages", Icons.AutoMirrored.Filled.Message),
    TopLevelDestination(Routes.CONTACTS, "Contacts", Icons.Filled.People),
)

/** True when [route] is one of the three tabs. Drives both the bars and the transitions. */
fun isTopLevelRoute(route: String?): Boolean =
    route != null && TOP_LEVEL_DESTINATIONS.any { it.route == route }
