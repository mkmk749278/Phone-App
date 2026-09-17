package com.dualshield.phone.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.ui.SystemActions
import com.dualshield.phone.ui.contacts.ContactsScreen
import com.dualshield.phone.ui.contacts.ContactsViewModel
import com.dualshield.phone.ui.messages.ConversationScreen
import com.dualshield.phone.ui.messages.MessagesScreen
import com.dualshield.phone.ui.messages.MessagesViewModel
import com.dualshield.phone.ui.onboarding.OnboardingScreen
import com.dualshield.phone.ui.onboarding.SetupScreen
import com.dualshield.phone.ui.phone.CallDetailsScreen
import com.dualshield.phone.ui.phone.DialpadScreen
import com.dualshield.phone.ui.phone.NumberActionSheet
import com.dualshield.phone.ui.phone.NumberActionTarget
import com.dualshield.phone.ui.phone.PhoneScreen
import com.dualshield.phone.ui.phone.PhoneViewModel
import com.dualshield.phone.ui.settings.CallRecordingScreen
import com.dualshield.phone.ui.settings.PrivacyScreen
import com.dualshield.phone.ui.settings.RulePacksScreen
import com.dualshield.phone.ui.settings.SettingsScreen
import com.dualshield.phone.ui.settings.SettingsViewModel
import com.dualshield.phone.ui.shield.AddRuleKind
import com.dualshield.phone.ui.shield.AllowlistScreen
import com.dualshield.phone.ui.shield.BlockedNumbersScreen
import com.dualshield.phone.ui.shield.IndiaProtectionScreen
import com.dualshield.phone.ui.shield.RecoveryProtectionScreen
import com.dualshield.phone.ui.shield.RuleEditorScreen
import com.dualshield.phone.ui.shield.RuleTesterScreen
import com.dualshield.phone.ui.shield.ShieldScreen
import com.dualshield.phone.ui.shield.ShieldViewModel
import com.dualshield.phone.ui.shield.SimRulesScreen
import com.dualshield.phone.ui.shield.VaultDetailScreen
import com.dualshield.phone.ui.shield.VaultScreen
import com.dualshield.phone.ui.shield.VaultViewModel
import com.dualshield.phone.ui.shield.patternType

/** How long a screen transition runs. Short enough to feel like a response, not an effect. */
private const val TRANSITION_MS = 190

/**
 * How far a screen slides in, as a fraction of the width.
 *
 * A partial slide rather than a full one: the incoming screen is already opaque, so a short
 * move reads as depth without the whole display sweeping sideways.
 */
private const val DEPTH_DIVISOR = 6

private val TRANSITION_SPEC = tween<IntOffset>(durationMillis = TRANSITION_MS)
private val FADE_SPEC = tween<Float>(durationMillis = TRANSITION_MS)

