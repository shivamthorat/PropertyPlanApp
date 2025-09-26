package com.example.propertyplan.ui.canvas

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TopBarStatus(
    scale: Float,
    ppm: Int,
    angleSnap: Boolean,
    gridSnap: Boolean,
    selection: String?,
    onZoomToFit: () -> Unit,
    showGrid: Boolean,
    toggleGrid: () -> Unit,
    showGuides: Boolean,
    toggleGuides: () -> Unit,
    showLabels: Boolean,
    toggleLabels: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Zoom ${(scale * 100).toInt()}%")
            Text("${ppm} px/m")
            AssistChip(onClick = onZoomToFit, label = { Text("Fit") })
            FilterChip(selected = showGrid, onClick = toggleGrid, label = { Text("Grid") })
            FilterChip(selected = showGuides, onClick = toggleGuides, label = { Text("Guides") })
            FilterChip(selected = showLabels, onClick = toggleLabels, label = { Text("Labels") })
            Text(selection?.let { "Selected: $it" } ?: "No selection")
            Text(if (angleSnap) "∠Snap 15°" else "∠Free")
            Text(if (gridSnap) "Grid Snap" else "Free Move")
        }
    }
}
