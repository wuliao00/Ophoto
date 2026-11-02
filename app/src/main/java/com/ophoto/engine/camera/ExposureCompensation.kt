package com.ophoto.engine.camera

import kotlin.math.roundToInt

/** Convert requested EV to a supported CameraX compensation index. */
internal fun exposureCompensationIndex(ev: Float, step: Float, min: Int, max: Int): Int {
    if (!ev.isFinite() || !step.isFinite() || step <= 0f || min > max) return 0
    return (ev / step).roundToInt().coerceIn(min, max)
}
