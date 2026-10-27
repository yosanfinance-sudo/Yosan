package com.example.features.updater

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onDismiss: () -> Unit,
    onStartDownload: (UpdateInfo) -> Unit,
    onInstallApk: (File) -> Unit
) {
    when (state) {
        is UpdateUiState.Idle -> {
            // Nothing to show
        }

        is UpdateUiState.Checking -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp
                    )
                },
                title = { Text("Checking for Updates") },
                text = { Text("Connecting to GitHub to see if a newer version is available...") },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        is UpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("You're Up to Date!") },
                text = {
                    Text("You are using the latest version of Yosan (v${state.currentVersion}). No update is needed at this time.")
                },
                confirmButton = {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                        Text("OK")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        is UpdateUiState.UpdateAvailable -> {
            val info = state.info
            val sizeMb = rememberFormattedSize(info.fileSize)

            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Update Available", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = info.versionName,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        if (sizeMb.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "What's New in this Update:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = sizeMb,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        } else {
                            Text(
                                text = "What's New in this Update:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = info.releaseNotes.ifBlank { "Performance improvements and UI updates." },
                                style = MaterialTheme.typography.bodySmall,
                                lineHeight = 18.sp,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onStartDownload(info) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Update Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Later")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        is UpdateUiState.Downloading -> {
            val pct = (state.progress * 100).toInt()
            val dlMb = rememberFormattedSize(state.downloadedBytes)
            val totMb = rememberFormattedSize(state.totalBytes)

            AlertDialog(
                onDismissRequest = { /* Non-dismissible while downloading */ },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Downloading Update...") },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (state.progress > 0f) {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "$pct%", style = MaterialTheme.typography.labelMedium)
                            if (totMb.isNotBlank()) {
                                Text(
                                    text = "$dlMb / $totMb",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        is UpdateUiState.ReadyToInstall -> {
            LaunchedEffect(state.apkFile) {
                onInstallApk(state.apkFile)
            }

            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Update Ready") },
                text = {
                    Text("The latest update has been downloaded. Click 'Install' to apply the update.")
                },
                confirmButton = {
                    Button(
                        onClick = { onInstallApk(state.apkFile) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Install")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        is UpdateUiState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = { Text("Update Failed") },
                text = { Text(state.message) },
                confirmButton = {
                    Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) {
                        Text("OK")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

private fun rememberFormattedSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes.toDouble() / (1024 * 1024)
    return String.format(Locale.US, "%.1f MB", mb)
}
