package com.example.propertyplan.ui.adaptive

import android.content.res.Configuration
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration


enum class Orientation { Portrait, Landscape }

@Composable
fun orientation(): Orientation {
    val cfg = LocalConfiguration.current
    return if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) Orientation.Landscape else Orientation.Portrait
}

data class Breakpoints(
    val compact: Boolean,
    val medium: Boolean,
    val expanded: Boolean
)

@Composable
fun breakpoints(wsc: WindowSizeClass): Breakpoints = when (wsc.widthSizeClass) {
    WindowWidthSizeClass.Compact -> Breakpoints(compact = true, medium = false, expanded = false)
    WindowWidthSizeClass.Medium -> Breakpoints(compact = false, medium = true, expanded = false)
    WindowWidthSizeClass.Expanded -> Breakpoints(compact = false, medium = false, expanded = true)
    else -> Breakpoints(true, false, false)
}

@Composable
fun isTall(wsc: WindowSizeClass) = wsc.heightSizeClass >= WindowHeightSizeClass.Medium
