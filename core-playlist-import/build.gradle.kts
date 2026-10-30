import java.util.Properties
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sielo.music.core.playlistimport"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        val youtubeApiKey = properties.getProperty("YOUTUBE_API_KEY") ?: ""
        val escapedKey = if (youtubeApiKey.startsWith("\"")) youtubeApiKey else "\"${youtubeApiKey}\""
        buildConfigField("String", "YOUTUBE_API_KEY", escapedKey)

        val spotifyClientId = properties.getProperty("SPOTIFY_CLIENT_ID") ?: ""
        val escapedSpotifyId = if (spotifyClientId.startsWith("\"")) spotifyClientId else "\"${spotifyClientId}\""
        buildConfigField("String", "SPOTIFY_CLIENT_ID", escapedSpotifyId)

        val spotifyClientSecret = properties.getProperty("SPOTIFY_CLIENT_SECRET") ?: ""
        val escapedSpotifySecret = if (spotifyClientSecret.startsWith("\"")) spotifyClientSecret else "\"${spotifyClientSecret}\""
        buildConfigField("String", "SPOTIFY_CLIENT_SECRET", escapedSpotifySecret)
    }
    
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core-network"))
    implementation(project(":core-database"))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
