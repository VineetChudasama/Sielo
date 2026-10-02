package com.sielo.music.core.network.innertube

/**
 * Utility for resolving appropriate User-Agent, Origin, and Referer headers
 * based on client identifiers embedded in YouTube stream URLs (`c` query parameter).
 * Adopted from Velune's stream client architecture to prevent HTTP 403 Forbidden errors.
 */
object StreamClientUtils {

    const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    const val USER_AGENT_ANDROID = "com.google.android.youtube/21.10.38 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip"
    const val USER_AGENT_ANDROID_MUSIC = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip"
    const val USER_AGENT_IOS = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"

    fun resolveUserAgent(clientParam: String): String {
        val c = clientParam.trim()
        return when {
            c.equals("WEB_REMIX", ignoreCase = true) ||
                c.equals("WEB", ignoreCase = true) ||
                c.equals("WEB_CREATOR", ignoreCase = true) -> USER_AGENT_WEB

            c.equals("TVHTML5", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY_EMBEDDED_PLAYER", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY", ignoreCase = true) ->
                "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15"

            c.equals("IOS_MUSIC", ignoreCase = true) ->
                "com.google.ios.youtubemusic/7.27.0 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)"

            c.startsWith("IOS", ignoreCase = true) -> USER_AGENT_IOS

            c.startsWith("ANDROID_VR", ignoreCase = true) ->
                "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)"

            c.equals("ANDROID_MUSIC", ignoreCase = true) -> USER_AGENT_ANDROID_MUSIC

            c.startsWith("ANDROID", ignoreCase = true) -> USER_AGENT_ANDROID

            else -> USER_AGENT_ANDROID
        }
    }

    data class OriginReferer(val origin: String?, val referer: String?)

    fun resolveOriginReferer(clientParam: String): OriginReferer {
        val c = clientParam.trim()
        return when {
            c.equals("WEB_REMIX", ignoreCase = true) ||
                c.equals("WEB", ignoreCase = true) ||
                c.equals("WEB_CREATOR", ignoreCase = true) ->
                OriginReferer("https://music.youtube.com", "https://music.youtube.com/")

            c.equals("TVHTML5", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY_EMBEDDED_PLAYER", ignoreCase = true) ||
                c.equals("TVHTML5_SIMPLY", ignoreCase = true) ->
                OriginReferer("https://www.youtube.com", "https://www.youtube.com/tv")

            else -> OriginReferer(null, null)
        }
    }
}
