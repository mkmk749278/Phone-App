package com.dualshield.phone.shield

import android.telecom.PhoneAccountHandle
import android.util.Log
import com.dualshield.phone.core.model.PhoneNumberInfo
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.rules.CompiledRule
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.core.rules.AllowReason
import com.dualshield.phone.core.rules.RuleEngine
import com.dualshield.phone.core.rules.RuleSnapshot
import com.dualshield.phone.core.rules.ShieldChannel
import com.dualshield.phone.core.rules.ShieldDecision
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.data.db.entity.BlockedMessageEntity
import com.dualshield.phone.data.repository.RuleRepository
import com.dualshield.phone.data.repository.SimRepository
import com.dualshield.phone.data.repository.VaultRepository
import com.dualshield.phone.data.system.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.dualshield.phone.core.number.PhoneNumberFormatter
import com.dualshield.phone.core.shield.CallerActivity
import com.dualshield.phone.core.shield.RecoveryEvaluator
import com.dualshield.phone.core.shield.RecoveryMode
import com.dualshield.phone.core.shield.RecoverySettings
import com.dualshield.phone.core.shield.ShieldPause
import com.dualshield.phone.data.repository.SettingsRepository
import kotlinx.coroutines.launch

/**
 * What the screening service should do, plus everything needed to explain it afterwards.
 *
 * Deliberately carries no record of whether the event was persisted: persistence happens
 * *after* this is handed to Telecom, so at decision time the answer does not exist yet.
 */
data class ScreeningOutcome(
    val decision: ShieldDecision,
    val info: PhoneNumberInfo,
    val slotIndex: Int?,
    val simLabel: String,
)

/**
 * Holds the live rule snapshot and answers screening questions.
 *
 * Android gives a `CallScreeningService` a few seconds to answer, and the user hears the
 * delay, so the decision path here does no I/O at all: no Room, no content provider, no
 * waiting on another coroutine, no `runBlocking`. Everything it consults — the rule
 * snapshot, the set of saved-contact keys — is pre-warmed in memory by collectors started
 * at process launch.
 *
 * Two rules govern the order of operations, and they are not interchangeable:
 *
 *  1. **Decide, respond, then persist.** The blocked-call record is written after Telecom
 *     has its answer. A storage failure must never turn a block into a ring: the earlier
 *     design wrote the record first and allowed the call if the write failed, which handed
 *     the database a veto over the user's own rules.
 *  2. **Fail open on uncertainty.** An unresolved SIM, a malformed number, a snapshot that
 *     has not loaded yet, or any exception at all resolves to allow. Blocking is only ever
 *     the result of a positive match.
 */
