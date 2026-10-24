# Changelog

All notable changes to **Ophoto (`com.ophoto`)** will be documented in this file.

## [v1.1.0] - 2026-10-24 — *Precision Studio*

### 🔥 核心修复：彻底解决专业模式预览与拍照过暗 / 黑片问题

- **专业模式四态曝光互易率架构（`ExposureConfig` + `CameraX2Engine`）**
  - 移除切入「专业」模式即硬锁 `ISO 200 + 1/60s` 的缺陷；进入专业模式默认运行原生 ISP 自动测光，仪表盘以 ~6Hz 实时回显 `TotalCaptureResult` 传感器感光度与快门速度。
  - 支持 **自动 AE / ISO 优先半自动 / 快门优先半自动 / 全手动 M** 四态无缝切换：单轴调整 `ISO` 或 `快门` 时基于环境测光互易率（`resolveManualParameters`）自动补偿另一轴，并下发 `SENSOR_FRAME_DURATION` 解锁慢门进光。
  - 新增全手动欠曝守护：当双轴手动组合相对环境测光欠曝超过 `-1.5 EV` 时，取景器右上角自动弹出 **「曝光偏暗 · 点此恢复自动」** 一键救回胶囊。
- **光学白平衡无损偏移与双流拍照帧率解绑**
  - 修复手动色温 K 值缺少 3×3 `COLOR_CORRECTION_TRANSFORM` 矩阵导致部分机型 ISP 偏绿发暗的问题，改为在 GPU 着色器（`uProWarmth`）与成片管线中进行无损光学色温偏移；纯自动态通过 `clearCaptureRequestOptions()` 100% 交还 OEM 原生 3A / HDR / 闪光灯控制权。
  - 新增 `setVideoModeEnabled`：拍照与专业模式仅绑定双流（`Preview + ImageCapture`），解除常驻 4K `VideoCapture` 对暗光自动快门 `≥ 1/30s` 的帧率钳制。
  - `takePhoto()` 在按下快门前显式 `await()` 异步 Camera2 参数生效，确保预览与成片亮度严格一致。
- **消除内置预设双重调色（Double-Grading）与补全着色器通道**
  - 相机启动默认改为纯净 **「原片直出」**；仅对真实外部 `.cube` 文件加载 3D LUT 纹理，彻底修复内置配方重复叠加两次导致暗部死黑、暗角翻倍的问题。
  - 补全 `GlFilterShaderProgram` 的 `uShadows`、`uHighlights`、`uWhiteGain`、`uFade`、`uSoftGlow` 着色器通道，并在 `GlLutPreviewView` 实现 `64×48` GPU FBO 实时 RGB/明度直方图采样与竖屏无畸变中心裁剪（`portraitRatio`）。

### 🎨 全应用 4 层光学暗房 UI 与交互重构

- **4 层光学仪器色彩与等宽遥测排版（`Color.kt` / `Type.kt` / `DesignSystem.kt` / `OphotoAppShell.kt`）**
  - 建立曜石底板 → 阳极氧化仪表甲板 → 内凹 LCD 参数槽 → 悬浮毛玻璃胶囊四级视觉层次，配合 `56dp` 紧凑型暗房底栏（`PrecisionStudioBottomDock`）释放垂直取景空间。
- **4 区哈苏/徕卡式专业相机界面（`CameraScreen.kt` + `ViewfinderOverlays.kt`）**
  - `42dp` 极简顶栏 + 四向旁轴取景角标 / 双翼电子水平仪 / 四角对焦框（支持点触对焦与上下滑动手势调节 EV）/ 内嵌等效焦段胶囊 + 互斥折叠的 5 格实时测光仪表盘与精雕刻度尺（`EngravedScaleMarks`）+ 居中模式拨盘与双环金属快门。
