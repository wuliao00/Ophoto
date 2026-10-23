package com.ophoto.data.repository

import com.ophoto.data.local.CompositionGuideType

enum class SceneCategory(val label: String) {
    ALL("全部场景"),
    PORTRAIT("人像写真"),
    STREET("街头纪实"),
    NIGHT("蓝调夜景"),
    LANDSCAPE("自然风光"),
    GOURMET("静物美食"),
    TRAVEL("旅行打卡"),
    ARCHITECTURE("建筑几何"),
    ACTION_PET("运动萌宠"),
}

data class ShotInspirationCard(
    val id: String,
    val title: String,
    val category: SceneCategory,
    val focalEquivalentMm: Int,
    val distanceHint: String,
    val cameraHeightHint: String,
    val recommendedExposure: String,
    val recommendedKelvin: Int,
    val recommendedEv: Float,
    val compositionGuide: CompositionGuideType,
    val subjectNormalizedX: Float, // 0..1 for parametric composition schematic
    val subjectNormalizedY: Float, // 0..1
    val lightDirectionDeg: Float,  // 0 = right, 90 = top, 135 = back-left golden rim
    val linkedPresetId: String,
    val effectSummary: String,
    val stepByStepTips: List<String>,
    val fieldWorkaround: String,
)

enum class LampNodeRole(val label: String, val colorHex: Long) {
    KEY_LIGHT("主光 Key", 0xFFFBBF24),
    FILL_LIGHT("辅光/反光板 Fill", 0xFF93C5FD),
    RIM_BACK("轮廓/逆光 Rim", 0xFFFF6A2B),
    PRACTICAL_BG("环境氛围光 BG", 0xFF34D399),
}

data class LampNode(
    val role: LampNodeRole,
    val angleDegFromCamera: Float, // 0° = camera axis, +45° = camera-right front, +135° = right rear
    val radiusRatio: Float,        // 0.3 .. 0.92
    val powerNote: String,
)

data class LightingSetupGuide(
    val id: String,
    val title: String,
    val tag: String,
    val lightRatio: String,
    val recommendedFocal: String,
    val nodes: List<LampNode>,
    val keyPoints: List<String>,
    val linkedPresetId: String,
)

data class ShootChecklistTemplate(
    val id: String,
    val sceneName: String,
    val subtitle: String,
    val gearItems: List<String>,
    val cameraSetupItems: List<String>,
    val icebreakerTalkLines: List<String>,
)

data class VisualTermEntry(
    val id: String,
    val term: String,
    val englishTag: String,
    val group: String,
    val coreFormulaOrRule: String,
    val interactiveHint: String,
    val sliderMinLabel: String,
    val sliderMaxLabel: String,
)

/**
 * Clean-room On-Site Photography Assistant Knowledge Base (inspired by 摄影现场助手 workflow:
 * Shot Inspiration Cards + Parametric Composition + Top-Down Lighting Diagrams + Checklists +
 * Icebreaker Talk Lines + Interactive Visual Glossary).
 */
object FieldAssistantCatalog {

