package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.propertyplan.model.Floor

@Composable
fun FloorTabs(
    floors: List<Floor>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        floors.forEachIndexed { i, f ->
            val selected = i == currentIndex
            FilterChip(
                selected = selected,
                onClick = { onSelect(i) },
                label = { Text(f.name) }
            )
        }
    }
}
