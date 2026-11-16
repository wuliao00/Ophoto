# Ophoto 架构设计与安全规范说明

## 1. 五层解耦架构

| 层级 | 包路径 | 核心职责 |
|---|---|---|
| **表现层 (Presentation)** | `com.ophoto.ui.*` | Compose 暗色光学取景器界面、折叠屏自适应 `NavigationBar`/`NavigationRail`、实时 GLSL 预览桥接、曲线与极坐标灯位 Canvas |
| **系统悬浮层 (Overlay Service)** | `com.ophoto.service` | `MasterOverlayService` 提供标准大师卡、紧凑胶囊、迷你悬浮球三种系统级半透明参考窗 |
| **领域与计算层 (Domain)** | `com.ophoto.domain.calculator` | 纯 Kotlin 零 Android 依赖的 NOAA 太阳赤纬/均时差黄金蓝调时刻、朔望月相、光学景深超焦距与 ND 慢门算法 |
| **核心引擎层 (Engine)** | `com.ophoto.engine.*` | `ICameraEngine` (`CameraX2Engine`)、`IFilterEngine` (`FilterEngineImpl`)、`GlFilterShaderProgram` (OpenGL ES 3.0 `sampler3D`)、`ImageProcessEngine`、`MetadataEngine` |
| **数据与基础设施层 (Data & Core)** | `com.ophoto.data.*`, `com.ophoto.core.*` | Room 数据库、DataStore 偏好、沙盒安全导入器 (`FilterImporterImpl` + `FileSandboxValidator`)、OPPO/OnePlus/realme 硬件探测器 (`OppoDeviceInspector`)、MediaStore 导出器 |

## 2. 外部滤镜包安全校验清单

1. **单文件 `.cube` 校验**：体积上限 `10 MB`，`LUT_3D_SIZE` 严格限制在 `2..64`，逐行浮点范围校验并拦截 `<script`、`javascript:`、`runtime.exec` 等异常载荷。
2. **压缩包 `.filterpack` / `.zip` 沙盒校验**：
   - 压缩包上限 `50 MB`，解压总累计字节上限 `100 MB`，最大文件条目数 `64`（防 Zip Bomb）。
   - 使用 `canonicalPath` 校验所有 `ZipEntry` 必须严格位于应用私有沙盒目录内部，拒绝 `../` 与反斜杠路径穿越（防 Zip-Slip）。
   - 拦截 `.apk` / `.dex` / `.so` / `.sh` / `.js` / `.py` / `.html` 等全部可执行与脚本扩展名。
   - 强制校验 `manifest.json` 的 `min_app_version` 版本兼容性，不通过时完整回滚临时目录并返回结构化错误码（`SECURITY_RISK` / `VERSION_NOT_COMPATIBLE` / `LUT_INVALID`）。

## 设计系统（v1.6.0 · Quiet Chrome）

| 层 | 位置 | 说明 |
| --- | --- | --- |
| 字体 | `ui/theme/Type.kt` + `res/font/inter_*.ttf` | Inter 400/500/600/700 承担正文与标签；ISO/快门/EV 读数使用 `ReadingStyle`（tabular 等宽） |
| 色板 | `ui/theme/Color.kt` | 近黑背景三档（`ObsidianBg` / `CanvasBlack` / `ViewfinderSurface`）、单一强调色 `BrassGold`、文字三级 |
| 组件 | `ui/theme/DesignSystem.kt` | `OphotoChip`（唯一控制态）、`OphotoIconButton`、`OphotoPageHeader`、`OphotoMaterial` / `MaterialThin/Regular/Thick`、`Modifier.pressScale()` |
| 示例素材 | `data/repository/DemoPhotos.kt` + `assets/demo/*.jpg` | 三张实拍底片（人像/夜景/风光），`loadScaled()` 采样解码并缓存，供调色库与现场助手使用 |

交互约定：

- 相机页 4 秒无操作 → `ChromeDensity.COMPACT`（顶部读数与底部工具行淡出），长按画面 → `HIDDEN`（纯构图）。
- 暗房点击画面切换 `chromeVisible`，双击对比原片；工具面板为 340 dp 上限的底部检查器，不再是常驻控制甲板。
- 所有抽屉/面板使用 `spring(dampingRatio = 0.86f, stiffness = 420f)` 从内容边缘延伸，背景只做 40% 压暗。
- 拍照完成后：`MediaStoreSaver` 写入相册 → `MediaRepository.recordCapture()` 落库 → `OphotoContainer.openMediaInEditor(uri)` 同时记录 `lastCapturedMediaUri`，暗房首次组合即可回放该成片。
