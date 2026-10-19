package com.sielo.music.core.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.sielo.music.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(
        val progress: Float, // 0.0f to 1.0f
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : UpdateDownloadState()
    data class Completed(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

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
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // Holds the new version string if an update is available, else null
    private val _availableUpdateVersion = MutableStateFlow<String?>(null)
    val availableUpdateVersion: StateFlow<String?> = _availableUpdateVersion.asStateFlow()

    // Controls whether the update popup should automatically display on launch
    private val _showAutoPrompt = MutableStateFlow(false)
    val showAutoPrompt: StateFlow<Boolean> = _showAutoPrompt.asStateFlow()

    // In-app download state
    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    private var activeDownloadJob: Job? = null
    private var detectedApkDownloadUrl: String? = null

    fun getUpdatesDir(): File {
        val dir = File(context.cacheDir, "sielo_updates")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun checkForUpdates() {
        scope.launch {
            try {
                val remoteVersion = fetchLatestVersionFromWebsite() ?: return@launch
                val currentVersion = BuildConfig.VERSION_NAME

                Log.d(TAG, "Current app version: $currentVersion, Latest website version: $remoteVersion")

                if (isNewerVersion(remoteVersion, currentVersion)) {
                    Log.i(TAG, "New version detected: $remoteVersion > $currentVersion")

                    // Check if already downloaded
                    val localApk = File(getUpdatesDir(), "Sielo_$remoteVersion.apk")
                    if (localApk.exists() && localApk.length() > 5 * 1024 * 1024L) {
                        _downloadState.value = UpdateDownloadState.Completed(localApk)
                    } else {
                        _downloadState.value = UpdateDownloadState.Idle
                    }
                    _availableUpdateVersion.value = remoteVersion

                    val now = System.currentTimeMillis()
                    val lastPromptTime = prefs.getLong(KEY_LAST_UPDATE_PROMPT_TIME, 0L)
                    val hasElapsed24Hours = (now - lastPromptTime) >= REMIND_DELAY_MS

                    if (hasElapsed24Hours) {
                        _showAutoPrompt.value = true
                    }
                } else {
                    Log.d(TAG, "App is up to date ($currentVersion >= $remoteVersion)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check for app updates: ${e.message}")
            }
        }
    }

    fun dismissAutoPrompt() {
        _showAutoPrompt.value = false
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
            val directApkMatch = findVersionAndUrlFromContent(htmlBody)
            if (directApkMatch != null) {
                detectedApkDownloadUrl = directApkMatch.second
                return@withContext directApkMatch.first
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

                    val versionFound = findVersionAndUrlFromContent(scriptBody)
                    if (versionFound != null) {
                        detectedApkDownloadUrl = versionFound.second
                        return@withContext versionFound.first
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
     * Extracts (version, directDownloadUrl) from content if an APK link or string is present.
     */
    private fun findVersionAndUrlFromContent(content: String): Pair<String, String>? {
        val apkRegex = Regex("""(?:href=[\"'])?([^\"'\s]*?(?:[Ss]ielo[_-]?)?(\d+\.\d+(?:\.\d+)?)\.apk)[\"']?""")
        val matches = apkRegex.findAll(content).toList()
        if (matches.isEmpty()) return null

        // Find the match with highest version number
        val highest = matches.maxWithOrNull { m1, m2 ->
            compareVersions(m1.groupValues[2], m2.groupValues[2])
        } ?: return null

        val rawUrl = highest.groupValues[1]
        val version = highest.groupValues[2]
        val fullUrl = when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            rawUrl.startsWith("/") -> "$UPDATE_WEBSITE_URL$rawUrl"
            else -> "$UPDATE_WEBSITE_URL/$rawUrl"
        }
        return Pair(version, fullUrl)
    }

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
     * Starts downloading the new APK file directly within the app.
     */
    fun startDownload(version: String) {
        activeDownloadJob?.cancel()
        activeDownloadJob = scope.launch {
            _downloadState.value = UpdateDownloadState.Downloading(progress = 0f, bytesDownloaded = 0L, totalBytes = 0L)

            val dir = getUpdatesDir()
            val targetFile = File(dir, "Sielo_$version.apk")
            val tempFile = File(dir, "Sielo_$version.tmp")

            // Candidate download URLs in priority order
            val candidates = mutableListOf<String>()
            detectedApkDownloadUrl?.let { candidates.add(it) }
            candidates.add("$UPDATE_WEBSITE_URL/Sielo-$version.apk")
            candidates.add("$UPDATE_WEBSITE_URL/Sielo_$version.apk")
            candidates.add("$UPDATE_WEBSITE_URL/$version.apk")
            candidates.add("https://github.com/VineetChudasama/Sielo/releases/download/v$version/Sielo_$version.apk")
            candidates.add("https://github.com/VineetChudasama/Sielo/releases/download/$version/Sielo_$version.apk")
            candidates.add("https://github.com/VineetChudasama/Sielo/releases/download/v$version/Sielo-$version.apk")
            candidates.add("https://github.com/VineetChudasama/Sielo/releases/latest/download/Sielo.apk")

            var downloadSuccess = false
            var lastErrorMessage = "Unable to connect to download server"

            for (url in candidates.distinct()) {
                Log.d(TAG, "Attempting APK download from: $url")
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Sielo-Android-App/${BuildConfig.VERSION_NAME}")
                        .build()

                    val response = httpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        lastErrorMessage = "Server returned ${response.code}"
                        continue
                    }

                    val body = response.body ?: continue
                    val contentLength = body.contentLength()

                    // Ensure not an HTML error or tiny payload
                    if (contentLength > 0 && contentLength < 1024 * 1024L) {
                        lastErrorMessage = "Invalid file payload received"
                        continue
                    }

                    if (tempFile.exists()) tempFile.delete()
                    val outputStream = FileOutputStream(tempFile)
                    val inputStream = body.byteStream()

                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastUpdateTimestamp = 0L

                    inputStream.use { input ->
                        outputStream.use { output ->
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastUpdateTimestamp > 100L || totalBytesRead == contentLength) {
                                    val progress = if (contentLength > 0L) {
                                        (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    _downloadState.value = UpdateDownloadState.Downloading(
                                        progress = progress,
                                        bytesDownloaded = totalBytesRead,
                                        totalBytes = contentLength
                                    )
                                    lastUpdateTimestamp = now
                                }
                            }
                        }
                    }

                    if (tempFile.exists() && tempFile.length() > 5 * 1024 * 1024L) {
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                        _downloadState.value = UpdateDownloadState.Completed(targetFile)
                        downloadSuccess = true
                        Log.i(TAG, "Successfully downloaded update APK (${targetFile.length() / (1024 * 1024)} MB) to ${targetFile.absolutePath}")
                        break
                    } else {
                        tempFile.delete()
                        lastErrorMessage = "Downloaded APK was incomplete or corrupted"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Download attempt failed for $url: ${e.message}")
                    lastErrorMessage = e.localizedMessage ?: "Network error during download"
                    if (tempFile.exists()) tempFile.delete()
                }
            }

            if (!downloadSuccess) {
                _downloadState.value = UpdateDownloadState.Error(lastErrorMessage)
            }
        }
    }

    /**
     * Cancels an ongoing download and cleans up temporary files.
     */
    fun cancelDownload() {
        activeDownloadJob?.cancel()
        activeDownloadJob = null
        try {
            val tempFiles = getUpdatesDir().listFiles { f -> f.name.endsWith(".tmp") }
            tempFiles?.forEach { it.delete() }
        } catch (_: Exception) {}
        _downloadState.value = UpdateDownloadState.Idle
    }

    /**
     * Installs the downloaded APK via Android Package Installer and prompts app refresh.
     */
    fun installAndRestart(activityContext: Context) {
        val state = _downloadState.value
        val apkFile = if (state is UpdateDownloadState.Completed) {
            state.apkFile
        } else {
            val ver = _availableUpdateVersion.value ?: return
            File(getUpdatesDir(), "Sielo_$ver.apk")
        }

        if (!apkFile.exists() || apkFile.length() < 1024 * 1024L) {
            Log.e(TAG, "Cannot install: APK file does not exist or is too small")
            _downloadState.value = UpdateDownloadState.Error("APK file not found. Please download again.")
            return
        }

        // Check Unknown Apps install permission on Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activityContext.packageManager.canRequestPackageInstalls()) {
                try {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activityContext.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activityContext.startActivity(settingsIntent)
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open unknown apps settings: ${e.message}")
                }
            }
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                activityContext,
                "${activityContext.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start package installer: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error("Could not start installer: ${e.message}")
        }
    }

    /**
     * Restarts the application process after update installation.
     */
    fun restartApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            if (intent != null) {
                context.startActivity(intent)
            }
            if (context is Activity) {
                context.finishAffinity()
            }
            Runtime.getRuntime().exit(0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart app: ${e.message}")
        }
    }

    /**
     * User selected "Remind me later" -> closes popup, stores current timestamp
     */
    fun remindMeLater() {
        prefs.edit().putLong(KEY_LAST_UPDATE_PROMPT_TIME, System.currentTimeMillis()).apply()
        _showAutoPrompt.value = false
        cancelDownload()
    }

    /**
     * Opens website in external browser as fallback
     */
    fun openWebsite(activityContext: Context) {
        prefs.edit().putLong(KEY_LAST_UPDATE_PROMPT_TIME, System.currentTimeMillis()).apply()
        _showAutoPrompt.value = false
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
