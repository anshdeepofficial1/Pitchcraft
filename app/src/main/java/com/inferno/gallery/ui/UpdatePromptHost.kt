package com.inferno.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inferno.gallery.data.network.AppUpdateManager
import com.inferno.gallery.data.network.AppUpdateState
import kotlinx.coroutines.launch

@Composable
fun UpdatePromptHost() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by AppUpdateManager.state.collectAsState()

    LaunchedEffect(Unit) {
        AppUpdateManager.check(context, silent = true)
    }

    when (val current = state) {
        is AppUpdateState.Available -> {
            val info = current.info
            AlertDialog(
                onDismissRequest = { AppUpdateManager.dismissAvailable() },
                title = {
                    Text(
                        "Pitchcraft ${info.version} available",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (info.apkSizeBytes > 0) {
                            Text(
                                "Download size: ${formatUpdateBytes(info.apkSizeBytes)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            info.changelog.ifBlank {
                                "A new Pitchcraft update is available from GitHub Releases."
                            }.take(1800),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (info.apkUrl.isNullOrBlank()) {
                            Text(
                                "This release does not contain a universal APK yet.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch { AppUpdateManager.download(context, info) }
                        },
                        enabled = !info.apkUrl.isNullOrBlank()
                    ) {
                        Text("Download update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { AppUpdateManager.dismissAvailable() }) {
                        Text("Later")
                    }
                }
            )
        }

        is AppUpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Downloading ${current.info.version}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { current.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${(current.progress * 100).toInt()}% • " +
                                "${formatUpdateBytes(current.downloadedBytes)} / " +
                                formatUpdateBytes(current.totalBytes),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "You can keep Pitchcraft open while the APK downloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is AppUpdateState.ReadyToInstall -> {
            AlertDialog(
                onDismissRequest = { AppUpdateManager.dismissAvailable() },
                title = { Text("Update ready to install", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Pitchcraft ${current.info.version} has been downloaded and verified. Android will ask you to confirm the installation."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            AppUpdateManager.install(context, current.file)
                        }
                    ) {
                        Text("Install update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { AppUpdateManager.dismissAvailable() }) {
                        Text("Later")
                    }
                }
            )
        }

        is AppUpdateState.Error -> {
            val info = current.info
            if (info != null) {
                AlertDialog(
                    onDismissRequest = { AppUpdateManager.dismissAvailable() },
                    title = { Text("Update couldn't be installed") },
                    text = { Text(current.message) },
                    confirmButton = {
                        if (!info.apkUrl.isNullOrBlank()) {
                            Button(
                                onClick = {
                                    scope.launch { AppUpdateManager.download(context, info) }
                                }
                            ) {
                                Text("Retry")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { AppUpdateManager.dismissAvailable() }) {
                            Text("Close")
                        }
                    }
                )
            }
        }

        else -> Unit
    }
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) return "Unknown"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 100) "${mb.toInt()} MB" else String.format("%.1f MB", mb)
}