    val SHOT_CARDS: List<ShotInspirationCard> = listOf(
        ShotInspirationCard(
            id = "shot_golden_rim_portrait",
            title = "落日发丝金边半身像",
            category = SceneCategory.PORTRAIT,
            focalEquivalentMm = 70,
            distanceHint = "距人物 2.2–3.0 米",
            cameraHeightHint = "平视眼部或微仰 5°",
            recommendedExposure = "f/2.0 · 1/500s · ISO 100",
            recommendedKelvin = 6200,
            recommendedEv = 0.3f,
            compositionGuide = CompositionGuideType.RULE_OF_THIRDS,
            subjectNormalizedX = 0.66f,
            subjectNormalizedY = 0.38f,
            lightDirectionDeg = 135f,
            linkedPresetId = "amber_golden_hour",
            effectSummary = "太阳位于人物斜后方 135°，发丝与肩线勾勒璀璨金边，背景树影化成温暖光斑。",
            stepByStepTips = listOf(
                "切至 3x 长焦（约 70mm），让人物背对斜下方太阳并侧身回眸 30°",
                "对准人物脸部点测光并向上微推 +0.3 EV，防止面部欠曝发灰",
                "用手掌或树叶在镜头上缘轻遮杂散眩光，保留干净雾化光晕",
            ),
            fieldWorkaround = "若脸部阴影偏暗且没带反光板，让模特手捧白色咖啡杯/书本在胸前即可充当天然眼神补光板。",
        ),
        ShotInspirationCard(
            id = "shot_rain_neon_street",
            title = "雨夜霓虹水面镜像街拍",
            category = SceneCategory.NIGHT,
            focalEquivalentMm = 46,
            distanceHint = "距主体 3–6 米",
            cameraHeightHint = "蹲低贴近积水边缘 15–25cm",
            recommendedExposure = "f/1.8 · 1/60s · ISO 400",
            recommendedKelvin = 4300,
            recommendedEv = -0.7f,
            compositionGuide = CompositionGuideType.GOLDEN_SPIRAL,
            subjectNormalizedX = 0.34f,
            subjectNormalizedY = 0.42f,
            lightDirectionDeg = 45f,
            linkedPresetId = "city_neon_night",
            effectSummary = "利用街角积水倒映招牌冷暖霓虹，撑伞行人剪影成为视觉锚点，画面极具电影叙事感。",
            stepByStepTips = listOf(
                "将手机倒置使主摄/2x 镜头尽量贴近积水表面，放大倒影面积",
                "点击霓虹灯牌锁定曝光（AE-L）并下拉 -0.7 EV，保住招牌高光细节",
                "等待撑透明雨伞的行人踏入灯箱暖光区瞬间连拍抓帧",
            ),
            fieldWorkaround = "地面没积水？倒半瓶矿泉水在深色大理石路沿，贴近液面 2cm 同样能拍出完整霓虹倒影！",
        ),
        ShotInspirationCard(
            id = "shot_window_cafe_green",
            title = "窗边绿荫胶片叙事感",
            category = SceneCategory.PORTRAIT,
            focalEquivalentMm = 48,
            distanceHint = "隔窗或室内 1.8 米",
            cameraHeightHint = "胸口至眼平高度",
            recommendedExposure = "f/1.8 · 1/200s · ISO 200",
            recommendedKelvin = 5100,
            recommendedEv = 0.2f,
            compositionGuide = CompositionGuideType.RULE_OF_THIRDS,
            subjectNormalizedX = 0.35f,
            subjectNormalizedY = 0.48f,
            lightDirectionDeg = 35f,
            linkedPresetId = "film_green_001",
            effectSummary = "透过咖啡馆玻璃捕捉室外树影反光与人物低头翻书神态，虚实叠影自带呼吸感。",
            stepByStepTips = listOf(
                "站在店外透过玻璃取景，让玻璃反射出约 30% 街对面绿树光斑",
                "避开反光遮挡人物眼睛的位置，将眼睛精准放在明亮暗底区域",
                "套用「青绿胶片」预设并将强度调至 85%，柔化高光反差",
            ),
            fieldWorkaround = "玻璃反光太强看不清脸？让人物往靠近玻璃一侧前倾 20cm，面部照度瞬间提升一倍。",
        ),
        ShotInspirationCard(
            id = "shot_hardlight_shadow_corridor",
            title = "正午硬光几何阴影切割",
            category = SceneCategory.STREET,
            focalEquivalentMm = 24,
            distanceHint = "高机位俯拍或过道 4–8 米",
            cameraHeightHint = "二楼走廊俯拍 35°",
            recommendedExposure = "f/2.2 · 1/1000s · ISO 100",
            recommendedKelvin = 5500,
            recommendedEv = -0.8f,
            compositionGuide = CompositionGuideType.DIAGONAL,
            subjectNormalizedX = 0.62f,
            subjectNormalizedY = 0.60f,
            lightDirectionDeg = 75f,
            linkedPresetId = "silver_documentary",
            effectSummary = "把正午刺眼顶光变废为宝：只对地面亮区测光，把阴影压成纯黑画框，等待单人步入亮斑。",
            stepByStepTips = listOf(
                "寻找骑楼柱廊、天桥阶梯等有强烈明暗分界线的几何空间",
                "开启斑马纹，按亮部地面曝光并压低 -0.8 EV，让暗部彻底隐没",
                "提前构好图锁焦锁曝（AE/AF-L），等行人迈出长影瞬间按下快门",
            ),
            fieldWorkaround = "现场杂乱色彩干扰视线时，直接切换「银盐纪实」黑白实时取景，光影结构立刻一清二楚。",
        ),
        ShotInspirationCard(
            id = "shot_coastal_horizon",
            title = "极简海岸三分留白远景",
            category = SceneCategory.LANDSCAPE,
            focalEquivalentMm = 24,
            distanceHint = "距人物 8–15 米大远景",
            cameraHeightHint = "平视海平线，严守电子水平仪 0.0°",
            recommendedExposure = "f/2.2 · 1/1200s · ISO 50",
            recommendedKelvin = 4900,
            recommendedEv = 0.4f,
            compositionGuide = CompositionGuideType.RULE_OF_THIRDS,
            subjectNormalizedX = 0.67f,
            subjectNormalizedY = 0.72f,
            lightDirectionDeg = 60f,
            linkedPresetId = "coastal_breeze_cyan",
            effectSummary = "海平线严格压在下三分线，占画面 2/3 的晴空与青蓝海浪衬托微小人物，清澈治愈。",
            stepByStepTips = listOf(
                "打开 Ophoto 电子水平仪，直至中央刻度条变绿（0.0° 绝对水平）",
                "曝光微加 +0.4 EV 让海浪泡沫与白色衬衫通透明亮",
                "开启连拍捕捉海风吹起衣角与浪花拍岸的最佳形态",
            ),
            fieldWorkaround = "阴天海水发灰怎么办？降低机位仰拍避开灰海面，或改用 ND64 拍 2 秒慢门雾化海浪纹理。",
        ),
        ShotInspirationCard(
            id = "shot_bistro_overhead_45",
            title = "餐桌侧逆光 45° 食欲特写",
            category = SceneCategory.GOURMET,
            focalEquivalentMm = 48,
            distanceHint = "距餐盘 45–65cm",
            cameraHeightHint = "俯角 40°–45°（拒绝生硬 1x 广角畸变）",
            recommendedExposure = "f/1.8 · 1/125s · ISO 250",
            recommendedKelvin = 5400,
            recommendedEv = 0.3f,
            compositionGuide = CompositionGuideType.DIAGONAL,
            subjectNormalizedX = 0.50f,
            subjectNormalizedY = 0.52f,
            lightDirectionDeg = 120f,
            linkedPresetId = "bistro_gourmet",
            effectSummary = "用 2x 焦段消除盘沿变形，对角线摆放主菜、水杯与刀叉，侧逆光勾出酱汁与蒸汽高光。",
            stepByStepTips = listOf(
                "将主盘放在正中偏下，咖啡杯与餐具沿左上—右下对角线错落延伸",
                "务必切到 2x（约 48mm）退后半步拍，餐盘圆润不变形且背景自然虚化",
                "邀请同伴夹起食物或淋酱汁瞬间抓拍动态生命力",
            ),
            fieldWorkaround = "餐厅顶灯让食物阴影死黑？把同行朋友手机屏幕调成纯白暖色，在餐盘右前方 15cm 补一抹眼神光。",
        ),
        ShotInspirationCard(
            id = "shot_architecture_spiral",
            title = "旋转楼梯斐波那契仰拍",
            category = SceneCategory.ARCHITECTURE,
            focalEquivalentMm = 14,
            distanceHint = "站在中庭正下方仰视 90°",
            cameraHeightHint = "贴近地面极低机位垂直向上",
            recommendedExposure = "f/2.2 · 1/320s · ISO 100",
            recommendedKelvin = 5000,
            recommendedEv = 0.0f,
            compositionGuide = CompositionGuideType.GOLDEN_SPIRAL,
            subjectNormalizedX = 0.68f,
            subjectNormalizedY = 0.36f,
            lightDirectionDeg = 90f,
            linkedPresetId = "architectural_slate",
            effectSummary = "超广角将旋转扶手拉伸成通向天窗光心的完美黄金螺线，极具秩序与延伸感。",
            stepByStepTips = listOf(
                "切换 0.6x 超广角并打开「黄金螺旋」构图线",
                "转动身体微调站位，使楼梯收拢终点精准落在螺旋眼焦点",
                "使用 3 秒定时快门双手举稳，避免触屏瞬间抖动",
            ),
            fieldWorkaround = "天窗过亮而楼梯过暗时，启用包围曝光（±2 EV）保留高光云层与暗部栏杆质感。",
        ),
        ShotInspirationCard(
            id = "shot_pet_eyelevel",
            title = "超低机位萌宠眼神光抓拍",
            category = SceneCategory.ACTION_PET,
            focalEquivalentMm = 48,
            distanceHint = "距宠物 0.8–1.5 米",
            cameraHeightHint = "手机倒拿贴草地，与猫狗眼睛完全等高",
            recommendedExposure = "f/1.8 · 1/800s · ISO 200",
            recommendedKelvin = 5400,
            recommendedEv = 0.1f,
            compositionGuide = CompositionGuideType.CENTER_CROSS,
            subjectNormalizedX = 0.50f,
            subjectNormalizedY = 0.46f,
            lightDirectionDeg = 25f,
            linkedPresetId = "velvet_skin_portrait",
            effectSummary = "彻底告别俯拍大头照，草地前景化为梦幻绿雾，1/800s 高速快门凝固胡须与眼神高光。",
            stepByStepTips = listOf(
                "把手机倒过来握持，让摄像头紧贴草叶顶端，瞬间获得单反级前景虚化",
                "在专业面板将快门锁定在 1/800s 以上，防止甩头糊片",
                "左手拿零食在镜头正上方 5cm 晃动吸引抬头，右手长按连拍",
            ),
            fieldWorkaround = "深色毛发小狗面部看不清细节时，让它面向开阔天空方向，利用天空巨型柔光箱打亮瞳孔反光。",
        ),
    )

