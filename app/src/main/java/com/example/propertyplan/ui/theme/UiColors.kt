package com.example.propertyplan.ui.theme

import androidx.compose.ui.graphics.Color

object UiColors {
    object Background {
        val Canvas = Color(0xFF0B1020)
    }
    object Grid {
        val Light = Color.White.copy(alpha = 0.06f)
        val Heavy = Color.White.copy(alpha = 0.18f) // heavy every 5th line
    }
    object State {
        val Snap = Color(0xFF22C55E)
        val Warning = Color(0xFFEAB308)
    }
}
