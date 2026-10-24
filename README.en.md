# Ophoto · Pro Camera × 3D LUT Film Grading × On-Site Photo Assistant

[![CI](https://github.com/wuliao00/Ophoto/actions/workflows/ci.yml/badge.svg)](https://github.com/wuliao00/Ophoto/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/wuliao00/Ophoto?color=FF6A2B&label=APK%20Release)](https://github.com/wuliao00/Ophoto/releases/latest)
[![Platform](https://img.shields.io/badge/Android-API%2026--35%20(ColorOS%20%2F%20OxygenOS)-10B981)](https://developer.android.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

> **📥 Direct Signed APK Download**: [**Ophoto v1.0.0 (`Ophoto-v1.0.0-release.apk`)**](https://github.com/wuliao00/Ophoto/releases/tag/v1.0.0) · **中文文档**: [README.md](README.md) · **Changelog**: [CHANGELOG.md](CHANGELOG.md) · **Architecture**: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

**Ophoto** (`com.ophoto`) is a clean-room open-source professional photography application tailored for **OPPO, OnePlus, and realme smartphones** (ColorOS / OxygenOS / realme UI) while remaining 100% compatible with standard Android 8.0+ devices.

## Key Capabilities

1. **CameraX + Camera2 Pro Viewfinder**: Multi-lens physical camera discovery (Ultra-Wide, 1-inch Main, Medium Tele, Periscope Tele), manual ISO, shutter speed (`SENSOR_EXPOSURE_TIME`), EV, AE/AF lock, manual focus + 2.5x focus zoom, Kelvin white balance (`2500K–10000K`) + RGGB tint, rule-of-thirds / golden-spiral / diagonal / XPan overlays, live RGB histogram, Sobel focus peaking, and 96% highlight zebra stripes.
2. **Real-Time OpenGL ES 3.0 3D LUT Engine & Hardened Importer**: 12 built-in trademark-clean film & color presets, `.cube` 3D LUT import/export, `.filterpack` / `.json` sandboxed importer (protected against Zip-Slip, zip bombs, and executable payloads), `OPHOTO1:` share codes & optical QR codes, plus a draggable ColorOS **Master Mode Floating Overlay HUD** (`MasterOverlayService`).
3. **On-Site Photography Assistant**: Parametric shot inspiration blueprints with one-tap camera setup loading, 2D polar lighting diagrams, NOAA Golden/Blue Hour & Moon Phase calculator, multi-sensor DoF/hyperfocal & ND1000 calculators, departure checklists, and portrait/street direction talk lines.
4. **Non-Destructive Darkroom Editor**: Interactive cubic RGB tone curve, 8-band HSL mixer, crop/straighten, radial/linear local masks, gallery signature frames, and one-toggle EXIF GPS privacy stripping.

## Build & Verify

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```