    val LIGHTING_GUIDES: List<LightingSetupGuide> = listOf(
        LightingSetupGuide(
            id = "light_rembrandt",
            title = "经典伦勃朗三角光",
            tag = "人像立体感",
            lightRatio = "光比 4:1",
            recommendedFocal = "50mm – 85mm",
            nodes = listOf(
                LampNode(LampNodeRole.KEY_LIGHT, 45f, 0.72f, "主光高出眼睛 30° 斜向下"),
                LampNode(LampNodeRole.FILL_LIGHT, -45f, 0.62f, "银白反光板柔和回补暗部"),
                LampNode(LampNodeRole.RIM_BACK, 150f, 0.80f, "弱轮廓光分离黑发与暗背景"),
            ),
            keyPoints = listOf(
                "调整主光角度直到背光侧脸颊颧骨下方出现一枚倒三角形亮斑",
                "三角亮斑宽度不超过眼睛宽度，长度不超过鼻尖",
                "适合沉稳肖像、复古胶片与男性棱角刻画",
            ),
            linkedPresetId = "terracotta_humanist",
        ),
        LightingSetupGuide(
            id = "light_golden_rim",
            title = "户外黄金逆光 + 正面漫射",
            tag = "外景万能公式",
            lightRatio = "光比 3:1",
            recommendedFocal = "70mm – 135mm",
            nodes = listOf(
                LampNode(LampNodeRole.RIM_BACK, 140f, 0.88f, "落日低角度太阳作天然发丝轮廓光"),
                LampNode(LampNodeRole.FILL_LIGHT, -15f, 0.55f, "折白板或浅色地面反射补亮五官"),
            ),
            keyPoints = listOf(
                "绝不让正午顶光直射眼窝产生熊猫眼，日落前 1 小时背光站位最出片",
                "长焦镜头将背景暗色树丛纳入画面，金边发丝对比度翻倍",
            ),
            linkedPresetId = "amber_golden_hour",
        ),
        LightingSetupGuide(
            id = "light_window_soft",
            title = "室内单侧窗光",
            tag = "零器材自然光",
            lightRatio = "光比 3:1 ~ 6:1",
            recommendedFocal = "35mm – 50mm",
            nodes = listOf(
                LampNode(LampNodeRole.KEY_LIGHT, 75f, 0.75f, "纱帘大窗漫射侧光"),
                LampNode(LampNodeRole.PRACTICAL_BG, -145f, 0.82f, "远端台灯营造冷暖空间纵深"),
            ),
            keyPoints = listOf(
                "关闭头顶色温混杂的吸顶日光灯，只留纯净侧窗光与一盏暖黄台灯",
                "人物离窗越近光比越戏剧化，退后 1.5 米则光线更柔和均匀",
            ),
            linkedPresetId = "velvet_skin_portrait",
        ),
        LightingSetupGuide(
            id = "light_cyber_dual_color",
            title = "街头冷暖双色霓虹光",
            tag = "赛博夜景",
            lightRatio = "冷暖对冲 2:1",
            recommendedFocal = "35mm – 50mm",
            nodes = listOf(
                LampNode(LampNodeRole.KEY_LIGHT, 55f, 0.68f, "街边暖橙/品红灯箱照亮面颊主侧"),
                LampNode(LampNodeRole.RIM_BACK, -125f, 0.78f, "对街冷蓝/青绿招牌勾勒冷色轮廓"),
            ),
            keyPoints = listOf(
                "站在两种不同颜色店招之间，让人物一侧受暖光、另一侧受青蓝环境光",
                "白平衡固定在 4200K–4500K，切忌自动白平衡把霓虹色彩中和抵消",
            ),
            linkedPresetId = "city_neon_night",
        ),
        LightingSetupGuide(
            id = "light_tabletop_grazing",
            title = "静物美食 110° 侧逆质感光",
            tag = "静物/探店",
            lightRatio = "光比 3:1",
            recommendedFocal = "48mm – 70mm",
            nodes = listOf(
                LampNode(LampNodeRole.KEY_LIGHT, 115f, 0.70f, "后侧方窗户/柔光灯擦过食物表面"),
                LampNode(LampNodeRole.FILL_LIGHT, -35f, 0.52f, "白色餐巾纸立在暗侧轻柔补光"),
            ),
            keyPoints = listOf(
                "顺光拍美食必显平淡油腻，110° 侧逆光才能拍出面包气孔与汤汁光泽",
                "深色木桌选负曝光 -0.3 EV，白色大理石桌选正曝光 +0.4 EV",
            ),
            linkedPresetId = "bistro_gourmet",
        ),
    )