- **大师调色库、无损暗房、现场助手与设备诊断升级**
  - **大师调色库**：修复 `MasterPresetCard` 布局错位 Bug，升级为 `132dp` 全宽样片横幅 + 6 格内凹大师参数遥测矩阵 + 紧凑光学搜索栏。
  - **无损暗房**：新增支持点按与拖拽的 **RGB / 红 / 绿 / 蓝 四通道交互曲线**、带实时渲染缩略图的滤镜带与 5 段式分段工具舱。
  - **现场助手 & 设备诊断**：升级分段式镜头蓝图、带光束锥的极坐标灯位雷达、4 格天文/景深遥测卡片与物理镜头矩阵诊断面板。

---

## [v1.0.0] - 2026-10-23 — *Optical Horizon*

### ✨ Highlights / 首发核心特性

- **专业相机与 OPPO / ColorOS 多物理镜头控制 (`CameraX + Camera2Interop`)**
  - 支持拍照、专业手动 Pro、人像、夜景、视频录制、慢门长曝光、连拍、AEB 包围曝光（`-2EV / 0EV / +2EV`）与 `3s / 5s / 10s` 倒计时快门。
  - 动态探测超广角（`0.6x`）、大底主摄（`1x`）、中焦人像（`2x–3x`）、潜望长焦（`3.5x–6x`）及前摄物理参数，完整适配 Find N 系列折叠屏双栏自适应布局。
  - 支持手动 ISO、手动快门速度（`SENSOR_EXPOSURE_TIME`）、EV 曝光补偿、一键 AE/AF 测光对焦锁定、`2500K–10000K` 白平衡色温与 RGGB 色调矩阵、手动对焦推杆及 `2.5x` 放大对焦。
  - 内置九宫格、黄金螺旋、对角线、十字准星、宽银幕 `21:9` / `XPan 65:24` 遮幅、重力传感器电子水平仪、实时 RGB/Luma 直方图、Sobel 边缘峰值对焦（红/绿/蓝/黄）与 `96%` 高光斑马纹。
- **OpenGL ES 3.0 实时 3D LUT 胶片调色、安全沙盒导入与大师悬浮窗**
  - 零拷贝 `samplerExternalOES` + 硬件三线性插值 `sampler3D` 着色器管线与高分辨率图片处理引擎。
  - 内置 12 款 100% 原创无商标风险色彩预设（`青绿胶片`、`城市夜景`、`琥珀暖阳`、`银盐纪实`、`柔雾清透人像`、`海风青蓝`、`赤陶人文`、`森系微光`、`旧日暖笺`、`极简几何`、`食光暖味`、`雾银低反差`），每款同步配备 **OPPO 原生大师模式调参参考卡**。
  - 安全沙盒导入器支持 `.cube`（`≤10MB`、`LUT_3D_SIZE ≤ 64`）、`.json`、`.filterpack` / `.zip`（拦截 Zip-Slip 路径穿越、Zip 炸弹与脚本载荷）、`OPHOTO1:` 离线口令与 25×25 光学二维码分享、社区大师 JSON 预设导入，以及 `.cube` LUT 导出。
  - `MasterOverlayService` 提供标准大师卡、紧凑胶囊、迷你悬浮球三种可拖拽半透明系统悬浮窗及 ColorOS 14–17 侧载受限设置解锁指引。
- **摄影现场助手 (`Field Assistant`)**
  - 场景镜头灵感卡（参数化构图光线蓝图 Canvas + 一键装载进相机开拍）、俯视极坐标布光雷达图、6 合 1 现场天文与光学计算器（NOAA 黄金/蓝调时刻、朔望月相、多传感器等效 35mm 焦段、景深超焦距、ND1000 慢门 B 门倒计时）、出发清单与破冰引导话术。
- **无损暗房编辑器与隐私保护**
  - 支持单张精修与批量套用预设、撤销/重做历史栈、按住前后对比、单调三次样条交互式 RGB 曲线、8 色相 HSL 混色器、裁剪遮罩、画廊卡纸/曜石黑金/35mm 底片大师签注相框及一键抹除 EXIF GPS 位置隐私。
