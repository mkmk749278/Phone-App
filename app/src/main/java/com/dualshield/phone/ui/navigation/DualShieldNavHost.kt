package com.dualshield.phone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.dualshield.phone.ui.phone.CallDetailsScreen
import com.dualshield.phone.ui.phone.DialpadScreen
import com.dualshield.phone.ui.phone.PhoneScreen
import com.dualshield.phone.ui.phone.PhoneViewModel
import com.dualshield.phone.ui.settings.PrivacyScreen
import com.dualshield.phone.ui.settings.RulePacksScreen
import com.dualshield.phone.ui.settings.SettingsScreen
import com.dualshield.phone.ui.settings.SettingsViewModel
import com.dualshield.phone.ui.shield.AllowlistScreen
import com.dualshield.phone.ui.shield.RuleEditorScreen
import com.dualshield.phone.ui.shield.RuleTesterScreen
import com.dualshield.phone.ui.shield.ShieldScreen
import com.dualshield.phone.ui.shield.ShieldViewModel
import com.dualshield.phone.ui.shield.SimRulesScreen
import com.dualshield.phone.ui.shield.VaultDetailScreen
import com.dualshield.phone.ui.shield.VaultScreen
import com.dualshield.phone.ui.shield.VaultViewModel

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
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.ONBOARDING) {
            val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
            OnboardingScreen(
                sims = settingsState.sims,
                onRequestScreeningRole = actions.requestScreeningRole,
                onRequestPhonePermissions = actions.requestPhonePermissions,
                onFinish = {
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
            LaunchedEffect(Unit) { phoneViewModel.refresh() }
            PhoneScreen(
                state = state,
                onQueryChange = phoneViewModel::onQueryChange,
                onOpenDialpad = { navController.navigate(Routes.DIALPAD) },
                onOpenDetails = { navController.navigate(Routes.callDetails(it)) },
            )
        }

        composable(Routes.DIALPAD) {
            val state by phoneViewModel.state.collectAsStateWithLifecycle()
            DialpadScreen(
                state = state,
                suggestions = phoneViewModel.dialSuggestions(),
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
            val contact = phoneViewModel.contactFor(number)
            CallDetailsScreen(
                number = number,
                displayName = contact?.displayName,
                history = phoneViewModel.historyFor(number),
                sims = state.sims,
                defaultSlot = state.selectedSlot,
                onBack = { navController.popBackStack() },
                onCall = phoneViewModel::call,
                onMessage = { navController.navigate(Routes.conversation(-1L, it)) },
                onBlock = { scope ->
                    phoneViewModel.blockNumber(number, contact?.displayName, scope)
                },
                onAllow = { scope ->
                    phoneViewModel.allowNumber(number, contact?.displayName, scope)
                },
            )
        }

        // ---------------------------------------------------------------- Messages

        composable(Routes.MESSAGES) {
            val state by messagesViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { messagesViewModel.refresh() }
            MessagesScreen(
                state = state,
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
            val threadId = entry.arguments?.getLong("threadId") ?: -1L
            val address = entry.arguments?.getString("address").orEmpty()
            ConversationRoute(
                threadId = threadId,
                address = address,
                messagesViewModel = messagesViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.NEW_MESSAGE) {
            ConversationRoute(
                threadId = -1L,
                address = "",
                messagesViewModel = messagesViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        // ---------------------------------------------------------------- Contacts

        composable(Routes.CONTACTS) {
            val state by contactsViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { contactsViewModel.refresh() }
            ContactsScreen(
                state = state,
                contacts = contactsViewModel.visibleContacts(),
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
                onOpenVault = { navController.navigate(Routes.VAULT) },
                onOpenTester = { navController.navigate(Routes.SHIELD_TEST) },
                onOpenAllowlist = { navController.navigate(Routes.allowlist(null)) },
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
                onToggleRule = shieldViewModel::setRuleEnabled,
                onOpenRule = { navController.navigate(Routes.rule(it)) },
                onAddRule = {
                    val scope = SimScope.forSlot(slot) ?: SimScope.SIM2
                    navController.navigate(Routes.newRule(scope.name))
                },
                onOpenAllowlist = { navController.navigate(Routes.allowlist(slot)) },
            )
        }

        composable(
            route = Routes.SHIELD_RULE_NEW,
            arguments = listOf(
                navArgument("scope") {
                    type = NavType.StringType
                    defaultValue = SimScope.SIM2.name
                },
            ),
        ) { entry ->
            val scopeName = entry.arguments?.getString("scope") ?: SimScope.SIM2.name
            val scope = SimScope.entries.firstOrNull { it.name == scopeName } ?: SimScope.SIM2
            LaunchedEffect(scopeName) {
                shieldViewModel.startNewRule(scope, PatternType.EXACT)
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
            val record = vaultViewModel.record(id)
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
                onSimLabelChange = settingsViewModel::setSimLabel,
                onNotifyChange = settingsViewModel::setNotifyOnBlockedCall,
                onOpenVault = { navController.navigate(Routes.VAULT) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenRulePacks = { navController.navigate(Routes.RULE_PACKS) },
                onRequestDialerRole = actions.requestDialerRole,
                onRequestScreeningRole = actions.requestScreeningRole,
                onRequestSmsRole = actions.requestSmsRole,
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
    onBack: () -> Unit,
) {
    val state by messagesViewModel.state.collectAsStateWithLifecycle()
    val conversation by messagesViewModel.conversation.collectAsStateWithLifecycle()

    LaunchedEffect(threadId, address) {
        messagesViewModel.openThread(threadId, address)
    }

    ConversationScreen(
        conversation = conversation,
        sims = state.sims,
        selectedSlot = state.selectedSlot,
        onSelectSim = messagesViewModel::onSelectSim,
        onDraftChange = messagesViewModel::onDraftChange,
        onRecipientChange = messagesViewModel::onRecipientChange,
        onSend = messagesViewModel::send,
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
        onTestNumber = shieldViewModel::onDraftTestNumber,
        onTest = shieldViewModel::testDraft,
        onSave = shieldViewModel::saveDraft,
    )
}
