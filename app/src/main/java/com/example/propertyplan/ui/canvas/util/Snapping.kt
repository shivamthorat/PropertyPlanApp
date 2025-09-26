package com.example.propertyplan.ui.canvas.util

import kotlin.math.abs

fun Float.snapToTargets(targets: List<Float>, threshold: Float): Float {
    var out = this
    for (t in targets) {
        if (abs(this - t) <= threshold) { out = t; break }
    }
    return out
}

fun Float.format0(): String = String.format("%.0f", this)
fun Float.format1(): String = String.format("%.1f", this)
