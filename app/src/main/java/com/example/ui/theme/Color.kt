package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core Obsidian Surfaces
val ObsidianBg = Color(0xFF0B0D12)
val ObsidianCard = Color(0xFF14171F)
val ObsidianCardElevated = Color(0xFF1C202B)
val BorderSubtle = Color(0xFF2A2F3D)
val BorderAmberGlow = Color(0x66FF9E00)

// Primary Neon Amber, Orange & Crimson Accents
val PikaYellow = Color(0xFFFFD60A)
val PikaYellowDark = Color(0xFFF59E0B)
val PikaCheekRed = Color(0xFFFF3B30)
val NeonAmber = Color(0xFFFF9F1C)
val NeonOrange = Color(0xFFFF5400)
val NeonCoral = Color(0xFFFF2A54)

// Legacy & Telemetry Tokens (Kept intact so no other files break)
val HunterGold = Color(0xFFFFB703)
val HunterCyan = Color(0xFF00F5D4)
val HunterEmerald = Color(0xFF10B981)
val HunterCrimson = Color(0xFFFF3838)
val HunterViolet = Color(0xFF9D4EDD)

// Typography Colors
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Reusable Brushes matching the reference mockups
val AmberCrimsonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFFB703), Color(0xFFFF5400), Color(0xFFFF2A54))
)

val PillStreakGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF3B30), Color(0xFFFF7B00), Color(0xFFFFB703))
)

val CardSurfaceGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF1B1F2A), Color(0xFF12151C))
)

var AppThemeIsDark: Boolean = true

// Compatibility tokens for Theme.kt & ModernDashboardScreen.kt
val PikaYellowLight = Color(0xFFFFF176)
val PikaCheekRedLight = Color(0xFFFF6B6B)
val DarkBg = Color(0xFF0B0D12)
val DarkSurface = Color(0xFF14171F)
val DarkCard = Color(0xFF14171F)
val DarkCardElevated = Color(0xFF1C202B)
val DarkBorder = Color(0xFF2A2F3D)
val LightBg = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightCard = Color(0xFFFFFFFF)
val LightCardElevated = Color(0xFFFEF9C3)
val LightBorder = Color(0xFFCBD5E1)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFFCBD5E1)
val DarkTextMuted = Color(0xFF94A3B8)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF334155)
val LightTextMuted = Color(0xFF475569)
val AccentTeal = Color(0xFFFFD60A)
val AccentTealDark = Color(0xFFF59E0B)
val AccentPurple = Color(0xFFFF3B30)
val AccentPurpleLight = Color(0xFFFF6B6B)
val AccentCyanGlow = Color(0xFFFFF176)
val WarningAmber = Color(0xFFF59E0B)
val DangerCrimson = Color(0xFFFF3B30)
val DarkCardBg = DarkCard
val LightCardBg = LightCard