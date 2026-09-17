package com.dualshield.phone.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dualshield.phone.AppContainer
import com.dualshield.phone.ui.components.AppHeader
import com.dualshield.phone.ui.contacts.ContactsViewModel
import com.dualshield.phone.ui.messages.MessagesViewModel
import com.dualshield.phone.ui.navigation.DualShieldNavHost
import com.dualshield.phone.ui.navigation.Routes
import com.dualshield.phone.ui.navigation.TOP_LEVEL_DESTINATIONS
import com.dualshield.phone.ui.phone.PhoneViewModel
import com.dualshield.phone.ui.settings.SettingsViewModel
import com.dualshield.phone.ui.shield.ShieldViewModel
import com.dualshield.phone.ui.shield.VaultViewModel

/** Actions that need an Activity (permission dialogs, role prompts, sharing). */
data class SystemActions(
    val requestScreeningRole: () -> Unit,
    val requestDialerRole: () -> Unit,
    val requestSmsRole: () -> Unit,
    val requestPhonePermissions: () -> Unit,
    val shareText: (String) -> Unit,
)

/**
 * The app shell: header, four-tab bottom navigation, and the nav host.
 *
 * ViewModels are created here rather than per-destination so that state — the SIM you
 * picked, the search you typed — survives moving between tabs, which is what makes this
 * feel like one phone app instead of four screens.
 */
@Composable
fun DualShieldApp(
    container: AppContainer,
    actions: SystemActions,
    startOnVault: Boolean,
    initialDialNumber: String?,
    versionName: String,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val factory = remember(container) { appViewModelFactory(container) }

    val phoneViewModel: PhoneViewModel = viewModel(factory = factory)
    val messagesViewModel: MessagesViewModel = viewModel(factory = factory)
    val contactsViewModel: ContactsViewModel = viewModel(factory = factory)
    val shieldViewModel: ShieldViewModel = viewModel(factory = factory)
    val vaultViewModel: VaultViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val phoneState by phoneViewModel.state.collectAsStateWithLifecycle()
    val messagesState by messagesViewModel.state.collectAsStateWithLifecycle()
    val shieldState by shieldViewModel.state.collectAsStateWithLifecycle()
    val vaultState by vaultViewModel.state.collectAsStateWithLifecycle()

    // Wait for the stored settings before building the graph, so the start destination is
    // decided once and onboarding never flashes for an existing user.
    if (!settingsState.settings.isLoaded) return

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination
    val showBottomBar = TOP_LEVEL_DESTINATIONS.any { destination ->
        currentRoute?.hierarchy?.any { it.route == destination.route } == true
    }

    // Deep links and notification taps land here.
    LaunchedEffect(startOnVault) {
        if (startOnVault) navController.navigate(Routes.VAULT)
    }
    LaunchedEffect(initialDialNumber) {
        initialDialNumber?.let {
            phoneViewModel.setDialInput(it)
            navController.navigate(Routes.DIALPAD)
        }
    }

    // One snackbar channel for the whole app, fed by whichever ViewModel has something to say.
    LaunchedEffect(phoneState.message) {
        phoneState.message?.let {
            snackbarHostState.showSnackbar(it)
            phoneViewModel.consumeMessage()
        }
    }
    LaunchedEffect(messagesState.message) {
        messagesState.message?.let {
            snackbarHostState.showSnackbar(it)
            messagesViewModel.consumeMessage()
        }
    }
    LaunchedEffect(shieldState.message) {
        shieldState.message?.let {
            snackbarHostState.showSnackbar(it)
            shieldViewModel.consumeMessage()
        }
    }
    LaunchedEffect(vaultState.message) {
        vaultState.message?.let {
            snackbarHostState.showSnackbar(it)
            vaultViewModel.consumeMessage()
        }
    }
    LaunchedEffect(settingsState.message) {
        settingsState.message?.let {
            snackbarHostState.showSnackbar(it)
            settingsViewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (showBottomBar) {
                AppHeader(
                    title = "DualShieldPhone",
                    onSettings = {
                        settingsViewModel.refreshRoles()
                        navController.navigate(Routes.SETTINGS)
                    },
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TOP_LEVEL_DESTINATIONS.forEach { destination ->
                        val selected =
                            currentRoute?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(destination.icon, contentDescription = null)
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            DualShieldNavHost(
                navController = navController,
                startDestination = if (settingsState.settings.onboardingComplete) {
                    Routes.PHONE
                } else {
                    Routes.ONBOARDING
                },
                phoneViewModel = phoneViewModel,
                messagesViewModel = messagesViewModel,
                contactsViewModel = contactsViewModel,
                shieldViewModel = shieldViewModel,
                vaultViewModel = vaultViewModel,
                settingsViewModel = settingsViewModel,
                actions = actions,
                versionName = versionName,
            )
        }
    }
}
