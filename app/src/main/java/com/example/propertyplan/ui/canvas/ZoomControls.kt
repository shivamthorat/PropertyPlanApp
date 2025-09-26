package com.example.propertyplan.ui.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ZoomControls(
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = modifier.padding(0.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.End
    ) {
        ElevatedButton(onClick = onZoomIn) { Text("+") }
        ElevatedButton(onClick = onZoomOut) { Text("–") }
        OutlinedButton(onClick = onReset) { Text("Reset") }
    }
}
