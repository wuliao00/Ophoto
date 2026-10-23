# Ophoto · 专业相机 × 3D LUT 胶片滤镜 × 摄影现场助手

[![CI](https://github.com/wuliao00/Ophoto/actions/workflows/ci.yml/badge.svg)](https://github.com/wuliao00/Ophoto/actions/workflows/ci.yml)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(ColorOS%20%2F%20OxygenOS%20%2F%20realme%20UI)-10B981)](https://developer.android.com)
[![Stack](https://img.shields.io/badge/Kotlin%202.0-Compose%20%2B%20Camera2%20%2B%20OpenGL%20ES%203.0-E5B869)](app/)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **英文文档 / English README**: [README.en.md](README.en.md) · **架构与安全规范**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · **浏览器实时光学预览台**: [`docs/web-studio/index.html`](docs/web-studio/index.html)

**Ophoto**（包名：`com.ophoto`）是一款专为 **OPPO / OnePlus（一加）/ realme 真我（ColorOS 13–17 / OxygenOS / realme UI）** 深度打磨、同时完整兼容主流 Android 8.0+（API 26–35）机型的**专业光学相机、实时 3D LUT 胶片调色、系统大师模式悬浮窗与摄影现场助手一体化应用**。

---

## 核心特性总览

### 1. 专业相机与多摄取景器（`CameraX` + `Camera2Interop`）
- **全模式拍摄矩阵**：拍照、专业手动（Pro）、人像、夜景、视频录制、慢门长曝光（光轨/流水）、连拍与 **AEB 包围曝光（-2EV / 0EV / +2EV）**、倒计时快门（3s / 5s / 10s）。
- **OPPO 多物理镜头动态识别**：通过 `CameraCharacteristics` 自动枚举超广角（0.6x / 14–16mm）、大底主摄（1x / 23–24mm）、中焦人像（2x–3x / 48–70mm）、潜望长焦（3.5x–6x / 85–135mm）及前摄，并适配 Find N 系列折叠屏双栏自适应布局（`NavigationRail` + 分栏工作台）。
- **真手动光学控制**：
  - 手动感光度 ISO（50–6400 硬件真实范围裁剪）、手动快门速度（1/8000s 至慢门秒级 `SENSOR_EXPOSURE_TIME`）
  - 曝光补偿 EV（-3.0 至 +3.0 EV）、一键 **AE/AF 测光对焦锁定**
  - 白平衡预设（自动 / 日光 / 阴天 / 钨丝灯 / 荧光灯）与 **2500K–10000K 色温 + 红绿洋红（RGGB 通道增益）**精细微调
  - 手动对焦距离推杆（`LENS_FOCUS_DISTANCE`）+ **2.5x 对焦放大窗**
- **专业监看 HUD 辅助**：
  - **构图线系统**：九宫格、斐波那契黄金螺旋、对角线、中心十字准星、画幅遮幅（4:3 / 3:2 / 16:9 / 1:1 / 21:9 / XPan 65:24）与**重力传感器实时电子水平仪**
  - **曝光与对焦监看**：实时 RGB + 亮度直方图、**Sobel 边缘检测峰值对焦（红 / 绿 / 蓝 / 黄 + 阈值调节）**、**96% 高光溢出斑马纹**与死黑裁切百分比告警

### 2. 实时 GPU 3D LUT 着色器、安全滤镜导入与大师模式悬浮窗
- **OpenGL ES 3.0 零拷贝取景着色管线（`GlFilterShaderProgram`）**：
  `OES 相机纹理 ──▶ 曝光 EV ──▶ 色温/色调矩阵 ──▶ 对比度/饱和度 ──▶ 硬件三线性插值 3D LUT (sampler3D) ──▶ 高光/阴影分离色调与暗部褪色 ──▶ 胶片颗粒与光学暗角 ──▶ Sobel 峰值对焦 & 斑马纹`
- **内置 12 款 100% 原创无商标风险色彩预设**：
  `青绿胶片`、`城市夜景`、`琥珀暖阳`、`银盐纪实`、`柔雾清透人像`、`海风青蓝`、`赤陶人文`、`森系微光`、`旧日暖笺`、`极简几何`、`食光暖味`、`雾银低反差`——每款预设同时内置实时渲染管线参数与 **OPPO 原生相机大师模式参考参数（底色 / 柔光 / 影调 / 饱和度 / 冷暖 / 青品 / 锐度 / 暗角 + ISO / 快门 / EV / 色温建议）**。
- **沙盒安全滤镜导入与生态兼容（`FilterImporterImpl` + `FileSandboxValidator`）**：
  - 原生支持 **`.cube` 3D LUT**（`LUT_3D_SIZE ≤ 64`、文件 `≤ 10MB`、防脚本注入校验）、**`.json` 参数包**、**`.filterpack` / `.zip` 标准滤镜包**（`manifest.json` + 版本兼容检查 + Zip-Slip 路径穿越拦截 + Zip 炸弹体积拦截 + 可执行/脚本载荷拦截）。
  - 支持 **`OPHOTO1:` 离线口令与 25×25 光学二维码分享/导入**，并兼容社区大师预设 JSON 格式（`presets[].sections[].items[]`）无损解析映射。
  - 支持将任意内置/自建滤镜**一键反向烘焙导出为标准 `.cube` 文件**，直接用于 DaVinci Resolve / Premiere Pro / Photoshop / 第三方相机。
- **系统相机大师模式半透明悬浮窗（`MasterOverlayService`）**：
  - 通过 `SYSTEM_ALERT_WINDOW` 提供 **标准大师卡 / 紧凑胶囊 / 迷你悬浮球** 三种形态，支持手指自由拖拽避让取景主体、左右切换预设、一键拉起 ColorOS 系统原生相机，并内置 **ColorOS 14–17 侧载受限设置解锁指引**。

### 3. 摄影现场助手（Field Assistant）
- **场景镜头灵感卡**：覆盖人像写真、街头人文、蓝调夜景、自然风光、美食静物、建筑极简等高频实拍场景，内置**参数化构图与光线方向蓝图 Canvas**、机位姿势拆解、没带反光板/脚架时的现场替代方案，支持**一键将焦段、滤镜、曝光补偿、色温与构图辅助线直接装载进相机取景器开拍**。
- **俯视灯位与自然光雷达图谱**：极坐标 Canvas 直观标示人物、机位、主光角度、轮廓逆光与反光板补光夹角及光比。
- **6 合 1 现场光学与天文计算器（`FieldCalculators`）**：
  - **NOAA 天文黄金时刻 & 蓝调时刻 & 太阳高度角**（内置香港维港、深圳湾、广州塔、上海外滩、北京角楼、杭州西湖、成都、大理洱海、台北象山、东京涩谷经纬度时区预设，亦支持任意自定义经纬度）
  - **朔望月相照明度与银河/月夜实拍建议**
  - **多画幅等效 35mm 焦距与水平/对角视场角（FoV）换算**（1/2.55" / 1/1.56" / 1/1.4" / 1.0 英寸大底 / APS-C / 全画幅）
  - **光学景深（DoF）近界、远界与超焦距计算**
  - **ND8 / ND64 / ND1000 减光镜慢门快门换算 + 一键 10 档 B 门震动倒计时器**
- **出发器材核对清单 & 现场破冰引导话术 & 每日拍摄挑战 & 光学词汇图解**：Room 本地持久化勾选状态，陌生人街拍破冰与人像手部动作引导话术一键复制。

### 4. 无损暗房编辑器与大师签注相框
- **本地单张精修 & 批量套用预设**：支持撤销/重做历史栈（`EditHistoryEntity`）、按住前后原片对比、自由/比例裁剪与水平校正、局部径向/线性遮罩、单调三次样条 **交互式 RGB 曲线（Master Tone Curve）**、**8 色相通道 HSL 混色器**、胶片颗粒、暗角与 3×3 卷积锐化。
- **大师签注艺术相框与隐私保护**：内置纯白画廊卡纸、曜石黑金大师签注、35mm 胶片底片三种高分辨率相框水印，支持**一键抹除 GPS 经纬度隐私元数据**并保存至系统相册 `Pictures/Ophoto`。

---

## 分层工程目录结构

```text
app/src/main/java/com/ophoto/
├── core/
│   ├── device/OppoDeviceInspector.kt       # ColorOS/OxygenOS/realme 机型识别与物理多摄参数探测
│   ├── permission/CameraPermissionHelper.kt# 运行时最小权限与悬浮窗状态检查
│   ├── security/FileSandboxValidator.kt    # Zip-Slip / Zip 炸弹 / 可执行载荷安全沙盒校验器
│   ├── storage/MediaStoreSaver.kt          # Scoped Storage 相册写入与 GPS 元数据隐私抹除
│   └── log/SafeLogger.kt                   # 自动脱敏安全日志
├── engine/
│   ├── camera/                             # ICameraEngine 接口与 CameraX + Camera2Interop 实现
│   ├── render/                             # OpenGL ES 3.0 OES + 3D LUT + 峰值对焦 + 斑马纹着色器
│   ├── filter/                             # CubeLutParser 三线性插值解析器与 FilterEngineImpl
│   ├── image/ImageProcessEngine.kt         # 高清照片曲线 + HSL + 3D LUT + 相框水印全管线引擎
│   └── metadata/MetadataEngine.kt          # EXIF 拍摄参数读写与隐私清理引擎
├── domain/
│   └── calculator/FieldCalculators.kt      # NOAA 黄金/蓝调时刻、月相、景深超焦距、等效焦段与 ND 计算
├── data/
│   ├── local/                              # Room 数据库 (filter / capture_record / edit_history / field_progress) + DataStore
│   ├── importer/                           # .cube / .json / .filterpack / OPHOTO1 口令 / 社区预设安全导入器
│   └── repository/                         # 12 款原创预设库、摄影现场助手完整场景图谱与仓库层
├── service/
│   └── MasterOverlayService.kt             # 系统相机上方可拖拽多形态大师参数半透明悬浮窗服务
└── ui/
    ├── theme/                              # 暗色光学取景器配色 (#090A0D + 暖金刻度 #E5B869 + 等宽读数)
    ├── navigation/OphotoAppShell.kt        # 直板机底部栏 / 折叠屏大屏 NavigationRail 自适应外壳
    ├── camera/                             # 专业取景器、构图/水平仪/直方图叠层与手动控制甲板
    ├── filter/                             # 大师调色库、安全导入/导出、光学二维码分享与悬浮窗控制台
    ├── assistant/                          # 摄影现场助手（镜头卡/灯位雷达/天文光学计算器/清单话术）
    ├── editor/                             # 无损暗房编辑器（RGB 曲线/8色 HSL/裁剪遮罩/大师相框）
    └── settings/                           # OPPO 多摄硬件检测报告、拍摄命名模板、功耗与隐私设置
```

---

## 快速构建与安装

### 1. Android 真机 APK 构建
环境要求：**JDK 17**、**Android SDK 35**。

```bash
# 运行全部 JUnit + Robolectric 单元测试、Android Lint 静态检查并构建 Debug APK
./gradlew testDebugUnitTest lintDebug assembleDebug

# 构建开启 R8 代码压缩与 Baseline Profile 的 Release APK
./gradlew assembleRelease
```

你也可以直接在 GitHub仓库 **Actions** 页面打开最新绿色工作流运行记录，下载构建产物 **`Ophoto-debug-apk`** 安装至 OPPO / 一加 / 真我或其他 Android 手机体验。

### 2. 浏览器实时光学工作室预览（Web Companion）
无需 Android 编译环境即可在浏览器内体验实时 `.cube` 3D LUT 三线性插值、峰值对焦、斑马纹、悬浮窗参数联动、天文黄金时刻计算器与暗房曲线签注相框：

```bash
python3 -m http.server 8080 --directory docs/web-studio
```

---

## 合规与原创声明

1. 本项目所有 Kotlin / GLSL / HTML 代码、内置 12 款色彩预设与参数曲线、UI 图标与矢量图谱均为 **100% 洁净室（Clean-Room）原创实现**，采用 **MIT License** 开源。
2. 内置预设采用纯粹色彩与摄影场景描述性命名（如 `青绿胶片`、`琥珀暖阳`、`银盐纪实`），不包含任何受注册商标保护的胶片品牌名称或闭源第三方二进制资源。
3. 滤镜导入系统仅解析纯数据描述文件（`.cube` / `.json` / 静态预览图），严格拒绝任何可执行代码或脚本载荷。
