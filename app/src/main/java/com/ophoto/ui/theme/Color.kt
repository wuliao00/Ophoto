package com.ophoto.ui.theme

import androidx.compose.ui.graphics.Color

/* ── Base Surfaces ── */
val ObsidianBg = Color(0xFF090A0D)        // 全局背景（最深）
val ViewfinderSurface = Color(0xFF11131A) // 取景器表面
val InstrumentDeck = Color(0xFF171B24)    // 控制面板 / 卡片底
val ControlPillBg = Color(0xFF212734)    // 药丸 / 芯片底
val HairlineBorder = Color(0xFF2B3242)   // 细边框
val SurfaceRaised = Color(0xFF1E2430)    // 浮起卡片（更亮一层）

/* ── Brand Accents ── */
val BrassGold = Color(0xFFE5B869)        // 主色黄铜金
val BrassGoldDim = Color(0xFF9E7B3E)     // 主色暗调
val BrassGoldSoft = Color(0xFFE5B869).copy(alpha = 0.18f) // 主色浅遮罩
val BrassGoldGlow = Color(0xFFE5B869).copy(alpha = 0.12f)
val ShutterOrange = Color(0xFFFF6A2B)    // 快门橙 / 危险强调
val OpticalEmerald = Color(0xFF34D399)   // 翡翠绿（水平/成功）
val CyanTelemetry = Color(0xFF38BDF8)    // 青色遥测
val AlertCrimson = Color(0xFFEF4444)     // 警告红
val VelvetRose = Color(0xFFE8A0B8)       // 柔雾人像粉
val OceanCyan = Color(0xFF6EC1E4)       // 海风青蓝
val FilmAmber = Color(0xFFF59E0B)        // 琥珀暖阳

/* ── Gradient & Glow (used by Deck & Shutter) ── */
val GradientStart = Color(0xFF1C2230)    // 渐变起
val GradientMid = Color(0xFF0F121A)      // 渐变中
val GradientEnd = Color(0xFF090A0D)      // 渐变终
val GlowBrass = Color(0xFFE5B869).copy(alpha = 0.35f)   // 黄铜光晕
val GlowEmerald = Color(0xFF34D399).copy(alpha = 0.30f) // 翡翠光晕
val GlowOrange = Color(0xFFFF6A2B).copy(alpha = 0.30f)  // 橙光晕
val GlassBlur = Color(0xCC0B0D12)        // 毛玻璃底
val GlassBorder = Color(0x1AFFFFFF)     // 玻璃边框

/* ── Typography Colors ── */
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)
val TextOnAccent = Color(0xFF090A0D)     // 在金/橙背景上的文字
val TextBrass = Color(0xFFE5B869)

/* ── Premium Extras ── */
val PremiumCardBg = Color(0xFF12151E)
val PremiumCardBorder = Color(0xFF252C3A)
val StudioWarm = Color(0xFF2A1F14)
val StudioCool = Color(0xFF14202A)
