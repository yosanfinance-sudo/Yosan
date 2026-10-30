package com.example.features.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.remote.ApiClient
import com.example.features.drive.DriveOperationResult
import com.example.features.drive.GoogleDriveService
import com.example.utils.BackupService
import com.example.utils.SecureTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

enum class StorageMode(val key: String, val displayName: String) {
    LOCAL_ONLY("LOCAL", "Local Only"),
    GOOGLE_DRIVE("DRIVE", "Google Drive"),
    LOCAL_PLUS_DRIVE("SYNC", "Local + Google Drive");

    companion object {
        fun fromKey(key: String?): StorageMode = when (key?.uppercase()) {
            "DRIVE" -> GOOGLE_DRIVE
            "SYNC" -> LOCAL_PLUS_DRIVE
            else -> LOCAL_ONLY
        }
    }
}

enum class SyncState {
    SYNCED,
    PENDING,
    SYNCING,
    OFFLINE,
    FAILED
}

data class SyncStatusInfo(
    val state: SyncState = SyncState.SYNCED,
    val lastSyncTimeMs: Long = 0L,
    val currentVersion: Int = 1,
    val errorMessage: String? = null
)

class SyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val tokenManager: SecureTokenManager
) {
    companion object {
        private const val TAG = "SyncManager"
        @Volatile
        var isRestoring: Boolean = false
        @Volatile
        var lastRestoredTimeMs: Long = 0L
    }

    private val driveService = GoogleDriveService(context, tokenManager)

    private val _storageMode = MutableStateFlow(StorageMode.fromKey(tokenManager.getStoragePreference()))
    val storageMode: StateFlow<StorageMode> = _storageMode.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncStatusInfo())
    val syncStatus: StateFlow<SyncStatusInfo> = _syncStatus.asStateFlow()

    suspend fun loadInitialStatus() = withContext(Dispatchers.IO) {
        val storedPref = tokenManager.getStoragePreference()
        _storageMode.value = StorageMode.fromKey(storedPref)

        val lastSyncStr = database.syncDao().getMetadataValue("last_backup_timestamp")
        val versionStr = database.syncDao().getMetadataValue("current_backup_version")
        val statusStr = database.syncDao().getMetadataValue("sync_status")

        val state = when (statusStr) {
            "PENDING" -> SyncState.PENDING
            "SYNCING" -> SyncState.PENDING
            "FAILED" -> SyncState.FAILED
            "OFFLINE" -> SyncState.OFFLINE
            else -> SyncState.SYNCED
        }

        _syncStatus.value = SyncStatusInfo(
            state = state,
            lastSyncTimeMs = lastSyncStr?.toLongOrNull() ?: 0L,
            currentVersion = versionStr?.toIntOrNull() ?: 1,
            errorMessage = null
        )
    }

    suspend fun setStorageMode(mode: StorageMode) = withContext(Dispatchers.IO) {
        _storageMode.value = mode
        tokenManager.setStoragePreference(mode.key)
        try {
            database.userProfileDao().updateStoragePreference(mode.key)
        } catch (_: Exception) {}

        // If switching to LOCAL_ONLY, do not upload; clear pending
        if (mode == StorageMode.LOCAL_ONLY) {
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.SYNCED, errorMessage = null)
            database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "SYNCED"))
        } else if (mode == StorageMode.LOCAL_PLUS_DRIVE) {
            // Trigger synchronization when enabled
            triggerSync()
        }
    }

    /**
     * Call when local records are inserted, modified, or deleted.
     */
    suspend fun markDataChanged() = withContext(Dispatchers.IO) {
        if (_storageMode.value == StorageMode.LOCAL_PLUS_DRIVE || _storageMode.value == StorageMode.GOOGLE_DRIVE) {
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.PENDING)
            database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "PENDING"))
        }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Executes the synchronization pipeline according to selected storage mode.
     */
    suspend fun triggerSync(onResult: ((Boolean, String?) -> Unit)? = null): Boolean = withContext(Dispatchers.IO) {
        if (_storageMode.value == StorageMode.LOCAL_ONLY) {
            if (driveService.isConnected() && !tokenManager.getDriveAccessToken().isNullOrBlank()) {
                _storageMode.value = StorageMode.GOOGLE_DRIVE
                tokenManager.setStoragePreference(StorageMode.GOOGLE_DRIVE.key)
            } else {
                onResult?.invoke(true, "Local only mode active; no cloud upload needed.")
                return@withContext true
            }
        }

        if (!isOnline()) {
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.OFFLINE, errorMessage = "Device is offline. Changes queued for sync.")
            database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "OFFLINE"))
            onResult?.invoke(false, "Offline: sync will resume when internet is available.")
            return@withContext false
        }

        _syncStatus.value = _syncStatus.value.copy(state = SyncState.SYNCING, errorMessage = null)
        database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "SYNCING"))

        val currentVersionStr = database.syncDao().getMetadataValue("current_backup_version")
        val currentVersion = currentVersionStr?.toIntOrNull() ?: 0
        val nextVersion = currentVersion + 1

        try {
            // 1. Try direct Google Drive API if Drive OAuth token is present
            if (driveService.isConnected() && !tokenManager.getDriveAccessToken().isNullOrBlank()) {
                val driveResult = driveService.performDirectDriveBackup(database, nextVersion)
                return@withContext when (driveResult) {
                    is DriveOperationResult.Success -> {
                        val nowMs = System.currentTimeMillis()
                        database.syncDao().setMetadata(SyncMetadataEntity("current_backup_version", nextVersion.toString()))
                        database.syncDao().setMetadata(SyncMetadataEntity("last_backup_timestamp", nowMs.toString()))
                        database.syncDao().setMetadata(SyncMetadataEntity("current_backup_file_id", driveResult.data.fileId))
                        database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "SYNCED"))

                        _syncStatus.value = SyncStatusInfo(
                            state = SyncState.SYNCED,
                            lastSyncTimeMs = nowMs,
                            currentVersion = nextVersion,
                            errorMessage = null
                        )
                        onResult?.invoke(true, "Google Drive sync completed (v$nextVersion)")
                        true
                    }
                    is DriveOperationResult.Error -> {
                        _syncStatus.value = _syncStatus.value.copy(state = SyncState.FAILED, errorMessage = driveResult.message)
                        database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "FAILED"))
                        onResult?.invoke(false, driveResult.message)
                        false
                    }
                }
            }

            // 2. Fallback to backend drive route if backend is reachable
            val api = ApiClient.getAuthApi(context)
            val backupJson = BackupService.generateBackupJsonObject(database, nextVersion)
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val mapAdapter = moshi.adapter(Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val payloadMap = mapAdapter.fromJson(backupJson.toString()) as? Map<String, Any?>

            val response = api.backupNow(com.example.data.remote.BackupNowRequest(payload = payloadMap))
            if (response.isSuccessful && response.body()?.success == true) {
                val nowMs = System.currentTimeMillis()
                val body = response.body()!!
                val fileId = body.fileId ?: ""

                database.syncDao().setMetadata(SyncMetadataEntity("current_backup_version", body.version.toString()))
                database.syncDao().setMetadata(SyncMetadataEntity("last_backup_timestamp", nowMs.toString()))
                if (fileId.isNotBlank()) {
                    database.syncDao().setMetadata(SyncMetadataEntity("current_backup_file_id", fileId))
                }
                database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "SYNCED"))

                _syncStatus.value = SyncStatusInfo(
                    state = SyncState.SYNCED,
                    lastSyncTimeMs = nowMs,
                    currentVersion = body.version,
                    errorMessage = null
                )
                onResult?.invoke(true, body.message ?: "Sync completed (v${body.version})")
                true
            } else {
                val errMsg = "Sync failed: ${response.message()}"
                _syncStatus.value = _syncStatus.value.copy(state = SyncState.FAILED, errorMessage = errMsg)
                database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "FAILED"))
                onResult?.invoke(false, errMsg)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}", e)
            val errMsg = e.localizedMessage ?: "Synchronization failed"
            _syncStatus.value = _syncStatus.value.copy(state = SyncState.FAILED, errorMessage = errMsg)
            database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "FAILED"))
            onResult?.invoke(false, errMsg)
            false
        }
    }

    /**
     * Checks if Google Drive has a newer backup than local database (e.g. uploaded from another device).
     * If newer, automatically restores it so both devices stay synchronized seamlessly.
     */
    suspend fun syncWithDriveIfNewer(userEmail: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext false
        val driveToken = tokenManager.getDriveAccessToken()
        if (driveToken.isNullOrBlank() || !driveService.isConnected()) return@withContext false
        if (!tokenManager.isLiveSyncEnabled()) return@withContext false

        try {
            val targetEmail = userEmail ?: tokenManager.getUserEmail()
            val latestJson = driveService.getLatestBackupContent(driveToken, targetEmail) ?: return@withContext false
            val root = org.json.JSONObject(latestJson)
            val remoteVersion = root.optInt("version", root.optInt("backup_version", 0))
            val remoteCreatedAt = root.optLong("createdAt", 0L)

            val localVersion = database.syncDao().getMetadataValue("current_backup_version")?.toIntOrNull() ?: 0
            val localTimestamp = database.syncDao().getMetadataValue("last_backup_timestamp")?.toLongOrNull() ?: 0L

            val isRemoteNewer = when {
                remoteCreatedAt > 0L && localTimestamp > 0L -> remoteCreatedAt > (localTimestamp + 2000L)
                remoteVersion > localVersion -> true
                localTimestamp == 0L && (remoteCreatedAt > 0L || remoteVersion > 0) -> true
                else -> false
            }

            if (isRemoteNewer) {
                Log.i(TAG, "Newer Google Drive backup found (remote v$remoteVersion, time $remoteCreatedAt vs local v$localVersion, time $localTimestamp). Auto-restoring for two-device sync...")
                isRestoring = true
                val restoreResult = try {
                    BackupService.restoreBackupJson(latestJson, database, clearExisting = true)
                } finally {
                    isRestoring = false
                }
                if (restoreResult.success) {
                    val finalTimestamp = if (remoteCreatedAt > 0L) remoteCreatedAt else System.currentTimeMillis()
                    lastRestoredTimeMs = System.currentTimeMillis()
                    database.syncDao().setMetadata(SyncMetadataEntity("current_backup_version", remoteVersion.toString()))
                    database.syncDao().setMetadata(SyncMetadataEntity("last_backup_timestamp", finalTimestamp.toString()))
                    database.syncDao().setMetadata(SyncMetadataEntity("sync_status", "SYNCED"))
                    _syncStatus.value = SyncStatusInfo(
                        state = SyncState.SYNCED,
                        lastSyncTimeMs = finalTimestamp,
                        currentVersion = remoteVersion,
                        errorMessage = null
                    )
                    Log.i(TAG, "Multi-device auto-sync successfully updated local database to version $remoteVersion.")
                    return@withContext true
                } else {
                    Log.w(TAG, "Auto-restore failed: ${restoreResult.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncWithDriveIfNewer check failed: ${e.message}")
        }
        false
    }
}
