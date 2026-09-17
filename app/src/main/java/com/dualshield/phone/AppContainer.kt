package com.dualshield.phone

import android.content.Context
import com.dualshield.phone.core.sim.SimResolver
import com.dualshield.phone.data.db.DualShieldDatabase
import com.dualshield.phone.data.repository.RuleRepository
import com.dualshield.phone.data.repository.SettingsRepository
import com.dualshield.phone.data.repository.SimRepository
import com.dualshield.phone.data.repository.VaultRepository
import com.dualshield.phone.data.system.CallLogRepository
import com.dualshield.phone.data.system.ContactsRepository
import com.dualshield.phone.data.system.SmsRepository
import com.dualshield.phone.shield.ShieldEngine
import com.dualshield.phone.shield.ShieldNotifier
import com.dualshield.phone.telecom.CallPlacer
import com.dualshield.phone.telecom.RoleRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Manual dependency container.
 *
 * A DI framework would buy little here and cost build complexity; what matters is that
 * background entry points (the screening service, the SMS receiver) can reach the same
 * singletons the UI uses, which a plain container gives us with no annotation processing.
 */
class AppContainer(
    private val appContext: Context,
    scope: CoroutineScope,
) {
    val database: DualShieldDatabase by lazy { DualShieldDatabase.get(appContext) }

    val simResolver: SimResolver by lazy { SimResolver(appContext) }

    val simRepository: SimRepository by lazy {
        SimRepository(database.simProfileDao(), simResolver)
    }

    val ruleRepository: RuleRepository by lazy {
        RuleRepository(
            context = appContext,
            ruleDao = database.callRuleDao(),
            allowDao = database.allowRuleDao(),
            simDao = database.simProfileDao(),
            packDao = database.rulePackMetadataDao(),
        )
    }

    val vaultRepository: VaultRepository by lazy {
        VaultRepository(database.blockedCallDao(), database.blockedMessageDao())
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val contactsRepository: ContactsRepository by lazy { ContactsRepository(appContext) }

    val callLogRepository: CallLogRepository by lazy { CallLogRepository(appContext) }

    val smsRepository: SmsRepository by lazy { SmsRepository(appContext) }

    val roleRepository: RoleRepository by lazy { RoleRepository(appContext) }

    val callPlacer: CallPlacer by lazy { CallPlacer(appContext, simRepository) }

    val shieldNotifier: ShieldNotifier by lazy { ShieldNotifier(appContext) }

    val shieldEngine: ShieldEngine by lazy {
        ShieldEngine(
            scope = scope,
            ruleRepository = ruleRepository,
            simRepository = simRepository,
            vaultRepository = vaultRepository,
            contactsRepository = contactsRepository,
            settingsRepository = settingsRepository,
        )
    }
}
