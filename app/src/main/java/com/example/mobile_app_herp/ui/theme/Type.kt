package com.example.mobile_app_herp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Three voices, all from fonts already on the device — nothing to ship, nothing
 * to download, and no first-frame flash while a webfont resolves.
 *
 *   Condensed  — signage. Titles and labels, tight and often uppercase.
 *   Roboto     — reading. Anything a person wrote; never shouted.
 *   Monospace  — record. Ticket ids, dates, hostnames: things you read out or
 *                compare character by character.
 *
 * Roboto Condensed has shipped on Android since 4.1. On a device that somehow
 * lacks it, Compose falls back to the default sans and the layout still holds —
 * condensed is doing tone here, not fitting text into a fixed width.
 */
private const val CONDENSED = "sans-serif-condensed"

val Condensed = FontFamily(
    Font(DeviceFontFamilyName(CONDENSED), FontWeight.Normal),
    Font(DeviceFontFamilyName(CONDENSED), FontWeight.Medium),
    Font(DeviceFontFamilyName(CONDENSED), FontWeight.Bold),
)

val Mono = FontFamily.Monospace

/**
 * Roles beyond Material's scale. Kept here rather than bent into `Typography`
 * because an eyebrow is not a `labelSmall` with extra tracking — it is its own
 * thing, and naming it that way keeps call sites readable.
 */
object HerpType {
    /** Screen titles. Tight tracking: condensed caps sprawl without it. */
    val Display = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.4).sp,
    )

    /** The small uppercase label above a title. Says where you are. */
    val Eyebrow = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    )

    /** Card and section headings. */
    val Title = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp,
    )

    /** A status, set like something stamped on a docket. */
    val Stamp = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.4.sp,
    )

    /** Ticket ids and dates. */
    val Record = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    )

    /** The workspace address on the login screen — the one big mono moment. */
    val Address = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Buttons. Condensed caps, so an action reads as an instruction. */
    val Action = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.0.sp,
    )
}

val Typography = Typography(
    // What people wrote stays in plain Roboto at a comfortable size.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    titleMedium = HerpType.Title,
    titleSmall = HerpType.Title.copy(fontSize = 16.sp, lineHeight = 20.sp),
    headlineSmall = HerpType.Display,
    labelLarge = HerpType.Action,
    labelMedium = HerpType.Record,
    labelSmall = HerpType.Record.copy(fontSize = 11.sp),
)
