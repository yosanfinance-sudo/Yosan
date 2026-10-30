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
 * Internal unexported broadcast receiver for periodic background update checks
 * triggered by AlarmManager.
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

        // Perform asynchronous background update check with wakeful async lock
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val updater = UpdateManager(appContext)
                updater.checkForUpdates()
            } catch (e: Exception) {
                Log.w(TAG, "Background check failed in alarm receiver: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "UpdateAlarmReceiver"
        const val ACTION_CHECK_UPDATES_ALARM = "com.example.ACTION_CHECK_UPDATES_ALARM"
    }
}