class ShieldEngine(
    private val scope: CoroutineScope,
    private val ruleRepository: RuleRepository,
    private val simRepository: SimRepository,
    private val vaultRepository: VaultRepository,
    private val contactsRepository: ContactsRepository,
    private val settingsRepository: SettingsRepository,
    private val frequencyStore: CallFrequencyStore,
) {

    private val _snapshot = MutableStateFlow(RuleSnapshot.EMPTY)
    val snapshot: StateFlow<RuleSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var warm: Boolean = false

    /**
     * Match keys of every saved contact, kept in memory.
     *
     * "Is this a saved contact?" feeds the rule engine, because a SIM can be set to let
     * contacts through whatever else matches. Answering it used to mean a ContactsProvider
     * query inside the screening callback — a cross-process call on the one path that must
     * not do I/O. The keys are loaded once and refreshed in the background instead.
     */
    @Volatile
    private var contactKeys: Set<String> = emptySet()

    /**
     * The pause in force, or null — the single authoritative copy.
     *
     * Kept in memory because the screening path must be able to ask "is Shield paused for
     * this line?" without touching storage. Whether it has *lapsed* is decided by comparing
     * timestamps when the question is asked, not by a timer, so there is no window in which
     * a call is screened against a pause that has already ended.
     */
    @Volatile
    private var pause: ShieldPause? = null

    /** Behavioural protection settings per slot, mirrored in memory for the same reason. */
    @Volatile
    private var recoveryBySlot: Map<Int, RecoverySettings> = emptyMap()

    /** The pause in force at this instant, or null. Safe to call from anywhere. */
    fun activePause(now: Long = System.currentTimeMillis()): ShieldPause? =
        pause?.takeIf { it.isActiveAt(now) }

    /** Starts keeping the snapshot and contact keys up to date. Called from Application. */
    fun start() {
        scope.launch {
            ruleRepository.observeSnapshot().collect { next ->
                _snapshot.value = next
                warm = true
            }
        }
        scope.launch {
            settingsRepository.settings.collect { settings ->
                pause = settings.shieldPause
                recoveryBySlot = settings.recoveryBySlot
            }
        }
        frequencyStore.start()
        refreshContactKeys()
    }

    /** Reloads the saved-contact keys. Safe to call whenever the address book may have changed. */
    fun refreshContactKeys() {
        scope.launch {
            runCatching {
                contactsRepository.loadContacts().flatMap { contact ->
                    contact.phoneNumbers.mapNotNull { it.matchKey.takeIf(String::isNotEmpty) }
                }.toSet()
            }.onSuccess { keys ->
                contactKeys = keys
            }.onFailure {
                Log.w(TAG, "Contact keys could not be loaded; contact bypass is off until they are.", it)
            }
        }
    }

    /**
     * Decides what to do about one incoming call, and does nothing else.
     *
     * This is the whole of the critical path. It touches only pre-warmed memory, so it
     * returns in well under a millisecond in the common case, and every failure inside it
     * resolves to allow.
     *
     * The caller must respond to Telecom with this outcome **first**, and only then call
     * [recordBlockedCall] to persist it.
     */
    fun screen(rawNumber: String?, accountHandle: PhoneAccountHandle?): ScreeningOutcome {
        val info = PhoneNumberNormalizer.normalize(rawNumber)
        val slotIndex = runCatching { simRepository.resolveSlotIndex(accountHandle) }.getOrNull()
        val snapshot = currentSnapshot()
        val simLabel = slotIndex?.let { snapshot.forSlot(it)?.label }?.takeIf { it.isNotBlank() }
            ?: slotIndex?.let { "SIM ${it + 1}" }
            ?: "Unknown SIM"

        // The pause is checked before anything else. While it is in force for this line,
        // Shield enforces nothing at all — no user rules, no India blocklist, no heuristics —
        // which is the whole point of a call window.
        if (isPausedFor(slotIndex)) {
            return ScreeningOutcome(
                decision = ShieldDecision.Allow(AllowReason.SHIELD_PAUSED),
                info = info,
                slotIndex = slotIndex,
                simLabel = simLabel,
            )
        }

        val isContact = isSavedContact(info)
        val decision = RuleEngine.evaluate(
            snapshot = snapshot,
            info = info,
            slotIndex = slotIndex,
            channel = ShieldChannel.CALL,
            isContact = isContact,
        )

        // Behavioural signals are consulted only once the rules have had their say, and only
        // when they did not reach a verdict. An explicit rule is a decision the user made;
        // a heuristic is a guess this device is making on their behalf, and the two do not
        // get equal standing.
        val finalDecision = if (decision is ShieldDecision.Allow &&
            decision.reason == AllowReason.NO_MATCH
        ) {
            applyRecovery(info, slotIndex, isContact, decision)
        } else {
            decision
        }

        return ScreeningOutcome(finalDecision, info, slotIndex, simLabel)
    }

    /**
     * Persists a blocked call, after the response has already gone to Telecom.
     *
     * Fire-and-forget by design. If this fails the call still stayed blocked, which is the
     * outcome the user asked for; what is lost is the audit record, and that is logged. The
     * inverse — letting a call through because a write failed — is the failure this ordering
     * exists to prevent.
     */
    fun recordBlockedCall(outcome: ScreeningOutcome) {
        val decision = outcome.decision as? ShieldDecision.Block ?: return
        scope.launch {
            runCatching {
                val rule = decision.rule
                // Resolved out here rather than in the screening callback: it can reach the
                // telephony service, which is exactly the kind of call the critical path
                // must not make.
                val subscriptionId = outcome.slotIndex
                    ?.let { simRepository.subscriptionIdForSlot(it) }
                    ?.takeIf { it >= 0 }
                    ?: -1
                vaultRepository.record(
                    BlockedCallEntity(
                        rawNumber = outcome.info.raw,
                        normalizedNumber = outcome.info.normalized,
                        displayName = contactsRepository.displayNameFor(outcome.info.raw),
                        timestamp = System.currentTimeMillis(),
                        simSlot = outcome.slotIndex ?: -1,
                        subscriptionId = subscriptionId,
                        simLabel = outcome.simLabel,
                        matchedRuleId = rule.id.takeIf { it != 0L },
                        matchedRuleStableId = rule.stableId,
                        matchedRuleName = rule.name,
                        category = rule.category,
                        reason = reasonFor(rule.category, rule.name),
                    ),
                )
                if (rule.id != 0L) ruleRepository.recordMatch(rule.id)
            }.onFailure {
                Log.w(TAG, "Blocked-call record could not be saved; the call stayed blocked.", it)
            }
        }
    }

    /**
     * Screens an incoming message.
     *
     * A blocked message is recorded and kept out of the user's attention, but the message
     * itself is still written to the system inbox by the caller — Shield hides messages, it
     * never destroys them.
     *
     * Same ordering as calls: the decision is made from memory and returned immediately, and
     * the record is written afterwards.
     *
     * @return true when the message should be treated as filtered.
     */
    fun screenMessage(
        rawNumber: String?,
        body: String,
        timestamp: Long,
        subscriptionId: Int,
    ): Boolean {
        val info = PhoneNumberNormalizer.normalize(rawNumber)
        val slotIndex = runCatching {
            simRepository.slotForSubscriptionId(subscriptionId)
        }.getOrNull()
        val snapshot = currentSnapshot()
        if (isPausedFor(slotIndex)) return false

        val decision = RuleEngine.evaluate(
            snapshot = snapshot,
            info = info,
            slotIndex = slotIndex,
            channel = ShieldChannel.SMS,
            isContact = isSavedContact(info),
        )
        val block = decision as? ShieldDecision.Block ?: return false

        val simLabel = slotIndex?.let { snapshot.forSlot(it)?.label }?.takeIf { it.isNotBlank() }
            ?: slotIndex?.let { "SIM ${it + 1}" }
            ?: "Unknown SIM"

        scope.launch {
            runCatching {
                vaultRepository.recordMessage(
                    BlockedMessageEntity(
                        rawNumber = info.raw,
                        normalizedNumber = info.normalized,
                        displayName = contactsRepository.displayNameFor(info.raw),
                        body = body,
                        timestamp = timestamp,
                        simSlot = slotIndex ?: -1,
                        simLabel = simLabel,
                        matchedRuleName = block.rule.name,
                        reason = reasonFor(block.rule.category, block.rule.name),
                    ),
                )
                if (block.rule.id != 0L) ruleRepository.recordMatch(block.rule.id)
            }.onFailure {
                Log.w(TAG, "Blocked-message record could not be saved; it stayed filtered.", it)
            }
        }
        return true
    }

    /** Evaluates without any side effects. Backs the rule tester. */
    fun dryRun(
        rawNumber: String,
        slotIndex: Int?,
        channel: ShieldChannel = ShieldChannel.CALL,
    ): Pair<PhoneNumberInfo, ShieldDecision> {
        val info = PhoneNumberNormalizer.normalize(rawNumber)
        return info to RuleEngine.evaluate(
            snapshot = currentSnapshot(),
            info = info,
            slotIndex = slotIndex,
            channel = channel,
            isContact = isSavedContact(info),
        )
    }

    /**
     * Records the attempt and asks whether local behaviour makes this caller suspicious.
     *
     * The attempt is recorded even when protection is set to Normal, so that turning it on
     * later has history to work with rather than starting blind. Recording is an in-memory
     * map update; the save to disk happens in the background.
     */
    private fun applyRecovery(
        info: PhoneNumberInfo,
        slotIndex: Int?,
        isContact: Boolean,
        fallback: ShieldDecision,
    ): ShieldDecision = runCatching {
        val key = PhoneNumberFormatter.matchKeyOf(info)
        if (key.isEmpty()) return fallback

        val now = System.currentTimeMillis()
        val activity = frequencyStore.recordAttempt(key, now)
        val settings = slotIndex?.let { recoveryBySlot[it] } ?: RecoverySettings.DEFAULT
        if (!settings.isActive) return fallback

        val result = RecoveryEvaluator.evaluate(activity, settings, isContact, now)
        if (!result.suspicious) return fallback

        val labels = result.signals.map { it.label }
        when (settings.mode) {
            RecoveryMode.SCREEN -> ShieldDecision.Screen(labels)
            RecoveryMode.BLOCK ->
                // Blocking needs a rule to cite, so the observation becomes one: an exact
                // match on this number, named after what was actually seen. That way the
                // blocked-call record says "Repeated caller · 4 days this week" rather than
                // pointing at a rule the user never wrote.
                heuristicRule(info, result.summary)
                    ?.let { ShieldDecision.Block(it) }
                    ?: ShieldDecision.Screen(labels)
            RecoveryMode.NORMAL -> fallback
        }
    }.getOrElse {
        Log.w(TAG, "Behavioural evaluation failed; allowing the call.", it)
        fallback
    }

    /**
     * A rule standing in for a behavioural verdict, so a heuristic block can be recorded and
     * explained like any other.
     *
     * Marked as a heuristic with low confidence and local provenance, because that is what
     * it is: an inference from this device's own observations, not an official
     * classification of the number.
     */
    private fun heuristicRule(info: PhoneNumberInfo, summary: String): CompiledRule? =
        CompiledRule.from(
            id = 0L,
            stableId = "heuristic-${info.normalized}",
            name = summary.ifBlank { "Suspicious calling pattern" },
            category = RuleCategory.BPO_COLLECTION_HEURISTIC,
            pattern = info.normalized,
            patternType = PatternType.EXACT,
            action = RuleAction.BLOCK,
            simScope = SimScope.BOTH,
            confidence = Confidence.LOW,
            provenance = Provenance.ON_DEVICE_OBSERVATION,
            priority = 900,
            builtIn = false,
            description = "Blocked by behavioural protection, from calls seen on this device",
        )

    /**
     * Whether Shield is paused for this line right now.
     *
     * A single read of the volatile field, then two comparisons. Nothing here can block, and
     * a fault resolves to "not paused", which leaves protection on rather than silently off.
     */
    private fun isPausedFor(slotIndex: Int?): Boolean =
        runCatching {
            pause?.suspends(slotIndex, System.currentTimeMillis()) == true
        }.getOrDefault(false)

    /**
     * Whether this caller is in the address book, answered from memory.
     *
     * Returns false when the keys have not loaded yet. That is the safe direction: the
     * contact bypass is a reason to *allow*, so not knowing means the ordinary rules apply,
     * never that someone is blocked who should not have been.
     */
    private fun isSavedContact(info: PhoneNumberInfo): Boolean {
        if (!info.hasDigits) return false
        val key = PhoneNumberFormatter.matchKeyOf(info)
        return key.isNotEmpty() && key in contactKeys
    }

    /**
     * The rule snapshot, or an empty one.
     *
     * Never waits. On a cold start — the process created by Telecom for this very call — the
     * snapshot may not have arrived yet, and the honest answer is to allow the call and warm
     * up in the background rather than hold the ring while Room opens. The cost is that a
     * call arriving in the first moments of a cold start is not filtered; the alternative is
     * missing Android's deadline, which risks the call being mishandled entirely.
     */
    private fun currentSnapshot(): RuleSnapshot {
        val current = _snapshot.value
        if (warm) return current
        warmInBackground()
        return current
    }

    private fun warmInBackground() {
        scope.launch {
            runCatching { ruleRepository.buildSnapshotNow() }
                .onSuccess {
                    _snapshot.value = it
                    warm = true
                }
                .onFailure { Log.w(TAG, "Rule snapshot could not be built.", it) }
        }
    }

    private fun reasonFor(category: RuleCategory, ruleName: String): String =
        "Matched \"$ruleName\" (${category.displayName})"

    private companion object {
        const val TAG = "ShieldEngine"
    }
}
