package com.ronaldcolocho.taskly.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ronaldcolocho.taskly.R

// ── Bricolage Grotesque (downloadable font via Google Fonts) ───
// The XML descriptor lives at res/font/bricolage_grotesque.xml.
// At runtime Android downloads the font from Google Play Services.
val BricolageGrotesque = FontFamily(
    Font(R.font.bricolage_grotesque, FontWeight.Normal),
    Font(R.font.bricolage_grotesque, FontWeight.Medium),
    Font(R.font.bricolage_grotesque, FontWeight.SemiBold),
    Font(R.font.bricolage_grotesque, FontWeight.Bold),
    Font(R.font.bricolage_grotesque, FontWeight.ExtraBold)
)

// ── App-wide typography ────────────────────────────────────────
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    )
)

// ── Marea Player Typography tokens ────────────────────────────
// These are standalone TextStyle values used only inside the player
// composable so they don't pollute the global Material3 typography.

/** Song title: 34sp ExtraBold, tight line-height & letter-spacing */
val MareaTitleStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 34.sp,
    lineHeight = 34.68.sp,      // ≈ 1.02 × 34
    letterSpacing = (-0.68).sp  // ≈ -0.02em at 34sp
)

/** Artist name: 16sp regular */
val MareaArtistStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/** Time labels: 13sp, tabular figures via FontFeatureSettings */
val MareaTimeStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = "\"tnum\" on"
)

/** Queue rows: song title 16sp medium */
val MareaQueueTitleStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.Medium,
    fontSize = 16.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp
)

/** Queue rows: subtitle 13sp */
val MareaQueueSubtitleStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.sp
)

/** "Sonando de" label: 14sp */
val MareaContextStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

/** Queue section title: 20sp ExtraBold */
val MareaQueueHeaderStyle = TextStyle(
    fontFamily = BricolageGrotesque,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 20.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp
)
