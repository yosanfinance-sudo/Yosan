package com.example.features.updater

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.utils.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UpdateCheckJobService : JobService() {

    private var jobScope: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val prefs = AppPreferences(applicationContext)
        if (!prefs.isAutoCheckUpdatesEnabled) {
            jobFinished(params, false)
            return false
        }

        jobScope = CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting periodic background update check...")
                val updater = UpdateManager(applicationContext)
                updater.checkForUpdates()
            } catch (e: Exception) {
                Log.w(TAG, "Background update check failed: ${e.message}")
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope?.cancel()
        return true
    }

    companion object {
        private const val TAG = "UpdateCheckJobService"
        private const val JOB_ID = 9001

        fun schedulePeriodicCheck(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val existing = scheduler.getPendingJob(JOB_ID)
                if (existing != null) {
                    return
                }

                val component = ComponentName(context, UpdateCheckJobService::class.java)
                val builder = JobInfo.Builder(JOB_ID, component)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L) // 15 mins (standard minimum interval)
                    .setPersisted(true)

                val result = scheduler.schedule(builder.build())
                if (result == JobScheduler.RESULT_SUCCESS) {
                    Log.d(TAG, "Scheduled background update check successfully (JOB_ID=$JOB_ID)")
                } else {
                    Log.w(TAG, "Failed to schedule background update check: $result")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling update check job: ${e.message}", e)
            }
        }
    }
}
