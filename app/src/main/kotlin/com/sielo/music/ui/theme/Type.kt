@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.sielo.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sielo.music.R

val MacondoFontFamily = FontFamily(
    Font(R.font.macondo_regular, FontWeight.Normal),
    Font(R.font.macondo_regular, FontWeight.Bold),
    Font(R.font.macondo_regular, FontWeight.SemiBold)
)

val SoraFontFamily = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_regular, FontWeight.W400),
    Font(R.font.sora_semibold, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.W500),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_semibold, FontWeight.W600),
    Font(R.font.sora_bold, FontWeight.Bold),
    Font(R.font.sora_bold, FontWeight.W700),
    Font(R.font.sora_bold, FontWeight.W800),
    Font(R.font.sora_bold, FontWeight.W900)
)

val UrbanistFontFamily = FontFamily(
    Font(R.font.urbanist_regular, FontWeight.Normal),
    Font(R.font.urbanist_regular, FontWeight.W400),
    Font(R.font.urbanist_medium, FontWeight.Medium),
    Font(R.font.urbanist_medium, FontWeight.W500),
    Font(R.font.urbanist_semibold, FontWeight.SemiBold),
    Font(R.font.urbanist_semibold, FontWeight.W600),
    Font(R.font.urbanist_bold, FontWeight.Bold),
    Font(R.font.urbanist_bold, FontWeight.W700),
    Font(R.font.urbanist_bold, FontWeight.W800),
    Font(R.font.urbanist_bold, FontWeight.W900)
)

val SieloTypography = Typography(
    // Headings & Large Stats: Sora 600–700
    displayLarge = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp
    ),
    displayMedium = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp
    ),
    displaySmall = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SoraFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),

    // Song titles: Urbanist 600
    titleLarge = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    titleSmall = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),

    // UI & Metadata: Urbanist 400–600
    bodyLarge = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    labelMedium = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 17.sp
    ),
    labelSmall = TextStyle(
        fontFamily = UrbanistFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 15.sp
    )
)
