package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class Available(val release: GitHubRelease) : UpdateState
    object UpToDate : UpdateState
    data class Error(val message: String) : UpdateState
}

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val downloadUrl: String?,
    val changelog: String,
    val publishedAt: String,
    val htmlUrl: String,
    val isNewerVersion: Boolean
)

class GitHubUpdateService(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Default repository (configurable by user in app settings)
    var repoOwner: String = "kingdomedantech"
    var repoName: String = "scalpsignal-app"

    suspend fun checkLatestRelease(customOwner: String? = null, customRepo: String? = null): GitHubRelease? = withContext(Dispatchers.IO) {
        val owner = customOwner?.trim()?.ifEmpty { null } ?: repoOwner
        val repo = customRepo?.trim()?.ifEmpty { null } ?: repoName

        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "ScalpSignal-App/1.0")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val jsonString = response.body?.string() ?: return@withContext null
                val json = JSONObject(jsonString)

                val tagName = json.optString("tag_name", "v1.0")
                val name = json.optString("name", tagName)
                val body = json.optString("body", "Pembaruan stabilitas dan fitur scalping terbaru.")
                val publishedAt = json.optString("published_at", "")
                val htmlUrl = json.optString("html_url", "https://github.com/$owner/$repo/releases")

                // Find APK asset download URL
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url", null)
                            break
                        }
                    }
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val isNewer = isVersionGreater(tagName, currentVersion)

                return@withContext GitHubRelease(
                    tagName = tagName,
                    name = name,
                    downloadUrl = apkUrl ?: htmlUrl,
                    changelog = body,
                    publishedAt = publishedAt,
                    htmlUrl = htmlUrl,
                    isNewerVersion = isNewer
                )
            }
        } catch (_: Exception) {
            return@withContext null
        }
    }

    private fun isVersionGreater(newVer: String, currentVer: String): Boolean {
        val cleanNew = newVer.replace("v", "", ignoreCase = true).trim()
        val cleanCurrent = currentVer.replace("v", "", ignoreCase = true).trim()
        if (cleanNew == cleanCurrent) return false

        val newParts = cleanNew.split(".").mapNotNull { it.toIntOrNull() }
        val currParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(newParts.size, currParts.size)
        for (i in 0 until maxLen) {
            val n = newParts.getOrElse(i) { 0 }
            val c = currParts.getOrElse(i) { 0 }
            if (n > c) return true
            if (n < c) return false
        }
        return false
    }

    fun openDownloadUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
