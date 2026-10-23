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
