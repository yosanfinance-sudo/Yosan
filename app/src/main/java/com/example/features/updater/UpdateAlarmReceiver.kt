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
 * Handles device boot, app package updates, and periodic background wakeups
 * to ensure update checks run reliably even when the app is swiped away from recents.
 */
class UpdateAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        val action = intent?.action
        Log.d(TAG, "UpdateAlarmReceiver triggered with action: $action")

        val prefs = AppPreferences(appContext)
        if (!prefs.isAutoCheckUpdatesEnabled) {
            return
        }

        // Reschedule JobScheduler jobs if triggered after a device reboot or app upgrade
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateCheckJobService.schedulePeriodicCheck(appContext)
            UpdateCheckJobService.scheduleAlarmCheck(appContext)
        }

        // Perform asynchronous background update check with wakeful async lock
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updater = UpdateManager(appContext)
                updater.checkForUpdates()
            } catch (e: Exception) {
                Log.w(TAG, "Background check failed in receiver: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateAlarmReceiver"
    }
}
