package com.dualshield.phone.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dualshield.phone.AppContainer
import com.dualshield.phone.ui.contacts.ContactsViewModel
import com.dualshield.phone.ui.messages.MessagesViewModel
import com.dualshield.phone.ui.phone.PhoneViewModel
import com.dualshield.phone.ui.settings.SettingsViewModel
import com.dualshield.phone.ui.shield.ShieldViewModel
import com.dualshield.phone.ui.shield.VaultViewModel

/**
 * One factory for every ViewModel in the app.
 *
 * With a manual container there is nothing to generate, so the whole wiring is this list.
 */
fun appViewModelFactory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
    initializer { PhoneViewModel(container) }
    initializer { MessagesViewModel(container) }
    initializer { ContactsViewModel(container) }
    initializer { ShieldViewModel(container) }
    initializer { VaultViewModel(container) }
    initializer { SettingsViewModel(container) }
}
