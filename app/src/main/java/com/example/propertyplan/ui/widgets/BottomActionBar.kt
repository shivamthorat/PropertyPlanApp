package com.example.propertyplan.ui.widgets

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.propertyplan.vm.UIMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BottomActionBar(
    mode: UIMode,
    onModeChange: (UIMode) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top row: mode toggle; pushes actions below on small widths
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SegmentedButtonGroup(
                    selected = if (mode == UIMode.Draw) 0 else 1,
                    labels = listOf("Draw", "Move"),
                    onSelected = { onModeChange(if (it == 0) UIMode.Draw else UIMode.Move) }
                )
            }

            // Actions: wrap on small screens, align nicely on large
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onUndo) { Text("Undo") }
                OutlinedButton(onClick = onRedo) { Text("Redo") }
                Button(onClick = onDuplicate) { Text("Duplicate") }
                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SegmentedButtonGroup(
    selected: Int,
    labels: List<String>,
    onSelected: (Int) -> Unit
) {
    if (labels.isEmpty()) return
    SingleChoiceSegmentedButtonRow {
        labels.forEachIndexed { index, label ->
            SegmentedButton(
                selected = selected == index,
                onClick = { onSelected(index) },
                shape = SegmentedButtonDefaults.itemShape(index, labels.size),
                label = { Text(label) }
            )
        }
    }
}
