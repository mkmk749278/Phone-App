package com.dualshield.phone.data.repository

import com.dualshield.phone.core.sim.SimInfo
import com.dualshield.phone.core.sim.SimResolver
import com.dualshield.phone.data.db.dao.SimProfileDao
import com.dualshield.phone.data.db.entity.SimProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Owns the two SIM profiles.
 *
 * The profiles exist whether or not a SIM is physically present, so that a user who takes
 * SIM 2 out for a day does not come back to an empty Shield screen with their rules gone.
 */
class SimRepository(
    private val dao: SimProfileDao,
    private val resolver: SimResolver,
) {

    fun observeProfiles(): Flow<List<SimProfileEntity>> = dao.observeAll()

    suspend fun profiles(): List<SimProfileEntity> = dao.getAll()

    suspend fun profile(slotIndex: Int): SimProfileEntity? = dao.getBySlot(slotIndex)

    /**
     * Creates the two default profiles on first run.
     *
     * Slot 0 starts protected and slot 1 unprotected, which matches the asymmetric policy
     * the product is built around: one line that needs filtering, and one that must ring
     * for anything — an unknown number on the second line is expected to be a call worth
     * taking.
     *
     * This is a *starting point*, not an assumption about what the lines are for. The app
     * does not know which SIM is which, does not name them, and either slot can be switched
     * at any time. Only a first run is affected: an existing install keeps whatever the user
     * already chose.
     */
    suspend fun ensureDefaults(now: Long) {
        if (dao.getAll().isNotEmpty()) return
        dao.upsertAll(
            listOf(
                SimProfileEntity(
                    slotIndex = 0,
                    label = defaultLabelForSlot(0),
                    filteringEnabled = defaultFilteringForSlot(0),
                    lastSeenAt = now,
                ),
                SimProfileEntity(
                    slotIndex = 1,
                    label = defaultLabelForSlot(1),
                    filteringEnabled = defaultFilteringForSlot(1),
                    lastSeenAt = now,
                ),
            ),
        )
    }

    /** Refreshes carrier/subscription details from the platform without touching user settings. */
    suspend fun syncHardware(now: Long) {
        val sims = resolver.activeSims()
        for (sim in sims) {
            val existing = dao.getBySlot(sim.slotIndex)
            if (existing == null) {
                dao.upsert(
                    SimProfileEntity(
                        slotIndex = sim.slotIndex,
                        label = defaultLabelForSlot(sim.slotIndex),
                        filteringEnabled = defaultFilteringForSlot(sim.slotIndex),
                        subscriptionId = sim.subscriptionId,
                        carrierName = sim.carrierName,
                        lastSeenAt = now,
                    ),
                )
            } else {
                dao.setHardwareInfo(sim.slotIndex, sim.subscriptionId, sim.carrierName, now)
            }
        }
    }

    suspend fun setFilteringEnabled(slotIndex: Int, enabled: Boolean) =
        dao.setFilteringEnabled(slotIndex, enabled)

    suspend fun setAllowContacts(slotIndex: Int, allow: Boolean) =
        dao.setAllowContacts(slotIndex, allow)

    suspend fun setLabel(slotIndex: Int, label: String) =
        dao.setLabel(slotIndex, label.trim().ifBlank { defaultLabelForSlot(slotIndex) })

    fun activeSims(): List<SimInfo> = resolver.activeSims()

    fun resolveSlotIndex(handle: android.telecom.PhoneAccountHandle?): Int? =
        resolver.resolveSlotIndex(handle)

    fun phoneAccountHandleForSlot(slotIndex: Int) = resolver.phoneAccountHandleForSlot(slotIndex)

    /**
     * Maps a call-log `PHONE_ACCOUNT_ID` back to a slot.
     *
     * The call log stores the account id as an opaque string, so we compare it against both
     * the handle id and the subscription id rather than parsing it.
     */
    fun slotForPhoneAccountId(accountId: String?): Int? {
        if (accountId.isNullOrBlank()) return null
        val sims = runCatching { resolver.activeSims() }.getOrDefault(emptyList())
        return sims.firstOrNull { sim ->
            sim.phoneAccountHandle?.id == accountId ||
                sim.subscriptionId.toString() == accountId
        }?.slotIndex
    }

    fun slotForSubscriptionId(subscriptionId: Int): Int? {
        if (subscriptionId < 0) return null
        return runCatching { resolver.activeSims() }
            .getOrDefault(emptyList())
            .firstOrNull { it.subscriptionId == subscriptionId }
            ?.slotIndex
    }

    fun subscriptionIdForSlot(slotIndex: Int): Int =
        runCatching { resolver.activeSims() }
            .getOrDefault(emptyList())
            .firstOrNull { it.slotIndex == slotIndex }
            ?.subscriptionId
            ?: -1

    companion object {
        /**
         * A fresh profile starts with no user label at all.
         *
         * The app used to seed "Duty" and "Personal", which then read back as though the
         * user had chosen them — and quietly disagreed with whatever they had actually named
         * their lines. An empty label means "the user has not named this SIM", and the UI
         * falls back to plain "SIM 1" / "SIM 2" until they do. Existing profiles are never
         * rewritten: a label already in the database is the user's and stays.
         */
        const val NO_LABEL = ""

        fun defaultLabelForSlot(@Suppress("UNUSED_PARAMETER") slotIndex: Int): String = NO_LABEL

        /**
         * Whether a brand-new profile for [slotIndex] starts with Shield enforcing.
         *
         * The first slot does, the second does not. It lives here as one function rather
         * than as a literal at each creation site, because the two sites — first run and a
         * SIM appearing later — used to disagree, and a slot created by the second path
         * came up with the opposite setting from the same slot created by the first.
         *
         * Any slot beyond the first two is unexpected hardware, and starts unfiltered:
         * leaving a line unfiltered is recoverable in a way that silently filtering one
         * the user has not seen yet is not.
         */
        fun defaultFilteringForSlot(slotIndex: Int): Boolean = slotIndex == 0
    }
}
