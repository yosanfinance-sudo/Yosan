package com.example.features.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.utils.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles device boot and package replacement events to reschedule update checks.
 * Exported specifically to receive Android system broadcasts.
 */
class UpdateBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val action = intent?.action
        Log.d(TAG, "UpdateBootReceiver triggered with action: $action")

        val prefs = AppPreferences(appContext)
        if (!prefs.isAutoCheckUpdatesEnabled) {
            return
        }

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateCheckJobService.schedulePeriodicCheck(appContext)
            UpdateCheckJobService.scheduleAlarmCheck(appContext)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updater = UpdateManager(appContext)
                updater.checkForUpdates()
            } catch (e: Exception) {
                Log.w(TAG, "Boot update check failed: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateBootReceiver"
    }
}
