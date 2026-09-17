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
    ) { /* Results are read back through each repository's own permission check. */ }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* Role state is re-read on the next Settings visit. */ }

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
                        requestPhonePermissions = ::requestCorePermissions,
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
     * Asks for the permissions the phone experience needs, in one contextual batch.
     *
     * Deliberately not every permission the app can use: SMS and contacts are requested by
     * their own tabs, so a user who never opens Messages is never asked for SMS access.
     */
    private fun requestCorePermissions() {
        val permissions = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        runCatching { permissionLauncher.launch(permissions.toTypedArray()) }
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
