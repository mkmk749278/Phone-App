package com.dualshield.phone.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import com.dualshield.phone.data.repository.SimRepository

/**
 * Places outgoing calls on a specific SIM.
 *
 * The SIM is selected by handing Telecom the real [android.telecom.PhoneAccountHandle] for
 * that slot. Nothing here infers a SIM from a subscription id or an index, because those
 * orderings do not hold across devices.
 */
class CallPlacer(
    private val context: Context,
    private val simRepository: SimRepository,
) {

    sealed interface Result {
        data object Placed : Result
        data class Failed(val message: String) : Result
    }

    fun placeCall(number: String, slotIndex: Int?): Result {
        val trimmed = number.trim()
        if (trimmed.isEmpty()) return Result.Failed("Enter a number to call.")

        val uri = Uri.fromParts("tel", trimmed, null)

        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Without CALL_PHONE we can still hand the number to the system dialer, which
            // is a better outcome than a dead button.
            return dial(uri)
        }

        val handle = slotIndex?.let {
            runCatching { simRepository.phoneAccountHandleForSlot(it) }.getOrNull()
        }
        val extras = Bundle().apply {
            if (handle != null) {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
            }
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
        }

        return runCatching {
            context.getSystemService(TelecomManager::class.java)?.placeCall(uri, extras)
            Result.Placed
        }.getOrElse {
            dial(uri)
        }
    }

    /** Falls back to the system dialer rather than showing the user a raw failure. */
    private fun dial(uri: Uri): Result = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        Result.Placed
    }.getOrElse {
        Result.Failed("This call couldn't be started. Check the number and try again.")
    }
}
