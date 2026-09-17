package com.dualshield.phone.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Which privileged roles the app currently holds. Drives the onboarding and Settings UI. */
data class RoleStatus(
    val isDefaultDialer: Boolean,
    val isCallScreener: Boolean,
    val isDefaultSmsApp: Boolean,
)

/**
 * Reads and requests the platform roles the app needs.
 *
 * Each role is requested on its own, at the moment it is actually needed, rather than as a
 * checklist on first launch. Call screening is the only one required for Shield to work —
 * the dialer and SMS roles are what make this a phone app rather than a filter.
 */
class RoleRepository(private val context: Context) {

    fun status(): RoleStatus = RoleStatus(
        isDefaultDialer = isDefaultDialer(),
        isCallScreener = holdsRole(RoleManager.ROLE_CALL_SCREENING),
        isDefaultSmsApp = isDefaultSmsApp(),
    )

    fun isDefaultDialer(): Boolean = runCatching {
        holdsRole(RoleManager.ROLE_DIALER)
    }.getOrDefault(false)

    fun isDefaultSmsApp(): Boolean = runCatching {
        context.packageName == Telephony.Sms.getDefaultSmsPackage(context)
    }.getOrDefault(false)

    fun holdsRole(role: String): Boolean = runCatching {
        val manager = context.getSystemService(RoleManager::class.java) ?: return false
        manager.isRoleAvailable(role) && manager.isRoleHeld(role)
    }.getOrDefault(false)

    /** @return an intent to launch for the role, or null when the device cannot offer it. */
    fun requestRoleIntent(role: String): Intent? = runCatching {
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(role)) return null
        manager.createRequestRoleIntent(role)
    }.getOrNull()

    fun requestDefaultSmsIntent(): Intent? = requestRoleIntent(RoleManager.ROLE_SMS)
}
