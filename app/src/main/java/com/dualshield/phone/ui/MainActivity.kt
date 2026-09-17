package com.dualshield.phone.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dualshield.phone.BuildConfig
import com.dualshield.phone.DualShieldApplication
import com.dualshield.phone.ui.theme.DualShieldTheme

/**
 * The only activity the user normally sees.
 *
 * It owns the things that genuinely need an Activity — runtime permission dialogs, the role
 * prompts and sharing — and hands them to the Compose tree as plain lambdas so no screen has
 * to know about Android's result APIs.
 */
class MainActivity : ComponentActivity() {

    private var openVaultOnStart by mutableStateOf(false)
    private var initialDialNumber by mutableStateOf<String?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // A denied permission caches an empty list. Granting it later has to drop those
        // caches, or the user sits looking at an empty screen until the TTL expires.
        invalidateCaches()
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // Becoming the default dialer or SMS app changes what we are allowed to read.
        invalidateCaches()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        val container = (application as DualShieldApplication).container

        setContent {
            DualShieldTheme {
                DualShieldApp(
                    container = container,
                    actions = SystemActions(
                        requestScreeningRole = { requestRole(RoleManager.ROLE_CALL_SCREENING) },
                        requestDialerRole = { requestRole(RoleManager.ROLE_DIALER) },
                        requestSmsRole = ::requestSmsRole,
                        requestPhonePermissions = ::requestPhonePermissions,
                        requestContactsPermission = ::requestContactsPermission,
                        requestSmsPermission = ::requestSmsPermission,
                        shareText = ::shareText,
                    ),
                    startOnVault = openVaultOnStart,
                    initialDialNumber = initialDialNumber,
                    versionName = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Handles launcher taps, `tel:` links and the blocked-call notification. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        openVaultOnStart = intent.getBooleanExtra(EXTRA_OPEN_VAULT, false)
        initialDialNumber = when (intent.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW ->
                intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
            else -> null
        }
    }

    /**
     * Permissions are requested in the three groups Setup presents them in, so each prompt
     * arrives next to an explanation of why it is needed rather than as one long queue.
     */
    private fun requestPhonePermissions() {
        val permissions = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        launchPermissions(permissions)
    }

    private fun requestContactsPermission() {
        launchPermissions(listOf(Manifest.permission.READ_CONTACTS))
    }

    private fun requestSmsPermission() {
        launchPermissions(
            listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS),
        )
    }

    private fun launchPermissions(permissions: List<String>) {
        runCatching { permissionLauncher.launch(permissions.toTypedArray()) }
    }

    /** Drops every cached provider read after a permission or role change. */
    private fun invalidateCaches() {
        runCatching {
            val container = (application as DualShieldApplication).container
            container.simResolver.invalidate()
            container.contactsRepository.invalidateCache()
            container.callLogRepository.invalidateCache()
            container.smsRepository.invalidateThreadCache()
        }
    }

    private fun requestRole(role: String) {
        val container = (application as DualShieldApplication).container
        val intent = container.roleRepository.requestRoleIntent(role)
        if (intent == null) {
            // Nothing to show the user beyond the app's own settings; better than a crash.
            openAppSettings()
            return
        }
        runCatching { roleLauncher.launch(intent) }
    }

    private fun requestSmsRole() {
        val container = (application as DualShieldApplication).container
        val intent = container.roleRepository.requestDefaultSmsIntent()
        if (intent == null) {
            openAppSettings()
            return
        }
        runCatching { roleLauncher.launch(intent) }
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", packageName, null),
                ),
            )
        }
    }

    /**
     * Shares exported rules.
     *
     * This is the one place data leaves the app, and it only happens because the user asked
     * for it and chose the destination themselves.
     */
    private fun shareText(text: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_TITLE, "DualShieldPhone rules")
            }
            startActivity(Intent.createChooser(intent, "Export rules"))
        }
    }

    companion object {
        const val EXTRA_OPEN_VAULT = "com.dualshield.phone.OPEN_VAULT"
    }
}
