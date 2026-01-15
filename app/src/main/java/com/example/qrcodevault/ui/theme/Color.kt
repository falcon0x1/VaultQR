package com.example.qrcodevault.ui.theme

import androidx.compose.ui.graphics.Color

// VaultQR Emerald Theme - Trust, Security, Modern
val VaultPrimary = Color(0xFF10B981)       // Emerald green
val VaultSecondary = Color(0xFF14B8A6)     // Teal
val VaultAccent = Color(0xFFF59E0B)        // Amber for highlights
val VaultBackground = Color(0xFF0A0F1C)    // Deep navy
val VaultSurface = Color(0xFF111827)       // Dark slate
val VaultCard = Color(0xFF1F2937)          // Card background
val VaultCardBorder = Color(0xFF374151)    // Border gray

// Text colors
val TextPrimary = Color(0xFFF9FAFB)        // Almost white
val TextSecondary = Color(0xFF9CA3AF)      // Muted gray
val TextMuted = Color(0xFF6B7280)          // Even more muted

// Legacy aliases for compatibility
val GlassPrimary = VaultPrimary
val GlassSecondary = VaultSecondary
val GlassAccent = VaultAccent
val GlassBackground = VaultBackground
val GlassSurface = VaultSurface
val GlassCard = VaultCard
val GlassCardBorder = VaultCardBorder
val GlassOverlay = Color(0xFF1F2937).copy(alpha = 0.9f)

// Status colors
val SuccessGreen = Color(0xFF22C55E)
val ErrorRed = Color(0xFFEF4444)
val WarningAmber = Color(0xFFF59E0B)
val InfoBlue = Color(0xFF3B82F6)