    val CHECKLIST_TEMPLATES: List<ShootChecklistTemplate> = listOf(
        ShootChecklistTemplate(
            id = "chk_portrait_street",
            sceneName = "人像与街拍出发清单",
            subtitle = "设备检查 · 相机预调 · 自然引导破冰话术",
            gearItems = listOf(
                "擦镜布彻底清洁手机主摄与潜望长焦镜片油膜（逆光发雾头号元凶！）",
                "检查机身剩余存储 ≥ 15GB，电量 ≥ 80%",
                "携带折叠五合一小反光板 / 透明长柄伞（晴雨两用道具）",
                "准备咖啡杯 / 复古书刊 / 耳机等手部互动小道具",
            ),
            cameraSetupItems = listOf(
                "开启九宫格 + 电子水平仪 + 实时直方图",
                "设置默认滤镜强度 80%–85%，开启高光斑马纹防脸部过曝",
                "开启 ColorOS 大师悬浮窗并预收藏 3 套目标预设",
            ),
            icebreakerTalkLines = listOf(
                "【陌生人街拍破冰】你好！刚才看到你这身穿搭和这束阳光特别搭，能耽误 30 秒给你拍两张照片吗？拍完现场发原图给你！",
                "【缓解镜头僵硬】不用特意看镜头，慢慢往前走四步，想象听到后面有人叫你名字再自然回头。",
                "【手部不自然急救】一只手轻理耳边碎发，另一只手扶住栏杆/咖啡杯，肩膀一高一低立刻松弛。",
            ),
        ),
        ShootChecklistTemplate(
            id = "chk_bluehour_landscape",
            sceneName = "风光与城市蓝调长曝光清单",
            subtitle = "机位踩点 · 慢门三脚架 · 包围曝光流程",
            gearItems = listOf(
                "便携三脚架 + 稳固手机夹具（避开高架桥震动伸缩缝）",
                "ND64 / ND1000 手机磁吸减光镜与 CPL 偏振镜",
                "蓝牙快门或启用 Ophoto 3 秒倒计时无接触快门",
            ),
            cameraSetupItems = listOf(
                "提前 40 分钟到达机位：在现场计算器核对今日日落与蓝调结束时刻",
                "切至专业模式：ISO 锁定 50/100，手动对焦无穷远（配合峰值对焦确认）",
                "关闭光学防抖（上脚架长曝防漂移），大光比场景加拍 ±2 EV 包围曝光",
            ),
            icebreakerTalkLines = listOf(
                "【日落后黄金 20 分钟提醒】太阳刚落山别急着收脚架！等 15 分钟天空变宝蓝色、建筑景观灯全亮时才是真正大片时刻。",
            ),
        ),
    )

