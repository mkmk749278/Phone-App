package com.dualshield.phone.shield

import android.telecom.PhoneAccountHandle
import android.util.Log
import com.dualshield.phone.core.model.PhoneNumberInfo
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.core.rules.AllowReason
import com.dualshield.phone.core.rules.RuleEngine
import com.dualshield.phone.core.rules.RuleSnapshot
import com.dualshield.phone.core.rules.ShieldDecision
import com.dualshield.phone.data.db.entity.BlockedCallEntity
import com.dualshield.phone.data.repository.RuleRepository
import com.dualshield.phone.data.repository.SimRepository
import com.dualshield.phone.data.repository.VaultRepository
import com.dualshield.phone.data.system.ContactsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** What the screening service should do, plus everything needed to explain it afterwards. */
data class ScreeningOutcome(
    val decision: ShieldDecision,
    val info: PhoneNumberInfo,
    val slotIndex: Int?,
    val simLabel: String,
    val vaultRecorded: Boolean,
)

/**
 * Holds the live rule snapshot and answers screening questions.
 *
 * The snapshot is kept warm in memory by a collector started at process launch, so the
 * common case for [screen] is a handful of hash lookups with no I/O at all. Room is only
 * touched on the call path in the cold-start case, and then under a hard timeout.
 */
class ShieldEngine(
    private val scope: CoroutineScope,
    private val ruleRepository: RuleRepository,
    private val simRepository: SimRepository,
    private val vaultRepository: VaultRepository,
    private val contactsRepository: ContactsRepository,
) {

    private val _snapshot = MutableStateFlow(RuleSnapshot.EMPTY)
    val snapshot: StateFlow<RuleSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var warm: Boolean = false

    /** Starts keeping the snapshot up to date. Called once, from Application.onCreate. */
    fun start() {
        scope.launch {
            ruleRepository.observeSnapshot().collect { next ->
                _snapshot.value = next
                warm = true
            }
        }
    }

    /**
     * Screens one incoming call.
     *
     * Every failure path in here resolves to allow. In particular, if the Vault record
     * cannot be written we deliberately let the call through: a call that disappears with
     * no trace is worse for the user than one unwanted ring.
     */
    fun screen(rawNumber: String?, accountHandle: PhoneAccountHandle?): ScreeningOutcome {
        val info = PhoneNumberNormalizer.normalize(rawNumber)
        val slotIndex = runCatching { simRepository.resolveSlotIndex(accountHandle) }.getOrNull()
        val snapshot = currentSnapshot()
        val simLabel = slotIndex
            ?.let { snapshot.forSlot(it)?.label }
            ?: slotIndex?.let { SimRepository.defaultLabelForSlot(it) }
            ?: "Unknown SIM"

        val decision = RuleEngine.evaluate(snapshot, info, slotIndex)
        if (decision !is ShieldDecision.Block) {
            return ScreeningOutcome(decision, info, slotIndex, simLabel, vaultRecorded = false)
        }

        val recorded = recordBlockedCall(info, decision, slotIndex ?: -1, simLabel)
        if (!recorded) {
            Log.w(TAG, "Vault write failed; allowing the call rather than dropping it silently.")
            return ScreeningOutcome(
                decision = ShieldDecision.Allow(AllowReason.ENGINE_ERROR),
                info = info,
                slotIndex = slotIndex,
                simLabel = simLabel,
                vaultRecorded = false,
            )
        }
        return ScreeningOutcome(decision, info, slotIndex, simLabel, vaultRecorded = true)
    }

    /** Evaluates without any side effects. Backs the rule tester. */
    fun dryRun(rawNumber: String, slotIndex: Int?): Pair<PhoneNumberInfo, ShieldDecision> {
        val info = PhoneNumberNormalizer.normalize(rawNumber)
        return info to RuleEngine.evaluate(currentSnapshot(), info, slotIndex)
    }

    private fun currentSnapshot(): RuleSnapshot {
        if (warm) return _snapshot.value
        // Cold start: the process was created by Telecom for this very call. Build once,
        // under a budget well inside the window CallScreeningService gives us.
        val built = runBlocking {
            withTimeoutOrNull(COLD_START_BUDGET_MS) {
                runCatching { ruleRepository.buildSnapshotNow() }.getOrNull()
            }
        }
        if (built != null) {
            _snapshot.value = built
            warm = true
            return built
        }
        Log.w(TAG, "Rule snapshot unavailable within budget; failing open.")
        return RuleSnapshot.EMPTY
    }

    private fun recordBlockedCall(
        info: PhoneNumberInfo,
        decision: ShieldDecision.Block,
        slotIndex: Int,
        simLabel: String,
    ): Boolean {
        val rule = decision.rule
        val displayName = runCatching {
            contactsRepository.displayNameFor(info.raw.takeIf { it.isNotBlank() })
        }.getOrNull()

        val entity = BlockedCallEntity(
            rawNumber = info.raw,
            normalizedNumber = info.normalized,
            displayName = displayName,
            timestamp = System.currentTimeMillis(),
            simSlot = slotIndex,
            subscriptionId = -1,
            simLabel = simLabel,
            matchedRuleId = rule.id.takeIf { it != 0L },
            matchedRuleStableId = rule.stableId,
            matchedRuleName = rule.name,
            category = rule.category,
            reason = reasonFor(rule.category, rule.name),
        )

        return runBlocking {
            withTimeoutOrNull(VAULT_WRITE_BUDGET_MS) {
                runCatching {
                    vaultRepository.record(entity)
                    if (rule.id != 0L) ruleRepository.recordMatch(rule.id)
                    true
                }.getOrDefault(false)
            }
        } ?: false
    }

    private fun reasonFor(category: RuleCategory, ruleName: String): String =
        "Matched \"$ruleName\" (${category.displayName})"

    private companion object {
        const val TAG = "ShieldEngine"
        const val COLD_START_BUDGET_MS = 2_000L
        const val VAULT_WRITE_BUDGET_MS = 1_500L
    }
}
