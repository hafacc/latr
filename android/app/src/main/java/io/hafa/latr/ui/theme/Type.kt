package io.hafa.latr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.hafa.latr.R

@OptIn(ExperimentalTextApi::class)
private fun geist(weight: FontWeight) = Font(
    R.font.geist,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Geist = FontFamily(
    geist(FontWeight.Normal),
    geist(FontWeight.Medium),
    geist(FontWeight.SemiBold),
)

private val base = Typography()

private fun TextStyle.geist() = copy(fontFamily = Geist)

val Typography = Typography(
    displayLarge = base.displayLarge.geist(),
    displayMedium = base.displayMedium.geist(),
    displaySmall = base.displaySmall.geist(),
    headlineLarge = base.headlineLarge.geist(),
    headlineMedium = base.headlineMedium.geist(),
    headlineSmall = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.01).em,
    ),
    titleLarge = base.titleLarge.geist().copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.geist().copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.geist(),
    bodyLarge = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    ),
    bodySmall = base.bodySmall.geist(),
    labelLarge = base.labelLarge.geist(),
    labelMedium = base.labelMedium.geist(),
    labelSmall = base.labelSmall.geist(),
)
