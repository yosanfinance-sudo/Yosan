package com.example.features.drive

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.remote.*
import com.example.utils.BackupEncryptionUtil
import com.example.utils.BackupService
import com.example.utils.RestoreResult
import com.example.utils.SecureTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

sealed class DriveOperationResult<out T> {
    data class Success<out T>(val data: T, val message: String = "") : DriveOperationResult<T>()
    data class Error(val message: String, val code: String = "DRIVE_ERROR") : DriveOperationResult<Nothing>()
}

class GoogleDriveService(
    private val context: Context,
    private val tokenManager: SecureTokenManager
) {
    private val TAG = "GoogleDriveService"
    private val driveApi: GoogleDriveApi = ApiClient.getGoogleDriveApi()

    fun isConnected(): Boolean {
        return tokenManager.isDriveConnected()
    }

    fun getConnectedDriveEmail(): String? {
        return tokenManager.getDriveEmail()
    }

    fun disconnectDrive() {
        tokenManager.clearDriveTokens()
        Log.i(TAG, "Google Drive disconnected locally")
    }

    suspend fun getValidDriveToken(): String? = withContext(Dispatchers.IO) {
        val driveEmail = tokenManager.getDriveEmail()
        val currentToken = tokenManager.getDriveAccessToken()
        if (!driveEmail.isNullOrBlank()) {
            try {
                val authManager = GoogleDriveAuthManager(context)
                val tokenRes = authManager.fetchAccessTokenForEmail(driveEmail)
                if (tokenRes is DriveAuthTokenResult.Success) {
                    tokenManager.saveDriveTokens(accessToken = tokenRes.token, driveEmail = driveEmail)
                    return@withContext tokenRes.token
                }
            } catch (e: Exception) {
                Log.w(TAG, "Silent Drive token refresh: ${e.message}")
            }
        }
        currentToken
    }

    /**
     * Resolves the root "Yosan-Backup" folder ID, creating it if it does not yet exist.
     * Prevents duplicate folder creation by querying first.
     */
    suspend fun getOrCreateRootFolder(bearerToken: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = 'Yosan-Backup' and trashed = false"
            val response = driveApi.listFiles(bearerToken = "Bearer $bearerToken", query = query)
            if (response.isSuccessful && response.body() != null) {
                val existing = response.body()!!.files.firstOrNull()
                if (existing != null) {
                    return@withContext existing.id
                }
            } else if (!response.isSuccessful) {
                Log.e(TAG, "Failed to list folders: ${response.code()} ${response.errorBody()?.string()}")
                return@withContext null
            }

            // Create Yosan-Backup folder using clean metadata request
            val folderMetadata = com.example.data.remote.GoogleDriveCreateFolderRequest(
                name = "Yosan-Backup"
            )
            val createRes = driveApi.createFolder(bearerToken = "Bearer $bearerToken", folder = folderMetadata)
            if (createRes.isSuccessful && createRes.body() != null) {
                val createdId = createRes.body()!!.id
                Log.i(TAG, "Root 'Yosan-Backup' folder created successfully: $createdId")
                return@withContext createdId
            } else {
                Log.e(TAG, "Failed to create root folder: ${createRes.code()} ${createRes.errorBody()?.string()}")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving root Drive folder: ${e.message}", e)
            null
        }
    }

    /**
     * Resolves the "versions" subfolder inside "Yosan-Backup", creating it if missing.
     */
    suspend fun getOrCreateVersionsFolder(bearerToken: String, rootFolderId: String): String? = withContext(Dispatchers.IO) {
        try {
            val query = "mimeType = 'application/vnd.google-apps.folder' and name = 'versions' and '${rootFolderId}' in parents and trashed = false"
            val response = driveApi.listFiles(bearerToken = "Bearer $bearerToken", query = query)
            if (response.isSuccessful && response.body() != null) {
                val existing = response.body()!!.files.firstOrNull()
                if (existing != null) {
                    return@withContext existing.id
                }
            } else if (!response.isSuccessful) {
                Log.e(TAG, "Failed to list versions subfolder: ${response.code()} ${response.errorBody()?.string()}")
                return@withContext null
            }

            // Create versions subfolder using clean metadata request
            val folderMetadata = com.example.data.remote.GoogleDriveCreateFolderRequest(
                name = "versions",
                parents = listOf(rootFolderId)
            )
            val createRes = driveApi.createFolder(bearerToken = "Bearer $bearerToken", folder = folderMetadata)
            if (createRes.isSuccessful && createRes.body() != null) {
                val createdId = createRes.body()!!.id
                Log.i(TAG, "Versions subfolder created successfully: $createdId")
                return@withContext createdId
            } else {
                Log.e(TAG, "Failed to create versions folder: ${createRes.code()} ${createRes.errorBody()?.string()}")
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving versions subfolder: ${e.message}", e)
            null
        }
    }

    /**
     * Verifies and immediately provisions both the root 'Yosan-Backup' folder
     * and the 'versions' subfolder upon account connection.
     */
    suspend fun ensureDriveFoldersExist(bearerToken: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rootId = getOrCreateRootFolder(bearerToken)
            if (!rootId.isNullOrBlank()) {
                val versionsId = getOrCreateVersionsFolder(bearerToken, rootId)
                Log.i(TAG, "Google Drive folders verified (root=$rootId, versions=$versionsId)")
                true
            } else {
                Log.e(TAG, "Could not verify or create root Google Drive folder")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "ensureDriveFoldersExist failed: ${e.message}", e)
            false
        }
    }

    /**
     * Uploads or updates a JSON backup file in the specified Google Drive folder.
     */
    suspend fun uploadJsonFile(
        bearerToken: String,
        folderId: String,
        fileName: String,
        jsonString: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            // Check if file already exists in folder
            val query = "name = '$fileName' and '$folderId' in parents and trashed = false"
            val listRes = driveApi.listFiles(bearerToken = "Bearer $bearerToken", query = query)
            if (!listRes.isSuccessful) {
                Log.e(TAG, "Failed to query file '$fileName': ${listRes.code()} ${listRes.errorBody()?.string()}")
                return@withContext null
            }
            val existingFile = listRes.body()?.files?.firstOrNull()

            val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
            val requestBody = jsonString.toRequestBody(mediaType)

            if (existingFile != null) {
                // Update media content of existing file
                val updateRes = driveApi.updateMedia(
                    bearerToken = "Bearer $bearerToken",
                    fileId = existingFile.id,
                    body = requestBody
                )
                if (updateRes.isSuccessful && updateRes.body() != null) {
                    return@withContext updateRes.body()!!.id
                } else {
                    Log.e(TAG, "Failed to update media: ${updateRes.code()} ${updateRes.errorBody()?.string()}")
                    return@withContext null
                }
            } else {
                // Construct multipart creation
                val boundary = "YosanDriveBoundary_" + System.currentTimeMillis()
                val metadataJson = """{"name":"$fileName","parents":["$folderId"],"mimeType":"application/json"}"""

                val multipartContent = buildString {
                    append("--$boundary\r\n")
                    append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    append(metadataJson)
                    append("\r\n--$boundary\r\n")
                    append("Content-Type: application/json\r\n\r\n")
                    append(jsonString)
                    append("\r\n--$boundary--\r\n")
                }

                val multipartBody = multipartContent.toRequestBody("multipart/related; boundary=$boundary".toMediaTypeOrNull())
                val uploadRes = driveApi.uploadMultipart(
                    bearerToken = "Bearer $bearerToken",
                    multipartBody = multipartBody
                )
                if (uploadRes.isSuccessful && uploadRes.body() != null) {
                    return@withContext uploadRes.body()!!.id
                } else {
                    Log.e(TAG, "Failed to create file: ${uploadRes.code()} ${uploadRes.errorBody()?.string()}")
                    return@withContext null
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading JSON to Drive: ${e.message}", e)
            null
        }
    }

    /**
     * Downloads file contents by file ID.
     */
    suspend fun downloadJsonFile(bearerToken: String, fileId: String): String? = withContext(Dispatchers.IO) {
        try {
            val response = driveApi.downloadMedia(bearerToken = "Bearer $bearerToken", fileId = fileId)
            if (response.isSuccessful && response.body() != null) {
                return@withContext response.body()!!.string()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading Drive file: ${e.message}", e)
            null
        }
    }

    private fun getBackupManifestFileName(userEmail: String? = null): String {
        val email = userEmail?.takeIf { it.isNotBlank() } ?: tokenManager.getUserEmail()
        val userKey = AppDatabase.sanitizeUserKey(email)
        return if (userKey.isBlank() || userKey == "guest") {
            "detailsaboutbackup.json"
        } else {
            "detailsaboutbackup_$userKey.json"
        }
    }

    /**
     * Retrieves the content of the latest backup JSON from root Yosan-Backup folder or versions folder.
     */
    suspend fun getLatestBackupContent(driveToken: String, userEmail: String? = null): String? = withContext(Dispatchers.IO) {
        try {
            val rootFolderId = getOrCreateRootFolder(driveToken) ?: return@withContext null
            val manifestName = getBackupManifestFileName(userEmail)

            // 1. Look for user-specific manifest in root
            var query = "name = '$manifestName' and '$rootFolderId' in parents and trashed = false"
            var listRes = driveApi.listFiles(bearerToken = "Bearer $driveToken", query = query)

            // 2. Fallback to generic unpartitioned manifest
            if ((!listRes.isSuccessful || listRes.body()?.files.isNullOrEmpty()) && manifestName != "detailsaboutbackup.json") {
                query = "name = 'detailsaboutbackup.json' and '$rootFolderId' in parents and trashed = false"
                listRes = driveApi.listFiles(bearerToken = "Bearer $driveToken", query = query)
            }

            // 3. Fallback to any JSON file in root containing 'backup'
            if (!listRes.isSuccessful || listRes.body()?.files.isNullOrEmpty()) {
                query = "'$rootFolderId' in parents and mimeType = 'application/json' and trashed = false"
                listRes = driveApi.listFiles(bearerToken = "Bearer $driveToken", query = query)
            }

            // 4. Fallback to versions folder if root has no manifest
            if (!listRes.isSuccessful || listRes.body()?.files.isNullOrEmpty()) {
                val versionsFolderId = getOrCreateVersionsFolder(driveToken, rootFolderId)
                if (versionsFolderId != null) {
                    query = "'$versionsFolderId' in parents and mimeType = 'application/json' and trashed = false"
                    listRes = driveApi.listFiles(bearerToken = "Bearer $driveToken", query = query)
                }
            }

            if (listRes.isSuccessful && listRes.body()?.files?.isNotEmpty() == true) {
                val file = listRes.body()!!.files.first()
                return@withContext downloadJsonFile(driveToken, file.id)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest backup content from Drive: ${e.message}", e)
        }
        null
    }

    /**
     * Performs a complete backup cycle directly to user's Google Drive:
     * 1. Generates JSON backup payload with SHA-256 checksum.
     * 2. Finds/creates "Yosan-Backup" root and "versions" subfolder.
     * 3. Writes historical snapshot `versions/backup-v%04d.json`.
     * 4. Updates root `detailsaboutbackup.json`.
     */
    suspend fun performDirectDriveBackup(
        database: AppDatabase,
        nextVersion: Int
    ): DriveOperationResult<DriveBackupVersionDto> = withContext(Dispatchers.IO) {
        val driveToken = getValidDriveToken() ?: tokenManager.getDriveAccessToken()
        if (driveToken.isNullOrBlank()) {
            return@withContext DriveOperationResult.Error(
                "Google Drive access token missing. Please connect Google Drive in Settings.",
                "DRIVE_NOT_CONNECTED"
            )
        }

        try {
            val lastBackupTimestamp = database.syncDao().getMetadataValue("last_backup_timestamp")?.toLongOrNull() ?: 0L
            val lastDataChange = database.syncDao().getMetadataValue("last_data_change_timestamp")?.toLongOrNull() ?: 0L
            if (lastBackupTimestamp > 0L && lastDataChange > 0L && lastBackupTimestamp >= lastDataChange) {
                Log.d(TAG, "No local changes since last backup. Skipping redundant snapshot upload.")
                val manifestName = getBackupManifestFileName()
                val dto = DriveBackupVersionDto(
                    version = nextVersion,
                    fileName = manifestName,
                    fileId = "",
                    isCurrent = true,
                    createdTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())
                )
                return@withContext DriveOperationResult.Success(dto, "Local database unchanged. Backup skipped.")
            }

            val rootFolderId = getOrCreateRootFolder(driveToken)
                ?: return@withContext DriveOperationResult.Error("Could not access 'Yosan-Backup' folder on Google Drive.")

            val versionsFolderId = getOrCreateVersionsFolder(driveToken, rootFolderId)
                ?: return@withContext DriveOperationResult.Error("Could not access 'versions' directory on Google Drive.")

            val backupJson = BackupService.generateBackupJsonObject(database, nextVersion)
            val rawJsonStr = backupJson.toString(2)

            val email = tokenManager.getUserEmail() ?: ""
            val session = if (email.isNotBlank()) tokenManager.getUserSession(email) else null
            val encryptionKey = session?.googleSub?.takeIf { it.isNotBlank() } ?: email.ifBlank { "yosan_secure_drive_backup" }
            val jsonStr = BackupEncryptionUtil.encrypt(rawJsonStr, encryptionKey)

            val userKey = AppDatabase.sanitizeUserKey(email)
            val snapshotName = if (userKey.isBlank() || userKey == "guest") {
                "backup-v%04d.json".format(java.util.Locale.US, nextVersion)
            } else {
                "backup-$userKey-v%04d.json".format(java.util.Locale.US, nextVersion)
            }
            uploadJsonFile(driveToken, versionsFolderId, snapshotName, jsonStr)

            // Non-blocking prune of old version files (retaining up to 15 snapshots)
            pruneOldVersions(driveToken, versionsFolderId, 15)

            // 2. Update current backup file
            val manifestName = getBackupManifestFileName()
            val currentFileId = uploadJsonFile(driveToken, rootFolderId, manifestName, jsonStr)
                ?: return@withContext DriveOperationResult.Error("Failed to update '$manifestName' on Google Drive.")

            val dto = DriveBackupVersionDto(
                version = nextVersion,
                fileName = manifestName,
                fileId = currentFileId,
                isCurrent = true,
                createdTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }.format(java.util.Date())
            )

            DriveOperationResult.Success(dto, "Successfully backed up to Google Drive (v$nextVersion)")
        } catch (e: Exception) {
            Log.e(TAG, "Direct Drive backup failed: ${e.message}", e)
            DriveOperationResult.Error(e.localizedMessage ?: "Google Drive backup failed")
        }
    }

    /**
     * Downloads and restores the latest backup from Google Drive.
     */
    private suspend fun pruneOldVersions(driveToken: String, versionsFolderId: String, keepCount: Int = 15) {
        try {
            val query = "'$versionsFolderId' in parents and mimeType = 'application/json' and trashed = false"
            val response = driveApi.listFiles(
                bearerToken = "Bearer $driveToken",
                query = query,
                fields = "files(id, name, createdTime)",
                pageSize = 100
            )
            if (response.isSuccessful) {
                val files = response.body()?.files ?: emptyList()
                if (files.size > keepCount) {
                    val sorted = files.sortedByDescending { it.createdTime ?: "" }
                    val toDelete = sorted.drop(keepCount)
                    for (file in toDelete) {
                        try {
                            driveApi.deleteFile("Bearer $driveToken", file.id)
                            Log.d(TAG, "Pruned old Drive backup version: ${file.name} (${file.id})")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to delete old version file ${file.id}: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prune old versions: ${e.message}")
        }
    }

    suspend fun restoreLatestFromDrive(
        database: AppDatabase,
        userEmail: String? = null
    ): DriveOperationResult<RestoreResult> = withContext(Dispatchers.IO) {
        val driveToken = getValidDriveToken() ?: tokenManager.getDriveAccessToken()
        if (driveToken.isNullOrBlank()) {
            return@withContext DriveOperationResult.Error(
                "Google Drive not connected. Please connect Google Drive first.",
                "DRIVE_NOT_CONNECTED"
            )
        }

        try {
            val jsonContent = getLatestBackupContent(driveToken, userEmail)
                ?: return@withContext DriveOperationResult.Error("No backup found in your Google Drive 'Yosan-Backup' folder.")

            val email = userEmail ?: tokenManager.getUserEmail() ?: ""
            val session = if (email.isNotBlank()) tokenManager.getUserSession(email) else null
            val encryptionKey = session?.googleSub?.takeIf { it.isNotBlank() } ?: email.ifBlank { "yosan_secure_drive_backup" }
            val restoreResult = BackupService.restoreBackupJson(jsonContent, database, passphrase = encryptionKey, clearExisting = true)
            if (restoreResult.success) {
                DriveOperationResult.Success(restoreResult, "Google Drive backup restored successfully!")
            } else {
                DriveOperationResult.Error(restoreResult.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct Drive restore failed: ${e.message}", e)
            DriveOperationResult.Error(e.localizedMessage ?: "Failed to restore backup from Google Drive")
        }
    }
}
