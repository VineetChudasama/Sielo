package com.sielo.music.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.sielo.music.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AppUpdateManager"
        const val UPDATE_WEBSITE_URL = "https://www.sielo.site"
        private const val PREFS_NAME = "sielo_app_update_prefs"
        private const val KEY_LAST_UPDATE_PROMPT_TIME = "last_update_prompt_time"
        private const val REMIND_DELAY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Holds the new version string if an update popup should be displayed, else null
    private val _availableUpdateVersion = MutableStateFlow<String?>(null)
    val availableUpdateVersion: StateFlow<String?> = _availableUpdateVersion.asStateFlow()

    fun checkForUpdates() {
        scope.launch {
            try {
                val now = System.currentTimeMillis()
                val lastPromptTime = prefs.getLong(KEY_LAST_UPDATE_PROMPT_TIME, 0L)
                val hasElapsed24Hours = (now - lastPromptTime) >= REMIND_DELAY_MS

                if (!hasElapsed24Hours) {
                    Log.d(TAG, "Update check skipped: 24hr throttle active. Next prompt after ${((REMIND_DELAY_MS - (now - lastPromptTime)) / 3600000)}h")
                    return@launch
                }

                val remoteVersion = fetchLatestVersionFromWebsite() ?: return@launch
                val currentVersion = BuildConfig.VERSION_NAME

                Log.d(TAG, "Current app version: $currentVersion, Latest website version: $remoteVersion")

                if (isNewerVersion(remoteVersion, currentVersion)) {
                    Log.i(TAG, "New version detected: $remoteVersion > $currentVersion")
                    _availableUpdateVersion.value = remoteVersion
                } else {
                    Log.d(TAG, "App is up to date ($currentVersion >= $remoteVersion)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check for app updates: ${e.message}")
            }
        }
    }

    private suspend fun fetchLatestVersionFromWebsite(): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch index HTML
            val htmlReq = Request.Builder()
                .url(UPDATE_WEBSITE_URL)
                .header("User-Agent", "Sielo-Android-App/${BuildConfig.VERSION_NAME}")
                .build()

            val htmlResp = httpClient.newCall(htmlReq).execute()
            val htmlBody = htmlResp.body?.string().orEmpty()

            // Check if HTML directly mentions an apk pattern
            val directApkMatch = findVersionFromApkStrings(htmlBody)
            if (directApkMatch != null) {
                return@withContext directApkMatch
            }

            // 2. Find scripts bundled in the SPA (e.g. /assets/index-*.js)
            val scriptRegex = Regex("""/assets/[a-zA-Z0-9_\.-]+\.js""")
            val scriptMatches = scriptRegex.findAll(htmlBody).map { it.value }.distinct().toList()

            for (scriptPath in scriptMatches) {
                val scriptUrl = if (scriptPath.startsWith("http")) scriptPath else "$UPDATE_WEBSITE_URL$scriptPath"
                try {
                    val scriptReq = Request.Builder()
                        .url(scriptUrl)
                        .header("User-Agent", "Sielo-Android-App/${BuildConfig.VERSION_NAME}")
                        .build()
                    val scriptResp = httpClient.newCall(scriptReq).execute()
                    val scriptBody = scriptResp.body?.string().orEmpty()

                    val versionFound = findVersionFromApkStrings(scriptBody)
                    if (versionFound != null) {
                        return@withContext versionFound
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed fetching script $scriptUrl: ${e.message}")
                }
            }

            null
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching website content: ${e.message}")
            null
        }
    }

    /**
     * Extracts version numbers from APK filenames such as Sielo_10.2.0.apk, Sielo-10.3.5.apk, or 10.4.0.apk
     */
    private fun findVersionFromApkStrings(content: String): String? {
        val apkRegex = Regex("""(?:[Ss]ielo[_-]?)?(\d+\.\d+(?:\.\d+)?)\.apk""")
        val matches = apkRegex.findAll(content).map { it.groupValues[1] }.toList()
        if (matches.isEmpty()) return null

        // Return the highest version among matches
        return matches.maxWithOrNull { v1, v2 -> compareVersions(v1, v2) }
    }

    /**
     * Returns true if remote is strictly newer than current (e.g. 10.3.5 > 10.3.4)
     */
    fun isNewerVersion(remote: String, current: String): Boolean {
        return compareVersions(remote, current) > 0
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    /**
     * User selected "Remind me later" -> closes popup, stores current timestamp
     */
    fun remindMeLater() {
        prefs.edit().putLong(KEY_LAST_UPDATE_PROMPT_TIME, System.currentTimeMillis()).apply()
        _availableUpdateVersion.value = null
    }

    /**
     * User selected "Update now" -> launches website in browser and closes popup
     */
    fun updateNow(activityContext: Context) {
        prefs.edit().putLong(KEY_LAST_UPDATE_PROMPT_TIME, System.currentTimeMillis()).apply()
        _availableUpdateVersion.value = null
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_WEBSITE_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch update URL: ${e.message}")
        }
    }
}
