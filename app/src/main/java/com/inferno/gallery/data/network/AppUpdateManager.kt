package com.inferno.gallery.data.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val info: UpdateInfo) : AppUpdateState
    data class Downloading(
        val info: UpdateInfo,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : AppUpdateState {
        val progress: Float
            get() = if (totalBytes > 0L) {
                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else 0f
    }
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : AppUpdateState
    data class UpToDate(val version: String) : AppUpdateState
    data class Error(val message: String, val info: UpdateInfo? = null) : AppUpdateState
}

object AppUpdateManager {
    private const val UPDATE_DIR = "updates"
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    suspend fun check(context: Context, silent: Boolean = false): UpdateInfo? {
        val app = context.applicationContext
        if (!silent) _state.value = AppUpdateState.Checking

        val currentVersion = runCatching {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "0.0.0"
        }.getOrDefault("0.0.0")

        val info = UpdateChecker.checkForUpdates(currentVersion)
        when {
            info == null -> {
                if (!silent) _state.value = AppUpdateState.Error("Unable to check for updates.")
            }
            info.isAvailable -> {
                _state.value = AppUpdateState.Available(info)
            }
            !silent -> {
                _state.value = AppUpdateState.UpToDate(currentVersion)
            }
        }
        return info
    }

    suspend fun download(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            _state.value = AppUpdateState.Error(
                "This GitHub release does not contain a universal APK asset.",
                info
            )
            return@withContext
        }

        val app = context.applicationContext
        val updateDir = File(app.cacheDir, UPDATE_DIR).apply { mkdirs() }
        updateDir.listFiles()?.forEach { old ->
            if (old.isFile) old.delete()
        }

        val safeVersion = info.normalizedVersion.replace(Regex("[^0-9A-Za-z._-]"), "_")
        val tempFile = File(updateDir, "Pitchcraft-${safeVersion}.download")
        val finalFile = File(updateDir, "Pitchcraft-${safeVersion}.apk")

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "Pitchcraft-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }

                val body = response.body ?: throw IllegalStateException("Empty update response")
                val total = body.contentLength().takeIf { it > 0 } ?: info.apkSizeBytes
                var downloaded = 0L
                val buffer = ByteArray(128 * 1024)
                var lastEmit = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            if (downloaded - lastEmit >= 512 * 1024 || downloaded == total) {
                                lastEmit = downloaded
                                _state.value = AppUpdateState.Downloading(
                                    info = info,
                                    downloadedBytes = downloaded,
                                    totalBytes = total
                                )
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (total > 0 && downloaded != total) {
                    throw IllegalStateException(
                        "Update download is incomplete (${downloaded}/${total} bytes)"
                    )
                }
            }

            if (finalFile.exists()) finalFile.delete()
            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            val verificationError = verifyApk(app, finalFile)
            if (verificationError != null) {
                finalFile.delete()
                _state.value = AppUpdateState.Error(verificationError, info)
                return@withContext
            }

            _state.value = AppUpdateState.ReadyToInstall(info, finalFile)
        } catch (t: Throwable) {
            tempFile.delete()
            finalFile.delete()
            _state.value = AppUpdateState.Error(
                t.message ?: "Update download failed.",
                info
            )
        }
    }

    fun install(context: Context, file: File): Boolean {
        val app = context.applicationContext

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${app.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
            return false
        }

        val uri = FileProvider.getUriForFile(
            app,
            "${app.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        app.startActivity(intent)
        return true
    }

    fun dismissAvailable() {
        _state.value = AppUpdateState.Idle
    }

    fun retryAvailable(info: UpdateInfo) {
        _state.value = AppUpdateState.Available(info)
    }

    private fun verifyApk(context: Context, file: File): String? {
        val pm = context.packageManager
        val flags = android.content.pm.PackageManager.PackageInfoFlags.of(
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        )

        val archive = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(
                file.absolutePath,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
        } ?: return "Downloaded file is not a valid Android APK."

        if (archive.packageName != context.packageName) {
            return "Update package does not match this Pitchcraft installation (${archive.packageName})."
        }

        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, flags)
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
        }

        if (archive.longVersionCode <= installed.longVersionCode) {
            return "Downloaded APK is not newer than the installed version."
        }

        val installedCert = installed.signingInfo?.apkContentsSigners
            ?.firstOrNull()?.toByteArray()?.let(::sha256)
        val archiveCert = archive.signingInfo?.apkContentsSigners
            ?.firstOrNull()?.toByteArray()?.let(::sha256)

        if (installedCert == null || archiveCert == null || installedCert != archiveCert) {
            return "Update signature does not match the installed Pitchcraft app."
        }

        return null
    }

    private fun sha256(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
