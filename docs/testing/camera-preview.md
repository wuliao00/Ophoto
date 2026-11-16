# Camera preview release smoke test

> v1.5.0 · Luminous Finder：以下 1–6 项仍然有效，另请补测第 7–10 项（取景亮度与黑场诊断）。

Run on physical devices (including the reported device) before considering the camera fix verified:

1. Start with camera permission denied. Check that no simulated photo can be captured, grant permission and confirm the live scene appears. Cover and uncover the lens to distinguish the scene from a placeholder.
2. Compare indoor/outdoor live view with the system camera; test the exposure chip at 0, +0.7, +1.3 and +2 EV. Compensation is limited and quantized by the device's supported range. Check for clipped highlights.
3. Switch lenses, rotate the device and background/foreground the app. Verify the stream resumes. Test repeated opening and closing of the camera screen.
4. Take an unfiltered picture and a filtered picture, verify the files in the gallery. The preview is currently unfiltered and may be cropped differently from the saved image. Check that errors do not save a synthetic image.
5. Test on a device with no compatible video+photo+preview combination: the photo-only fallback should still show a viewfinder; recording should report unavailability.
6. Test lack of permission, camera in use by another application and storage failure. Check that retry and error messages are visible.

Note (v1.5.0): the preview is still unfiltered (filters apply on capture). Zebra and the live histogram are now driven by sampled preview frames, so they are coarse but real. CI runs unit tests and compiles instrumentation tests; it does not run these physical-device checks.

## v1.5.0 取景亮度专项

7. 相机页点「亮度」→ 依次切 5 档方案，确认画面亮度有可感知变化且**成片曝光不变**（拍一张对比 EXIF 的 EV）。
8. 在暗光房间打开相机，等待约 1 秒：左下测光条应显示实测亮度百分比；低于约 5% 时右上出现「画面过暗 · 点此修复」，点击后恢复自动 AE 并提亮取景。
9. 进入「专业」把 ISO 设为 50、快门设为 1/8000（画面变黑），再切回「拍照」：取景应自动恢复正常亮度（验证 `AE_MODE_ON` 回写）。
10. 设置 → 相机体检 → 「测一帧真实亮度」：多数机型应返回亮度百分比与 AE 状态；若提示探针不可用（系统禁止独占相机），确认取景功能本身不受影响。
11. 连续切 5 次底部 Tab 往返相机页，取景应在 1 秒内恢复（不再重建会话导致长时间黑屏）。
