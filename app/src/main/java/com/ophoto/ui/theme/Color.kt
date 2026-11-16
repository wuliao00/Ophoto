package com.ophoto.ui.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
   Ophoto 色板 · Clarity / Deference / Depth
   ----------------------------------------------------------------------------
   原则：
   1. 背景是「黑场」，不是「深蓝」——让照片成为唯一亮色。
   2. 只用一种强调色（黄铜金 #E5B869）标记可操作状态；其余信息靠层级与留白区分。
   3. 材质半透明而非实心，控件浮在内容之上，而不是与内容争抢注意力。
   ============================================================================ */

/* ── 背景层 ── */
val ObsidianBg = Color(0xFF08090B)          // 应用最低层（近黑，非纯黑，避免 OLED 拖影）
val CanvasBlack = Color(0xFF000000)         // 照片画布（纯黑，最大限度凸显画面）
val ViewfinderSurface = Color(0xFF0D0E11)   // 取景器表面
val InstrumentDeck = Color(0xFF121317)      // 控制甲板
val ControlPillBg = Color(0xFF1C1D22)       // 药丸 / 芯片底
val HairlineBorder = Color(0xFF2A2C33)      // 细边框（信息切分）
val SurfaceRaised = Color(0xFF191A1F)       // 浮起卡片

/* ── 材质（Clarity 依靠半透明 + 模糊观感） ── */
val MaterialThin = Color(0xD1121317)        // 轻材质：贴近内容的浮层
val MaterialRegular = Color(0xE6101114)     // 标准材质：工具栏
val MaterialThick = Color(0xF5121317)        // 厚材质：全屏面板
val MaterialHairline = Color(0x1FFFFFFF)    // 材质高光边（1px 顶部反光）

/* ── 品牌强调色（唯一） ── */
val BrassGold = Color(0xFFE5B869)           // 主强调：可操作 / 选中
val BrassGoldDim = Color(0xFF9C7C42)        // 强调色的静息态
val BrassGoldSoft = Color(0xFFE5B869).copy(alpha = 0.14f)
val BrassGoldGlow = Color(0xFFE5B869).copy(alpha = 0.10f)
val ShutterOrange = Color(0xFFFF6A2B)       // 快门橙（仅快门 / 录制）
val OpticalEmerald = Color(0xFF4ADE80)      // 状态：正常 / 已对齐
val AlertCrimson = Color(0xFFF05252)        // 状态：错误 / 过曝
val CyanTelemetry = Color(0xFF7DD3FC)       // 读数辅助（极少量）
val VelvetRose = Color(0xFFE8A0B8)
val OceanCyan = Color(0xFF6EC1E4)
val FilmAmber = Color(0xFFF59E0B)

/* ── 渐变与光晕（Depth：极克制的浮起感） ── */
val GradientStart = Color(0xFF16171C)
val GradientMid = Color(0xFF0C0D11)
val GradientEnd = Color(0xFF08090B)
val GlowBrass = Color(0xFFE5B869).copy(alpha = 0.30f)
val GlowEmerald = Color(0xFF4ADE80).copy(alpha = 0.26f)
val GlowOrange = Color(0xFFFF6A2B).copy(alpha = 0.28f)
val GlassBlur = Color(0xCC0B0C0F)
val GlassBorder = Color(0x1AFFFFFF)

/* ── 文字层级（Clarity：高对比、只有三级） ── */
val TextPrimary = Color(0xFFF5F6F8)         // 主信息（对 #08090B 对比度 ~18:1）
val TextSecondary = Color(0xFFA0A4AD)       // 次信息（~7:1）
val TextMuted = Color(0xFF6B7078)           // 辅助说明（~4.6:1，仅用于非关键文字）
val TextOnAccent = Color(0xFF0A0B0D)        // 金底上的文字
val TextBrass = Color(0xFFE5B869)

/* ── 旧命名兼容（保留调用点，值已收敛到新色板） ── */
val PremiumCardBg = Color(0xFF101115)
val PremiumCardBorder = Color(0xFF23252B)
val StudioWarm = Color(0xFF241C14)
val StudioCool = Color(0xFF141C22)
