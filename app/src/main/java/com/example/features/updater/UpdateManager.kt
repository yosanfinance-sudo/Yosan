package com.example.features.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val fileName: String,
    val fileSize: Long,
    val isNewer: Boolean
)

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateUiState()
    data class UpToDate(val currentVersion: String) : UpdateUiState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

class UpdateManager(
    private val context: Context,
    private val repoOwner: String = "ROOPARAJB",
    private val repoName: String = "Yosan"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    fun resetState() {
        _uiState.value = UpdateUiState.Idle
    }

    suspend fun checkForUpdates(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        _uiState.value = UpdateUiState.Checking
        try {
            var candidateRelease: UpdateInfo? = null
            var apiSuccess = false

            // Strategy 1: Fetch list of recent releases from GitHub API
            // Querying /releases returns all recent releases so we select the one with the highest semantic version,
            // avoiding GitHub /releases/latest's strict publication-timestamp bias.
            val listApiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=20"
            val listRequest = Request.Builder()
                .url(listApiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "Yosan-Android-App")
                .build()

            try {
                client.newCall(listRequest).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val jsonArray = JSONArray(bodyStr)
                        val releasesList = mutableListOf<UpdateInfo>()

                        for (i in 0 until jsonArray.length()) {
                            val releaseObj = jsonArray.getJSONObject(i)
                            if (releaseObj.optBoolean("draft", false)) continue

                            val tag = releaseObj.optString("tag_name", "").trim()
                            if (tag.isBlank()) continue

                            val title = releaseObj.optString("name", tag)
                            val notes = releaseObj.optString("body", "")
                            val cleanTag = tag.removePrefix("v").removePrefix("V")
                            var apkUrl = ""
                            var apkName = "yosan-$cleanTag.apk"
                            var apkSize = 0L

                            val assets = releaseObj.optJSONArray("assets")
                            if (assets != null) {
                                for (j in 0 until assets.length()) {
                                    val asset = assets.getJSONObject(j)
                                    val name = asset.optString("name", "")
                                    if (name.endsWith(".apk", ignoreCase = true)) {
                                        apkName = name
                                        apkUrl = asset.optString("browser_download_url", "")
                                        apkSize = asset.optLong("size", 0L)
                                        break
                                    }
                                }
                            }
                            if (apkUrl.isBlank()) {
                                apkUrl = "https://github.com/$repoOwner/$repoName/releases/download/$tag/$apkName"
                            }

                            releasesList.add(
                                UpdateInfo(
                                    versionName = tag,
                                    releaseTitle = title,
                                    releaseNotes = notes,
                                    downloadUrl = apkUrl,
                                    fileName = apkName,
                                    fileSize = apkSize,
                                    isNewer = false
                                )
                            )
                        }

                        if (releasesList.isNotEmpty()) {
                            candidateRelease = releasesList.maxWithOrNull { a, b ->
                                compareVersions(a.versionName, b.versionName)
                            }
                            apiSuccess = candidateRelease != null
                        }
                    }
                }
            } catch (_: Exception) {}

            // Strategy 2: If /releases list failed, query /releases/latest as secondary API attempt
            if (!apiSuccess || candidateRelease == null) {
                val latestApiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
                val latestRequest = Request.Builder()
                    .url(latestApiUrl)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "Yosan-Android-App")
                    .build()

                try {
                    client.newCall(latestRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            val json = JSONObject(bodyStr)
                            val tag = json.optString("tag_name", "").trim()
                            if (tag.isNotBlank()) {
                                val cleanTag = tag.removePrefix("v").removePrefix("V")
                                val title = json.optString("name", tag)
                                val notes = json.optString("body", "")
                                var apkUrl = ""
                                var apkName = "yosan-$cleanTag.apk"
                                var apkSize = 0L

                                val assets = json.optJSONArray("assets")
                                if (assets != null) {
                                    for (i in 0 until assets.length()) {
                                        val asset = assets.getJSONObject(i)
                                        val name = asset.optString("name", "")
                                        if (name.endsWith(".apk", ignoreCase = true)) {
                                            apkName = name
                                            apkUrl = asset.optString("browser_download_url", "")
                                            apkSize = asset.optLong("size", 0L)
                                            break
                                        }
                                    }
                                }
                                if (apkUrl.isBlank()) {
                                    apkUrl = "https://github.com/$repoOwner/$repoName/releases/download/$tag/$apkName"
                                }

                                candidateRelease = UpdateInfo(
                                    versionName = tag,
                                    releaseTitle = title,
                                    releaseNotes = notes,
                                    downloadUrl = apkUrl,
                                    fileName = apkName,
                                    fileSize = apkSize,
                                    isNewer = false
                                )
                                apiSuccess = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Strategy 3: HTML fallback - scrape releases page if API was rate-limited (HTTP 403)
            if (!apiSuccess || candidateRelease == null) {
                val releasesWebUrl = "https://github.com/$repoOwner/$repoName/releases"
                val webRequest = Request.Builder()
                    .url(releasesWebUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                try {
                    client.newCall(webRequest).execute().use { webResponse ->
                        if (webResponse.isSuccessful) {
                            val html = webResponse.body?.string() ?: ""
                            val tagRegex = Regex("""/releases/tag/([vV]?[0-9]+(?:\.[0-9]+)+)""")
                            val foundTags = tagRegex.findAll(html)
                                .map { it.groupValues[1] }
                                .distinct()
                                .toList()

                            val highestTag = foundTags.maxWithOrNull { a, b ->
                                compareVersions(a, b)
                            }

                            if (highestTag != null && highestTag.isNotBlank()) {
                                val cleanTag = highestTag.removePrefix("v").removePrefix("V")
                                val apkName = "yosan-$cleanTag.apk"
                                candidateRelease = UpdateInfo(
                                    versionName = highestTag,
                                    releaseTitle = "Release $highestTag",
                                    releaseNotes = "New updates and stability improvements for $highestTag.",
                                    downloadUrl = "https://github.com/$repoOwner/$repoName/releases/download/$highestTag/$apkName",
                                    fileName = apkName,
                                    fileSize = 0L,
                                    isNewer = false
                                )
                                apiSuccess = true
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // Strategy 4: Web redirect fallback to /releases/latest
            if (!apiSuccess || candidateRelease == null) {
                val latestWebUrl = "https://github.com/$repoOwner/$repoName/releases/latest"
                val latestWebRequest = Request.Builder()
                    .url(latestWebUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()

                try {
                    client.newCall(latestWebRequest).execute().use { webResponse ->
                        val finalUrl = webResponse.request.url.toString()
                        val pathSegments = webResponse.request.url.pathSegments
                        val tagIndex = pathSegments.indexOf("tag")
                        var tag = ""
                        if (tagIndex != -1 && tagIndex + 1 < pathSegments.size) {
                            tag = pathSegments[tagIndex + 1]
                        } else if (finalUrl.contains("/tag/")) {
                            tag = finalUrl.substringAfterLast("/tag/")
                        }

                        if (tag.isNotBlank()) {
                            val cleanTag = tag.removePrefix("v").removePrefix("V")
                            val apkName = "yosan-$cleanTag.apk"
                            candidateRelease = UpdateInfo(
                                versionName = tag,
                                releaseTitle = "Release $tag",
                                releaseNotes = "New updates and stability improvements for $tag.",
                                downloadUrl = "https://github.com/$repoOwner/$repoName/releases/download/$tag/$apkName",
                                fileName = apkName,
                                fileSize = 0L,
                                isNewer = false
                            )
                            apiSuccess = true
                        }
                    }
                } catch (_: Exception) {}
            }

            val targetRelease = candidateRelease
            if (!apiSuccess || targetRelease == null) {
                val state = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                _uiState.value = state
                return@withContext Result.success(null)
            }

            val cleanRemoteVersion = targetRelease.versionName.removePrefix("v").removePrefix("V").trim()
            val cleanCurrentVersion = BuildConfig.VERSION_NAME.removePrefix("v").removePrefix("V").trim()

            val isNewer = isRemoteVersionNewer(cleanRemoteVersion, cleanCurrentVersion)

            if (isNewer && targetRelease.downloadUrl.isNotBlank()) {
                val finalInfo = targetRelease.copy(
                    isNewer = true,
                    releaseNotes = targetRelease.releaseNotes.ifBlank {
                        "What's New in ${targetRelease.versionName}:\n• Automated update and release pipeline\n• Security & UI stability improvements\n• Bug fixes and performance enhancements"
                    }
                )
                _uiState.value = UpdateUiState.UpdateAvailable(finalInfo)
                showSystemUpdateNotification(finalInfo)
                Result.success(finalInfo)
            } else {
                _uiState.value = UpdateUiState.UpToDate(BuildConfig.VERSION_NAME)
                Result.success(null)
            }
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to check for updates"
            _uiState.value = UpdateUiState.Error(errMsg)
            Result.failure(e)
        }
    }

    suspend fun downloadAndPrepareApk(updateInfo: UpdateInfo): Result<File> = withContext(Dispatchers.IO) {
        _uiState.value = UpdateUiState.Downloading(0f, 0L, updateInfo.fileSize)
        try {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, updateInfo.fileName.ifBlank { "yosan-update.apk" })
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "Yosan-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val msg = "Download failed with HTTP ${response.code}"
                    _uiState.value = UpdateUiState.Error(msg)
                    return@withContext Result.failure(Exception(msg))
                }

                val responseBody = response.body ?: throw Exception("Empty response body")
                val responseContentLength = responseBody.contentLength()
                val totalLength = if (responseContentLength > 0) responseContentLength else updateInfo.fileSize
                var bytesDownloaded = 0L

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var lastProgressUpdate = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            bytesDownloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 100 || (totalLength > 0 && bytesDownloaded == totalLength)) {
                                lastProgressUpdate = now
                                val progress = if (totalLength > 0) {
                                    (bytesDownloaded.toFloat() / totalLength).coerceIn(0f, 1f)
                                } else 0f
                                _uiState.value = UpdateUiState.Downloading(progress, bytesDownloaded, totalLength)
                            }
                        }
                        outputStream.flush()
                    }
                }
                if (bytesDownloaded == 0L) {
                    apkFile.delete()
                    val msg = "Download failed: received 0 bytes."
                    _uiState.value = UpdateUiState.Error(msg)
                    return@withContext Result.failure(Exception(msg))
                }
                if (totalLength > 0 && bytesDownloaded < totalLength) {
                    apkFile.delete()
                    val msg = "Download incomplete: expected $totalLength bytes, received $bytesDownloaded bytes."
                    _uiState.value = UpdateUiState.Error(msg)
                    return@withContext Result.failure(Exception(msg))
                }
            }

            _uiState.value = UpdateUiState.ReadyToInstall(apkFile)
            Result.success(apkFile)
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Download failed"
            _uiState.value = UpdateUiState.Error(errMsg)
            Result.failure(e)
        }
    }

    fun installApk(apkFile: File) {
        try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                _uiState.value = UpdateUiState.Error("APK file is invalid or missing.")
                return
            }

            val isFromPlayStore = try {
                val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getInstallerPackageName(context.packageName)
                }
                installer == "com.android.vending"
            } catch (_: Exception) {
                false
            }

            if (isFromPlayStore) {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(playIntent)
                return
            }

            // Android 8.0+ unknown sources permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val canInstall = try {
                    context.packageManager.canRequestPackageInstalls()
                } catch (_: SecurityException) {
                    false
                }

                if (!canInstall) {
                    try {
                        val settingsIntent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(settingsIntent)
                    } catch (_: Exception) {
                        try {
                            val genericSettingsIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(genericSettingsIntent)
                        } catch (_: Exception) {}
                    }
                    return
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }

            // Grant explicit URI read permission to all matching package installer handlers
            try {
                val resInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.queryIntentActivities(
                        installIntent,
                        android.content.pm.PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.queryIntentActivities(installIntent, 0)
                }
                for (resolveInfo in resInfoList) {
                    val pkgName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(pkgName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: Exception) {}

            context.startActivity(installIntent)
        } catch (e: Exception) {
            _uiState.value = UpdateUiState.Error("Failed to launch installer: ${e.localizedMessage}")
        }
    }

    fun showSystemUpdateNotification(info: UpdateInfo) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val permission = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
                if (permission != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val channelId = "yosan_updates_channel"
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Yosan App Updates",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when a newer app update is available."
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_OPEN_UPDATE_DIALOG", true)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                1001,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) android.app.PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("You got an update!")
                .setContentText("Yosan ${info.versionName} is available. Tap to review and install.")
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("Yosan ${info.versionName} is now available!\n${info.releaseTitle}\nTap to update now."))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(1001, notification)
        } catch (e: Exception) {
            Log.e("UpdateManager", "showSystemUpdateNotification failed: ${e.message}", e)
        }
    }

    companion object {
        fun parseVersionParts(version: String): List<Int> {
            return version.removePrefix("v").removePrefix("V").trim()
                .split(".")
                .mapNotNull { part -> part.filter { it.isDigit() }.toIntOrNull() }
        }

        fun compareVersions(v1: String, v2: String): Int {
            val p1 = parseVersionParts(v1)
            val p2 = parseVersionParts(v2)
            val maxLen = maxOf(p1.size, p2.size)
            for (i in 0 until maxLen) {
                val n1 = p1.getOrElse(i) { 0 }
                val n2 = p2.getOrElse(i) { 0 }
                if (n1 != n2) return n1.compareTo(n2)
            }
            return 0
        }

        fun isRemoteVersionNewer(remote: String, local: String): Boolean {
            return compareVersions(remote, local) > 0
        }
    }
}
