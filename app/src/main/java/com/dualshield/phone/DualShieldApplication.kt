package com.dualshield.phone

import android.app.Application
import android.content.Context
import android.util.Log
import com.dualshield.phone.data.repository.SimRepository
import com.dualshield.phone.telecom.CallRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Process entry point.
 *
 * Note what does *not* happen here: no network client, no analytics, no crash reporter, no
 * remote config. The heaviest thing this does is warm the rule snapshot so that a call
 * arriving one second after boot is screened from memory rather than from disk.
 */
class DualShieldApplication : Application() {

    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext, applicationScope)
        container.shieldNotifier.ensureChannel()
        container.shieldEngine.start()

        CallRegistry.setSimLabelResolver { handle ->
            val slot = runCatching { container.simResolver.resolveSlotIndex(handle) }.getOrNull()
            val label = slot?.let { index ->
                container.shieldEngine.snapshot.value.forSlot(index)?.label
                    ?: SimRepository.defaultLabelForSlot(index)
            }
            slot to label
        }

        applicationScope.launch {
            val now = System.currentTimeMillis()
            runCatching {
                container.simRepository.ensureDefaults(now)
                container.simRepository.syncHardware(now)
                container.ruleRepository.seedBundledPackIfNeeded(now)
            }.onFailure { Log.e(TAG, "First-run setup failed.", it) }
        }

        applicationScope.launch {
            container.settingsRepository.settings.collect { settings ->
                container.shieldNotifier.enabled = settings.notifyOnBlockedCall
            }
        }
    }

    companion object {
        private const val TAG = "DualShieldApplication"

        /** Safe accessor for background entry points that only hold a [Context]. */
        fun containerOrNull(context: Context): AppContainer? =
            (context.applicationContext as? DualShieldApplication)?.container
    }
}
