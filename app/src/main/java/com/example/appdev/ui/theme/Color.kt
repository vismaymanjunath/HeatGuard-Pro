package com.example.appdev.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.appdev.domain.thermal.TemperatureLevel

/** HeatGuard Pro palette: deep neutral surfaces, a cool accent, and three status colours. */
object HeatColors {
    val Background = Color(0xFF0A0E13)
    val Surface = Color(0xFF11161D)
    val SurfaceRaised = Color(0xFF18202A)
    val SurfaceHighest = Color(0xFF202A36)
    val Outline = Color(0xFF2A3644)
    val OutlineSubtle = Color(0xFF1E2833)

    val TextPrimary = Color(0xFFE8EDF3)
    val TextSecondary = Color(0xFF8E9AAA)
    val TextTertiary = Color(0xFF7D8999) // >= 4.5:1 on Surface (WCAG AA)

    val Accent = Color(0xFF5EC4FF)
    val OnAccent = Color(0xFF00131F)

    val Normal = Color(0xFF2FD39A)
    val Elevated = Color(0xFFFFA63D)
    val High = Color(0xFFFF7A45)
    val Critical = Color(0xFFFF5A5F)
    val Unknown = Color(0xFF5B6675)
}

fun TemperatureLevel.color(): Color = when (this) {
    TemperatureLevel.NORMAL -> HeatColors.Normal
    TemperatureLevel.ELEVATED -> HeatColors.Elevated
    TemperatureLevel.CRITICAL -> HeatColors.Critical
    TemperatureLevel.UNKNOWN -> HeatColors.Unknown
}

fun com.example.appdev.domain.thermal.RiskLevel.color(): Color = when (this) {
    com.example.appdev.domain.thermal.RiskLevel.LOW -> HeatColors.Normal
    com.example.appdev.domain.thermal.RiskLevel.MODERATE -> HeatColors.Elevated
    com.example.appdev.domain.thermal.RiskLevel.HIGH -> HeatColors.High
    com.example.appdev.domain.thermal.RiskLevel.CRITICAL -> HeatColors.Critical
}
