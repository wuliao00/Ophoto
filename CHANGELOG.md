# Changelog

All notable changes to **Ophoto (`com.ophoto`)** will be documented in this file.

## [v1.2.1] - 2026-10-24 — *Darkroom Stable Hotfix*

### 🛠️ 深度复审 · 彻底封堵残余闪退

- **批量相册注册崩溃修复**：`PickMultipleVisualMedia` 在 API26-32 / ColorOS 无 PhotoPicker 设备上于 Composable 注册阶段即抛 `IllegalStateException` 导致进入暗房即闪退；改为 **仅当 `isPhotoPickerAvailable==true` 时条件式注册**，否则批量按钮自动降级为单张选择并 Toast 提示，彻底消除旧机型进暗房闪退。
- **曲线手势冲突修复**：`ProCurvePad` 原双 `pointerInput`（`detectTapGestures` + `detectDragGestures`）抢占冲突导致手势失效；收敛为单一 `pointerInput + detectTapGestures`，保证点击抬升/压暗稳定不丢帧。
- **空指针与未使用状态修复**：`loadErrorMsg!!` 改为安全解包、`animatedAlpha` 未应用导致渲染态无过渡现已绑定至画布透明度、`DesignSystem.EmptyState` 的 `!!` 改为安全解包，消除 Lint `abortOnError` 隐患。
- **资源回收与解码加固**：批量导出时 `cur/out` 回收增加 `!== sourceBitmap` 双重校验，避免回收仍在展示的原片；超大图分支阈值与 `FileDescriptor` 采样路径已加 `NoClassDefFoundError` 兜底。

## [v1.2.0] - 2026-10-24 — *Darkroom Pro Fix*

### 🛠️ 紧急修复 · 闪退终结

- **彻底修复“点击相册闪退”**（高优先级 P0 缺陷闭环）：
  - 原因：`PickVisualMedia` 在 OPPO / ColorOS / realme 低版本系统及无 Google 相册支持设备上不可用，直接抛 `IllegalStateException`；大图 `readBytes()` 无采样易触发 `OutOfMemoryError`；全链路无异常兜底。
  - 修复：新增 **双轨安全相册选择器** — 自动检测 `isPhotoPickerAvailable`，支持则走系统 Photo Picker，否则无缝回退至 `GetContent("image/*")` 并双重 `try/catch` 兜底；批量选择在不支持设备上优雅降级并提示。
  - **超大图安全解码管线**：优先 `FileDescriptor + inSampleSize` 采样至 ≤2200px，>18MB 自动再降采样，`OOM` 时自动切换 `RGB_565` 二次解码，EXIF 读取与渲染全包裹 `runCatching`，失败回退原片并 Toast 友好提示，彻底杜绝暗房页闪退。

### ✨ 暗房 2.0 · 专业光学级 UI 重塑

- **沉浸式 PRO DARKROOM 工作台**：顶部专业状态栏（分辨率胶囊 / EXIF 一行流 / 撤销重做 / 原片对比 / 一键导出）、中央黑曜画布（微渐晕 + 网格叠加 + 加载遮罩 + 错误重试卡 + 空状态引导）、底部 5 段式光学分栏。
- **胶片预设带**：120dp 横向胶片卡，`FilterEngine` 对缩略图实时渲染预览，选中鎏金边框，强度气泡滑杆，批量套用与配方复制一键完成。
- **影调曲线台**：综合/R/G/B 四通道分色曲线，支持点击 + 拖拽手势，填充光晕与暗部/高光刻度，内置胶片 S 曲线与日系高调空气预设。
- **HSL 混色台**：8 色相药丸带色点选中态，色相/饱和/明度三滑杆独立调节，清透天空一键预设。
- **裁剪蒙版台**：可视化画幅图标（1:1/4:3/3:2/16:9/21:9/原比例）、旋转 90°/水平镜像、-15°~+15° 水平校正、线性天空/径向聚光局部蒙版与 EV 微调。
- **画框导出台**：四种大师签注相框可视化卡（无/纯白画廊/曜石黑金/胶片底片）、自定义签名输入、JPEG/PNG/WEBP 格式切换、GPS 隐私抹除开关与导出位置说明。

### 🎨 全局导航升级

- **品牌化专业工作台外壳**：折叠屏 `NavigationRail` 新增镜头品牌头（`OP` 徽章 + `OPTICAL STUDIO` 副标题）与版本徽章；手机端底栏升级为 18dp 圆角悬浮 InstrumentDeck，选中态鎏金光晕与加粗标识，统一 Obsidian / BrassGold 光学主题，告别简陋感。

## [v1.1.0] - 2026-10-24 — *Precision Studio*

### ✨ 优化

- **专业模式曝光修复**：四态曝光互易率、双流拍照帧率解绑、无损光学白平衡、滤镜双重叠加修复。
- **全应用 4 层光学仪器级 UI 重构**：42dp 极简顶栏、大画幅无畸变取景框、5 格测光仪表盘、56dp 暗房底栏、大师调色 132dp 样片横幅等。

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
