# Camera preview release smoke test

Run on physical devices (including the reported device) before considering the camera fix verified:

1. Start with camera permission denied. Check that no simulated photo can be captured, grant permission and confirm the live scene appears. Cover and uncover the lens to distinguish the scene from a placeholder.
2. Compare indoor/outdoor live view with the system camera; test the exposure chip at 0, +0.7, +1.3 and +2 EV. Compensation is limited and quantized by the device's supported range. Check for clipped highlights.
3. Switch lenses, rotate the device and background/foreground the app. Verify the stream resumes. Test repeated opening and closing of the camera screen.
4. Take an unfiltered picture and a filtered picture, verify the files in the gallery. The preview is currently unfiltered and may be cropped differently from the saved image. Check that errors do not save a synthetic image.
5. Test on a device with no compatible video+photo+preview combination: the photo-only fallback should still show a viewfinder; recording should report unavailability.
6. Test lack of permission, camera in use by another application and storage failure. Check that retry and error messages are visible.

The preview currently does not support live filter, peaking, zebra or live histogram overlays. CI runs unit tests and compiles instrumentation tests; it does not run these physical-device checks.