/**
 * True when this transition is between two of the bottom-navigation tabs.
 *
 * Tab switches are not navigation in depth, so they get no animation at all.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    isTopLevelRoute(initialState.destination.route) &&
        isTopLevelRoute(targetState.destination.route)

/** Wires every route to its screen. Screens stay free of navigation logic. */
@Composable
fun DualShieldNavHost(
    navController: NavHostController,
    startDestination: String,
    phoneViewModel: PhoneViewModel,
    messagesViewModel: MessagesViewModel,
    contactsViewModel: ContactsViewModel,
    shieldViewModel: ShieldViewModel,
    vaultViewModel: VaultViewModel,
    settingsViewModel: SettingsViewModel,
    actions: SystemActions,
    versionName: String,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // An opaque background under the graph. Every screen carries its own Scaffold, and
        // during a crossfade two of them were being blended together — which is exactly the
        // "ghost screen" the recording showed. With a solid surface underneath, nothing
        // shows through whatever is on top.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // Switching tabs is immediate: a tab bar is a place selector, and animating it makes
        // the app feel slower than the tap. Everything else gets one controlled horizontal
        // push, so forward and back read as depth rather than as a dissolve.
        enterTransition = {
            if (isTabSwitch()) {
                EnterTransition.None
            } else {
                slideInHorizontally(TRANSITION_SPEC) { width -> width / DEPTH_DIVISOR } +
                    fadeIn(FADE_SPEC)
            }
        },
        exitTransition = {
            if (isTabSwitch()) {
                ExitTransition.None
            } else {
                slideOutHorizontally(TRANSITION_SPEC) { width -> -width / DEPTH_DIVISOR } +
                    fadeOut(FADE_SPEC)
            }
        },
        popEnterTransition = {
            if (isTabSwitch()) {
                EnterTransition.None
            } else {
                slideInHorizontally(TRANSITION_SPEC) { width -> -width / DEPTH_DIVISOR } +
                    fadeIn(FADE_SPEC)
            }
        },
        popExitTransition = {
            if (isTabSwitch()) {
                ExitTransition.None
            } else {
                slideOutHorizontally(TRANSITION_SPEC) { width -> width / DEPTH_DIVISOR } +
                    fadeOut(FADE_SPEC)
            }
        },
    ) {

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onContinue = { navController.navigate(Routes.setup(first = true)) },
                onSkip = {
                    settingsViewModel.setOnboardingComplete()
                    navController.navigate(Routes.PHONE) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.SETUP,
            arguments = listOf(
                navArgument("first") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val first = entry.arguments?.getBoolean("first") ?: false
            val state by settingsViewModel.state.collectAsStateWithLifecycle()

            // Roles and permissions can change while we are backgrounded in system settings.
            LaunchedEffect(Unit) { settingsViewModel.refreshRoles() }

            SetupScreen(
                sims = state.sims,
                roles = state.roles,
                hasPhonePermissions = state.permissions.phone,
                hasContactsPermission = state.permissions.contacts,
                hasSmsPermission = state.permissions.sms,
                showDoneButton = first,
                onSimLabelChange = settingsViewModel::setSimLabel,
                onToggleProtection = settingsViewModel::setProtectionEnabled,
                onRequestPhonePermissions = actions.requestPhonePermissions,
                onRequestContactsPermission = actions.requestContactsPermission,
                onRequestSmsPermission = actions.requestSmsPermission,
                onRequestScreeningRole = actions.requestScreeningRole,
                onRequestDialerRole = actions.requestDialerRole,
                onRequestSmsRole = actions.requestSmsRole,
                onBack = {
                    if (first) {
                        settingsViewModel.setOnboardingComplete()
                        navController.navigate(Routes.PHONE) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDone = {
                    settingsViewModel.setOnboardingComplete()
                    navController.navigate(Routes.PHONE) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        // ---------------------------------------------------------------- Phone

        composable(Routes.PHONE) {
            val state by phoneViewModel.state.collectAsStateWithLifecycle()
            val recents by phoneViewModel.filteredRecents.collectAsStateWithLifecycle()
            // The row that was tapped, if any. Held here rather than inside the list so the
            // sheet survives the list recomposing underneath it.
            var actionTarget by remember { mutableStateOf<NumberActionTarget?>(null) }
            LaunchedEffect(Unit) { phoneViewModel.refreshIfStale() }
            PhoneScreen(
                state = state,
                recents = recents,
                onQueryChange = phoneViewModel::onQueryChange,
                onOpenDialpad = { navController.navigate(Routes.DIALPAD) },
                onOpenDetails = { navController.navigate(Routes.callDetails(it)) },
                onOpenActions = { call ->
                    actionTarget = NumberActionTarget(
                        rawNumber = call.number,
                        displayNumber = call.displayNumber,
                        displayName = call.displayName,
                        photoUri = call.photoUri,
                        contactId = call.contactId,
                    )
                },
                onOpenBlockedCalls = { navController.navigate(Routes.VAULT) },
                onOpenContactActions = { contact ->
                    val number = contact.phoneNumbers.firstOrNull()
                    if (number != null) {
                        actionTarget = NumberActionTarget(
                            rawNumber = number.raw,
                            displayNumber = number.display,
                            displayName = contact.displayName,
                            photoUri = contact.photoUri,
                            contactId = contact.id,
                        )
                    }
                },
            )

            actionTarget?.let { target ->
                NumberActionSheet(
                    target = target,
                    sims = state.sims,
                    defaultSlot = state.selectedSlot,
                    onDismiss = { actionTarget = null },
                    onCall = phoneViewModel::call,
                    onMessage = { navController.navigate(Routes.conversation(-1L, it)) },
                    onBlock = { navController.navigate(Routes.callDetails(it)) },
                    onOpenDetails = { navController.navigate(Routes.callDetails(it)) },
                    onActionFailed = phoneViewModel::showMessage,
                )
            }
        }

        composable(Routes.DIALPAD) {
            val state by phoneViewModel.state.collectAsStateWithLifecycle()
            val suggestions by phoneViewModel.dialSuggestions.collectAsStateWithLifecycle()
            DialpadScreen(
                state = state,
                suggestions = suggestions,
                onDigit = phoneViewModel::onDigit,
                onZeroLongPress = phoneViewModel::onZeroLongPress,
                onBackspace = phoneViewModel::onBackspace,
                onClear = phoneViewModel::onClearDial,
                onSelectSim = phoneViewModel::onSelectSim,
                onCall = phoneViewModel::call,
                onPickSuggestion = phoneViewModel::setDialInput,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CALL_DETAILS,
            arguments = listOf(navArgument("number") { type = NavType.StringType }),
        ) { entry ->
            val number = entry.arguments?.getString("number").orEmpty()
            val state by phoneViewModel.state.collectAsStateWithLifecycle()
            val details by phoneViewModel.callDetails.collectAsStateWithLifecycle()
            LaunchedEffect(number) { phoneViewModel.openDetails(number) }
            CallDetailsScreen(
                number = number,
                displayName = details.contact?.displayName,
                photoUri = details.photoUri,
                contactId = details.contactId,
                isBlocked = details.isBlocked,
                history = details.history,
                sims = state.sims,
                defaultSlot = state.selectedSlot,
                onBack = { navController.popBackStack() },
                onCall = phoneViewModel::call,
                onMessage = { navController.navigate(Routes.conversation(-1L, it)) },
                onBlock = { scope ->
                    phoneViewModel.blockNumber(number, details.contact?.displayName, scope)
                },
                onUnblock = { phoneViewModel.unblockNumber(number) },
                onMessageShown = phoneViewModel::showMessage,
            )
        }

        // ---------------------------------------------------------------- Messages

        composable(Routes.MESSAGES) {
            val state by messagesViewModel.state.collectAsStateWithLifecycle()
            val threads by messagesViewModel.filteredThreads.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { messagesViewModel.refreshIfStale() }
            MessagesScreen(
                state = state,
                threads = threads,
                onQueryChange = messagesViewModel::onQueryChange,
                onOpenThread = { threadId, address ->
                    navController.navigate(Routes.conversation(threadId, address))
                },
                onNewMessage = { navController.navigate(Routes.NEW_MESSAGE) },
                onOpenVault = { navController.navigate(Routes.VAULT) },
            )
        }

        composable(
            route = Routes.CONVERSATION,
            arguments = listOf(
                navArgument("threadId") { type = NavType.LongType },
                navArgument("address") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            ConversationRoute(
                threadId = entry.arguments?.getLong("threadId") ?: -1L,
                address = entry.arguments?.getString("address").orEmpty(),
                messagesViewModel = messagesViewModel,
                phoneViewModel = phoneViewModel,
                onCall = { phoneViewModel.call(it) },
                onOpenDetails = { navController.navigate(Routes.callDetails(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NEW_MESSAGE) {
            ConversationRoute(
                threadId = -1L,
                address = "",
                messagesViewModel = messagesViewModel,
                phoneViewModel = phoneViewModel,
                onCall = { phoneViewModel.call(it) },
                onOpenDetails = { navController.navigate(Routes.callDetails(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------- Contacts

        composable(Routes.CONTACTS) {
            val state by contactsViewModel.state.collectAsStateWithLifecycle()
            val contacts by contactsViewModel.visibleContacts.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { contactsViewModel.refreshIfStale() }
            ContactsScreen(
                state = state,
                contacts = contacts,
                onQueryChange = contactsViewModel::onQueryChange,
                onOpenContact = { navController.navigate(Routes.callDetails(it)) },
            )
        }

        // ---------------------------------------------------------------- Shield

        composable(Routes.SHIELD) {
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            ShieldScreen(
                state = state,
                onToggleSim = shieldViewModel::setProtectionEnabled,
                onOpenSim = { navController.navigate(Routes.shieldSim(it)) },
                onOpenBlockedNumbers = { navController.navigate(Routes.BLOCKED_NUMBERS) },
                onOpenAllowlist = { navController.navigate(Routes.allowlist(null)) },
                onOpenIndiaProtection = { navController.navigate(Routes.INDIA_PROTECTION) },
                onOpenVault = { navController.navigate(Routes.VAULT) },
                onOpenRecovery = { navController.navigate(Routes.RECOVERY_PROTECTION) },
                onOpenTester = { navController.navigate(Routes.SHIELD_TEST) },
                onPause = shieldViewModel::pauseShield,
                onResume = shieldViewModel::resumeShield,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.RECOVERY_PROTECTION) {
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            val slot by shieldViewModel.recoverySlot.collectAsStateWithLifecycle()
            RecoveryProtectionScreen(
                sims = state.sims,
                selectedSlot = slot,
                settings = state.recoveryFor(slot),
                onSelectSlot = shieldViewModel::onRecoverySlot,
                onChange = { settings ->
                    slot?.let { shieldViewModel.setRecoverySettings(it, settings) }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.BLOCKED_NUMBERS) {
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            BlockedNumbersScreen(
                rules = state.userRules,
                sims = state.sims,
                onBack = { navController.popBackStack() },
                onOpenRule = { navController.navigate(Routes.rule(it)) },
                onToggleAction = shieldViewModel::toggleRuleAction,
                onAdd = { kind ->
                    val scope = state.sims.firstOrNull { it.protectionEnabled }
                        ?.let { SimScope.forSlot(it.slotIndex) }
                        ?: SimScope.SIM2
                    if (kind == AddRuleKind.CONTACT) {
                        navController.navigate(Routes.CONTACTS)
                    } else {
                        navController.navigate(
                            Routes.newRule(scope.name, kind.patternType().name, ""),
                        )
                    }
                },
            )
        }

        composable(Routes.INDIA_PROTECTION) {
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            val slot by shieldViewModel.indiaSlot.collectAsStateWithLifecycle()
            IndiaProtectionScreen(
                rules = state.rules.filter { it.builtIn },
                sims = state.sims,
                selectedSlot = slot,
                onSelectSlot = shieldViewModel::onIndiaSlot,
                onToggleRule = shieldViewModel::setRuleEnabled,
                onOpenRule = { navController.navigate(Routes.rule(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SHIELD_SIM,
            arguments = listOf(navArgument("slot") { type = NavType.IntType }),
        ) { entry ->
            val slot = entry.arguments?.getInt("slot") ?: 0
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            SimRulesScreen(
                slotIndex = slot,
                state = state,
                onBack = { navController.popBackStack() },
                onToggleProtection = { shieldViewModel.setProtectionEnabled(slot, it) },
                onToggleAllowContacts = { shieldViewModel.setAllowContacts(slot, it) },
                onToggleRule = shieldViewModel::setRuleEnabled,
                onToggleRuleAction = shieldViewModel::toggleRuleAction,
                onOpenRule = { navController.navigate(Routes.rule(it)) },
                onAddRule = {
                    val scope = SimScope.forSlot(slot) ?: SimScope.SIM2
                    navController.navigate(
                        Routes.newRule(scope.name, PatternType.EXACT.name, ""),
                    )
                },
                onOpenAllowlist = { navController.navigate(Routes.allowlist(slot)) },
                onOpenIndiaProtection = { navController.navigate(Routes.INDIA_PROTECTION) },
            )
        }

        composable(
            route = Routes.SHIELD_RULE_NEW,
            arguments = listOf(
                navArgument("scope") {
                    type = NavType.StringType
                    defaultValue = SimScope.SIM2.name
                },
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = PatternType.EXACT.name
                },
                navArgument("pattern") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val scopeName = entry.arguments?.getString("scope") ?: SimScope.SIM2.name
            val typeName = entry.arguments?.getString("type") ?: PatternType.EXACT.name
            val pattern = entry.arguments?.getString("pattern").orEmpty()
            LaunchedEffect(scopeName, typeName, pattern) {
                shieldViewModel.startNewRule(
                    scope = SimScope.entries.firstOrNull { it.name == scopeName } ?: SimScope.SIM2,
                    patternType = PatternType.entries.firstOrNull { it.name == typeName }
                        ?: PatternType.EXACT,
                    pattern = pattern,
                )
            }
            RuleEditorRoute(shieldViewModel) { navController.popBackStack() }
        }

        composable(
            route = Routes.SHIELD_RULE,
            arguments = listOf(navArgument("ruleId") { type = NavType.LongType }),
        ) { entry ->
            val ruleId = entry.arguments?.getLong("ruleId") ?: 0L
            LaunchedEffect(ruleId) { shieldViewModel.loadRule(ruleId) }
            RuleEditorRoute(shieldViewModel) { navController.popBackStack() }
        }

        composable(Routes.SHIELD_TEST) {
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            val tester by shieldViewModel.tester.collectAsStateWithLifecycle()
            RuleTesterScreen(
                tester = tester,
                sims = state.sims,
                onNumberChange = shieldViewModel::onTesterNumber,
                onSlotChange = shieldViewModel::onTesterSlot,
                onRun = shieldViewModel::runTester,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.VAULT) {
            val state by vaultViewModel.state.collectAsStateWithLifecycle()
            VaultScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOpenRecord = { navController.navigate(Routes.vaultDetail(it)) },
                onClearVault = vaultViewModel::clearVault,
            )
        }

        composable(
            route = Routes.VAULT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            val state by vaultViewModel.state.collectAsStateWithLifecycle()
            val record = state.blockedCalls.firstOrNull { it.id == id }
            VaultDetailScreen(
                record = record,
                sims = state.sims,
                onBack = { navController.popBackStack() },
                onAllow = { scope -> record?.let { vaultViewModel.allow(it, scope) } },
                onBlockAlways = { scope -> record?.let { vaultViewModel.blockAlways(it, scope) } },
                onDelete = {
                    vaultViewModel.delete(id)
                    navController.popBackStack()
                },
            )
        }

        composable(
            route = Routes.ALLOWLIST,
            arguments = listOf(
                navArgument("slot") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) { entry ->
            val slot = entry.arguments?.getInt("slot")?.takeIf { it >= 0 }
            val state by shieldViewModel.state.collectAsStateWithLifecycle()
            AllowlistScreen(
                state = state,
                sims = state.sims,
                slotIndex = slot,
                onBack = { navController.popBackStack() },
                onRemove = shieldViewModel::deleteAllowRule,
            )
        }

        // ---------------------------------------------------------------- Settings

        composable(Routes.SETTINGS) {
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { settingsViewModel.refreshRoles() }
            SettingsScreen(
                state = state,
                versionName = versionName,
                onBack = { navController.popBackStack() },
                onOpenSetup = { navController.navigate(Routes.setup(first = false)) },
                onSimLabelChange = settingsViewModel::setSimLabel,
                onNotifyChange = settingsViewModel::setNotifyOnBlockedCall,
                onOpenShield = { navController.navigate(Routes.SHIELD) },
                onOpenCallRecording = { navController.navigate(Routes.CALL_RECORDING) },
                onOpenVault = { navController.navigate(Routes.VAULT) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenRulePacks = { navController.navigate(Routes.RULE_PACKS) },
                onOpenBlockedNumbers = { navController.navigate(Routes.BLOCKED_NUMBERS) },
                onOpenAllowlist = { navController.navigate(Routes.allowlist(null)) },
                onOpenIndiaBlocklist = { navController.navigate(Routes.INDIA_PROTECTION) },
                onOpenRecovery = { navController.navigate(Routes.RECOVERY_PROTECTION) },
            )
        }

        composable(Routes.CALL_RECORDING) {
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { settingsViewModel.detectRecordingCapability() }
            CallRecordingScreen(
                capability = state.recordingCapability,
                onRecheck = { settingsViewModel.detectRecordingCapability(force = true) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.RULE_PACKS) {
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            RulePacksScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onExport = settingsViewModel::exportRules,
                onImport = settingsViewModel::importRules,
                onShareExport = actions.shareText,
                onConsumeExport = settingsViewModel::consumeExport,
            )
        }
    }
}

@Composable
private fun ConversationRoute(
    threadId: Long,
    address: String,
    messagesViewModel: MessagesViewModel,
    phoneViewModel: PhoneViewModel,
    onCall: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by messagesViewModel.state.collectAsStateWithLifecycle()
    val conversation by messagesViewModel.conversation.collectAsStateWithLifecycle()

    LaunchedEffect(threadId, address) { messagesViewModel.openThread(threadId, address) }

    ConversationScreen(
        conversation = conversation,
        sims = state.sims,
        selectedSlot = state.selectedSlot,
        onSelectSim = messagesViewModel::onSelectSim,
        onDraftChange = messagesViewModel::onDraftChange,
        onRecipientChange = messagesViewModel::onRecipientChange,
        onSend = messagesViewModel::send,
        onCall = onCall,
        onOpenDetails = onOpenDetails,
        onActionFailed = phoneViewModel::showMessage,
        onBack = onBack,
    )
}

@Composable
private fun RuleEditorRoute(
    shieldViewModel: ShieldViewModel,
    onBack: () -> Unit,
) {
    val state by shieldViewModel.state.collectAsStateWithLifecycle()
    val draft by shieldViewModel.draft.collectAsStateWithLifecycle()

    RuleEditorScreen(
        draft = draft,
        sims = state.sims,
        onBack = onBack,
        onName = shieldViewModel::onDraftName,
        onPattern = shieldViewModel::onDraftPattern,
        onPatternType = shieldViewModel::onDraftPatternType,
        onScope = shieldViewModel::onDraftScope,
        onAction = shieldViewModel::onDraftAction,
        onDescription = shieldViewModel::onDraftDescription,
        onEnabled = shieldViewModel::onDraftEnabled,
        onBlocksCalls = shieldViewModel::onDraftBlocksCalls,
        onBlocksSms = shieldViewModel::onDraftBlocksSms,
        onTestNumber = shieldViewModel::onDraftTestNumber,
        onTest = shieldViewModel::testDraft,
        onSave = shieldViewModel::saveDraft,
    )
}
