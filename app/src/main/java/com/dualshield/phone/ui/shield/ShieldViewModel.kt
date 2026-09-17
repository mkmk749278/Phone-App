package com.dualshield.phone.ui.shield

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dualshield.phone.AppContainer
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.shield.PauseDuration
import com.dualshield.phone.core.shield.ShieldPause
import com.dualshield.phone.ui.components.Formatting
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.core.rules.CompiledRule
import com.dualshield.phone.core.rules.RuleIndex
import com.dualshield.phone.core.rules.ShieldDecision
import com.dualshield.phone.data.db.entity.AllowRuleEntity
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.data.repository.compile
import com.dualshield.phone.data.rulepack.RulePackParser
import com.dualshield.phone.ui.components.SimOption
import com.dualshield.phone.ui.simOptionsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything under the Shield tab: overview, per-SIM rules, the editor and the tester. */
class ShieldViewModel(private val container: AppContainer) : ViewModel() {

    @Immutable
    data class UiState(
        val sims: List<SimOption> = emptyList(),
        val rules: List<CallRuleEntity> = emptyList(),
        val allowRules: List<AllowRuleEntity> = emptyList(),
        val blockedCallCount: Int = 0,
        val blockedMessageCount: Int = 0,
        val pause: ShieldPause? = null,
        val message: String? = null,
    ) {
        val anyProtectionOn: Boolean get() = sims.any { it.protectionEnabled }

        /** True while a pause is in force, whatever its scope. */
        val isPaused: Boolean get() = pause != null

        /** Whether this particular line is currently exempt from Shield. */
        fun isPausedForSlot(slotIndex: Int): Boolean = pause?.covers(slotIndex) == true

        fun sim(slotIndex: Int): SimOption? = sims.firstOrNull { it.slotIndex == slotIndex }

        fun rulesForSlot(slotIndex: Int): List<CallRuleEntity> =
            rules.filter { it.simScope.coversSlot(slotIndex) }

        fun allowRulesForSlot(slotIndex: Int): List<AllowRuleEntity> =
            allowRules.filter { it.simScope.coversSlot(slotIndex) }

        /**
         * What the Blocked numbers screen lists: the user's own entries, newest first.
         *
         * Built-in pack rules are deliberately excluded — they belong under India protection
         * where their provenance and confidence are shown alongside them.
         */
        val userRules: List<CallRuleEntity>
            get() = rules.filterNot { it.builtIn }.sortedByDescending { it.createdAt }

        val blockedCount: Int get() = userRules.count { it.action == RuleAction.BLOCK }
    }

    /** An in-progress rule. Kept separate from the entity so a bad draft never reaches Room. */
    data class RuleDraft(
        val id: Long = 0,
        val name: String = "",
        val patternType: PatternType = PatternType.EXACT,
        val pattern: String = "",
        val scope: SimScope = SimScope.SIM2,
        val action: RuleAction = RuleAction.BLOCK,
        val category: RuleCategory = RuleCategory.USER_BLOCK,
        val description: String = "",
        val enabled: Boolean = true,
        val builtIn: Boolean = false,
        val blocksCalls: Boolean = true,
        val blocksSms: Boolean = true,
        val patternError: String? = null,
        val testNumber: String = "",
        val testOutcome: TestOutcome? = null,
        val saved: Boolean = false,
    ) {
        val isAdvanced: Boolean
            get() = patternType == PatternType.REGEX || patternType == PatternType.CONTAINS
    }

