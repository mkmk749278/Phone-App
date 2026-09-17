package com.dualshield.phone.ui

import com.dualshield.phone.AppContainer
import com.dualshield.phone.ui.components.SimOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The SIM list every screen shares.
 *
 * Profiles come from the database (so labels and protection state survive a SIM being
 * removed), while presence comes from the platform. When we cannot read the platform at all
 * — typically because the phone-state permission has not been granted yet — both SIMs are
 * treated as present so the UI stays usable rather than appearing broken.
 */
fun AppContainer.simOptionsFlow(): Flow<List<SimOption>> =
    simRepository.observeProfiles()
        .map { profiles ->
            val presentSlots = runCatching { simRepository.activeSims() }
                .getOrDefault(emptyList())
                .map { it.slotIndex }
                .toSet()
            profiles
                .sortedBy { it.slotIndex }
                .map { profile ->
                    SimOption(
                        slotIndex = profile.slotIndex,
                        label = profile.label,
                        present = presentSlots.isEmpty() || profile.slotIndex in presentSlots,
                        protectionEnabled = profile.filteringEnabled,
                    )
                }
        }
        .flowOn(Dispatchers.IO)
