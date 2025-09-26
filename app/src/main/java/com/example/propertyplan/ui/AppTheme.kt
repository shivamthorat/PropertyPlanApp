package com.example.propertyplan.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Emerald = Color(0xFF10B981)
private val Dark = darkColorScheme(
    primary = Emerald,
    secondary = Color(0xFFA7F3D0),
    background = Color(0xFF0A0F1A),
    surface = Color(0xFF0F1626),
    onSurface = Color(0xFFEAFBF5),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Dark,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(22.dp),
            extraLarge = RoundedCornerShape(26.dp),
        ),
        content = content
    )
}
