package com.dualshield.phone.data.repository

import android.content.Context
import com.dualshield.phone.core.model.Confidence
import com.dualshield.phone.core.model.PatternType
import com.dualshield.phone.core.model.Provenance
import com.dualshield.phone.core.model.RuleAction
import com.dualshield.phone.core.model.RuleCategory
import com.dualshield.phone.core.model.SimScope
import com.dualshield.phone.core.number.PhoneNumberNormalizer
import com.dualshield.phone.core.rules.CompiledRule
import com.dualshield.phone.core.rules.RuleSnapshot
import com.dualshield.phone.data.db.dao.AllowRuleDao
import com.dualshield.phone.data.db.dao.CallRuleDao
import com.dualshield.phone.data.db.dao.RulePackMetadataDao
import com.dualshield.phone.data.db.dao.SimProfileDao
import com.dualshield.phone.data.db.entity.AllowRuleEntity
import com.dualshield.phone.data.db.entity.CallRuleEntity
import com.dualshield.phone.data.db.entity.RulePackMetadataEntity
import com.dualshield.phone.data.db.entity.SimProfileEntity
import com.dualshield.phone.data.rulepack.RulePackDto
import com.dualshield.phone.data.rulepack.RulePackParser
import com.dualshield.phone.data.rulepack.RulePackResult
import com.dualshield.phone.data.rulepack.RulePackRuleDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** Everything that reads or writes filtering rules. */
class RuleRepository(
    private val context: Context,
    private val ruleDao: CallRuleDao,
    private val allowDao: AllowRuleDao,
    private val simDao: SimProfileDao,
    private val packDao: RulePackMetadataDao,
) {

    fun observeRules(): Flow<List<CallRuleEntity>> = ruleDao.observeAll()

    fun observeAllowRules(): Flow<List<AllowRuleEntity>> = allowDao.observeAll()

    fun observeRule(id: Long): Flow<CallRuleEntity?> = ruleDao.observeById(id)

    fun observePacks(): Flow<List<RulePackMetadataEntity>> = packDao.observeAll()

    fun observeEnabledRuleCount(scope: SimScope): Flow<Int> =
        ruleDao.observeEnabledCountForScope(scope.name)

    fun observeAllowCount(scope: SimScope): Flow<Int> = allowDao.observeCountForScope(scope.name)

    /**
     * The snapshot the call firewall reads.
     *
     * Combining here (rather than querying inside the screening service) is what keeps Room
     * off the latency-critical path entirely.
     */
    fun observeSnapshot(): Flow<RuleSnapshot> =
        combine(
            simDao.observeAll(),
            ruleDao.observeEnabled(),
            allowDao.observeAll(),
        ) { profiles, rules, allows ->
            buildSnapshot(profiles, rules, allows)
        }

    suspend fun buildSnapshotNow(): RuleSnapshot =
        buildSnapshot(simDao.getAll(), ruleDao.getEnabled(), allowDao.getAll())

    private fun buildSnapshot(
        profiles: List<SimProfileEntity>,
        rules: List<CallRuleEntity>,
        allows: List<AllowRuleEntity>,
    ): RuleSnapshot {
        val compiled = rules.mapNotNull { it.compile() }
        val perSlot = profiles.associate { profile ->
            val allowNumbers = allows
                .filter { it.simScope.coversSlot(profile.slotIndex) }
                .map { it.normalizedNumber }
                .toSet()
            profile.slotIndex to RuleSnapshot.buildSimRuleSet(
                slotIndex = profile.slotIndex,
                label = profile.label,
                filteringEnabled = profile.filteringEnabled,
                allowNumbers = allowNumbers,
                rules = compiled,
                allowContacts = profile.allowContacts,
            )
        }
        return RuleSnapshot(perSlot = perSlot, revision = System.currentTimeMillis())
    }

    // ---------------------------------------------------------------- rule CRUD

    suspend fun rule(id: Long): CallRuleEntity? = ruleDao.getById(id)

    suspend fun insertRule(rule: CallRuleEntity): Long = ruleDao.insert(rule)

    suspend fun updateRule(rule: CallRuleEntity) = ruleDao.update(rule)

    suspend fun deleteRule(id: Long) = ruleDao.deleteById(id)

    suspend fun setRuleEnabled(id: Long, enabled: Boolean, now: Long) =
        ruleDao.setEnabled(id, enabled, now)

    suspend fun recordMatch(ruleId: Long) = ruleDao.incrementMatchCount(ruleId)

    /**
     * Blocks a number from anywhere in the app (call details, Vault, contact sheet).
     *
     * Any existing allow entry for the same number and scope is removed first, otherwise
     * the allowlist would keep winning and the user's action would appear to do nothing.
     */
    suspend fun blockNumber(
        rawNumber: String,
        displayName: String?,
        scope: SimScope,
        now: Long,
        blocksCalls: Boolean = true,
        blocksSms: Boolean = true,
    ): Long {
        val normalized = PhoneNumberNormalizer.normalizedOrEmpty(rawNumber)
        require(normalized.isNotEmpty()) { "Cannot block an empty number" }
        allowDao.findByNumber(normalized)
            .filter { it.simScope == scope || it.simScope == SimScope.BOTH }
            .forEach { allowDao.delete(it) }

        val stableId = "user-block-$normalized-${scope.name}"
        ruleDao.getByStableId(stableId)?.let { existing ->
            ruleDao.update(
                existing.copy(
                    enabled = true,
                    blocksCalls = blocksCalls,
                    blocksSms = blocksSms,
                    updatedAt = now,
                ),
            )
            return existing.id
        }
        return ruleDao.insert(
            CallRuleEntity(
                stableId = stableId,
                name = displayName?.takeIf { it.isNotBlank() } ?: rawNumber,
                category = RuleCategory.USER_BLOCK,
                pattern = normalized,
                patternType = PatternType.EXACT,
                action = RuleAction.BLOCK,
                simScope = scope,
                enabled = true,
                priority = 50,
                confidence = Confidence.HIGH,
                provenance = Provenance.USER_DEFINED,
                description = "Blocked from the app",
                builtIn = false,
                blocksCalls = blocksCalls,
                blocksSms = blocksSms,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /**
     * The user block rules covering [rawNumber], across every SIM scope.
     *
     * Used to answer "is this blocked?" for a single number without walking the whole rule
     * set, and to undo exactly what [blockNumber] created.
     */
    suspend fun userBlockRulesFor(rawNumber: String): List<CallRuleEntity> {
        val normalized = PhoneNumberNormalizer.normalizedOrEmpty(rawNumber)
        if (normalized.isEmpty()) return emptyList()
        return SimScope.entries.mapNotNull { scope ->
            ruleDao.getByStableId("user-block-$normalized-${scope.name}")
        }.filter { it.enabled }
    }

    /**
     * Removes the user's own block on [rawNumber].
     *
     * Only rules this app created from a Block action are touched: a rule from the India
     * pack or one the user wrote by hand is left alone, because deleting someone's carefully
     * written rule as a side effect of an Unblock tap would be a nasty surprise. When a
     * blocked number is still blocked afterwards, a broader rule is the reason, and the
     * Shield screens are where that is managed.
     *
     * @return how many rules were removed.
     */
    suspend fun unblockNumber(rawNumber: String): Int {
        val rules = userBlockRulesFor(rawNumber)
        rules.forEach { ruleDao.delete(it) }
        return rules.size
    }

    // ---------------------------------------------------------------- allowlist

    /**
     * Adds an explicit allow entry, and disables any user block rule for the same number so
     * that "Allow this number" is genuinely one tap and genuinely reversible.
     */
    suspend fun allowNumber(
        rawNumber: String,
        displayName: String?,
        scope: SimScope,
        now: Long,
    ): Long {
        val normalized = PhoneNumberNormalizer.normalizedOrEmpty(rawNumber)
        require(normalized.isNotEmpty()) { "Cannot allow an empty number" }

        ruleDao.getByStableId("user-block-$normalized-${scope.name}")
            ?.let { ruleDao.setEnabled(it.id, false, now) }
        if (scope == SimScope.BOTH) {
            SimScope.entries.forEach { other ->
                ruleDao.getByStableId("user-block-$normalized-${other.name}")
                    ?.let { ruleDao.setEnabled(it.id, false, now) }
            }
        }

        return allowDao.insert(
            AllowRuleEntity(
                normalizedNumber = normalized,
                displayName = displayName?.takeIf { it.isNotBlank() },
                simScope = scope,
                createdAt = now,
            ),
        )
    }

    suspend fun deleteAllowRule(id: Long) = allowDao.deleteById(id)

    // ---------------------------------------------------------------- rule packs

    /**
     * Installs the bundled India pack on first run, and refreshes its wording on upgrade.
     *
     * A refresh never re-enables a rule the user turned off, and never changes the SIM a
     * rule applies to.
     */
    suspend fun seedBundledPackIfNeeded(now: Long): RulePackResult {
        val raw = runCatching {
            context.assets.open(BUNDLED_PACK_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse { return RulePackResult.Failure("The built-in rule pack could not be read.") }

        return when (val parsed = RulePackParser.parse(raw, now)) {
            is RulePackResult.Failure -> parsed
            is RulePackResult.Success -> {
                val installed = packDao.get(parsed.pack.packId)
                if (installed != null && installed.packVersion >= parsed.pack.packVersion) {
                    return parsed
                }
                applyPack(parsed, now)
                parsed
            }
        }
    }

    suspend fun importPack(raw: String, now: Long): RulePackResult {
        val parsed = RulePackParser.parse(raw, now)
        if (parsed is RulePackResult.Success) applyPack(parsed, now)
        return parsed
    }

    private suspend fun applyPack(parsed: RulePackResult.Success, now: Long) {
        for (rule in parsed.rules) {
            val existing = ruleDao.getByStableId(rule.stableId)
            if (existing == null) {
                ruleDao.insert(rule)
            } else if (existing.builtIn) {
                ruleDao.refreshBuiltIn(
                    stableId = rule.stableId,
                    name = rule.name,
                    category = rule.category.name,
                    pattern = rule.pattern,
                    patternType = rule.patternType.name,
                    action = rule.action.name,
                    confidence = rule.confidence.name,
                    provenance = rule.provenance.name,
                    description = rule.description,
                    priority = rule.priority,
                    packId = rule.packId,
                    updatedAt = now,
                )
            }
        }
        packDao.upsert(
            RulePackMetadataEntity(
                packId = parsed.pack.packId,
                schemaVersion = parsed.pack.schemaVersion,
                packVersion = parsed.pack.packVersion,
                country = parsed.pack.country,
                source = parsed.pack.source,
                ruleCount = parsed.rules.size,
                installedAt = now,
            ),
        )
    }

    /** Serialises the user's own rules so they can be backed up or moved to a new device. */
    suspend fun exportUserRules(): String {
        val userRules = ruleDao.getAll().filterNot { it.builtIn }
        val dto = RulePackDto(
            schemaVersion = RulePackParser.SUPPORTED_SCHEMA_VERSION,
            packId = "user-export",
            packVersion = 1,
            country = "IN",
            source = "Exported from DualShieldPhone on this device",
            notes = "User-authored rules. Import on another device to restore them.",
            rules = userRules.map { rule ->
                RulePackRuleDto(
                    stableId = rule.stableId,
                    name = rule.name,
                    category = rule.category.name,
                    pattern = rule.pattern,
                    patternType = rule.patternType.name,
                    action = rule.action.name,
                    simScope = rule.simScope.name,
                    enabled = rule.enabled,
                    priority = rule.priority,
                    confidence = rule.confidence.name,
                    provenance = rule.provenance.name,
                    description = rule.description,
                    blocksCalls = rule.blocksCalls,
                    blocksSms = rule.blocksSms,
                )
            },
        )
        return RulePackParser.encode(dto)
    }

    companion object {
        private const val BUNDLED_PACK_ASSET = "rules/india_rules.json"
    }
}

/** Turns a stored row into the pre-indexed form the engine wants. */
fun CallRuleEntity.compile(): CompiledRule? = CompiledRule.from(
    id = id,
    stableId = stableId,
    name = name,
    category = category,
    pattern = pattern,
    patternType = patternType,
    action = action,
    simScope = simScope,
    confidence = confidence,
    provenance = provenance,
    priority = priority,
    builtIn = builtIn,
    description = description,
    blocksCalls = blocksCalls,
    blocksSms = blocksSms,
)