    data class TestOutcome(
        val matched: Boolean,
        val headline: String,
        val detail: String,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _draft = MutableStateFlow(RuleDraft())
    val draft: StateFlow<RuleDraft> = _draft.asStateFlow()

    private val _tester = MutableStateFlow(TesterState())
    val tester: StateFlow<TesterState> = _tester.asStateFlow()

    /**
     * Which SIM the India protection screen is showing.
     *
     * Its own state rather than the rule tester's: sharing one slot between two unrelated
     * screens meant testing a number silently changed which SIM's rules you were looking at.
     */
    private val _indiaSlot = MutableStateFlow<Int?>(null)
    val indiaSlot: StateFlow<Int?> = _indiaSlot.asStateFlow()

    fun onIndiaSlot(slotIndex: Int) {
        _indiaSlot.value = slotIndex
    }

    data class TesterState(
        val number: String = "",
        val slotIndex: Int? = null,
        val outcome: TestOutcome? = null,
    )

    init {
        viewModelScope.launch {
            combine(
                container.simOptionsFlow(),
                container.ruleRepository.observeRules(),
                container.ruleRepository.observeAllowRules(),
            ) { sims, rules, allows -> Triple(sims, rules, allows) }
                .collect { (sims, rules, allows) ->
                    _state.update {
                        it.copy(sims = sims, rules = rules, allowRules = allows)
                    }
                    val firstSlot = sims.firstOrNull()?.slotIndex
                    if (_tester.value.slotIndex == null) {
                        _tester.update { it.copy(slotIndex = firstSlot) }
                    }
                    if (_indiaSlot.value == null) {
                        _indiaSlot.value = sims.firstOrNull { it.protectionEnabled }?.slotIndex
                            ?: firstSlot
                    }
                }
        }
        viewModelScope.launch {
            // The stored pause drops itself once expired, so the screen returns to
            // "Protection ON" on its own without anything having to fire a timer.
            container.settingsRepository.settings.collect { settings ->
                _state.update { it.copy(pause = settings.shieldPause) }
            }
        }
        viewModelScope.launch {
            container.vaultRepository.observeBlockedCallCount().collect { count ->
                _state.update { it.copy(blockedCallCount = count) }
            }
        }
        viewModelScope.launch {
            container.vaultRepository.observeBlockedMessageCount().collect { count ->
                _state.update { it.copy(blockedMessageCount = count) }
            }
        }
    }

    // ------------------------------------------------------------------ pause

    /**
     * Starts a pause.
     *
     * Nothing in the rule set is touched: the rules stay exactly as they are and simply stop
     * being enforced for the chosen scope until the pause lapses or is lifted.
     */
    fun pauseShield(duration: PauseDuration, scope: SimScope) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val pause = duration.toPause(scope, now)
            runCatching { container.settingsRepository.setShieldPause(pause) }
                .onSuccess {
                    _state.update { it.copy(message = pauseStartedMessage(pause, scope)) }
                }
                .onFailure {
                    _state.update { it.copy(message = "Shield couldn't be paused.") }
                }
        }
    }

    /** Lifts the pause early. Protection returns to exactly what it was. */
    fun resumeShield() {
        viewModelScope.launch {
            runCatching { container.settingsRepository.clearShieldPause() }
                .onSuccess { _state.update { it.copy(message = "Shield resumed") } }
                .onFailure {
                    _state.update { it.copy(message = "Shield couldn't be resumed.") }
                }
        }
    }

    private fun pauseStartedMessage(pause: ShieldPause, scope: SimScope): String {
        val where = when (scope) {
            SimScope.BOTH -> "both SIMs"
            SimScope.SIM1 -> _state.value.sim(0)?.display ?: "SIM 1"
            SimScope.SIM2 -> _state.value.sim(1)?.display ?: "SIM 2"
        }
        val until = pause.expiresAtMillis
            ?.let { " until ${Formatting.timeOfDay(it)}" }
            .orEmpty()
        return "Shield paused on $where$until"
    }

    // ------------------------------------------------------------------ SIM state

    fun setProtectionEnabled(slotIndex: Int, enabled: Boolean) {
        viewModelScope.launch {
            container.simRepository.setFilteringEnabled(slotIndex, enabled)
            val label = _state.value.sim(slotIndex)?.display ?: "SIM ${slotIndex + 1}"
            _state.update {
                it.copy(
                    message = if (enabled) {
                        "Protection on for $label"
                    } else {
                        "Protection off for $label"
                    },
                )
            }
        }
    }

    fun setSimLabel(slotIndex: Int, label: String) {
        viewModelScope.launch { container.simRepository.setLabel(slotIndex, label) }
    }

    /**
     * Flips a rule between blocking and allowing, in place.
     *
     * The BLOCK/ALLOW pill used to be decoration — there was no way to change a rule's
     * action short of deleting it and starting again.
     */
    fun toggleRuleAction(rule: CallRuleEntity) {
        viewModelScope.launch {
            val next = if (rule.action == RuleAction.BLOCK) RuleAction.ALLOW else RuleAction.BLOCK
            container.ruleRepository.updateRule(
                rule.copy(
                    action = next,
                    category = if (next == RuleAction.ALLOW) {
                        RuleCategory.USER_ALLOW
                    } else {
                        RuleCategory.USER_BLOCK
                    },
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _state.update {
                it.copy(
                    message = if (next == RuleAction.BLOCK) {
                        "${RuleDisplay.pattern(rule)} is now blocked"
                    } else {
                        "${RuleDisplay.pattern(rule)} is now always allowed"
                    },
                )
            }
        }
    }

    /** Cycles a rule between blocking calls, SMS, or both — MIUI's three-way choice. */
    fun cycleRuleChannels(rule: CallRuleEntity) {
        viewModelScope.launch {
            val (calls, sms) = when {
                rule.blocksCalls && rule.blocksSms -> true to false
                rule.blocksCalls -> false to true
                else -> true to true
            }
            container.ruleRepository.updateRule(
                rule.copy(
                    blocksCalls = calls,
                    blocksSms = sms,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun setAllowContacts(slotIndex: Int, allow: Boolean) {
        viewModelScope.launch {
            container.simRepository.setAllowContacts(slotIndex, allow)
            _state.update {
                it.copy(
                    message = if (allow) {
                        "Saved contacts will always get through"
                    } else {
                        "Contacts are no longer automatically allowed"
                    },
                )
            }
        }
    }

    fun setRuleEnabled(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            container.ruleRepository.setRuleEnabled(ruleId, enabled, System.currentTimeMillis())
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            container.ruleRepository.deleteRule(ruleId)
            _state.update { it.copy(message = "Rule deleted") }
        }
    }

    fun deleteAllowRule(id: Long) {
        viewModelScope.launch {
            container.ruleRepository.deleteAllowRule(id)
            _state.update { it.copy(message = "Removed from the allowlist") }
        }
    }

    fun allowNumber(number: String, displayName: String?, scope: SimScope) {
        viewModelScope.launch {
            runCatching {
                container.ruleRepository.allowNumber(
                    number,
                    displayName,
                    scope,
                    System.currentTimeMillis(),
                )
            }.onSuccess { _state.update { it.copy(message = "Number allowed") } }
                .onFailure { _state.update { it.copy(message = "That number couldn't be allowed.") } }
        }
    }

    fun blockNumber(number: String, displayName: String?, scope: SimScope) {
        viewModelScope.launch {
            runCatching {
                container.ruleRepository.blockNumber(
                    number,
                    displayName,
                    scope,
                    System.currentTimeMillis(),
                )
            }.onSuccess { _state.update { it.copy(message = "Number blocked") } }
                .onFailure { _state.update { it.copy(message = "That number couldn't be blocked.") } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ------------------------------------------------------------------ rule editor

    fun startNewRule(
        scope: SimScope,
        patternType: PatternType = PatternType.EXACT,
        pattern: String = "",
        name: String = "",
    ) {
        _draft.value = RuleDraft(
            patternType = patternType,
            scope = scope,
            pattern = pattern,
            name = name,
        )
    }

    fun loadRule(ruleId: Long) {
        if (ruleId <= 0L) return
        viewModelScope.launch {
            val rule = container.ruleRepository.rule(ruleId) ?: return@launch
            _draft.value = RuleDraft(
                id = rule.id,
                name = rule.name,
                patternType = rule.patternType,
                pattern = rule.pattern,
                scope = rule.simScope,
                action = rule.action,
                category = rule.category,
                description = rule.description,
                enabled = rule.enabled,
                builtIn = rule.builtIn,
                blocksCalls = rule.blocksCalls,
                blocksSms = rule.blocksSms,
            )
        }
    }

    fun onDraftName(value: String) = _draft.update { it.copy(name = value, saved = false) }

    fun onDraftPattern(value: String) = _draft.update {
        it.copy(pattern = value, patternError = null, testOutcome = null, saved = false)
    }

    fun onDraftPatternType(value: PatternType) = _draft.update {
        it.copy(patternType = value, patternError = null, testOutcome = null, saved = false)
    }

    fun onDraftScope(value: SimScope) = _draft.update { it.copy(scope = value, saved = false) }

    fun onDraftAction(value: RuleAction) = _draft.update {
        it.copy(
            action = value,
            category = if (value == RuleAction.ALLOW) {
                RuleCategory.USER_ALLOW
            } else {
                RuleCategory.USER_BLOCK
            },
            saved = false,
        )
    }

    fun onDraftDescription(value: String) = _draft.update { it.copy(description = value) }

    fun onDraftEnabled(value: Boolean) = _draft.update { it.copy(enabled = value) }

    fun onDraftBlocksCalls(value: Boolean) = _draft.update { it.copy(blocksCalls = value) }

    fun onDraftBlocksSms(value: Boolean) = _draft.update { it.copy(blocksSms = value) }

    fun onDraftTestNumber(value: String) = _draft.update {
        it.copy(testNumber = value, testOutcome = null)
    }

    /**
     * Tests the draft rule against a number without writing anything.
     *
     * This exists because a mistyped regex is the easiest way for a user to silently lose
     * calls, and the only honest defence is to let them see the match before they save.
     */
    fun testDraft() {
        val current = _draft.value
        val patternError = validatePattern(current)
        if (patternError != null) {
            _draft.update { it.copy(patternError = patternError, testOutcome = null) }
            return
        }
        val compiled = compileDraft(current)
        if (compiled == null) {
            _draft.update {
                it.copy(patternError = "This rule can't be tested yet. Check the pattern.")
            }
            return
        }
        val info = PhoneNumberNormalizer.normalize(current.testNumber)
        val matched = RuleIndex.build(listOf(compiled)).match(info) != null
        val scopeLabel = scopeDisplay(current.scope)
        _draft.update {
            it.copy(
                patternError = null,
                testOutcome = TestOutcome(
                    matched = matched,
                    headline = if (matched) "MATCH" else "NO MATCH",
                    detail = if (matched) {
                        if (current.action == RuleAction.ALLOW) {
                            "This number would always be allowed on $scopeLabel."
                        } else {
                            "This number would be blocked on $scopeLabel."
                        }
                    } else {
                        "This rule would not affect this number."
                    },
                ),
            )
        }
    }

    fun saveDraft() {
        val current = _draft.value
        if (current.name.isBlank()) {
            _draft.update { it.copy(patternError = "Give this rule a name so you can find it later.") }
            return
        }
        val patternError = validatePattern(current)
        if (patternError != null) {
            _draft.update { it.copy(patternError = patternError) }
            return
        }
        if (current.action == RuleAction.BLOCK && !current.blocksCalls && !current.blocksSms) {
            _draft.update {
                it.copy(patternError = "Choose whether this blocks calls, messages, or both.")
            }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = if (current.id > 0) container.ruleRepository.rule(current.id) else null
            if (existing != null) {
                container.ruleRepository.updateRule(
                    existing.copy(
                        name = current.name.trim(),
                        pattern = current.pattern.trim(),
                        patternType = current.patternType,
                        action = current.action,
                        simScope = current.scope,
                        enabled = current.enabled,
                        description = current.description.trim(),
                        blocksCalls = current.blocksCalls,
                        blocksSms = current.blocksSms,
                        updatedAt = now,
                    ),
                )
            } else {
                container.ruleRepository.insertRule(
                    CallRuleEntity(
                        stableId = "user-${current.patternType.name.lowercase()}-" +
                            "${current.pattern.trim()}-${current.scope.name}-$now",
                        name = current.name.trim(),
                        category = current.category,
                        pattern = current.pattern.trim(),
                        patternType = current.patternType,
                        action = current.action,
                        simScope = current.scope,
                        enabled = current.enabled,
                        priority = if (current.action == RuleAction.ALLOW) 20 else 50,
                        confidence = Confidence.HIGH,
                        provenance = Provenance.USER_DEFINED,
                        description = current.description.trim(),
                        builtIn = false,
                        blocksCalls = current.blocksCalls,
                        blocksSms = current.blocksSms,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
            _draft.update { it.copy(saved = true, patternError = null) }
            _state.update { it.copy(message = "Rule saved") }
        }
    }

    // ------------------------------------------------------------------ rule tester

    fun onTesterNumber(value: String) = _tester.update { it.copy(number = value, outcome = null) }

    fun onTesterSlot(slotIndex: Int) = _tester.update { it.copy(slotIndex = slotIndex, outcome = null) }

    /**
     * Runs the whole engine, exactly as the screening service would, with no side effects.
     *
     * Using the real engine rather than a simplified copy is the point: what the tester says
     * is what will actually happen to the next call.
     */
    fun runTester() {
        val current = _tester.value
        if (current.number.isBlank()) {
            _tester.update {
                it.copy(outcome = TestOutcome(false, "Enter a number", "Type a number to test."))
            }
            return
        }
        val (info, decision) = container.shieldEngine.dryRun(current.number, current.slotIndex)
        val simLabel = current.slotIndex
            ?.let { slot -> _state.value.sim(slot)?.display }
            ?: "an unidentified SIM"

        val outcome = when (decision) {
            is ShieldDecision.Block -> TestOutcome(
                matched = true,
                headline = "WOULD BE BLOCKED",
                detail = "${Formatted.number(info.normalized)} matches " +
                    "\"${decision.rule.name}\" on $simLabel.",
            )
            is ShieldDecision.Allow -> TestOutcome(
                matched = false,
                headline = "WOULD RING",
                detail = decision.rule
                    ?.let { "Allowed by \"${it.name}\" on $simLabel." }
                    ?: "${decision.reason.explanation} on $simLabel.",
            )
        }
        _tester.update { it.copy(outcome = outcome) }
    }

    private object Formatted {
        fun number(value: String) = value.ifBlank { "This caller" }
    }

    // ------------------------------------------------------------------ helpers

    private fun validatePattern(draft: RuleDraft): String? = when (draft.patternType) {
        PatternType.REGEX -> RulePackParser.validateRegex(draft.pattern.trim())
        PatternType.SPECIAL -> null
        PatternType.REPEATED_CALL -> "Repeated-caller rules aren't available yet."
        else -> if (draft.pattern.none { it.isDigit() }) {
            "Enter at least one digit to match."
        } else {
            null
        }
    }

    private fun compileDraft(draft: RuleDraft): CompiledRule? = CompiledRule.from(
        id = draft.id,
        stableId = "draft",
        name = draft.name.ifBlank { "Untitled rule" },
        category = draft.category,
        pattern = draft.pattern.trim(),
        patternType = draft.patternType,
        action = draft.action,
        simScope = draft.scope,
        confidence = Confidence.HIGH,
        provenance = Provenance.USER_DEFINED,
        priority = 50,
        builtIn = false,
        description = draft.description,
    )

    private fun scopeDisplay(scope: SimScope): String = when (scope) {
        SimScope.BOTH -> "both SIMs"
        SimScope.SIM1 -> _state.value.sim(0)?.display ?: "SIM 1"
        SimScope.SIM2 -> _state.value.sim(1)?.display ?: "SIM 2"
    }
}

/** Convenience used by the Shield screens when turning a stored rule into a compiled one. */
fun CallRuleEntity.toCompiledOrNull(): CompiledRule? = compile()