    val VISUAL_GLOSSARY: List<VisualTermEntry> = listOf(
        VisualTermEntry(
            id = "term_exposure_triangle",
            term = "曝光三角互易律",
            englishTag = "Exposure Reciprocity",
            group = "曝光核心",
            coreFormulaOrRule = "EV = log₂(N² / t) - log₂(ISO / 100)",
            interactiveHint = "拖动滑块观察：快门每加快 1 档凝固动态，需开大 1 档光圈或翻倍 ISO 才能维持相同画面亮度。",
            sliderMinLabel = "慢门丝绢 1/4s · ISO 50",
            sliderMaxLabel = "高速抓拍 1/1000s · ISO 12800",
        ),
        VisualTermEntry(
            id = "term_ettr_zebra",
            term = "向右曝光与斑马纹 (ETTR)",
            englishTag = "Expose To The Right",
            group = "数码宽容度",
            coreFormulaOrRule = "手机 CMOS 高光一旦死白无法拉回，暗部提亮则易出彩噪",
            interactiveHint = "开启 96% 斑马纹：让画面最亮云层/灯牌刚好临界不出现条纹，即可获得最纯净信噪比。",
            sliderMinLabel = "欠曝提亮（暗部噪点剧增）",
            sliderMaxLabel = "向右临界曝光（宽容度最优）",
        ),
        VisualTermEntry(
            id = "term_cube_lut",
            term = "3D LUT 色彩查找表 (.cube)",
            englishTag = "3D Color Lookup Table",
            group = "色彩科学",
            coreFormulaOrRule = "RGB(r, g, b) ∈ [0,1]³ ──三线性插值──▶ RGB'(r', g', b')",
            interactiveHint = "不同于简单线性滑块，3D LUT 能在保持肤色自然的同时独立扭转青绿暗部与暖金高光色相。",
            sliderMinLabel = "0% 原片直出 Rec.709",
            sliderMaxLabel = "100% 完整胶片色彩映射",
        ),
        VisualTermEntry(
            id = "term_hyperfocal",
            term = "超焦距对焦法",
            englishTag = "Hyperfocal Distance",
            group = "光学景深",
            coreFormulaOrRule = "H = f² / (N · c) + f（对焦于 H 处，从 H/2 至无穷远全清晰）",
            interactiveHint = "拍风光如果直接对焦在无穷远山峰，脚下前景花草反而会虚；对焦在超焦距点可最大化清晰景深。",
            sliderMinLabel = "近处微距浅景深",
            sliderMaxLabel = "超焦距全景深合焦",
        ),
        VisualTermEntry(
            id = "term_kelvin_wb",
            term = "色温 K 值与色彩心理",
            englishTag = "Correlated Color Temperature",
            group = "光线色彩",
            coreFormulaOrRule = "低 K 值偏蓝冷冽（3200K–4500K） · 高 K 值偏琥珀暖金（6000K–7500K）",
            interactiveHint = "打破自动白平衡还原白纸的执念：蓝调时刻刻意降到 4200K 强化冷蓝，日落升到 6300K 渲染暮色。",
            sliderMinLabel = "3200K 冷蓝静谧蓝调",
            sliderMaxLabel = "7200K 炽烈暮色金阳",
        ),
    )

    val DAILY_CHALLENGES: List<String> = listOf(
        "【光影猎人】在下午四点的街头，拍一张「影子比人更有戏」的对角线照片",
        "【色彩孤岛】寻找整幅画面只有一种高饱和主色（如红伞/黄出租车）的瞬间",
        "【框中框】利用车窗、门洞、树枝或后视镜作为天然画框拍摄主体",
        "【水面倒影】蹲下寻找一滩水、玻璃幕墙或不锈钢桌面拍出对称镜像世界",
        "【3x 空间压缩】全程锁定长焦镜头散步 20 分钟，只拍局部光影切片",
        "【冷暖碰撞】在同一画面同时收录一盏暖黄灯光与一片深蓝夜空",
        "【慢门动静】固定手机拍一张行人模糊穿梭而主角安静伫立的对比照",
        "【极简留白】拍一张留白面积超过 75% 的建筑天空或海岸极简作品",
    )
}
