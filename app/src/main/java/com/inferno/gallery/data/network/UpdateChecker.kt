package com.inferno.gallery.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class UpdateInfo(
    val isAvailable: Boolean,
    val version: String,
    val normalizedVersion: String,
    val releasePageUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSizeBytes: Long,
    val changelog: String,
    val publishedAt: String?
)

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/anshdeepofficial1/Pitchcraft/releases/latest"

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun checkForUpdates(currentVersion: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(GITHUB_LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "Pitchcraft-Android")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "GitHub update check failed: HTTP ${response.code}")
                        return@withContext null
                    }

                    val responseBody = response.body?.string() ?: return@withContext null
                    val json = JSONObject(responseBody)

                    val tagName = json.optString("tag_name").ifBlank { return@withContext null }
                    val latestVersion = normalizeVersion(tagName)
                    val current = normalizeVersion(currentVersion)
                    val releasePage = json.optString("html_url")
                    val changelog = json.optString("body").trim()
                    val publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }

                    val assets = json.optJSONArray("assets")
                    var bestName: String? = null
                    var bestUrl: String? = null
                    var bestSize = 0L
                    var bestScore = Int.MIN_VALUE

                    if (assets != null) {
                        for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            val name = asset.optString("name")
                            val url = asset.optString("browser_download_url")
                            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue

                            val lower = name.lowercase()
                            var score = 0
                            if ("pitchcraft" in lower) score += 20
                            if ("universal" in lower) score += 30
                            if ("release" in lower) score += 10
                            if ("debug" in lower) score -= 5
                            if ("arm64" in lower || "x86" in lower || "armeabi" in lower) score -= 20

                            if (score > bestScore) {
                                bestScore = score
                                bestName = name
                                bestUrl = url
                                bestSize = asset.optLong("size", 0L)
                            }
                        }
                    }

                    UpdateInfo(
                        isAvailable = isNewerVersion(current, latestVersion),
                        version = tagName,
                        normalizedVersion = latestVersion,
                        releasePageUrl = releasePage,
                        apkUrl = bestUrl,
                        apkName = bestName,
                        apkSizeBytes = bestSize,
                        changelog = changelog,
                        publishedAt = publishedAt
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check for updates", e)
                null
            }
        }

    private fun normalizeVersion(version: String): String {
        return version.trim()
            .removePrefix("v")
            .substringBefore("-")
            .substringBefore("+")
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }

        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val c = currentParts.getOrElse(i) { 0 }
            val l = latestParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
